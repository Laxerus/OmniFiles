# OmniFiles

OmniFiles, Android için tek APK yaklaşımıyla geliştirilen modern bir dosya yöneticisi ve geliştirici araç setidir. Ortak depolama işlemlerini Android API'leriyle, daha ileri inceleme işlemlerini ise Android 11+ Kablosuz Hata Ayıklama üzerinden APK içine gömülü ADB istemcisiyle yapar.

**Shizuku, LADB veya çalışma anında bir PC zorunlu değildir.** Android'in güvenlik modeli geçerlidir; dahili ADB normalde `shell` kimliğiyle çalışır ve uygulama erişemediği özel verilere erişebiliyormuş gibi davranmaz.

## Geliştirme durumu

Aktif geliştirme sürümü **`0.8.0-dev`** olarak sabit tutulur. Normal geliştirme ve hata düzeltmeleri yayın anına kadar sürüm numarasını ilerletmez. Repository kökündeki Android/Gradle ağacı güncel kaynak kodun tek kaynağıdır; `.source/` yalnız tarihsel kurtarma alanıdır.

## Güncel APK

- [OmniFiles-0.8.0-dev-debug.apk](https://omnifiles-apk.onrender.com/OmniFiles-0.8.0-dev-debug.apk)
- [SHA-256](https://omnifiles-apk.onrender.com/OmniFiles-0.8.0-dev-debug.apk.sha256)

APK, `main` dalından Render build hattında kaynak sanity kontrolü, unit test, Android Lint ve debug assemble kapıları geçtikten sonra yayınlanır.

## Başlıca özellikler

- Android 11+ için Tüm Dosyalara Erişim akışı ve eski Android sürümlerinde uygun legacy izin davranışı.
- Arama, sıralama, gizli öğeler, favoriler, seçim ve toplu işlemler içeren yerel dosya yöneticisi.
- Güvenli `FileProvider` üzerinden dosya açma/paylaşma; `file://` kullanmama.
- Yerel VIEW, SEND ve SHA-256 Intent üretimini tek güvenli `LocalFileIntents` katmanında toplama; normal dosya/direct-path kontrolünü aksiyonlar arasında aynı tutma.
- Uygulama içi kalıcı çöp alanı, geri alma ve toplu çöp işlemleri.
- Dosya/klasör ayrıntıları, yol kopyalama, doğrudan yerel SHA-256 işlemi.
- **SHA-256 karşılaştırma:** beklenen 64 hex değeri veya `sha256:` önekli değeri girip/yapıştırıp hesaplanan dosya hash'iyle doğrudan eşleşme kontrolü yapma.
- Ana ekranda kullanılan/toplam/boş ortak depolama alanı ve doluluk göstergesi.
- **Depolama Analizi:** stack-safe ve bounded tarama, en büyük dosya/klasörler, sabit sekiz dosya kategorisi ve alan kullanım yüzdeleri.
- Kategori kartına dokunarak o türdeki **en büyük dosyaları** bounded drill-down listesinde inceleme; dosyayı açma, paylaşma, SHA-256 hesaplama, klasöründe gösterme, ayrıntı ve yol kopyalama.
- Analiz sonucundaki klasörü dosya yöneticisinde açma veya büyük bir dosyanın bulunduğu klasöre doğrudan gitme.
- Analizden bir dosyanın klasörüne geçildiğinde hedef dosyayı güvenli biçimde bulup **otomatik kaydırma + seçimden bağımsız Material vurgu** ile gösterme.
- APK içine gömülü **Kadb 2.1.4** ile Kablosuz ADB eşleştirme, bağlantı, mDNS keşfi, klasör listeleme, önizleme, paylaşım ve dışa aktarma.
- ADB bağlantısı için gerçek shell health probe ve doğrulanmış pull işlemleri.
- Güvenli olmayan ADB dosya adlarını UI/path katmanına taşımama ve filtrelenen giriş sayısını kullanıcıya bildirme.
- Uzak dosyada doğrulanmış pull sonrası yerel SHA-256 hesaplama; uzak cihazda `sha256sum` bulunmasına bağımlı olmama.
- **Save Scout** ile erişilebilir standart oyun/save konumlarını sınırlı tarama.
- **SQLite Studio** ile çalışma kopyasında tablo/satır görüntüleme ve uygun hücreleri düzenleme; geri yazım sonrası boyut + SHA-256 + SQLite integrity doğrulaması.
- Material 3 tabanı ve Android 15/16 edge-to-edge uyumlu ortak Activity altyapısı.

## Güvenli yerel transferler

Yerel kopyala/taşı işlemleri canonical/direct-entry yol doğrulamasından geçer. Depolama kökünün tamamını taşımak, klasörü kendi altına göndermek, güvenli alan dışına kaçmak ve sessiz overwrite yapmak engellenir.

Kopyalama ve move-fallback işlemleri önce hedef klasörde gizli `.omnifiles-transfer-v2-*` staging alanına yazılır. Her normal dosyada kaynak akışından SHA-256 hesaplanır ve staging kopyası yeniden okunarak doğrulanır. Kaynak dosyanın boyut ve `mtime` değeri işlem öncesi/sonrası kontrol edilir; klasörlerde de doğrudan çocuk adları ve klasör `mtime` snapshot'ı commit öncesi yeniden doğrulanır. Hata veya iptalde yarım staging ağacı temizlenir ve kullanıcı hedef adına commit edilmez.

Klasör kopyalama, toplam boyut taraması ve move-fallback kaynak temizliği recursive çağrı yerine explicit `ArrayDeque` yapılarıyla yürütülür. Böylece çok derin klasör ağaçlarında stack overflow riski azaltılır. Süreç çökmesiyle kalmış staging öğeleri yalnız yapılandırılmış ad zaman damgası, gerçek dosya `mtime` yaşı ve tam canonical/direct-entry ağaç doğrulaması birlikte geçerse otomatik temizlenir.

### Toplu aktarım ilerlemesi ve tüm kuyruğu iptal

Çoklu seçimle kopyala/taşı başlatıldığında `TransferBatchRunner` önce her kaynak için stack-safe byte tahmini çıkarır. `TransferBatchRuntime` toplam öğe sayısını, hazırlanmış/işlenmiş öğeleri, başarı/hata sayılarını, tamamlanmış byte miktarını ve aktif öğenin byte ilerlemesini tek yaşam döngüsü snapshot'ında tutar.

Material aktarım penceresi örneğin **`2/7 öğe`**, aktif dosya adı, **toplam aktarılan / toplam byte** ve genel yüzdeyi gösterir. Hazırlık aşamasında kaç kaynağın boyutunun tarandığı da görünür.

**Tüm aktarımı durdur** düğmesi yalnız görsel bir işlem değildir. Batch kimliğine bağlı iptal token'ı aktif `FileOperations.copy/move` çağrısına aktarılır. İstek; boyut taramasında, iteratif traversal sırasında, her 64 KiB kopyalama bloğunda, hedef SHA-256 doğrulamasında ve staging commitinden önce kontrol edilir. Aktif öğe `TransferCancelledException` ile mevcut rollback yoluna girer ve `TransferBatchRunner` sonraki öğeyi **başlatmaz**.

İptal anına kadar tamamen bitmiş öğeler geçerli kalır. Aktif iptal edilen öğe ile henüz işlenmemiş öğeler tekrar bekleyen transfer kuyruğuna bırakılır; böylece COPY işleminde başarıyla bitmiş dosyalar gereksiz yere yeniden kopyalanmaz. MOVE işleminde henüz işlenmemiş kaynaklara dokunulmaz ve aktif öğe commit edilmediyse kaynak korunur. Normal bir öğe hatası ise bütün kuyruğu durdurmaz; hata kaydedilir ve sonraki kaynak işlenmeye devam eder.

Aynı dosya sistemi içinde `renameTo` ile doğrudan tamamlanan MOVE çok kısa sürebilir; böyle bir öğe kullanıcı iptal etkileşiminden önce tamamlanabilir. Batch iptali bundan sonra sıradaki öğelerin başlamasını yine engeller.

Tekil transferlerde mevcut `TransferRuntime` byte telemetrisi kullanılmaya devam eder. Batch aktifken `OmniActivity`, içteki per-item runtime olaylarını ayrı bir ikinci progress penceresi olarak göstermez; aggregate batch snapshot'ı UI için kanonik kaynaktır.

## SHA-256 doğrulama ve güvenli yerel Intent katmanı

SHA-256 ekranı dosyanın hash'ini üretmenin yanında beklenen değeri de doğrular. Kullanıcı düz **64 hex karakter** veya case-insensitive `sha256:` önekli değer girebilir; panodan yapıştırma desteklenir. `Sha256Verifier` beklenen değeri normalize eder, geçersiz formatı hesaplanan hash'ten bağımsız olarak reddeder ve sonucu `WAITING_FOR_HASH`, `MATCH` veya `MISMATCH` durumlarından biriyle açıkça ayırır. Beklenen değer, seçili URI ve hesaplanan hash Activity yeniden oluşturulurken korunur.

Yerel normal dosyaların VIEW, SEND ve Checksum Intent'leri `LocalFileIntents` içinde merkezileştirilmiştir. Yardımcı katman doğrudan normal dosya şartını tekrar doğrular, güvenli `FileProvider` `content://` URI'si üretir, MIME tipini tek noktadan çözer ve URI okuma iznini `ClipData` ile birlikte taşır. Böylece Analyzer, normal dosya listesi ve ADB'den güvenli cache'e alınmış önizlemeler aynı URI/Intent politikasını kullanır.

## Depolama Analizi güvenlik modeli

Storage Analyzer ortak depolama kökünü değiştirmez; yalnız metadata ve dosya boyutlarını okur. Tarama recursive fonksiyon çağrıları yerine explicit `ArrayDeque` ile ilerler ve varsayılan olarak en fazla **40.000 öğe** işler. Sembolik bağlantılar izlenmez; her giriş `FilePathPolicy.requireDirectEntry` ile doğrulanır.

Sonuç belleği bounded tutulur: tüm taranan öğeleri saklamak yerine yalnız gösterilecek en büyük N dosya ve N klasör adayı tutulur. Dosya türü dağılımı görsel, video, ses, APK/uygulama paketi, arşiv, belge, veritabanı ve diğer olmak üzere sekiz sabit kovada `IntArray`/`LongArray` sayaçlarıyla hesaplanır.

Kategori drill-down da bounded kalır. Her kategori için varsayılan olarak yalnız **8 en büyük dosya adayı** tutulur; API seviyesinde kategori başına hard-cap **20** dosyadır. Aday listeleri sekiz kategoriye karşılık gelen sabit boyutlu bir `Array` içinde tutulur; benzersiz uzantı veya taranan dosya sayısı arttıkça sınırsız sonuç belleği oluşmaz. Kategori kartına dokunulduğunda bu sıralı adaylar gösterilir ve seçilen dosya mevcut güvenli analiz aksiyonlarına yönlendirilir.

İptal veya 40.000 öğe sınırında duran taramalarda kategori sayıları, byte toplamları ve kategori top-N listeleri aynı güvenilir **kısmi tarama kesitini** temsil eder. Genel en büyük dosyalar listesi ile kategori top-N listeleri birbirinden bağımsız limitlenir; örneğin global listede görünmeyen bir video kendi kategori drill-down'ında yine yer alabilir.

Bir analiz sonucuna işlem yapılacağı anda yol yeniden doğrulanır. Taramadan sonra taşınmış/değişmiş, kök dışına çıkan veya dolaylı/symlink hedefe dönüşmüş öğe reddedilir. `BrowserStartPathPolicy` yalnız mevcut, doğrudan ve ortak depolama içindeki klasörleri başlangıç yolu olarak kabul eder. `EXTRA_START_PATH` yalnız Activity'nin ilk oluşturuluşunda uygulanır; ekran yeniden oluşturulurken kaydedilmiş gezinme durumu korunur.

Dosya sonucu için **Bulunduğu klasörü aç** kullanıldığında Analyzer ayrıca hedef dosyanın canonical yolunu tek kullanımlık highlight ekstra alanıyla taşır. `BrowserHighlightPolicy` yalnız mevcut, doğrudan/canonical ve gerçekten yüklenmiş liste içinde bulunan hedefi kabul eder. Eşleşme varsa RecyclerView hedefe kaydırılır ve satır tema `colorPrimary` stroke'u ile vurgulanır. Bu vurgu seçim durumundan ayrıdır, dokunulduğunda temizlenir ve Activity yeniden oluşturulurken tekrar zorlanmaz. Symlink, kayıp, liste dışı veya başka konumdaki hedef görsel olarak vurgulanmaz.

## ADB ve veri güvenliği

Kablosuz ADB kullanıcı tarafından Android ayarlarından etkinleştirilip eşleştirilmelidir. Root tespiti yalnız durum bilgisidir; OmniFiles kendiliğinden `su` başlatmaz.

ADB pull işlemi uzak dosyanın transfer öncesi/sonrası boyut, `mtime` ve mode snapshot'ını karşılaştırır; yerel uzunluk da son snapshot ile eşleşmelidir. Uzak dosya aktarım sırasında değişmişse geçici çıktı güvenilir kabul edilmez.

ADB yol politikası yalnız mutlak yolları kabul eder; `.`/`..`, NUL, ISO kontrol karakterleri ve 255 karakteri aşan tekil dosya adları reddedilir. `listDirectoryDetailed` güvenli giriş listesinin yanında `skippedUnsafeEntries` sayısını da üretir. Böylece güvensiz uzak adlar sonraki UI/path katmanlarına taşınmazken kullanıcı klasörde güvenlik politikası nedeniyle kaç girişin gizlendiğini görür. Eski `listDirectory` çağrısı uyumluluk için güvenli giriş listesini döndürmeye devam eder.

ADB pull ile uygulama cache'ine alınan önizleme/paylaşım dosyaları da `LocalFileIntents` üzerinden açılır veya paylaşılır; uzak isim doğrudan bir `file://` URI'ye dönüştürülmez.

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

Önemli regresyon testleri:

- `FileOperationsTest`: staging, kaynak snapshot'ları, SHA doğrulaması, 1200 katmanlı stack-safe transfer, monotonic byte progress ve iptalde rollback.
- `TransferRuntimeTest`: tekil progress/cancel/finish yaşam döngüsü ve stale transfer kimliği izolasyonu.
- `TransferBatchRunnerTest`: ikinci öğede iptalin sonraki öğeyi başlatmaması, aggregate byte progress, normal hata sonrası kuyruğun devam etmesi ve stale batch kimliği izolasyonu.
- `StorageAnalyzerTest`: bounded global top-N, **kategori başına bounded/sıralı top-N**, sabit kategori kovaları, öğe sınırı, kullanıcı iptali ve 800 katmanlı stack-safe analiz ağacı.
- `BrowserStartPathPolicyTest`: dosya, kayıp, kök dışı ve symlink başlangıç hedeflerinin güvenli fallback davranışı.
- `BrowserHighlightPolicyTest`: doğrudan listelenen hedefi bulma; kayıp, liste dışı ve symlink hedeflerini reddetme.
- `Sha256VerifierTest`: `sha256:` öneki ve uppercase normalizasyonu, bozuk format reddi, bekleme/eşleşme/eşleşmeme durumları.
- `RemotePathPolicyTest`: uzak yol normalizasyonu, traversal/kontrol karakteri/uzun ad reddi.
- `DigestUtilsTest`: SHA-256 yardımcıları.

`source_sanity.py`; transfer staging güvenliği, `TransferBatchRuntime`, `TransferBatchRunner`, batch testleri, Material batch progress/cancel wiring'i, Storage Analyzer bounded kategori drill-down sözleşmesi, SHA-256 karşılaştırıcı, merkezi `LocalFileIntents`, ADB unsafe-entry metadata akışı ve Analyzer hedef vurgulama sözleşmesini zorunlu tutar. Bu parçalar yanlışlıkla silinirse APK build'i erken durur.

## Ana kaynak alanları

- `app/src/main/java/dev/laxerus/omnifiles/access` — depolama erişimi
- `app/src/main/java/dev/laxerus/omnifiles/adb` — Kablosuz ADB ve uzak yol/pull güvenliği
- `app/src/main/java/dev/laxerus/omnifiles/fs` — yol politikaları, checksum, transfer, batch runtime/runner, çöp ve Storage Analyzer
- `app/src/main/java/dev/laxerus/omnifiles/scout` — Save Scout
- `app/src/main/java/dev/laxerus/omnifiles/sqlite` — SQLite Studio
- `app/src/main/java/dev/laxerus/omnifiles/ui` — Activity ve liste arayüzleri
- `app/src/test` — unit testler
- `scripts/source_sanity.py` — kaynak ve CI regresyon kontrolleri
