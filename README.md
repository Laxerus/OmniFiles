# OmniFiles

OmniFiles, Android için geliştirilen modern bir dosya yöneticisi ve yardımcı araç setidir. Amaç; ortak depolama işlemlerini, güvenli dosya araçlarını ve gerektiğinde Kablosuz ADB desteğini tek APK içinde toplamaktır.

**Shizuku, LADB veya sürekli bağlı bir PC gerekmez.** Android'in güvenlik modeli geçerlidir; dahili ADB normalde `shell` yetkileriyle çalışır.

## Güncel sürüm

Geliştirme sürümü yayınlanana kadar **`0.8.0-dev`** olarak sabit tutulur.

- [APK indir](https://omnifiles-apk.onrender.com/OmniFiles-0.8.0-dev-debug.apk)
- [SHA-256](https://omnifiles-apk.onrender.com/OmniFiles-0.8.0-dev-debug.apk.sha256)

## Neler yapabiliyor?

- Arama, sıralama, favoriler, gizli dosyalar ve toplu seçim içeren dosya yöneticisi.
- Güvenli kopyalama/taşıma, yeniden adlandırma, paylaşma ve klasör oluşturma.
- Geri yüklenebilir **OmniFiles Çöp Kutusu**.
- **Gereksiz Dosya Temizliği:** eski geçici dosyaları, yarım indirmeleri, metadata artıklarını ve boş cache/temp klasörlerini tarar; uygun adayları kalıcı silmek yerine Çöp Kutusu'na taşır.
- **Depolama Analizi:** en büyük dosyaları, klasörleri ve dosya kategorilerini gösterir.
- SHA-256 hesaplama ve beklenen hash ile doğrulama.
- Dahili Kablosuz ADB istemcisi ile erişilebilir Android dosyalarını inceleme ve dışa aktarma.
- Save Scout ile erişilebilir oyun/save konumlarını tarama.
- SQLite Studio ile çalışma kopyası üzerinden güvenli veritabanı inceleme/düzenleme.
- Material 3 ve Android 15/16 edge-to-edge uyumlu arayüz.

## Güvenlik yaklaşımı

OmniFiles dosya işlemlerinde canonical yol kontrolleri kullanır, sembolik/dolaylı yolları sınırlar ve kritik Android klasörlerinin kendisini değiştirmeyi engeller.

Gereksiz Dosya Temizliği özellikle muhafazakârdır: `Android/` ve `LOST.DIR` tarama dışında tutulur; fotoğraf, video veya belge klasörleri yalnız adına bakılarak gereksiz kabul edilmez. Temizlik öncesinde aday tekrar doğrulanır ve uygun öğe Çöp Kutusu'na taşınır.

## Geliştirme

Kaynak kodun güncel hali repository kökündeki Android/Gradle projesidir. `.source/` yalnız eski kurtarma arşividir ve aktif geliştirme kaynağı değildir.

Her APK yayını şu kontrollerden geçer:

```text
source sanity
unit tests
Android Lint
assembleDebug
```

Build hattı başarılı olduğunda güncel APK üstteki bağlantıda yayınlanır.
