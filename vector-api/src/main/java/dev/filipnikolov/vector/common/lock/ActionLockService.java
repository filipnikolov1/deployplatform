package dev.filipnikolov.vector.common.lock;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Component
public class ActionLockService {

    private final ConcurrentHashMap<String, Semaphore> locks = new ConcurrentHashMap<>();

    public Optional<LockHandle> tryLock(String key) {
        Semaphore lock = locks.computeIfAbsent(key, k -> new Semaphore(1));
        try {
            if (lock.tryAcquire(5, TimeUnit.SECONDS)) {
                return Optional.of(new LockHandle(key, lock, this));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return Optional.empty();
    }

    private void tryRemoveIfIdle(String key, Semaphore lock) {
        locks.compute(key, (k, existing) -> {
            if (existing == null) return null;
            // Re-create race guard: another caller may have removed-then-recreated the
            // semaphore for this key while we were closing the old handle.
            if (existing != lock) return existing;
            if (existing.availablePermits() == 1 && !existing.hasQueuedThreads()) {
                return null;
            }
            return existing;
        });
    }

    public static class LockHandle implements AutoCloseable {
        private final String key;
        private final Semaphore lock;
        private final ActionLockService owner;

        LockHandle(String key, Semaphore lock, ActionLockService owner) {
            this.key = key;
            this.lock = lock;
            this.owner = owner;
        }

        @Override
        public void close() {
            try {
                lock.release();
            } finally {
                owner.tryRemoveIfIdle(key, lock);
            }
        }
    }
}
