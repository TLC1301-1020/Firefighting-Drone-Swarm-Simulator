import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * A utility class for analyzing drone log data.
 * This class contains methods for parsing log entries and organizing them by component type.
 * It stores logs in separate categories such as drone, process fault, scheduler listener, and subsystem logs.
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
    private static LocalTime start = null;
    private static LocalTime end = null;

    private static int droneTotal = 0;
    private static double programLife = 0;
    /**
     * Represents a log entry with a timestamp, component, and event code.
     * This class provides a method to parse log lines into LogEntry objects.
     */
    private static class LogEntry {
        private LocalTime timestamp;
        private String component;
        private String event;
        private String threadType;

        /**
         * Parses a log line and creates a LogEntry object.
         *
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

        /**
         * Constructs a new LogEntry object.
         *
         * @param timestamp The timestamp of the log entry
         * @param component The component from which the log entry originated
         * @param threadType The type of the thread generating the log
         * @param event The event associated with this log entry
         */
        LogEntry(LocalTime timestamp, String component,String threadType, String event) {
            this.timestamp = timestamp;
            this.component = component;
            this.threadType = threadType;
            this.event = event;
        }
        //Getters
        public String getComponent() {return component;}
        public LocalTime getTimestamp() {return timestamp;}
        public String getThreadType() {return threadType;}
        public String getEvent() {return event;}
    }
    /**
     * Reads the log file and parses each line into a {@link LogEntry} object.
     * Depending on the component and thread type of the log entry, the logs are separated into different categories:
     * <ul>
     *     <li>Scheduler listener logs</li>
     *     <li>Process fault logs</li>
     *     <li>Subsystem logs</li>
     *     <li>Drone logs</li>
     * </ul>
     * The method also updates the total number of drones based on the thread type in the logs.
     *
     * @param file The log file to read and process.
     */
    public static void readLogs(String file) {

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                LogEntry logEntry = LogEntry.parse(line);
                if (logEntry != null) {
                    if(start == null){
                        start = logEntry.getTimestamp();
                        System.out.println("Start time of the program: " + start);
                    }
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
                    }else if(logEntry.getEvent().contains("Program ended, shutting down.")){
                        end = logEntry.getTimestamp();
                    }
                    programLife = Duration.between(start,logEntry.getTimestamp()).toMillis() /1000.0;
                }
                //drone starts from 0
                droneTotal += 1;
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("End time of the program: " + end);
        if(end != null) {
            programLife = Duration.between(start, end).toMillis() / 1000.0;
        }
    }
    /**
     * Analyzes and processes logs by reading the log file and categorizing log entries.
     * The method performs several metrics calculations and outputs:
     * <ul>
     *     <li>Drones logs: Sorted and metrics are calculated</li>
     *     <li>Subsystem logs: Metrics such as lifetime, response time, throughput, latency, and utilization</li>
     *     <li>Scheduler listener logs: Metrics including lifetime, response time, throughput, and utilization</li>
     *     <li>Process fault logs: Metrics including lifetime, response time, throughput, and utilization</li>
     * </ul>
     * It outputs the results for each category and handles situations when no logs are found.
     */
    public static void analyzeLogs() {
        readLogs(LOG_FILE);  // Read and parse the log entries
        if (droneLogs.isEmpty() && subsystemLogs.isEmpty()) {
            System.out.println("No previous log file found.");
        }else{
            System.out.println("=================== Drones ===================");

            sortDrones();
            droneMetrics();

            System.out.println("=================== Subsystem - run ===================\n");

            responseTime(subsystemLogs,"Waiting","Received");
            throughput(subsystemLogs,programLife,"Waiting","Received");
            utilization(subsystemLogs,programLife, "Waiting", "Received");

            System.out.println("\n============ Subsystem - ListeningToScheduler ============\n");

            responseTime(schedulerListenerLogs,"received","handled");
            throughput((schedulerListenerLogs),programLife,"received","handled");
            utilization(schedulerListenerLogs,programLife,"received","handled");

            System.out.println("\n============ Subsystem - ProcessFault ============\n");

            responseTime(processFaultLogs,"Waiting","Received");
            throughput(processFaultLogs,programLife,"Waiting", "Received");
            utilization(processFaultLogs,programLife,"Waiting", "Received");
        }
    }
    /**
     * Calculates the total and average running time of drones based on log entries.
     * The method considers logs with "Assigned" and "Arrived back at base" events to calculate the total running time
     * for each task and the average running time per task.
     *
     * @param drone A list of LogEntry objects representing drone logs.
     * @return The total running time of all tasks in seconds. If no valid logs are found, returns 0.0.
     */
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

        double average = (totalTime/countTask);
        if(average > 0) {
            System.out.printf("Average running time: %.4fs\n", average);
        }else {
            System.out.println("Average running time: Not available");
        }
        return totalTime;
    }
    /**
     * Calculates the utilization of drones based on their work time and the total program lifetime.
     * Utilization is calculated as the ratio of work time to lifetime.
     *
     * @param drone A list of LogEntry objects representing drone logs.
     * @param lifetime The total lifetime of the program in seconds.
     * @return The utilization of the drone as a decimal fraction. If no valid logs or lifetime are provided, returns 0.0.
     */
    public static double droneUtilization(List<LogEntry> drone, double lifetime){
        if (lifetime <= 0 || drone.isEmpty()) {
            return 0.0;
        }
        double workTime = 0;
        LocalTime workStart = null;

        for(LogEntry log: drone){
            if(log.getEvent().contains("Assigned") && workStart == null){
                workStart = log.getTimestamp();
            }else if((log.getEvent().contains("Arrived back at base")  || log.getEvent().contains("complete") || log.getEvent().contains("FAULT")) && workStart != null){
                workTime += Duration.between(workStart, log.getTimestamp()).toMillis() / 1000.0;
                workStart = null;
            }
        }
        System.out.println("Busy time: " + workTime + "s");
        double utilization = workTime / lifetime;

        if (utilization <= 0) {
            System.out.println("Utilization: Not available");
        } else {
            System.out.printf("Utilization: %.4f\n", utilization);
            System.out.printf("Utilization%%: %.2f%%\n", utilization * 100.00);
        }
        return utilization;
    }

    /**
     * Calculates the average deployment time based on the log entries.
     * A deployment is considered as starting with a "Deploying" event and completing with a "payload complete" event.
     * The method computes the total deployment time and returns the average time for completed deployments.
     *
     * @param logs A list of LogEntry objects representing the logs to analyze.
     * @return The average deployment time in seconds. If no valid deployments are found, returns 0.
     */
    public static double averageDeploy(List<LogEntry> logs){
        if(logs.isEmpty()){
            System.out.println("Average Deploy Time: Not available");
            return 0;
        }

        double totalDeployTime = 0;
        LocalTime deployStart = null;
        LocalTime deployEnd = null;
        int count = 0;
        for(LogEntry log: logs){
            //new deploying event
            if (log.getEvent().contains("Deploying"))  {
                deployStart = log.getTimestamp();
            }else if(log.getEvent().contains("payload complete")){
                if(deployStart != null){
                    deployEnd = log.getTimestamp();
                    totalDeployTime += Duration.between(deployStart,deployEnd).toMillis() / 1000.0;
                    deployStart = null;
                    deployEnd = null;
                    count++;
                }
            }
        }
        if(count <= 0 || totalDeployTime <= 0){
            System.out.println("Total Task Completed: " + 0);
            System.out.println("Average Deploy Time: not available");
            return 0;
        }
        totalDeployTime = (totalDeployTime/count);
        System.out.println("Total Task Completed: " + count);
        System.out.printf("Average Deploy Time: %.4fs\n", totalDeployTime);
        return totalDeployTime;

    }


    /**
     * Calculates the throughput based on logs, time, and specified event types.
     * Throughput is computed as the number of completed requests per unit of time,
     * based on the occurrences of the specified events (x and y) in the logs.
     *
     * @param logs The list of logs containing event data.
     * @param time The total time in seconds over which throughput is calculated.
     * @param x The event type indicating the start of a request.
     * @param y The event type indicating the completion of a request.
     * @return The calculated throughput (requests per second), or 0 if no valid data is found.
     */
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
            System.out.println("Throughput: not available");
            return 0;
        }else{
            System.out.printf("Throughput: %.2f/s\n", throughput);
        }
        return throughput;

    }
    /**
     * Calculates the utilization based on logs, lifetime, and specified event types.
     * Utilization is the ratio of total working time to the given lifetime, determined
     * by the occurrences of the specified events (x and y) in the logs.
     *
     * @param logs The list of logs containing event data.
     * @param lifetime Program lifetime in seconds over which utilization is calculated.
     * @param x The event type indicating the start of a working period.
     * @param y The event type indicating the end of a working period.
     * @return The calculated utilization (working time / lifetime), or 0 if no valid data is found.
     */
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
            System.out.println("Utilization: not available");
            return 0.0;
        }

        double utilPercent = utilization * 100.00;
        System.out.printf("Utilization: %.4f\n", utilization);
        System.out.printf("Utilization%%: %.2f%%\n", utilPercent);

        return utilization;
    }
    /**
     * Calculates the average response time based on logs, using the specified event types.
     * Response time is calculated as the duration between the start event (x) and the end event (y) for each request.
     *
     * @param logs The list of logs containing event data.
     * @param x The event type indicating the start of a request.
     * @param y The event type indicating the end of a request.
     * @return The calculated average response time in seconds, or 0 if no valid data is found.
     */
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
        if(responseTimes <= 0 || requestCount == 0){
            System.out.println("Response Time: not available");
            return 0;
        }
        responseTimes = responseTimes/requestCount;
        System.out.printf("Response Time: %.4fs\n", responseTimes/requestCount);
        return responseTimes;
    }

    /**
     * Calculates the total lifetime based on the first and last log entry timestamps.
     * The lifetime is the duration between the first and last log entries in seconds.
     *
     * @param logs The list of log entries.
     * @return The calculated total lifetime in seconds, or 0 if no valid logs are found or an error occurs.
     */
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
    /**
     * Sorts the drone log entries into separate lists based on the thread type.
     * The logs are organized by the thread type,
     * each thread type has its own list of log entries in the `drones` HashMap.
     */
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
    /**
     * Analyzes and prints various metrics for each drone, including:
     * - Lifetime of each drone
     * - Drone utilization
     * - Average deployment time
     * - Response time
     * - Run time
     * - Throughput
     *
     * It calculates the overall average deployment time for all drones and displays the results.
     */
    public static void droneMetrics(){
        double total = 0;
        int count = 0;
        double average = 0;

        for(String key: drones.keySet()){
            System.out.println(" Drone - " + key);
            calculateTotalTime(drones.get(key));
            droneUtilization(drones.get(key),programLife);

            average = averageDeploy(drones.get(key));
            if(average > 0){
                total += average;
                count++;
            }

            responseTime(drones.get(key),"Waiting", "Received");
            double time = droneRunTime(drones.get(key));
            if(time != 0) {
                throughput(drones.get(key),time,"Waiting", "Received");
            }else{
                System.out.println("Throughput: Not available");
            }
            System.out.println("--------------------------------");
        }
        total = total/count;
        System.out.printf("All Drones - Average Deployment Time: %.4fs\n", total);
        System.out.println();

    }

    //Getters
    /**
     * @return the list of drone logs
     */
    public static List<LogEntry> getDroneLogs(){
        return droneLogs;
    }
    /**
     * @return the list of process fault logs
     */
    public static List<LogEntry> getProcessFaultLogs(){
        return processFaultLogs;
    }
    /**
     * @return the list of scheduler listener logs
     */
    public static List<LogEntry> getSchedulerListenerLogs(){
        return schedulerListenerLogs;
    }
    /**
     * @return the map of drones, each associated with a list of log entries
     */
    public static HashMap<String, List<LogEntry>> getDrones(){
        return drones;
    }
    /**
     * @return the list of subsystem logs
     */
    public static List<LogEntry> getSubsystemLogs(){
        return subsystemLogs;
    }

    public static void main(String[] args){
        analyzeLogs();
    }
}
