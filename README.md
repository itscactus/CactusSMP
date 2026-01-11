# CactusSMP Claim System


## Öne Çıkanlar
- **NBT tabanlı menüler**: Slot çakışması yok, tüm itemlar CustomModelData destekli.
- **Claim Bankası**: Para yatır/çek, geçmiş görüntüleme, tüm işlemler loglanır.
- **Aktivite Logları**: Banka, trust, ayar, home, üye ve koruma olayları GUI’de filtreli.
- **Çoklu Home**: Claim başına birden fazla home, GUI’den oluştur/ışınlan/sil (shift+sağ tık ile silme).
- **Pazar**: 45+ ilan için sayfalama, dinamik fiyatlandırma.
- **Trust & Ayarlar**: Visitor/Builder/Manager seviyeleri; PvP, patlama, etkileşim, kapı izinleri GUI’den.
- **Gelişmiş Koruma**: Mob spawn, fire spread, crop growth, leaf decay, mob grief, fluid flow bayrakları.
- **Davet & Auto-Claim**: Davet süresi, otomatik claim ve radius claim desteği.

## Kurulum
1) Vault + ekonomi sağlayıcısı kurulu olmalı.
2) `mvn clean package` ile derleyin.
3) `target/CactusSMP-*.jar` dosyasını `plugins/` klasörüne atın.
4) Sunucuyu başlatın; `config.yml` ve `Messages.yml` otomatik oluşur.

## Yapılandırma
- **config.yml**: Menüler, fiyatlar, süreler, bayraklar, market, banka, home limitleri vb.
- **Messages.yml**: Tüm mesajlar MiniMessage ile düzenlenebilir.
- Menü ikonları/metinleri `menus.*` altında; tamamı CustomModelData destekli.

## Komutlar (özet)
- `/claim` – Ana menü
- `/claim buy` – Claim işlemleri
- `/claim home|sethome` – Home sistemi
- `/claim trust|untrust` – Yetki verme/çekme
- `/claim sell|unsell|market` – Pazar
- `/claim invite|accept|decline` – Davet sistemi
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
