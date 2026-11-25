package com.okulservisi.backend.support;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Faz C/D - Testcontainers altyapisinin dogrulama testi.
 *
 * <p>Kapsam (docs/TEST_PLAN.md "Temiz DB migration" satiri): Spring context'in gercek, izole,
 * ephemeral bir PostgreSQL container'ina karsi basarili sekilde ayaga kalktigini ve DataSource'un
 * o container'a baglandigini dogrular.
 *
 * <p>NOT: Bu testin yazildigi tarihte backend'de henuz hicbir Flyway migration dosyasi (V1__...sql)
 * yoktur (sadece bos BackendApplication iskeleti var). Bu nedenle "tum migration'larin hatasiz
 * uygulandigi" iddiasi su an icin TRIVIAL olarak dogru (0 migration calistirilir, 0 hata olur) -
 * gercek kapsam Backend Agent V1 migration'lari (app_user, refresh_token, password_reset_token vb.
 * docs/DATABASE_DESIGN.md) ekledikce bu test tarafindan otomatik olarak yeniden dogrulanacaktir
 * (migration sayisi arttikca context yuklenemezse bu test FAIL verir).
 */
class ApplicationContextSmokeTest extends AbstractIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void contextLoadsAgainstIsolatedPostgresContainer() throws Exception {
        assertThat(postgresContainer().isRunning()).isTrue();

        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.isValid(2)).isTrue();
            assertThat(connection.getMetaData().getURL()).contains(postgresContainer().getDatabaseName());
        }
    }
}
