package be.vercauteren.accounting;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Demarre le contexte complet sur un PostgreSQL jetable. Verifie au passage que
 * les migrations Flyway produisent un schema conforme aux entites, puisque
 * ddl-auto=validate echouerait sinon. Requiert un daemon Docker.
 */
@SpringBootTest
@Testcontainers
class AccountingAssistantApplicationTests {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

	@Test
	void contextLoads() {
	}

}
