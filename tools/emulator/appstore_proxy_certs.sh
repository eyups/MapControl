#!/usr/bin/env bash
# LionAppStore mock proxy için test CA + server sertifikası üretir ve
# test CA'yı emülatörün sistem CA store'una bind-mount eder.
#
# Kullanım:
#   tools/emulator/appstore_proxy_certs.sh [--serial emulator-5554]
#   tools/emulator/appstore_mock_proxy.py --port 8443
set -euo pipefail

SERIAL="${ANDROID_SERIAL:-emulator-5554}"
while [ $# -gt 0 ]; do
  case "$1" in
    --serial) SERIAL="$2"; shift 2 ;;
    *) echo "bilinmeyen argüman: $1"; exit 2 ;;
  esac
done

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
CERT_DIR="$ROOT/tools/emulator/certs"
CA_KEY="$CERT_DIR/ca.key"
CA_CRT="$CERT_DIR/ca.crt"
SRV_KEY="$CERT_DIR/server.key"
SRV_CRT="$CERT_DIR/server.crt"
mkdir -p "$CERT_DIR"

if [ ! -f "$CA_KEY" ] || [ ! -f "$CA_CRT" ] || [ ! -f "$SRV_KEY" ] || [ ! -f "$SRV_CRT" ]; then
  echo "sertifikalar üretiliyor: $CERT_DIR"
  cat > "$CERT_DIR/ca.cnf" <<'EOF'
[req]
distinguished_name=dn
x509_extensions=v3_ca
prompt=no
[dn]
CN=MapControl Emulator CA
O=MapControl
[v3_ca]
basicConstraints=critical,CA:TRUE
keyUsage=critical,keyCertSign,cRLSign
subjectKeyIdentifier=hash
authorityKeyIdentifier=keyid:always
EOF
  cat > "$CERT_DIR/server.cnf" <<'EOF'
[req]
distinguished_name=dn
prompt=no
[dn]
CN=cainternational-eu.cheryinternational.com
O=MapControl
[v3_srv]
basicConstraints=critical,CA:FALSE
keyUsage=critical,digitalSignature,keyEncipherment
extendedKeyUsage=serverAuth
subjectAltName=DNS:cainternational-eu.cheryinternational.com,DNS:*.cheryinternational.com,DNS:*.cherystack.com,DNS:*.lionaitech.com,DNS:*.ebroauto.com,DNS:*.omodajaecoo.com.br,DNS:*.chirey.mx,DNS:*.chery.co.id,DNS:*.chery.my,DNS:*.cherry.ru,DNS:*.xtrimcars.com,IP:10.0.2.2
subjectKeyIdentifier=hash
authorityKeyIdentifier=keyid,issuer
EOF
  openssl req -x509 -newkey rsa:2048 -nodes -keyout "$CA_KEY" -out "$CA_CRT" -days 3650 -config "$CERT_DIR/ca.cnf" >/dev/null 2>&1
  openssl req -newkey rsa:2048 -nodes -keyout "$SRV_KEY" -out "$CERT_DIR/server.csr" -config "$CERT_DIR/server.cnf" >/dev/null 2>&1
  openssl x509 -req -in "$CERT_DIR/server.csr" -CA "$CA_CRT" -CAkey "$CA_KEY" -CAcreateserial \
    -out "$SRV_CRT" -days 3650 -extfile "$CERT_DIR/server.cnf" -extensions v3_srv >/dev/null 2>&1
  rm -f "$CERT_DIR/server.csr" "$CERT_DIR/ca.cnf" "$CERT_DIR/server.cnf" "$CERT_DIR/ca.srl"
fi

CA_HASH="$(openssl x509 -in "$CA_CRT" -noout -subject_hash_old)"
CA_NAME="${CA_HASH}.0"
cp "$CA_CRT" "$CERT_DIR/$CA_NAME"

ADB=(adb -s "$SERIAL")
if ! "${ADB[@]}" shell id 2>/dev/null | grep -q 'uid=0'; then
  echo "adb root"
  "${ADB[@]}" root >/dev/null
  sleep 2
fi

# /system ro olduğu için cacerts dizinini /data'da hazırlayıp bind-mount ediyoruz.
"${ADB[@]}" shell 'umount /system/etc/security/cacerts 2>/dev/null || true'
"${ADB[@]}" push "$CERT_DIR/$CA_NAME" "/data/local/tmp/$CA_NAME" >/dev/null
"${ADB[@]}" shell "rm -rf /data/local/tmp/cacerts && mkdir -p /data/local/tmp/cacerts \
  && cp -a /system/etc/security/cacerts/. /data/local/tmp/cacerts/ \
  && cp /data/local/tmp/$CA_NAME /data/local/tmp/cacerts/$CA_NAME \
  && chmod 644 /data/local/tmp/cacerts/$CA_NAME \
  && chown root:root /data/local/tmp/cacerts/$CA_NAME \
  && chcon u:object_r:system_file:s0 /data/local/tmp/cacerts /data/local/tmp/cacerts/* \
  && mount --bind /data/local/tmp/cacerts /system/etc/security/cacerts \
  && echo 'CA kuruldu: /system/etc/security/cacerts/$CA_NAME'"

echo "server cert: $SRV_CRT"
echo "server key : $SRV_KEY"
echo "CA         : $CA_CRT ($CA_NAME)"
