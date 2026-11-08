#!/usr/bin/env python3
"""LionAppStore için yerel TLS proxy + mock backend.

Neden gerekli:
  - cainternational-eu.cheryinternational.com sertifikası Ed25519; Android 11
    BoringSSL bu sertifikayla TLS handshake yapamıyor.
  - Gerçek backend /intlCabin/app/selectedVehicleApp için istemci sertifikası
    (PKI) istiyor; emülatörde VIN/SN olmadığı için alınamıyor.

Bu proxy CONNECT isteğini karşılar, TLS'i sonlandırır ve hedef host
`--mock-host` ise yerel JSON/APK yanıtı döndürür; diğer host'ları OpenSSL
üzerinden gerçek sunucuya tüneller.

Kullanım:
  tools/emulator/appstore_proxy_certs.sh          # certleri üret + CA'yı cihaza kur
  python3 tools/emulator/appstore_mock_proxy.py --port 8443
  adb shell settings put global http_proxy 10.0.2.2:8443
"""
import argparse
import json
import socket
import ssl
import struct
import sys
import threading
import time
import urllib.parse
import zlib
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
DEFAULT_CERT = HERE / "certs" / "server.crt"
DEFAULT_KEY = HERE / "certs" / "server.key"
DEFAULT_APK = HERE / "assets" / "LionTest.apk"
ICON_DIR = HERE / "assets" / "icons"
CAR_APP_DIR = ROOT / "car_info" / "rootdump" / "full_20261101" / "root" / "product" / "app"
MOCK_HOST = "cainternational-eu.cheryinternational.com"

# Aracın kendi Lion/Chery uygulamaları (gerçek APK'lar root dump'tan).
LION_APPS = [
    {"pkg": "com.liontech.spotify", "name": "Spotify", "vc": 25092411, "vn": "0.2.25092411", "size": "62 MB", "type": "Müzik", "dir": "LionSpotify"},
    {"pkg": "com.liontech.autonavi", "name": "Navi", "vc": 25121822, "vn": "1.0.0.025121822", "size": "188 MB", "type": "Navigasyon", "dir": "LionAutoNavi"},
    {"pkg": "com.liontech.weather", "name": "Weather", "vc": 25120323, "vn": "0.2.25120323", "size": "150 MB", "type": "Hava Durumu", "dir": "LionWeather"},
    {"pkg": "com.news.cherynews", "name": "News", "vc": 24091317, "vn": "1.0.4", "size": "72 MB", "type": "Haber", "dir": "LionNews"},
    {"pkg": "com.liontech.membercenter", "name": "Member Center", "vc": 26010610, "vn": "0.2.26010610", "size": "40 MB", "type": "Hesap", "dir": "LionMemberCenter"},
    {"pkg": "com.vk.chery.music", "name": "VK Music", "vc": 430362492, "vn": "1.0.251013195549", "size": "86 MB", "type": "Müzik", "dir": "LionVKMusic"},
    {"pkg": "com.vk.chery.video", "name": "VK Video", "vc": 425610547, "vn": "1.0.250528191914", "size": "72 MB", "type": "Video", "dir": "LionVKVideo"},
    {"pkg": "com.beeptunes.cherymusic", "name": "Beeptunes", "vc": 24091317, "vn": "1.0.6", "size": "35 MB", "type": "Müzik", "dir": "LionBeeptunes"},
    {"pkg": "com.bebin.cheryvideo", "name": "Bebin", "vc": 24091317, "vn": "1.0.3", "size": "41 MB", "type": "Video", "dir": "LionBebin"},
    {"pkg": "com.liontech.radio", "name": "myTuner", "vc": 25092413, "vn": "0.2.25092413", "size": "71 MB", "type": "Radyo", "dir": "LionOnlineRadio"},
]
APP_APKS = {a["pkg"]: CAR_APP_DIR / a["dir"] / f"{a['dir']}.apk" for a in LION_APPS}
APP_BY_PKG = {a["pkg"]: a for a in LION_APPS}
SECTIONS = [
    ("Öne Çıkanlar", ["com.liontech.spotify", "com.liontech.autonavi", "com.liontech.weather"]),
    ("Eğlence", ["com.vk.chery.music", "com.vk.chery.video", "com.beeptunes.cherymusic", "com.bebin.cheryvideo", "com.liontech.radio"]),
    ("Hizmetler", ["com.liontech.membercenter", "com.news.cherynews"]),
]


def log(msg):
    print(time.strftime("%H:%M:%S"), msg, flush=True)


def make_png(w, h, rgba):
    """Minimal geçerli RGBA PNG (harici bağımlılık yok)."""
    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    raw = b"".join(b"\x00" + bytes(rgba) * w for _ in range(h))
    return (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 6))
            + chunk(b"IEND", b""))


