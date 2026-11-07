# RoadDeck UI Audit

**Cihaz/hedef:** Desay SV tabanlı Chery IVI, Android 11; emülatör 2560×1440 @ 240 dpi.
**Tasarım kaynağı:** `docs/design/DESIGN.md` (Cockpit Telematics System).
**Kapsam:** Kokpit ana kabuğu ve ana sekmeler; iş mantığı, CAN/VDBus komutları kapsam dışı.

---

## 1. Tur — P0/P1 temel düzeltmeler (özet)

- Dokunma hedefleri: ortak bileşenlerde minimum **64dp**; `CockpitToggle` görsel 40dp / hedef 72×64dp; chip, segmented, dialog ve legacy butonlar 64dp.
- Renkler: `values/colors.xml` + `values-night/colors.xml` semantik renkleri `ck_*` tokenlarına alias’landı; `ck_primary`, `ck_on_surface`, `ck_on_surface_variant`, `ck_outline_variant`, `ck_surface_lowest`, `ck_on_critical` eklendi.
- Tipografi: `StatusBadge` 14sp; `MetricView` label 14sp.
- Radius: kart/yüzeyler **16dp**; chip/pill’ler DESIGN gereği 24dp.
- Kokpit bileşenleri ve ana builder’larda token dışı hardcoded hex temizlendi.

---

## 2. Tur — AI Slop Temizliği ve Tasarım Denetimi

### 2.1 Gereksiz iç içe kartlar / wrapper’lar

| Alan | Önce | Sonra |
|---|---|---|
| Home / “Şimdi çalıyor” | Şarkı sözü paneli, medya kartının içinde ikinci bir koyu kart | Panel kaldırıldı; tek yüzey + 1px dikey ayırıcı |
| Climate / Sürücü & Yolcu | Dış kart içinde iki iç kart (kart içinde kart) | Dış wrapper kart kaldırıldı; başlık sayfada, iki bağımsız kural kartı |
| Climate / kural kartı | İkon, 44dp tinted kutu içinde | Düz 28dp ikon; kutu yok |
| Camera 360 / mod seçimi | Ortalanmış ikon kutusu + 2×2 eşit kart | Sol başlık + tam genişlik birincil kart + 3’lü ikincil satır |

### 2.2 Rastgele ikon kutuları, pill/glow/gradient temizliği

- **Camera 360:** Ortalanmış kamera ikon kutusu, 4 karttaki tinted yuvarlak ikon rozetleri ve ağır pill görünümü kaldırıldı. İkonlar düz; birincil kart accent border ile ayrışıyor.
- **Climate:** `SÜRÜCÜ` / `YOLCU` dekoratif pill’leri kaldırıldı; bölüm başlığı tarafı zaten söylüyor. Koltuk ikonlarının tinted kutuları kaldırıldı.
- **Yardım Sistemleri:** Her karttaki tekrarlanan `AYARLANMADI` / `AÇIK` durum pill’leri kaldırıldı; durum artık toggle’ın kendi pozisyonu ve etiketiyle okunuyor.
- **Yansıtma:** `İSO-HUD v2` versiyon pill’i kaldırıldı.
- **Home:** Araç durumu kartındaki dekoratif araç ikonu kaldırıldı.
- Gradient/glow: Kokpit yüzeylerinde dekoratif gradient/glow yok; `DrivingAnalysisActivity`’nin sıcak amber teması korunarak yalnız renk değerleri token’a bağlandı.

### 2.3 Ortalanmış jenerik başlıklar → sol hizalama

- Camera 360 açılış ekranındaki ortalanmış `Kamera 360` + ikon kutusu kaldırıldı; yerine içerik kolonunun solunda `Bir mod seçin` başlığı geldi.
- Home, Climate ve diğer kokpit sayfalarında başlıklar zaten sol hizalı; bu turda yalnız Camera 360 istisnası düzeltildi.

### 2.4 Eşit görsel ağırlık → birincil/ikincil hiyerarşi

- **Home:** 4/4 kolon → **5/3**. Medya kartı birincil (geniş, şarkı sözü okunur), araç durumu ikincil (dar, içerik yüksekliğinde). Araç durumu kartı artık sayfa yüksekliğine uzatılmıyor; 240dp gövde ile kendi doğal yüksekliğinde.
- **Camera 360:** `Canlı Önizleme` tam genişlik, accent border’lı birincil kart; `USB Kayıt`, `Araç İçi Kayıt`, `İzleme Ekranı` üç eşit ikincil kart. Önce dört eşit karttı.
- **Climate:** Isıtma/havalandırma eş kartları bilinçli olarak eşit bırakıldı; bunlar birbirinin alternatifi olan eş işlevler, biri diğerinin altında değil.

### 2.5 Polish

- Home medya kartı başlığı 3 satıra kadar; uzun parça adları kesilmiyor.
- Footer linkleri 64dp hedef + 15sp; dokunma hedefi korunuyor.
- Camera 360 ikincil kartları 3 sütun; 1120dp içerik kolonunda taşma yok.
- Climate bölüm başlıkları sayfa yüzeyinde; kart içinde kart hissi yok.
- Tüm yeni yüzeyler 16dp radius, 1px `ck_stroke`; accent yalnız seçili/birincil durumda.

