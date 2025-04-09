import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.*;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The {@code DroneSubsystem} class manages multiple drone threads and handles communication
 * between drones and the {@code Scheduler} using UDP. It maintains a request queue for drones
 * to submit requests and a response queue for receiving instructions from the Scheduler.
 * <p>
 * Implements {@code Runnable} to execute in a separate thread
 */
public class DroneSubsystem implements Runnable {

    /** Handles sending and receiving of UDP packets */
    private DatagramSocket sendSocket, receiveSocket;
    /** Stores all Drone instances, mapped by ID */
    private final HashMap<Integer,Drone> drones = new HashMap<>();

    /** Indicates whether drones have been initialized */
    private boolean dronesInitialized;
    /** Thread safe queue for drones to send requests to the Scheduler */
    private final ConcurrentLinkedQueue<String> requestQueue = new ConcurrentLinkedQueue<>();
    /** Thread safe map for drones to receive Scheduler responses; key: drone ID, value: response queue */
    private final ConcurrentHashMap< Integer, LinkedList<String> > responseQueue = new ConcurrentHashMap<>();
    /** Zone data loaded from CSV; key: Zone ID, value: Zone */
    public static Map<Integer, Zone> zoneMap = new HashMap<>();

    /** Schedules and triggers drone fault events */
    EventScheduler scheduler = new EventScheduler();

    /** Stores drone fault events from file for scheduling */
    private ArrayList<Event> faults = new ArrayList<>();

    /** Master run flag to stop all threads; disabled only during testing */
    private volatile boolean running = true;

