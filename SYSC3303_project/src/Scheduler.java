import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * {@code Scheduler} coordinates fire requests between the fire incident and drone subsystem threads.
 * Scheduler synchronizes request handling, ensuring proper thread-safe task distribution and response management
 */
public class Scheduler {
    /**
     * current fire request being processed
     */
    private FireRequest currentRequest = null;
    /**
     * latest response received from the drone subsystem
     */
    private Response currentResponse = null;
    /**
     * flag indicating whether a fire request is available
     */
    private boolean requestAvailable = false;
    /**
     * flag indicating whether a response is available
     */
    private boolean responseAvailable = false;

    private HashMap<Integer, DroneStatus> drones;

    //    private List<DroneSubsystem> drones;    // old
    private Queue<FireRequest> requestQueue;
    private SchedulerState currentState;

    public static final int DATA_BUFFER_SIZE = 256;
    public static final int FIRE_TO_SCHEDULER_PORT = 5000;
    public static final int DRONE_TO_SCHEDULER_PORT = 5001;
    public static final int FIRE_INCIDENT_SUBSYSTEM_PORT = 5002;
    public static final int DRONE_SUBSYSTEM_PORT = 5003;
    private DatagramSocket fireReceiveSocket, droneReceiveSocket, sendSocket;
    //private final ConcurrentLinkedQueue<Integer> waitingDronesQueue = new ConcurrentLinkedQueue<>();

    /**
     * Placeholder zone coordinates
     */
    public final static int zone2Xstart = 50;
    public final static int zone2Xend = 150;
    public final static int zone2Ystart = 100;
    public final static int zone2Yend = 200;

    public final static int zone3Xstart = 150;
    public final static int zone3Xend = 250;
    public final static int zone3Ystart = 50;
    public final static int zone3Yend = 150;

    public final static int zone7Xstart = 300;
    public final static int zone7Xend = 400;
    public final static int zone7Ystart = 50;
    public final static int zone7Yend = 150;