BANNER = make_png(800, 360, (38, 74, 128, 255))
ICON = make_png(128, 128, (210, 96, 40, 255))
ICON2 = make_png(128, 128, (60, 150, 90, 255))


def json_resp(obj, code=200):
    body = json.dumps(obj, ensure_ascii=False).encode()
    return (f"HTTP/1.1 {code} OK\r\n"
            "Content-Type: application/json;charset=utf-8\r\n"
            f"Content-Length: {len(body)}\r\n"
            "Connection: keep-alive\r\n\r\n").encode() + body


def data_resp(data, code=200):
    return json_resp({"code": code, "message": "ok", "data": json.dumps(data, ensure_ascii=False)})


def app_entry(app, host, section_name, app_id):
    pkg = app["pkg"]
    return {
        "appId": str(app_id), "appName": app["name"], "apkname": f"{pkg}.apk",
        "apkpackage": pkg, "apkurl": f"https://{host}/apk/{pkg}.apk", "apksize": app["size"],
        "apkversioncode": app["vc"], "apkversionname": app["vn"],
        "appTypeName": app["type"], "binding": "", "downloadtotal": "1234",
        "iconurl": f"https://{host}/icon/{pkg}.png", "sectionName": section_name,
        "sectionid": str(app_id), "sourceType": 0, "updateType": 0,
    }


def top_select(host):
    base = f"https://{host}"
    banner = {
        "id": "b1", "imageurl": f"{base}/img/banner.png", "targeturl": "",
        "title": "Lion Uygulamaları", "type": 0,
    }
    sections = []
    for idx, (name, pkgs) in enumerate(SECTIONS, start=1):
        items = [app_entry(APP_BY_PKG[p], host, name, idx * 100 + j) for j, p in enumerate(pkgs, start=1)]
        sections.append({"name": name, "sectionid": str(idx), "queryRotationChartDto": [], "vehicleAppDto": items})
    return {
        "queryRotationChartListDto": [{"queryRotationChartDto": [banner]}],
        "selectedVehicleAppListDto": sections,
    }


def read_request(f):
    line = f.readline()
    if not line:
        return None
    request_line = line.decode("latin1").rstrip("\r\n")
    headers = {}
    while True:
        line = f.readline()
        if not line or line in (b"\r\n", b"\n"):
            break
        k, _, v = line.decode("latin1").partition(":")
        headers[k.strip().lower()] = v.strip()
    body = b""
    n = int(headers.get("content-length", 0) or 0)
    if n:
        body = f.read(n)
    return request_line, headers, body


def handle_mock(tls_conn, host, apk_path):
    f = tls_conn.makefile("rwb")
    while True:
        req = read_request(f)
        if not req:
            break
        line, headers, body = req
        try:
            method, path, _ = line.split(" ", 2)
        except ValueError:
            break
        p = urllib.parse.urlparse(path).path
        log(f"MOCK {host} {method} {p} body={body[:160]!r}")
        if p.endswith("/app/selectedVehicleApp"):
            resp = data_resp(top_select(host))
        elif p.endswith("/app/queryAppToUpdate") or p.endswith("/app/queryManageAppList"):
            resp = data_resp([])
        elif p.endswith("/app/queryVehicleColumnApp"):
            resp = data_resp([{"sectionid": "1", "sectionName": "Öne Çıkanlar", "appTypeName": "Eğlence"}])
        elif p.endswith("/app/queryAppDetails"):
            try:
                req = json.loads(body or b"{}")
            except ValueError:
                req = {}
            pkg = req.get("appid") or req.get("apkpackage") or "com.liontech.spotify"
            app = APP_BY_PKG.get(pkg, LION_APPS[0])
            resp = data_resp({
                **app_entry(app, host, "Detay", 1001),
                "screenShotList": [], "apkversionname": app["vn"],
            })
        elif p.endswith("/intlCabin/map/getMapPOI"):
            # LionAutoNavi: search açılışında POI tip listesi (List<PoiTypeBean>)
            resp = data_resp([])
        elif p.endswith("/intlCabin/srcManage/qryActivationStatus"):
            # LionAutoNavi: aktivasyon zamanı; emülatörde süresiz aktif
            resp = data_resp({
                "active": True, "expiredMillSeconds": 4102444800000,
                "expireDate": "2099-12-31 23:59:59", "appId": "1", "vin": "",
            })
        elif p.endswith("/app/updateDownloadCnt") or p.endswith("/flow/sendAppInstallWarning"):
            resp = data_resp(True)
        elif p.startswith("/img/"):
            data = BANNER if "banner" in p else (ICON2 if "icon2" in p else ICON)
            resp = (b"HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nContent-Length: "
                    + str(len(data)).encode() + b"\r\nConnection: keep-alive\r\n\r\n" + data)
        elif p.startswith("/icon/"):
            name = p.rsplit("/", 1)[-1]
            icon_file = ICON_DIR / name
            try:
                data = icon_file.read_bytes() if icon_file.exists() else ICON
            except OSError:
                data = ICON
            resp = (b"HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nContent-Length: "
                    + str(len(data)).encode() + b"\r\nConnection: keep-alive\r\n\r\n" + data)
        elif p.startswith("/apk/"):
            name = p.rsplit("/", 1)[-1]
            pkg = name[:-4] if name.endswith(".apk") else name
            target = APP_APKS.get(pkg, apk_path)
            try:
                data = target.read_bytes()
                resp = (b"HTTP/1.1 200 OK\r\nContent-Type: application/vnd.android.package-archive\r\n"
                        b"Content-Length: " + str(len(data)).encode() + b"\r\nConnection: close\r\n\r\n" + data)
            except OSError as e:
                resp = json_resp({"code": 500, "message": str(e)}, 500)
        else:
            resp = data_resp(True)
        try:
            f.write(resp)
            f.flush()
        except OSError:
            break
    try:
        tls_conn.close()
    except OSError:
        pass


