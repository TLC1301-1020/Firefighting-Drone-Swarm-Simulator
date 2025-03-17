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
    private FireRequest currentRequest = null;
    private Response currentResponse = null;
    private boolean requestAvailable = false;
    private boolean responseAvailable = false;
    private HashMap<Integer, DroneStatus> drones;
    private Deque<FireRequest> requestQueue;
    private SchedulerState currentState;
    public static final int DATA_BUFFER_SIZE = 256;
    public static final int FIRE_TO_SCHEDULER_PORT = 5000;
    public static final int DRONE_TO_SCHEDULER_PORT = 5001;
    public static final int FIRE_INCIDENT_SUBSYSTEM_PORT = 5002;
    public static final int DRONE_SUBSYSTEM_PORT = 5003;
    private DatagramSocket fireReceiveSocket, droneReceiveSocket, sendSocket;
    private boolean fireRequestAssigned = true;

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

    public Scheduler() {
        this.drones = new HashMap<>();
        this.requestQueue = new LinkedList<>();
        this.currentState = new Idle();
        parseZoneFile("SYSC3303_project/src/zone_file.csv");
        for (Zone zone : zoneMap.values()) {
            System.out.println("Parsed zone: " + zone);
        }
        try {
            sendSocket = new DatagramSocket();
            fireReceiveSocket = new DatagramSocket(FIRE_TO_SCHEDULER_PORT);
            droneReceiveSocket = new DatagramSocket(DRONE_TO_SCHEDULER_PORT);
        } catch (SocketException e) {
            System.err.println(e);
        }
        Thread droneHandler = new listenToDrone();
        Thread fireHandler = new listenToFire();
        Thread processPendingRequests = new ProcessPendingRequests();
        droneHandler.start();
        fireHandler.start();
        processPendingRequests.start();
    }

    private class listenToFire extends Thread {
        @Override
        public void run() {
            System.out.println("\n[ SF  ]  -   SCHEDULER IS LISTENING TO FIRE  -   ");

            while (true) {
                System.out.println("\n[ SF  ]  -   SCHEDULER IS WAITING FOR MESSAGE FROM FIRE  -   ");
                String request = receivePacket(fireReceiveSocket);
                System.out.print("\n[SF<-F]  -   SCHEDULER RECEIVED FROM FIRE: "+request+ "  -   ");
                if (request.contains("FIRE_DATA_REQUEST")) {
                    System.out.println("request contains FIRE_DATA_REQUEST - sending through sendSocket + port: " + FIRE_INCIDENT_SUBSYSTEM_PORT);
                    sendPacket(sendSocket, FIRE_INCIDENT_SUBSYSTEM_PORT, takeResponse().toString());
                }
                else {
                    System.out.println("request is new fire request - sending through sendSocket + port: " + FIRE_INCIDENT_SUBSYSTEM_PORT);
                    FireRequest fireRequest = new FireRequest(request);
                    addRequest(fireRequest, 0);
                    sendPacket(sendSocket, FIRE_INCIDENT_SUBSYSTEM_PORT, "SCHEDULER:ACKNOWLEDGED");
                }
            }
        }
    }

    private class listenToDrone extends Thread {
        @Override
        public void run() {
            System.out.println("\n[ SD  ]  -   SCHEDULER IS LISTENING TO DRONE  -   ");
            while (true) {
                System.out.println("\n[ SD  ]  -   SCHEDULER IS WAITING FOR MESSAGE FROM DRONE  -   ");
                String request = receivePacket(droneReceiveSocket);
                System.out.println("\n[SD<-D]  -   SCHEDULER RECEIVED FROM DRONE: "+request+ "  -   ");
                String response = handleDroneRequest(request);
                System.out.println("\n[SD->D]  -   SCHEDULER RESPONSE TO DRONE: "+response+ "  -   ");
                sendPacket( sendSocket, DRONE_SUBSYSTEM_PORT, response );
            }
        }
    }

    // assigning fire requests
    private class ProcessPendingRequests extends Thread {
        @Override
        public void run() {
            while (true) {
                synchronized(requestQueue) {
                    if (!requestQueue.isEmpty()) {
                        //FireRequest req = requestQueue.peek();
                        assignFireRequest();
                        //markFireRequestAssigned();
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
            int droneId = Integer.parseInt(request.split(":")[0]);
            System.out.println("[SCHEDULER] Registering drone " + droneId);
            return "ACK:" + droneId + ":REGISTERED";
        }
        // check format     -   in expected format "DRONE_ID:STATE:REQUEST:X:Y:CURR_TASK"
        String[] items = request.split(":");
        if (items.length != 6) return "ERROR: Invalid request format:" + request;
        FireRequest currTask = new FireRequest(items[5]);
        int droneId = -1;
        try {
            droneId = Integer.parseInt(items[0]);
        } catch (NumberFormatException e) {
            return "ERROR: Invalid drone ID";
        }
        DroneStatus drone = this.drones.get(droneId);
        // if drone does not exist with scheduler, register drone and return register event
        if (drone == null) return registerDrone(droneId) + ":" + request;
        String droneState = items[1];
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
        int x,y,zone = -1;
        try {
            x = Integer.parseInt(items[3]);
            y = Integer.parseInt(items[4]);
        } catch (NumberFormatException e) {
            return "ERROR: Invalid location value: " + items[3] + "," + items[4];
        }
        //drone.setState(droneState);
        drone.setLocation(x, y);
        // TODO: handle request to proceed with the state corresponding to when this event occurs
        switch(eventRequest)
        {
            case NEW_FIRE_REQUEST:
                System.out.println("Drone " + droneId + " is now waiting for a fire request.");
                drone.setState("[IDLE]");
                return "WAIT:" + request;
            case PERMISSION_TO_DROP:
                drone.setState("[DEPLOYING]");
                return "ACK:" + request;
            case PAYLOAD_DROPPED:
                drone.setState("[RETURNING]");
                return "ACK:" + request + ":COMPLETED";
            case PAYLOAD_DEPLOY_FAILURE:
                drone.setState("[DEPLOY FAILURE]");
                return "ACK:" + request;
            case DEPLOY_FAILURE_ACKNOWLEDGED:
                // TODO :                 drone.setState("[DEPLOY FAILURE]");
                return "ACK:" + request;
            case RETURNED_TO_BASE:
                drone.setState("[REFILLING]");
                return "ACK:" + request;
            case REFILL_COMPLETE:
                //drone.setState("[IDLE]");
                FireRequest completedTask = drone.getCurrentTask();
                synchronized (this) {  // Ensuring proper synchronization
                    currentResponse = new Response(completedTask, "COMPLETED");
                    drone.setCurrentTask(new FireRequest());
                    responseAvailable = true;
                    notifyAll();
                }
                return "ACK:" + request;
            case DRONE_STUCK:
                return "ACK:" + request;
            case STUCK_RESOLVED:
                return "ACK:" + request;
            case STATUS:
                return "Temp:" + request;
            default:
                return "ERROR: UNKNOWN drone request: " + request; // should never hit

        }
    }

    /**
     * Assigns a fire request to the most appropriate drone.
     */
    private void assignFireRequest() {
        FireRequest fireRequest = getNextFireRequest();
        if (fireRequest == null) return; // Handle interruption case

        System.out.println("\n[ SF  ]  -   ASSIGN FIRE REQUEST CALLED  -   " + fireRequest);
        int selectedDroneId = selectDrone(fireRequest);

        if (selectedDroneId == -1) {
            System.out.println("No available drones to handle fire request. Re-adding request.");
            addRequest(fireRequest, 1);
            return;
        }

        DroneStatus selectedDrone = drones.get(selectedDroneId);
        System.out.println("\n[ SF  ]  -   selectedDrone: " + selectedDrone);

        FireRequest previousTask = selectedDrone.getCurrentTask();
        selectedDrone.setCurrentTask(fireRequest);
        selectedDrone.setState("[TRAVELING]");

        //removeRequest(fireRequest);

        String droneRequest;
        if (previousTask.getZoneId() != -1) {
            droneRequest = "NEW:" + selectedDroneId + ":" + selectedDrone.getState() + ":NEW_FIRE_REQUEST:" +
                    selectedDrone.getX() + ":" + selectedDrone.getY() + ":" + fireRequest;
            System.out.println("[SCHEDULER] Sending to DroneSubsystem: " + droneRequest);
            sendPacket(sendSocket, DRONE_SUBSYSTEM_PORT, droneRequest);
            System.out.println("Reassigning previous task: " + previousTask);
            addRequest(previousTask, 1); // Put the old request back into the queue
        } else {
            droneRequest = "ACK:" + selectedDroneId + ":" + selectedDrone.getState() + ":NEW_FIRE_REQUEST:" +
                    "0:0:" + fireRequest;
            sendPacket(sendSocket, DRONE_SUBSYSTEM_PORT, droneRequest);
        }

        System.out.println("[SCHEDULER->DRONE SUBSYSTEM] Sent fire request to drone " + selectedDroneId);
    }


//    private void assignFireRequest(FireRequest fireRequest) {
//        System.out.println("\n[ SF  ]  -   ASSIGN FIRE REQUEST CALLED  -   " + fireRequest.toString());
//        int selectedDroneId = selectDrone(fireRequest);
//        System.out.println("\n[ SF  ]  -   selectedDroneId: " + selectedDroneId);
//        if (selectedDroneId == -1) {
//            System.out.println("No available drones to handle fire request");
//            return;
//        }
//        DroneStatus selectedDrone = drones.get(selectedDroneId);
//        System.out.println("\n[ SF  ]  -   selectedDrone: " + selectedDrone.toString());
//        FireRequest previousTask = selectedDrone.getCurrentTask();
//        selectedDrone.setCurrentTask(fireRequest);
//        selectedDrone.setState("[TRAVELING]");
//        removeRequest(fireRequest);
//
//        // Build request string in expected format: "DRONE_ID:STATE:REQUEST:X:Y:CURR_TASK"
//
//        //
//        if (previousTask.getZoneId() != -1) {
//            // If drone had a previous task, droneRequest is constructed with "NEW" and uses the current x and y location values
//            String droneRequest = "NEW:" + selectedDroneId + ":"+ selectedDrone.getState()+ ":NEW_FIRE_REQUEST:" +
//                    selectedDrone.getX() + ":" + selectedDrone.getY() + ":" + fireRequest;
//            System.out.println("[SCHEDULER] Sending to DroneSubsystem: " + droneRequest);
//            sendPacket(sendSocket, DRONE_SUBSYSTEM_PORT, droneRequest);
//            System.out.println("Reassigning previous task: " + previousTask);
//            addRequest(previousTask); // Put the old request back into the queue
//        } else {
//            // This drone did not have a previous task, so droneRequest is constructed as a simple acknowledgment to begin the drone's activity
//            String droneRequest = "ACK:" + selectedDroneId + ":"+ selectedDrone.getState()+ ":NEW_FIRE_REQUEST:" +
//                    "0:0:" + fireRequest;
//            sendPacket(sendSocket, DRONE_SUBSYSTEM_PORT, droneRequest);
//        }
//        System.out.println("[SCHEDULER->DRONE SUBSYSTEM] Sent fire request to drone " + selectedDroneId);
//    }

    private synchronized FireRequest getNextFireRequest() {
        while (requestQueue.isEmpty()) {
            try {
                System.out.println("[INFO] Waiting for fire requests...");
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("[ERROR] Interrupted while waiting for fire requests.");
                return null;
            }
        }
        return requestQueue.poll();
    }

    /**
     * Removes a fire request from the queue after it has been assigned.
     */
    private synchronized void removeRequest(FireRequest fireRequest) {
        if (requestQueue.isEmpty()) {
            System.out.println("[WARN] removeRequest called, but queue is empty!");
            return;
        }

        if (requestQueue.remove(fireRequest)) { // Ensure request was actually in the queue
            System.out.println("[ S ] removeRequest removing: " + fireRequest);
            System.out.println("[ S ] removeRequest post removal: " + Arrays.toString(requestQueue.toArray()));
            notifyAll(); // Notify any waiting threads
        } else {
            System.out.println("[WARN] Attempted to remove a request that was not in the queue: " + fireRequest);
        }
    }

    // todo : setState needs to be synchronized
    public void setState(SchedulerState newState){
        System.out.println("* SCHEDULER STATE CHANGE * " + this.currentState.display() + " -> " + newState.display());
        this.currentState = newState;
    }

    // todo : getCurrentState needs to be synchronized
    public SchedulerState getCurrentState(){
        return this.currentState;
    }

    /**
     * Getter
     * @return currentRequest - the current fire request be tasked by the scheduler
     */
    public FireRequest getCurrentRequest() {
        return currentRequest;
    }

    /**
     * Getter
     * @return currentResponse - the latest response from the drone subsystem
     */
    public Response getCurrentResponse() {
        return currentResponse;
    }

    /**
     * checks if a fire request is available
     * @return true if a request is available, false otherwise
     */
    public boolean isRequestAvailable() {
        return requestAvailable;
    }

    /**
     * checks if a response is available
     * @return true if a response is available, false otherwise
     */
    public boolean isResponseAvailable() {
        return responseAvailable;
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

    public int selectDrone(FireRequest request) {

        System.out.println("\n[ SF  ]  -   SELECT DRONE CALLED  -   " + request.toString());

        Zone targetZone = zoneMap.get(request.getZoneId());
        if (targetZone == null) {
            System.out.println("Error: No zone data found for zone ID " + request.getZoneId());
            return -1;
        }
        // First, check for traveling drones that are on the path
        for (Map.Entry<Integer, DroneStatus> entry : drones.entrySet()) {
            DroneStatus drone = entry.getValue();
            System.out.println("\n[ SF  ]  -   SELECT DRONE check state ID: " +drone.getDroneId() + " : " + drone.getState());

            if (drone.getState().contains("TRAVELING")) {
                // Here you would implement your own logic to decide if the drone is on the path.
                // For illustration, assume we have an isOnPath() method:
                if (isOnPath(drone, targetZone)) {
                    return entry.getKey();
                }
            }
        }
        // Otherwise, select the closest idle drone
        return findClosestIdleDrone(targetZone);
    }

    private boolean isOnPath(DroneStatus drone, Zone targetZone) {
        // Retrieve the destination zone from the drone's current task.
        Zone destZone = zoneMap.get(drone.getCurrentTask().getZoneId());
        if (destZone == null) return false;
        // Use a helper method to check for intersection.
        return zonesIntersect(destZone, targetZone);
    }

    private boolean zonesIntersect(Zone a, Zone b) {
        // Check if two zones (rectangles) intersect.
        return !(a.getEndX() < b.getStartX() ||
                a.getStartX() > b.getEndX() ||
                a.getEndY() < b.getStartY() ||
                a.getStartY() > b.getEndY());
    }



    /**
     * Finds the closest idle drone to the center of the target zone.
     *
     * @param targetZone the target Zone object.
     * @return the drone ID of the closest idle drone, or -1 if none are available.
     */
    public int findClosestIdleDrone(Zone targetZone) {
        System.out.println("\n[ SF  ]  -   FIND CLOSES IDLE DRONE CALLED  -   " + targetZone.toString());

        if (targetZone == null) {
            System.out.println("Error: Target zone not found for the requested zone ID.");
            return -1;
        }
        int bestDroneId = -1;
        double bestDistance = Double.MAX_VALUE;
        for (Map.Entry<Integer, DroneStatus> entry : drones.entrySet()) {
            DroneStatus drone = entry.getValue();
            if (drone.getState().contains("IDLE")) {
                int centerX = (targetZone.getStartX() + targetZone.getEndX()) / 2;
                int centerY = (targetZone.getStartY() + targetZone.getEndY()) / 2;
                double distance = Math.sqrt(Math.pow(drone.getX() - centerX, 2) + Math.pow(drone.getY() - centerY, 2));
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestDroneId = entry.getKey();
                }
            }
        }
        return bestDroneId;
    }



    public boolean willPassThrough(DroneStatus drone, int requestZoneId) {
        int droneX = drone.getX();
        int droneY = drone.getY();

        // Get the destination zone from the drone's current task
        Zone destinationZone = zoneMap.get(drone.getCurrentTask().getZoneId());
        // Get the request zone from the zoneMap
        Zone requestZone = zoneMap.get(requestZoneId);

        if (destinationZone == null || requestZone == null) {
            return false; // Cannot determine without proper zone data
        }

        // Check if the drone is already within the destination zone
        if (droneX >= destinationZone.getStartX() && droneX <= destinationZone.getEndX() &&
                droneY >= destinationZone.getStartY() && droneY <= destinationZone.getEndY()) {
            return true;
        }

        // Check if the drone's current position is within the request zone boundaries
        if (droneX >= requestZone.getStartX() && droneX <= requestZone.getEndX() &&
                droneY >= requestZone.getStartY() && droneY <= requestZone.getEndY()) {
            return true;
        }

        return false;
    }





















    public List<DroneSubsystem> getDrones(){
//        return drones;
        return null;
    }

    // TODO: Damon & Dylan update this logic to pick a suitable drone from the DroneStatus objects available in this.drones
//    private synchronized DroneSubsystem getAvailableDrone(){
//        for (DroneSubsystem drone : drones) {
//            if (drone.getCurrentState() instanceof DroneIdle && drone.getCurrTask() == null){
//                return drone;
//            }
//        }
//        return null;
//    }
//
//    public int getRequestQueueSize() {
//        return requestQueue.size();
//    }

    /**
     * adds a fire request to the scheduler, ensuring only one request is handled at a time.
     * To be used by Fire Incident Subsystem
     * @param request the FireRequest to be added
     */
    public synchronized void addRequest(FireRequest request, int first) {
        boolean wasEmpty = requestQueue.isEmpty();
        if (first == 1){
            requestQueue.addFirst(request);
        } else {
            requestQueue.offer(request);
        }
        System.out.println("From Scheduler - receiving request from fire incident: \n" + request + "\n");

        setState(new ProcessData(new ReceiveData()));
        currentState.handleEvent(this, SchedulerEvent.REQUEST_RECEIVED);

        if (wasEmpty) {
            notifyAll(); // Only notify if threads might have been waiting
        }
    }


    // TODO: Damon & Dylan update this logic to pick a suitable drone from the DroneStatus objects available in this.drones
//    public synchronized void assignRequests(){
//        while (!requestQueue.isEmpty()) {
//            DroneSubsystem availableDrone = getAvailableDrone();
//            if (availableDrone == null) {
//                System.out.println("No available drones, requests will remain in queue.");
//                break;
//            }
//            FireRequest req = requestQueue.poll();
//            System.out.println("From Scheduler - assigning request to drone: \n" + req + "\n");
//            availableDrone.setCurrTask(req);
//            availableDrone.handleEvent(DroneEvent.NEW_FIRE_REQUEST);
//            setState(new ProcessData(new TaskDrone()));
//            currentState.handleEvent(this, SchedulerEvent.DRONE_CHOSEN);
//        }
//    }

    /**
     * retrieves a fire request for processing by the drone subsystem.
     * To be used by Drone Subsystem
     * @return the fire request to be processed
     */
//    public synchronized FireRequest takeRequest(DroneSubsystem drone) {
//        while (requestQueue.isEmpty() || !(drone.getCurrentState() instanceof DroneIdle)) {
//            try { wait(); }
//            catch (InterruptedException e) { System.err.println(e); }
//        }
//        FireRequest req = requestQueue.poll();
//        System.out.println("From Scheduler - sending request to drone: \n" + req  + "\n");
//
//        currentRequest = null;
//        if (requestQueue.isEmpty()){
//            requestAvailable = false;
//        }
//
//        drone.setCurrTask(req);
//        drone.handleEvent(DroneEvent.NEW_FIRE_REQUEST);
//        return req;
//    }

    /**
     * Drone Subsystem calls this function after completing a fire request and is waiting for the
     * completion response to be processed by the scheduler.
     * @param response the response indicating completion of a fire request
     */
//    public synchronized void addResponse(Response response, DroneSubsystem drone) {
//        while (responseAvailable) {
//            try { wait(); }
//            catch (InterruptedException e) { System.err.println(e); }
//        }
//        this.currentResponse = response;
//        System.out.println("From Scheduler - receiving response from drone: \n" + response + "\n");
//
//        processResponse(response, drone);
//    }

//    private void processResponse(Response response, DroneSubsystem drone) {
//        String message = response.getStatus();
//
//        // response in expected format "KEYWORD:DRONE_ID:STATE:RESPONSE:X:Y"
//
//        switch (message) {
//            case "arrived_at_zone":
//                drone.handleEvent(DroneEvent.PERMISSION_TO_DROP);
//                currentState.handleEvent(this, SchedulerEvent.DRONE_ARRIVED);
//                break;
//            case "payload_dropped":
//                drone.handleEvent(DroneEvent.PAYLOAD_DROPPED);
//                break;
//            case "payload_deploy_failure":
//                drone.handleEvent(DroneEvent.PAYLOAD_DEPLOY_FAILURE);
//                currentState.handleEvent(this, SchedulerEvent.DRONE_DEPLOY_FAILURE);
//                break;
//            case "returned_to_base":
//                drone.handleEvent(DroneEvent.RETURNED_TO_BASE);
//                break;
//            case "refill_complete":
//                drone.handleEvent(DroneEvent.REFILL_COMPLETE);
//                break;
//            case "drone_stuck":
//                drone.handleEvent(DroneEvent.DRONE_STUCK);
//                currentState.handleEvent(this, SchedulerEvent.DRONE_STUCK);
//                break;
//            case "stuck_resolved":
//                drone.handleEvent(DroneEvent.STUCK_RESOLVED);
//                break;
//            case "deploy_failure_acknowledged":
//                drone.handleEvent(DroneEvent.DEPLOY_FAILURE_ACKNOWLEDGED);
//                break;
//            case "completed":
//                System.out.println("Drone has completed Fire request. Ready for the next task.");
//                responseAvailable = true;
//                notifyAll();
//                setState(new SendData());
//                currentState.handleEvent(this, SchedulerEvent.RESPONSE_SENT);
//                assignRequests();
//                break;
//            default:
//                System.out.println("Scheduler: Unknown response received.");
//                break;
//        }
//    }

    /**
     * Fire Incident Subsystem calls this function when waiting for a response to become available from the scheduler.
     * Once available, Scheduler retrieves a response for processing
     * @return the response from the drone subsystem
     */
    public synchronized Response takeResponse() {
        while (!responseAvailable) {
            try { wait(); }
            catch (InterruptedException e) { System.err.println(e); }
        }
        Response res = currentResponse;
        System.out.println("From Scheduler - sending response to fire incident: \n" + res  + "\n");

        currentResponse = null;
        responseAvailable = false;
        notifyAll();
        return res;
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

    private synchronized void markFireRequestAssigned()
    {
        fireRequestAssigned = true;
        notifyAll();
    }

    /**
     * Create a new Scheduler and start listening to a FireIncidentSubsystem and DroneSubsystem.
     * @param args CLI arguments.
     */
    public static void main(String[] args) {
        Scheduler s = new Scheduler();
    }
}
