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
 *           Tries to assign each pending request to an available drone.
 *           Considers drone availability, task severity, and possible rerouting logic.
 */
public class Scheduler {
    public static Map<Integer, Zone> zoneMap = new HashMap<>();
    private HashMap<Integer, DroneStatus> drones;
    private Queue<FireRequest> requestQueue;
    private SchedulerState currentState;
    public static final int DATA_BUFFER_SIZE = 256;
    public static final int FIRE_TO_SCHEDULER_PORT = 5000;
    public static final int DRONE_TO_SCHEDULER_PORT = 5001;
    public static final int FIRE_INCIDENT_SUBSYSTEM_PORT = 5002;
    public static final int DRONE_SUBSYSTEM_PORT = 5003;
    private DatagramSocket fireReceiveSocket, droneReceiveSocket, fireSendSocket, droneSendSocket;
    private final ConcurrentLinkedQueue<DroneAssignment> pendingAssignments = new ConcurrentLinkedQueue<>();
    private volatile boolean running = true;
    public HashMap<Integer, DroneStatus> getDrones() { return drones; }

    /**
     * constructor calls {@link Scheduler#parseZoneFile} to populate {@link Scheduler#zoneMap} with zone data
     * <p>initializes various member collection objects and send/receive sockets</p>
     * <p>initializes and starts all scheduler threads</p>
     */
    public Scheduler() {
        this.drones = new HashMap<>();
        this.requestQueue = new LinkedList<>();
        SchedulerEventLogger.getInstance().info("Scheduler", "MAIN", "Scheduler initialized.");
        parseZoneFile("SYSC3303_project/src/zone_file.csv");
        for (Zone zone : zoneMap.values()) {
            System.out.println("Parsed zone: " + zone);
            SchedulerEventLogger.getInstance().info("Scheduler", "MAIN", "Parsed zone: " + zone);
        }
        try {
            fireSendSocket = new DatagramSocket();
            droneSendSocket = new DatagramSocket();
            fireReceiveSocket = new DatagramSocket(FIRE_TO_SCHEDULER_PORT);
            droneReceiveSocket = new DatagramSocket(DRONE_TO_SCHEDULER_PORT);
        } catch (SocketException e) {
            System.err.println(e);
        }
        this.currentState = new Idle();
    }

    /** sets the state of the scheduler
     @param newState next state the scheduler transitions to
     */
    public synchronized void setState(SchedulerState newState){
        if (!this.currentState.display().equals(newState.display())) {
            System.out.println("* SCHEDULER STATE CHANGE * " + this.currentState.display() + " -> " + newState.display());
            this.currentState = newState;
        }
    }

