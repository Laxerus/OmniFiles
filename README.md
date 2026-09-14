# OmniFiles

OmniFiles, Android için tek APK yaklaşımıyla geliştirilen modern bir dosya yöneticisi ve geliştirici araç setidir. Normal ortak depolama işlemlerini doğrudan Android API'leriyle, daha ileri dosya inceleme işlemlerini ise Android 11+ Kablosuz Hata Ayıklama üzerinden APK içine gömülü ADB istemcisiyle yapar.

**Shizuku, LADB veya çalışma anında bir PC zorunlu değildir.** Android'in güvenlik modeli yine geçerlidir; dahili ADB normalde `shell` kimliğiyle çalışır ve uygulama özel verilerine Android'in izin vermediği durumlarda OmniFiles bu sınırı aşmış gibi davranmaz.

## Geliştirme durumu

Aktif geliştirme sürümü `0.8.0-dev` olarak sabit tutulur. Normal özellik ve hata düzeltmeleri sürüm numarasını otomatik ilerletmez.

Repository kökündeki normal Android/Gradle ağacı kaynak kodun tek güncel kaynağıdır. `.source/` klasörü yalnızca tarihsel kurtarma snapshot'ı olarak korunur ve aktif APK build'i tarafından kullanılmaz.

## Güncel APK

En güncel debug APK, repository'nin `main` dalından Render build hattıyla üretilir:

