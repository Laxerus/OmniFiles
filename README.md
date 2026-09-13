# OmniFiles

OmniFiles, Android için tek APK içinde çalışan gelişmiş dosya yöneticisi, save inceleme aracı ve geliştirici yardımcı paketidir. **Shizuku, LADB veya PC çalışma anında gerekmez.** Gelişmiş shell erişimi için Android 11+'ın kendi **Kablosuz hata ayıklama** özelliği kullanılır.

## 0.8.0-dev öne çıkanlar

### 0.8 — Material 3 arayüz + sağlamlık dalgası

- Uygulamanın ana görsel sistemi **Material 3** tabanında yeniden kuruldu: modern kartlar, tonal/outlined action tile'lar, büyük köşe yarıçapları, tutarlı surface katmanları ve sade ikon ailesi.
- Android 12+ cihazlarda **Dynamic Color** otomatik uygulanır; menüden **Sistem / Açık / Koyu** görünüm seçilebilir.
- Tüm Activity'ler ortak `OmniActivity` tabanına taşındı; Android 15/16 edge-to-edge ve display-cutout/system-bar inset davranışı merkezi olarak güvenli yönetilir.
- Ana ekran; Erişim Merkezi, hızlı konumlar ve dahili araçlar olarak yeniden tasarlandı. ADB/root/depolama durumları gerçek sağlık testiyle gösterilir.
- Dosya tarayıcısı Material 3 toolbar, backend etiketi, öğe sayacı, boş-durum kartı, checkable dosya kartları ve oluşturma FAB'ı ile yenilendi; liste `ListAdapter + DiffUtil` kullanır.
- Dahili ADB eşleştirme ekranı iki aşamalı modern onboarding'e geçirildi ve host/port/6-haneli kod doğrulaması hem UI hem ADB katmanında zorunlu hale getirildi.
- Save Scout hedef-uygulama ekranı modernleştirildi ve Android paket adı doğrulaması sıkılaştırıldı.
- **SQLite Studio** artık SQL yazmadan tablo/satır seçip hücre değerlerini düzenleyebilir. BLOB alanlar güvenlik için salt okunur, `WITHOUT ROWID` tablolar SQL konsoluna yönlendirilir; değişiklikler yine kullanıcı `Kaydet` demeden kaynak dosyaya yazılmaz.
- Save restore artık hedef dosyaları önce güvenli snapshot'a alır. Restore ortada hata verirse dokunulan dosyaları geri yüklemeyi dener; tekrar eden arşiv yolları ve birden fazla manifest reddedilir.
- ZIP restore/extract, hash ve karşılaştırma akışlarında null-stream/sessiz overwrite noktaları sertleştirildi; hash aracı dosyayı tek geçişte birden fazla algoritmayla işler.
- Repoya hızlı `scripts/source_sanity.py` kontrolü eklendi. GitHub Actions artık kaynak/XML kontrolü → unit test → **Android Lint** → `assembleDebug` sırasını zorunlu build gate olarak çalıştırır.

## Dahili Kablosuz ADB

OmniFiles, `com.flyfishxu:kadb:2.1.4` istemcisini APK içine gömer. Bu nedenle ADB shell kullanmak için Shizuku/LADB kurmak veya bilgisayara bağlanmak gerekmez.

## Android güvenlik sınırı

Tek APK olması Android'in sandbox güvenlik modelini ortadan kaldırmaz. Dahili ADB normalde `shell` kimliğiyle çalışır. Private app data için hedef uygulamanın `debuggable=true` olup `run-as` kabul etmesi veya cihazda önceden kullanıcı tarafından root sağlanmış olması gerekir.

## Derleme

- JDK 17+
- Android SDK 36
- Android Gradle Plugin 8.10.1
- Gradle 8.11.1
- Kotlin 2.4.0

GitHub Actions workflow'u debug APK'yı `OmniFiles-debug-apk` artifact'i olarak üretir.
