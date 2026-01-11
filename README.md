# CactusSMP Claim System

## Özellikler
- **NBT tabanlı menüler**: Slot çakışması yok, tüm itemlar CustomModelData destekli.
- **Claim Bankası**: Para yatır/çek ve geçmiş; tüm işlemler loglanır.
- **Aktivite Logları**: Banka, trust, ayar, home, üye ve koruma olayları GUI’de filtrelenebilir.
- **Çoklu Home**: Claim başına birden fazla home; GUI’den oluştur, ışınlan, sil (shift+sağ tık ile silme).
- **Pazar**: Sayfalama ve dinamik fiyatlandırma ile satış listeleri.
- **Trust & Ayarlar**: Visitor/Builder/Manager seviyeleri; PvP, patlama, etkileşim, kapı izinleri GUI’den.
- **Gelişmiş Koruma**: Mob spawn, fire spread, crop growth, leaf decay, mob grief, fluid flow bayrakları.

## Hızlı Kullanım Akışı
- **Claim al**: Chunk üzerinde `/claim` → “Chunk Al” → isim ver.
- **Ayarları düzenle**: Claim sahibiyken `/claim` → “Ayarlar” → PvP/Patlama/Etkileşim/Kapı bayraklarını aç/kapat.
- **Üye yönetimi**: `/claim` → “Üye Yönetimi”; oyuncuya VISITOR/BUILDER/MANAGER yetkisi ver veya kaldır.
- **Bank**: `/claim` → “Claim Bankası”; para yatır/çek ve geçmişi gör.
- **Home**: `/claim sethome` ile ekle, `/claim home` ile ışınlan; GUI’den de yönet.
- **Market**: `/claim sell <fiyat>` ile listele, `/claim market` ile ilanlara göz at.
- **Loglar**: `/claim logs` ile son olayları gör (banka, ayar, home, üye vb.).

## Kurulum
1) Vault + ekonomi sağlayıcısı kurulu olmalı.
2) `mvn clean package` ile derleyin.
3) `target/CactusSMP-*.jar` dosyasını `plugins/` klasörüne kopyalayın.
4) Sunucuyu başlatın; `config.yml` ve `Messages.yml` otomatik oluşur.

## Yapılandırma
- **config.yml**: Menüler, fiyatlar, süreler, bayraklar, market, banka, home limitleri vb.
- **Messages.yml**: Tüm mesajlar MiniMessage ile düzenlenebilir.
- Menü ikonları/metinleri `menus.*` altında; tamamı CustomModelData destekli.

### config.yml kısa rehber
- `prices.*`: claim/uzatma taban fiyatları.
- `extend.dynamic_pricing.*`: süre uzatma için gün başına taban fiyat ve gün bazlı çarpan ayarı.
- `menus.*`: GUI başlıkları, slotlar, materyal, custom_model_data, lore.
- `claim_home.*`: home limiti, cooldown/warmup ayarları.
- `trust_system.*`: varsayılan seviye ve yetki listeleri.
- `claim_market.*`: min/max fiyat, komisyon ve vergi.
- `border_effects.*`: chunk giriş/çıkış efektleri.
- `advanced_protection.*`: default koruma bayrakları (mob_spawn, fire_spread, vb.).
- `claim_effects.*`: partikül efekt ayarları.

### Messages.yml kısa rehber
- `titles.*`: Menü başlıkları.
- `messages.*`: Genel işlem geri bildirimleri (claim, bank, önizleme, üyelik).
- `home_messages.*`: Home sisteme özel uyarılar.
- `trust_messages.*`: Yetki verme/çekme metinleri.
- `market_messages.*`: Satış/alış bildirimleri.
- `border_messages.*`: Bölge giriş-çıkış başlıkları.
- `log_messages.*`: Aktivite log metinleri.
- `effect_messages.*`: Efekt başlat/durdur bildirimleri.
- `help_messages.*`: `/claim help` çıktıları.

## Komutlar
- `/claim` – Ana menü
- `/claim buy` – Claim alma
- `/claim home|sethome` – Home sistemi
- `/claim trust|untrust` – Yetki verme/çekme
- `/claim sell|unsell|market` – Pazar
- `/claim logs` – Log görüntüleme

## Notlar
- Banka ve log menülerine yalnızca claim sahibi ve manager erişebilir.
- Çoklu home limiti `claim_home.max_homes_per_claim` ile ayarlanır.
- Log saklama süresi `activity_log.retention_days` ile ayarlanır.

## Derleme
```
mvn clean package
```

## Lisans
Bu projede lisans belirtilmemiştir.