- [OmniFiles-0.8.0-dev-debug.apk](https://omnifiles-apk.onrender.com/OmniFiles-0.8.0-dev-debug.apk)
- [SHA-256](https://omnifiles-apk.onrender.com/OmniFiles-0.8.0-dev-debug.apk.sha256)

## Mevcut özellikler

- Android 11+ için **Tüm dosyalara erişim** ayar akışı; eski Android sürümlerinde uygun legacy izin akışı.
- Ana ekranda ortak depolamanın **kullanılan / toplam / boş alanını ve doluluk yüzdesini** gösteren canlı depolama sağlık göstergesi.
- Ana ekranda kayıtlı ADB uç noktasını yalnız yapılandırılmış kabul etmek yerine gerçek shell probe ile **canlı bağlantı sağlığı** kontrolü.
- Ortak depolama için güvenli yerel dosya tarayıcısı.
- Yerel tarayıcıda dosya ve klasörler için **kopyala / taşı / hedef klasöre yapıştır** akışı.
- Kopyalamada mevcut kullanıcı dosyasının üzerine yazmama; isim çakışmasında güvenli yeni ad üretme.
- Kopyaları önce gizli staging alanında tamamlama; başarıdan önce yarım hedef dosya/klasör göstermeme.
- Kopyalanan her dosyada **boyut + SHA-256 içerik doğrulaması**; doğrulama başarısızsa staging çıktısını geri alma.
- Ani kapanma veya süreç ölümü nedeniyle kalabilecek yeni nesil transfer staging öğelerini hedef klasörde sonraki aktarım öncesi **yaş + ad içi zaman damgası + gerçek mtime + canonical-path** kontrolleriyle güvenli biçimde temizleme.
- Staging temizliğinde yalnız OmniFiles'ın `.omnifiles-transfer-v2-<zaman>-<token>` biçimini kabul etme; taze, eski-format veya bozuk/benzer isimli öğelere otomatik dokunmama.
- Klasörün kendi altına kopyalanmasını/taşınmasını ve depolama kökünün tamamının yanlışlıkla transfer edilmesini engelleyen path politikaları.
- Dosya/klasör adlarında yol ayırıcılarına ek olarak satır sonu ve kontrol karakterlerini reddederek yanıltıcı adları engelleme.
- Büyük veya çok öğeli transferlerde ana UI thread'ini bloklamayan coroutine tabanlı dosya işlemleri ve işlem göstergesi.
- Yerel ve ADB tarayıcılarında **arama**, **ad/tarih/boyut sıralama**, klasörleri üstte tutma ve **gizli öğe filtresi**.
- Yerel/ADB tarayıcılarında klasör, arama, sıralama ve gizli öğe durumunun ekran döndürmelerinde korunması.
- Dosya satırlarında uzun basmaya ek olarak erişilebilir, doğrudan tıklanabilir işlem düğmesi.
- Canonical-path doğrulaması; depolama kökü dışına kaçışların engellenmesi.
- `Android`, `Android/data`, `Android/obb` ve `Android/media` gibi kritik dizin köklerinin kendisine yönelik tehlikeli mutasyonların engellenmesi.
- Dosyaları destekleyen uygulamalarda açmak için güvenli `FileProvider` paylaşımı.
- Doğrudan kalıcı silme yerine uygulama içi güvenli çöp alanına taşıma.
- Bağımsız **SHA-256 Doğrulama** aracı: Android belge seçiciden herhangi bir dosyayı salt okunur açıp hash üretme, dosya adı/boyut/MIME bilgisini gösterme ve özeti panoya kopyalama.
- APK içine gömülü `Kadb 2.1.4` ile Kablosuz ADB eşleştirme ve bağlantı.
- mDNS ile ADB uç noktası keşfi; pair/connect portlarının aynı cihaz host'u ile eşleştirilmesi.
- Daha önce eşleştirilmiş cihaza tekrar kod istemeden **yalnızca bağlan** akışı.
- ADB klasör listeleme, dosya önizleme, doğrudan paylaşım ve Android'in belge seçicisine güvenli dışa aktarma.
- ADB dosya çekimlerinde aktarım öncesi/sonrası **boyut + mtime + mod snapshot doğrulaması**; uzak dosya aktarım sırasında değişirse yerel geçici kopyayı reddetme ve silme.
- ADB tarayıcısında yükleme durumunun listeden bağımsız progress göstergesiyle yönetilmesi; başarılı yüklemeden sonra takılı kalan “yükleniyor” durumunun engellenmesi.
- Geçici ADB önizleme/dışa aktarma dosyalarının iptal, başarı, Activity yeniden oluşturma ve yaşa bağlı cache temizliği akışlarında yönetilmesi.
- **Save Scout** ile üçüncü taraf paketlerini listeleme ve erişilebilir standart oyun/save konumlarını tarama.
- Save Scout'ta `SaveGames`, `Saved`, `userdata`, `profiles`, `worlds`, `UE4Game` gibi yaygın klasör adlarını sınırlı ve güvenli biçimde öne çıkarma.
- **SQLite Studio** ile çalışma kopyasında tablo/satır görüntüleme ve uygun hücreleri düzenleme.
- BLOB hücrelerini ve güvenli rowid düzenlemesi olmayan `WITHOUT ROWID` tabloları salt okunur tutma.
- SQLite kaydında önce bütünlük kontrolü, ardından kaynak yazımı; yazılan dosyayı yeniden okuyup **boyut + SHA-256 + SQLite integrity** doğrulaması ve başarısızlıkta yedekten geri yükleme denemesi.
- Material 3 tabanı ve Android 15/16 edge-to-edge uyumlu ortak Activity altyapısı.

## Güvenlik yaklaşımı

OmniFiles, Android sandbox'ını atlatıyormuş gibi davranmaz. Kablosuz ADB kullanıcı tarafından Android ayarlarından açıkça etkinleştirilmeli ve eşleştirilmelidir. Root tespiti yalnızca durum bilgisi içindir; uygulama kendiliğinden `su` başlatmaz. Shell/path girdileri ayrı doğrulama katmanlarından geçirilir ve sembolik bağlantı önizlemeleri ADB tarayıcısında engellenir.

Yerel kopyala/taşı katmanı canonical path doğrulamasından geçer. Transfer hedefinde sessiz overwrite yapılmaz; klasör kendi altına gönderilemez. Kopya önce aynı hedef klasörde gizli bir staging öğesine yazılır. Her dosyanın kaynak akışından hesaplanan SHA-256 değeri staging kopyasının tekrar okunmasıyla doğrulanır; ancak bundan sonra staging öğesi görünür hedef adına geçirilir. Kopya sırasında hata oluşursa bu işlem tarafından yeni oluşturulan kısmi staging öğeleri geri alınmaya çalışılır. Taşıma farklı dosya sistemi nedeniyle doğrudan rename kullanamazsa aynı doğrulanmış kopya akışına düşer ve hedef kopya doğrulanmadan eski konumu temizlemez.

Yeni staging adları oluşturulma zamanını içerir. Sonraki kopyala/taşı işleminde hedef klasör taranırken ancak ad içindeki zaman damgası ve dosya sistemindeki değiştirilme zamanı varsayılan altı saatlik eşiği birlikte aşmışsa temizlik adayı olur. Aday ağacı silinmeden önce canonical/direct-entry politikasıyla tamamen doğrulanır; sembolik bağlantı veya güvenli alan dışına yönlenme görülürse otomatik temizleme yapılmaz.

ADB pull akışı, mümkün olduğunda uzak dosyayı aynı parent dizininden transfer öncesi ve sonrası tekrar stat ederek boyut, değiştirilme zamanı ve mode bilgisini karşılaştırır. Dosya aktarım sırasında değişmiş veya kaybolmuşsa önizleme/dışa aktarma kopyası güvenilir kabul edilmez ve silinir.

SHA-256 aracı seçilen belgeyi değiştirmez; dosyayı yalnızca `ContentResolver` üzerinden salt okunur akış olarak okur. Hash hesaplaması UI thread'i dışında yapılır ve ekran döndürmede tamamlanmış sonuç korunur.

SQLite düzenleme doğrudan kaynak üzerinde yapılmaz. Önce uygulama cache alanında çalışma ve yedek kopyaları oluşturulur; değişiklikler kullanıcı kaydetmeden kaynak URI'ye yazılmaz.

## Build ve test

Gereksinimler:

- JDK 17+
- Android SDK 37.0
- Android Build Tools 36.0.0
- Android Gradle Plugin 9.1.1
- Gradle 9.3.1
- Kotlin 2.4.0

Aktif GitHub Actions akışı doğrudan repository kökündeki güncel kaynak ağacını kullanır ve şu gate'leri uygular:

1. `scripts/source_sanity.py`
2. `:app:testDebugUnitTest`
3. `:app:lintDebug`
4. `:app:assembleDebug`
5. APK ZIP bütünlük kontrolü ve SHA-256 çıktısı

`FileOperationsTest`, klasör oluşturma/yeniden adlandırmaya ek olarak dosya kopyalama, binary içerik doğruluğu, staging commit temizliği, stale staging kurtarma filtresi, taze staging koruması, klasör ağacı kopyalama, isim çakışması, klasörün kendi içine transfer edilmesinin engellenmesi ve taşıma davranışını doğrular. `DigestUtilsTest`, bağımsız SHA-256 aracının bilinen test vektörünü ve binary byte dönüşümünü doğrular.

Render build hattı, GitHub-hosted runner erişilemediğinde aynı `main` kaynağından taşınabilir Android toolchain kurarak APK'yı üretir ve sabit indirme adresinde yayınlar. Render hattı da unit test, Android Lint ve APK assemble kapılarını geçmeden artifact yayınlamaz.

`source_sanity.py`, aktif workflow'un eski `.source` snapshot'ını yeniden build kaynağı yapmasını, deprecated geri navigasyonunu, sessiz overwrite davranışını, doğrulanmış staging transferinin kaldırılmasını, ADB pull snapshot doğrulamasının kaybolmasını, checksum aracının sökülmesini veya güvenli transfer wiring'inin bozulmasını hata kabul eder.

## Ana kaynak alanları

- `app/src/main/java/dev/laxerus/omnifiles/access` — depolama erişimi ve erişim durumu
- `app/src/main/java/dev/laxerus/omnifiles/adb` — Kablosuz ADB, mDNS, canlı health probe ve doğrulanmış uzak dosya pull politikaları
- `app/src/main/java/dev/laxerus/omnifiles/fs` — yerel yol güvenliği, SHA-256 yardımcıları, doğrulanmış transfer işlemleri ve çöp yönetimi
- `app/src/main/java/dev/laxerus/omnifiles/scout` — Save Scout
- `app/src/main/java/dev/laxerus/omnifiles/sqlite` — SQLite Studio çalışma alanı
- `app/src/main/java/dev/laxerus/omnifiles/ui` — Activity ve liste arayüzleri
- `app/src/test` — unit testler
- `scripts/source_sanity.py` — hızlı kaynak ve CI regresyon kontrolleri
