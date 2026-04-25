package dev.filipnikolov.vector.common.lock;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class ActionLockService {

    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public Optional<LockHandle> tryLock(String key) {
        ReentrantLock lock = locks.computeIfAbsent(key, k -> new ReentrantLock());
        try {
            if (lock.tryLock(5, TimeUnit.SECONDS)) {
                return Optional.of(new LockHandle(key, lock));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return Optional.empty();
    }

    /**
     * Removes the lock entry if it's currently held by exactly one holder
     * (the caller about to release it) and has no waiters. Called from
     * LockHandle.close() to keep the map bounded.
     */
    private void tryRemoveIfIdle(String key, ReentrantLock lock) {
        locks.compute(key, (k, existing) -> {
            if (existing == null) return null;
            if (existing != lock) return existing;
            if (existing.getHoldCount() == 1 && !existing.hasQueuedThreads()) {
                return null;
            }
            return existing;
        });
    }

    public class LockHandle implements AutoCloseable {
        private final String key;
        private final ReentrantLock lock;

        LockHandle(String key, ReentrantLock lock) {
            this.key = key;
            this.lock = lock;
        }

        @Override
        public void close() {
            try {
                tryRemoveIfIdle(key, lock);
            } finally {
                lock.unlock();
            }
        }
    }
}
