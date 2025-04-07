import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.regex.*;

public class SchedulerLogAnalyzer {
    private final String LOG_FILE = "scheduler_event_log.txt";
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    /** Stores response times for each thread from the parsed entry logs*/
    private Map<String, List<Double>> responseTimesByThread = new HashMap<>();
    /** Stores total thread lifetimes for each thread from the parsed entry logs*/
    private Map<String, Double> threadLifetimes = new HashMap<>();

    private int countCompletedFireRequests = 0;
    private int countFireRequestCreated = 0;
    private int countFireRequests = 0;
    private int countSuccessfulDroneAssignments = 0;
    private int countReassignmentsOptimum = 0;
    private int countReassignmentsFaults = 0;
    private int countFaults = 0;
    private int countFailedAssignments = 0;

    /**
     Helper class to store parsed events
     <p>format: [timestamp] [level] [component] [threadTag] message </p>
     <p>format example: <p> [19:10:57.507] [DEBUG] [Scheduler] [SD] Received drone message: 0:[ACTIVE][TRAVELING]:STATUS:28:9:FireRequest{time=10-30-15, zone=4, event=FIRE_DETECTED, severity=High, id=1A}
     </p>
     */
    public class ParsedLogEntry
    {
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

    public void analyzeLogs() {
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

        List<Double> rtbtSD = new ArrayList<>();
        List<Double> rtbtSP = new ArrayList<>();

        // hash maps to store all entries
        Map<Integer, ParsedLogEntry> lastSDMap = new HashMap<>();
        Map<String, ParsedLogEntry> lastSPMap = new HashMap<>();
        Map<Integer, ParsedLogEntry> firstSFMap = new HashMap<>();
        Map<Integer, ParsedLogEntry> lastSFMap = new HashMap<>();

        ParsedLogEntry firstSDLog = null;
        ParsedLogEntry lastSDLog = null;

        ParsedLogEntry firstSPLog = null;

        ParsedLogEntry lastSFLog = null;

        ParsedLogEntry firstSchedulerLog = null;
        ParsedLogEntry lastSchedulerLog = null;
        ParsedLogEntry firstFireIncident = null;

        boolean firstFireStored = false;
        boolean firstAssignmentMade = false;

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
                    if ( entry.level.equals("ERROR") )  countFaults++;
                    else if ( entry.message.contains("Task is being reassigned") ) countReassignmentsFaults++;
                    else if( entry.message.contains("Received drone message:") )
                    {
                        if ( entry.message.contains("Received drone message: 0:[IDLE]:INIT:")) firstSDLog = entry;

                        int id = getDroneId( entry.message );
                        if (id!=-1) lastSDMap.put(id, entry);
                    }
                    else if ( entry.message.contains("Responding to drone with:") )
                    {
                        int id = getDroneId( entry.message );
                        if (id!=-1)
                        {   // add the calculated response time for this thread by drone id based off prev added entry for the drone/scheduler communication
                            ParsedLogEntry lastEntry = lastSDMap.get(id);
                            rtbtSD.add(calculateTimeDuration( lastEntry, entry ));
                        }
                    }
                    lastSDLog = entry;
                    break;
                case "SP":
                    // SP thread for all Scheduler internal processing of fire requests assignments to drones
                    if ( entry.message.contains("Attempting assignment:") )
                    {
                        // when a fire request is attempting assignment to any available drone
                        String message = entry.message;
                        String[] items = message.split("id=");
                        lastSPMap.put(items[1].substring(0,2), entry); // add the last
                        if(!firstAssignmentMade)
                        {
                            firstSPLog = entry;
                            firstAssignmentMade=true;
                        }
                    }
                    else if ( entry.message.contains("this fire request:") )
                    {
                        // when a fire request is successfully assigned to an available drone
                        countSuccessfulDroneAssignments++;
                        String message = entry.message;
                        String[] items = message.split("id=");
                        ParsedLogEntry prevSPLog = lastSPMap.remove(items[1].substring(0,2)); // add the last
                        double rtSP = calculateTimeDuration(prevSPLog,entry);
                        rtbtSP.add(rtSP);
                    }
                    else if ( entry.message.contains("Reassigning previous task:") ) countReassignmentsOptimum++;
                    else if ( entry.message.contains("No available drone for request:") ) countFailedAssignments++;
                    break;
                case "SF":
                    // SF thread for all Scheduler<->FireIncidentSubsystem communication
                    if ( entry.message.contains("Received fire message:") )
                    {
                        // counts and stores the fire request id to check when it is added
                        countFireRequests++;
                        if(!firstFireStored)
                        {
                            firstFireIncident = entry;
                            firstFireStored=true;
                        }
                        String message = entry.message;
                        String[] items = message.split("id=");
                        String numValue = items[1].trim();
                        Integer fRID = -1;
                        try{fRID = Integer.valueOf(numValue.charAt(0)); } catch (NumberFormatException e) {}
                        if(fRID!=1) firstSFMap.put(fRID, entry); // add the last
                    }
                    else if ( entry.message.contains("Added FireRequest:") )
                    {
                        countFireRequestCreated++;
                    }
                    else if (entry.message.contains("Sending to FireIncident Subsystem Scheduler Acknowledged Fire Request:"))
                    {
                        lastSFLog = entry;
                        String message = entry.message;
                        String[] items = message.split("id=");
                        Integer fRID = -1;
                        try{fRID = Integer.valueOf(items[1].charAt(0)); } catch (NumberFormatException e) {}
                        if(fRID!=1) lastSFMap.put(fRID, entry); // add the last
                    }
                    else if ( entry.message.equals("Received Shutdown Message From Fire: SHUTDOWN") ) lastSFLog = entry;
                    break;
                case "MAIN":
                    // scheduler initialized and all threads completed event logs
                    if ( entry.message.contains("Scheduler is now online")) firstSchedulerLog = entry;
                    else if ( entry.message.contains("All threads have finished. Log file ready for analysis.")) lastSchedulerLog = entry;
                    break;
                default:
                    break;
            }
        }
        responseTimesByThread.put("SD",rtbtSD);
        responseTimesByThread.put("SP",rtbtSP);
        responseTimesByThread.put("SF",new ArrayList<>());

