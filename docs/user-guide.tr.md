# RecordRelay — Kullanıcı Kılavuzu

## Bu kılavuz kime hitap eder?

Bu kılavuz, gerçek bir veritabanı kaydını yerel veya test ortamında yeniden oluşturması gereken herkese yöneliktir — bir production hatasını local'de debug etmeye çalışan geliştiriciler, gerçek verilere benzer test verileriyle çalışmak isteyen test uzmanları, veri setini bir meslektaşıyla paylaşması gereken analistler, olay inceleyen SRE'ler ya da belirli bir müşterinin verilerinin nasıl göründüğünü merak eden ürün yöneticileri. Derin bir veritabanı bilgisi gerekmez; bu kılavuz her kavramı ve her ekranı sıfırdan açıklar.

---

## RecordRelay nedir?

Bir geliştirici olduğunuzu ve bir müşterinin hata bildirdiğini düşünün. Hata yalnızca o müşteriye özgü verilerle oluşuyor — sipariş, adres, fatura ve hesap ayarlarının çok özel bir kombinasyonu. Bunu elle yeniden oluşturmak neredeyse imkânsız. RecordRelay tam olarak bunu çözer: "production'daki #12345 numaralı müşteriyi benim local veritabanıma kopyala" dersiniz ve uygulama bunu yapar — o müşteriye ait tüm siparişler, faturalar, adresler ve ayarlarla birlikte.

Arka planda RecordRelay, veritabanı tablolarınız arasındaki bağlantıları otomatik olarak takip eder. Bir müşteri kaydını klonladığınızda, önce o müşteriyle ilişkili tüm siparişleri bulur; sonra o siparişlerle ilişkili tüm sipariş satırlarını; sonra o sipariş satırlarıyla ilişkili tüm ürün kayıtlarını — belirlediğiniz derinliğe kadar böyle devam eder. Veritabanı yapısını önceden bilmenize gerek yok; RecordRelay keşfeder.

RecordRelay 14 farklı veri kaynağıyla çalışır: tüm büyük SQL veritabanları (PostgreSQL, MySQL, SQL Server, Oracle, SQLite), NoSQL veritabanları (MongoDB, Cassandra, Redis, Elasticsearch) ve dosya formatları (CSV, Excel, JSON, YAML, Parquet). Masaüstü uygulaması, komut satırı aracı ya da IntelliJ IDEA eklentisi olarak çalışır.

---

## Bilmeniz gereken kavramlar

### Kayıt / Satır
Bir veritabanı tablosunda saklanan tek bir öğe. Örneğin bir müşteri, bir sipariş ya da bir ürün. Elektronik tablo analojisiyle, bir kayıt bir satıra karşılık gelir.

### İlişki / Yabancı Anahtar (FK)
İki tablo arasındaki bağlantı. Örneğin "siparişler" tablosu, siparişi veren müşterinin ID'sini saklayabilir. Bu bağlantıya yabancı anahtar (foreign key) denir — "bu sipariş o müşteriye aittir" anlamına gelir. RecordRelay bu bağlantıları otomatik olarak takip ederek klonladığınız kayıtla ilişkili tüm verileri bulur.

### Derinlik
RecordRelay'in kaç "atlama" yapacağını belirler. Derinlik 1 yalnızca kök kaydı kopyalar. Derinlik 2, kök kaydı ve doğrudan onunla ilişkili tüm kayıtları kopyalar. Derinlik 3, bir kademe daha derine iner — kök, birinci seviye bağlantılar ve ikinci seviye bağlantılar. Çoğu kullanım senaryosu için derinlik 3 veya 4 yeterlidir.

### Klonlama
Bir kaydı (ve yapılandırılan derinliğe kadar tüm ilişkili kayıtları) bir veritabanından diğerine kopyalama işlemi. Kaynak veritabanı salt okunurdur; hedef veritabanı kopyalanan verileri alır.

### Ortam
Veritabanı bağlantılarının mantıksal grubu. Örneğin "production", "staging" ve "local" adında üç ortam tanımlayabilirsiniz. Her ortama bir veya daha fazla bağlantı atarsınız. Bu sayede klonlama yaparken doğru bağlantı çiftini hızlıca seçebilirsiniz.

### Bağlantı
Bir veritabanına erişmek için gereken bilgilerin toplamı: sunucu adresi, port, veritabanı adı, kullanıcı adı ve parola. Bağlantılar bir kez Bağlantılar ekranında tanımlanır ve diğer tüm ekranlarda tekrar kullanılır.

