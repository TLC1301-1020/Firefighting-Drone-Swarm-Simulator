import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.regex.*;

public class SchedulerLogAnalyzer {
    private static final String LOG_FILE = "scheduler_event_log.txt";
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    public static void analyzeLogs() {
        //TODO: Uncomment analyzeLogs() in Scheduler -> main()
        List<ParsedLogEntry> entries = parseLogFile();
        if (entries.isEmpty()) {
            System.out.println("No logs found to analyze.");
            return;
        }

        System.out.println("\n=== METRICS ANALYSIS ===");
        for (ParsedLogEntry entry : entries) {
            System.out.println(entry);
        }
        //TODO: computeResponseTimes(events);
        //TODO: computeThroughput(events);
        //TODO: computeUtilization(events);
        //TODO: Whatever else you want
        System.out.println("=========================\n");
    }

    private static List<ParsedLogEntry> parseLogFile() {
        List<ParsedLogEntry> parsedEntries = new ArrayList<>();
        Pattern logPattern = Pattern.compile(
                "\\[(.*?)\\] \\[(.*?)\\] \\[(.*?)\\] \\[(.*?)\\] (.*)"
        );

        try {
            List<String> lines = Files.readAllLines(Paths.get(LOG_FILE));
            for (String line : lines) {
                Matcher matcher = logPattern.matcher(line);
                if (matcher.matches()) {
                    String timestamp = matcher.group(1);
                    String level = matcher.group(2);
                    String component = matcher.group(3);
                    String threadTag = matcher.group(4);
                    String message = matcher.group(5);

                    parsedEntries.add(new ParsedLogEntry(
                            LocalDateTime.parse(timestamp, formatter),
                            level, component, threadTag, message
                    ));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return parsedEntries;
    }

    // Helper class to store parsed events
    private static class ParsedLogEntry {
        private final LocalDateTime timestamp;
        private final String level;
        private final String component;
        private final String threadTag;
        private final String message;

        public ParsedLogEntry(LocalDateTime timestamp, String level, String component, String threadTag, String message) {
            this.timestamp = timestamp;
            this.level = level;
            this.component = component;
            this.threadTag = threadTag;
            this.message = message;
        }

        public String toString() {
            return String.format("[%s] [%s] [%s] [%s] %s",
                    formatter.format(timestamp), level, component, threadTag, message);
        }

        // Getters here for future filtering/analysis
        public LocalDateTime getTimestamp() { return timestamp; }
        public String getLevel() { return level; }
        public String getComponent() { return component; }
        public String getThreadTag() { return threadTag; }
        public String getMessage() { return message; }
    }
}
