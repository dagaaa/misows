package pl.edu.agh.misows;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class Scheduler {

    private final ScheduledExecutorService scheduledExecutorService;

    public Scheduler() {
        this.scheduledExecutorService = Executors.newScheduledThreadPool(1);
    }

    public ScheduledFuture<?> scheduleTasks() throws IOException {
        ReadWriteTask task = new ReadWriteTask();

        return scheduledExecutorService.scheduleAtFixedRate(
                task,
                0L,
                1L,
                TimeUnit.MINUTES
        );
    }
}
