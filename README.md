# RecordRelay

**Universal Data Reproduction & Debug Platform** — Gerçek üretim veya test sistemi durumunu dakikalar içinde yerel ortamda yeniden üret.

[![CI](https://github.com/MstroCA/recordrelay/actions/workflows/ci.yml/badge.svg)](https://github.com/MstroCA/recordrelay/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/MstroCA/recordrelay?label=release)](https://github.com/MstroCA/recordrelay/releases/latest)
[![Coverage](https://codecov.io/gh/MstroCA/recordrelay/graph/badge.svg)](https://codecov.io/gh/MstroCA/recordrelay)
[![JetBrains Marketplace](https://img.shields.io/jetbrains/plugin/v/io.recordrelay.plugin?label=marketplace)](https://plugins.jetbrains.com/plugin/io.recordrelay.plugin)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)

---

## Vizyon

RecordRelay bir ETL aracı değildir. Geliştiriciler ve SRE'ler için **business context reproduction** platformudur: bir müşteri, sipariş veya kullanıcı gibi gerçek bir varlığı tüm ilişkileriyle birlikte production'dan yerel ortama dakikalar içinde yeniden üretir — veya bir `.rrpkg` paketi olarak başkalarıyla paylaşır.

**Üç dağıtım hedefi:**
- **Desktop** — JavaFX tabanlı masaüstü uygulaması (AtlantaFX) — Clone Context ekranı ile tek tıkla yeniden üretim
- **CLI** — CI/CD pipeline'larına entegre edilebilen komut satırı aracı
- **IntelliJ Plugin** — IDE içinden doğrudan Clone sekmesi, Connections, Discovery, Monitor

---

## CLI Kullanımı

```bash
# Bağlantı ekle
rr conn add --name prod --type POSTGRESQL --host db.prod --port 5432 --database mydb --user admin
rr conn add --name local --type POSTGRESQL --host localhost --port 5432 --database mydb --user admin

# Müşteriyi production'dan local'e clone et
rr clone --entity customer --id 12345 --from prod --target local --depth 3

# Pakete export et (başkasıyla paylaş)
rr export --entity customer --id 12345 --from prod --output ./exports/

# Paketi başka bir ortama import et
rr import customer-12345-1234567890.rrpkg --target staging

# İki environment'ı karşılaştır
rr diff --entity customer --id 12345 --from prod --to staging

# Schema keşfi
rr discover --source prod
```

---

## Mimari

```
┌──────────────────────────────────────────────────────────────┐
│                         UI LAYER                             │
│        Desktop (JavaFX) │ CLI (Picocli) │ IntelliJ Plugin    │
└────────────────────────┬─────────────────────────────────────┘
                         │
┌────────────────────────▼─────────────────────────────────────┐
│                  APPLICATION / ENGINE                        │
│   recordrelay-engine (orchestration + identity mapping)      │
│   recordrelay-graph-engine  (FK & heuristic discovery)       │
│   recordrelay-masking-engine  (deterministic PII masking)    │
│   recordrelay-package-engine  (.rrpkg v2.1 export/import)    │
└────────────────────────┬─────────────────────────────────────┘
                         │
┌────────────────────────▼─────────────────────────────────────┐
│                  DOMAIN & CORE                               │
│   recordrelay-domain  (BusinessEntity, RelationshipGraph,    │
│     ContextClonePlan, IdentityMapping, MaskingConfig…)       │
│   recordrelay-core  (ConnectionProfile, ContextProviderPort  │
│     SPI, RecordReader/Writer, ConnectorRegistry)             │
└────────────────────────┬─────────────────────────────────────┘
                         │ implements ContextProviderPort (ServiceLoader)
┌────────────────────────▼─────────────────────────────────────┐
│                  ADAPTERS (11 connectors)                    │
│  SQL: postgres │ mysql │ sqlserver │ oracle │ sqlite         │
│  NoSQL: mongodb │ cassandra │ redis │ elasticsearch          │
│  File: CSV │ Excel │ JSON │ YAML │ Parquet                   │
└──────────────────────────────────────────────────────────────┘
```

Hexagonal Architecture (Ports & Adapters): domain ve core modülleri yalnızca arayüz tanımlarını içerir. Hiçbir DB sürücüsüne veya UI framework'üne bağımlı değildir. Connector'lar ve engine'ler birer adapter'dır.

---

## Modüller

| Modül | Dizin | Açıklama |
|-------|-------|----------|
| `recordrelay-core` | `recordrelay-core/` | ConnectionProfile, ContextProviderPort SPI, ConnectorRegistry |
| `recordrelay-domain` | `recordrelay-domain/` | BusinessEntity, RelationshipGraph, ContextClonePlan, IdentityMapping, MaskingConfig |
| `recordrelay-engine` | `recordrelay-engine/` | Clone orchestration, identity mapping, BFS extraction, replay |
| `recordrelay-graph-engine` | `recordrelay-graph-engine/` | FK-based ve heuristic relationship discovery |
| `recordrelay-masking-engine` | `recordrelay-masking-engine/` | Deterministic PII masking (EMAIL, PHONE, ADDRESS, IBAN, NATIONAL_ID) |
| `recordrelay-package-engine` | `recordrelay-package-engine/` | `.rrpkg` v2.1 ZIP export ve import |
| `recordrelay-cli` | `recordrelay-cli/` | Picocli CLI — clone, export, import, replay, diff, discover, analyze, env, conn, status |
| `recordrelay-desktop` | `recordrelay-desktop/` | JavaFX masaüstü uygulaması — Clone Context, Environments, Connections, Discovery, Monitor |
| `recordrelay-plugin` | `recordrelay-plugin/` | IntelliJ IDEA plugin — Clone, Connections, Discovery, Monitor sekmeleri |
| `recordrelay-adapter-jdbc-base` | `recordrelay-adapter-jdbc-base/` | JDBC connector'ları için abstract base |
| `recordrelay-adapter-postgres` | `recordrelay-adapter-postgres/` | PostgreSQL 14+ |
| `recordrelay-adapter-mysql` | `recordrelay-adapter-mysql/` | MySQL 8+ / MariaDB 10.6+ |
| `recordrelay-adapter-sqlserver` | `recordrelay-adapter-sqlserver/` | Microsoft SQL Server 2019+ |
| `recordrelay-adapter-oracle` | `recordrelay-adapter-oracle/` | Oracle 19c+ |
| `recordrelay-adapter-sqlite` | `recordrelay-adapter-sqlite/` | SQLite (embedded) |
| `recordrelay-adapter-mongodb` | `recordrelay-adapter-mongodb/` | MongoDB 6+ |
| `recordrelay-adapter-cassandra` | `recordrelay-adapter-cassandra/` | Apache Cassandra 4+ |
| `recordrelay-adapter-redis` | `recordrelay-adapter-redis/` | Redis 7+ (Lettuce) |
| `recordrelay-adapter-elasticsearch` | `recordrelay-adapter-elasticsearch/` | Elasticsearch 8+ |
| `recordrelay-adapter-file` | `recordrelay-adapter-file/` | CSV, Excel, JSON Lines, YAML, Parquet |
| `recordrelay-adapter-template` | `recordrelay-adapter-template/` | Yeni connector geliştirme şablonu |

---

## Desteklenen Kaynaklar

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
| Format | Reader | Writer |
|--------|:------:|:------:|
| CSV | ✓ | ✓ |
| Excel (.xlsx) | ✓ | ✓ |
| JSON Lines | ✓ | ✓ |
| YAML | ✓ | ✓ |
| Parquet | ✓ | ✓ |

---

## Kurulum

### Desktop (macOS / Windows / Linux)

[GitHub Releases](https://github.com/MstroCA/recordrelay/releases/latest) sayfasından platformunuza uygun paketi indirin.

### CLI

```bash
curl -L https://github.com/MstroCA/recordrelay/releases/latest/download/rr-cli-x.y.z.tar.gz | tar xz
export PATH="$PWD/rr-cli-x.y.z/bin:$PATH"
rr --version
```

> Gereksinim: Java 21+

### IntelliJ IDEA Plugin

**Settings → Plugins → Marketplace → "RecordRelay"**

---

## Kaynak Koddan Derleme

```bash
git clone https://github.com/MstroCA/recordrelay.git
cd recordrelay

# Derle + statik analiz + unit testler
./gradlew build

# Integration testler (Docker gerektirir)
./gradlew integrationTest

# CLI fat JAR üret
./gradlew :recordrelay-cli:shadowJar
# → recordrelay-cli/build/libs/rr-cli-0.1.0-SNAPSHOT.jar

# Desktop
./gradlew :recordrelay-desktop:installDist

# Plugin ZIP
./gradlew :recordrelay-plugin:buildPlugin
```

---

## Yeni Connector Yazma

`recordrelay-adapter-template` modülünü kopyalayın ve `ContextProviderPort` arayüzünü implement edin:

```java
public class MyConnector implements ContextProviderPort {
    @Override public String connectorId() { return "my-db"; }
    @Override public boolean supports(ConnectionProfile p) { ... }
    // testConnection, listDatabases, schemaInspector, createReader, createWriter, healthCheck
}
```

SPI kaydı için `META-INF/services/io.recordrelay.core.port.out.ContextProviderPort` dosyasına sınıf adını ekleyin. JAR classpath'e atıldığında `ServiceLoader` otomatik keşfeder.

---

## Katkıda Bulunma

Lütfen [CONTRIBUTING.md](CONTRIBUTING.md) dosyasını okuyun. Tüm katkılar Apache-2.0 lisansı altında kabul edilir.

---

## Lisans

[Apache License 2.0](LICENSE) — Copyright 2026 the RecordRelay authors
