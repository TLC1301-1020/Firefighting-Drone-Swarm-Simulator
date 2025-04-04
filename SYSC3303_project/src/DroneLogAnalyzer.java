import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Analyze event logs and calculate the metrics
 * It reads log entries from event_log.txt
 */
public class DroneLogAnalyzer {
    //TODO CHANGE THIS IF NEEDED
    private static final String LOG_FILE = "drone_event_log.txt";
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    // Separate ArrayLists for storing logs by component type
    private static final List<LogEntry> droneLogs = new ArrayList<>();
    private static final List<LogEntry> processFaultLogs = new ArrayList<>();
    private static final List<LogEntry> schedulerListenerLogs = new ArrayList<>();
    private static final List<LogEntry> subsystemLogs = new ArrayList<>();

    /**
     * Reads and analyzes log entries
     * If no log file is found, a message is displayed
     */
    public static void analyzeLogs() {
        readLogs();  // Read and parse the log entries
        if (droneLogs.isEmpty() && subsystemLogs.isEmpty()) {
            System.out.println("No previous log file found.");
            return;
        }
        //TODO: Calculate metrics for drone logs
        //drone array will have all the logs for drone (doesn't matter the id - threadType)

        //TODO: Calculate metrics for subsystem logs
        //subsystem array will have to split the logs based on the
    }

    /**
     * Reads log entries from the event log file and converts them into lists of LogEntry objects
     * Based on component type (drone or subsystem)
     */
    private static void readLogs() {
        try (BufferedReader reader = new BufferedReader(new FileReader(LOG_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                LogEntry logEntry = LogEntry.parse(line);
                if (logEntry != null) {
                    //TODO: CHANGE component name based on what name being stored

                    // Separate the logs based on the component type
                    if (logEntry.getComponent().equals("Drone")) {
                        droneLogs.add(logEntry);
                    } else if (logEntry.getComponent().equals("Subsystem")) {
                        if(logEntry.getThreadType().equals("SchedulerListener")){
                            droneLogs.add(logEntry);
                        }else if(logEntry.getThreadType().equals("processFaults")){
                            processFaultLogs.add(logEntry);
                        }else{
                            subsystemLogs.add(logEntry);
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * Represents a log entry with a timestamp, component, and event code
     * A method to parse log lines into LogEntry objects
     */
    private static class LogEntry {
        private LocalTime timestamp;
        private String component;
        private String event;
        private String threadType;

        /**
         * Parses a log line and creates a LogEntry object.
         * @param logLine The raw log line to parse
         * @return A LogEntry object if parsing is successful, otherwise null
         */
        static LogEntry parse(String logLine) {
            Pattern pattern = Pattern.compile("\\[(.*?)\\] \\[(.*?)\\] \\[(.*?)\\] \\[(.*?)\\]");
            Matcher matcher = pattern.matcher(logLine);

            if (matcher.matches()) {
                LocalTime timestamp = LocalTime.parse(matcher.group(1), formatter);
                String component = matcher.group(2);
                String threadType = matcher.group(3);
                String event = matcher.group(4);

                return new LogEntry(timestamp, component, threadType, event);
            }
            // Return null if the log line doesn't match the expected format
            return null;
        }

        // Constructor to initialize LogEntry object
        LogEntry(LocalTime timestamp, String component,String threadType, String event) {
            this.timestamp = timestamp;
            this.component = component;
            this.threadType = threadType;

            this.event = event;
        }

        //getters
        public String getComponent() {
            return component;
        }

        public LocalTime getTimestamp() {
            return timestamp;
        }

        public String getThreadType() {
            return threadType;
        }

        public String getEvent() {
            return event;
        }
    }


}
