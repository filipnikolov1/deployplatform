package com.filipnikolov.launchpad.common.lock;

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
                return Optional.of(new LockHandle(lock));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return Optional.empty();
    }

    public static class LockHandle implements AutoCloseable {
        private final ReentrantLock lock;

        LockHandle(ReentrantLock lock) {
            this.lock = lock;
        }

        @Override
        public void close() {
            lock.unlock();
        }
    }
}
