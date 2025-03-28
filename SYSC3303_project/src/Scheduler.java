import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * {@code Scheduler} class coordinates fire incident handling by acting as the central controller
 * between the {@code FireIncidentSubsystem} and the {@code DroneSubsystem}.
 * <p>
 * Summary:
 *   <li>Receives fire incident requests from the {@code FireIncidentSubsystem}
 *   <li>Maintains a queue of pending fire requests
 *   <li>Assigns fire requests to drones based on availability, location, and severity
 *   <li>Receives status updates and requests from drones
 *   <li>Sends commands to drones and responds back to the fire subsystem
 *
 * <p>This class has three main threads:
 * <ul>
 *   <li>{@code listenToFire}: Listens for fire incident data and completion data requests from the {@code FireIncidentSubsystem}.
 *           If a new incident is received, it is added to the request queue.
 *           If a {@code FIRE_DATA_REQUEST} is received, the scheduler responds with completed fire task information
 *
 *   <li>{@code listenToDrone}: Listens for drone status updates and task requests from the {@code DroneSubsystem}.
 *      Parses drone messages and updates internal drone states. Responds with commands or task acknowledgements.
 *
 *   <li>{@code ProcessPendingRequests}: Continuously monitors the fire request queue.
 *
 *           Tries to assign each pending request to an available drone.
 *           Considers drone availability, task severity, and possible rerouting logic.
 */
public class Scheduler {
    public static Map<Integer, Zone> zoneMap = new HashMap<>();

    /**
     * hash map of {@link DroneStatus} objects by drone ID <p>DroneStatus stores drone data and is updated on the scheduler side only</p>*/
    private HashMap<Integer, DroneStatus> drones;
    private List<Integer> reassignedDrones;
    /**
     * Thread safe Queue to store fire requests tasked from FireIncidentSubsystem, but will also hold requests reassigned*/
    private Queue<FireRequest> requestQueue;

    private SchedulerState currentState;

    public static final int DATA_BUFFER_SIZE = 256;
    public static final int FIRE_TO_SCHEDULER_PORT = 5000;
    public static final int DRONE_TO_SCHEDULER_PORT = 5001;
    public static final int FIRE_INCIDENT_SUBSYSTEM_PORT = 5002;
    public static final int DRONE_SUBSYSTEM_PORT = 5003;

    private DatagramSocket fireReceiveSocket, droneReceiveSocket, fireSendSocket, droneSendSocket;
    private final ConcurrentLinkedQueue<DroneAssignment> pendingAssignments = new ConcurrentLinkedQueue<>();
    /**
     * boolean set for all thread function loops. set to false only in testing contexts for back to back instances of scheduler threads running */
    private volatile boolean running = true;

    /**
     * Object used to store drones marked for 'interrupt' reassignment by SP thread for checking in SD thread */
    public static class DroneAssignment {
        int droneId;
        FireRequest request;

        DroneAssignment(int droneId, FireRequest request) {
            this.droneId = droneId;
            this.request = request;
        }
    }

