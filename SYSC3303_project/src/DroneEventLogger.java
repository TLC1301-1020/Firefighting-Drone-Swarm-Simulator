import java.io.*;
import java.util.*;
import java.util.concurrent.*;
/**
 * Singleton logger class responsible for capturing and writing drone-related events to a log file
 * <p>
 * Supports multiple log levels (INFO, DEBUG, WARN, ERROR) and buffers events before writing to a file
 * at fixed intervals. Ensures thread-safe logging and includes a shutdown hook to flush logs on exit
 * <p>
 * Usage:
 * <pre>
 *     DroneEventLogger.getInstance().info("Drone", "Drone-1", "Payload deployed successfully");
 * </pre>
 */
public class DroneEventLogger {

    private static final String LOG_FILE = "drone_event_log.txt";
    private static final int FLUSH_INTERVAL_SECONDS = 2;

    private final List<DroneEventClass> eventBuffer = Collections.synchronizedList(new ArrayList<>());

    /**
     * Scheduled executor that runs periodic tasks using a single daemon thread
     * Ensures the logging flush task does not prevent shutdown
     */
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });
    /**instance of DroneEventLogger */
    private static final DroneEventLogger instance = new DroneEventLogger();
    /**
     * Private constructor that initializes the log file and sets up periodic
     * and shutdown flushing of logs
     *
     * - Clears the log file on startup
     * - Schedules periodic flushing of log data at fixed intervals
     * - Adds a shutdown to flush logs when terminating
     */
    private DroneEventLogger() {
        try (PrintWriter writer = new PrintWriter(LOG_FILE)) {
            writer.print("");
        } catch (IOException e) {
            e.printStackTrace();
        }

        scheduler.scheduleAtFixedRate(this::flush, FLUSH_INTERVAL_SECONDS, FLUSH_INTERVAL_SECONDS, TimeUnit.SECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(this::flush));
    }
    /**
     * Returns the singleton instance of the DroneEventLogger
     *
     * @return the single instance of DroneEventLogger
     */
    public static DroneEventLogger getInstance() {
        return instance;
    }
    /**
     * Logs a message with the specified log level, component, and thread tag
     *
     * @param level     the log level (e.g., INFO, DEBUG, WARN, ERROR)
     * @param component the component generating the log (e.g., Scheduler, Drone)
     * @param threadTag a tag identifying the thread (e.g., Drone-1, Scheduler-Main)
     * @param message   the log message
     */
    public void log(String level, String component, String threadTag, String message) {
        eventBuffer.add(new DroneEventClass(level, component, threadTag, message));
    }

    /**
     * Logs an INFO level message
     *
     * @param component the component generating the log
     * @param threadTag the thread tag
     * @param message   the log message
     */
    public void info(String component, String threadTag, String message) {
        log("INFO", component, threadTag, message);
    }
    /**
     * Logs a DEBUG level message
     *
     * @param component the component generating the log
     * @param threadTag the thread tag
     * @param message   the log message
     */
    public void debug(String component, String threadTag, String message) {
        log("DEBUG", component, threadTag, message);
    }
    /**
     * Logs a WARN level message
     *
     * @param component the component generating the log
     * @param threadTag the thread tag
     * @param message   the log message
     */
    public void warn(String component, String threadTag, String message) {
        log("WARN", component, threadTag, message);
    }
    /**
     * Logs an ERROR level message
     *
     * @param component the component generating the log
     * @param threadTag the thread tag
     * @param message   the log message
     */
    public void error(String component, String threadTag, String message) {
        log("ERROR", component, threadTag, message);
    }
    /**
     * Logs an ERROR level message along with an exception's stack trace
     *
     * @param component the component generating the log
     * @param threadTag the thread tag
     * @param message   the log message
     * @param e         the exception to log
     */
    public void error(String component, String threadTag, String message, Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        log("ERROR", component, threadTag, message + "\n" + sw.toString());
    }
    /**
     * Flushes all buffered drone events to the log file.
     * <p>
     * Creates a snapshot of the current buffer, clears it,
     * and writes each log entry to the log file.
     * It is synchronized to prevent concurrent modification of the buffer during flushing.
     * <p>
     * This method is called periodically by a scheduled task and once on shutdown.
     */
    private void flush() {
        List<DroneEventClass> toFlush;
        synchronized (eventBuffer) {
            if (eventBuffer.isEmpty()) return;
            toFlush = new ArrayList<>(eventBuffer);
            eventBuffer.clear();
        }

        try (FileWriter writer = new FileWriter(LOG_FILE, true)) {
            for (DroneEventClass e : toFlush) {
                writer.write(e.toString() + System.lineSeparator());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns the current event buffer
     * <p>
     * This method is primarily intended for inspection or testing purposes
     * The returned list may not reflect real-time contents if the buffer is being modified concurrently
     *
     * @return the list of currently buffered drone events
     */
    List<DroneEventClass> getEventBuffer() {
        return eventBuffer;
    }

}