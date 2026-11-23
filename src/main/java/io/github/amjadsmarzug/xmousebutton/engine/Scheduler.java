package io.github.amjadsmarzug.xmousebutton.engine;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Runs a task after a delay. The returned runnable cancels the task if it has not run yet. */
@FunctionalInterface
public interface Scheduler {

    Runnable schedule(Runnable task, long delayMillis);

    /** A daemon timer thread, used by the real application. */
    static Scheduler system() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "xmousebutton-timer");
            thread.setDaemon(true);
            return thread;
        });
        return (task, delayMillis) -> {
            ScheduledFuture<?> future = executor.schedule(task, delayMillis, TimeUnit.MILLISECONDS);
            return () -> future.cancel(false);
        };
    }
}