    /**
     * Initializes the DroneSubsystem by creating DatagramSockets for sending and receiving data
     * The sendSocket is created without a specific port, while the receiveSocket listens on the
     * DRONE_SUBSYSTEM_PORT defined in the Scheduler
     */
    public DroneSubsystem()
    {
        try {
            sendSocket = new DatagramSocket();
            receiveSocket = new DatagramSocket(Scheduler.DRONE_SUBSYSTEM_PORT);
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
    }
    /**
     * Returns the DatagramSocket used for sending messages to other components
     *
     * @return the send socket
     */
    DatagramSocket getSendSocket() {
        return sendSocket;
    }
    /**
     * Returns the DatagramSocket used for receiving messages from other components
     *
     * @return the receive socket
     */
    DatagramSocket getReceiveSocket() {
        return receiveSocket;
    }

    /**
     * Adds a request to the request queue and notifies all waiting threads
     * Synchronized to ensure thread safety while modifying the request queue
     *
     * @param request the request to be added to the queue
     */
    public void addRequest(String request) {
        synchronized (requestQueue) {
            this.requestQueue.offer(request);
            requestQueue.notifyAll();
        }
    }

    /**
     * Retrieves and removes the next request from the request queue
     * If the queue is empty, it waits until a new request is added
     * Synchronized to ensure thread safety while accessing the request queue
     *
     * @return the next request from the queue, or "REQUESTQUEUE_EMPTY" if interrupted
     */
    public String getRequest()
    {
        synchronized (this.requestQueue)
        {
            while (this.requestQueue.isEmpty())
            {
                try {
                    DroneEventLogger.getInstance().info("DroneSubsystem", "Run", "Waiting for a Drone request");
                    this.requestQueue.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return "REQUESTQUEUE_EMPTY";
                }
            }
            return this.requestQueue.poll();
        }
    }

    /**
     * Adds a response for a specific drone to the response queue
     * If no entry exists for the drone, a new list is created
     * Synchronized to ensure thread safety while modifying the response queue
     *
     * @param droneId the ID of the drone receiving the response
     * @param response the response to be added
     */
    public void addResponse(int droneId, String response)
    {
        synchronized (this.responseQueue)
        {
            LinkedList<String> responses = this.responseQueue.get(droneId);
            if ( responses == null )
            {
                responses = new LinkedList<>();
                this.responseQueue.put(droneId, responses );
            }

            responses.add(response);

            this.responseQueue.put(droneId, responses);
            DroneEventLogger.getInstance().info("DroneSubsystem", "ListeningToScheduler", "Response handled.");
            this.responseQueue.notifyAll();
        }
    }

    /**
     * Retrieves and removes the response for the specified drone
     * If no response is available, the thread waits until a response is added
     *
     * @param droneId the ID of the drone for which the response is retrieved
     * @return the first response in the queue for the specified drone
     */
    public String getResponse(int droneId) {
        synchronized (this.responseQueue) {
            while (!this.responseQueue.containsKey(droneId) || this.responseQueue.get(droneId).isEmpty() ) {
                try {
                    DroneEventLogger.getInstance().info("Drone", String.valueOf(droneId), "Waiting for Scheduler response");
                    this.responseQueue.wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            responseQueue.notifyAll();

            LinkedList<String> responses = this.responseQueue.get(droneId);

            return responses.poll();
        }
    }

    /**
     * Initializes all drones by sending an initialization request to the Scheduler
     * and waiting for an acknowledgment for each drone. Successfully initialized drones
     * are added to the drone collection
     *
     * @param droneSubsystem the DroneSubsystem instance used to manage drone operations
     * @param numberOfDrones the number of drones to initialize
     */
    public void initializeAllDrones(DroneSubsystem droneSubsystem, int numberOfDrones) {
        // Check if drones are already initialized
        if (this.dronesInitialized) return;

        int droneCounter = 0;
        for (int i = 0; i < numberOfDrones; ++i) {
            String request = i + ":[IDLE]:INIT:0:0:0";
            System.out.println("\n[ DSS->S ] INIT DRONE REQ:           " + request);
            sendPacket(request);

            // Wait for a response from the Scheduler
            String response = receivePacket();
            System.out.println("\n[ S->DSS ] INIT DRONE RES:           " + response);

            if (response != null && response.trim().contains("ACK")) {
                // Registration is successful; add the drone thread to the collection.
                this.drones.put(i, new Drone(droneSubsystem, i));
                droneCounter++;
                System.out.println("DRONE " + i + " is online");
            } else {
                System.out.println("DRONE " + i + " failed to come online. Response: " + response);
            }
        }
        System.out.println(droneCounter + "/" + numberOfDrones + " are now online");
        this.dronesInitialized = true;
    }

    /**
     * Starts a daemon thread that listens for responses from the Scheduler
     * It processes incoming responses and handles shutdown or passes requests to the DroneSubsystem
     */
    private void startListeningToScheduler() {
        Thread schedulerListener = new Thread(() -> {
            while (running) {
                String response = receivePacket();
                System.out.println("[ S->DSS ] startListeningToScheduler thread received RESPONSE :     " + response);

                DroneEventLogger.getInstance().info("DroneSubsystem", "ListeningToScheduler", "Response received from scheduler: " + response);

                if(response.equals("SHUTDOWN"))
                {
                    shutdown();
                    break;
                } else
                {
                    handleDroneResponse(response);  // function used only for passing scheduler requests to the drone subsystem
                }
            }
        });
        schedulerListener.setDaemon(true);
        schedulerListener.start();
    }
    /**
     * A thread that listens for faults and injects them into the corresponding drones
     * Faults include "DRONE_STUCK", "NOZZLE_JAMMED", and "PACKET_LOSS"
     */
    private class processFaults extends Thread {
        @Override
        public void run() {

            while (running) {
                // Block until a fault is ready to process
                DroneEventLogger.getInstance().info("DroneSubsystem", "ProcessFaults", "Waiting for new Drone faults");
                String fault = (String)scheduler.getEvent().getEvent();
                String[] parts = fault.split(":");

                int droneId = -1;
                try {droneId = Integer.parseInt(parts[1]);}
                catch (NumberFormatException e) {
                    System.out.println("ERROR: Invalid int parsing processFaults");
                    return;
                }
                DroneEventLogger.getInstance().info("DroneSubsystem", "ProcessFaults", "Received Drone fault, injecting");
                Drone drone = drones.get(droneId);

                if (drone != null) {
                    switch (parts[0]) {
                        case "DRONE_STUCK":
                            drone.setStuckFault();
                            break;
                        case "NOZZLE_JAMMED":
                            drone.setJammedFault();
                            break;
                        case "PACKET_LOSS":
                            drone.setPacketLossFault();
                            break;
                    }
                    System.out.println("\nDRONE " + droneId + " injected with " + parts[0] + " fault\n");
                } else {
                    System.out.println("\nFault " + parts[0] + " failed to inject: Invalid Drone ID\n");
                }
            }
        }
    }
    /**
     * The main execution loop for the DroneSubsystem
     * <p>
     * 1. Starts listening for responses from the Scheduler
     * 2. Initializes and starts all drones
     * 3. Adds faults to the EventScheduler and starts it
     * 4. Starts a separate thread to process faults
     * 5. Continuously checks the request queue, handling drone requests and sending them to the Scheduler
     */
    public void run() {
        startListeningToScheduler();

        Collection<Drone> allDrones = this.drones.values();
        for (Drone drone : allDrones) {
            drone.start();
        }

        for (Event fault : faults) {
            scheduler.addEvent(fault);
        }
        scheduler.start();

        // Start the processFaults thread
        Thread faultHandler = new DroneSubsystem.processFaults();
        faultHandler.start();

        while (running) {
            // Check request queue - communication from drones
            String request = getRequest();
            DroneEventLogger.getInstance().info("DroneSubsystem", "Run", "Received a Drone request, sending to Scheduler");
            System.out.println("\n[ DSS->S ] HANDLING DRONE REQ :                                         " + request);

            // Artificial delay added here to slow things down
            try {
                Thread.sleep(100); // Adjust the delay as needed
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Send UDP packet direct to scheduler with drone request
            if(running)sendPacket(request);
        }
    }

    /**
     * Handles a response received from the Scheduler and adds it to the appropriate drone's response queue
     * <p>
     * Parses the drone ID from the response and stores it in the response queue for later retrieval
     *
     * @param response The response string from the Scheduler
     */
    public void handleDroneResponse(String response)
    {
        String[] items = response.split(":");

        int droneId = -1;
        try {droneId = Integer.parseInt(items[1]);}
        catch (NumberFormatException e) {
            System.out.println("ERROR: Invalid int parsing handleDroneResponse");
            return;
        }

        // add response to the response queue for drones to get
        addResponse(droneId, response);
    }

    /**
     * Sends a request packet to the Scheduler over UDP
     * <p>
     * Converts the request string to bytes and sends it as a DatagramPacket to the Scheduler
     *
     * @param request The request to be sent to the Scheduler
     */
    public void sendPacket(String request){

        byte msg[] = request.getBytes();
        DatagramPacket packet;

        try {
            packet = new DatagramPacket(msg, msg.length, InetAddress.getLocalHost(), Scheduler.DRONE_TO_SCHEDULER_PORT);
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
     * Receives a packet from the Scheduler over UDP
     * <p>
     * Waits for an incoming DatagramPacket on the received socket, and returns the received data as a string
     *
     * @return The received data as a string
     * @throws RuntimeException If an error occurs while receiving the packet
     */
    public String receivePacket(){

        byte data[] = new byte[Scheduler.DATA_BUFFER_SIZE];
        DatagramPacket receivePacket = new DatagramPacket(data, data.length);

        try {
            // Block until a datagram is received via socket
            receiveSocket.receive(receivePacket);
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
        int len = receivePacket.getLength();
        return new String(data,0,len);
    }

    /**
     * Reads a fault file and adds faults to the faults list
     * <p>
     * The method processes each line in the given file, splitting it into event time and type,
     * and adds the parsed events to the faults list
     *
     * @param inputFile The path of the input file containing fault events
     */
    public void readFaultFile(String inputFile){

        try (BufferedReader reader = new BufferedReader(new FileReader(inputFile))){
            String line;
            while((line = reader.readLine()) != null){
                String[] parts = line.split(",");

                if (parts.length >= 2) {
                    String time = parts[0].trim().replaceAll(":", "-");
                    String eventType = parts[1].trim();

                    Event event = new Event(eventType, time);
                    faults.add(event);
                    System.out.println("Reading faults: " + event.getEventTime() + " - " + event.getEvent());
                }
            }
            System.out.println("\n");
        } catch (IOException e){
            e.printStackTrace();
        }

    }
    /**
     * Reads a zone file and stores zone data in a static map
     * <p>
     * The method parses each line in the file, extracting zone information including coordinates,
     * and adds each zone to the zoneMap
     *
     * @param zoneFile The path of the zone file to be processed
     */
    public void readZoneFile(String zoneFile) {
        try (BufferedReader reader = new BufferedReader(new FileReader(zoneFile))) {
            String header = reader.readLine(); // Skip header
            String line;
            while ((line = reader.readLine()) != null) {

                String[] parts = line.split(",");
                if (parts.length < 3) continue;
                int zoneId = Integer.parseInt(parts[0].trim());

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
     * Gracefully shuts down the program by stopping execution and closing sockets
     */
    private void shutdown() {
        this.running = false;
        DroneEventLogger.getInstance().info("All", "All", "Program ended, shutting down.");

        // close sockets if they are open
        if (sendSocket != null && !sendSocket.isClosed()) sendSocket.close();
        if (receiveSocket != null && !receiveSocket.isClosed()) receiveSocket.close();
    }

    //Getters
    public Zone getZone(int zoneId) {
        return zoneMap.get(zoneId);
    }
    public ArrayList<Event> getFaults(){
        return faults;
    }

    public static void main(String[] args)
    {
        DroneSubsystem dss = new DroneSubsystem();
        dss.readZoneFile("SYSC3303_project/src/zone_file.csv");
        dss.readFaultFile("SYSC3303_project/src/Faults.txt");
        dss.initializeAllDrones(dss, 10);

        Thread droneSubsystem = new Thread( dss );
        droneSubsystem.start();
    }
}