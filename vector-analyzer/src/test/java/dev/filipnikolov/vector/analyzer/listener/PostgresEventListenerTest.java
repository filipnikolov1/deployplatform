package dev.filipnikolov.vector.analyzer.listener;

import dev.filipnikolov.vector.analyzer.logtail.LogTailService;
import dev.filipnikolov.vector.analyzer.timeline.TimelineEventService;
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

        listener.maybeRestartLogTail("""
                {"id":10,"app_name":"docs","event_type":"DEPLOY_FINISHED","status":"SUCCESS"}
                """);
        listener.maybeRestartLogTail("""
                {"id":11,"app_name":"docs","event_type":"RESTARTED","status":"SUCCESS"}
                """);
        listener.maybeRestartLogTail("""
                {"id":12,"app_name":"docs","event_type":"MANUAL_ROLLBACK","status":"SUCCESS"}
                """);

        verify(logTail, org.mockito.Mockito.times(3)).onDeployFinished("docs");
    }

    @Test
    void doesNotRestartLogTailForFailedDeploys() {
        LogTailService logTail = mock(LogTailService.class);
        PostgresEventListener listener = new PostgresEventListener(
                mock(DataSource.class),
                mock(TimelineEventService.class),
                logTail);

        listener.maybeRestartLogTail("""
                {"id":10,"app_name":"docs","event_type":"DEPLOY_FINISHED","status":"FAILURE"}
                """);

        verify(logTail, never()).onDeployFinished("docs");
    }
}
