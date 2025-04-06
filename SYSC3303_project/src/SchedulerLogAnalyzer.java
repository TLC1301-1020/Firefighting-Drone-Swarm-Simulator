import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.regex.*;

public class SchedulerLogAnalyzer {
    private static final String LOG_FILE = "scheduler_event_log.txt";
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

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

        // hash maps to store all entries
        Map<Integer, ParsedLogEntry> lastSDMap = new HashMap<>();
        Map<String, ParsedLogEntry> lastSPMap = new HashMap<>();
        Map<String, ParsedLogEntry> lastSFMap = new HashMap<>();

        //TODO: computeResponseTimes(entries);
        computeResponseTimes(entries);
        //TODO: computeUtilization(entries);
        computeUtilization(entries);
        //TODO: computeThroughput(entries);
        computeThroughput(entries);
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
                            LocalTime.parse(timestamp, formatter),
                            level, component, threadTag, message
                    ));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return parsedEntries;
    }


    /**
     Helper class to store parsed events
     <p>format: [timestamp] [level] [component] [threadTag] message </p>
     <p>format example: <p> [19:10:57.507] [DEBUG] [Scheduler] [SD] Received drone message: 0:[ACTIVE][TRAVELING]:STATUS:28:9:FireRequest{time=10-30-15, zone=4, event=FIRE_DETECTED, severity=High, id=1A}
     </p>
     */
    private static class ParsedLogEntry {
        private final LocalTime timestamp;
        private final String level;
        private final String component;
        private final String threadTag;
        private final String message;

        public ParsedLogEntry(LocalTime timestamp, String level, String component, String threadTag, String message) {
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
        public LocalTime getTimestamp() { return timestamp; }
        public String getLevel() { return level; }
        public String getComponent() { return component; }
        public String getThreadTag() { return threadTag; }
        public String getMessage() { return message; }
    }

    /** Stores response times for each thread for agent and individual Chefs. Populated in {@code calcResponseTimes} from the parsed entry logs*/
    private static Map<String, List<Double>> responseTimesByThread = new HashMap<>();
    /** Stores total thread lifetimes for each thread. Populated in {@code calcResponseTimes} from the parsed entry logs */
    private static Map<String, Double> threadLifetimes = new HashMap<>();

    private static void computeResponseTimes( List<ParsedLogEntry> entries )
    {
        // hash maps to store all entries
        Map<Integer, ParsedLogEntry> lastSDMap = new HashMap<>();
        Map<String, ParsedLogEntry> lastSPMap = new HashMap<>();
        Map<String, ParsedLogEntry> lastSFMap = new HashMap<>();
        ParsedLogEntry firstSDLog = null;
        ParsedLogEntry lastSDLog = null;
        int countCompletedFireRequests = 0;
        int countFireRequestCreated = 0;
        int countFireRequests = 0;

        SchedulerLogAnalyzer.responseTimesByThread.put("SD",new ArrayList<>());
        SchedulerLogAnalyzer.responseTimesByThread.put("SP",new ArrayList<>());
        SchedulerLogAnalyzer.responseTimesByThread.put("SF",new ArrayList<>());

        for( ParsedLogEntry entry : entries )
        {
            switch(entry.threadTag)
            {
                case "SD":
                    // SD thread for all Scheduler<->Drone communication (via DSS)
                    /*
                    Response times for SD thread  - calc time difference based on drone message, take average of all of these to get an average response time:
                    <time> [DEBUG] [Scheduler] [SD]  Received drone message: 1:<request>
                    <time> [DEBUG] [Scheduler] [SD] Responding to drone with: <header>:1:<request>

                    double utilizationSP = (busyTimeSP / lifetimeSP)
                    Where busyTime is the sum of all response times for SD thread
                    Where lifetime is the last - first eventlog SD outputs
                     */
                    // count number of completed fire requests
                    if( entry.message.contains("Notifying FireIncidentSubsystem of task completion:") ) countCompletedFireRequests++;

                    // store logs based on contents for metrics
                    if ( entry.message.equals("Listening to DroneSubsystem...") ) firstSDLog = entry;
                    else if( entry.message.contains("Received drone message:") )
                    {
                        int id = getDroneId( entry.message );
                        System.out.println( id + " found in SD RECEIVED message: " + entry.message + "\n");
                        if (id!=-1) lastSDMap.put(id, entry);
                    }
                    else if ( entry.message.contains("Responding to drone with:") )
                    {
                        int id = getDroneId( entry.message );
                        System.out.println( id + " found in SD RESPONSE message: " + entry.message + "\n");
                        if (id!=-1)
                        {   // add the calculated response time for this thread by drone id based off prev added entry for the drone/scheduler communication
                            ParsedLogEntry lastEntry = lastSDMap.get(id);
                            List<Double> rtbt = responseTimesByThread.remove("SD");
                            rtbt.add(calculateTimeDuration( lastEntry, entry ));
                            responseTimesByThread.put("SD", rtbt);
                        }
                    }
                    lastSDLog = entry;
                    break;
                case "SP":
                    // SP thread for all Scheduler internal processing of fire requests assignments to drones
                    if ( entry.message.contains("Added FireRequest:") ) countFireRequestCreated++;

                    break;
                case "SF":
                    if ( entry.message.contains("Received fire message:") ) countFireRequests++;
                    if ( entry.message.contains("Added FireRequest:") ) countFireRequestCreated++;
                    // SF thread for all Scheduler<->FireIncidentSubsystem communication

                    break;
                case "MAIN":
                    // scheduler initialized and all threads completed
                    break;
                default:
                    break;
            }
        }
        List<Double> rtbtSD = responseTimesByThread.remove("SD");
        double lifeTimeSD = calculateTimeDuration( firstSDLog, lastSDLog );
        double busyTimeSD = 0.000;
        for ( Double rtSD : rtbtSD ) busyTimeSD+=rtSD;
        double avgResponseSD = busyTimeSD/( (double)rtbtSD.size() );
        double utilizationSD = (busyTimeSD / lifeTimeSD);

        System.out.println("\n-----------------------------------------------------");
        System.out.println(" * Scheduler Thread - SD * ");
        System.out.println("                         Start Time: " + firstSDLog.timestamp);
        System.out.println("                           End Time: " + lastSDLog.timestamp);
        System.out.printf("                            Lifetime: %.4f\n", lifeTimeSD);
        System.out.printf("                            BusyTime: %.4f\n\n", busyTimeSD);

        System.out.printf("                         Utilization: %.4f\n", utilizationSD);
        System.out.printf("Total Communication events w. Drones: %s\n", rtbtSD.size());
        System.out.printf("               Average Response Time: %.4f\n\n", avgResponseSD);

        System.out.printf("             Total Fire Requests Sent to Scheduler: %s\n", countFireRequests);
        System.out.printf("          Total Fire Requests Created by Scheduler: %s\n", countFireRequestCreated);
        System.out.printf("Total Completed Fire Requests Handled by Scheduler: %s\n", countCompletedFireRequests);
        System.out.printf("                                     Throughput SD: %.4f\n", (double)countCompletedFireRequests/lifeTimeSD);
        System.out.println("-----------------------------------------------------");


        /*
        Drone - 2

        Start time: 22:05:25.706

        End time: 22:08:27.156
        Lifetime: 181.4500s
         */
    }

    private static int getDroneId(String message)
    {
        String[] items = message.split("\\[");

        String[] subitems = items[0].split(":");
        System.out.print( " items->" + Arrays.toString(items) + " subitems->" + Arrays.toString(subitems));
        for (String si : subitems)
        {
            try{
                return Integer.valueOf(si.trim()); // trim space chars for handling of just number
            } catch (NumberFormatException e) {}
        }
        return -1;
    }

    private static void computeUtilization(List<ParsedLogEntry> entries)
    {

    }

    private static void computeThroughput(List<ParsedLogEntry> entries)
    {

    }

    /**
     * Calculates the time duration between two log entries in seconds
     * @param firstLog The earlier event
     * @param lastLog  The later event
     * @return Duration in seconds (and ms) as a double
     */
    public static double calculateTimeDuration(ParsedLogEntry firstLog, ParsedLogEntry lastLog)
    {
        double startTime = convertToTimeValue(firstLog.getTimestamp());
        double endTime = convertToTimeValue(lastLog.getTimestamp());
        return endTime-startTime;
    }

    /**
     * Converts a timestamp string to a double-precision value in seconds.
     *
     * @param time Timestamp in HH:mm:ss.SSS format
     * @return Time as seconds in the format ss.SSS
     */
    public static double convertToTimeValue(LocalTime time)
    {
        double hh = (double)time.getHour();
        double mm = (double)time.getMinute();
        double ss = (double)time.getSecond();
        double SSS = ((double)time.getNano())/1000000000.0;

        return hh*3600.000 + mm*60.000 + ss + SSS;
    }

    public static void main(String[] args)
    {
        SchedulerLogAnalyzer.analyzeLogs();
    }
}
