import jdk.javadoc.doclet.Taglet;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.*;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.io.FileWriter;

/**
 * The FireIncidentSubsystem handles fire incident requests and communicates with the Scheduler.
 * It reads incident data from a file, sends requests to the scheduler, and receives updates.
 * Implements Runnable to execute in a separate thread.
 */
public class FireIncidentSubsystem implements Runnable {
    /**
     * the file for fire events
     */
    private String inputFile = "SYSC3303_project/src/fireincidents.txt";

    /**
     * The file for zone definitions.
     */
    private String zoneFile = "SYSC3303_project/src/zone_file.csv";

    /**
     * scheduler instance for managing fire requests - used in testing
     */
    private Scheduler scheduler;
    /**
     * list of fire requests created from the data received in the input file
     * <a href="file:../src/fireincedents.txt">/src/fireincedents.txt</a>
     */
    private List<FireRequest> tasks;
    /**
     * UDP socket for receiving packets from the Scheduler */
    private DatagramSocket receiveSocket;
    /**
     * UDP socket for sending packets to the Scheduler */
    private DatagramSocket sendSocket;
    /**
     * A static map that holds zone information parsed from the csv
     * Key: Zone ID, Value: Zone object
     */
    public static Map<Integer, Zone> zoneMap = new HashMap<>();
    /**
     * Formatter instance for printing and parsing date-time objects with the following example format: hour(24):minute:second.millisecond */
    private static final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH-mm-ss");

    /**
     * ArrayList that stores FireRequests from an EventScheduler that are ready to be processed. */
    private ArrayList<FireRequest> readyToSend = new ArrayList<>();
    /**
     * A synchronized list of expected fire request completion IDs to be used for knowing when simulation is complete and sending shutdown message to scheduler.
     * Each fire request (e.g., high severity) generates multiple sub-requests (e.g., "1A", "1B", "1C") */
    private final List<String> expectedCompletions = Collections.synchronizedList(new ArrayList<>());
    /**
     * A synchronized list of actual fire request completion IDs received from the Scheduler to be used for knowing when simulation is complete and sending shutdown message to scheduler. */
    private final List<String> receivedCompletions = Collections.synchronizedList(new ArrayList<>());

    /** Suffix appended to messages from the Scheduler indicating a task was completed */
    private static final String COMPLETED_SUFFIX = ":COMPLETED";
    /**
     * Logger used to log events and actions from the FireIncidentSubsystem to a log file */
    private static final LoggerDaemon logger = new LoggerDaemon();

