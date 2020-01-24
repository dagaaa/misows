package pl.edu.agh.misows;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class Main {
    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {
        Scheduler scheduler = new Scheduler();
        scheduler.scheduleTasks().get();
    }
}
