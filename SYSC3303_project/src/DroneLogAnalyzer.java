import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.Duration;
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
    private static final String LOG_FILE = "drone_event_log.txt";
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    // Separate ArrayLists for storing logs by component type
    private static final List<LogEntry> droneLogs = new ArrayList<>();
    private static final List<LogEntry> processFaultLogs = new ArrayList<>();
    private static final List<LogEntry> schedulerListenerLogs = new ArrayList<>();
    private static final List<LogEntry> subsystemLogs = new ArrayList<>();
    // Time stamp for starting and ending
    private static LocalTime startPoint;
    private static LocalTime endPoint;

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
            Pattern pattern = Pattern.compile("\\[(.*?)\\] \\[(.*?)\\] \\[(.*?)\\] \\[(.*?)\\] (.*)");
            Matcher matcher = pattern.matcher(logLine);

            if (matcher.matches()) {
                String timestampStr = matcher.group(1).trim(); // Remove the brackets around the timestamp
                LocalTime timestamp = LocalTime.parse(timestampStr, formatter);
                String component = matcher.group(3);
                String threadType = matcher.group(4);
                String event = matcher.group(5);

                // Update startPoint and endPoint
                if (startPoint == null) {
                    startPoint = timestamp; // Set start point only once
                }
                endPoint = timestamp; // Always update the end point

                // Create a LogEntry and add it to the list
                LogEntry entry = new LogEntry(timestamp, component, threadType, event);
                return entry;
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
        public String getComponent() {return component;}
        public LocalTime getTimestamp() {return timestamp;}
        public String getThreadType() {return threadType;}
        public String getEvent() {return event;}
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
                    // Separate the logs based on the component type
                   if (logEntry.getComponent().contains("DroneSubsystem")) {
                        //listening to scheduler
                        if(logEntry.getThreadType().contains("ListeningToScheduler")){
                            schedulerListenerLogs.add(logEntry);
                        //faults
                        }else if(logEntry.getThreadType().contains("ProcessFaults")){
                            processFaultLogs.add(logEntry);
                        //run
                        }else{
                            subsystemLogs.add(logEntry);
                        }
                    }else if(logEntry.getComponent().contains("Drone")) {
                        droneLogs.add(logEntry);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    /**
     * Reads and analyzes log entries
     * If no log file is found, a message is displayed
     */
    public static void analyzeLogs() {
        readLogs();  // Read and parse the log entries
        if (droneLogs.isEmpty() && subsystemLogs.isEmpty()) {
            System.out.println("No previous log file found.");
            return;
        }else{
            //TODO: Calculate metrics for drone logs
            //drone array will have all the logs for drone (doesn't matter the id - threadType)

            //TODO: Calculate metrics for subsystem logs
            //subsystem array will have to split the logs based on the type
            //run thread
            System.out.println("============ Subsystem ============");
            subsystemAverageLatency();
            subsystemThroughput();
            subsystemUtilization();
        }
    }

    public static void subsystemAverageLatency(){
        long totalLatency = 0;
        int requestCount = 0;
        LogEntry previousWaiting = null;
        for(LogEntry log: subsystemLogs){
            if(log.event.contains("Waiting")){
                previousWaiting = log;
            }
            if(log.event.contains("Received")){
                if (previousWaiting != null){
                    totalLatency += Duration.between(previousWaiting.getTimestamp(), log.getTimestamp()).toMillis();
                    requestCount++;
                    previousWaiting = null;
                }
            }
        }
        totalLatency = totalLatency/requestCount;
        System.out.printf("Average latency: %.2f ms\n", (double) totalLatency);
    }

    public static void subsystemThroughput(){
        int requestCount = 0;
        LogEntry previousWaiting = null;
        for(LogEntry log: subsystemLogs){
            if(log.event.contains("Waiting")){
                previousWaiting = log;
            }
            if(log.event.contains("Received")){
                if (previousWaiting != null){
                    requestCount++;
                    previousWaiting = null;
                }
            }
        }
        double throughput = requestCount/calculateTotalTime();
        System.out.printf("Throughput: %.2f per second\n", throughput);
    }
    public static void subsystemUtilization() {
        double totalWorking = 0;
        double totalWaiting = 0;
        LocalTime lastReceived = null;
        LocalTime lastWaiting = null;

        boolean received = false;
        boolean waiting = false;

        for (LogEntry log : subsystemLogs) {
            String event = log.getEvent();

            if (event.contains("Received")) {

                if (waiting) {
                    Duration waitingDuration = Duration.between(lastWaiting, log.getTimestamp());
                    totalWaiting += waitingDuration.toMillis() / 1000.0;
                    waiting = false;
                }

                lastReceived = log.getTimestamp();
                received = true;

            } else if (event.contains("Waiting")) {

                if (received) {
                    if (!waiting) {
                        Duration workingDuration = Duration.between(lastReceived, log.getTimestamp());
                        totalWorking += workingDuration.toMillis() / 1000.0;
                        received = false;
                    }

                    lastWaiting = log.getTimestamp();
                    waiting = true;
                }
            }
        }
        if (totalWaiting == 0) {
            System.out.println("No waiting time recorded, utilization cannot be calculated.");
        } else {
            double utilization = totalWorking / totalWaiting;
            System.out.printf("Utilization: %.2f\n", utilization);
        }
    }


    public static double calculateTotalTime() {
        if (startPoint != null && endPoint != null) {

            Duration duration = Duration.between(startPoint, endPoint);
            return duration.toMillis() / 1000.0;
        } else {
            return 0.0;
        }
    }

    public static void main(String[] args){
        analyzeLogs();
    }

}
