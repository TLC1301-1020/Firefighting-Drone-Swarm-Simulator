import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * {@code Scheduler} coordinates fire requests between the fire incident and drone subsystem threads.
 * Scheduler synchronizes request handling, ensuring proper thread-safe task distribution and response management
 */
public class Scheduler {
    public static Map<Integer, Zone> zoneMap = new HashMap<>();

    /**
     * Thread safe Queue to store multiple responses from FireIncidentSubsystem */
    private final Queue<String> responseQueue = new ConcurrentLinkedQueue<>();

    private HashMap<Integer, DroneStatus> drones;
    private List<Integer> reassignedDrones;

    private Queue<FireRequest> requestQueue;
    private SchedulerState currentState;

    public static final int DATA_BUFFER_SIZE = 256;
    public static final int FIRE_TO_SCHEDULER_PORT = 5000;
    public static final int DRONE_TO_SCHEDULER_PORT = 5001;
    public static final int FIRE_INCIDENT_SUBSYSTEM_PORT = 5002;
    public static final int DRONE_SUBSYSTEM_PORT = 5003;
    private DatagramSocket fireReceiveSocket, droneReceiveSocket, fireSendSocket, droneSendSocket;

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
     * Thread to listen to FireIncidentSubsystem.
     */
    private class listenToFire extends Thread {
        @Override
        public void run() {
            System.out.println("\n[   SF]  -   SCHEDULER IS LISTENING TO FIRE  -   ");

            while (true) {
                System.out.println("\n[   SF]  -   SCHEDULER IS WAITING FOR MESSAGE FROM FIRE  -   ");

                // This request will be a new FireRequest to add, or just a data request for an available Drone response
                String request = receivePacket(fireReceiveSocket);

                System.out.print("\n[ SF<-F ]  -   SCHEDULER RECEIVED FROM FIRE: "+request+ "  -   ");

                // This is a data request, and we will wait via takeResponse() until an available Drone response is ready to send back
                if (request.contains("FIRE_DATA_REQUEST")) {
                    String update = takeResponse();
                    System.out.println("\n[ SF->F ]  -   SCHEDULER WILL NOW SEND TO FIRE  -   " + update);
                    sendPacket(fireSendSocket, FIRE_INCIDENT_SUBSYSTEM_PORT, update);
                    System.out.println("\n[ SF->F ]  -   * SENT TO FIRE  -   " + update);
                }

                // Otherwise it's a new FireRequest. Add it, and send back an acknowledgement
                else {
                    System.out.println("request is new fire request - sending through sendSocket + port: " + FIRE_INCIDENT_SUBSYSTEM_PORT);

                    FireRequest fireRequest = new FireRequest(request);
                    addRequest(fireRequest);
                    System.out.println("\n[ SF->F ]  -   SCHEDULER WILL NOW SEND TO FIRE  -   SCHEDULER:ACKNOWLEDGED");
                    sendPacket(fireSendSocket, FIRE_INCIDENT_SUBSYSTEM_PORT, "SCHEDULER:ACKNOWLEDGED");
                    System.out.println("\n[ SF->F ]  -   * SENT TO FIRE  -   SCHEDULER:ACKNOWLEDGED");
                }
            }
        }
    }

