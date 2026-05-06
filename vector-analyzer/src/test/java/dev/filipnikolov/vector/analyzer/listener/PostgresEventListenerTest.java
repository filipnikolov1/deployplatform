package dev.filipnikolov.vector.analyzer.listener;

import dev.filipnikolov.vector.analyzer.logtail.LogTailService;
import dev.filipnikolov.vector.analyzer.timeline.TimelineEventService;
import dev.filipnikolov.vector.analyzer.timeline.TimelineEventService.ProcessedDeploymentEvent;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PostgresEventListenerTest {

    @Test
    void restartsLogTailForSuccessfulLifecycleEvents() {
        LogTailService logTail = mock(LogTailService.class);
        PostgresEventListener listener = new PostgresEventListener(
                mock(DataSource.class),
                mock(TimelineEventService.class),
                logTail);

        listener.maybeRestartLogTail(new ProcessedDeploymentEvent(10, "docs", "DEPLOY_FINISHED", "SUCCESS"));
        listener.maybeRestartLogTail(new ProcessedDeploymentEvent(11, "docs", "RESTARTED", "SUCCESS"));
        listener.maybeRestartLogTail(new ProcessedDeploymentEvent(12, "docs", "MANUAL_ROLLBACK", "SUCCESS"));

        verify(logTail, org.mockito.Mockito.times(3)).onDeployFinished("docs");
    }

    @Test
    void doesNotRestartLogTailForFailedDeploys() {
        LogTailService logTail = mock(LogTailService.class);
        PostgresEventListener listener = new PostgresEventListener(
                mock(DataSource.class),
                mock(TimelineEventService.class),
                logTail);

        listener.maybeRestartLogTail(new ProcessedDeploymentEvent(10, "docs", "DEPLOY_FINISHED", "FAILURE"));

        verify(logTail, never()).onDeployFinished("docs");
    }
}
