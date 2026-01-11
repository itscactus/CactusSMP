# CactusSMP Claim System

## Özellikler
- **NBT tabanlı menüler**: Slot çakışması yok, tüm itemlar CustomModelData destekli.
- **Claim Bankası**: Para yatır/çek ve geçmiş; tüm işlemler loglanır.
- **Aktivite Logları**: Banka, trust, ayar, home, üye ve koruma olayları GUI’de filtrelenebilir.
- **Çoklu Home**: Claim başına birden fazla home; GUI’den oluştur, ışınlan, sil (shift+sağ tık ile silme).
- **Pazar**: Sayfalama ve dinamik fiyatlandırma ile satış listeleri.
- **Trust & Ayarlar**: Visitor/Builder/Manager seviyeleri; PvP, patlama, etkileşim, kapı izinleri GUI’den.
- **Gelişmiş Koruma**: Mob spawn, fire spread, crop growth, leaf decay, mob grief, fluid flow bayrakları.

## Kurulum
1) Vault + ekonomi sağlayıcısı kurulu olmalı.
2) `mvn clean package` ile derleyin.
3) `target/CactusSMP-*.jar` dosyasını `plugins/` klasörüne kopyalayın.
4) Sunucuyu başlatın; `config.yml` ve `Messages.yml` otomatik oluşur.

## Yapılandırma
- **config.yml**: Menüler, fiyatlar, süreler, bayraklar, market, banka, home limitleri vb.
- **Messages.yml**: Tüm mesajlar MiniMessage ile düzenlenebilir.
- Menü ikonları/metinleri `menus.*` altında; tamamı CustomModelData destekli.

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
