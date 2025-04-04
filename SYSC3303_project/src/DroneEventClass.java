import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DroneEventClass {
    private final String timestamp;
    private final String level;
    private final String component;
    private final String threadTag;
    private final String message;

    public DroneEventClass(String level, String component, String threadTag, String message) {
        this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
        this.level = level;
        this.component = component;
        this.threadTag = threadTag;
        this.message = message;
    }

    @Override
    public String toString() {
        return String.format("[%s] [%s] [%s] [%s] %s", timestamp, level, component, threadTag, message);
    }
}