    /** creates a {@link DroneStatus} object with the passed drone ID and returns an acknowledgement to be passed to DroneSubsystem to indicate
     *  the drone is successfully added to the {@link Scheduler#drones} hashmap
     @param droneId of the drone that is created on the DroneSubsystem side
     */
    public String registerDrone(int droneId)
    {
        if (droneId!=-1)
        {
            this.drones.put(droneId, new DroneStatus(droneId));
            return "ACK";
        }
        else return "ERROR: drone initialization with id error " + droneId;
    }

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
        public listenToFire() {
            SchedulerEventLogger.getInstance().info("Scheduler", "SF", "Thread initialized: listenToFire");
        }
        /**
         * SF Thread function to listen to packets from {@link FireIncidentSubsystem} and creates {@link FireRequest} objects as they arrive
         * <p>responds to FireIncidentSubsystem with acknowledgements and when the FireRequests are completed</p>
         */
        @Override
        public void run() {
            System.out.println("\n[   SF]  -   SCHEDULER IS LISTENING TO FIRE  -   ");

            while (running) {
                 // This request will be a new FireRequest to add
                String request = receivePacket(fireReceiveSocket);
                if (request == null) break;
                if (request.contains("SHUTDOWN")){
                    waitForDroneCompletion();
                    SchedulerEventLogger.getInstance().debug("Scheduler", "SF", "Received Shutdown Message From Fire: " + request);
                    shutdown();
                    break;
                }
                System.out.print("\n[ SF<-F ]  -   SCHEDULER RECEIVED FROM FIRE: "+request+ "  -   ");
                SchedulerEventLogger.getInstance().debug("Scheduler", "SF", "Received fire message: " + request);
                Queue<FireRequest> fireRequests = makeFireRequests(request);
                for( FireRequest fr : fireRequests ) {
                    addRequest(fr);
                    SchedulerEventLogger.getInstance().info("Scheduler", "SF", "Added FireRequest: " + fr);
                }
                sendPacket(fireSendSocket, FIRE_INCIDENT_SUBSYSTEM_PORT, "SCHEDULER:ACKNOWLEDGED");
                System.out.println("\n[ SF->F ]  -   * SENT TO FIRE  -   SCHEDULER:ACKNOWLEDGED");
                SchedulerEventLogger.getInstance().debug("Scheduler", "SF", "Sending to FireIncident Subsystem Scheduler Acknowledged Fire Request: " + request);
            }
        }
    }

    /**
     * SD Thread to listen to packets from {@link DroneSubsystem} and calls {@link Scheduler#handleDroneRequest} to handle the requests
     * <p>{@link Scheduler#handleDroneRequest} returns a response string to send back to DroneSubsystem</p>
     */
    private class listenToDrone extends Thread {
        public listenToDrone() {
            SchedulerEventLogger.getInstance().info("Scheduler", "SD", "Listening to DroneSubsystem...");
        }
        /**
         * SD Thread function to listen to packets from {@link DroneSubsystem} and calls {@link Scheduler#handleDroneRequest} to handle the requests
         * <p>{@link Scheduler#handleDroneRequest} returns a response string to send back to DroneSubsystem</p>
         */
        @Override
        public void run() {
            System.out.println("\n[SD  ]  -   SCHEDULER IS LISTENING TO DRONE  -   ");
            while (running) {
                // Receive packet from the DroneSubsystem
                String request = receivePacket(droneReceiveSocket);
                if (request == null) break;

                // get drone status -
                System.out.println("\n[SD<-D ]  -   SCHEDULER RECEIVED FROM DRONE: "+request+ "  -   ");
                SchedulerEventLogger.getInstance().debug("Scheduler", "SD", "Received drone message: " + request);

                // Parse the packet (assuming we are not actually storing physical Drones anymore, and instead are only storing crucial data for each drone):
                setState(new ProcessData(new TaskDrone()));
                String response = handleDroneRequest(request);
                setState(new Idle());

                System.out.println("\n[SD->D ]  -   SCHEDULER RESPONSE TO DRONE: "+response+ "  -   ");
                SchedulerEventLogger.getInstance().debug("Scheduler", "SD", "Responding to drone with: " + response);

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
        public ProcessPendingRequests() {
            SchedulerEventLogger.getInstance().info("Scheduler", "SP", "Processing pending fire requests...");
        }
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
                        setState(new ProcessData(new TaskDrone()));
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
                        setState(new Idle());
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
     * Parses the Drones request and returns:
     * <li>an ACK header for permission for that drone to proceed with its next state transition (or to continue waiting for a fire request)</li>
     * <li>a NEW header with an attached fire request when the drone is to be assigned OR reassigned a new fire request </li>
     * <p>response format is:<p>
     *     RESPONSE_HEADER:REQUEST<p>
     *     the same as...<p>
     *     RESPONSE_HEADER:DRONE_ID:STATE:REQUEST_BODY:X_POS:Y_POS:CURR)FIREREQUEST
     @return String value of entire formatted response to send to Drone in {@link listenToDrone}
     */
    String handleDroneRequest(String request)
    {
        if (request.startsWith("INIT")) {
            // Extract the drone ID if needed; here we assume items[0] is the drone ID in the registration request.
            int droneId = Integer.parseInt(request.split(":")[0]);
            System.out.println("[SD   ] Registering drone " + droneId);
            return "ACK:" + droneId + ":REGISTERED";
        }
        String[] items = request.split(":");
        FireRequest currTask = new FireRequest(items[5]);
        int droneId = -1;
        try {
            droneId = Integer.parseInt(items[0]);
        } catch (NumberFormatException e) {
            return "ERROR: Invalid drone ID";
        }
        DroneStatus drone = this.drones.get(droneId);
        if (drone == null) return registerDrone(droneId) + ":" + request;
        String droneState = items[1];
        DroneEvent eventRequest = null;
        try {
            eventRequest = DroneEvent.valueOfEvent(items[2]); // Convert string to enum
        } catch (IllegalArgumentException e) {
            System.out.println("ERROR: Unknown drone event: " + items[2]);
        }
        int x,y = -1;
        try {
            x = Integer.parseInt(items[3]);
            y = Integer.parseInt(items[4]);
        } catch (NumberFormatException e) {
            return "ERROR: Invalid location value: " + items[3] + "," + items[4];
        }
        drone.updateBattery(x, y);
        drone.setLocation(x, y);
        if (drone.getState().contains("TRAVELING") || drone.getState().contains("RETURNING")) {
            System.out.println("\nDrone " + droneId + " battery level: " + drone.getBatteryLevel());
        }
        switch(eventRequest)
        {
            case NEW_FIRE_REQUEST:
                SchedulerEventLogger.getInstance().info("Scheduler", "SD", "Drone " + droneId + " transitioning to [IDLE] on task " + currTask);
                drone.setState("[IDLE]");
                return "WAIT:" + request;
            case PERMISSION_TO_DROP:
                SchedulerEventLogger.getInstance().info("Scheduler", "SD", "Drone " + droneId + " transitioning to [TRAVELING] on task " + currTask);
                drone.setState("[TRAVELING]");
                return "ACK:" + request;
            case PAYLOAD_DROPPED:
                SchedulerEventLogger.getInstance().info("Scheduler", "SD", "Drone " + droneId + " transitioning to [DEPLOYING] on task " + currTask);
                drone.setState("[DEPLOYING]");
                SchedulerEventLogger.getInstance().info("Scheduler", "SD", "Notifying FireIncidentSubsystem of task completion: " + currTask);
                sendPacket(fireSendSocket, FIRE_INCIDENT_SUBSYSTEM_PORT, drone.getCurrentTask()+ ":COMPLETED");
                return "ACK:" + request;
            case PAYLOAD_DEPLOY_FAILURE:
                drone.setState("[OFFLINE]");
                if( !currTask.isDefault() && droneState.equals("[ACTIVE][DEPLOYING]") )
                {
                    SchedulerEventLogger.getInstance().info("Scheduler", "SD", "Task is being reassigned: " + currTask);
                    addRequest(currTask);
                    SchedulerEventLogger.getInstance().error("Scheduler", "SD", "Drone " + droneId + " failed to deploy payload. Transitioning to [OFFLINE] on task " + currTask);
                }
                drone.setCurrentTask(new FireRequest());
                return "ACK:" + request;
            case DEPLOY_FAILURE_ACKNOWLEDGED:
                return "ACK:" + request;
            case RETURNED_TO_BASE:
                SchedulerEventLogger.getInstance().info("Scheduler", "SD", "Drone " + droneId + " transitioning to [RETURNING] on task " + currTask);
                if (!drone.getState().equals("[OFFLINE]")) {drone.setState("[RETURNING]");}
                return "ACK:" + request;
            case REFILL_COMPLETE:
                FireRequest completedTask = drone.getCurrentTask();
                if (completedTask.isDefault())
                {
                    SchedulerEventLogger.getInstance().info("Scheduler", "SD", "Drone " + droneId + " Has completed task and refill. Transitioning to [IDLE] on NO task.");
                    drone.setState("[IDLE]");
                    drone.setCurrentTask(new FireRequest());
                }
                else
                {
                    SchedulerEventLogger.getInstance().info("Scheduler", "SD", "Drone " + droneId + " Returned to base. Transitioning to [REFILLING] on task " + currTask);
                    drone.setState("[REFILLING]");
                    drone.setCurrentTask(new FireRequest());
                }
                drone.chargeBattery();
                return "ACK:" + request;
            case DRONE_STUCK:
                drone.setState("[OFFLINE]");
                if( !currTask.isDefault() && droneState.equals("[ACTIVE][TRAVELING]") )
                {
                    SchedulerEventLogger.getInstance().error("Scheduler", "SD", "Drone " + droneId + " is stuck. Transitioning to [OFFLINE] on task " + currTask);
                    SchedulerEventLogger.getInstance().info("Scheduler", "SD", "Task is being reassigned " + currTask);
                    addRequest(currTask);
                }
                drone.setCurrentTask(new FireRequest());
                return "ACK:" + request;
            case STUCK_RESOLVED:
                return "ACK:" + request;
            case STATUS:
                if (!drone.getState().equals("[TRAVELING]") && !drone.getCurrentTask().isDefault()) { drone.setState("[TRAVELING]"); }
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
                    String droneRequest = "NEW:" + droneId + ":" + drone.getState() + ":NEW_FIRE_REQUEST:" + drone.getX() + ":" + drone.getY() + ":" + match.request;
                    SchedulerEventLogger.getInstance().info("Scheduler", "SD", "Drone " + droneId + " is being assigned a task " + match.request);
                    return droneRequest;
                }
                return "ACK:" + request;
            case CONTINUING:
                if (!drone.getState().equals("[TRAVELING]") && !drone.getState().equals("[OFFLINE]")) { drone.setState("[TRAVELING]"); }
                return "ACK:" + request;
            case RETURN_STATUS:
                SchedulerEventLogger.getInstance().info("Scheduler", "SD", "Drone " + droneId + " transitioning to [RETURNING] on task " + currTask);
                if (!drone.getState().equals("[OFFLINE]")) {drone.setState("[RETURNING]");}
                return "ACK:" + request;
            case null:
                SchedulerEventLogger.getInstance().error("Scheduler", "SD", "Drone " + droneId + " sent nonsense packet. Requesting resend " + currTask);
                return "RESEND:" + request;
            default:
                SchedulerEventLogger.getInstance().error("Scheduler", "SD", "Drone " + droneId + " sent nonsense packet. Requesting resend " + currTask);
                return "RESEND:" + request;
        }
    }

    /**
     * Called from {@link ProcessPendingRequests} (SP) thread and Assigns a fire request to the most appropriate drone using {@link Scheduler#pendingAssignments}
     * queue to be received and handled in {@link Scheduler#handleDroneRequest} from the SD thread as a response to a drone Status request
     */
    private synchronized boolean assignFireRequest(FireRequest fireRequest) {

        System.out.println("\n[  SP  ]  -   ASSIGN FIRE REQUEST CALLED  -   " + fireRequest.toString());
        SchedulerEventLogger.getInstance().info("Scheduler", "SP", "Attempting assignment: " + fireRequest);
        // FOLLOWING LOGIC IS FOR finding a drone that can or cant service this request
        int selectedDroneId = selectDrone(fireRequest);
        // FOLLOWING LOGIC IS FOR no drones available, exiting function gracefully from this assignment attempt
        if (selectedDroneId == -1) {
            System.out.println("\n[  SP  ]  -   No available travel drones or idle drones to handle fire request:   -       " + fireRequest.toString());
            SchedulerEventLogger.getInstance().warn("Scheduler", "SP", "No available drone for request: " + fireRequest);
            return false;
        }
        SchedulerEventLogger.getInstance().info("Scheduler", "SP", "Assigned to drone " + selectedDroneId + " this fire request: " + fireRequest);
        // FOLLOWING LOGIC IS FOR informing a drone they are now tasked
        DroneStatus selectedDrone = drones.get(selectedDroneId);
        System.out.println("\n[  SP  ]  -   selectedDrone for task:     " + selectedDrone.toString());
        // FOLLOWING LOGIC IS FOR preserving previous task for drone in case they are reassigned
        FireRequest previousTask = selectedDrone.getCurrentTask();
        selectedDrone.setCurrentTask(fireRequest);  // set the task to the new one so scheduler knows but do not set the drone state
        // FOLLOWING LOGIC IS checking if drone had previous default task and if not if that drones state is traveling        // Build request string in expected format: "DRONE_ID:STATE:REQUEST:X:Y:CURR_TASK"
        if ( !previousTask.isDefault() && selectedDrone.getState().equals("[TRAVELING]"))
        {
            System.out.println("[  SP  ]    Reassigning previous task:      " + previousTask);
            SchedulerEventLogger.getInstance().info("Scheduler", "SP", "Reassigning previous task: " + previousTask);
            addRequest(previousTask); // put the old request back into the queue to preserve reassigned task
        }
        pendingAssignments.add(new DroneAssignment(selectedDroneId, fireRequest));
        return true;
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
        // First, check for TRAVELING drones that are on the path
        for (Map.Entry<Integer, DroneStatus> entry : drones.entrySet()) {
            DroneStatus drone = entry.getValue();
            System.out.println("\n[  SP  ]  -   SELECT DRONE TRAVEL check state ID: " +drone.getDroneId() + " : " + drone.getState());
            if (drone.getState().contains("TRAVELING")) {
                int droneID = findClosestDrone(targetZone, request);
                if( droneID != -1 )
                {
                    return droneID;
                }
                else
                {
                    System.out.println("\n[  SP  ]  -   drone cant be reassigned: " +drone.getDroneId() + " drone must be tasked to continue traveling here "+droneID);
                }
            }
        }
        // Next, check all IDLE drones
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
        for (Map.Entry<Integer, DroneStatus> entry : drones.entrySet())
        {
            DroneStatus drone = entry.getValue();
            if (drone.getState().contains("TRAVELING"))
            {
                if ( drone.getCurrentTask().getZoneId() == targetZone.getZoneId() )
                {
                    System.out.println("\n[  SP  ]  -   DRONE "+drone.getDroneId()+" IS ALREADY MOVING TO THIS ZONE -      " + targetZone.toString());
                    continue;
                }
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
     * checks if this drone will pass through the target zone
     * @param drone DroneStatus object with real time drone location data to check if the condition is true
     * @param newTargetZone immutable Zone object that contains coordinates to check if the conditions are true
     * @return true if the drone will pass through the target zone
     */
    public boolean willPassThrough(DroneStatus drone, Zone newTargetZone) {
        Zone currDestinationZone = zoneMap.get(drone.getCurrentTask().getZoneId());
        if (currDestinationZone == null || newTargetZone == null) {
            return false;
        }
        int droneX = drone.getX();
        int droneY = drone.getY();
        System.out.println("\n[ SF ] - DRONE " + drone.getDroneId() + " LOCATION (" + droneX + "," + droneY + ") " +
                "- COMPARED TO REQUEST ZONE: " + newTargetZone.toString() +
                " AND DRONE DESTINATION: " + currDestinationZone.toString());
        if (droneX >= currDestinationZone.getStartX() && droneX <= currDestinationZone.getEndX() &&
                droneY >= currDestinationZone.getStartY() && droneY <= currDestinationZone.getEndY())
        {
            System.out.println("\n[ SF ] - DRONE " + drone.getDroneId() + " is already in the CURRENT DESTINATION zone... invalid reassignment.");
            return false;
        }
        if (droneX >= newTargetZone.getStartX() && droneX <= newTargetZone.getEndX() &&
                droneY >= newTargetZone.getStartY() && droneY <= newTargetZone.getEndY())
        {
            System.out.println("\n[ SF ] - DRONE " + drone.getDroneId() + " is already in the NEW REQUEST zone... valid reassignment.");
            return true;
        }
        int centerXCurrDestination = (currDestinationZone.getStartX() + currDestinationZone.getEndX()) / 2;
        int centerYCurrDestination = (currDestinationZone.getStartY() + currDestinationZone.getEndY()) / 2;
        int centerXNewRequest = (newTargetZone.getStartX() + newTargetZone.getEndX()) / 2;
        int centerYNewRequest = (newTargetZone.getStartY() + newTargetZone.getEndY()) / 2;
        double distanceToCurrDestination = Math.sqrt(Math.pow(droneX - centerXCurrDestination, 2) + Math.pow(droneY - centerYCurrDestination, 2));
        double distanceToNewRequest = Math.sqrt(Math.pow(droneX - centerXNewRequest, 2) + Math.pow(droneY - centerYNewRequest, 2));
        double droneSlope = (double) (currDestinationZone.getStartY() - droneY) / ( currDestinationZone.getStartX() - droneX );
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
        setState(new ProcessData(new ReceiveData()));
        byte data[] = new byte[DATA_BUFFER_SIZE];
        DatagramPacket receivePacket = new DatagramPacket(data, data.length);
        try {
            socket.receive(receivePacket);
        } catch (SocketException e) {
            System.out.println("[SHUTDOWN] Socket closed, terminating listener.");
            return null;
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
        int len = receivePacket.getLength();
        setState(new Idle());
        return new String(data,0,len);
    }

    /**
     * Send a UDP packet.
     * @param socket which socket to send on.
     * @param port port number for the packet.
     * @param response message to send.
     */
    private void sendPacket(DatagramSocket socket, int port, String response) {
        setState(new ProcessData(new SendData()));
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
        setState(new Idle());
    }

    /**
     * Creates a new thread to listen for fire incident messages.
     * @return a new {@code Thread} that handles fire input.
     */
    public Thread createFireThread() {
        return new listenToFire();
    }

    /**
     * Creates a new thread to listen for drone status updates.
     * @return a new {@code Thread} that handles drone communication.
     */
    public Thread createDroneThread() {
        return new listenToDrone();
    }

    /**
     * Creates a new thread that processes pending fire requests and assigns them to drones.
     * @return a new {@code Thread} that handles request processing.
     */
    public Thread createProcessingThread() {
        return new Thread(new ProcessPendingRequests());
    }

    /**
     * Creates a thread responsible for pushing UI updates periodically.
     * @return a new {@code Thread} for sending UI updates.
     */
    private Thread createUIPusher()
    {
        return new UIUpdatePusher();
    }

    /**
     * Waits until all drones have completed their current tasks or gone offline.
     * <p>This method blocks the calling thread until no drones are active.</p>
     */
    public void waitForDroneCompletion() {
        System.out.println("[SCHEDULER] Waiting for all drones to finish...");
        while (true) {
            boolean allFinished = true;
            synchronized (drones) {
                for (DroneStatus drone : drones.values()) {
                    String state = drone.getState();
                    if (!state.contains("IDLE") && !state.contains("OFFLINE")) {
                        allFinished = false;
                        break;
                    }
                }
            }
            if (allFinished) {
                System.out.println("[SCHEDULER] All drones have completed their tasks.");
                break;
            }
            try {
                Thread.sleep(500); // Wait before checking again
            } catch (InterruptedException e) {
                System.out.println("[SCHEDULER] Interrupted while waiting for drones.");
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * gracefully exit for all thread function loops. running set to false only in testing contexts for back to back instances of scheduler threads running
     */
    private void shutdown() {
        this.running = false;
        sendPacket( droneSendSocket, DRONE_SUBSYSTEM_PORT, "SHUTDOWN" );
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
        Scheduler scheduler = new Scheduler();

        Thread fire = scheduler.createFireThread();
        Thread drone = scheduler.createDroneThread();
        Thread process = scheduler.createProcessingThread();
        Thread uiPusher = scheduler.createUIPusher();

        fire.start();
        drone.start();
        process.start();
        uiPusher.start();

        SchedulerEventLogger.getInstance().info("Main", "MAIN", "Scheduler is now online");

        try {
            fire.join();
            drone.join();
            process.join();
            uiPusher.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        SchedulerEventLogger.getInstance().info("Main", "MAIN", "All threads have finished. Log file ready for analysis.");
    }

    /**
     * A thread that periodically pushes updates about drones, fires, and faults
     * to the UI receiver via UDP packets.
     *
     * <p>This thread collects the current status of all drones, active fire requests,
     * and faults (e.g., offline drones), then serializes and sends the data to a
     * predefined UI receiver port. The updates are sent every 2 seconds.</p>
     */
    private class UIUpdatePusher extends Thread {
        private final int UI_RECEIVER_PORT = 6000;

        /**
         * Continuously gathers data from the system and sends it to the UI application.
         *
         * <p>Specifically, it gathers:
         * <ul>
         *   <li>Drone statuses: position and task severity</li>
         *   <li>Fire events: both currently assigned and pending</li>
         *   <li>Faults: currently offline drones</li>
         * </ul>
         * These are serialized into a {@code DroneUIApp.UIUpdate} object and sent via UDP.</p>
         */
        @Override
        public void run() {
            System.out.println("[ UI ]  -   UIUpdatePusher thread started  -   ");
            try (DatagramSocket socket = new DatagramSocket()) {
                while (running) {
                    Thread.sleep(2000);  // send updates every 2000 ms
                    List<DroneUIApp.DroneStatus> uiDrones = new ArrayList<>();
                    List<DroneUIApp.Fire> uiFires = new ArrayList<>();
                    List<DroneUIApp.Fault> uiFaults = new ArrayList<>();

                    // Collect drone statuses
                    for (DroneStatus d : drones.values()) {
                        String severity = d.getCurrentTask().getSeverity();
                        if (d.getCurrentTask().isDefault()) severity = "";
                        uiDrones.add(new DroneUIApp.DroneStatus(d.getDroneId(), d.getX(), d.getY(), severity));
                        if(!d.getCurrentTask().isDefault()) {
                            uiFires.add(new DroneUIApp.Fire(d.getCurrentTask().getId(), d.getCurrentTask().getSeverity(), d.getCurrentTask().getZoneId()));
                        }
                    }
                    // Collect active fires (queue + assigned)
                    synchronized (requestQueue) {
                        for (FireRequest fr : requestQueue) {
                            uiFires.add(new DroneUIApp.Fire(fr.getId(), fr.getSeverity(), fr.getZoneId()));
                        }
                    }
                    // Collect offline drones as faults
                    for (DroneStatus d : drones.values()) {
                        if (d.getState().equals("[OFFLINE]")) {
                            uiFaults.add(new DroneUIApp.Fault(d.getDroneId(), "DRONE OFFLINE"));
                        }
                    }
                    DroneUIApp.UIUpdate update = new DroneUIApp.UIUpdate(uiDrones, uiFires, uiFaults);
                    String serialized = serializeUpdate(update);
                    byte[] data = serialized.getBytes();
                    DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getLocalHost(), UI_RECEIVER_PORT);
                    socket.send(packet);
                }
            } catch (Exception e) {
                System.err.println("UIUpdatePusher error: " + e.getMessage());
            }
        }

        /**
         * Serializes a {@link DroneUIApp.UIUpdate} object into a custom string format for transmission.
         *
         * <p>The serialized string includes the following sections:
         * <ul>
         *   <li><b>DRONES</b>: A list of drone statuses in the format {@code (id,x,y,fireStatus)}</li>
         *   <li><b>FIRES</b>: A list of active fires in the format {@code (fireId,severity,zoneId)}</li>
         *   <li><b>FAULTS</b>: A list of drone faults in the format {@code (droneId,faultDescription)}</li>
         * </ul>
         * Each section is enclosed in brackets and separated by semicolons.</p>
         *
         * @param update The {@code UIUpdate} object containing the latest statuses, fires, and faults.
         * @return A string representing the serialized update data.
         */
        private String serializeUpdate(DroneUIApp.UIUpdate update) {
            StringBuilder sb = new StringBuilder();

            sb.append("DRONES=[");
            for (DroneUIApp.DroneStatus d : update.droneStatuses) {
                sb.append(String.format("(%d,%d,%d,%s);", d.droneId, d.x, d.y, d.fireStatus));
            }
            sb.append("];");
            sb.append("FIRES=[");
            for (DroneUIApp.Fire f : update.activeFires) {
                sb.append(String.format("(%s,%s,%d);", f.fireId, f.severity, f.zoneId));
            }
            sb.append("];");
            sb.append("FAULTS=[");
            for (DroneUIApp.Fault fault : update.faults) {
                sb.append(String.format("(%d,%s);", fault.droneId, fault.faultDescription));
            }
            sb.append("];");

            return sb.toString();
        }
    }
}