    /**
     * Thread to listen to DroneSubsystem.
     */
    private class listenToDrone extends Thread {
        @Override
        public void run() {
            System.out.println("\n[SD  ]  -   SCHEDULER IS LISTENING TO DRONE  -   ");
            while (true) {
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

    // assigning fire requests
    private class ProcessPendingRequests implements Runnable {
        @Override
        public void run() {
            System.out.println("\n[  SP  ]  -   SCHEDULER IS CHECKING LISTENING TO REQUEST QUEUE  -   ");
            while (true) {
                synchronized(requestQueue) {
                    if (!requestQueue.isEmpty()) {
                        FireRequest req = requestQueue.peek();

                        System.out.println("\n[  SP  ]  -   SCHEDULER REQUEST QUEUE REMOVED  -       " + req.toString());

                        if ( assignFireRequest(req) )
                        {
                            System.out.println("\n[  SP  ]  -   SCHEDULER SUCCESSFULLY TASKED A DRONE TO ANSWER REQUEST  -       " + req.toString());
                        }
                        else
                        {
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
     * response format is:<p>
     *     RESPONSE_HEADER:REQUEST<p>
     *     or<p>
     *     RESPONSE_HEADER:DRONE_ID:STATE:REQUEST_BODY:X_POS:Y_POS
     @return String value of entire formatted respnse to send to Drone
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

        // check format     -   in expected format "DRONE_ID:STATE:REQUEST:X:Y:CURR_TASK"
        String[] items = request.split(":");
        if (items.length != 6) return "ERROR: Invalid request format:" + request;
        FireRequest currTask = new FireRequest(items[5]);

        // get drone id     -   in expected format "DRONE_ID:STATE:REQUEST:X:Y:CURR_TASK"
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

        // get current state of this drone     -   in expected format "DRONE_ID:STATE:REQUEST:X:Y:CURR_TASK"
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
            "STATUS"   DRONE_ID:<STATE>:STATUS:X:Y:CURR_TASK  -> when there is a status update
                       DRONE_ID:<STATE>:REQUEST:X:Y:CURR_TASK  -> when there is all other requests
         */

        /*  response types
            "WAIT" in format WAIT:DRONE_ID:STATE:REQUEST:X:Y:CURR_TASK    - for wait for a fire request
            "ACK" in format ACK:DRONE_ID:STATE:REQUEST:X:Y:CURR_TASK    - for saying acknowledge

             not assigned here* "NEW" in format NEW:DRONE_ID:STATE:FIREREQUEST:X:Y:CURR_TASK  - for reassigning current task and state
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
//                return "ACK:" + request + ":COMPLETED";
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
                    addResponse(responseToDrone);
                    drone.setCurrentTask(new FireRequest());
                }
                else
                {
                    System.out.println("\n[SD   ]  -   switch(eventRequest) == "+eventRequest+":    drone "+drone.getDroneId()+ " * state change " + drone.getState()+ " -> [REFILLING] *");
                    drone.setState("[REFILLING]");
                    System.out.println("\n[SD   ]***************** SHOULD hit when drone returns to base ******************* handleDroneRequest");
                    // add complete so scheduler passes to FIS the task is completed
                    System.out.println(responseToDrone+ ":COMPLETED \n\n\n");
                    addResponse(responseToDrone+ ":COMPLETED");
                    drone.setCurrentTask(new FireRequest());
                }
                return "ACK:" + request;
            case DRONE_STUCK:
                return "ACK:" + request;
            case STUCK_RESOLVED:
                return "ACK:" + request;
            case STATUS:
                // drone is sending location update while traveling to fire zone
                if (!drone.getState().equals("[TRAVELING]")) { drone.setState("[TRAVELING]"); }
                System.out.println("\n[SD   ]  -   switch(eventRequest) == "+eventRequest+":    drone "+drone.getDroneId()+ " * NO STATE CHANGE remains at  " + drone.getState()+ "*");

                return "ACK:" + request;
            case CONTINUING:
                // Send an ack -- this drone is continuing on its old mission
                if (!drone.getState().equals("[TRAVELING]")) { drone.setState("[TRAVELING]"); }
                return "ACK:" + request;
            default:
                System.out.println("\n[SD   ]  -   switch(eventRequest) == UNKNOWN:    drone "+drone.getDroneId()+ " * NO STATE CHANGE remains at  " + drone.getState()+ "*");
                return "ERROR: UNKNOWN drone request: " + request; // should never hit
        }
    }

    /**
     * Assigns a fire request to the most appropriate drone.
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

        // TODO: Instead of removing this request, need to take into account severity, and possibly
        // TODO: throw it back at the start of the queue to be assigned to different drones
        removeRequest(fireRequest);

        // FOLLOWING LOGIC IS checking if drone had previous default task and if not if that drones state is traveling        // Build request string in expected format: "DRONE_ID:STATE:REQUEST:X:Y:CURR_TASK"
        if ( !previousTask.isDefault() && selectedDrone.getState().equals("[TRAVELING]"))
        {
            // If drone had a previous task, droneRequest is constructed with "NEW" and uses the current x and y location values
            String droneRequest = "NEW:" + selectedDroneId + ":"+ selectedDrone.getState()+ ":NEW_FIRE_REQUEST:" +
                    selectedDrone.getX() + ":" + selectedDrone.getY() + ":" + fireRequest;

            System.out.println("\n[ SP->DSS ]   Sending to DroneSubsystem change task:   -       " + droneRequest);
            sendPacket(droneSendSocket, DRONE_SUBSYSTEM_PORT, droneRequest);

            System.out.println("[  SP  ]    Reassigning previous task:      " + previousTask);
            addRequest(previousTask); // put the old request back into the queue to preserve reassigned task
        }
        else {
            // This drone did not have a previous task, so droneRequest is constructed as a simple acknowledgment to begin the drone's activity
            String droneRequest = "NEW:" + selectedDroneId + ":"+ selectedDrone.getState()+ ":NEW_FIRE_REQUEST:" +
                    selectedDrone.getX() + ":" + selectedDrone.getY() + ":" + fireRequest;

            System.out.println("\n[ SP->DSS ]       Sending to DroneSubsystem START task:   -       " + droneRequest);
            sendPacket(droneSendSocket, DRONE_SUBSYSTEM_PORT, droneRequest);
        }

        return true;
    }

    /**
     * Removes a fire request from the queue after it has been assigned.
     */
    private void removeRequest(FireRequest fireRequest) {
        synchronized (requestQueue) {
            System.out.println( "[  SP  ] requestQueue removing item:   -        " + fireRequest.toString() );

            requestQueue.remove(fireRequest);

            System.out.println( "[  SP  ] requestQueue post removal:    -        " + Arrays.toString(requestQueue.toArray()));
        }
    }

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

    public void setState(SchedulerState newState){
        System.out.println("* SCHEDULER STATE CHANGE * " + this.currentState.display() + " -> " + newState.display());
        this.currentState = newState;
    }

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

    // if return is -1, there are no traveling drones, there are no idle drones, they are in another state
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
                int droneID = findClosestDrone(targetZone);
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
     * Finds the closest idle drone to the center of the target zone.
     *
     * @param targetZone the target Zone object.
     * @return the drone ID of the closest idle drone, or -1 if none are available.
     */
    public int findClosestDrone(Zone targetZone) {
        System.out.println("\n[   SF]  -   FIND CLOSEST DRONE CALLED  -   " + targetZone.toString());

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
                    System.out.println("\n[   SF]  -   DRONE "+drone.getDroneId()+" IS ALREADY MOVING TO THIS ZONE -      " + targetZone.toString());
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

    public boolean willPassThrough(DroneStatus drone, Zone targetZone) {

        // Get the destination zone from the drone's current task
        Zone destinationZone = zoneMap.get(drone.getCurrentTask().getZoneId());

        if (destinationZone == null || targetZone == null) {
            return false; // Cannot determine without proper zone data
        }

        int droneX = drone.getX();
        int droneY = drone.getY();
        System.out.println("\n[ SF ] - DRONE " + drone.getDroneId() + " LOCATION (" + droneX + "," + droneY + ") " +
                "- COMPARED TO REQUEST ZONE: " + targetZone.toString() +
                " AND DRONE DESTINATION: " + destinationZone.toString());

//        return false;

        // TODO: UNCOMMENT THE FOLLOWING LOCATION COMPARISON CALCULATIONS ....
        // check if the drone is already in its destination zone
        if (droneX >= destinationZone.getStartX() && droneX <= destinationZone.getEndX() &&
                droneY >= destinationZone.getStartY() && droneY <= destinationZone.getEndY())
        {
            System.out.println("\n[ SF ] - DRONE " + drone.getDroneId() + " is already in the DESTINATION zone... invalid reassignment.");
            return false;
        }

        // check if the drone is already in the request zone
        if (droneX >= targetZone.getStartX() && droneX <= targetZone.getEndX() &&
                droneY >= targetZone.getStartY() && droneY <= targetZone.getEndY())
        {
            System.out.println("\n[ SF ] - DRONE " + drone.getDroneId() + " is already in the REQUEST zone... valid reassignment.");
            return true;
        }


        // Logic for if Drone is going to pass through request zone before destination zone

        // get center of destination zone
        int centerXDestination = (destinationZone.getStartX() + destinationZone.getEndX()) / 2;
        int centerYDestination = (destinationZone.getStartY() + destinationZone.getEndY()) / 2;

        // get center of request zone
        int centerXRequest = (targetZone.getStartX() + targetZone.getEndX()) / 2;
        int centerYRequest = (targetZone.getStartY() + targetZone.getEndY()) / 2;

        // check distances to compare which zone the drone is closer to
        double distanceToDestination = Math.sqrt(Math.pow(droneX - centerXDestination, 2) + Math.pow(droneY - centerYDestination, 2));
        double distanceToRequest = Math.sqrt(Math.pow(droneX - centerXRequest, 2) + Math.pow(droneY - centerYRequest, 2));

        double droneSlope = (double) (destinationZone.getStartY() - droneY) / ( destinationZone.getStartX() - droneX );


        if (distanceToRequest <= distanceToDestination) {
            if ( (targetZone.getStartX() * droneSlope) >= targetZone.getStartY() || (targetZone.getStartX() * droneSlope) <= targetZone.getEndY() ) {
                return true;
            }
            else if ( (targetZone.getEndX() * droneSlope) >= targetZone.getStartY() || (targetZone.getEndX() * droneSlope) <= targetZone.getEndY() ) {
                return true;
            }
            else if ( (targetZone.getStartY() * droneSlope) >= targetZone.getStartX() || (targetZone.getStartY() * droneSlope) <= targetZone.getEndX() ) {
                return true;
            }
            else if ( (targetZone.getEndY() * droneSlope) >= targetZone.getStartX() || (targetZone.getEndY() * droneSlope) <= targetZone.getEndX() ) {
                return true;
            }
        }

        return false;

    }

    /**
     * adds a fire request to the scheduler, ensuring only one request is handled at a time.
     * To be used by Fire Incident Subsystem
     * @param request the FireRequest to be added
     */
    public synchronized void addRequest(FireRequest request) {
        requestQueue.offer(request);
        System.out.println("From Scheduler - receiving request from fire incident: \n" + request + "\n");
    }

    public String takeResponse() {
        synchronized (responseQueue) {
            while (responseQueue.isEmpty()) {
                try {
                    System.out.println("\n[   SF]  -   WAITING FOR RESPONSE -   ");
                    responseQueue.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }

            String response = responseQueue.poll();
            System.out.println("[   SF]  -   RESPONSE TAKEN FROM QUEUE: " + response);

            return response;
        }
    }

    public void addResponse(String response) {
        if (response == null || response.isEmpty()) {
            System.out.println("[  SP  ] - WARNING: Attempted to add an empty response!");
            return;
        }

        synchronized (responseQueue) {
            responseQueue.offer(response);
            System.out.println("\n[  SP  ]  -   RESPONSE ADDED TO QUEUE: " + response);

            responseQueue.notifyAll();
        }
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
     * Create a new Scheduler and start listening to a FireIncidentSubsystem and DroneSubsystem.
     * @param args CLI arguments.
     */
    public static void main(String[] args) {
        Scheduler s = new Scheduler();
    }
}
