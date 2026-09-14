# Ayarlar, araçlar ve Android sistem yönlendirmeleri

OmniFiles `0.8.0-dev` normal açılışta doğrudan dosya yöneticisini gösterir. Gelişmiş seçenekler sağ üstteki **Ayarlar ve Araçlar** eyleminde toplanır. Dosya yöneticisinin overflow menüsünde Depolama Analizi, Çöp Kutusu, SHA-256 ve Kablosuz ADB için hızlı kısayollar da bulunur.

## Daha anlaşılır modern menü

Ayarlar merkezi artık kartın içinde ayrı butonlar kullanan uzun bir form değildir. Ekran, modern Android ayar ekranlarına benzer şekilde tam kart yüzeyi tıklanabilen kısa eylem satırlarından oluşur. Her satırda solda anlamlı ikon, ortada başlık ve kısa açıklama, sağda yön oku vardır. Böylece dokunma hedefi büyür, gereksiz görsel tekrar azalır ve ekran daha hızlı taranabilir.

Üstteki durum kartı depolama kullanımını ve üç ayrı durum chip'ini gösterir:

- **Depolama:** hazır veya izin gerekli.
- **ADB:** ayarlanmadı, kontrol ediliyor, bağlı veya erişilemiyor.
- **Root:** algılandı veya yok.

Eylemler dört açık bölüme ayrılır:

- **Telefon ve izinler:** Tüm dosyalara erişim, Geliştirici seçenekleri, Wi-Fi ayarları, OmniFiles uygulama ayarları.
- **Dosya araçları:** Depolama Analizi, Çöp Kutusu, SHA-256 doğrulama.
- **Kablosuz ADB:** eşleştirme/bağlantı, ADB dosya tarayıcısı, Save Scout.
- **Geliştirici:** SQLite Studio.

Hazır olmayan özellikler gri ve açıklamasız biçimde kilitlenmez. Depolama Analizi için izin yoksa ilgili karta dokunmak kullanıcıyı doğrudan depolama erişim akışına götürür. ADB Dosyaları veya Save Scout için ADB kurulmamışsa kullanıcıya kısa açıklama gösterilir ve Kablosuz ADB kurulum ekranı açılır. Böylece menüde ölü eylem kalmaz.

## Telefon ayarlarına doğrudan geçiş

`SystemSettingsNavigator` Android sistem ayarlarına geçişi tek yerde yönetir. Her hedef için en spesifik resmi Android intent'i önce denenir; üretici veya Android sürümü o ekranı sunmuyorsa daha genel güvenli ayara kontrollü fallback uygulanır.

- **Tüm dosyalara erişim:** Android 11+ üzerinde önce `ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` ve OmniFiles paket URI'si kullanılır. Bu ekran yoksa genel tüm-dosyalar yönetimi, uygulama ayrıntıları ve son olarak genel Ayarlar denenir. Android 10 ve altında runtime storage permission akışı korunur.
- **Geliştirici seçenekleri:** `ACTION_APPLICATION_DEVELOPMENT_SETTINGS`, ardından genel Ayarlar fallback'i kullanılır. Android SDK cihazlar arası güvenilir bir “Kablosuz hata ayıklama” alt sayfa intent'i tanımlamadığı için üreticiye özel gizli activity adına güvenilmez.
- **Wi-Fi:** `ACTION_WIFI_SETTINGS`, ardından kablosuz ayarlar ve genel Ayarlar fallback'i kullanılır.
- **OmniFiles uygulama ayrıntıları:** `ACTION_APPLICATION_DETAILS_SETTINGS` + `package:` URI'si ile doğrudan OmniFiles sistem sayfası açılır; ardından uygulamalar listesi ve genel Ayarlar fallback'i vardır.

Ayar açma başarısız olursa uygulama çökmez; kullanıcıya açık hata bildirimi gösterilir.

## Kablosuz ADB ekranı

Kablosuz ADB kurulumu modern Material kart düzenindedir. Üst karttan doğrudan Geliştirici seçenekleri veya Wi-Fi ayarları açılabilir. Host, eşleştirme portu, altı haneli kod ve bağlantı portu alanları tek sütunlu formdadır. mDNS keşfi bulunan değerleri otomatik doldurabilir ve bağlantı sonucu ayrı bir Material kartta gösterilir.

## Regresyon koruması

`scripts/file_manager_shell_sanity.py` aşağıdakilerin yanlışlıkla kaybolmasını build öncesinde engeller:

- `FileBrowserActivity`nin tek launcher olarak kalması,
- dosya yöneticisi hızlı araç menüsü,
- merkezi ve fallback'li `SystemSettingsNavigator`,
- modern tam-kart tıklanabilir Ayarlar ve Araçlar ekranı,
- durum chip'leri ve dinamik erişim açıklamaları,
- hazır olmayan Depolama/ADB araçlarının kurulum ekranına yönlendirilmesi,
- ADB ekranındaki Geliştirici seçenekleri ve Wi-Fi kısayolları.

Bu kontrol `scripts/source_sanity.py` ile birlikte hem Render APK hattında hem GitHub Actions hattında çalışır.
