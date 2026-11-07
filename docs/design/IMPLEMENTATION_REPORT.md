# Cockpit Telematics — Uygulama Raporu (F0–F9)

Tarih: 2026-10-30 · Emülatör: `MapControl_Tiggo8_156` (2560×1440, 240 dpi)
APK: `com.eyups.roaddeck` (namespace `com.mapcontrol`), debug. Son ölçüm: 74 MB.

## Faz sonuçları

| Faz | Durum | Özet |
|---|---|---|
| F0 | Tamam | `screen_inventory.md`, `baseline/*.png`, `ui_style_calls.txt`; baseline soğuk açılış 188/165/142 ms, APK 75.79 MB |
| F1 | Tamam | `ck_*` renk/dimens token'ları, Inter (regular/semibold) + JetBrains Mono alt kümesi (Latin + Latin Ext-A), `CockpitTokens`, tipografi stilleri |
| F2 | Tamam | `ui/cockpit/` 18 bileşen + `CockpitGalleryActivity`; Türkçe diakritik ve 64dp hedefler görsel doğrulandı |
| F3 | Tamam | 56dp status bar, 96/240dp nav rail, 48dp chip alt sekme şeridi, 8 kolon `CockpitGrid`; TAB_* ve callback'ler korundu |
| F4 | Tamam | Tüm domain ekranları cockpit yüzey/tipografi/grid diline taşındı: Kokpit, Araç Bilgisi, Sürüş Modu, Yardım, Klima/Cam Tavan, Sürüş Karnesi, Kamera 360, Vale, Profiller, Yüz Tanıma, Ekolayzer, Hoşgeldin, Projection, Split Screen, Apps, Wi-Fi, Web, Roadmate, Log, Ayarlar |
| F5 | Tamam | Launcher dashboard 5/8 araç + 3/8 dikey yığın (hızlı erişim + medya) olarak yeniden kompoze edildi; lite panel/clock Inter+tnum. 3D model emülatörde yok |
| F6 | Tamam | Dialoglar CustomAlertDialog, cluster kartları Level 1, hazırlık halo kaldırıldı; welcome Inter; splash düz cockpit zemin + logo + ince accent progress, sonsuz animasyonlar kaldırıldı |
| F7 | Tamam | `DrivingAnalysisActivity` 56dp kabuk + token palet; `CameraActivity` token layout + 56dp üst çubuk; `DmsFaceProfileActivity` token yüzey/üst çubuk; probe activity'lerde ortak tipografi token pass |
| F8 | Tamam | UnusedResources: 0; eski cyber tema/stil, kullanılmayan string/color/dimen/drawable/layout temizlendi; ölü `UiStyles` yardımcıları kaldırıldı. Lint: 20 hata (platform API, baseline), 426 uyarı |
| F9 | Tamam | Build + 37/37 unit test + lint çalıştı; yeni Ana Sayfa ve profil ayar özeti ekran görüntüleri `after/`; ölçümler aşağıda |

## F9 ölçümleri

- `assembleDebug`: başarılı.
- `testDebugUnitTest`: **37/37 geçti**. `DrivingScoreTest` güncel algoritmaya göre (93/A) düzeltildi; iş mantığı değişmedi.
- Soğuk açılış (`am start -W`, 240 dpi): 345 / 471 / 480 ms (force-stop + 2 sn). Baseline 142–188 ms; debug emülatörde hedef dışı, FATAL/ANR yok.
- PSS: 65.642 KB (launcher kapalı); baseline kaydı yok.
- APK: 74 MB (baseline 75.79 MB altında). Outfit kaldırıldı, Inter 2 ağırlığa indirildi.
- TR ve EN locale: ana ekran, rail ve chip'lerde kırpılma yok; `ĞÜŞİÖÇ ığüşöç` galeride doğrulandı. 240 dpi ekran görüntüleri `after/*.png`.
- Launcher modu: prefs ile açıldı, dashboard çizildi, geri kapatıldı.
- TalkBack: rail `contentDescription` ve toggle durumları eklendi; tam audit yapılmadı.

## §11 varsayılanları

