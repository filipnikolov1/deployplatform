package dev.filipnikolov.vector.analyzer.common;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class AfterCommitRunnerTest {

    @Test
    void runsImmediatelyWhenNoTransactionIsActive() {
        AtomicBoolean ran = new AtomicBoolean(false);
        new AfterCommitRunner().run(() -> ran.set(true));
        assertThat(ran).isTrue();
    }
}
