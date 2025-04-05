import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
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

    private static int droneTotal = 0;
    private static double lifetime;
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
    public static void readLogs(String file) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
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
        readLogs(LOG_FILE);  // Read and parse the log entries
        if (droneLogs.isEmpty() && subsystemLogs.isEmpty()) {
            System.out.println("No previous log file found.");
        }else{
            sortDrones();
            System.out.println("=================== Drones ===================");
            droneMetrics();

            System.out.println("=================== Subsystem - run ===================\n");
            lifetime = calculateTotalTime(subsystemLogs);
            responseTime(subsystemLogs,"Waiting","Received");
            throughput(subsystemLogs,lifetime,"Waiting","Received");
            subsystemLatency();
            utilization(subsystemLogs,lifetime, "Waiting", "Received");

            System.out.println("\n============ Subsystem - ListeningToScheduler ============\n");
            lifetime = calculateTotalTime(schedulerListenerLogs);

            responseTime(schedulerListenerLogs,"received","handled");
            throughput((schedulerListenerLogs),lifetime,"received","handled");
            utilization(schedulerListenerLogs,lifetime,"received","handled");

            System.out.println("\n============ Subsystem - ProcessFault ============\n");
            lifetime = calculateTotalTime(processFaultLogs);
            responseTime(processFaultLogs,"Waiting","Received");
            throughput(processFaultLogs,lifetime,"Waiting", "Received");
            utilization(processFaultLogs,lifetime,"Waiting", "Received");
        }
    }

    public static double droneRunTime(List<LogEntry> drone){
        if(drone.isEmpty()){
            return 0.0;
        }
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

    public static double droneUtilization(List<LogEntry> drone, double lifetime){
        if (lifetime <= 0 || drone.isEmpty()) {
            return 0.0;
        }
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

        if (utilization <= 0) {
            System.out.println("Utilization: Not available");
        } else {
            System.out.printf("Utilization: %.4f\n", utilization);
        }
        return utilization;
    }
    //Subsystem metrics
    public static double subsystemLatency(){
        if(subsystemLogs.isEmpty()){
            return 0;
        }
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
        if(totalLatency < 0){
            System.out.println("Error data.");
            return 0;
        }else{
            System.out.printf("Average Latency: %.4fs\n", totalLatency);
        }
        return totalLatency;
    }

    //shared methods for calculating the metrics
    //throughput: Units completed / total time
    public static double throughput(List<LogEntry> logs, double time, String x, String y){
        if(x.isEmpty() || y.isEmpty() || logs.isEmpty() || time <= 0){
            return 0;
        }
        int requestCount = 0;
        double throughput;
        LogEntry previousWaiting = null;
        for(LogEntry log: logs){
            if(log.event.contains(x)){
                previousWaiting = log;
            }
            if(log.event.contains(y)){
                if (previousWaiting != null){
                    requestCount++;
                    previousWaiting = null;
                }
            }
        }
        throughput = requestCount/time;
        if(throughput <= 0){
            System.out.println("No Throughput result available.");
            return 0;
        }else{
            System.out.printf("Throughput: %.2f/s\n", throughput);
        }
        return throughput;

    }

    //utilization: busy time / lifetime
    public static double utilization(List<LogEntry> logs, double lifetime,String x, String y) {
        if(logs.isEmpty() || lifetime <= 0 || x.isEmpty() || y.isEmpty()){
            return 0;
        }
        double totalWorking = 0;
        LocalTime lastReceived = null;
        boolean received = false;

        for (LogEntry log : logs) {
            String event = log.getEvent();
            if (event.contains(x)) {
                lastReceived = log.getTimestamp();
                received = true;

            } else if (event.contains(y)) {
                if (received) {
                    Duration workingDuration = Duration.between(lastReceived, log.getTimestamp());
                    totalWorking += workingDuration.toMillis() / 1000.0;
                    received = false;
                }

            }
        }
        double utilization = totalWorking / lifetime;
        if(utilization < 0){
            System.out.println("Error in data.");
            return 0.0;
        }
        System.out.printf("Utilization: %.4f\n", utilization);
        return utilization;
    }
    //average response time: total response time / # of request
    public static double responseTime(List<LogEntry> logs,String x, String y) {
        if(logs.isEmpty() || x.isEmpty() || y.isEmpty()){
            System.out.println("Response Time: not available");
            return 0;
        }
        double responseTimes = 0;
        int requestCount = 0;
        LocalTime requestStart = null;

        for (LogEntry log : logs) {
            String event = log.getEvent();
            if (event.contains(x)) {
                requestStart = log.getTimestamp();
            } else if (requestStart != null && event.contains(y)) {
                responseTimes += Duration.between(requestStart, log.getTimestamp()).toMillis() / 1000.0;
                requestCount++;
                requestStart = null;
            }
        }
        responseTimes = responseTimes/requestCount;
        System.out.printf("Response Time: %.4fs\n", responseTimes/requestCount);
        return responseTimes;
    }

    //overall lifetime
    public static double calculateTotalTime(List<LogEntry> logs) {
        if(logs.isEmpty()){
            System.out.println("Lifetime: not available");
            return 0;
        }
        double lifetime = 0;
        LocalTime startPoint;
        LocalTime endPoint;
        startPoint = logs.getFirst().getTimestamp();
        endPoint = logs.getLast().getTimestamp();

        if (startPoint != null && endPoint != null) {
            lifetime = Duration.between(startPoint, endPoint).toMillis() / 1000.0;
            if (lifetime < 0) {
                System.out.println("Error in timestamp.");
                return 0.0;
            }
            System.out.println("Start time: " + startPoint);
            System.out.println("End time: " + endPoint);
            System.out.printf("Lifetime: %.4fs\n \n", lifetime);
        }
        return lifetime;
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
            System.out.println(" Drone - " + key);
            double lifetime = calculateTotalTime(drones.get(key));
            responseTime(drones.get(key),"Waiting", "Received");
            double time = droneRunTime(drones.get(key));
            if(time != 0) {
                throughput(drones.get(key),time,"Waiting", "Received");
            }else{
                System.out.println("Throughput: Not available");
            }
            droneUtilization(drones.get(key),lifetime);
            System.out.println("--------------------------------");
        }
        System.out.println();
    }

    public static List<LogEntry> getDroneLogs(){
        return droneLogs;
    }
    public static List<LogEntry> getProcessFaultLogs(){
        return processFaultLogs;
    }
    public static List<LogEntry> getSchedulerListenerLogs(){
        return schedulerListenerLogs;
    }
    public static HashMap<String, List<LogEntry>> getDrones(){
        return drones;
    }
    public static List<LogEntry> getSubsystemLogs(){
        return subsystemLogs;
    }

    public static void main(String[] args){
        analyzeLogs();
    }
}
