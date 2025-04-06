import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class FireIncidentLogAnalyzer {
    private static final String LOG_FILE = "firesubsystem_logs.txt";
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

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

// Helper class to store each log entry
class LogEntry {
    private final LocalTime time;
    private final String action;
    private final String data;

    public LogEntry(LocalTime time, String action, String data) {
        this.time = time;
        this.action = action;
        this.data = data;
    }

    public LocalTime getTime() {
        return time;
    }

    public String getAction() {
        return action;
    }

    public String getData() {
        return data;
    }
}
