import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class SchedulerEventLogger {

    private static final String LOG_FILE = "scheduler_event_log.txt";
    private static final int FLUSH_INTERVAL_SECONDS = 2;

    private final List<SchedulerEventClass> eventBuffer = Collections.synchronizedList(new ArrayList<>());
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });

    private static final SchedulerEventLogger instance = new SchedulerEventLogger();

    private SchedulerEventLogger() {
        try (PrintWriter writer = new PrintWriter(LOG_FILE)) {
            writer.print(""); // Clear old log
        } catch (IOException e) {
            e.printStackTrace();
        }

        scheduler.scheduleAtFixedRate(this::flush, FLUSH_INTERVAL_SECONDS, FLUSH_INTERVAL_SECONDS, TimeUnit.SECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(this::flush));
    }

    public static SchedulerEventLogger getInstance() {
        return instance;
    }

    public void log(String level, String component, String threadTag, String message) {
        eventBuffer.add(new SchedulerEventClass(level, component, threadTag, message));
    }

    // Convenience methods
    public void info(String component, String threadTag, String message) {
        log("INFO", component, threadTag, message);
    }

    public void debug(String component, String threadTag, String message) {
        log("DEBUG", component, threadTag, message);
    }

    public void warn(String component, String threadTag, String message) {
        log("WARN", component, threadTag, message);
    }

    public void error(String component, String threadTag, String message) {
        log("ERROR", component, threadTag, message);
    }

    public void error(String component, String threadTag, String message, Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        log("ERROR", component, threadTag, message + "\n" + sw.toString());
    }

    private void flush() {
        List<SchedulerEventClass> toFlush;
        synchronized (eventBuffer) {
            if (eventBuffer.isEmpty()) return;
            toFlush = new ArrayList<>(eventBuffer);
            eventBuffer.clear();
        }

        try (FileWriter writer = new FileWriter(LOG_FILE, true)) {
            for (SchedulerEventClass e : toFlush) {
                writer.write(e.toString() + System.lineSeparator());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
