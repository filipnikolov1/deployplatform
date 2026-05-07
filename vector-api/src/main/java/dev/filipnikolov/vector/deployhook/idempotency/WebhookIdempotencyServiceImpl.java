package dev.filipnikolov.vector.deployhook.idempotency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class WebhookIdempotencyServiceImpl implements WebhookIdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(WebhookIdempotencyServiceImpl.class);
    private static final int PURGE_OLDER_THAN_MINUTES = 10;

    private final WebhookIdempotencyRepository repository;

    public WebhookIdempotencyServiceImpl(WebhookIdempotencyRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryRegister(String signature) {
        try {
            repository.saveAndFlush(new WebhookIdempotencyRecord(signature, LocalDateTime.now()));
            return true;
        } catch (DataIntegrityViolationException e) {
            log.warn("Webhook replay detected for signature prefix={}", safePrefixOf(signature));
            return false;
        }
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void purgeExpiredEntries() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(PURGE_OLDER_THAN_MINUTES);
        repository.deleteByReceivedAtBefore(cutoff);
    }

    private static String safePrefixOf(String sig) {
        if (sig == null) return "null";
        return sig.length() > 12 ? sig.substring(0, 12) + "..." : sig;
    }
}
