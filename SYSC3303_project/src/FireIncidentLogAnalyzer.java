import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Analyzes a log file from the Fire Incident Subsystem to calculate key metrics:
 * <ul>
 *     <li>Lifetime of system activity</li>
 *     <li>Total fire incidents sent</li>
 *     <li>Total idle time</li>
 *     <li>Utilization percentage (active time vs. idle)</li>
 * </ul>
 */
public class FireIncidentLogAnalyzer {

    /**
     * file name to be read in and parsed for event logs that are then analyzed for metrics */
    private static final String LOG_FILE = "firesubsystem_logs.txt";
    /**
     * Formatter instance for printing and parsing date-time objects with the following example format: hour(24):minute:second.millisecond */
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    /**
     * Reads the log file and calculates system metrics such as:
     * start time, end time, total incidents, idle time, and utilization*/
    public static void main(String[] args) {
        List<LogEntry> entries = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(LOG_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Expected log format: timestamp - action - data
                String[] parts = line.split(" - ", 3);
                if (parts.length < 3) continue;
                LocalTime time = LocalTime.parse(parts[0].trim(), formatter);
                String action = parts[1].trim();
                String data = parts[2].trim();
                entries.add(new LogEntry(time, action, data));
            }
        } catch (IOException e) {
            System.err.println("Error reading log file: " + e.getMessage());
            return;
        }

        if (entries.isEmpty()) {
            System.out.println("No log entries found.");
            return;
        }

        // Determine start and end time
        LocalTime startTime = entries.get(0).getTime();
        LocalTime endTime = entries.get(entries.size() - 1).getTime();
        long lifetimeMillis = java.time.Duration.between(startTime, endTime).toMillis();

        // Count fire incidents sent (log entries with "Sending Incident")
        int fireIncidentCount = 0;
        long idleMillis = 0;
        LocalTime idleStart = null;

        for (LogEntry entry : entries) {
            if (entry.getAction().equals("Sending Incident")) {
                fireIncidentCount++;
            }
            if (entry.getAction().equals("STS Idle Start")) {
                idleStart = entry.getTime();
            }
            if (entry.getAction().equals("STS Idle End") && idleStart != null) {
                idleMillis += java.time.Duration.between(idleStart, entry.getTime()).toMillis();
                idleStart = null;
            }
        }

        double utilization = 100.0;
        if (lifetimeMillis > 0) {
            // Utilization is the percent of time the system was active (not idle)
            utilization = ((lifetimeMillis - idleMillis) / (double) lifetimeMillis) * 100.0;
        }

        // Print out the metrics
        System.out.println("=== Metrics ===");
        System.out.println("Start Time: " + startTime);
        System.out.println("End Time: " + endTime);
        System.out.println("Lifetime (ms): " + lifetimeMillis);
        System.out.println("Total fire incidents sent out: " + fireIncidentCount);
        System.out.println("Total idle time (ms): " + idleMillis);
        System.out.println("Utilization (% active): " + utilization);
    }
}

/**
 * Represents a parsed log entry from the fire incident log.
 * Each log contains a timestamp, action type, and related metadata.
 */
class LogEntry {
    /** The timestamp of the log entry */
    private final LocalTime time;
    /** The type of action performed (e.g., "Sending Incident", "Idle Start") */
    private final String action;
    /** Additional information or metadata about the action */
    private final String data;

    /**
     * Constructs a new {@code LogEntry} with time, action, and data components.
     *
     * @param time   The timestamp of the log entry
     * @param action The type of action performed (e.g., "Sending Incident", "Idle Start")
     * @param data   Additional information or metadata about the action
     */
    public LogEntry(LocalTime time, String action, String data) {
        this.time = time;
        this.action = action;
        this.data = data;
    }

    /**
     * @return The LocalTime of the log
     */
    public LocalTime getTime() {
        return time;
    }

    /**
     * @return action as a string for the log
     */
    public String getAction() {
        return action;
    }


    /**
     * @return the Additional information or metadata about the action for the log
     */
    public String getData() {
        return data;
    }
}