    /**
     * Constructs a FireIncidentSubsystem with a given scheduler.
     */
    public FireIncidentSubsystem() {
        this.tasks = new ArrayList<>();

        try {
            sendSocket = new DatagramSocket();
            receiveSocket = new DatagramSocket(Scheduler.FIRE_INCIDENT_SUBSYSTEM_PORT);
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Thread to receive acknowledgements and indications when fire request mission is completed
     */
    private class ListenToScheduler extends Thread {
        @Override
        public void run() {
            System.out.println("\n[ FIRE<-S  ]  -   SCHEDULER IS LISTENING TO DRONE  -   ");
            while (true) {
                String update = receiveUpdate();
                System.out.println("\n[ FIRE<-S ]  UPDATE IS:       " + update);
                if (update.endsWith(COMPLETED_SUFFIX)) {
                    String fullId = update.replace(COMPLETED_SUFFIX, "").trim();
                    int idx = fullId.indexOf("id=");
                    if (idx != -1) {
                        String idPart = fullId.substring(idx + 3);
                        idPart = idPart.replaceAll("[^0-9A-Z]", ""); // extract e.g. "2A", "3B"

                        if (!receivedCompletions.contains(idPart)) {
                            receivedCompletions.add(idPart);
                            System.out.println("Adding to received Completions: " + idPart);
                        }

                        if (receivedCompletions.containsAll(expectedCompletions)) {
                            System.out.println("[ FIRE ] All fire requests completed. Sending SHUTDOWN.");
                            sendIncident("SHUTDOWN");
                        }
                    }
                }
            }
        }
    }

    /**
     * Thread to send fire data from the readyToSend array of fire data sent to Scheduler to handle with drone fire request missions.
     */
    private class SendToScheduler extends Thread {

        @Override
        public void run() {
            while (true) {
                try {
                    FireRequest request;
                    synchronized (readyToSend) {
                        if (readyToSend.isEmpty()) {
                            logger.log("STS Idle Start", "Waiting for tasks");
                            while (readyToSend.isEmpty()) {
                                readyToSend.wait();
                            }
                            logger.log("STS Idle End", "Task available");
                        }
                        //not empty list, taking the request
                        request = readyToSend.remove(0);
                        readyToSend.notifyAll();
                    }
                    //send the request
                    if(request!=null){
                        logger.log("Sending Incident", request.toString());
                        System.out.println("[ STS ] - sending the request: " + request);
                        sendIncident(request.toString());
                    }
                } catch (InterruptedException e) {
                    logger.log("STS Interrupted", "Thread interrupted");
                    System.out.println("SendToScheduler interrupted.");
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    /**
     * thread function for the fire incident subsystem
     * reads fire incidents, sends requests, and processes responses
     */
    public void run(){
        // Parse the zone file first.
        readZoneFile(zoneFile);
        // For debugging, print the parsed zones.
        for (Zone zone : zoneMap.values()) {
            System.out.println("[F] Parsed zone: " + zoneMap.get(zone.getZoneId()));
        }

        readInputFile(inputFile);

        // After readInputFile(), tasks stores all FireRequests in sorted order by time
        // Send these FireRequests to our EventScheduler
        // Pass pointer to this entity so that we can re-add tasks that are ready?
        EventScheduler scheduler = new EventScheduler();
        for (FireRequest fr : tasks) {
            scheduler.addEvent(new Event(fr, fr.getTime()));
        }
        scheduler.start();

        // Create and start threads to listen to other subsystems
        Thread receiver = new FireIncidentSubsystem.ListenToScheduler();
        Thread sender = new FireIncidentSubsystem.SendToScheduler();

        receiver.start();
        sender.start();

        // Retrieve and store FireRequests that are ready to be sent from the EventScheduler
        while (true) {
            Event event = scheduler.getEvent();
            synchronized (readyToSend) {
                FireRequest fr = (FireRequest) event.getEvent();
                readyToSend.add(fr);
                //pendingRequestIds.add(fr.getId());
                readyToSend.notifyAll();
            }
        }
    }

    /**
     * @param inputFile read the incidents' detail from the inputFile
     * and stores them as FireRequest objects.
     * Each line in the file represents a fire incident with details separated by commas.
     */
    public void readInputFile(String inputFile) {
        try (BufferedReader reader = new BufferedReader(new FileReader(inputFile))) {
            String line;

            int id = 0;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                String time = parts[0].trim().replaceAll(":", "-");

                int zoneId = Integer.parseInt(parts[1].trim());
                String eventType = parts[2].trim();
                String severity = parts[3].trim();

                int copies = switch (severity) {
                    case "High" -> 3;
                    case "Moderate" -> 2;
                    case "Low" -> 1;
                    default -> 1;
                };
                String baseId = String.valueOf(++id);
                String[] suffixes = {"A", "B", "C"};
                for (int i = 0; i < copies; i++) {
                    expectedCompletions.add(baseId + suffixes[i]);
                    System.out.println("Adding to expected Completions: " + baseId + suffixes[i]);
                }

                FireRequest task = new FireRequest(time, zoneId, eventType, severity, baseId);
                addTask(task);
            }
            System.out.println("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Inserts a {@link FireRequest} into the internal task list {@link FireIncidentSubsystem#tasks} in chronological order based on timestamp
     * <p>
     * performs an insertion sort by task.getTime(). If a task with the same timestamp already exists, the new task is inserted before it.
     *
     * @param task the {@link FireRequest} to be added to the task list
     */
    public void addTask(FireRequest task){
        System.out.println(task.getTime());
        LocalTime taskLocalTime = LocalTime.parse(task.getTime(), timeFormatter);
        int low = 0;
        int high = tasks.size()-1;
        // insertion sort operation:
        while (low <= high) {
            int mid = (low + high) / 2;
            FireRequest midTask = tasks.get(mid);
            //Insertion comparison
            LocalTime midTaskTime = LocalTime.parse(midTask.getTime(), timeFormatter);
            int comparison = midTaskTime.compareTo(taskLocalTime);
            if (comparison < 0) {
                low = mid + 1;
            } else if (comparison > 0) {
                high = mid - 1;
            } else {
                tasks.add(mid, task);
                return;
            }
        }
        tasks.add(low, task);
        //logging
        logger.log("Task added", task.toString());
        System.out.println("Task added: " + task);

    }
    /**
     * Reads the zone information from the given CSV file and stores it in the static zoneMap.
     * Expected CSV format:
     * Zone ID,Zone Start,Zone End
     * 1,(0;0),(700;600)
     * 2,(0;600),(650;1500)
     *
     * @param zoneFile the file containing zone definitions.
     */
    public void readZoneFile(String zoneFile) {
        try (BufferedReader reader = new BufferedReader(new FileReader(zoneFile))) {
            String header = reader.readLine(); // Skip header
            String line;
            while ((line = reader.readLine()) != null) {
                // Expected line format: Zone ID,Zone Start,Zone End
                String[] parts = line.split(",");
                if (parts.length < 3) continue;
                int zoneId = Integer.parseInt(parts[0].trim());

                // Parse start coordinates
                String startStr = parts[1].trim();
                startStr = startStr.substring(1, startStr.length() - 1); // Remove parentheses
                String[] startCoords = startStr.split(";");
                int startX = Integer.parseInt(startCoords[0].trim());
                int startY = Integer.parseInt(startCoords[1].trim());

                // Parse end coordinates
                String endStr = parts[2].trim();
                endStr = endStr.substring(1, endStr.length() - 1); // Remove parentheses
                String[] endCoords = endStr.split(";");
                int endX = Integer.parseInt(endCoords[0].trim());
                int endY = Integer.parseInt(endCoords[1].trim());

                // Create and store the zone
                Zone zone = new Zone(zoneId, startX, startY, endX, endY);
                zoneMap.put(zoneId, zone);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * Sends an incident request to the scheduler.
     * Extracts the next available fire request, formats the data, and sends it via a UDP packet.
     */
    public void sendIncident(String request){
        logger.log("Sending Incident", request);
        byte msg[] = request.toString().getBytes();
        DatagramPacket packet;

        try {
            packet = new DatagramPacket(msg, msg.length, InetAddress.getLocalHost(), Scheduler.FIRE_TO_SCHEDULER_PORT);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }

        try {
            sendSocket.send(packet);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Receives updates from the scheduler via a UDP packet.
     * Waits for an incoming message and prints the received update.
     */
    public String receiveUpdate() {
        try {
            byte data[] = new byte[Scheduler.DATA_BUFFER_SIZE];
            DatagramPacket receivePacket = new DatagramPacket(data, data.length);
            receiveSocket.receive(receivePacket);
            String update = new String(data, 0, receivePacket.getLength());
            //logging
            logger.log("Received Update", update);
            return update;
        } catch (SocketTimeoutException e) {
            //logging
            logger.log("Update Timeout", "No updates available");
            return "No updates available";
        } catch (IOException e) {
            logger.log("Receive Error", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /** creates instance of fire incident subsystem and starts thread function to invoke run to handle the 4 threads of this fire incident system */
    public static void main(String[] args) {
        FireIncidentSubsystem fis = new FireIncidentSubsystem();
        Thread thread = new Thread(fis);
        thread.start();
    }

    /**
     * Getter used for testing
     *
     * @return The Scheduler instance associated with this subsystem.
     */
    public Scheduler getScheduler() {
        return scheduler;
    }

    /**
     * Getter
     *
     * @return list of fire requests
     */
    public List<FireRequest> getTasks() {
        return tasks;
    }

    /**
     * Getter
     * @return the socket used for sending udp packets 'sendSocket'
     */
    public DatagramSocket getSendSocket() {
        return sendSocket;
    }

    /**
     * Getter
     * @return the socket used for receiving udp packets 'receiveSocket'
     */
    public DatagramSocket getReceiveSocket() {
        return receiveSocket;
    }
}

/**
 * A background logging utility that asynchronously writes timestamped log entries to a file.
 * <p>
 * The logger uses a daemon thread and a blocking queue to collect and write log entries,
 * ensuring non-blocking and thread-safe logging from multiple sources.
 */
class LoggerDaemon implements Runnable {
    /**
     * Thread-safe queue to hold log entries before they are written to file */
    private final BlockingQueue<String> logQueue = new LinkedBlockingQueue<>();
    /**
     * Flag to control the logging loop. When set to false, the logger will finish writing remaining entries and stop */
    private volatile boolean running = true;
    /**
     * Name of the file to which log entries will be written */
    private final String logFileName = "firesubsystem_logs.txt";
    /**
     * Formatter to apply timestamps in the format HH:mm:ss.SSS */
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    /**
     * Constructs and starts the logger daemon thread, and thread is marked as a daemon so it will not prevent JVM shutdown */
    public LoggerDaemon() {
        Thread thread = new Thread(this);
        thread.setDaemon(true); // Daemon thread so it doesn't block JVM exit
        thread.start();
    }

    /**
     * Adds a log entry to the queue with the current timestamp, specified action, and data.
     *
     * @param action the type or name of the event being logged
     * @param data   the associated information to include in the log
     */
    public void log(String action, String data) {
        String timestamp = LocalDateTime.now().format(formatter);
        String logEntry = timestamp + " - " + action + " - " + data;
        logQueue.add(logEntry);
    }

    /**
     * The main logging loop executed by the daemon thread
     * <p>
     * Continuously polls the queue and writes available entries to the log file,
     * flushing after each write. Sleeps briefly if no entries are available
     */
    @Override
    public void run() {
        try (FileWriter writer = new FileWriter(logFileName, false)) {
            while (running || !logQueue.isEmpty()) {
                String logEntry = logQueue.poll();
                if (logEntry != null) {
                    writer.write(logEntry + "\n");
                    writer.flush();
                } else {
                    // Sleep briefly if no messages are available
                    Thread.sleep(50);
                }
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("LoggerDaemon encountered an error: " + e.getMessage());
        }
    }

    /**
     * Signals the logger main loop to stop running after it has flushed all remaining log entries
     * <p>
     * Intended to be called during shutdown to gracefully terminate logging operations when simulation is complete
     */
    public void shutdown() {
        running = false;
    }
}