### .rrpkg paketi
Klonlanmış kayıtları içeren taşınabilir bir dosya (`.rrpkg` uzantılı bir ZIP arşivi). Doğrudan bir hedef veritabanına yazmak yerine klonu bir `.rrpkg` dosyasına dışa aktarabilir ve canlı veritabanı erişimi olmayan bir meslektaşınızla paylaşabilirsiniz.

### KBV / Maskeleme
KBV, Kişisel Belirleyici Veri anlamına gelir — e-posta adresleri, telefon numaraları, fiziksel adresler, IBAN'lar ve TC kimlik numaraları gibi veriler. RecordRelay bu alanları hedef veritabanına yazmadan önce otomatik olarak gerçekçi görünen sahte değerlerle değiştirebilir. Maskeleme deterministiktir: aynı giriş her zaman aynı maskelenmiş çıktıyı üretir, böylece tablolar arası yabancı anahtar bağlantıları geçerliliğini korur.

---

## Hızlı Başlangıç (5 dakika)

1. **Kaynak bağlantınızı ekleyin** — Sol kenar çubuğundan **Bağlantılar** ekranını açın. **Ekle…** butonuna tıklayın, kaynak veritabanınızın (örn. production) bilgilerini girin ve **Bağlantıyı Test Et** ile doğrulayın.

2. **Hedef bağlantınızı ekleyin** — Adım 1'i hedef veritabanınız (örn. local) için tekrarlayın.

3. **Klonlama ekranını açın** — Sol kenar çubuğundan **Klonlama** seçeneğine tıklayın.

4. **1. Adım — Bağlantı seçin** — Kaynak bağlantısını ilk açılır listeden, hedef bağlantısını ikinci açılır listeden seçin.

5. **2. Adım — Kaydı seçin** — Kök tabloyu açılır listeden seçin (ya da **Tespit Et** butonuna tıklayın, RecordRelay en olası başlangıç tablosunu önersin). Primary key kolonunu ve klonlamak istediğiniz kaydın ID'sini girin.

6. **3. Adım — Seçenekleri ayarlayın** — Derinlik kaydırıcısını 3 olarak ayarlayın. Çakışma çözümlemesini varsayılan **Yeni Kimlik Ata** olarak bırakın.

7. **Context Kopyala butonuna tıklayın** — Alt kısımdaki log panelini izleyin. "Klonlama tamamlandı" mesajı göründüğünde kayıt ve ilişkili tüm veriler hedef veritabanına yazılmıştır.

---

## Ekranlar

### Klonlama (Clone Context)

Klonlama ekranı RecordRelay'in merkezidir. Uygulamadaki her şey bu tek işlemi destekler: gerçek bir kaydı — tüm bağlamıyla birlikte — bir veritabanından diğerine kopyalamak.

Ekran dört ardışık adımdan oluşur.

---

#### 1. Adım — Bağlantı Seç

**Kaynak (Source Database)** — Kopyalama yaptığınız veritabanı. Bu veritabanı asla değiştirilmez; RecordRelay yalnızca okur.

**Hedef (Target Database)** — Kopyalanan kayıtların yazılacağı veritabanı.

**Export modu onay kutusu** — Bu kutu işaretlendiğinde RecordRelay hiçbir veritabanına yazmaz. Bunun yerine klonlanan kayıtları belirlediğiniz klasöre bir `.rrpkg` dosyası olarak kaydeder. Canlı veritabanı erişimi olmayan bir meslektaşınızla veri paylaşmak ya da bir kaydın anlık görüntüsünü arşivlemek istediğinizde kullanın.

---

#### 2. Adım — Tablo & Kayıt

**Başlangıç Tablosu (Root Table)** — Klonlamak istediğiniz kaydı içeren tablo. Kaynak bağlantı seçildiğinde RecordRelay mevcut tabloların listesini bu açılır listeye otomatik olarak yükler. Hangi tablodan başlayacağınızdan emin değilseniz **Tespit Et** butonuna tıklayın — RecordRelay veritabanı ilişkilerini analiz ederek en olası "kök" tabloları önerir.

**Primary Key Kolonu** — Kök tabloda kaydı benzersiz şekilde tanımlayan kolon. Çoğu tablo için bu `id`'dir. RecordRelay bunu mümkün olduğunda otomatik doldurur.

**Kayıt ID'si** — Klonlamak istediğiniz kaydın primary key değeri. Örneğin 12345 numaralı müşteriyi klonlamak istiyorsanız buraya `12345` yazın.

**Önizle butonu** — Gerçek klonlamayı çalıştırmadan önce **Önizle**'ye tıklayarak hangi tabloların etkileneceğini ve kaç satır kopyalanacağını görebilirsiniz. Bu güvenli, salt okunur bir işlemdir.