---

## Korunan ürün kararları

- **DrivingAnalysisActivity:** amber/sıcak tema korunuyor; cyan’a çevrilmedi. Yalnız `BG/CARD/BORDER/AMBER/CYAN/TEXT/MUTED` değerleri aynı görünümü veren tokenlara bağlandı.
- **Cam Tavan:** kullanıcı kararları (başlangıç modu, gölgelik, hassasiyet, emniyet anahtarları) değiştirilmedi; yalnız genel radius/token/64dp kuralları uygulandı.
- **Son Sürüş:** kartın içeriği ve “Henüz sürüş kaydı yok.” boş durumu korunuyor; yalnız Home grid hiyerarşisi değişti.
- **CAN/VDBus:** hiçbir komut, ID veya iş mantığı değiştirilmedi.

---

## Doğrulama

- İstenen komut:
  `sh ./gradlew :app:assembleDebug :app:testDebugUnitTest`
  → **task çözümlemede kırılıyor**: `:app:testDebugUnitTest` flavor’lar nedeniyle ambiguous (`tts30`, `tts40`, `ttsService`). Kod hatası değil.
- Eşdeğer doğrulama:
  `sh ./gradlew :app:assembleTts30Debug :app:testTts30DebugUnitTest`
  → **BUILD SUCCESSFUL** (43 task), unit testler geçti.
- APK emülatöre kuruldu; Home, Camera 360 ve Climate ekranları görsel olarak kontrol edildi. `FATAL EXCEPTION` / `ANR` yok.
- Ekran görüntüleri: `docs/design/audit-round2/` (`home.png`, `camera-360.png`, `climate.png`, `assist.png`).
- Mevcut uyarılar: `minSdk 29 > targetSdk 28`, CMake SDK XML sürüm uyarıları (önceden var).

## Kalan işler

- Camera 360 altında geniş boş alan: gerçek kayıt/durum bilgisi eklenirse sayfa “thin” görünümden çıkar.
- Apps başlığındaki yıkıcı eylemlerin gruplanması ve casing’i.
- Voice Assistant dar sağ panel; Yüz Tanıma ilk kurulum kartlarında seçim göstergesi.
- Sistem saati + uygulama saati tekrarı (ürün kararı).
- 3D/welcome/debug ekranlarındaki sanat renkleri ve fullscreen lyrics light paleti bilinçli olarak token dışı bırakıldı.

---

## 3. Tur — Erişilebilirlik, kontrast ve kenar durumları (Harden)

### 3.1 TalkBack / contentDescription

- Toggle'lar: `CockpitToggle.setLabel()` + `stateDescription`; 8 ADAS anahtarı dahil tüm doğrudan kullanımlar etiketli.
- Kartlar: `CockpitCard.setCardOnClickListener()` başlıktan otomatik `contentDescription`; Camera 360 mod kartları başlık + alt açıklama; profil satırları profil adı.
- Kontroller: `CockpitStepper` +/- “Azalt/Artır”; iklim `SeekBar`'ları “Gecikme süresi / Sıcaklık eşiği / Koltuk seviyesi”; Wi-Fi/Web/Apps ikon butonları mevcut içerik açıklamalarını koruyor.
- Durum: seçili rail/chip/kart/segmented `setSelected`; toggle `checked` + stateDescription.

### 3.2 Metin taşmaları ve Türkçe diakritikler

- 2560×1440 ve `font_scale=1.3` kontrolü: Home medya başlığı 3 satır, Camera ikincil kartları 3 sütun, Klima etiketleri ve chip'ler taşmıyor.
- `Ş, Ğ, İ, ı, ç, ö, ü` içeren metinler `values-tr` kaynaklarından; büyük harf dönüşümleri Türkçe locale ile yapılıyor.
- Camera açılış başlığı jenerik “Bir mod seçin” → **“Kamera modu seçin”** (EN: “Select camera mode”).
- Alt metinler `maxLines` + ellipsis; uzun parça adı 3 satıra kadar gösteriliyor.

### 3.3 Kontrast ve odak

- Seçili segment metni `ck_on_accent` (7.62:1); dialog pozitif butonu aynı token.
- `ck_text_muted` yalnız dekoratif/disabled yüzeylerde; Settings ve Vale chevron'ları `ck_text_medium` (6.11:1) yapıldı.
- Kapalı toggle track'ine 1px `ck_text_medium` stroke: off durumu kart zemininden 3:1 ile ayrışıyor.
- Tüm ortak tıklanabilir bileşenlerde `setDefaultFocusHighlightEnabled(true)`; klavye/D-pad odağı görünür.
- Araç durumu etiketleri `ck_text_medium`; `BAGAJ/KAPUT` okunur.

### 3.4 Ekran bazlı düzeltme matrisi