def pipe(a, b):
    try:
        while True:
            data = a.recv(65536)
            if not data:
                break
            b.sendall(data)
    except OSError:
        pass
    finally:
        for s in (a, b):
            try:
                s.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass


def handle(conn, addr, opts, server_ctx, client_ctx):
    try:
        conn.settimeout(30)
        buf = b""
        while b"\r\n\r\n" not in buf and len(buf) < 65536:
            chunk = conn.recv(4096)
            if not chunk:
                return
            buf += chunk
        head, _, _ = buf.partition(b"\r\n\r\n")
        first = head.split(b"\r\n")[0].decode("latin1", "replace")
        if not first.upper().startswith("CONNECT "):
            log(f"{addr} non-CONNECT {first[:80]}")
            return
        target = first.split()[1]
        host, _, port_s = target.rpartition(":")
        port = int(port_s) if port_s else 443
        log(f"{addr} CONNECT {host}:{port}")
        conn.sendall(b"HTTP/1.1 200 Connection Established\r\n\r\n")
        tls_conn = server_ctx.wrap_socket(conn, server_side=True)
        if opts.mock and host == opts.mock_host:
            log(f"{addr} MOCK MODE {host}")
            handle_mock(tls_conn, host, Path(opts.apk))
            return
        if opts.mock and opts.no_upstream:
            log(f"{addr} no-upstream, closing {host}")
            tls_conn.close()
            return
        upstream = socket.create_connection((host, port), timeout=20)
        upstream = client_ctx.wrap_socket(upstream, server_hostname=host)
        log(f"{addr} tunnel {host}:{port}")
        threading.Thread(target=pipe, args=(tls_conn, upstream), daemon=True).start()
        pipe(upstream, tls_conn)
    except Exception as e:
        log(f"{addr} error: {e}")
    finally:
        try:
            conn.close()
        except OSError:
            pass


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--bind", default="0.0.0.0")
    ap.add_argument("--port", type=int, default=8443)
    ap.add_argument("--cert", default=str(DEFAULT_CERT))
    ap.add_argument("--key", default=str(DEFAULT_KEY))
    ap.add_argument("--apk", default=str(DEFAULT_APK))
    ap.add_argument("--mock-host", default=MOCK_HOST)
    ap.add_argument("--no-mock", action="store_true", help="sadece TLS tüneli")
    ap.add_argument("--no-upstream", action="store_true", help="mock olmayan host'ları kapat")
    opts = ap.parse_args()

    if not Path(opts.cert).exists() or not Path(opts.key).exists():
        sys.exit(f"cert/key yok: {opts.cert} {opts.key}\nönce tools/emulator/appstore_proxy_certs.sh çalıştır")

    server_ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    server_ctx.load_cert_chain(opts.cert, opts.key)
    client_ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
    client_ctx.check_hostname = False
    client_ctx.verify_mode = ssl.CERT_NONE
    opts.mock = not opts.no_mock

    s = socket.socket()
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    s.bind((opts.bind, opts.port))
    s.listen(128)
    log(f"listening {opts.bind}:{opts.port} mock={opts.mock} host={opts.mock_host}")
    while True:
        c, addr = s.accept()
        threading.Thread(target=handle, args=(c, addr, opts, server_ctx, client_ctx), daemon=True).start()


if __name__ == "__main__":
    main()
