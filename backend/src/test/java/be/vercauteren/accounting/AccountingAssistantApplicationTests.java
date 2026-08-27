package be.vercauteren.accounting;

import static org.assertj.core.api.Assertions.assertThat;

import be.vercauteren.accounting.entity.User;
import be.vercauteren.accounting.repository.UserRepository;
import be.vercauteren.accounting.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
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

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void contextLoads() {
	}

	/**
	 * L'auteur des factures creees par le scan d'inbox planifie. Il doit exister
	 * — sinon la contrainte ck_invoice_author_required fait echouer le scan — et
	 * rester inconnectable, puisque rien ne s'authentifie sous cette identite.
	 */
	@Test
	void theTechnicalUserExistsAndCannotLogIn() {
		User system = userRepository.findByUsername(UserService.SYSTEM_USERNAME).orElseThrow();

		assertThat(system.isEnabled()).isFalse();
		assertThat(passwordEncoder.matches("system", system.getPassword())).isFalse();
		assertThat(passwordEncoder.matches("", system.getPassword())).isFalse();
		assertThat(passwordEncoder.matches(system.getPassword(), system.getPassword())).isFalse();
	}
}
