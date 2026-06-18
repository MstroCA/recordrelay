# Contributing to RecordRelay

RecordRelay'e katkıda bulunduğunuz için teşekkürler! Bu döküman, geliştirme sürecini, kod standartlarını ve yeni connector yazma adımlarını açıklar.

---

## İçindekiler

1. [Geliştirme Ortamı](#geliştirme-ortamı)
2. [Proje Yapısı](#proje-yapısı)
3. [Kod Standartları](#kod-standartları)
4. [Test Yazma](#test-yazma)
5. [Yeni Connector Yazma](#yeni-connector-yazma)
6. [PR Süreci](#pr-süreci)
7. [Commit Mesajları](#commit-mesajları)

---

## Geliştirme Ortamı

### Gereksinimler
- Java 21 (Temurin önerilir)
- Docker Desktop (Testcontainers için)
- IntelliJ IDEA veya herhangi bir Java IDE

### İlk Kurulum

```bash
git clone https://github.com/recordrelay/recordrelay.git
cd recordrelay
./gradlew build          # Derleme + tüm kontroller
./gradlew test           # Sadece unit testler
./gradlew integrationTest # Testcontainers integration testleri (Docker gerekir)
```

### Kalite Araçları

| Araç | Amaç | Komut |
|------|------|-------|
| Spotless | Kod formatı (Google Java Format) | `./gradlew spotlessApply` |
| Checkstyle | Yapısal kurallar | `./gradlew checkstyleMain` |
| SpotBugs | Statik analiz, bug pattern'leri | `./gradlew spotbugsMain` |
| JaCoCo | Kod coverage (%70 minimum) | `./gradlew jacocoTestReport` |

PR açmadan önce şunu çalıştırın:
```bash
./gradlew spotlessApply && ./gradlew check
```

---

## Proje Yapısı

```
core/                    ← Buraya adapter kodu GİRMEZ
  domain/                ← Değişmez iş nesneleri (record/immutable)
  port/in/               ← Driving ports (use case arayüzleri)
  port/out/              ← Driven ports (connector arayüzleri)
  spi/                   ← ServiceLoader registry
  engine/                ← Transfer orchestration

connectors/
  connector-{db}/        ← Her DB için ayrı modül
    src/main/java/...    ← Port implement eden sınıflar
    src/main/resources/
      META-INF/services/ ← SPI kayıt dosyaları
    src/test/java/...    ← Unit + Testcontainers testleri
```

---

## Kod Standartları

- **Java 21**: Record'lar, sealed interface'ler, pattern matching tercih edilir.
- **Immutability**: Domain nesneleri `record` veya `@Value` (Lombok yok) olmalı.
- **Null safety**: `Optional` kullanın; `null` dönmeyin ve kabul etmeyin.
- **Exception handling**: Checked exception'lar connector boundary'de `ConnectorException` (unchecked) olarak wrap edilmeli.
- **Javadoc**: `public` API'lar için zorunlu. Implementation detayı yazma — "ne" değil "neden".
- **Yorum satırı**: Yalnızca non-obvious constraint veya workaround'lar için. "Bu metod X yapar" türü yorumlar kabul edilmez.
- **Metod uzunluğu**: Max 60 satır (Checkstyle enforce eder).
- **Cyclomatic complexity**: Max 10 (Checkstyle enforce eder).

### Paket Yapısı

```
io.recordrelay.core.domain.*
io.recordrelay.core.port.in.*
io.recordrelay.core.port.out.*
io.recordrelay.core.spi.*
io.recordrelay.core.engine.*
io.recordrelay.connector.postgresql.*
io.recordrelay.connector.mongodb.*
io.recordrelay.cli.*
io.recordrelay.desktop.*
io.recordrelay.plugin.*
```

---

## Test Yazma

### Unit Test

```java
@ExtendWith(MockitoExtension.class)
class TransferEngineTest {

    @Mock
    private RecordReader reader;

    @Mock
    private RecordWriter writer;

    @InjectMocks
    private DefaultTransferEngine engine;

    @Test
    void shouldTransferAllRecords() {
        // Arrange
        var job = TransferJob.builder()...build();
        // Act + Assert (AssertJ)
        assertThat(result.transferredCount()).isEqualTo(100);
    }
}
```

### Integration Test (Testcontainers)

```java
@Testcontainers
@Tag("integration")
class PostgreSqlConnectorIT {

    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void shouldListDatabases() {
        var connector = new PostgreSqlConnector(buildProfile(postgres));
        assertThat(connector.listDatabases()).isNotEmpty();
    }
}
```

Integration testleri `integrationTest` source set'inde bulunur ve `./gradlew integrationTest` ile ayrıca çalıştırılır.

---

## Yeni Connector Yazma

### 1. Modül Oluşturma

`connectors/` altında yeni bir Gradle alt projesi açın:

```
connectors/connector-{dbname}/
  build.gradle.kts
  src/main/java/io/recordrelay/connector/{dbname}/
    {Dbname}Connector.java         ← DataSourceConnector impl
    {Dbname}SchemaInspector.java   ← SchemaInspector impl
    {Dbname}RecordReader.java      ← RecordReader impl
    {Dbname}RecordWriter.java      ← RecordWriter impl
  src/main/resources/META-INF/services/
    io.recordrelay.core.port.out.DataSourceConnector
```

### 2. Port'ları Implement Etme

```java
public final class MyDbConnector implements DataSourceConnector {

    @Override
    public String connectorId() {
        return "mydb"; // benzersiz tanımlayıcı
    }

    @Override
    public boolean supports(ConnectionProfile profile) {
        return "mydb".equals(profile.type());
    }

    @Override
    public Connection connect(ConnectionProfile profile) throws ConnectorException {
        // ...
    }
}
```

### 3. SPI Kaydı

`src/main/resources/META-INF/services/io.recordrelay.core.port.out.DataSourceConnector` dosyasını oluşturun:

```
io.recordrelay.connector.mydb.MyDbConnector
```

`ConnectorRegistry`, `ServiceLoader` aracılığıyla bu sınıfı otomatik olarak keşfeder.

### 4. `settings.gradle.kts`'e Ekleme

```kotlin
include("connectors:connector-mydb")
```

### 5. Test

Testcontainers ile gerçek bir DB container'ı ayağa kaldırarak integration testi yazın.

---

## PR Süreci

1. `develop` branch'inden fork alın veya feature branch açın: `feature/connector-cassandra`
2. Değişikliklerinizi yapın ve testleri geçirin: `./gradlew check`
3. PR açarken şablonu doldurun: ne değişti, neden, nasıl test edildi.
4. En az 1 reviewer onayı gerekir.
5. CI tüm matriste (Linux/macOS/Windows) yeşil olmalı.
6. Squash merge — commit geçmişi temiz kalır.

### Branch İsimlendirme

```
feature/connector-cassandra
fix/postgresql-schema-introspection
chore/update-dependencies
docs/connector-guide
```

---

## Commit Mesajları

[Conventional Commits](https://www.conventionalcommits.org/) formatı:

```
feat(connector-postgresql): add schema introspection for partitioned tables
fix(core): handle null column default values in ColumnMeta
chore(deps): upgrade Testcontainers to 1.20.0
docs(contributing): add connector writing guide
test(connector-mongodb): add collection listing integration test
```

Tipler: `feat`, `fix`, `chore`, `docs`, `test`, `refactor`, `perf`, `ci`

---

## Lisans

Tüm katkılar [Apache-2.0](LICENSE) lisansı altında kabul edilir. Katkıda bulunarak, yazdığınız kodun bu lisans altında yayınlanmasına onay vermiş olursunuz.
