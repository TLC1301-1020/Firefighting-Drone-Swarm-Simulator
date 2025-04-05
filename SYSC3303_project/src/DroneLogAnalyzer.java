import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*  2025 04 05
    TODO:   Listening to Scheduler metrics
            //TODO: LTSResponseTime()
            //TODO: LTSUtilization()
            //TODO: LTSThroughput()
    TODO:   Test methods (?)
    TODO:   Javadoc
 */
/**
 * Analyze event logs and calculate the metrics
 * It reads log entries from event_log.txt
 */
public class DroneLogAnalyzer {
    private static final String LOG_FILE = "drone_event_log.txt";
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    // Separate ArrayLists for storing logs by component type
    private static final HashMap<String,List<LogEntry>> drones = new HashMap<>();

    private static final List<LogEntry> droneLogs = new ArrayList<>();
    private static final List<LogEntry> processFaultLogs = new ArrayList<>();
    private static final List<LogEntry> schedulerListenerLogs = new ArrayList<>();
    private static final List<LogEntry> subsystemLogs = new ArrayList<>();

    // Time stamp for starting and ending
    private static LocalTime startPoint;
    private static LocalTime endPoint;
    private static int droneTotal = 0;
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
                    startPoint = timestamp;
                }
                endPoint = timestamp;

                // Create a LogEntry and add it to the list
                LogEntry entry = new LogEntry(timestamp, component, threadType, event);
                return entry;
            }
            // Return null if the log line doesn't match the expected format
            return null;
        }


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

                        if(logEntry.getThreadType().contains("ListeningToScheduler")){
                            schedulerListenerLogs.add(logEntry);

                        }else if(logEntry.getThreadType().contains("ProcessFaults")){
                            processFaultLogs.add(logEntry);

                        }else{
                            subsystemLogs.add(logEntry);
                        }
                    }else if(logEntry.getComponent().contains("Drone")) {
                        //update number of drones if needed
                        if(Integer.parseInt(logEntry.getThreadType()) > droneTotal) droneTotal = Integer.parseInt(logEntry.getThreadType());
                       droneLogs.add(logEntry);
                    }
                }
                //drone starts from 0
                droneTotal += 1;
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
        }else{
            sortDrones();
            System.out.println("=================== Drones ===================");
            droneMetrics();
            System.out.println("=================== Subsystem - run ===================\n");
            responseTime(subsystemLogs);
            throughput(subsystemLogs,calculateTotalTime(subsystemLogs));
            subsystemLatency();
            utilization(subsystemLogs,calculateTotalTime(subsystemLogs));
            System.out.println("\n============ Subsystem - ListeningToScheduler ============\n");
            //TODO
            System.out.println("============ Subsystem - ProcessFault ============\n");
            responseTime(processFaultLogs);
            throughput(processFaultLogs,calculateTotalTime(processFaultLogs));
            utilization(processFaultLogs,calculateTotalTime(processFaultLogs));
        }
    }

    //TODO: Haven't check if the output is right
    public static double droneRunTime(List<LogEntry> drone){
        LocalTime start = null;
        double totalTime = 0;
        int countTask = 0;
        for(LogEntry log: drone){
            if(log.getEvent().contains("Assigned")){
                start = log.getTimestamp();
            }
            if(log.getEvent().contains("Arrived back at base") && start != null){
                totalTime += Duration.between(start, log.getTimestamp()).toMillis() / 1000.0;
                countTask++;
                start = null;
            }
        }

        double average = totalTime/countTask;
        if(average > 0) {
            System.out.printf("Average running time: %.4fs\n", average);
        }else {
            System.out.println("Average running time: Not available");
        }
        return totalTime;
    }

    public static void droneUtilization(List<LogEntry> drone, double lifetime){
        double workTime = 0;
        LocalTime workStart = null;

        for(LogEntry log: drone){
            if(log.getEvent().contains("Assigned") && workStart == null){
                workStart = log.getTimestamp();

            }else if(log.getEvent().contains("Arrived back at base") && workStart != null){
                workTime += Duration.between(workStart, log.getTimestamp()).toMillis() / 1000.0;
                workStart = null;
            }
        }
        double utilization = workTime / lifetime;

        if (utilization == 0) {
            System.out.println("Utilization: Not available");
        } else {
            System.out.printf("Utilization: %.4f\n", utilization);
        }
    }
    //Subsystem metrics
    public static void subsystemLatency(){
        double totalLatency = 0;
        int requestCount = 0;
        LogEntry previousWaiting = null;
        for(LogEntry log: subsystemLogs){
            if(log.event.contains("Waiting")){
                previousWaiting = log;
            }
            if(log.event.contains("Received")){
                if (previousWaiting != null){
                    totalLatency += Duration.between(previousWaiting.getTimestamp(), log.getTimestamp()).toMillis() / 1000.0;
                    requestCount++;
                    previousWaiting = null;
                }
            }
        }
        totalLatency = totalLatency/requestCount;
        System.out.printf("Average Latency: %.4fs\n", (double) totalLatency);
    }

    //shared methods for calculating the metrics
    //throughput: Units completed / total time
    public static void throughput(List<LogEntry> logs, double time){
        int requestCount = 0;
        LogEntry previousWaiting = null;
        for(LogEntry log: logs){
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
        if(time == 0){
            System.out.println("No Throughput result available.");
        }else{

            double throughput = requestCount/time;
            System.out.printf("Throughput: %.2f/s\n", throughput);
        }

    }

    //utilization: busy time / waiting time
    public static void utilization(List<LogEntry> logs, double lifetime) {
        double totalWorking = 0;
        LocalTime lastReceived = null;
        boolean received = false;

        for (LogEntry log : logs) {
            String event = log.getEvent();
            if (event.contains("Received")) {
                lastReceived = log.getTimestamp();
                received = true;

            } else if (event.contains("Waiting")) {
                if (received) {
                        Duration workingDuration = Duration.between(lastReceived, log.getTimestamp());
                        totalWorking += workingDuration.toMillis() / 1000.0;
                        received = false;
                }

            }
        }

        double utilization = totalWorking / lifetime;
        System.out.printf("Utilization: %.4f\n", utilization);

    }
    //average response time: total response time / # of request
    public static void responseTime(List<LogEntry> logs) {
        double responseTimes = 0;
        int requestCount = 0;
        LocalTime requestStart = null;

        for (LogEntry log : logs) {
            String event = log.getEvent();
            if (event.contains("Received")) {
                requestStart = log.getTimestamp();
            } else if (requestStart != null && event.contains("Waiting")) {
                responseTimes += Duration.between(requestStart, log.getTimestamp()).toMillis() / 1000.0;
                requestCount++;
                requestStart = null;
            }
        }
        System.out.printf("Response Time: %.4fs\n", responseTimes/requestCount);
    }

    //overall lifetime
    public static double calculateTotalTime(List<LogEntry> logs) {
        startPoint = logs.getFirst().getTimestamp();
        endPoint = logs.getLast().getTimestamp();
        if (startPoint != null && endPoint != null) {
            Duration duration = Duration.between(startPoint, endPoint);
            return duration.toMillis() / 1000.0;
        } else {
            return 0.0;
        }
    }

    public static void sortDrones() {
        for (LogEntry log : droneLogs) {
            String threadType = log.getThreadType();
            if (drones.containsKey(log.getThreadType())) {
                drones.get(threadType).add(log);
            } else {
                List<LogEntry> newLogList = new ArrayList<>();
                newLogList.add(log);
                drones.put(threadType, newLogList);
            }
        }
    }

    public static void droneMetrics(){
        for(String key: drones.keySet()){
            responseTime(drones.get(key));
            double time = droneRunTime(drones.get(key));
            if(time != 0) {
                throughput(drones.get(key),time);
            }else{
                System.out.println("Throughput: Not available");
            }
            droneUtilization(drones.get(key),calculateTotalTime(drones.get(key)));
            System.out.println();
        }

    }
    public static void main(String[] args){
        analyzeLogs();
    }
}
