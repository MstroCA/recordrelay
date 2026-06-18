# RecordRelay

**Universal Dynamic ETL Tool** — SQL ve NoSQL veritabanları arasında dinamik, esnek, bağımsız çalışan veri aktarım aracı.

[![CI](https://github.com/recordrelay/recordrelay/actions/workflows/ci.yml/badge.svg)](https://github.com/recordrelay/recordrelay/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/recordrelay/recordrelay?label=release)](https://github.com/recordrelay/recordrelay/releases/latest)
[![Coverage](https://codecov.io/gh/recordrelay/recordrelay/graph/badge.svg)](https://codecov.io/gh/recordrelay/recordrelay)
[![JetBrains Marketplace](https://img.shields.io/jetbrains/plugin/v/io.recordrelay.plugin?label=marketplace)](https://plugins.jetbrains.com/plugin/io.recordrelay.plugin)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)

---

## Vizyon

RecordRelay, geliştiricilerin ve veri mühendislerinin herhangi bir SQL veya NoSQL veritabanından diğerine — schema keşfi, otomatik eşleme ve canlı ilerleme takibi ile — veri aktarmasını sağlar. Tek bir konfigürasyon adımı; kod yazmaya gerek yok.

**Üç dağıtım hedefi:**
- **Desktop** — JavaFX tabanlı modern masaüstü uygulaması
- **CLI** — CI/CD pipeline'larına entegre edilebilen komut satırı aracı
- **IntelliJ Plugin** — IDE içinden doğrudan ETL işlemi

---

## Mimari

```
┌─────────────────────────────────────────────────────────┐
│                     UI LAYER                            │
│         Desktop (JavaFX) │ CLI (Picocli) │ Plugin       │
└───────────────────────┬─────────────────────────────────┘
                        │ uses
┌───────────────────────▼─────────────────────────────────┐
│                    CORE (Hexagonal)                     │
│  Domain Models │ Port Interfaces │ Transfer Engine      │
│  SPI Registry (ServiceLoader)                           │
└───────────────────────┬─────────────────────────────────┘
                        │ implements ports
┌───────────────────────▼─────────────────────────────────┐
│                  CONNECTOR ADAPTERS                     │
│  PostgreSQL │ MySQL │ MongoDB │ Cassandra │ Redis │ ...  │
└─────────────────────────────────────────────────────────┘
```

Hexagonal Architecture (Ports & Adapters): `core` modülü yalnızca domain modelleri ve port arayüzlerini içerir. Hiçbir somut veritabanı sürücüsüne veya UI framework'üne bağımlı değildir. Connector'lar ve UI katmanları birer adapter'dır.

---

## Modüller

| Modül | Açıklama |
|-------|----------|
| `core` | Domain modelleri, port arayüzleri, SPI registry, transfer engine |
| `connectors/connector-postgresql` | PostgreSQL adapter (JDBC + HikariCP) |
| `connectors/connector-mongodb` | MongoDB adapter (MongoDB Driver Sync) |
| `cli` | Picocli tabanlı CLI uygulaması |
| `desktop` | JavaFX standalone masaüstü uygulaması |
| `plugin` | IntelliJ IDEA plugin (geliştirme aşamasında) |

---

## Desteklenen Veritabanları

### SQL
- PostgreSQL 14+
- MySQL 8+ / MariaDB 10.6+
- Oracle 19c+ *(planlı)*
- SQL Server 2019+ *(planlı)*
- SQLite *(planlı)*
- H2 *(planlı)*

### NoSQL
- MongoDB 6+
- Cassandra 4+ *(planlı)*
- Redis 7+ *(planlı)*
- Elasticsearch 8+ *(planlı)*

### Dosya Formatları *(planlı)*
- CSV, Excel, JSON, YAML, Parquet

---

## Kurulum

### Desktop (macOS / Windows / Linux)

[GitHub Releases](https://github.com/recordrelay/recordrelay/releases/latest) sayfasından platformunuza uygun paketi indirin:

| Platform | Dosya |
|----------|-------|
| macOS | `RecordRelay-x.y.z.dmg` |
| Windows | `RecordRelay-x.y.z.exe` |
| Linux (Debian/Ubuntu) | `recordrelay_x.y.z_amd64.deb` |

> Gereksinim: bundled JRE dahil olduğu için ayrıca Java kurulumu gerekmez.

### CLI

```bash
# Arşivi indirin ve çıkarın
curl -L https://github.com/recordrelay/recordrelay/releases/latest/download/rr-cli-x.y.z.tar.gz | tar xz
export PATH="$PWD/rr-cli-x.y.z/bin:$PATH"

# Kullanım
rr --help
rr env list
rr conn add --name prod --type POSTGRESQL --host localhost --port 5432 --database mydb --user admin
rr discover --source prod
rr transfer --source prod --target staging --mapping mapping.yaml
```

> Gereksinim: Java 21+

### IntelliJ IDEA Plugin

JetBrains Marketplace'ten yükleyin: **Settings → Plugins → Marketplace → "RecordRelay"**

veya [Releases](https://github.com/recordrelay/recordrelay/releases/latest) sayfasından `RecordRelay-x.y.z.zip` indirip disk'ten yükleyin: **Settings → Plugins → ⚙ → Install Plugin from Disk…**

### Gereksinimler (geliştirme)
- Java 21+
- Docker (integration testleri için)

### Kaynak koddan derleme

```bash
git clone https://github.com/recordrelay/recordrelay.git
cd recordrelay

# Derle + tüm kontroller
./gradlew build

# Sadece unit testler
./gradlew test

# Integration testler (Docker gerektirir)
./gradlew integrationTest

# CLI'yi çalıştır
./gradlew :cli:run --args="--help"

# CLI fat JAR üret
./gradlew :cli:shadowJar
# → cli/build/libs/rr-cli-0.1.0-SNAPSHOT.jar
```

### CLI Kullanımı

```bash
# Bağlantı testi
rr conn test --name prod

# Schema keşfi
rr discover --source prod --database mydb

# Veri aktarımı
rr transfer \
  --source prod \
  --target staging \
  --mapping mapping.yaml \
  --batch-size 1000
```

---

## Connector Yazma

Yeni bir veritabanı desteği eklemek için `CONTRIBUTING.md` dosyasına bakın. Temel adımlar:

1. `core` modülündeki `DataSourceConnector`, `SchemaInspector`, `RecordReader`, `RecordWriter` port'larını implement edin.
2. `META-INF/services/io.recordrelay.core.port.out.DataSourceConnector` dosyasına sınıf adını ekleyin.
3. `ConnectorRegistry`, connector'ınızı `ServiceLoader` ile otomatik olarak keşfeder.

---

## Katkıda Bulunma

Lütfen [CONTRIBUTING.md](CONTRIBUTING.md) dosyasını okuyun. Tüm katkılar Apache-2.0 lisansı altında kabul edilir.

---

## Lisans

[Apache License 2.0](LICENSE) — Copyright 2026 the RecordRelay authors
