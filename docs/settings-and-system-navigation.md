# Ayarlar, araçlar ve Android sistem yönlendirmeleri

OmniFiles `0.8.0-dev` normal açılışta dosya yöneticisini gösterir. Gelişmiş seçenekler sağ üstteki **Ayarlar ve Araçlar** eyleminde toplanır. Bu merkez düz bir buton listesi yerine Material 3 kartlarıyla dört amaca ayrılır: mevcut erişim/depolama durumu, telefon ayarları, depolama araçları ve ADB/geliştirici araçları.

## Telefon ayarlarına doğrudan geçiş

`SystemSettingsNavigator` Android sistem ayarlarına geçişi tek yerde yönetir. Her hedef için en spesifik resmi Android intent'i önce denenir; üretici/Android sürümü o ekranı sunmuyorsa daha genel güvenli ayara kontrollü fallback uygulanır.

- **Tüm dosyalara erişim:** Android 11+ üzerinde önce `ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` ve OmniFiles paket URI'si kullanılır. Bu ekran yoksa genel tüm-dosyalar yönetimi, uygulama ayrıntıları ve son olarak genel Ayarlar denenir. Android 10 ve altında mevcut runtime storage permission akışı korunur.
- **Geliştirici seçenekleri:** `ACTION_APPLICATION_DEVELOPMENT_SETTINGS`, ardından genel Ayarlar fallback'i kullanılır. Android SDK herkese açık, cihazlar arası güvenilir bir “Kablosuz hata ayıklama alt sayfası” intent'i tanımlamadığı için OmniFiles üreticiye özel gizli activity adına güvenmez.
- **Wi‑Fi:** `ACTION_WIFI_SETTINGS`, ardından kablosuz ayarlar ve genel Ayarlar fallback'i kullanılır.
- **OmniFiles uygulama ayrıntıları:** `ACTION_APPLICATION_DETAILS_SETTINGS` + `package:` URI'si ile doğrudan OmniFiles sistem sayfası açılır; sonra uygulamalar listesi ve genel Ayarlar fallback'i vardır.

Ayar açma başarısız olursa uygulama çökmez; kullanıcıya açık hata bildirimi gösterilir.

## Modern Ayarlar ve Araçlar merkezi

Ayarlar merkezi sabit Material toolbar + kaydırılabilir kart yapısına sahiptir. Üst özet kartı sürüm, ortak depolama erişimi, dahili ADB durumu, root algılama durumu ve depolama kullanımını gösterir. Telefon ayarları ayrı kartlarla Tüm dosyalara erişim, Geliştirici seçenekleri, Wi‑Fi ve OmniFiles uygulama ayrıntılarına gider.

OmniFiles araçları üç gruptadır:

- **Depolama ve dosyalar:** Depolama Analizi, Çöp Kutusu, SHA-256 doğrulama.
- **Kablosuz ADB ve gelişmiş erişim:** Kablosuz ADB, ADB dosya tarayıcısı, Save Scout.
- **Geliştirici araçları:** SQLite Studio.

Depolama erişimi verildiğinde “Erişimi yönet” düğmesi kaybolmaz; izin durumunu sonradan değiştirmek için Android'in ilgili sayfasına tekrar gidilebilir. Ekran okuyucu açıklaması erişim hazır olup olmadığını ayrıca bildirir.

## Dosya yöneticisi hızlı araç menüsü

Dosya yöneticisinin dişli eylemi tam Ayarlar ve Araçlar merkezini açar. Overflow menüsünde ayrıca sık kullanılan araçlara doğrudan kısayollar bulunur:

- Depolama Analizi
- Çöp Kutusu
- SHA-256 doğrulama
- Kablosuz ADB

Böylece ana dosya yönetimi ekranı sade kalırken sık kullanılan gelişmiş araçlar birkaç dokunuş uzağında tutulur.

## Kablosuz ADB ekranı

Kablosuz ADB kurulumu da modern Material kart düzenine geçirilmiştir. Üst karttan doğrudan Geliştirici seçenekleri veya Wi‑Fi ayarları açılabilir. Host, eşleştirme portu, altı haneli kod ve bağlantı portu formu tek sütunlu, klavye action sırası belirlenmiş alanlardan oluşur. Eşleştirme/bağlantı sonucu ayrı bir Material kartta gösterilir.

## Regresyon koruması

`scripts/file_manager_shell_sanity.py` aşağıdakilerin yanlışlıkla kaybolmasını build öncesinde engeller:

- FileBrowserActivity'nin tek launcher olması,
- Ayarlar ve Araçlar merkezi,
- hızlı araç overflow menüsü,
- `SystemSettingsNavigator` hedefleri ve fallback'leri,
- Tüm dosyalara erişimin merkezi yönlendirmesi,
- ADB ekranındaki Geliştirici seçenekleri ve Wi‑Fi kısayolları,
- modern Material kart layout sözleşmeleri.

Bu kontrol `scripts/source_sanity.py` ile birlikte hem Render APK hattında hem GitHub Actions hattında çalışır.