        calcSDMetrics(firstSDLog, lastSDLog);
        calcSPMetrics(firstSPLog, lastSFLog, lastSPMap);
        calcSFMetrics(firstFireIncident, lastSFLog, firstSFMap, lastSFMap);
        calcGeneralMetrics(firstSchedulerLog, lastSchedulerLog, firstFireIncident);
    }

    /**
     * METRICS SD: data for to respond to drone, after receiving request
     *
     * @return
     */
    private double[] calcSDMetrics(ParsedLogEntry firstSDLog, ParsedLogEntry lastSDLog)
    {
        List<Double> rtbtSD = responseTimesByThread.remove("SD");
        double lifeTimeSD = calculateTimeDuration( firstSDLog, lastSDLog );
        double busyTimeSD = 0.000;
        for ( Double rtSD : rtbtSD ) busyTimeSD+=rtSD;
        double avgResponseSD = busyTimeSD/( (double)rtbtSD.size() );
        double utilizationSD = (busyTimeSD / lifeTimeSD);

        System.out.println("\n==================================================================");
        System.out.println(" * Scheduler Thread - SD - For Drone <-> Scheduler Communication * ");
        System.out.println("                 First Drone Request: " + firstSDLog.timestamp);
        System.out.println("                            End Time: " + lastSDLog.timestamp);
        System.out.printf("                            Lifetime: %.4f s\n", lifeTimeSD);
        System.out.printf("                            BusyTime: %.4f s\n\n", busyTimeSD);

        System.out.printf("                         Utilization: %.4f\n", utilizationSD);
        System.out.printf("                        Utilization%%: %.2f %%\n", utilizationSD*100.0000);
        System.out.printf("Total Communication events w. Drones: %s\n", rtbtSD.size());
        System.out.printf("               Average Response Time: %.4f s\n", avgResponseSD);

        double[] output = {lifeTimeSD, busyTimeSD, utilizationSD, avgResponseSD};
        return output;
    }

    /**
     METRICS SF: Data for making/adding, then pass fire requests to Scheduler processing queue when first received fire incident
     */
    private double[] calcSFMetrics(ParsedLogEntry firstFireIncident,
                                           ParsedLogEntry lastSFLog,
                                           Map<Integer, ParsedLogEntry> firstSFMap,
                                           Map<Integer, ParsedLogEntry> lastSFMap)
    {
        List<Double> rTSF = responseTimesByThread.remove("SF");

        double busyTimeSF = 0.000;
        // iterate through keys in firstSFMap to get ids of fire requests
        for (Integer fireId : firstSFMap.keySet())
        {
            ParsedLogEntry firstEntry = firstSFMap.get(fireId);
            ParsedLogEntry lastEntry = lastSFMap.get(fireId);
            double responseTimeSF = calculateTimeDuration(firstEntry,lastEntry);
            rTSF.add(responseTimeSF);
            busyTimeSF+=responseTimeSF;
        }
        double lifeTimeSF = calculateTimeDuration( firstFireIncident, lastSFLog );
        double avgResponseSF = busyTimeSF/( (double)rTSF.size() );
        double utilizationSF = (busyTimeSF / lifeTimeSF);

        System.out.println("\n==================================================================");
        System.out.println(" * Scheduler Thread - SF - For FireIncidentSubsystem (FISS) <-> Scheduler Communication * ");
        System.out.println("        First Fire Incident Received: " + firstFireIncident.timestamp);
        System.out.println("                            End Time: " + lastSFLog.timestamp);
        System.out.printf("                            Lifetime: %.4f s\n", lifeTimeSF);
        System.out.printf("                            BusyTime: %.4f s\n\n", busyTimeSF);

        System.out.printf("                         Utilization: %.4f\n", utilizationSF);
        System.out.printf("                        Utilization%%: %.2f %%\n", utilizationSF*100.0000);
        System.out.printf("  Total Communication events w. FISS: %s\n", countFireRequests);
        System.out.printf("               Average Response Time: %.4f s\n", avgResponseSF);

        double[] output = {lifeTimeSF, busyTimeSF, utilizationSF, avgResponseSF};
        return output;
    }

    /**
     METRICS SP: Data for assigning fire requests to drones
     */
    private double[] calcSPMetrics(ParsedLogEntry firstSPLog,
                                      ParsedLogEntry lastSPLog,
                                      Map<String, ParsedLogEntry> lastSPMap)
    {
        List<Double> rtbtSP = responseTimesByThread.remove("SP");

        double busyTimeSP = 0.000;
        for ( Double rt : rtbtSP ) busyTimeSP+=rt;
        double lifeTimeSP = calculateTimeDuration( firstSPLog, lastSPLog );
        double avgResponseSP = busyTimeSP/( (double)rtbtSP.size() );
        double utilizationSP = (busyTimeSP / lifeTimeSP);

        System.out.println("\n==================================================================");
        System.out.println(" * Scheduler Thread - SP - For assigning fire requests to drones * ");
        System.out.println("         First Fire Request Received: " + firstSPLog.timestamp);
        System.out.println("                            End Time: " + lastSPLog.timestamp);
        System.out.printf("                            Lifetime: %.4f s\n", lifeTimeSP);
        System.out.printf("                            BusyTime: %.4f s\n\n", busyTimeSP);

        System.out.printf("           # of Successful Drone Assignments : %s\n", countSuccessfulDroneAssignments);
        System.out.printf("# of Drone Reassignments from path Optimizing: %s\n", countReassignmentsOptimum);
        System.out.printf("         # of Drone Reassignments from faults: %s\n", countReassignmentsFaults);
        System.out.printf("         # of Unsuccessful Drone Assignments : %s\n\n", countFailedAssignments);

        System.out.printf("                         Utilization: %.4f\n", utilizationSP);
        System.out.printf("                        Utilization%%: %.2f %%\n", utilizationSP*100.000);
        System.out.printf("               Average Response Time: %.4f s\n", avgResponseSP);

        double[] output = {lifeTimeSP, busyTimeSP, utilizationSP, avgResponseSP};
        return output;
    }

    /**
     METRICS General: data for Scheduler system as a whole (System of all three threads working together)
     */
    private double calcGeneralMetrics(ParsedLogEntry firstSchedulerLog,
                                           ParsedLogEntry lastSchedulerLog,
                                           ParsedLogEntry firstFireIncident)
    {
        double lifeTimeScheduler = calculateTimeDuration(firstSchedulerLog, lastSchedulerLog);
        double lifeTimeServicingFires = calculateTimeDuration(firstFireIncident, lastSchedulerLog)*EventScheduler.simulationSpeed;
        double throughPut = (double)countFireRequests/lifeTimeServicingFires;
        System.out.println("\n==================================================================");
        System.out.println(" * Scheduler General Metrics * ");
        System.out.println("                          Start Time: " + firstSchedulerLog.timestamp);
        System.out.println("                            End Time: " + lastSchedulerLog.timestamp);
        System.out.printf("                     System Lifetime: %.4f s\n\n", lifeTimeScheduler);
        System.out.printf(" Lifetime Servicing Fires (adjusted): %.4f s\n\n", lifeTimeServicingFires);

        System.out.printf("       Total Fire Incidents Sent to Scheduler: %s\n", countFireRequests);
        System.out.printf(" #of Drone Missions Required to Service Fires: %s\n", countFireRequestCreated);
        System.out.printf("                 #of Drone Missions Completed: %s\n", countCompletedFireRequests);
        System.out.printf("                             #of Drone Faults: %s\n", countFaults);
        System.out.printf("        Fires Serviced With Drones Throughput: %.4f /s\n", throughPut);
        return throughPut;
    }


    private List<ParsedLogEntry> parseLogFile() {
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

    private int getDroneId(String message)
    {
        String[] items = message.split("\\[");

        String[] subitems = items[0].split(":");
//        System.out.print( " items->" + Arrays.toString(items) + " subitems->" + Arrays.toString(subitems));
        for (String si : subitems)
        {
            try{
                return Integer.valueOf(si.trim()); // trim space chars for handling of just number
            } catch (NumberFormatException e) {}
        }
        return -1;
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
        SchedulerLogAnalyzer sla = new SchedulerLogAnalyzer();
        sla.analyzeLogs();
    }
}