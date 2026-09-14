# OmniFiles dosya yöneticisi ana akışı

OmniFiles `0.8.0-dev` artık uygulama simgesinden açıldığında doğrudan `FileBrowserActivity` ile başlar. `MainActivity` launcher değildir; uygulama içindeki **Ayarlar ve Araçlar** merkezi olarak kullanılır.

Dosya yöneticisinin üst çubuğundaki dişli eylemi `FileManagerToolbar` üzerinden Ayarlar ve Araçlar merkezini açar. Bu merkezden depolama erişimi, Depolama Analizi, Çöp Kutusu, SHA-256 doğrulama, Kablosuz ADB, ADB dosya tarayıcısı, Save Scout ve SQLite Studio erişilebilir. **Dosyalara dön** eylemi yeni bir tarayıcı örneği açmak yerine merkezi kapatıp mevcut dosya yöneticisine geri döner.

İlk çalıştırmada ortak depolama izni yoksa `StorageAccessButton` dosya yöneticisinin içinde görünür ve kullanıcıyı Android'in uygun depolama erişimi akışına yönlendirir. Uygulamaya dönüldüğünde düğme izin durumunu yeniden kontrol eder; erişim hazırsa gizlenir ve mevcut `FileBrowserActivity.onResume()` akışı dosya listesini yükler.

Bu davranış `scripts/file_manager_shell_sanity.py` ile korunur. Kontrol; manifestte tek launcher'ın `FileBrowserActivity` olmasını, `MainActivity`nin internal kalmasını, toolbar menüsünü, Ayarlar ve Araçlar yönlendirmesini, depolama erişim kurtarma düğmesini ve ilgili kaynak bağlantılarını doğrular. Hem Render APK build hattı hem de GitHub Actions bu kontrolü `scripts/source_sanity.py` ile birlikte çalıştırır.