    /**
     * function used to parse zone_file.csv for zone data and creates {@link Zone} object from data with respective coordinates and zone id */
    public void parseZoneFile(String zoneFilePath) {
        try (BufferedReader br = new BufferedReader(new FileReader(zoneFilePath))) {
            String header = br.readLine(); // Skip header line
            String line;
            while ((line = br.readLine()) != null) {
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
                // Create a new Zone and add it to the map
                Zone zone = new Zone(zoneId, startX, startY, endX, endY);
                zoneMap.put(zoneId, zone);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * SF Thread to listen to packets from {@link FireIncidentSubsystem} and creates {@link FireRequest} objects as they arrive
     * <p>responds to FireIncidentSubsystem with acknowledgements and when the FireRequests are completed</p>
     */
    private class listenToFire extends Thread {
        /**
         * SF Thread function to listen to packets from {@link FireIncidentSubsystem} and creates {@link FireRequest} objects as they arrive
         * <p>responds to FireIncidentSubsystem with acknowledgements and when the FireRequests are completed</p>
         */
        @Override
        public void run() {
            System.out.println("\n[   SF]  -   SCHEDULER IS LISTENING TO FIRE  -   ");

            while (running) {
                System.out.println("\n[   SF]  -   SCHEDULER IS WAITING FOR MESSAGE FROM FIRE  -   ");

                // This request will be a new FireRequest to add
                String request = receivePacket(fireReceiveSocket);

                System.out.print("\n[ SF<-F ]  -   SCHEDULER RECEIVED FROM FIRE: "+request+ "  -   ");

                System.out.println("request is new fire request - sending through sendSocket + port: " + FIRE_INCIDENT_SUBSYSTEM_PORT);

                Queue<FireRequest> fireRequests = makeFireRequests(request);
//                FireRequest fireRequest = new FireRequest(request);
                for( FireRequest fr : fireRequests ) addRequest(fr);

                System.out.println("\n[ SF->F ]  -   SCHEDULER WILL NOW SEND TO FIRE  -   SCHEDULER:ACKNOWLEDGED");
                sendPacket(fireSendSocket, FIRE_INCIDENT_SUBSYSTEM_PORT, "SCHEDULER:ACKNOWLEDGED");
                System.out.println("\n[ SF->F ]  -   * SENT TO FIRE  -   SCHEDULER:ACKNOWLEDGED");
            }
        }
    }

    /**
     * Parses a single FireRequest string and expands it into multiple {@link FireRequest} objects
     * based on its severity level.
     * <p>
     * The number of fire requests created is determined by the severity:
     * <ul>
     *   <li>Low &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;→ 1 FireRequest</li>
     *   <li>Moderate → 2 FireRequests</li>
     *   <li>High &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;→ 3 FireRequests</li>
     * </ul>
     * Each generated request has a unique sub-ID appended (eg A, B, C) to indicate this was a split request
     * <p>
     * The input string must follow the format produced by {@code FireRequest.toString()}:
     * <pre>
     * FireRequest{time=12:00, zone=2, event=FIRE_DETECTED, severity=High, id=1}
     * </pre>
     *
     * @param request the string representation of a FireRequest
     * @return a queue of FireRequest objects with adjusted IDs and same metadata
     */
    private Queue<FireRequest> makeFireRequests(String request)
    {
        Queue<FireRequest> fireRequests = new LinkedList<>();

        request = request.replace("FireRequest{", "").replace("}", "");
        String[] parts = request.split(", ");

        int requestsRequired = 0;
        for (String part : parts)
        {
            if(part.contains("severity"))
            {
                if (part.contains("High")) requestsRequired = 3;
                else if (part.contains("Moderate")) requestsRequired = 2;
                else if (part.contains("Low")) requestsRequired = 1;
            }
        }
        String[] subID = {"A","B","C"};
        for( int i = 0 ; i < requestsRequired ; ++i )
        {
            String time = "0";
            int zoneId = -1;
            String eventType = "0";
            String severity = "0";
            String id = "0";

            for (String part : parts) {
                String[] value = part.split("=");
                switch (value[0]) {
                    case "time":
                        time = value[1];
                        break;
                    case "zone":
                        zoneId = Integer.parseInt(value[1]);
                        break;
                    case "event":
                        eventType = value[1];
                        break;
                    case "severity":
                        severity = value[1];
                        break;
                    case "id":
                        id = value[1]+subID[i];
                        break;
                }
            }
            fireRequests.add(new FireRequest(time, zoneId, eventType, severity, id));
        }
        return fireRequests;
    }



    /**
     * SD Thread to listen to packets from {@link DroneSubsystem} and calls {@link Scheduler#handleDroneRequest} to handle the requests
     * <p>{@link Scheduler#handleDroneRequest} returns a response string to send back to DroneSubsystem</p>
     */
    private class listenToDrone extends Thread {
        /**
         * SD Thread function to listen to packets from {@link DroneSubsystem} and calls {@link Scheduler#handleDroneRequest} to handle the requests
         * <p>{@link Scheduler#handleDroneRequest} returns a response string to send back to DroneSubsystem</p>
         */
        @Override
        public void run() {
            System.out.println("\n[SD  ]  -   SCHEDULER IS LISTENING TO DRONE  -   ");
            while (running) {
                System.out.println("\n[SD  ]  -   SCHEDULER IS WAITING FOR MESSAGE FROM DRONE  -   ");
                // Receive packet from the DroneSubsystem
                String request = receivePacket(droneReceiveSocket);

                // get drone status -
                System.out.println("\n[SD<-D ]  -   SCHEDULER RECEIVED FROM DRONE: "+request+ "  -   ");

                // Parse the packet (assuming we are not actually storing physical Drones anymore, and instead are only storing crucial data for each drone):
                String response = handleDroneRequest(request);

                System.out.println("\n[SD->D ]  -   SCHEDULER RESPONSE TO DRONE: "+response+ "  -   ");

                // send response
                sendPacket( droneSendSocket, DRONE_SUBSYSTEM_PORT, response );
            }
        }
    }

    /**
     * SP Thread to handle Scheduler fire requests added from {@link listenToFire} and checks the {@link #requestQueue}
     * <p>{@link FireRequest} objects taken (not removed) from this queue is passed into {@link Scheduler#assignFireRequest}</p>
     */
    private class ProcessPendingRequests implements Runnable {
        /**
         * SP Thread function to handle Scheduler fire requests added from {@link listenToFire} and checks the {@link #requestQueue}
         * <p>{@link FireRequest} objects taken (not removed) from this queue is passed into {@link Scheduler#assignFireRequest}</p>
         */
        @Override
        public void run() {
            System.out.println("\n[  SP  ]  -   SCHEDULER IS CHECKING LISTENING TO REQUEST QUEUE  -   ");
            while (running) {
                synchronized(requestQueue) {
                    if (!requestQueue.isEmpty()) {
                        // request is removed here and added back only if it is not assigned to a drone
                        FireRequest req = requestQueue.remove();

                        System.out.println("\n[  SP  ]  -   SCHEDULER REQUEST QUEUE REMOVED  -       " + req.toString());

                        if ( assignFireRequest(req) )   // returns true if drone was assigned this fire request
                        {
                            System.out.println("\n[  SP  ]  -   SCHEDULER SUCCESSFULLY TASKED A DRONE TO ANSWER REQUEST  -       " + req.toString());
                        }
                        else
                        {   // request is added back here only when it is not assigned to a drone
                            requestQueue.add(req);
                            System.out.println("\n[  SP  ]  -   SCHEDULER FOUND NO DRONES TO ANSWER REQUEST              -       " + req.toString());
                            try {
                                Thread.sleep(100); // Check every 1000ms; adjust as needed.
                            } catch (InterruptedException e) {
                                System.out.println("ProcessPendingRequests thread interrupted.");
                            }
                        }
                    }
                }
                try {
                    Thread.sleep(100); // Check every 100ms; adjust as needed.
                } catch (InterruptedException e) {
                    System.out.println("ProcessPendingRequests thread interrupted.");
                }
            }
        }
    }


    /**
     * Parses the Drones request and returns:
     * <li>an ACK header for permission for that drone to proceed with its next state transition (or to continue waiting for a fire request)</li>
     * <li>a NEW header with an attached fire request when the drone is to be assigned OR reassigned a new fire request </li>
     * <p>response format is:<p>
     *     RESPONSE_HEADER:REQUEST<p>
     *     the same as...<p>
     *     RESPONSE_HEADER:DRONE_ID:STATE:REQUEST_BODY:X_POS:Y_POS:CURR)FIREREQUEST
     @return String value of entire formatted response to send to Drone in {@link listenToDrone}
     */
    private String handleDroneRequest(String request)
    {
        System.out.println("\n[SD   ]  -   SCHEDULER HANDLE DRONE REQUEST: "+request+ "  -   ");

        if (request.startsWith("INIT")) {
            // Extract the drone ID if needed; here we assume items[0] is the drone ID in the registration request.
            int droneId = Integer.parseInt(request.split(":")[0]);
            System.out.println("[SD   ] Registering drone " + droneId);
            return "ACK:" + droneId + ":REGISTERED";
        }

        // check format     -   in expected format "DRONE_ID:STATE:REQUEST:X:Y:CURR_TASK:FIREREQ_ID"
        String[] items = request.split(":");
//        if (items.length != 6) return "ERROR: Invalid request format:" + request;
        FireRequest currTask = new FireRequest(items[5]);

        // get drone id     -   in expected format "DRONE_ID:STATE:REQUEST:X:Y:CURR_TASK:ID"
        int droneId = -1;
        try {
            droneId = Integer.parseInt(items[0]);
        } catch (NumberFormatException e) {
            return "ERROR: Invalid drone ID";
        }
        // check drone collection for this drone id
        DroneStatus drone = this.drones.get(droneId);
        // if drone does not exist with scheduler, register drone and return register event
        if (drone == null) return registerDrone(droneId) + ":" + request;

        // otherwise parse & handle request...

        // get current state of this drone     -   in expected format "DRONE_ID:STATE:REQUEST:X:Y:CURR_TASK:FIREREQ_ID"
        String droneState = items[1];

        // get request of this drone and convert it to DroneEvent
        DroneEvent eventRequest = null;
        try {
            eventRequest = DroneEvent.valueOfEvent(items[2]); // Convert string to enum
        } catch (IllegalArgumentException e) {
            if (!items[2].equals( DroneEvent.STATUS )) return "ERROR: Unknown drone event: " + items[2];
            else
            {
                // Status request event flow here
            }
        }

        // get location of this drone
        int x,y,zone = -1;
        try {
            x = Integer.parseInt(items[3]);
            y = Integer.parseInt(items[4]);
            zone = currTask.getZoneId();
        } catch (NumberFormatException e) {
            return "ERROR: Invalid location value: " + items[3] + "," + items[4];
        }
        // update location of drone
        drone.setLocation(x, y);
        /* request types
            "STATUS"   DRONE_ID:<STATE>:STATUS:X:Y:CURR_TASK:FIREREQ_ID  -> when there is a status update
                       DRONE_ID:<STATE>:REQUEST:X:Y:CURR_TASK:FIREREQ_ID  -> when there is all other requests
         */

        /*  response types
            "WAIT" in format WAIT:DRONE_ID:STATE:REQUEST:X:Y:CURR_TASK:FIREREQ_ID    - for wait for a fire request
            "ACK" in format ACK:DRONE_ID:STATE:REQUEST:X:Y:CURR_TASK:FIREREQ_ID    - for saying acknowledge

             not assigned here* "NEW" in format NEW:DRONE_ID:STATE:FIREREQUEST:X:Y:CURR_TASK:FIREREQ_ID  - for reassigning current task and state
         */
        // TODO: handle request to proceed with the state corresponding to when this event occurs
        switch(eventRequest)
        {
            case NEW_FIRE_REQUEST:
                // drones next event required for a successful state transition is NEW_FIRE_REQUEST
                System.out.println("\n[SD   ]  -   switch(eventRequest) == "+eventRequest+":    drone "+drone.getDroneId()+ " * state change " + drone.getState()+ " -> [IDLE] (wait context) *");
                drone.setState("[IDLE]");
                return "WAIT:" + request;
            case PERMISSION_TO_DROP:
                System.out.println("\n[SD   ]  -   switch(eventRequest) == "+eventRequest+":    drone "+drone.getDroneId()+ " * state change " + drone.getState()+ " -> [TRAVELING] *");
                drone.setState("[TRAVELING]");
                return "ACK:" + request;
            case PAYLOAD_DROPPED:
                // Send an ACK with a completed to allow to transition to next state
                System.out.println("\n[SD   ]  -   switch(eventRequest) == "+eventRequest+":    drone "+drone.getDroneId()+ " * state change " + drone.getState()+ " -> [DEPLOYING] *");
                drone.setState("[DEPLOYING]");
                
                // Send confirmation that this drone has completed its request asynchronously to the FireIncidentSubsystem
                sendPacket(fireSendSocket, FIRE_INCIDENT_SUBSYSTEM_PORT, drone.getCurrentTask()+ ":COMPLETED");

                return "ACK:" + request;
            case PAYLOAD_DEPLOY_FAILURE:
                drone.setState("[DEPLOY FAILURE]");
                return "ACK:" + request;
            case DEPLOY_FAILURE_ACKNOWLEDGED:
                // TODO :                 drone.setState("[DEPLOY FAILURE]");
                return "ACK:" + request;
            case RETURNED_TO_BASE:
                System.out.println("\n[SD   ]  -   switch(eventRequest) == "+eventRequest+":    drone "+drone.getDroneId()+ " * state change " + drone.getState()+ " -> [RETURNING] *");
                drone.setState("[RETURNING]");
//                return "ACK:" + request + ":COMPLETED";
                return "ACK:" + request;
            case REFILL_COMPLETE:
                // here, drone is sending info to scheduler, "I am able to refill now, should i proceed?"
                // this is the last state of answering  fire request , the drone
                FireRequest completedTask = drone.getCurrentTask();
                String responseToDrone = completedTask.toString();
                if (completedTask.isDefault())
                {
                    System.out.println("\n[SD   ]  -   switch(eventRequest) == "+eventRequest+":    drone "+drone.getDroneId()+ " * state change " + drone.getState()+ " -> [IDLE] *");
                    drone.setState("[IDLE]");
                    // if task is defualt meaning they are sending a fire request to scheduler with no data
                    System.out.println(" \n[SD   ]***************** SHOULD HIT WHEN DRONE IS DONE REFILLING ******************* handleDroneRequest handling no data");
                    System.out.println(responseToDrone + "\n\n\n");
                    // addResponse(responseToDrone);
                    drone.setCurrentTask(new FireRequest());
                }
                else
                {
                    System.out.println("\n[SD   ]  -   switch(eventRequest) == "+eventRequest+":    drone "+drone.getDroneId()+ " * state change " + drone.getState()+ " -> [REFILLING] *");
                    drone.setState("[REFILLING]");
                    System.out.println("\n[SD   ]***************** SHOULD hit when drone returns to base ******************* handleDroneRequest");
                    // add complete so scheduler passes to FIS the task is completed
                    System.out.println(responseToDrone+ ":COMPLETED \n\n\n");
                    drone.setCurrentTask(new FireRequest());
                }
                return "ACK:" + request;
            case DRONE_STUCK:
                return "ACK:" + request;
            case STUCK_RESOLVED:
                return "ACK:" + request;
            case STATUS:
                if (!drone.getState().equals("[TRAVELING]") && !drone.getCurrentTask().isDefault()) { drone.setState("[TRAVELING]"); }

                // Check if this drone has a pending assignment
                DroneAssignment match = null;
                for (DroneAssignment assignment : pendingAssignments) {
                    if (assignment.droneId == droneId) {
                        match = assignment;
                        break;
                    }
                }

                if (match != null) {
                    pendingAssignments.remove(match);
                    drone.setCurrentTask(match.request);

                    String droneRequest = "NEW:" + droneId + ":" + drone.getState() + ":NEW_FIRE_REQUEST:" +
                            drone.getX() + ":" + drone.getY() + ":" + match.request;

                    System.out.println("[SD->DSS] Sending assignment from queue to drone: " + droneRequest);
//                    sendPacket(droneSendSocket, DRONE_SUBSYSTEM_PORT, droneRequest);
                    return droneRequest;
                }

                System.out.println("\n[SD   ]  -   switch(eventRequest) == STATUS:    drone " + droneId + " * NO STATE CHANGE remains at  " + drone.getState() + "*");
                return "ACK:" + request;
            case CONTINUING:
                // Send an ack -- this drone is continuing on its old mission
                if (!drone.getState().equals("[TRAVELING]")) { drone.setState("[TRAVELING]"); }
                return "ACK:" + request;
            case RETURN_STATUS:
                System.out.println("\n[SD   ]  -   switch(eventRequest) == RETURN_STATUS:    drone " + droneId + " * is currently  " + drone.getState() + "*");
                drone.setState("[RETURNING]");
                return "ACK:" + request;
            default:
                System.out.println("\n[SD   ]  -   switch(eventRequest) == UNKNOWN:    drone "+drone.getDroneId()+ " * NO STATE CHANGE remains at  " + drone.getState()+ "*");
                return "ERROR: UNKNOWN drone request: " + request; // should never hit
        }
    }

    /**
     * Called from {@link ProcessPendingRequests} (SP) thread and Assigns a fire request to the most appropriate drone using {@link Scheduler#pendingAssignments}
     * queue to be received and handled in {@link Scheduler#handleDroneRequest} from the SD thread as a response to a drone Status request
     */
    private synchronized boolean assignFireRequest(FireRequest fireRequest) {

        System.out.println("\n[  SP  ]  -   ASSIGN FIRE REQUEST CALLED  -   " + fireRequest.toString());

        // FOLLOWING LOGIC IS FOR finding a drone that can or cant service this request
        int selectedDroneId = selectDrone(fireRequest);
        // TODO: split select drone into selectTravelDrones, selectIdleDrones, handleNoSelectedDrones for testing and readibility/code tracing

        System.out.println("\n[  SP  ]  -   selectedDroneId: " + selectedDroneId);

        // FOLLOWING LOGIC IS FOR no drones available, exiting function gracefully from this assignment attempt
        if (selectedDroneId == -1) {
            System.out.println("\n[  SP  ]    No available travel drones or idle drones to handle fire request:   -       " + fireRequest.toString());
            return false;
        }

        // FOLLOWING LOGIC IS FOR informing a drone they are now tasked
        DroneStatus selectedDrone = drones.get(selectedDroneId);
        System.out.println("\n[  SP  ]  -   selectedDrone for task:     " + selectedDrone.toString());

        // FOLLOWING LOGIC IS FOR preserving previous task for drone in case they are reassigned
        FireRequest previousTask = selectedDrone.getCurrentTask();
        selectedDrone.setCurrentTask(fireRequest);  // set the task to the new one so scheduler knows but do not set the drone state

        // FOLLOWING LOGIC IS checking if drone had previous default task and if not if that drones state is traveling        // Build request string in expected format: "DRONE_ID:STATE:REQUEST:X:Y:CURR_TASK"
        if ( !previousTask.isDefault() && selectedDrone.getState().equals("[TRAVELING]"))
        {
            // If drone had a previous task, droneRequest is constructed with "NEW" and uses the current x and y location values

            //String droneRequest = "NEW:" + selectedDroneId + ":"+ selectedDrone.getState()+ ":NEW_FIRE_REQUEST:" +
            //        selectedDrone.getX() + ":" + selectedDrone.getY() + ":" + fireRequest;

            //System.out.println("\n[ SP->DSS ]   Sending to DroneSubsystem change task:   -       " + droneRequest);
            //sendPacket(droneSendSocket, DRONE_SUBSYSTEM_PORT, droneRequest);

            System.out.println("[  SP  ]    Reassigning previous task:      " + previousTask);
            addRequest(previousTask); // put the old request back into the queue to preserve reassigned task
        }
        else {
            // This drone did not have a previous task, so droneRequest is constructed as a simple acknowledgment to begin the drone's activity
            //String droneRequest = "NEW:" + selectedDroneId + ":"+ selectedDrone.getState()+ ":NEW_FIRE_REQUEST:" +
            //        selectedDrone.getX() + ":" + selectedDrone.getY() + ":" + fireRequest;

            //System.out.println("\n[ SP->DSS ]       Sending to DroneSubsystem START task:   -       " + droneRequest);
            //sendPacket(droneSendSocket, DRONE_SUBSYSTEM_PORT, droneRequest);
        }
        pendingAssignments.add(new DroneAssignment(selectedDroneId, fireRequest));

        return true;
    }

    /**
     * Called from {@link Scheduler#findClosestDrone} function called from {@link Scheduler#selectDrone} from the SP thread
     * 
     * <p>Compares each request based on severity</p>
     * @param currentRequest   the drone is handling
     * @param newRequest       the drone may be tasked 
     * @return true if the new request is more or equal in FireRequest severity, false if it is less severe
     */
    boolean isNewRequestMoreSevere(FireRequest currentRequest, FireRequest newRequest) {
        Map<String, Integer> severityRank = Map.of(
                "Low", 1,
                "Moderate", 2,
                "High", 3
        );

        int currentSeverity = severityRank.getOrDefault(currentRequest.getSeverity(), 0);
        int newSeverity = severityRank.getOrDefault(newRequest.getSeverity(), 0);

        return newSeverity >= currentSeverity;
    }

    /**
     * Called from {@link Scheduler#assignFireRequest} function from the SP thread
     * <p>Removes a fire request from the {@link Scheduler#requestQueue} after it has been assigned to a drone</p>
     * @param fireRequest to be removed from the queue
     */
    private void removeRequest(FireRequest fireRequest) {
        synchronized (requestQueue) {
            System.out.println( "[  SP  ] requestQueue removing item:   -        " + fireRequest.toString() );

            requestQueue.remove(fireRequest);

            System.out.println( "[  SP  ] requestQueue post removal:    -        " + Arrays.toString(requestQueue.toArray()));
        }
    }

    /**
     * contructor calls {@link Scheduler#parseZoneFile} to populate {@link Scheduler#zoneMap} with zone data
     * <p>initializes various member collection objects and send/receive sockets</p>
     * <p>initializes and starts all scheduler threads</p>
     */
    public Scheduler() {
        this.drones = new HashMap<>();
        this.requestQueue = new LinkedList<>();
        this.reassignedDrones = new ArrayList<>();
//        this.currentState = new Idle();

        // Parse the zone file to populate zoneMap
        parseZoneFile("SYSC3303_project/src/zone_file.csv");
        // Print out the zones for debugging
        for (Zone zone : zoneMap.values()) {
            System.out.println("Parsed zone: " + zone);
        }

        try {
            fireSendSocket = new DatagramSocket();
            droneSendSocket = new DatagramSocket();
            fireReceiveSocket = new DatagramSocket(FIRE_TO_SCHEDULER_PORT);
            droneReceiveSocket = new DatagramSocket(DRONE_TO_SCHEDULER_PORT);
        } catch (SocketException e) {
            System.err.println(e);
        }

        // Create and start threads to listen to other subsystems
        Thread droneHandler = new listenToDrone();
        Thread fireHandler = new listenToFire();

        droneHandler.start();
        fireHandler.start();

        // Start the background thread that processes pending fire requests
        new Thread(new ProcessPendingRequests()).start();
    }

    /** sets the state of the scheduler
    @param newState next state the scheduler transitions to
     */
    public void setState(SchedulerState newState){
        System.out.println("* SCHEDULER STATE CHANGE * " + this.currentState.display() + " -> " + newState.display());
        this.currentState = newState;
    }

    /** creates a {@link DroneStatus} object with the passed drone ID and returns an acknowledgement to be passed to DroneSubsystem to indicate
     *  the drone is successfully added to the {@link Scheduler#drones} hashmap
     @param droneId of the drone that is created on the DroneSubsystem side
     */
    public String registerDrone(int droneId)
    {
        // drone is not initialized
        if (droneId!=-1)
        {
            this.drones.put(droneId, new DroneStatus(droneId));
            return "ACK";
        }
        else return "ERROR: drone initialization with id error " + droneId;
    }

    /**
     * selects an available drone to be tasked with the passed fireRequest and returns that drones ID. If no drone is availible given environment/system context
     * will return -1 if there are no traveling drones and there are no idle drones
     * <p>Called from {@link Scheduler#assignFireRequest} in the SP thread </p>
     * @param request is the {@link FireRequest} object the Scheduler is finding an availible drone to answer
     * @return drone id of selected drone (default is -1)
     */
    public int selectDrone(FireRequest request) {

        System.out.println("\n[  SP  ]  -   SELECT DRONE CALLED  -   " + request.toString());

        Zone targetZone = zoneMap.get(request.getZoneId());
        if (targetZone == null) {
            System.out.println("Error: No zone data found for zone ID " + request.getZoneId());
            return -1;
        }

        // First, check for traveling drones that are on the path
        for (Map.Entry<Integer, DroneStatus> entry : drones.entrySet()) {
            DroneStatus drone = entry.getValue();
            System.out.println("\n[  SP  ]  -   SELECT DRONE TRAVEL check state ID: " +drone.getDroneId() + " : " + drone.getState());

            if (drone.getState().contains("TRAVELING")) {
                // Here you would implement your own logic to decide if the drone is on the path.
                // For illustration, assume we have an isOnPath() method:

                // if return is -1, do not use that drone id
                int droneID = findClosestDrone(targetZone, request);
                if( droneID != -1 )
                {
                    return droneID;
                }
                else
                {
                    // send to drone continue traveling
                    System.out.println("\n[  SP  ]  -   drone cant be reassigned: " +drone.getDroneId() + " drone must be tasked to continue traveling here "+droneID);

                }
            }
        }

        // check all idle drones
        for (Map.Entry<Integer, DroneStatus> entry : drones.entrySet()) {
            DroneStatus drone = entry.getValue();
            System.out.println("\n[  SP  ]  -   SELECT DRONE IDLE  check availibility: " + drone.toString());

            if (drone.getState().contains("IDLE")) {
                if( drone.getCurrentTask().isDefault() )
                {
                    System.out.println("[  SP  ]  -   drone is available (current task is default)");
                    return drone.getDroneId();
                }
                else System.out.println("[  SP  ]  -   drone is not available as current drone is tasked out and scheduler waiting for drone acknowledgement");
            }
        }
        // Otherwise, return no drones available
        return -1;
    }

    /**
     * checks all drones in {@link Scheduler#drones} if they are able to be reassigned from their current task if
     * <li>they are traveling
     * <li>they have a less severity than the fire request passed in
     * <p>if these^ are true, returns the drone id of that drone, otherwise returns -1<p/>
     *
     * @param targetZone the target Zone of the new fire request to be compared with the drones current target zone
     * @param newRequest the new fire request that the scheduler is looking to reassign any drone
     * @return the drone ID of the valid drone to be reassigned, or -1 if none are available or conditions dont meet
     */
    public int findClosestDrone(Zone targetZone, FireRequest newRequest) {
        System.out.println("\n[  SP  ]  -   FIND CLOSEST DRONE CALLED  -   " + targetZone.toString());

        if (targetZone == null) {
            System.out.println("Error: Target zone not found for the requested zone ID.");
            return -1;
        }
        int bestDroneId = -1;
        double bestDistance = Double.MAX_VALUE;
        for (Map.Entry<Integer, DroneStatus> entry : drones.entrySet())
        {
            DroneStatus drone = entry.getValue();
            if (drone.getState().contains("TRAVELING"))
            {
                // check if this is for a request for a current zone being answered
                if ( drone.getCurrentTask().getZoneId() == targetZone.getZoneId() )
                {
                    System.out.println("\n[  SP  ]  -   DRONE "+drone.getDroneId()+" IS ALREADY MOVING TO THIS ZONE -      " + targetZone.toString());
                    continue;
                }
                // Only allow reassignment if new request is more severe or equal
                if (!isNewRequestMoreSevere(drone.getCurrentTask(), newRequest)) {
                    System.out.println("\n[  SP  ]  -   DRONE " + drone.getDroneId() + " is on a more severe task. Skipping reassignment.");
                    continue;
                }

                if ( willPassThrough( drone, targetZone ) )
                {
                    return drone.getDroneId();
                }
                else
                {
                    return -1;
                }
            }
        }
        return bestDroneId;
    }

    /**
     * checks if this drone will pass through the target zone
     * @param drone DroneStatus object with real time drone location data to check if the condition is true
     * @param newTargetZone immutable Zone object that contains coordinates to check if the conditions are true
     * @return true if the drone will pass through the target zone
     */
    public boolean willPassThrough(DroneStatus drone, Zone newTargetZone) {

        // Get the destination zone from the drone's current task
        Zone currDestinationZone = zoneMap.get(drone.getCurrentTask().getZoneId());

        if (currDestinationZone == null || newTargetZone == null) {
            return false; // Cannot determine without proper zone data
        }

        int droneX = drone.getX();
        int droneY = drone.getY();
        System.out.println("\n[ SF ] - DRONE " + drone.getDroneId() + " LOCATION (" + droneX + "," + droneY + ") " +
                "- COMPARED TO REQUEST ZONE: " + newTargetZone.toString() +
                " AND DRONE DESTINATION: " + currDestinationZone.toString());

//        return false;

        // TODO: UNCOMMENT THE FOLLOWING LOCATION COMPARISON CALCULATIONS ....
        // prevents reassigning a drone that has already arrived at its current destination.
        if (droneX >= currDestinationZone.getStartX() && droneX <= currDestinationZone.getEndX() &&
                droneY >= currDestinationZone.getStartY() && droneY <= currDestinationZone.getEndY())
        {
            System.out.println("\n[ SF ] - DRONE " + drone.getDroneId() + " is already in the CURRENT DESTINATION zone... invalid reassignment.");
            return false;
        }

        // if the drone is already in the new target zone then immediate reassignment is optimal and no further checking is required
        if (droneX >= newTargetZone.getStartX() && droneX <= newTargetZone.getEndX() &&
                droneY >= newTargetZone.getStartY() && droneY <= newTargetZone.getEndY())
        {
            System.out.println("\n[ SF ] - DRONE " + drone.getDroneId() + " is already in the NEW REQUEST zone... valid reassignment.");
            return true;
        }


        // Logic for if Drone is going to pass through request zone before destination zone

        // get center of destination zone
        int centerXCurrDestination = (currDestinationZone.getStartX() + currDestinationZone.getEndX()) / 2;
        int centerYCurrDestination = (currDestinationZone.getStartY() + currDestinationZone.getEndY()) / 2;

        // get center of request zone
        int centerXNewRequest = (newTargetZone.getStartX() + newTargetZone.getEndX()) / 2;
        int centerYNewRequest = (newTargetZone.getStartY() + newTargetZone.getEndY()) / 2;

        // check distances to compare which zone the drone is closer to
        double distanceToCurrDestination = Math.sqrt(Math.pow(droneX - centerXCurrDestination, 2) + Math.pow(droneY - centerYCurrDestination, 2));
        double distanceToNewRequest = Math.sqrt(Math.pow(droneX - centerXNewRequest, 2) + Math.pow(droneY - centerYNewRequest, 2));

        double droneSlope = (double) (currDestinationZone.getStartY() - droneY) / ( currDestinationZone.getStartX() - droneX );

        // if new request is closer than current request
        if (distanceToNewRequest <= distanceToCurrDestination) {
            System.out.println("\n[ SF ] - DRONE " + drone.getDroneId() + " not in either new or current zone... checking trajectory...");
            if ( (newTargetZone.getStartX() * droneSlope) >= newTargetZone.getStartY() || (newTargetZone.getStartX() * droneSlope) <= newTargetZone.getEndY() ) {
                return true;
            }
            else if ( (newTargetZone.getEndX() * droneSlope) >= newTargetZone.getStartY() || (newTargetZone.getEndX() * droneSlope) <= newTargetZone.getEndY() ) {
                return true;
            }
            else if ( (newTargetZone.getStartY() * droneSlope) >= newTargetZone.getStartX() || (newTargetZone.getStartY() * droneSlope) <= newTargetZone.getEndX() ) {
                return true;
            }
            else if ( (newTargetZone.getEndY() * droneSlope) >= newTargetZone.getStartX() || (newTargetZone.getEndY() * droneSlope) <= newTargetZone.getEndX() ) {
                return true;
            }
        } else System.out.println("\n[ SF ] - DRONE " + drone.getDroneId() + " not in either new or current zone... request is further than current assignment...");

        return false;

    }

    /**
     * adds a fire request to the {@link Scheduler#requestQueue}
     *
     * @param request the FireRequest to be added
     */
    public synchronized void addRequest(FireRequest request) {
        requestQueue.offer(request);
        System.out.println("From Scheduler - receiving request from fire incident: \n" + request + "\n");
    }

    /**
     * Receive a UDP packet.
     * @param socket which socket to receive on.
     * @return the message received.
     */
    private String receivePacket(DatagramSocket socket) {

        byte data[] = new byte[DATA_BUFFER_SIZE];
        DatagramPacket receivePacket = new DatagramPacket(data, data.length);
        try {
            // Block until a datagram is received via socket
            socket.receive(receivePacket);
        } catch(IOException e) {
            throw new RuntimeException(e);
        }

        int len = receivePacket.getLength();
//        System.out.println("\n x x x RECEIVE PACKET SCHEDULER x x x " + new String(data,0,len));

        // Return a String from the byte array
        return new String(data,0,len);
    }

    /**
     * Send a UDP packet.
     * @param socket which socket to send on.
     * @param port port number for the packet.
     * @param response message to send.
     */
    private void sendPacket(DatagramSocket socket, int port, String response) {
//        System.out.println("\n x x x SEND PACKET SCHEDULER x x x " + response);

        byte msg[] = response.getBytes();
        DatagramPacket packet;

        try {
            packet = new DatagramPacket(msg, msg.length, InetAddress.getLocalHost(), port);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }

        try {
            socket.send(packet);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * gracefull exit for all thread function loops. running set to false only in testing contexts for back to back instances of scheduler threads running */
    private void shutdown() {
        this.running = false;

        // close sockets if they are open
        if (fireReceiveSocket != null && !fireReceiveSocket.isClosed()) fireReceiveSocket.close();
        if (droneReceiveSocket != null && !droneReceiveSocket.isClosed()) droneReceiveSocket.close();
        if (fireSendSocket != null && !fireSendSocket.isClosed()) fireSendSocket.close();
        if (droneSendSocket != null && !droneSendSocket.isClosed()) droneSendSocket.close();
    }

    /**
     * Create a new Scheduler and start listening to a FireIncidentSubsystem and DroneSubsystem.
     * @param args CLI arguments.
     */
    public static void main(String[] args) {
        Scheduler s = new Scheduler();
    }
}