---

#### 3. Adım — Seçenekler

**İlişki derinliği** — Kaydırıcı, kaç seviye ilişkili tabloyu takip edeceğini belirler. 1 değeri yalnızca kök kaydı kopyalar. 3 değeri (önerilen varsayılan) kök kaydı, doğrudan ona bağlı tüm kayıtları ve bunlara bağlı kayıtları kopyalar.

**PII Maskele** — İşaretlendiğinde RecordRelay kişisel verileri (e-posta adresleri, telefon numaraları, fiziksel adresler, IBAN'lar ve TC kimlik numaraları) hedef veritabanına yazmadan önce gerçekçi sahte değerlerle değiştirir. Aynı orijinal değer her zaman aynı maskelenmiş değeri üretir, dolayısıyla kayıtlar arası bağlantılar geçerli kalır.

---

#### Çakışma Çözümlemesi (On conflict)

**Çakışma nasıl çözülsün?** açılır listesi, RecordRelay hedef veritabanına bir kayıt yazmaya çalışırken aynı ID'li bir kaydın zaten mevcut olduğu durumda ne yapılacağını belirler. Bu, anlaşılması en önemli ayarlardan biridir.

---

**Yeni Kimlik Ata** *(varsayılan — çoğu durumda önerilen)*

RecordRelay klonlanan her kayda yeni bir ID atar ve tablolar arası tüm bağlantıları yeni ID'lere göre günceller. Yazma işleminden sonra veritabanının otomatik artış sayacını en yüksek tahsis edilen ID'nin üzerine ilerletir; böylece uygulamanızın ilerleyen zamanlarda oluşturacağı kayıtlar RecordRelay'in kullandığı ID'lerle çakışmaz.

Ne zaman kullanılır: Neredeyse her zaman. Bu, hedefte zaten veri olsa bile çakışmayı garanti eden en güvenli seçenektir. Hedef veritabanınızda veri varsa ve kopyalanan kayıtların mevcut veriyle karışmamasını istiyorsanız bu seçeneği kullanın.

Örnek: Production'dan müşteri #12345'i klonlarsınız. Hedef veritabanında bu müşteri yeni bir ID alır — diyelim ki #9001. Tüm siparişler de yeni ID'ler alır; sipariş tablolarındaki `customer_id` alanları #9001'i gösterecek şekilde otomatik güncellenir.

---

**Ad Alanında İzole Et** (Isolate Namespace)

RecordRelay tüm ID'leri öteleyerek veya önekleyerek birden fazla klonlanmış bağlamın aynı hedef veritabanında mevcut veriyle ve birbirleriyle çakışmadan bir arada bulunmasını sağlar.

Ne zaman kullanılır: Aynı hedef veritabanına aynı anda birden fazla farklı kaydı klonlamanız ve her klonun bağımsız kalmasını istemeniz durumunda. Örneğin paralel test senaryoları çalıştıran bir QA ekibi — her senaryo kendi izole veri dilimine sahip olur.

---

**Çakışmada Dur** (Fail Safe)

RecordRelay herhangi bir şey yazmadan önce hedef veritabanını kontrol eder. Yazılacak tablolardan herhangi biri zaten kayıt içeriyorsa tüm işlem iptal edilir — hiçbir şey yazılmaz.

Ne zaman kullanılır: Temiz bir hedefle çalıştığınızdan mutlak garantiye ihtiyaç duyduğunuzda. Sıfırdan bir test ortamı kurmak için veya her çalıştırmadan önce veritabanını sıfırlayan otomatik test akışları için uygundur. Hedef boş değilse işlem hemen ve açıkça başarısız olur — eski ve yeni verinin sessizce karışmasına izin vermez.

---

**Mevcutu Atla** (Skip Existing)

RecordRelay her kaydı eklemeye çalışır. Hedef veritabanında aynı ID'li bir kayıt zaten mevcutsa o kayıt sessizce atlanır — mevcut kayda dokunulmaz ve klonlama kalan kayıtlarla devam eder.

Ne zaman kullanılır: Daha önce klonladığınız bir kaydı yenilediğinizde (örneğin dün klonladığınız müşteriyi bugün güncellemek istiyorsunuz) ve yalnızca eksik kayıtları eklemek istediğinizde. Ya da bir veritabanından özdeş bir kopyasına klonlama yaparken, çoğu kaydın zaten mevcut olduğu durumlarda.

Not: Bu seçenek mevcut kayıtları güncellemez. Kaynak kayıt son klonlamadan bu yana değiştiyse bu değişiklikler hedefte yansıtılmaz.

---

#### 4. Adım — Alan Değiştirme (İsteğe Bağlı)

Alan değiştirme tablosu, hedef veritabanına yazarken belirli kolon değerlerini değiştirmenizi sağlar. Örneğin:

- `created_by` alanını `test-kullanici` olarak değiştirerek klonlanan kayıtların gerçek bir production kullanıcısı tarafından oluşturulmuş gibi görünmesini önleyebilirsiniz.
- `durum` alanını `TASLAK` olarak değiştirerek klonlanan siparişlerin production akışlarını tetiklemesini engelleyebilirsiniz.
- `eposta` alanını belirli bir test adresine değiştirerek sistemin gerçek müşteriye e-posta göndermesini önleyebilirsiniz.

Tablodaki her satırın üç kolonu vardır:
- **Tablo (boş=hepsi)** — Override'ın uygulanacağı tablo. Bu alan boş bırakılırsa bu adlaki kolona sahip tüm tablolara uygulanır.
- **Kolon** — Değeri değiştirilecek kolon.
- **Yeni Değer** — Hedef veritabanına yazılacak değer.

Yeni bir satır eklemek için **+ Ekle** butonuna, seçili satırı silmek için **Sil** butonuna tıklayın.

---

#### Role Göre Yaygın Kullanım Senaryoları

**Geliştirici: "Bir hatayı local'de yeniden üretmem gerekiyor"**
1. Klonlama ekranını açın. Kaynak olarak production'ı, hedef olarak local veritabanınızı seçin.
2. Hatanın en ilgili olduğu tabloyu kök tablo olarak seçin (örn. `siparisler`). Hatayı tetikleyen siparişin ID'sini girin.
3. Yeterli bağlamı yakalamak için derinliği 3 veya 4 olarak ayarlayın.
4. Çakışma çözümlemesini **Yeni Kimlik Ata** olarak bırakın.
5. İsterseniz **PII Maskele**'yi etkinleştirin.
6. **Context Kopyala** butonuna tıklayın. Debugger'ınızı açın.

**Test Uzmanı: "Gerçek production benzeri verilerle test etmem gerekiyor"**
1. Klonlama ekranını açın. Kaynak olarak production'ı, hedef olarak test ortamınızı seçin.
2. **PII Maskele**'yi etkinleştirin — test ortamına kopyalarken her zaman maskeleyin.
3. Derinliği 3 olarak ayarlayın. **Yeni Kimlik Ata** seçeneğini kullanın.
4. İsteğe bağlı olarak bir Alan Değiştirme ekleyin: `eposta` alanını `qa-test@ornek.com` olarak ayarlayın.
5. Klonlayın. Test paketinizi yeni verilerle çalıştırın.

**Analist: "Verileri bir meslektaşımla paylaşmam gerekiyor"**
1. Klonlama ekranını açın, kaynak veritabanınızı seçin.
2. **Export modu**'nu işaretleyin ve bir çıktı klasörü seçin.
3. Kök tabloyu ve kayıt ID'sini seçin.
4. Meslektaşınızın gerçek kişisel verileri görmemesi gerekiyorsa **PII Maskele**'yi etkinleştirin.
5. **Paket Dışa Aktar** butonuna tıklayın. `.rrpkg` dosyasını paylaşın.

---

### Ortamlar

Ortamlar ekranı, veritabanı bağlantılarınızı mantıksal gruplara ayırmanızı sağlar. Örneğin "Production", "Staging" ve "Local Geliştirme" adında üç ortam oluşturabilirsiniz.

Ortam oluşturmak için **Ekle…** butonuna tıklayın, bir ad ve isteğe bağlı açıklama girin, kaydedin. Ortama bağlantı atamak için ortamı düzenleyin ve ilgili bağlantıları seçin.

---

### Bağlantılar

Bağlantılar ekranı, çalıştığınız her veritabanına ait kimlik bilgilerini ve sunucu ayrıntılarını sakladığınız yerdir. Her bağlantının şu alanları vardır:

- **Ad** — Okunabilir bir etiket (örn. "prod-postgres", "local-mysql")
- **Tip** — Veritabanı motoru (PostgreSQL, MySQL, MongoDB, vb.)
- **Host** — Sunucu adresi
- **Port** — Port numarası (veritabanı tipine göre otomatik doldurulur)
- **Veritabanı** — Veritabanı/şema adı
- **Kullanıcı** ve **Parola** — Kimlik bilgileri

Kaydetmeden önce **Bağlantıyı Test Et** butonuyla bağlantının çalıştığını doğrulayın. Bağlantılar yalnızca yerel olarak saklanır, hiçbir yere iletilmez.

---

### Keşif (Şema Keşfi)

Keşif ekranı, herhangi bir sorgu yazmadan bağlı herhangi bir veritabanının yapısını incelemenizi sağlar. Bir bağlantı seçin, bir veritabanı seçin, ardından bir tablo seçip **Kolonları İncele** butonuna tıklayın — tüm kolon adlarını, veri tiplerini, null değer alıp alamadıklarını ve hangisinin primary key olduğunu görebilirsiniz.

Bu ekran, klon çalıştırmadan önce doğru tablo adını ve primary key kolon adını teyit etmek istediğinizde işe yarar.

---

### İzleme (Kontrol Paneli)

İzleme ekranı, uygulamayı açtığınızdan bu yana gerçekleşen tüm işlemlerin oturum genelinde görünümünü sunar.

**Kopyalama Geçmişi** — Bu oturumda çalıştırdığınız her klonlama işlemini gösteren tablo: kök tablo, kayıt ID'si, kaynak, hedef, aktarılan kayıt sayısı, süre ve başarı durumu.

**Özet çipler** — En üstte hızlı istatistikler: toplam aktarılan kayıt, işlem sayısı, başarısız işlemler ve son işlemin süresi.

**Bağlantı Sağlığı** — Tüm kaydedilmiş bağlantıların güncel erişilebilirlik ve gecikme bilgilerini gösteren tablo. Güncellemek için **Sağlık Kontrol** butonuna tıklayın.

---

### Graf Görünümü

Graf Görünümü, veritabanı şemanızı görsel bir ilişki haritası olarak gösterir — hangi tabloların birbirine bağlı olduğunu ve bu bağlantıların nasıl ilerlediğini ortaya koyar.

**Nasıl kullanılır:**
1. Araç çubuğundaki açılır listeden bir bağlantı seçin.
2. Kök tablo olarak başlamak istediğiniz tabloyu seçin.
3. **Keşfet** butonuna tıklayın. RecordRelay veritabanı meta verilerini sorgulayarak ilişki grafiğini çizer.

**Grafiği okumak:**
- Her kutu (düğüm) bir tabloyu temsil eder.
- Kutular arasındaki çizgiler ilişkileri temsil eder. Düz çizgiler tanımlanmış yabancı anahtarlardır; kesik çizgiler RecordRelay'in kolon adı kalıplarından çıkardığı sezgisel bağlantılardır.
- Herhangi bir düğümün üzerine gelin — yalnızca o düğüme bağlı kenarlar vurgulanır, tek bir tablonun ilişkilerini izlemek kolaylaşır.

**Gezinme:**
- **Zoom** kaydırıcısını veya **+/−** butonlarını kullanın.
- Fareyi kaydırırken **Ctrl** tuşunu basılı tutun.
- Tüm düğümleri görmek için **Ekrana Sığdır** butonuna tıklayın.

---

### Şema Karşılaştırma (Migration Drift)

Şema Karşılaştırma ekranı iki veritabanı arasındaki yapısal farklılıkları tespit eder. Staging veritabanınızın production ile aynı şemaya sahip olduğunu doğrulamak ya da bir migration'ı planlamak istediğinizde kullanışlıdır.

**Nasıl kullanılır:**
1. Kaynak bağlantı ve veritabanı seçin (genellikle production — referans).
2. Hedef bağlantı ve veritabanı seçin (genellikle staging veya local — kontrol edilecek olan).
3. **Analiz Et** butonuna tıklayın.

Sonuçlar tablosunda her fark gösterilir:
- **Eksik Tablo** — kaynak veritabanında var, hedefte yok
- **Fazla Tablo** — hedef veritabanında var, kaynakta yok
- **Eksik Kolon** — kaynak tabloda var, hedef tabloda yok
- **Fazla Kolon** — hedef tabloda var, kaynak tabloda yok
- **Tip Uyuşmazlığı** — her iki tabloda da var ama veri tipi farklı

**SQL Üret** butonu, hedef şemayı kaynak şemayla uyumlu hale getirecek bir SQL yama komut dosyası oluşturur. Güvenli değişiklikler doğrudan SQL olarak üretilir; riskli değişiklikler (kolon silmek gibi) yorum satırı olarak eklenir.

---

### Satır Sayısı Farkı (Row Count Diff)

Satır Sayısı Farkı ekranı iki veritabanı arasında her tablonun kaç kayıt içerdiğini karşılaştırır. Kayıtların içeriğine bakmaz — yalnızca sayıları karşılaştırır.

**Tablo durumları:**
- **Eşit** — her iki veritabanında aynı sayı
- **Hedef eksik** — hedef veritabanında kaynaktan daha az kayıt var
- **Hedef fazla** — hedef veritabanında kaynaktan daha fazla kayıt var

---

### Sorgu Analizcisi (Query Analyzer)

Sorgu Analizcisi, bağlı herhangi bir veritabanında sorgu çalıştırmanızı ve sonuçları bir tabloda görmenizi sağlar. İki modu vardır:

**Akış modu (görsel sorgu oluşturucu)**
SQL bilgisi gerekmeden sürükle-bırak ile sorgu oluşturun.
1. Araç çubuğundan bağlantı ve veritabanı seçin.
2. Sol paneldeki herhangi bir tabloya çift tıklayarak canvas'a ekleyin.
3. Bir kolonun yanındaki yuvarlak port butonuna tıklayarak JOIN başlatın, ardından başka bir tablonun portuna tıklayarak tamamlayın. INNER, LEFT veya RIGHT JOIN seçeneği sorulur.
4. Sonuçlarda görünmesini istediğiniz kolonları işaretleyin/kaldırın.
5. Sağ panelde WHERE filtreleri, ORDER BY ve satır limiti ekleyin.
6. Oluşturulan SQL önizleme panelinde canlı olarak güncellenir. Çalıştırmak için **Çalıştır** butonuna tıklayın.

**SQL modu (ham editör)**
Doğrudan SELECT sorgusu yazın ve Çalıştır'a tıklayın.

Sorgu Analizcisi salt okunurdur — veri değiştiremez.

---

### Bağlantı Sağlığı (Connection Health)

Bağlantı Sağlığı ekranı kayıtlı tüm bağlantılarınızı ping'ler ve durumu raporlar. Kontrolü başlatmak için **Tümünü Test Et** butonuna tıklayın.

Her bağlantı için şunları görürsünüz:
- **Durum** — bağlantının başarılı mı yoksa başarısız mı olduğu
- **Gecikme** — bağlantı girişiminin kaç milisaniye sürdüğü
- **Detay** — bağlantı başarısız olursa hata mesajı

---

### Maskeleme Kapsamı (Masking Coverage)

Maskeleme Kapsamı ekranı bir veritabanı şemasını tarayarak kişisel veri içerme ihtimali yüksek kolonları tespit eder ve her kolonun bir maskeleme kuralıyla kapsanıp kapsanmadığını gösterir.

**Tarama** butonuna tıklayın. Sonuçlar tablosu RecordRelay'in KBV riski taşıdığını değerlendirdiği her kolonu, tespit edilen kategoriyi (E-POSTA, TELEFON, ADRES, IBAN, KİMLİK_NO) ve **PII Maskele** etkin bir klonlama çalıştırsaydınız maskelenip maskelenmeyeceğini gösterir.

**Yalnızca açık olanları göster** onay kutusunu kullanarak maskeleme kurallarıyla kapsanmamış kolonları filtreleyin.

---

### Klonlama Öntanımları (Clone Presets)

Klonlama Öntanımları ekranı, kaynak, hedef, kök tablo, ID, derinlik, maskeleme ayarları ve alan değiştirmelerden oluşan eksiksiz bir klonlama yapılandırmasını kaydetmenizi sağlar; böylece her seferinde formu doldurmak yerine tek tıkla yeniden çalıştırabilirsiniz.

Öntanım kaydetmek için:
1. Ekranın alt kısmındaki **Yeni Öntanım Kaydet** bölümündeki alanları doldurun.
2. Açıklayıcı bir ad verin (örn. "Günlük prod müşteri yenileme").
3. **Öntanımı Kaydet** butonuna tıklayın.

Öntanım üst listeye eklenir. Çalıştırmak için öntanımı seçin ve **Şimdi Çalıştır** butonuna tıklayın.

---

### Zamanlanmış Senkronizasyon (Scheduled Sync)

Zamanlanmış Senkronizasyon ekranı, Klonlama Öntanımlarını bir zamanlama katmanıyla genişletir. Bir senkronizasyon tanımlarsınız (öntanımlarla aynı parametreler) ve buna bir zamanlama eklersiniz — cron ifadesi veya dakika cinsinden basit bir aralık.

**Cron ifadesi** — örneğin `0 6 * * 1-5` "her hafta içi sabah 6:00'da çalıştır" anlamına gelir.

**Aralık** — örneğin `60` "her 60 dakikada bir çalıştır" anlamına gelir.

Her ikisini de boş bırakırsanız yalnızca **Şimdi Çalıştır** ile manuel olarak tetiklenebilen bir senkronizasyon oluşturursunuz.

---

## CLI Referansı

`rr` komut satırı aracı, tüm temel RecordRelay işlemlerini terminal veya CI/CD hattından destekler:

```bash
# Bağlantı ekle
rr conn add --name prod --type POSTGRESQL --host db.prod --port 5432 --database mydb --user admin

# Kayıt klon
rr clone --entity customer --id 12345 --from prod --target local --depth 3

# PII maskelemeyle klon
rr clone --entity customer --id 12345 --from prod --target local --depth 3 --mask-pii

# .rrpkg dosyasına dışa aktar
rr export --entity customer --id 12345 --from prod --output ./exports/

# .rrpkg dosyasını içe aktar
rr import customer-12345-1234567890.rrpkg --target local

# İki ortamı karşılaştır
rr diff --entity customer --id 12345 --from prod --to staging

# Şema keşfi
rr discover --source prod
```

Tam komut listesi için bkz. [CLI Reference](cli-reference.md).

---

## Role Göre İş Akışları

### Test Uzmanı Olarak

Amacınız, gerçek production kullanıcı verilerini doğrudan kullanmadan gerçekçi verilerle uygulama davranışını test etmektir.

**Sprint öncesi test veritabanı kurma:**
1. Test veritabanınızın kayıtlı bir bağlantı olarak erişilebilir olduğundan emin olun.
2. **Klonlama Öntanımları** ekranını açın ve yaygın test senaryoları için öntanımlar oluşturun (örn. "Aktif sipariş müştericisi", "Ödeme başarısız müşteri", "Yeni müşteri").
3. Her öntanımda **PII Maskele**'yi etkinleştirin ve Alan Değiştirme ile `eposta` alanını test adresine ayarlayın.
4. Her sabah tüm öntanımları **Şimdi Çalıştır** ile çalıştırarak test verilerini yenileyin.

**Belirli bir hatayı yeniden üretme:**
1. Hata raporundaki production kayıt ID'sini alın.
2. **Klonlama** ekranını açın, kaynak = production, hedef = test ortamınız.
3. **PII Maskele**'yi etkinleştirin, uygun derinliği ayarlayın, **Yeni Kimlik Ata**'yı seçin.
4. Klonlayın. Test ortamındaki uygulamayı açın. Hatayı yeniden üretin.

---

### Geliştirici Olarak

Amacınız sorunları yerel ortamda yeniden üretmek veya elle INSERT yazmadan gerçekçi verilerle çalışmaktır.

**Bir production hatasını local'de yeniden üretme:**
1. **Klonlama** ekranını açın, kaynak = production, hedef = local.
2. Hata raporundaki kayıt ID'sini kullanın.
3. Derinliği 3–4 olarak ayarlayın.
4. **PII Maskele**'yi etkinleştirin.
5. **Yeni Kimlik Ata**'yı seçin.
6. Klonlayın. Debugger'ınızı ekleyin.

**Bilinmeyen bir şemayı anlamak:**
1. **Graf Görünümü**'nü açın, kaynak bağlantınızı seçin.
2. Bir tabloyu kök olarak belirleyin ve **Keşfet**'e tıklayın.
3. Hover ile tabloların bağlantılarını takip edin.

**Local şemanızın güncel olup olmadığını kontrol etme:**
1. **Şema Karşılaştırma**'yı açın, kaynak = production, hedef = local.
2. **Analiz Et**'e tıklayın.

---

### Veri Analisti Olarak

Amacınız, karmaşık sorgular yazmadan veya veritabanı erişimi talep etmeden analiz için belirli verilere ulaşmaktır.

**Çalışmak için veri anlık görüntüsü alma:**
1. **Klonlama** ekranını açın. Kaynak olarak veri kaynağınızı seçin.
2. Veri yönetimi politikanız gerektiriyorsa **PII Maskele**'yi etkinleştirin.
3. Çevrimdışı çalışmak veya paylaşmak istiyorsanız **Export modu**'nu işaretleyin.
4. Klonlayın veya dışa aktarın.

**Mevcut veriyi keşfetme:**
1. **Keşif** ekranını açın, bağlantınızı seçin.
2. Veritabanları ve tablolar arasında gezinin.
3. **Sorgu Analizcisi**'ni kullanarak canlı veriye keşif sorguları çalıştırın.

---

### SRE / DevOps Mühendisi Olarak

Amacınız veri yenileme süreçlerini ortam yönetimi akışlarınıza entegre etmektir.

**Staging'i otomatik güncel tutma:**
1. **Zamanlanmış Senkronizasyon** ekranını açın ve yeni bir senkronizasyon oluşturun.
2. Kaynak = production, hedef = staging.
3. **PII Maskele**'yi etkinleştirin (production dışı ortamlara yazmadan önce her zaman gerekli).
4. Cron zamanlaması ayarlayın: `0 3 * * *` (her gece saat 03:00).
5. Kaydedin.

**CI/CD'ye entegrasyon:**
```bash
rr clone --entity order --id 99 --from prod --target ci-db --depth 3 --mask-pii --conflict REGENERATE_IDENTITIES
```

---

## Sık Sorulan Sorular

**S: "Derinlik" tam olarak ne anlama gelir?**
Derinlik, RecordRelay'in kaç seviye bağlantılı tabloyu takip edeceğini belirler. Müşteriyi derinlik 1 ile klonlarsanız yalnızca müşteri kaydını alırsınız. Derinlik 2'de müşteri kaydına doğrudan bağlı tablolardaki tüm kayıtları da alırsınız (örn. adresleri, siparişleri). Derinlik 3'te bir kademe daha ilerler — siparişlerin satırlarını, sipariş geçmişi olaylarını vb. Seçtiğiniz kayıttan N atlama uzağında durana kadar bağlantı ağında genişleyen bir keşif düşünün.

**S: FAIL_SAFE yerine ne zaman REGENERATE_IDENTITIES kullanmalıyım?**
Hedef veritabanınızda zaten veri varsa ve kopyalanan kayıtlarla karışmasını istemiyorsanız **Yeni Kimlik Ata** kullanın. **Çakışmada Dur**'u ise tamamen temiz bir hedefle çalıştığınızdan mutlak garantiye ihtiyaç duyduğunuzda kullanın; hedefte herhangi bir veri varsa işlem hemen durur.

**S: SKIP_EXISTING ne zaman kullanılmalı?**
Daha önce klonladığınız bir kaydı yenilediğinizde ve yalnızca eksik kayıtları eklemek istediğinizde kullanın. Mevcut kayıtları güncellemediğini unutmayın — kaynak değiştiyse o değişiklikler hedefte yansıtılmaz.

**S: .rrpkg dosyası tam olarak nedir?**
Yapılandırılmış biçimde klonlanmış kayıtları ve şema, kaynak ve ilişkileri açıklayan bir manifest içeren ZIP arşividir. Kendi başına taşınabilir — veritabanınıza herhangi bir erişim olmadan paylaşılabilir ve içe aktarılabilir.

**S: Veri maskelebilir miyim? Hangi alanlar maskelenir?**
Evet. 3. Adım'daki **PII Maskele** onay kutusunu işaretleyin. RecordRelay E-POSTA, TELEFON, ADRES, IBAN ve KİMLİK_NO kalıplarıyla eşleşen kolon adlarını otomatik olarak maskeler. Maskeleme deterministiktir: aynı orijinal değer her zaman aynı maskelenmiş değeri üretir.

**S: Klonlama kaynak veritabanını etkiler mi?**
Hayır. RecordRelay kaynak veritabanını yalnızca okur. Hiçbir şekilde yazmaz, değiştirmez veya diğer kullanıcıları etkileyecek şekilde kilitlemez.

**S: Klonum başarıyla tamamlandı ama bazı tablolar 0 kayıt yazıldı gösteriyor. Neden?**
Bu normaldir. RecordRelay keşfettiği tüm ilişkileri takip eder, ancak klonladığınız varlık için birçok ilişkili tabloda kayıt olmayabilir. Örneğin başarısız ödemesi olmayan bir müşteriyi klonlarsanız `basarisiz_odemeler` tablosunda 0 kayıt görürsünüz — kopyalanacak kayıt yoktur. Bu 0 bilgilendirici bir değerdir, hata değil.

**S: Klonlama tip uyuşmazlığı uyarısıyla başarısız oldu. Ne yapmalıyım?**
Bu genellikle kaynak ve hedef veritabanları arasında küçük bir şema farkı olduğu anlamına gelir (örneğin bir kolon kaynakta enum tipi, hedefte düz metin). **Şema Karşılaştırma** ekranını açarak tam farkı tespit edin, SQL yama komut dosyası oluşturun ve hedef veritabanına uygulayın. Ardından klonu yeniden çalıştırın.

**S: Farklı veritabanı türleri arasında klonlama yapabilir miyim (örn. PostgreSQL'den MySQL'e)?**
Evet. RecordRelay, veritabanından bağımsız bir ara gösterim kullanır; PostgreSQL'den MySQL'e, MySQL'den SQLite'a, CSV dosyasından veritabanına klonlama yapabilirsiniz. Tip eşlemeleri otomatik olarak yapılır.
