import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

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

    private List<DroneSubsystem> drones;
    private Queue<FireRequest> requestQueue;
    private SchedulerState currentState;

    public Scheduler() {
        this.drones = new ArrayList<>();
        this.requestQueue = new LinkedList<>();
        this.currentState = new Idle();
    }

    public void setState(SchedulerState newState){
        System.out.println("* SCHEDULER STATE CHANGE * " + this.currentState.display() + " -> " + newState.display());
        this.currentState = newState;
    }

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

    public void registerDrone(DroneSubsystem drone){
        if (!drones.contains(drone)){
            drones.add(drone);
        } else {
            System.out.println("Drone is already registered.");
        }
    }

    public List<DroneSubsystem> getDrones(){
        return drones;
    }

    private synchronized DroneSubsystem getAvailableDrone(){
        for (DroneSubsystem drone : drones) {
            if (drone.getCurrentState() instanceof DroneIdle && drone.getCurrTask() == null){
                return drone;
            }
        }
        return null;
    }

    public int getRequestQueueSize() {
        return requestQueue.size();
    }

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
    public synchronized void assignRequests(){
        while (!requestQueue.isEmpty()) {
            DroneSubsystem availableDrone = getAvailableDrone();
            if (availableDrone == null) {
                System.out.println("No available drones, requests will remain in queue.");
                break;
            }
            FireRequest req = requestQueue.poll();
            System.out.println("From Scheduler - assigning request to drone: \n" + req + "\n");
            availableDrone.setCurrTask(req);
            availableDrone.handleEvent(DroneEvent.NEW_FIRE_REQUEST);
            setState(new ProcessData(new TaskDrone()));
            currentState.handleEvent(this, SchedulerEvent.DRONE_CHOSEN);
        }
    }

    /**
     * retrieves a fire request for processing by the drone subsystem.
     * To be used by Drone Subsystem
     * @return the fire request to be processed
     */
    public synchronized FireRequest takeRequest(DroneSubsystem drone) {
        while (requestQueue.isEmpty() || !(drone.getCurrentState() instanceof DroneIdle)) {
            try { wait(); }
            catch (InterruptedException e) { System.err.println(e); }
        }
        FireRequest req = requestQueue.poll();
        System.out.println("From Scheduler - sending request to drone: \n" + req  + "\n");

        currentRequest = null;
        if (requestQueue.isEmpty()){
            requestAvailable = false;
        }

        drone.setCurrTask(req);
        drone.handleEvent(DroneEvent.NEW_FIRE_REQUEST);
        return req;
    }

    /**
     * Drone Subsystem calls this function after completing a fire request and is waiting for the
     * completion response to be processed by the scheduler.
     * @param response the response indicating completion of a fire request
     */
    public synchronized void addResponse(Response response, DroneSubsystem drone) {
        while (responseAvailable) {
            try { wait(); }
            catch (InterruptedException e) { System.err.println(e); }
        }
        this.currentResponse = response;
        System.out.println("From Scheduler - receiving response from drone: \n" + response + "\n");

        processResponse(response, drone);
    }

    private void processResponse(Response response, DroneSubsystem drone) {
        String message = response.getStatus();

        switch (message) {
            case "arrived_at_zone":
                drone.handleEvent(DroneEvent.PERMISSION_TO_DROP);
                currentState.handleEvent(this, SchedulerEvent.DRONE_ARRIVED);
                break;
            case "payload_dropped":
                drone.handleEvent(DroneEvent.PAYLOAD_DROPPED);
                break;
            case "payload_deploy_failure":
                drone.handleEvent(DroneEvent.PAYLOAD_DEPLOY_FAILURE);
                currentState.handleEvent(this, SchedulerEvent.DRONE_DEPLOY_FAILURE);
                break;
            case "returned_to_base":
                drone.handleEvent(DroneEvent.RETURNED_TO_BASE);
                break;
            case "refill_complete":
                drone.handleEvent(DroneEvent.REFILL_COMPLETE);
                break;
            case "drone_stuck":
                drone.handleEvent(DroneEvent.DRONE_STUCK);
                currentState.handleEvent(this, SchedulerEvent.DRONE_STUCK);
                break;
            case "stuck_resolved":
                drone.handleEvent(DroneEvent.STUCK_RESOLVED);
                break;
            case "deploy_failure_acknowledged":
                drone.handleEvent(DroneEvent.DEPLOY_FAILURE_ACKNOWLEDGED);
                break;
            case "completed":
                System.out.println("Drone has completed Fire request. Ready for the next task.");
                responseAvailable = true;
                notifyAll();
                setState(new SendData());
                currentState.handleEvent(this, SchedulerEvent.RESPONSE_SENT);
                assignRequests();
                break;
            default:
                System.out.println("Scheduler: Unknown response received.");
                break;
        }
    }

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
}
