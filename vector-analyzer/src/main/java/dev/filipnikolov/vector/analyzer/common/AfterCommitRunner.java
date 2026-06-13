package dev.filipnikolov.vector.analyzer.common;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Defers a side effect until the surrounding transaction commits. Without this,
 * @Async work or SSE events fired mid-transaction reference rows other
 * connections can't see yet. Runs immediately when no transaction is active.
 */
@Component
public class AfterCommitRunner {

    public void run(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
