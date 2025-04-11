import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * A thread-safe singleton logger for Scheduler events.
 * <p>
 * Logs messages of various levels (INFO, DEBUG, WARN, ERROR) and flushes them periodically to a log file.
 * The logger uses a daemon thread to periodically write buffered log entries to disk and ensures graceful flushing at shutdown.
 */
public class SchedulerEventLogger {
    /** Log file name for storing scheduler events */
    private static final String LOG_FILE = "scheduler_event_log.txt";
    /** Time interval in seconds between automatic flushes to disk */
    private static final int FLUSH_INTERVAL_SECONDS = 2;
    /** Internal synchronized buffer to hold pending log events */
    private final List<SchedulerEventClass> eventBuffer = Collections.synchronizedList(new ArrayList<>());
    /** Executor service for running periodic flushes in a background daemon thread */
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });
    /**  instance of the logger to be used for logging */
    private static final SchedulerEventLogger instance = new SchedulerEventLogger();

    /**
     * Initializes the log file (clears old content), starts periodic flushing, and registers a shutdown */
    private SchedulerEventLogger() {
        try (PrintWriter writer = new PrintWriter(LOG_FILE)) {
            writer.print(""); // Clear old log
        } catch (IOException e) {
            e.printStackTrace();
        }

        scheduler.scheduleAtFixedRate(this::flush, FLUSH_INTERVAL_SECONDS, FLUSH_INTERVAL_SECONDS, TimeUnit.SECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(this::flush));
    }

    /**
     * Returns the instance of the logger
     *
     * @return the shared {@code SchedulerEventLogger} instance
     */
    public static SchedulerEventLogger getInstance() {
        return instance;
    }

    /**
     * Logs an event with the specified level, component, thread tag, and message
     *
     * @param level     the log level (eg INFO, DEBUG, ERROR)
     * @param component the component emitting the log (eg "Scheduler")
     * @param threadTag the tag or thread name (eg "SP", "SD")
     * @param message   the log message
     */
    public void log(String level, String component, String threadTag, String message) {
        eventBuffer.add(new SchedulerEventClass(level, component, threadTag, message));
    }

    /**
     * Logs an INFO-level event
     *
     * @param component the component emitting the log
     * @param threadTag the thread or subsystem tag
     * @param message   the log message
     */
    public void info(String component, String threadTag, String message) {
        log("INFO", component, threadTag, message);
    }

    /**
     * Logs a DEBUG-level event
     *
     * @param component the component emitting the log
     * @param threadTag the thread or subsystem tag
     * @param message   the log message
     */
    public void debug(String component, String threadTag, String message) {
        log("DEBUG", component, threadTag, message);
    }

    /**
     * Logs a WARN-level event
     *
     * @param component the component emitting the log
     * @param threadTag the thread or subsystem tag
     * @param message   the log message
     */
    public void warn(String component, String threadTag, String message) {
        log("WARN", component, threadTag, message);
    }

    /**
     * Logs an ERROR-level event
     *
     * @param component the component emitting the log
     * @param threadTag the thread or subsystem tag
     * @param message   the log message
     */
    public void error(String component, String threadTag, String message) {
        log("ERROR", component, threadTag, message);
    }

    /**
     * Logs an ERROR-level event and appends the exception stack trace
     *
     * @param component the component emitting the log
     * @param threadTag the thread or subsystem tag
     * @param message   the log message
     * @param e         the exception to log
     */
    public void error(String component, String threadTag, String message, Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        log("ERROR", component, threadTag, message + "\n" + sw.toString());
    }

    /**
     * Flushes all buffered log entries to the log file.
     * This method is triggered periodically or on JVM shutdown.
     */
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