K1 Cyber kaldırıldı (pref okunur, yok sayılır) · K2 tek koyu tema (`values` = `values-night`) · K3 rail 96dp, genişletme prefs'te · K4 §3 tablosu · K5 Outfit kaldırıldı (font dosyası silindi) · K6 welcome içerikleri korunuyor, tipografi token'a taşınmadı · K7 web panel kapsam dışı · K8 hareket halinde metin kilidi uygulanmadı (mevcut repo dinleyicisi Home'da var, input ekranlarına bağlanmadı).

## Değişen ana dosyalar

- `app/src/main/java/com/mapcontrol/ui/cockpit/*` (yeni bileşenler, `CockpitTokens`, `CockpitNavModel`, `CockpitNavRail`, `CockpitStatusBar`, `CockpitSubTabBar`, `CockpitGrid`, galeri)
- `ui/activity/MainActivity.java` (kabuk entegrasyonu, alt sekme callback'i, rail genişletme, uyarı akışı)
- `ui/builder/TopBarBuilder.java`, `SideRailBuilder.java` (callback korunarak yeniden yazıldı)
- `ui/builder/HomeTabBuilder.java`, `VehicleInfoTabBuilder.java`, `DriveModeTabBuilder.java`, `AssistTabBuilder.java`, `ComfortTabBuilder.java`
- `ui/builder/SettingsTabBuilder.java` (tema bölümü bilgiye indirildi), `ProjectionTabBuilder.java` (Durdur secondary)
- `ui/theme/UiStyles.java` (64dp buton, segment tipografisi, blur no-op), `ThemeManager.java` (cyber kapalı)
- `util/DialogHelper.java` (CustomAlertDialog), `util/DisplayHelper.java` (hazırlık halo)
- `res/colors.xml`, `values-night/colors.xml`, `dimens.xml`, `themes.xml`, `font/*`, `strings_ui.xml` (tr/en/default)
- `ui/widget/LauncherLitePanelView.java` (Inter/tnum), `ui/activity/DrivingAnalysisActivity.java` (palet)
- `docs/design/after/*.png`, `docs/design/IMPLEMENTATION_REPORT.md`

## Sürücü domaini (2026-10-30)

- Sol menüye Araç'tan hemen sonra `Sürücü` domaini eklendi.
- `Sürücü profilleri` ve `Yüz Tanıma` Güvenlik domaininden buraya taşındı.
- Sürücü profilleri üç kolonlu kart düzenine geçirildi: aktif profil (accent çerçeve), diğer profiller, Yeni Profil/Misafir kartı.
- Profil kartlarında yüz tanıma, koltuk hafızası, kontakta uygulama ve ek ayar özeti var; detaylı koltuk/ek ayarlar düzenleme dialogunda korunuyor.

## F4 ek turlar (2026-10-30)

- `6a8d411` Kamera 360 menü/kart + Uygulama Yönetimi katalog/yerel satırlar.
- `35cddc5` all-caps butonlarda label-caps renk ezilmesi düzeltildi.
- `3623821` Sürüş Karnesi tipografi/empty card.
- `79f2951` Log görünürlük bug'ı + cockpit terminal/TTS.
- `bd2878e` Yüz Tanıma seçim/normal ekran kartları.
- `f31887d` Roadmate mock: 3/5 kolon, durum chip, aksiyon ikonları, footer.
- `c8f577d` Wi-Fi iki kart + empty state + network row token.
- `0f18284` Projection yüzey/kontrol tipografisi.
- `c15bed4` Kamera 360 kayıt/önizleme iç kontrol polish.
- DriverProfiles/Equalizer/Welcome/Settings/SplitScreen/Valet/Temperature yüzeyleri `level1` + token'a çekildi.

## F7/F8/F9 kapanış (2026-10-30)

- F7: `CameraActivity` layout'u `ck_*` yüzeyler, 56dp üst çubuk ve 72dp geri hedefiyle yeniden yazıldı. `DrivingAnalysisActivity` sabit 56dp başlık + geri hedefi, cyan accent ve gradyansız yüzeylere geçirildi. `DmsFaceProfileActivity` üst çubuğu 56dp, geri hedefi 72dp ve metin/surface renkleri token'a çekildi. Probe/test activity'lerinde ortak tipografi uygulandı.
- F8: lint `UnusedResources` 0'a indirildi; kullanılmayan 220 string, 70 renk, 29 dimen, 4 stil, 2 drawable, 1 layout, 1 xml, 2 integer, 1 array temizlendi. Ölü `UiStyles` yardımcıları ve eski cyber tema/stilleri kaldırıldı.
- F9: `assembleDebug` ✅, `testDebugUnitTest` 37/37 ✅, `lintDebug` çalıştı (20 hata tamamı baseline platform/API uyarıları; UnusedResources yok). APK 74 MB (baseline 75.79 MB altında). PSS 65.642 KB. Ekran görüntüleri: `after/f9_home_final.png`, `after/f9_profile_settings_summary.png`; mevcut F4/F5/F6 görüntüleri geçerli.
- Soğuk açılış: 345–480 ms (3 ölçüm; baseline 142–188 ms). Debug emülatörde splash/ilk kurulum etkisi; FATAL/ANR yok.

## Cam Tavan redesign (2026-10-31)

- Üç eşit sütun: canlı durum + manuel kontrol, otomasyon + modlar, hızlı kumanda + emniyet.
- Manuel `Tam Aç / Havalandır / Gölge / Tam Kapat` butonları `VehicleSunroofController` üzerinden çalışır; bağlantı yoksa toast.
- Canlı kartta CAN-BUS bağlantı rozeti; otomasyon kartında AKTİF/PASİF rozeti.
- Tüm panel/toggle/segment yüzeyleri cockpit tokenlarına taşındı.
- Açılışta cam tavan/güneşlik tercihleri korundu.
- Görsel: `docs/design/after/f4_sunroof_redesign.png`.

## Açık sorular / riskler

1. **F4 tamamlandı**: tüm domain ekranları CockpitCard/Grid, token yüzey ve tipografiye taşındı; web/QR, kamera iç kontrolleri, Roadmate mock kompozisyonu, Wi-Fi, Log, Yüz Tanıma ve Projection dahil.
2. **Soğuk açılış**: debug emülatörde 345–480 ms; baseline ±%10 hedefi aşılıyor. İş mantığına dokunmadan splash/ilk yerleşim maliyeti ayrı performans işi.
3. **APK/PSS**: APK baseline altında; PSS 65.642 KB, hedef bütçenin üzerinde değil.
5. **3D launcher**: emülatörde Filament modeli görünmüyor; gerçek araçta doğrulanmalı.
6. **TalkBack/locale**: tam erişilebilirlik taraması ve 1920×720 cluster görsel kontrolü araçta yapılmalı.
7. **F8 kapandı**: kullanılmayan kaynaklar temizlendi; lint hataları baseline platform API kaynaklı.

---

## UI Audit & Redesign Summary (2026-11-07)

- **1. Tur / temel:** `ck_*` tokenları DESIGN.md ile hizalandı; ortak bileşenlerde 64dp dokunma hedefi, 16dp kart radius'u, 14sp alt sınır; seçili segment ve dialog butonu kontrastı düzeltildi; `selected` semantiği eklendi.
- **2. Tur / AI slop:** Home 5/3 birincil-ikincil hiyerarşi; iç içe şarkı sözü paneli kaldırıldı. Camera 360 ortalanmış ikon kutusu ve 2×2 eşit kart yerine sola hizalı başlık + birincil `Canlı Önizleme` + 3 ikincil mod. Climate dış wrapper kartı, `SÜRÜCÜ/YOLCU` pill'leri ve ikon kutuları kaldırıldı; ADAS'taki tekrarlanan durum pill'leri ve Projection versiyon pill'i kaldırıldı.
- **3. Tur / harden:** TalkBack etiketleri (toggle, kart, stepper, seekbar, profil satırı), focus highlight, off-toggle 3:1 stroke, chevron kontrastı; Türkçe diakritik/1.3× font kontrolü; “Kamera modu seçin” otomotiv kopyası. DrivingAnalysis amber teması, Cam Tavan kullanıcı kararları ve Son Sürüş içeriği korundu.
- **Doğrulama:** `:app:assembleTts30Debug :app:testTts30DebugUnitTest :app:lintTts30Debug` başarılı (49 task); unit testler geçti; lint tamamlandı (92 legacy/OEM hatası, build durdurmuyor). `:app:assembleDebug`/`:app:testDebugUnitTest`/`:app:lintDebug` isimleri flavor'lar nedeniyle ambiguous.
- **Kanıt:** `docs/design/UI_AUDIT.md`, `docs/design/audit-round2/`, `docs/ui_audit_2026-11-07/`.
- **Açık P3:** Camera altındaki boş alan; Apps yıkıcı eylem hiyerarşisi; Voice Assistant paneli; Yüz Tanıma gate kartları; sistem/uygulama saati tekrarı; 3D/welcome/debug sanat renkleri.
