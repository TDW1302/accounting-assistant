package be.vercauteren.accounting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AccountingAssistantApplication {

	public static void main(String[] args) {
		SpringApplication.run(AccountingAssistantApplication.class, args);
	}

}
