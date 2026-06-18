# RecordRelay

**Universal Dynamic ETL Tool** — SQL ve NoSQL veritabanları ile dosya formatları arasında dinamik, esnek, bağımsız çalışan veri aktarım aracı.

[![CI](https://github.com/MstroCA/recordrelay/actions/workflows/ci.yml/badge.svg)](https://github.com/MstroCA/recordrelay/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/MstroCA/recordrelay?label=release)](https://github.com/MstroCA/recordrelay/releases/latest)
[![Coverage](https://codecov.io/gh/MstroCA/recordrelay/graph/badge.svg)](https://codecov.io/gh/MstroCA/recordrelay)
[![JetBrains Marketplace](https://img.shields.io/jetbrains/plugin/v/io.recordrelay.plugin?label=marketplace)](https://plugins.jetbrains.com/plugin/io.recordrelay.plugin)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)

---

## Vizyon

RecordRelay, geliştiricilerin ve veri mühendislerinin herhangi bir veritabanından veya dosya formatından diğerine — schema keşfi, otomatik eşleme ve canlı ilerleme takibi ile — veri aktarmasını sağlar. Tek bir konfigürasyon adımı; kod yazmaya gerek yok.

**Üç dağıtım hedefi:**
- **Desktop** — JavaFX tabanlı modern masaüstü uygulaması (AtlantaFX, MVVM)
- **CLI** — CI/CD pipeline'larına entegre edilebilen komut satırı aracı
- **IntelliJ Plugin** — IDE içinden doğrudan ETL işlemi, 4-tab tool window

---

## Mimari

```
┌──────────────────────────────────────────────────────────────┐
│                         UI LAYER                             │
│        Desktop (JavaFX) │ CLI (Picocli) │ IntelliJ Plugin    │
└────────────────────────┬─────────────────────────────────────┘
                         │ uses
┌────────────────────────▼─────────────────────────────────────┐
│                     CORE (Hexagonal)                         │
│   Domain Models │ Port Interfaces │ Transfer Engine           │
│   SPI Registry (ServiceLoader) │ HealthStatus framework      │
└────────────────────────┬─────────────────────────────────────┘
                         │ implements ports
┌────────────────────────▼─────────────────────────────────────┐
│                   CONNECTOR ADAPTERS                         │
│  SQL: PostgreSQL │ MySQL/MariaDB │ SQL Server │ Oracle │ SQLite│
│  NoSQL: MongoDB │ Cassandra │ Redis │ Elasticsearch           │
│  File: CSV │ Excel │ JSON │ YAML │ Parquet                    │
└──────────────────────────────────────────────────────────────┘
```

Hexagonal Architecture (Ports & Adapters): `core` modülü yalnızca domain modelleri ve port arayüzlerini içerir. Hiçbir somut veritabanı sürücüsüne veya UI framework'üne bağımlı değildir. Connector'lar ve UI katmanları birer adapter'dır.

---

## Modüller

| Modül | Açıklama |
|-------|----------|
| `core` | Domain modelleri, port arayüzleri, SPI registry, transfer engine, HealthStatus |
| `engine-batch` | Spring Batch 5 pipeline adapter (H2 embedded meta DB) |
| `mapping-parsers` | JSON / YAML / SQL / NoSQL DSL parser'ları, 6 transform fonksiyonu |
| `cli` | Picocli tabanlı CLI — 7 komut, AES-256/GCM credential şifreleme |
| `desktop` | JavaFX masaüstü uygulaması — 5 ekran, MVVM, AtlantaFX |
| `plugin` | IntelliJ IDEA plugin — 4-tab tool window, 2 action |
| `connectors/connector-jdbc-base` | Tüm JDBC connector'ları için abstract base (reader/writer/schema) |
| `connectors/connector-postgresql` | PostgreSQL 14+ (JDBC + HikariCP, server-side cursor) |
| `connectors/connector-mysql` | MySQL 8+ / MariaDB 10.6+ |
| `connectors/connector-sqlserver` | Microsoft SQL Server 2019+ |
| `connectors/connector-oracle` | Oracle 19c+ (ojdbc11) |
| `connectors/connector-sqlite` | SQLite (embedded, dosya tabanlı) |
| `connectors/connector-mongodb` | MongoDB 6+ (Driver Sync) |
| `connectors/connector-cassandra` | Apache Cassandra 4+ (DataStax Driver 4.x) |
| `connectors/connector-redis` | Redis 7+ (Lettuce 6.x) |
| `connectors/connector-elasticsearch` | Elasticsearch 8+ (Java API Client) |
| `connectors/connector-file` | CSV, Excel (POI streaming), JSON Lines, YAML, Parquet |
| `connectors/connector-template` | Üçüncü taraf connector geliştirme şablonu |

---

## Desteklenen Kaynaklar / Hedefler

### SQL Veritabanları
| Veritabanı | Versiyon | Reader | Writer | Schema |
|------------|----------|:------:|:------:|:------:|
| PostgreSQL | 14+ | ✓ | ✓ | ✓ |
| MySQL / MariaDB | 8+ / 10.6+ | ✓ | ✓ | ✓ |
| SQL Server | 2019+ | ✓ | ✓ | ✓ |
| Oracle | 19c+ | ✓ | ✓ | ✓ |
| SQLite | 3.x | ✓ | ✓ | ✓ |

### NoSQL Veritabanları
| Veritabanı | Versiyon | Reader | Writer | Schema |
|------------|----------|:------:|:------:|:------:|
| MongoDB | 6+ | ✓ | ✓ | ✓ |
| Cassandra | 4+ | ✓ | ✓ | ✓ |
| Redis | 7+ | ✓ | ✓ | — |
| Elasticsearch | 8+ | ✓ | ✓ | ✓ |

### Dosya Formatları
| Format | Reader | Writer | Notlar |
|--------|:------:|:------:|--------|
| CSV | ✓ | ✓ | Apache Commons CSV, header otomatik algılama |
| Excel (.xlsx) | ✓ | ✓ | Apache POI SXSSF (streaming, büyük dosya) |
| JSON Lines | ✓ | ✓ | Newline-delimited JSON (Jackson streaming) |
| YAML | ✓ | ✓ | Sequence of maps (SnakeYAML) |
| Parquet | ✓ | ✓ | parquet-avro, Hadoop bağımsız |

---

## Kurulum

### Desktop (macOS / Windows / Linux)

[GitHub Releases](https://github.com/MstroCA/recordrelay/releases/latest) sayfasından platformunuza uygun paketi indirin:

| Platform | Dosya |
|----------|-------|
| macOS | `RecordRelay-x.y.z.dmg` |
| Windows | `RecordRelay-x.y.z.exe` |
| Linux (Debian/Ubuntu) | `recordrelay_x.y.z_amd64.deb` |

> Bundled JRE dahil — ayrıca Java kurulumu gerekmez.

### CLI

```bash
# Arşivi indirin ve çıkarın
curl -L https://github.com/MstroCA/recordrelay/releases/latest/download/rr-cli-x.y.z.tar.gz | tar xz
export PATH="$PWD/rr-cli-x.y.z/bin:$PATH"

# Bağlantı ekle ve test et
rr conn add --name prod --type POSTGRESQL --host localhost --port 5432 --database mydb --user admin
rr conn test --name prod

# Schema keşfi
rr discover --source prod --database mydb

# Veri aktarımı
rr transfer --source prod --target staging --mapping mapping.yaml --batch-size 1000
```

> Gereksinim: Java 21+

### IntelliJ IDEA Plugin

JetBrains Marketplace'ten yükleyin: **Settings → Plugins → Marketplace → "RecordRelay"**

veya [Releases](https://github.com/MstroCA/recordrelay/releases/latest) sayfasından `RecordRelay-x.y.z.zip` indirip: **Settings → Plugins → ⚙ → Install Plugin from Disk…**

---

## Kaynak Koddan Derleme

### Gereksinimler
- Java 21+
- Docker (integration testleri için)

```bash
git clone https://github.com/MstroCA/recordrelay.git
cd recordrelay

# Derle + tüm statik analiz + unit testler
./gradlew build

# Integration testler (Docker gerektirir — PostgreSQL, MongoDB, MySQL, vb.)
./gradlew integrationTest

# CLI fat JAR üret
./gradlew :cli:shadowJar
# → cli/build/libs/rr-cli-0.1.0-SNAPSHOT.jar

# Desktop kurulum dağıtımı
./gradlew :desktop:installDist

# Plugin ZIP üret
./gradlew :plugin:buildPlugin
```

---

## Yeni Connector Yazma

`connectors/connector-template` modülünü kopyalayın ve 4 arayüzü implement edin:

```
1. DataSourceConnector  — bağlantı, DB listesi, sağlık kontrolü
2. RecordReader         — kayıt okuma (streaming)
3. RecordWriter         — kayıt yazma (batch insert)
4. SchemaInspector      — tablo/kolon keşfi
```

SPI kaydı için `META-INF/services/io.recordrelay.core.port.out.DataSourceConnector` dosyasına sınıf adını ekleyin. JAR classpath'e atıldığında `ServiceLoader` otomatik keşfeder.

Detaylı rehber: [CONTRIBUTING.md](CONTRIBUTING.md)

---

## Dokümantasyon

| Doküman | İçerik |
|---------|--------|
| [docs/architecture.md](docs/architecture.md) | Hexagonal mimari, modül bağımlılıkları, veri akışı |
| [docs/mapping-dsl.md](docs/mapping-dsl.md) | JSON / YAML / SQL / NoSQL mapping DSL referansı |
| [docs/cli-reference.md](docs/cli-reference.md) | 7 CLI komutunun tam flag ve örnek referansı |
| [docs/batch-tuning.md](docs/batch-tuning.md) | Batch size, parallelism, skip/retry, dead-letter ayarı |
| [docs/plugin-development.md](docs/plugin-development.md) | IntelliJ plugin geliştirme rehberi |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Geliştirme ortamı kurulumu, kod standartları, PR kuralları |
| [SECURITY.md](SECURITY.md) | Güvenlik açığı bildirme süreci |

---

## Katkıda Bulunma

Lütfen [CONTRIBUTING.md](CONTRIBUTING.md) dosyasını okuyun. Tüm katkılar Apache-2.0 lisansı altında kabul edilir.

---

## Lisans

[Apache License 2.0](LICENSE) — Copyright 2026 the RecordRelay authors
