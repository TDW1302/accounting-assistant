package be.vercauteren.accounting.config;

import be.vercauteren.accounting.dto.InboxScanResult;
import be.vercauteren.accounting.service.InboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.inbox.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class InboxScheduler {

    private final InboxService inboxService;

    @Scheduled(cron = "0 0 3 * * *")
    public void scheduledScan() {
        log.info("Starting scheduled inbox scan");
        InboxScanResult result = inboxService.scan();
        log.info("Scheduled inbox scan result: {} processed, {} matched, {} created, {} errors",
            result.filesProcessed(), result.matched(), result.created(), result.errors());
    }
}
