# OmniFiles

OmniFiles, Android için tek APK yaklaşımıyla geliştirilen modern bir dosya yöneticisi ve geliştirici araç setidir. Normal ortak depolama işlemlerini Android API'leriyle, daha ileri dosya inceleme işlemlerini ise Android 11+ Kablosuz Hata Ayıklama üzerinden APK içine gömülü ADB istemcisiyle yapar.

**Shizuku, LADB veya çalışma anında bir PC zorunlu değildir.** Android'in güvenlik modeli geçerliliğini korur; dahili ADB normalde `shell` kimliğiyle çalışır ve OmniFiles erişemediği uygulama özel verilerine erişebiliyormuş gibi davranmaz.

## Geliştirme durumu

Aktif geliştirme sürümü `0.8.0-dev` olarak sabit tutulur. Normal özellik ve hata düzeltmeleri sürüm numarasını otomatik ilerletmez.

Repository kökündeki normal Android/Gradle ağacı kaynak kodun tek güncel kaynağıdır. `.source/` yalnız tarihsel kurtarma snapshot'ıdır ve aktif APK build'inde kullanılmaz.

## Güncel APK

En güncel debug APK `main` dalından Render build hattıyla üretilir:

- [OmniFiles-0.8.0-dev-debug.apk](https://omnifiles-apk.onrender.com/OmniFiles-0.8.0-dev-debug.apk)
- [SHA-256](https://omnifiles-apk.onrender.com/OmniFiles-0.8.0-dev-debug.apk.sha256)

## Mevcut özellikler

- Android 11+ için **Tüm dosyalara erişim** akışı ve eski Android sürümlerinde uygun legacy izin davranışı.
- Ana ekranda ortak depolamanın **kullanılan / toplam / boş alanını ve doluluk yüzdesini** gösteren canlı depolama göstergesi.
- Kayıtlı ADB uç noktasını gerçek shell probe ile doğrulayan **canlı ADB bağlantı sağlığı**.
- Ortak depolama için güvenli yerel dosya tarayıcısı; arama, sıralama, gizli öğeler, favoriler, seçim ve toplu işlemler.
- Yerel normal dosya satırlarında doğrudan **SHA** düğmesi; dosyayı ayrı belge seçici açmadan güvenli `FileProvider` URI'siyle SHA-256 ekranına gönderme.
- Dosya ve klasörlerde **kopyala / taşı / hedefe yapıştır** akışı; sessiz overwrite yapmama ve isim çakışmasında güvenli yeni ad üretme.
- Kopya/move-fallback aktarımında gerçek **byte tabanlı ilerleme**, Material progress penceresi ve o anda işlenen öğe için güvenli iptal.
- Kullanıcı iptalinde boyut ön taraması, klasör traversal'ı, 64 KiB kopyalama blokları ve hedef SHA-256 doğrulaması arasında cancellation checkpoint'leri.
- İptal veya hata halinde staging rollback; yarım kopyanın kullanıcı hedef adına commit edilmemesi.
- Kopyaları önce gizli staging alanında tamamlama; her dosyada **boyut + SHA-256 doğrulaması**.
- Yerel dosya kopyasında kaynak için **boyut + mtime snapshot doğrulaması**; kaynak aktarım sırasında değişirse staging kopyasını reddetme.
- Kaynak klasörlerde **mtime + çocuk adları snapshot doğrulaması**; aktarım sırasında yapısal değişiklikleri commit öncesi yakalama.
- Klasör kopyalama ve move-fallback kaynak temizliğinde recursive çağrı yerine **iteratif `ArrayDeque` traversal**.
- Ani kapanma nedeniyle kalabilecek `.omnifiles-transfer-v2-*` staging öğelerini yalnız yaş, gömülü zaman damgası, gerçek mtime ve canonical-path şartları birlikte sağlanırsa temizleme.
- Klasörün kendi altına transferini, depolama kökünün tamamını transfer etmeyi ve güvenli alan dışına path kaçışını engelleyen politikalar.
- Dosya/klasör adlarında yol ayırıcıları, NUL, satır sonu ve kontrol karakterlerini reddetme.
- **Depolama Analizi**: ortak depolamayı salt okunur ve stack-safe biçimde tarayıp en büyük dosya ve klasörleri gösterme.
- Depolama Analizi taramasında **40.000 öğelik sert güvenlik sınırı**, kullanıcı iptali, canonical/direct-entry kontrolü ve symlink izlememe.
- Analiz sırasında bütün 40.000 sonucu RAM'de biriktirmek yerine yalnız gösterilecek **en büyük N dosya ve N klasör adayını** tutma.
- Depolama Analizi sonuçlarında dosya açma, paylaşma, SHA-256 hesaplama, ayrıntıları görme ve yolu kopyalama; klasörlerde ayrıntı/yol işlemleri.
- Analiz sonucu üzerinde işlem yapılacağı anda yolu tekrar direct-entry/canonical politikasıyla doğrulama; taramadan sonra taşınan/değişen güvensiz öğeyi reddetme.
- Büyük veya çok öğeli işlemlerde UI thread'ini bloklamayan coroutine tabanlı iş akışları.
- Güvenli `FileProvider` ile dosya açma/paylaşma ve doğrudan kalıcı silme yerine uygulama içi çöp alanı.
- **SHA-256 Doğrulama** aracı: Android belge seçiciden salt okunur dosya açma, hash üretme ve panoya kopyalama.
- SHA-256 ekranının OmniFiles içindeki güvenli `FileProvider` URI'lerini doğrudan kabul etmesi ve Activity yeniden oluşturulurken seçili URI'yi koruması.
- APK içine gömülü `Kadb 2.1.4` ile Kablosuz ADB eşleştirme/bağlantı ve mDNS keşfi.
- ADB klasör listeleme, dosya önizleme, doğrudan paylaşım ve Android belge seçicisine dışa aktarma.
- ADB pull işlemlerinde transfer öncesi/sonrası **boyut + mtime + mode snapshot doğrulaması**.
- ADB normal dosya satırlarında doğrudan **SHA** düğmesi; uzak dosyayı doğrulanmış pull akışıyla geçici cache'e alıp yerelde SHA-256 hesaplama.
- ADB checksum geçici kopyasını hash sonrasında silme ve süreç çökmesiyle kalmış checksum cache dosyalarını yaş sınırıyla temizleme.
- ADB yol bileşenlerinde NUL/satır sonu/ISO kontrol karakterlerini ve aşırı uzun dosya adlarını reddetme; güvenli olmayan uzak girişleri liste katmanına taşımama.
- **Save Scout** ile erişilebilir standart oyun/save konumlarını sınırlı ve güvenli biçimde tarama.
- **SQLite Studio** ile çalışma kopyasında tablo/satır görüntüleme ve uygun hücreleri düzenleme.
- SQLite kaydında bütünlük kontrolü, kaynak yazımı sonrası **boyut + SHA-256 + SQLite integrity** doğrulaması ve hata halinde geri yükleme denemesi.
- Material 3 tabanı ve Android 15/16 edge-to-edge uyumlu ortak Activity altyapısı.

## Depolama Analizi güvenlik modeli

Storage Analyzer ortak depolama kökünü değiştirmez; yalnız dosya metadata'sını ve dosya boyutlarını okur. Tarama recursive fonksiyon çağrıları yerine explicit bir `ArrayDeque` iş kuyruğuyla ilerler. Her öğe `FilePathPolicy.requireDirectEntry` üzerinden doğrulandığı için sembolik bağlantı veya canonical path kaçışı taranmaya devam edilmez.

Tarama varsayılan olarak en fazla **40.000 öğe** işler ve en büyük 20 dosya ile 20 klasörü gösterir. Sonuç seçimi bounded top-N mantığıyla yapılır; taranan her öğenin `Entry` nesnesini sonuç listesinde tutmak yerine yalnız sonuç limitine girebilecek adaylar bellekte tutulur. Klasör boyutları çocuk dosya ve klasörlerin taranan toplamlarından post-order olarak hesaplanır. Kullanıcı iptal ettiğinde tarayıcı yeni öğe işlemeyi bırakır ve güvenilir kısmi sonuç döndürür; güvenlik sınırında durduğunda sonuç açıkça kısmi olarak işaretlenir.

Sonuç kartına dokunulduğunda dosyanın tarama anındaki yoluna körlemesine güvenilmez. Öğenin hâlâ ortak depolama kökü içinde doğrudan bir giriş olduğu, mevcut olduğu ve dosya/klasör tipinin beklenen türle eşleştiği yeniden doğrulanır. Ancak bundan sonra `FileProvider` URI'si oluşturularak açma/paylaşma veya SHA-256 ekranına geçiş yapılır. Uzun basma yolu güvenli biçimde panoya kopyalar.

## Transfer güvenliği

Yerel kopyala/taşı katmanı canonical path doğrulamasından geçer. Transfer hedefinde sessiz overwrite yapılmaz ve klasör kendi altına gönderilemez. Kopya önce aynı hedef klasörde gizli bir staging öğesine yazılır. Her dosyanın kaynak akışından hesaplanan SHA-256 değeri staging kopyasının tekrar okunmasıyla doğrulanır; ayrıca kaynak dosyanın boyut ve değiştirilme zamanı işlem başı/sonunda karşılaştırılır.

Aktarım başlamadan önce toplam dosya byte miktarı stack-safe biçimde hesaplanır. `FileOperations.copy` ve fallback gerektiren `move` işlemleri 64 KiB bloklarda ilerleme üretir. Bu telemetri `TransferRuntime` üzerinden yaşam döngüsüne dayanıklı biçimde aktif `OmniActivity` ekranına aktarılır; Material progress penceresi dosya adını, kopyalanan/toplam byte miktarını ve yüzdeyi gösterir. Ekran yeniden oluşturulursa aktif transfer snapshot'ı yeni Activity'ye tekrar verilir.

Progress penceresindeki **Bu öğeyi iptal et** düğmesi yalnız o anda işlenen transfer öğesini durdurur. Çoklu seçim kuyruğunun tamamını otomatik iptal etmez. İptal isteği boyut taramasında, iteratif klasör kuyruğunda, her 64 KiB dosya bloğunda, destination SHA-256 doğrulamasında ve staging commitinden hemen önce kontrol edilir. `TransferCancelledException` normal hata/rollback yolunu kullandığı için yarım staging ağacı temizlenir ve görünür hedef adına taşınmaz. Move doğrudan aynı dosya sistemi içinde atomik `renameTo` ile tamamlanabiliyorsa işlem çok kısa olduğu için iptal penceresi pratikte kullanıcı etkileşiminden önce tamamlanabilir.

Kaynak klasörlerin değiştirilme zamanı ve doğrudan çocuk adları final commit öncesi tekrar doğrulanır. Klasör kopyalama ve move-fallback kaynak temizliği recursive Java/Kotlin çağrı zincirine dayanmaz; düğümler explicit kuyruklarda tutulur. Kaynak ağacı silinmeden önce tamamı direct-entry/canonical kurallarıyla doğrulanır ve sonra çocuklardan köke doğru temizlenir.

Yeni staging adları oluşturulma zamanını içerir. Sonraki kopyala/taşı işleminde hedef klasör taranırken ancak ad içindeki zaman damgası ve dosya sistemindeki değiştirilme zamanı altı saatlik eşiği birlikte aşmışsa temizlik adayı olur. Aday ağacı silinmeden önce bütünü doğrulanır; sembolik bağlantı veya güvenli alan dışına yönlenme görülürse otomatik temizlik yapılmaz.

## ADB ve veri güvenliği

OmniFiles Android sandbox'ını atlatıyormuş gibi davranmaz. Kablosuz ADB kullanıcı tarafından Android ayarlarından etkinleştirilmeli ve eşleştirilmelidir. Root tespiti yalnız durum bilgisi içindir; uygulama kendiliğinden `su` başlatmaz.

ADB pull akışı mümkün olduğunda uzak dosyayı transfer öncesi ve sonrası tekrar stat ederek boyut, değiştirilme zamanı ve mode bilgisini karşılaştırır. Dosya aktarım sırasında değişmiş veya kaybolmuşsa yerel geçici kopya güvenilir kabul edilmez ve silinir.

ADB SHA-256 işlemi cihazda `sha256sum` gibi bir shell aracının varlığını varsaymaz. İstenen uzak dosya önce aynı doğrulanmış pull mekanizmasıyla uygulamanın özel cache alanına alınır; snapshot doğrulaması geçerse yerel geçici kopyanın SHA-256 değeri hesaplanır ve kopya `finally` temizliğiyle silinir. Böylece farklı üretici ROM'larında shell araç setine bağımlılık oluşmaz.

Uzak ADB yol politikası yalnız mutlak yolları kabul eder; `.`/`..`, NUL, ISO kontrol karakterleri ve 255 karakteri aşan tekil dosya adları reddedilir. ADB klasör listesinden gelen güvenli olmayan adlar `AdbRemoteEntry` oluşturulmadan elenir; bu sayede DiffUtil veya sonraki path işlemleri yanıltıcı bir ad üzerinden çalışmaz.

SHA-256 aracı seçilen belgeyi değiştirmez; `ContentResolver` üzerinden salt okunur akış kullanır. Analizörden ve yerel dosya listesinden gelen dosyalar doğrudan `file://` URI ile açılmaz; uygulamanın `FileProvider` content URI'si kullanılır. Hash hesaplaması sırasında Activity yeniden oluşturulursa kaynak URI state içinde korunarak işlem yeniden başlatılabilir. SQLite düzenleme doğrudan kaynak üzerinde yapılmaz; önce çalışma ve yedek kopyaları oluşturulur.

## Build ve test

Gereksinimler:

- JDK 17+
- Android SDK 37.0
- Android Build Tools 36.0.0
- Android Gradle Plugin 9.1.1
- Gradle 9.3.1
- Kotlin 2.4.0

Build kapıları:

1. `scripts/source_sanity.py`
2. `:app:testDebugUnitTest`
3. `:app:lintDebug`
4. `:app:assembleDebug`
5. APK SHA-256 çıktısı

`FileOperationsTest` transfer, staging, kaynak snapshot, **1200 katmanlı stack-safe klasör**, monotonic byte-progress ve iptalde staging rollback davranışını doğrular. `TransferRuntimeTest` progress/cancel/finish yaşam döngüsünü ve eski transfer kimliğinin yeni bir transferi iptal edememesini doğrular. `StorageAnalyzerTest` en büyük dosya/klasör sıralamasını, bounded top-N aday seçimini, yapılandırılabilir öğe sınırını, kullanıcı iptalini ve **800 katmanlı stack-safe analiz ağacını** doğrular. `RemotePathPolicyTest` uzak yol normalizasyonunu, traversal engelini, kontrol karakteri/uzun ad reddini ve parent sınırlarını doğrular. `DigestUtilsTest` SHA-256 yardımcılarını denetler.

`source_sanity.py`; güvenli transfer primitive'leri, staging kurtarma, ADB pull doğrulaması, checksum aracı, Storage Analyzer sınır/iptal/path korumaları ve ana ekran wiring'i kaybolursa build'i durdurur.

## Ana kaynak alanları

- `app/src/main/java/dev/laxerus/omnifiles/access` — depolama erişimi ve erişim durumu
- `app/src/main/java/dev/laxerus/omnifiles/adb` — Kablosuz ADB, mDNS, health probe, doğrulanmış pull ve uzak yol politikaları
- `app/src/main/java/dev/laxerus/omnifiles/fs` — yol güvenliği, SHA-256, transfer, progress runtime, çöp ve Storage Analyzer çekirdeği
- `app/src/main/java/dev/laxerus/omnifiles/scout` — Save Scout
- `app/src/main/java/dev/laxerus/omnifiles/sqlite` — SQLite Studio
- `app/src/main/java/dev/laxerus/omnifiles/ui` — Activity ve liste arayüzleri
- `app/src/test` — unit testler
- `scripts/source_sanity.py` — kaynak ve CI regresyon kontrolleri
