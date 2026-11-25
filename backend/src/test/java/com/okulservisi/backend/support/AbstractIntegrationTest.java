package com.okulservisi.backend.support;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.function.Supplier;

/**
 * Tum backend entegrasyon testlerinin temel sinifi.
 *
 * <p>Amac: docs/TEST_PLAN.md "Ortam izolasyonu" bolumunde tanimlanan kurala uymak -
 * her test run'i kendi ephemeral PostgreSQL container'ini kullanir, dev compose.yaml'daki
 * "ssp-postgres" container'ina HICBIR SEKILDE dokunmaz (farkli, izole, tek kullanimlik container).
 *
 * <p>{@code @ServiceConnection} (Spring Boot 3.1+ / spring-boot-testcontainers) sayesinde
 * spring.datasource.* ozellikleri manuel olarak set edilmez; Spring context, container ayaga
 * kalktiginda otomatik olarak gercek baglanti bilgilerini kullanir.
 *
 * <p><b>ONEMLI - "singleton container" deseni dogru uygulanmali:</b> Container BILEREK
 * {@code @Testcontainers}/{@code @Container} JUnit5 extension'i ILE YONETILMIYOR; bunun yerine
 * statik bir blokta MANUEL olarak bir kez baslatiliyor ve hic durdurulmuyor (JVM kapanirken Ryuk
 * temizler). Neden: {@code @Testcontainers} + statik {@code @Container} alani bir ust sinifta
 * olsa bile, extension her test SINIFININ kendi {@code afterAll}'inda container'i DURDURUR - bir
 * sonraki test sinifi {@code beforeAll}'da ayni (durmus) nesne uzerinde {@code start()} cagirdiginda
 * Testcontainers YENI bir container (yeni portla) olusturur, ama Spring'in test-context cache'i
 * ESKI (artik gecersiz) baglanti bilgileriyle context'i cache'lemis olabilir -> "Connection refused"
 * hatasi (bu tam olarak bu dosyanin ilk suruminde yasanan, gercek Testcontainers run'inda
 * gozlenen bir hataydi; bkz. Test Agent raporu). Manuel static-start deseni, container'in TUM
 * test suite boyunca TEK ve SABIT kalmasini (tek port, tek gercek container) garanti eder.
 *
 * <p>Her test METODU kendi verisini izole etmek icin benzersiz (UUID'li) fixture verisi kullanir
 * (bkz. {@link TestUsers}) - paylasilan container/DB'de METOTLAR ARASI çakışma olmaz.
 *
 * <p>NOT: postgres:16.4 imaji kasten compose.yaml'daki surumle AYNI secildi (ortamlar arasi
 * davranis farkini onlemek icin), ANCAK bu ayri, bagimsiz bir container'dir - compose.yaml
 * stack'i calismiyor olsa da bu testler calisir (Docker Desktop calisiyor olmasi yeterli).
 */
@Tag("integration")
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16.4"))
                    .withDatabaseName("ssp_test")
                    .withUsername("ssp_test")
                    .withPassword("ssp_test");

    static {
        // Bilerek @Container/@Testcontainers KULLANILMIYOR - bkz. sinif Javadoc'u. Tek, kalici,
        // tum test suite'i boyunca paylasilan container. JVM kapaninca Ryuk siler.
        POSTGRES.start();
    }

    @LocalServerPort
    private int port;

    private TestRestTemplate restTemplate;

    protected static PostgreSQLContainer<?> postgresContainer() {
        return POSTGRES;
    }

    /**
     * Spring'in otomatik ayağa kaldırdığı (context-level bean olarak @Autowired edilebilen)
     * {@code TestRestTemplate}'i KASITLI OLARAK kullanmıyoruz: onun varsayılan istek fabrikası
     * (eski {@code sun.net.www.protocol.http.HttpURLConnection} tabanlı
     * {@code SimpleClientHttpRequestFactory}) bilinen bir JDK kısıtına sahip - bir POST isteğine
     * sunucu 401 ile cevap verdiğinde istemci kimlik doğrulama için otomatik "retry" denemeye
     * çalışıyor ve gövde zaten gönderildiği için "cannot retry due to server authentication, in
     * streaming mode" IOException'ı fırlatıyor ({@code setOutputStreaming(false)} dahi bunu
     * engellemedi - gözlemlenen davranış, bkz. Test Agent raporu). Bu SADECE test istemcisinin bir
     * kısıtıdır, backend'in 401 davranışıyla ilgisi yoktur. Çözüm: eski {@code HttpURLConnection}
     * yerine modern {@code java.net.http.HttpClient} tabanlı {@code JdkClientHttpRequestFactory}
     * (Spring Framework 6.1+) kullanmak - bu istemcide bahsedilen retry kısıtı yoktur.
     */
    protected TestRestTemplate restTemplate() {
        if (restTemplate == null) {
            Supplier<ClientHttpRequestFactory> factorySupplier = JdkClientHttpRequestFactory::new;
            RestTemplateBuilder builder = new RestTemplateBuilder()
                    .rootUri("http://localhost:" + port)
                    .requestFactory(factorySupplier);
            restTemplate = new TestRestTemplate(builder);
        }
        return restTemplate;
    }
}
