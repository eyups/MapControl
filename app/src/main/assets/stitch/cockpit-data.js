(function () {
  function text(value, fallback) {
    return value && value !== '—' ? value : (fallback || 'Veri yok');
  }
  function setTexts(map) {
    var originals = window.__cockpitOriginals;
    if (!originals) {
      originals = window.__cockpitOriginals = new WeakMap();
    }
    var mapped = window.__cockpitMapped;
    if (!mapped) {
      mapped = window.__cockpitMapped = new WeakSet();
    }
    var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, {
      acceptNode: function (node) {
        if (!node.nodeValue || !node.nodeValue.trim()) return NodeFilter.FILTER_REJECT;
        var p = node.parentElement;
        if (!p || p.tagName === 'SCRIPT' || p.tagName === 'STYLE') return NodeFilter.FILTER_REJECT;
        return NodeFilter.FILTER_ACCEPT;
      }
    });
    var nodes = [];
    while (walker.nextNode()) nodes.push(walker.currentNode);
    nodes.forEach(function (node) {
      var raw = originals.has(node) ? originals.get(node) : node.nodeValue;
      if (!originals.has(node)) originals.set(node, raw);
      var match = raw.match(/^(\s*)([\s\S]*?)(\s*)$/);
      if (!match) return;
      var key = match[2];
      if (Object.prototype.hasOwnProperty.call(map, key)) {
        node.nodeValue = match[1] + map[key] + match[3];
        mapped.add(node);
      }
    });
  }
  function apply(d) {
    d = d || { connected: false };
    var has = !!d.connected;
    var no = 'Veri yok';
    var map = {
      'v2.6.1': text(d.appVersion, no),
      'v2.4.1': text(d.appVersion, no),
      '14:32': text(d.time, no),
      '14:32:08 TRT': text(d.timeFull, no),
      '18°C Dış': no,
      '18°C': no,
      '4G LTE': no,
      '516 km': has ? text(d.range, no) : no,
      '516 km (%100)': has ? text(d.range, no) + ' (' + text(d.fuelShort, no) + ')' : no,
      '516': has ? text(d.rangeValue, no) : no,
      'Telematik Veriyolu CAN-FD v2.2 Aktif': 'Telematik Veriyolu CAN-FD',
      'CAN Lokma': 'CAN-FD',
      '(%100)': has ? text(d.fuelShort, no) : no,
      '2.221': has ? text(d.odoValue, no) : no,
      '2.221 km': has ? text(d.odo, no) : no,
      '2,4 bar': has ? text(d.tire, no) : no,
      '2.4 bar': has ? text(d.tire, no) : no,
      '2,4': has ? text(d.tire, no) : no,
      '2.4': has ? text(d.tire, no) : no,
      'TPMS OK (2,4 bar)': has ? text(d.tpmsLong, no) : no,
      'TPMS OK (2.4 bar)': has ? text(d.tpmsLong, no) : no,
      'TPMS OK': has ? text(d.tpms, no) : no,
      'TPMS Veri yok': no,
      '0 km/s': has ? text(d.speed, no) : no,
      '65 L': no,
      '42 km': has ? text(d.tripDistance, no) : no,
      '42': has ? text(d.tripDistance, no).replace(' km','') : no,
      '6.8 lt': has ? text(d.tripConsumption, no) : no,
      '6,8 lt': has ? text(d.tripConsumption, no) : no,
      '34 dk': has ? text(d.tripDuration, no) : no,
      '100 / 100': has ? text(d.score, no) + ' / 100' : no,
      '100 Puan': has ? text(d.score, no) + ' Puan' : no,
      'Efsane Şoför (100 Puan)': has ? text(d.scoreTitle, no) : no,
      '100 Puan (Efsane Şoför)': has ? text(d.scoreTitle, no) : no,
      '100': has ? text(d.score, no) : no,
      'Ahmet Y.': text(d.driver, no),
      'İyi günler, Ahmet Y.': 'İyi günler, ' + text(d.driver, no),
      'Aktif Profil: Ahmet Y. (Varsayılan - Ana Anahtar)': 'Aktif Profil: ' + text(d.driver, no),
      'AY': text(d.driverInitials, '—'),
      'D1': text(d.driverInitials, '—'),
      'Zeynep Y.': no,
      'Park Halinde': has ? 'Park Halinde' : no,
      'CANLI DVR': 'DVR',
      'NOMINAL': no,
      'NOMİNAL': no,
      'DOĞRULANDI & AKTİF': no,
      'Aktif': no,
      'AKTİF': no,
      'HAZIR': no,
      'Normal Belirti Yok': no,
      'Depo: %100 Dolu': has ? 'Depo: ' + text(d.fuel, no) : 'Depo: Veri yok',
      'Depo: Veri yok Dolu': 'Depo: Veri yok',
      'KAYITLI (Hazır)': no,
      'Akıllı Anahtar 1 (AWD-Key)': no,
      'Akıllı Anahtar 2': no,
      '60 FPS IR AKTİF': no,
      'Aktif: 108° / 42cm': no,
      'AKTİF: 108° / 42cm': no,
      'Hafıza 1 (Aktif)': no,
      'CAN: SEAT_CTRL_01': no,
      'DTC Durumu: 0 Aktif Hata': no,
      'Toplam 28 ECU Taraması Tamamlandı • 0 Hata (DTC Clean) • 2 Bilgilendirme Notu': no,
      'Tolerans: ±0.04°': no,
      'Eksen: 0.00°': no,
      'Algılama: 150m': no,
      'ISO 2575 OK': no,
      'Hafıza Konumu: Pos-2': no,
      'Hafıza 1 (Aktif)': no,
      'Hafıza 1': no,
      'Hafıza 2': no,
      'Hafıza 3': no,
      'Dolu': no,
      'Isıtma Seviye 2': no,
      'Soğutma Seviye 1': no,
      'Klima Açık': 'Klima',
      '12.4m • Bağlı Hızı: 0 km/s': no,
      'Veri yok Açık': no,
      'Veri yok Açık (Havalandırma)': no,
      'Veri yok Yüksek Güneş': no,
      'Hafıza 1 (Ahmet)': text(d.driver, no),
      '24.5': no,
      '21.5': no,
      '22.5': no,
      '22.0°C': no,
      'Klima 22°C': no,
      'İki Bölge Auto': no,
      'Kabine Üfleme: 21.0°C': no,
      'Kademe 4 / 7 (Orta Seviye)': no,
      'Kademe 4 / 7': no,
      '%45 Açık': no,
      '%100 Açık': no,
      '%45 Açık (Havalandırma)': no,
      'PM2.5 İyonizer: Aktif': no,
      'Hafıza 1 (Ahmet)': text(d.driver, no),
      'Bulut Senkron': 'Bulut Senkron',
      'Tam Güncel': text(d.cloud, no),
      'KAYIT DEVAM EDİYOR': text(d.recordingText, no),
      '● KAYIT DEVAM EDİYOR': text(d.recordingText, no),
      'REC [CAM-01 Ön]': has && d.recording ? 'REC [CAM-01 Ön]' : no,
      '1080P 60FPS': no,
      '118 GB Boş': no,
      'Bugün, 12:45 • Kadıköy - Levent': no,
      'Çevre Dostu Hızlanma: Mükemmel': no,
      'Sistem Durumu: Tüm sistemler hazır ve güncel (OTA v2.4.1)': 'Sistem Durumu: ' + no,
      'Chery Lion 5.0 Akıllı Kokpit • En son güvenlik yaması 2 gün önce yüklendi.': no,
      'SİSTEMLER HAZIR': text(d.system, no),
      'CAN-FD Telematik • Tüm sistemler izleniyor': 'CAN-FD Telematik',
      'Ön Radar: Normal': no,
      'Geri Görüş & Kör Nokta: Aktif': no,
      'Farlar Otomatik': no,
      'Kapılar Kilitli': text(d.doors, no),
      'Lastikler Dengeli': text(d.tpms, no),
      'Bagaj Kapalı': text(d.trunk, no),
      'Sürüş Modu: Eco / Konfor': 'Sürüş Modu: ' + text(d.driveMode, no),
      'Süspansiyon: Standart Yükseklik': no,
      'AWD ŞASİ KALİBRASYONU TAM': no,
      'Akıllı Güç Tasarrufu Aktif': no,
      'Şehir İçi Modu': no,
      '4 Lastik Dengeli': text(d.tpms, no),
      'Trip A: 42 km': no,
      'Servis: 7.779 km': no,
      '118 GB Boş': no,
      'SD Kart: 118 GB Boş': no,
      'Eco / Konfor': text(d.driveMode, no),
      'Standart Yükseklik': no,
      'Ö: 2.4 | 2.4': no,
      'A: 2.4 | 2.4': no,
      'NOMINAL': no,
      'NOMİNAL': no,
      'DOĞRULANDI & AKTİF': no,
      '0 Aktif Hata': no,
      '0 Hata': no,
      'DTC 0x0000': no,
      'Toplam 28 ECU Taraması Tamamlandı • 0 Hata (DTC Clean) • 2 Bilgilendirme Notu': no,
      '112 / 147 (%76.2)': no,
      '112 / 147': no,
      '28 ECU Düğümü Çevrimiçi': no,
      '50 km/s': no,
      '12.4m': no,
      '12.4m • Bağlı Hızı: 0 km/s': no,
      '%0 Kirlilik (Temiz)': no,
      'Mükemmel (Nominal)': no,
      '60.0 FPS Senkron': no,
      '%18.4 (120 μs Gecikme)': no,
      '%0.00 (Hatasız)': no,
      'AES-256 Aktif': no,
      'Doğrulandı (Valid)': no,
      '%96': no,
      '%70': no,
      '%99': no,
      '108° (Dikey Destek)': no,
      '42 cm (%60 Seviye)': no,
      '4-Yönlü: Konfor +2': no,
      'Sol +3° / Sağ -2°': no,
      'Aktif: 108° / 42cm': no,
      'Son Mola: 1 sa 15 dk önce': no,
      'Ön Cam / Yol %99 Odak': no,
      'Normal Belirti Yok': no
    };
    setTexts(map);
    var textNodes = [];
    var tw = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null);
    while (tw.nextNode()) {
      var n = tw.currentNode;
      if (n.parentElement && n.parentElement.tagName !== 'SCRIPT' && n.parentElement.tagName !== 'STYLE') textNodes.push(n);
    }
    var subs = [
      [/516(?=\s|$)/g, has ? text(d.rangeValue, no) : no],
      [/%100/g, has ? text(d.fuelShort, no) : no],
      [/2\.221/g, has ? text(d.odoValue, no) : no],
      [/42 km/g, has ? text(d.tripDistance, no) : no],
      [/7\.779/g, no],
      [/118 GB Boş/g, no],
      [/2[\.,]4/g, no]
    ];
    textNodes.forEach(function (n) {
      if (window.__cockpitMapped && window.__cockpitMapped.has(n)) return;
      var whole = (n.nodeValue || '').trim();
      var numericData = /^(?:%?\d[\d.,]*\s*(?:km\/s|km|bar|lt|dk|°C|°|cm|GB|L|Puan|FPS|m|ms|V|A|MHz|GHz|MP)|\d+\s*\/\s*\d+|0x[0-9a-f]+)$/i;
      if (numericData.test(whole) || /^EOL\s+0x/i.test(whole) || /^EOL\s+Veri yok/i.test(whole)
          || /^AKTİF\s*:/i.test(whole) || /^\[\d{2}:\d{2}:\d{2}/.test(whole)) {
        n.nodeValue = n.nodeValue.replace(whole, no);
      }
    });
    // Live speed nodes have a sibling unit label, so they can be updated without
    // touching step numbers, scores or diagnostic constants.
    document.querySelectorAll('span').forEach(function (unit) {
      if ((unit.textContent || '').trim() !== 'km/s') return;
      var parent = unit.parentElement;
      if (!parent) return;
      for (var i = 0; i < parent.childNodes.length; i++) {
        var child = parent.childNodes[i];
        if (child.nodeType === 3 && /^\s*[\d.,—-]+\s*$/.test(child.nodeValue)) {
          child.nodeValue = has ? text(d.speedValue, no) : no;
          break;
        }
      }
    });
  }
  window.applyCockpitData = apply;
  apply({ connected: false });
  try {
    if (window.Android && Android.getVehicleData) {
      var raw = Android.getVehicleData();
      if (raw) apply(JSON.parse(raw));
    }
  } catch (e) {}
})();