    /**
     * Thread to listen to FireIncidentSubsystem.
     */
    private class listenToFire extends Thread {
        @Override
        public void run() {
            while (true) {

                // This request will be a new FireRequest to add, or just a data request for an available Drone response
                String request = receivePacket(fireReceiveSocket);

                /* TODO: need to handle scheduler set state here with two threads
                 droneHandler and fireHandler threads
                * */

                // This is a data request, and we will wait via takeResponse() until an available Drone response is ready to send back

                // At this point currentResponse should be a list of responses since we will most likely be taking more than one at a time
                if (request.contains("FIRE_DATA_REQUEST")) {
                    sendPacket(sendSocket, FIRE_INCIDENT_SUBSYSTEM_PORT, takeResponse().toString());
                }

                // Otherwise it's a new FireRequest. Add it, and send back an acknowledgement
                else {
                    FireRequest fireRequest = new FireRequest(request);
                    addRequest(fireRequest);
                    sendPacket(sendSocket, FIRE_INCIDENT_SUBSYSTEM_PORT, "SCHEDULER:ACKNOWLEDGED");

                    // After adding the fire request, attempt to assign it to a drone
                    assignFireRequest(fireRequest);
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
            while (true) {
                // Receive packet from the DroneSubsystem
                String request = receivePacket(droneReceiveSocket);

                /* TODO: need to handle scheduler set state here with two threads
                    droneHandler and fireHandler threads
                 */

                // get drone status -

                // Parse the packet (assuming we are not actually storing physical Drones anymore, and instead are only storing crucial data for each drone):
                String response = handleDroneRequest(request);
                // send response
                sendPacket( sendSocket, DRONE_SUBSYSTEM_PORT, response );
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
            eventRequest = DroneEvent.valueOf(items[2]); // Convert string to enum
        } catch (IllegalArgumentException e) {
            if (!items[2].equals("[STATUS]")) return "ERROR: Unknown drone event: " + items[2];
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
        // update state/location of drone
        drone.setState(droneState);
        drone.setLocation(x, y);


        /* request types
            "STATUS"   DRONE_ID:<STATE>:[STATUS]:X:Y:CURR_TASK  -> when there is a request
            DRONE_ID:<STATE>:REQUEST:X:Y:CURR_TASK  -> when there is a request
         */

        /*  response types
            "ACK" in format ACK:DRONE_ID:STATE:REQUEST:X:Y:CURR_TASK    - for saying acknowledge
            "NEW" in format NEW:DRONE_ID:STATE:FIREREQUEST:X:Y:CURR_TASK  - for reassigning current task and state
         */


        // TODO: handle request to proceed with the state corresponding to when this event occurs
        switch(eventRequest)
        {
            case NEW_FIRE_REQUEST:
                System.out.println("Drone " + droneId + " is now waiting for a fire request.");
                return "WAIT:" + request;
            case PERMISSION_TO_DROP:
                return "ACK:" + request;
            case PAYLOAD_DROPPED:
                return "ACK:" + request;
            case PAYLOAD_DEPLOY_FAILURE:
                return "ACK:" + request;
            case DEPLOY_FAILURE_ACKNOWLEDGED:
                return "ACK:" + request;
            case RETURNED_TO_BASE:
                return "ACK:" + request;
            case REFILL_COMPLETE:
                return "ACK:" + request;
            case DRONE_STUCK:
                return "ACK:" + request;
            case STUCK_RESOLVED:
                return "ACK:" + request;
            default:
                if ( items[2].equals("[STATUS]") )
                {
                    // drone is sending location update while traveling to fire zone
                    return "Temp:" + request;
                }
                else
                {
                    return "ERROR: UNKNOWN drone request: " + request; // should never hit
                }
        }

        // (If parsing with processResponse(), need to update that logic to include checking for which droneId, etc.)
        // If it's a request for a FireRequest, provide a request from takeRequest()
        // If it's a status update for a certain drone, store that information, reply with a proper acknowledgement/response
        // etc.
        // Example: sending DroneSubsystem a new request after it has asked for one
        // sendPacket(sendSocket, DRONE_SUBSYSTEM_PORT, takeRequest().toString())
        // Example: sending a general acknowledgement after a drone sends us its updated location
        // sendPacket(sendSocket, DRONE_SUBSYSTEM_PORT, "SCHEDULER:ACKNOWLEDGED")

    }

    /**
     * Assigns a fire request to the most appropriate drone.
     */
    private void assignFireRequest(FireRequest fireRequest) {
        int zone = fireRequest.getZoneId();
        int zoneX,zoneY;
        switch(zone)
        {
            case 2:
                zoneX = 100;
                zoneY = 150;
                break;
            case 3:
                zoneX = 200;
                zoneY = 100;
                break;
            case 7:
                zoneX = 350;
                zoneY = 50;
                break;
            default:
                zoneX = 0;
                zoneY = 0;
                break;
        }
        int selectedDroneId = selectDrone(zoneX, zoneY, zone);

        if (selectedDroneId == -1) {
            System.out.println("No available drones to handle fire request");
            return;
        }

        DroneStatus selectedDrone = drones.get(selectedDroneId);
        FireRequest previousTask = selectedDrone.getCurrentTask();
        selectedDrone.setCurrentTask(fireRequest);
        removeRequest(fireRequest);

        // Build request string in expected format: "DRONE_ID:STATE:REQUEST:X:Y:CURR_TASK"

        if (previousTask.getZoneId() != -1) {
            // If drone had a previous task, droneRequest is constructed with "NEW" and uses the current x and y location values
            String droneRequest = "NEW:" + selectedDroneId + ":[IDLE]:NEW_FIRE_REQUEST:" +
                    selectedDrone.getX() + ":" + selectedDrone.getY() + ":" + fireRequest;
            sendPacket(sendSocket, DRONE_SUBSYSTEM_PORT, droneRequest);
            System.out.println("Reassigning previous task: " + previousTask);
            addRequest(previousTask); // Put the old request back into the queue
        } else {
            // This drone did not have a previous task, so droneRequest is constructed as a simple acknowledgment to begin the drone's activity
            String droneRequest = "ACK:" + selectedDroneId + ":[IDLE]:NEW_FIRE_REQUEST:" +
                    "0:0:" + fireRequest;
            sendPacket(sendSocket, DRONE_SUBSYSTEM_PORT, droneRequest);
        }
        System.out.println("[SCHEDULER->DRONE SUBSYSTEM] Sent fire request to drone " + selectedDroneId);

    }

    /**
     * Removes a fire request from the queue after it has been assigned.
     */
    private void removeRequest(FireRequest fireRequest) {
        synchronized (requestQueue) {
            requestQueue.remove(fireRequest);
        }
    }

    public Scheduler() {
        this.drones = new HashMap<>();
        this.requestQueue = new LinkedList<>();
        this.currentState = new Idle();

        try {
            sendSocket = new DatagramSocket();
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

        // alternative use thread methods
//        new Thread(this::droneHandler).start();
//        new Thread(this::fireHandler).start();
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

    private int selectDrone(int requestX, int requestY, int requestZone) {
        DroneStatus bestCandidate = null;
        int bestDroneId = -1;

        for (Map.Entry<Integer, DroneStatus> entry : drones.entrySet()) {
            int droneId = entry.getKey();
            DroneStatus drone = entry.getValue();

            if (drone.getState().equals("[TRAVELING]")) {
                if (willPassThrough(drone, requestZone)) {
                    return droneId; // Immediately return if a traveling drone will pass through the request zone
                }
            } else if (drone.getState().equals("[IDLE]")) {
                if (bestCandidate == null) {
                    bestCandidate = drone;
                    bestDroneId = droneId;
                }
            }
        }

        return bestDroneId; // If no traveling drone is found, return an idle drone (or -1 if none are available)
    }

    private boolean willPassThrough(DroneStatus drone, int requestZone) {
        int droneX = drone.getX();
        int droneY = drone.getY();
        int destinationZone = drone.getCurrentTask().getZoneId();

        // Get zone boundaries
        int zoneXStart, zoneXEnd, zoneYStart, zoneYEnd;
        switch (destinationZone) {
            case 2:
                zoneXStart = zone2Xstart; zoneXEnd = zone2Xend;
                zoneYStart = zone2Ystart; zoneYEnd = zone2Yend;
                break;
            case 3:
                zoneXStart = zone3Xstart; zoneXEnd = zone3Xend;
                zoneYStart = zone3Ystart; zoneYEnd = zone3Yend;
                break;
            case 7:
                zoneXStart = zone7Xstart; zoneXEnd = zone7Xend;
                zoneYStart = zone7Ystart; zoneYEnd = zone7Yend;
                break;
            default:
                return false; // Unknown zone
        }

        // Check if the drone is already in the requested zone
        if (droneX >= zoneXStart && droneX <= zoneXEnd &&
                droneY >= zoneYStart && droneY <= zoneYEnd) {
            return true;
        }

        // Check if the drone will pass through the request zone
        switch (requestZone) {
            case 2:
                return (droneX <= zone2Xend && droneX >= zone2Xstart) &&
                        (droneY <= zone2Yend && droneY >= zone2Ystart);
            case 3:
                return (droneX <= zone3Xend && droneX >= zone3Xstart) &&
                        (droneY <= zone3Yend && droneY >= zone3Ystart);
            case 7:
                return (droneX <= zone7Xend && droneX >= zone7Xstart) &&
                        (droneY <= zone7Yend && droneY >= zone7Ystart);
            default:
                return false;
        }
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
    public synchronized void addRequest(FireRequest request) {
        requestQueue.offer(request);
        System.out.println("From Scheduler - receiving request from fire incident: \n" + request + "\n");
        setState(new ProcessData(new ReceiveData()));
        currentState.handleEvent(this, SchedulerEvent.REQUEST_RECEIVED);
        notifyAll();
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
