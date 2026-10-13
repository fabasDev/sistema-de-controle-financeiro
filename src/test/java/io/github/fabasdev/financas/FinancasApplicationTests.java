package io.github.fabasdev.financas;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class FinancasApplicationTests {

    /** Sobe o app inteiro: Flyway aplica as migrações e o Hibernate confere se as entidades batem. */
    @Test
    void contextLoads() {
    }
}
