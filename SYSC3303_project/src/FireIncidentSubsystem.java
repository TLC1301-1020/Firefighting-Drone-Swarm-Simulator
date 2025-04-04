import jdk.javadoc.doclet.Taglet;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

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
     * scheduler instance for managing fire requests
     */
    private Scheduler scheduler;
    /**
     * list of fire requests created from the data received in the input file
     * <a href="file:../src/fireincedents.txt">/src/fireincedents.txt</a>
     */
    private List<FireRequest> tasks;

    private DatagramSocket receiveSocket, sendSocket;

    /**
     * A static map that holds zone information parsed from the csv
     * Key: Zone ID, Value: Zone object
     */
    public static Map<Integer, Zone> zoneMap = new HashMap<>();

    private static final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH-mm-ss");

    /**
     * ArrayList that stores FireRequests from an EventScheduler that are ready to be processed.
     */
    private ArrayList<FireRequest> readyToSend = new ArrayList<>();
    private final List<String> expectedCompletions = Collections.synchronizedList(new ArrayList<>());
    private final List<String> receivedCompletions = Collections.synchronizedList(new ArrayList<>());
    private static final String COMPLETED_SUFFIX = ":COMPLETED";

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
     * Thread to listen to DroneSubsystem.
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
     * TODO: Thread to listen to DroneSubsystem.
     * TODO: Make a new list for requests that are ready to send ex) List<FireRequest> readyToSend
     * TODO: Synchronize on readyToSend
     * TODO: if there's a request in readyToSend, send it with sendIncident(firerequest.toString())
     */
    private class SendToScheduler extends Thread {

        @Override
        public void run() {
            while (true) {
                try {
                    FireRequest request;
                    synchronized (readyToSend) {
                        while (readyToSend.isEmpty()) {
                            System.out.println("[ STS ] - waiting for upcoming tasks.");
                            readyToSend.wait();
                        }
                        //not empty list, taking the request
                        request = readyToSend.remove(0);
                        readyToSend.notifyAll();
                    }
                    //send the request
                    if(request!=null){
                        System.out.println("[ STS ] - sending the request: " + request);
                        sendIncident(request.toString());
                    }
                } catch (InterruptedException e) {
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
//                String time = parts[0].trim();
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

    /**TODO: Add task to the list by timestamps
     *
     */
    public void addTask(FireRequest task){
        System.out.println(task.getTime());
        LocalTime taskLocalTime = LocalTime.parse(task.getTime(), timeFormatter);
        int low = 0;
        int high = tasks.size()-1;

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
            // Set a timeout of 5000ms (5 seconds)
//            receiveSocket.setSoTimeout(5000);
            byte data[] = new byte[Scheduler.DATA_BUFFER_SIZE];
            DatagramPacket receivePacket = new DatagramPacket(data, data.length);
            receiveSocket.receive(receivePacket);
            return new String(data, 0, receivePacket.getLength());
        } catch (SocketTimeoutException e) {
            return "No updates available";
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public static void main(String[] args) {
        FireIncidentSubsystem fis = new FireIncidentSubsystem();
        Thread thread = new Thread(fis);
        thread.start();
    }

    /**
     * Getters
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

//    public DatagramPacket getSendPacket() {
//        return sendPacket;
//    }
//
//    public DatagramPacket getReceivePacket() {
//        return receivePacket;
//    }

    public DatagramSocket getSendSocket() {
        return sendSocket;
    }

    public DatagramSocket getReceiveSocket() {
        return receiveSocket;
    }
}
