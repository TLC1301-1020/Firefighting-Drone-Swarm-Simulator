public class Scheduler {
    private FireRequest currentRequest = null;
    private Response currentResponse = null;
    private boolean requestAvailable = false;
    private boolean responseAvailable = false;

    public FireRequest getCurrentRequest() {
        return currentRequest;
    }

    public Response getCurrentResponse() {
        return currentResponse;
    }

    public boolean isRequestAvailable() {
        return requestAvailable;
    }

    public boolean isResponseAvailable() {
        return responseAvailable;
    }
    // To be used by Fire Incident Subsystem
    public synchronized void addRequest(FireRequest request) {
        while (requestAvailable) {
            try { wait(); }
            catch (InterruptedException e) { System.err.println(e); }
        }
        this.currentRequest = request;
        requestAvailable = true;
        notifyAll();
    }

    // To be used by Drone Subsystem
    public synchronized FireRequest takeRequest() {
        while (!requestAvailable) {
            try { wait(); }
            catch (InterruptedException e) { System.err.println(e); }
        }
        FireRequest req = currentRequest;
        currentRequest = null;
        requestAvailable = false;
        notifyAll();
        return req;
    }

    // To be used by Drone Subsystem
    public synchronized void addResponse(Response response) {
        while (responseAvailable) {
            try { wait(); }
            catch (InterruptedException e) { System.err.println(e); }
        }
        this.currentResponse = response;
        responseAvailable = true;
        notifyAll();
    }

    // To be used by Fire Incident Subsystem
    public synchronized Response takeResponse() {
        while (!responseAvailable) {
            try { wait(); }
            catch (InterruptedException e) { System.err.println(e); }
        }
        Response res = currentResponse;
        currentResponse = null;
        responseAvailable = false;
        notifyAll();
        return res;
    }
}