| Ekran | P0/P1/P2 | Uygulanan düzeltme | Durum |
|---|---|---|---|
| Kokpit / Home | P1/P2 | 5/3 hiyerarşi; araç kartı doğal yükseklik; iç içe şarkı sözü paneli kaldırıldı; başlık 3 satır; footer 64dp | Çözüldü |
| Sürüş Modu | P1 | Seçili kart semantiği; yazım hatasında `ADVISORY`; bounded background retry; 64dp kartlar | Çözüldü |
| Yardım Sistemleri | P1/P2 | Toggle etiketleri + 64dp; tekrarlanan durum pill'leri kaldırıldı; kontrast | Çözüldü |
| Klima Otomasyonu | P1/P2 | Dış wrapper kart kaldırıldı; `SÜRÜCÜ/YOLCU` pill'leri ve ikon kutuları kaldırıldı; seekbar 64dp + etiket; master kapalıyken alt kontroller pasif | Çözüldü |
| Cam Tavan | P1 | 64dp hedefler, token renkleri, seçili segment kontrastı; kullanıcı kararları korundu | Çözüldü |
| Araç Bilgi | P1 | 28sp cümle taşması → `—` + 15sp caption; birimler; 14sp metrik etiketi | Çözüldü |
| Sürüş Karnesi | P1/P2 | 64dp/16dp/token; boş durum metni korundu | Çözüldü |
| Sürücü Profilleri | P2 | Tek primary (“Yeni profil”); “Eşitle” secondary; satır cd + focus | Çözüldü |
| Yüz Tanıma | P2 | “Hafıza Tipini Değiştir” i18n; “Sağdaki alandan” → “Aşağıdaki profil alanından” | Kısmi (gate kartı seçim göstergesi açık) |
| Kamera 360 | P1/P2 | Sol başlık; birincil/ikincil hiyerarşi; ikon kutuları kaldırıldı; cd + focus; “Kamera modu seçin” | Çözüldü |
| Vale Modu | P2 | Curfew kapalıyken saat alanları pasif; 64dp/16dp; chevron kontrastı | Çözüldü |
| Ekolayzer | P1/P2 | Segment kontrastı; 64dp; token yüzeyler | Çözüldü |
| Yansıtma | P2 | `İSO-HUD v2` pill'i kaldırıldı; 64dp; tokenlar | Çözüldü |
| Bölünmüş Ekran | P2 | Gesture hücreleri ripple + 64dp; çakışan hücreler daha az soluk | Çözüldü |
| Bağlantı / Apps | P2 | Tokenlar; alt sekme hizası; yıkıcı başlık eylemleri hâlâ açık | Kısmi |
| Wi-Fi | P1 | Gerçek bağlantı durumu; kapalıyken tarama guard'ı; `■` glifi kaldırıldı | Çözüldü |
| Web | P2 | `▶` yerine play/stop drawable; “Talimatlar” i18n; 64dp | Çözüldü |
| Ayarlar | P1/P2 | Seçili segment kontrastı; tekrar başlık kaldırıldı; tema metni; 64dp | Çözüldü |
| Dialoglar | P1/P2 | Scrim; pozitif buton kontrastı; 64dp buton hedefi | Çözüldü |

### 3.5 Bulgu özeti

- **P0:** 0.
- **P1 (7):** etiketsiz/40dp toggle; 2.36:1 seçili segment; seçim semantiği yok; Araç Bilgi metrik taşması; sürüş modu yanlış başarı + ana thread retry; Wi-Fi çelişkili durum; EN modda hardcoded TR metinler. **Tümü çözüldü.**
- **P2 (18):** alt sekme hizası, içerik genişliği, genişleyen rail, ripple, accent bar, modal scrim, üçlü başlık, araç etiketi kontrastı, ebeveyn/çocuk kontrol durumu, legacy tipografi clamp, Apps başlığı, Voice Assistant paneli, Yüz Tanıma kopyası/kartları, iki primary, rozet semantiği, Unicode glifler, saat hizası, sabit px. **Çoğu çözüldü; Apps başlığı, Voice paneli, Yüz Tanıma gate kartları açık.**
- **P3 (açık):** Camera 360 altındaki boş alan; sistem saati + uygulama saati tekrarı; 3D/welcome/debug sanat renkleri; fullscreen lyrics light paleti; Voice Assistant dar panel.

### 3.6 Son doğrulama (3. Tur)

- `sh ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` → flavor task ambiguity nedeniyle task çözümlemede durur.
- Eşdeğer: `sh ./gradlew :app:assembleTts30Debug :app:testTts30DebugUnitTest :app:lintTts30Debug` → **BUILD SUCCESSFUL** (49 task), unit testler geçti, lint tamamlandı.
- Lint raporu: 573 warning / 92 error; hatalar legacy/OEM kaynaklı (`MissingTranslation` 67, `WrongConstant` 7, `UnsafeImplicitIntentLaunch` 7, `ProtectedPermissions` 3, `StringFormat*` 6, `NewApi` 1, ChromeOS 1). Bu UI değişikliklerinden kaynaklanan yeni hata yok; proje lint yapılandırması build'i durdurmuyor.
- Emülatörde Home, Camera 360, Climate, Yardım ekranları kontrol edildi; `FATAL/ANR` yok.
