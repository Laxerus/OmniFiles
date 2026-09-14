# OmniFiles

OmniFiles, Android için tek APK yaklaşımıyla geliştirilen modern bir dosya yöneticisi ve geliştirici araç setidir. Normal ortak depolama işlemlerini doğrudan Android API'leriyle, daha ileri dosya inceleme işlemlerini ise Android 11+ Kablosuz Hata Ayıklama üzerinden APK içine gömülü ADB istemcisiyle yapar.

**Shizuku, LADB veya çalışma anında bir PC zorunlu değildir.** Android'in güvenlik modeli yine geçerlidir; dahili ADB normalde `shell` kimliğiyle çalışır ve uygulama özel verilerine Android'in izin vermediği durumlarda OmniFiles bu sınırı aşmış gibi davranmaz.

## Geliştirme durumu

Aktif geliştirme sürümü `0.8.0-dev` olarak sabit tutulur. Normal özellik ve hata düzeltmeleri sürüm numarasını otomatik ilerletmez.

Repository kökündeki normal Android/Gradle ağacı kaynak kodun tek güncel kaynağıdır. `.source/` klasörü yalnızca tarihsel kurtarma snapshot'ı olarak korunur ve aktif APK build'i tarafından kullanılmaz.

## Mevcut özellikler

- Android 11+ için **Tüm dosyalara erişim** ayar akışı; eski Android sürümlerinde uygun legacy izin akışı.
- Ortak depolama için güvenli yerel dosya tarayıcısı.
- Yerel ve ADB tarayıcılarında **arama**, **ad/tarih/boyut sıralama**, klasörleri üstte tutma ve **gizli öğe filtresi**.
- Canonical-path doğrulaması; depolama kökü dışına kaçışların engellenmesi.
- `Android`, `Android/data`, `Android/obb` ve `Android/media` gibi kritik dizin köklerinin kendisine yönelik tehlikeli mutasyonların engellenmesi.
- Dosyaları destekleyen uygulamalarda açmak için güvenli `FileProvider` paylaşımı.
- Doğrudan kalıcı silme yerine uygulama içi güvenli çöp alanına taşıma.
- APK içine gömülü `Kadb 2.1.4` ile Kablosuz ADB eşleştirme ve bağlantı.
- mDNS ile ADB uç noktası keşfi; pair/connect portlarının aynı cihaz host'u ile eşleştirilmesi.
- Daha önce eşleştirilmiş cihaza tekrar kod istemeden **yalnızca bağlan** akışı.
- ADB klasör listeleme, dosya önizleme ve Android'in belge seçicisine güvenli dışa aktarma.
- Geçici ADB önizleme dosyalarının yaşa göre cache temizliği.
- **Save Scout** ile üçüncü taraf paketlerini listeleme ve erişilebilir standart oyun/save konumlarını tarama.
- Save Scout'ta `SaveGames`, `Saved`, `userdata`, `profiles`, `worlds`, `UE4Game` gibi yaygın klasör adlarını sınırlı ve güvenli biçimde öne çıkarma.
- **SQLite Studio** ile çalışma kopyasında tablo/satır görüntüleme ve uygun hücreleri düzenleme.
- BLOB hücrelerini ve güvenli rowid düzenlemesi olmayan `WITHOUT ROWID` tabloları salt okunur tutma.
- SQLite kaydında önce bütünlük kontrolü, ardından kaynak yazımı; yazılan dosyayı yeniden okuyup **boyut + SHA-256 + SQLite integrity** doğrulaması ve başarısızlıkta yedekten geri yükleme denemesi.
- Material 3 tabanı ve Android 15/16 edge-to-edge uyumlu ortak Activity altyapısı.

## Güvenlik yaklaşımı

OmniFiles, Android sandbox'ını atlatıyormuş gibi davranmaz. Kablosuz ADB kullanıcı tarafından Android ayarlarından açıkça etkinleştirilmeli ve eşleştirilmelidir. Root tespiti yalnızca durum bilgisi içindir; uygulama kendiliğinden `su` başlatmaz. Shell/path girdileri ayrı doğrulama katmanlarından geçirilir ve sembolik bağlantı önizlemeleri ADB tarayıcısında engellenir.

SQLite düzenleme doğrudan kaynak üzerinde yapılmaz. Önce uygulama cache alanında çalışma ve yedek kopyaları oluşturulur; değişiklikler kullanıcı kaydetmeden kaynak URI'ye yazılmaz.

## Build ve test

Gereksinimler:

- JDK 17+
- Android SDK 36
- Android Gradle Plugin 8.10.1
- Gradle 8.11.1
- Kotlin 2.4.0

Aktif GitHub Actions akışı doğrudan repository kökündeki güncel kaynak ağacını kullanır ve şu gate'leri uygular:

1. `scripts/source_sanity.py`
2. `:app:testDebugUnitTest`
3. `:app:lintDebug`
4. `:app:assembleDebug`
5. APK ZIP bütünlük kontrolü ve SHA-256 çıktısı

`source_sanity.py`, aktif workflow'un eski `.source` snapshot'ını yeniden build kaynağı yapmasını özellikle hata kabul eder.

## Ana kaynak alanları

- `app/src/main/java/dev/laxerus/omnifiles/access` — depolama erişimi ve erişim durumu
- `app/src/main/java/dev/laxerus/omnifiles/adb` — Kablosuz ADB, mDNS ve uzak yol politikaları
- `app/src/main/java/dev/laxerus/omnifiles/fs` — yerel yol güvenliği ve çöp yönetimi
- `app/src/main/java/dev/laxerus/omnifiles/scout` — Save Scout
- `app/src/main/java/dev/laxerus/omnifiles/sqlite` — SQLite Studio çalışma alanı
- `app/src/main/java/dev/laxerus/omnifiles/ui` — Activity ve liste arayüzleri
- `app/src/test` — unit testler
- `scripts/source_sanity.py` — hızlı kaynak ve CI regresyon kontrolleri
