# OmniFiles

OmniFiles, Android için modern bir dosya yöneticisi ve yardımcı araç setidir. Ortak depolama işlemlerini, güvenli bakım araçlarını ve gerektiğinde Kablosuz ADB erişimini tek APK içinde toplar.

**Shizuku, LADB veya sürekli bağlı bir PC gerekmez.** Android'in normal güvenlik modeli geçerlidir; dahili ADB bağlantısı `shell` yetkileriyle çalışır.

## Güncel sürüm

Geliştirme sürümü yayınlanana kadar **`0.8.0-dev`** olarak sabittir.

- [APK indir](https://omnifiles-apk.onrender.com/OmniFiles-0.8.0-dev-debug.apk)
- [SHA-256](https://omnifiles-apk.onrender.com/OmniFiles-0.8.0-dev-debug.apk.sha256)

## Özellikler

- Arama, sıralama, favoriler, gizli dosyalar ve toplu seçim içeren dosya yöneticisi.
- Güvenli kopyalama/taşıma, yeniden adlandırma, paylaşma ve klasör oluşturma.
- Geri yüklenebilir **OmniFiles Çöp Kutusu** ve muhafazakâr **Gereksiz Dosya Temizliği**.
- **Depolama Analizi** ile en büyük dosya, klasör ve dosya türlerini görme.
- **Yinelenen Dosyalar** ile aynı boyuttaki adayları SHA-256 üzerinden doğrulayıp kazanılabilecek alanı görme.
- SHA-256 hesaplama ve beklenen hash ile doğrulama.
- Dahili Kablosuz ADB, Save Scout ve SQLite Studio araçları.
- Material 3 ve Android 15/16 edge-to-edge uyumlu arayüz.

## Güvenlik

Dosya işlemleri canonical yol kontrolleriyle sınırlandırılır; sembolik/dolaylı yollar ve kritik Android klasörlerinin kendisi korunur. Temizlik araçları kullanıcı medyasını yalnız adına bakarak gereksiz kabul etmez. Yinelenen dosya bulucu sonuçları otomatik silmez.

OmniFiles ayrıca önceki oturumlardan kalmış kendi geçici ADB önizleme, checksum ve SQLite çalışma dosyalarını uygulama başlangıcında sınırlandırılmış bir bakım işlemiyle temizler.

## Geliştirme

Güncel kaynak repository kökündeki Android/Gradle projesidir. `.source/` yalnız eski kurtarma arşividir.

APK hattı: `source sanity → unit tests → Android Lint → assembleDebug`.
