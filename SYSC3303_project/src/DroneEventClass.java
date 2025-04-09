import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class DroneEventClass {
    private final String timestamp;
    private final String level;
    private final String component;
    private final String threadTag;
    private final String message;
    /**
     * Constructs a new DroneEventClass with the details
     * <p>
     * The timestamp is automatically set to the current time in the format HH:mm:ss.SSS.
     *
     * @param level the log level (e.g., INFO, DEBUG, ERROR)
     * @param component the component where the event occurred
     * @param threadTag the tag identifying the thread that handled the event
     * @param message the message describing the event
     */
    public DroneEventClass(String level, String component, String threadTag, String message) {
        this.timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
        this.level = level;
        this.component = component;
        this.threadTag = threadTag;
        this.message = message;
    }

    @Override
    public String toString() {
        return String.format("[%s] [%s] [%s] [%s] %s", timestamp, level, component, threadTag, message);
    }
    /**
     * @return the log level (e.g., INFO, ERROR)
     */
    public String getLevel(){
        return level;
    }
    /**
     * @return the component that generated the event (e.g., Scheduler, Drone)
     */
    public String getComponent(){
        return component;
    }
    /**
     * @return indicates the thread （e.g., drone id, run(), processFault)
     */
    public String getThreadTag(){
        return threadTag;
    }
    /**
     * @return the log message content
     */
    public String getMessage(){
        return message;
    }
}