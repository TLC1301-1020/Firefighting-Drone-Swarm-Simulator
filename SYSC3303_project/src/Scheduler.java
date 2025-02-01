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

    /**
     * adds a fire request to the scheduler, ensuring only one request is handled at a time.
     * To be used by Fire Incident Subsystem
     * @param request the FireRequest to be added
     */
    public synchronized void addRequest(FireRequest request) {
        while (requestAvailable) {
            try { wait(); }
            catch (InterruptedException e) { System.err.println(e); }
        }
        this.currentRequest = request;
        System.out.println("From Scheduler - receiving request from fire incident: \n" + request + "\n");
        requestAvailable = true;
        notifyAll();
    }

    /**
     * retrieves a fire request for processing by the drone subsystem.
     * To be used by Drone Subsystem
     * @return the fire request to be processed
     */
    public synchronized FireRequest takeRequest() {
        while (!requestAvailable) {
            try { wait(); }
            catch (InterruptedException e) { System.err.println(e); }
        }
        FireRequest req = currentRequest;
        System.out.println("From Scheduler - sending request to drone: \n" + req  + "\n");

        currentRequest = null;
        requestAvailable = false;
        notifyAll();
        return req;
    }

    /**
     * Drone Subsystem calls this function after completing a fire request and is waiting for the
     * completion response to be processed by the scheduler.
     * @param response the response indicating completion of a fire request
     */
    public synchronized void addResponse(Response response) {
        while (responseAvailable) {
            try { wait(); }
            catch (InterruptedException e) { System.err.println(e); }
        }
        this.currentResponse = response;
        System.out.println("From Scheduler - receiving response from drone: \n" + response + "\n");

        responseAvailable = true;
        notifyAll();
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
