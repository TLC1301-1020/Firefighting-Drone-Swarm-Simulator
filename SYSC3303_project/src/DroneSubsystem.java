import java.io.IOException;
import java.net.*;
import java.time.LocalTime;

/**
 * {@code DroneSubsystem} class simulates a drone responding to fire incidents.
 * Class receives {@code FireRequest}, simulates traveling to the fire location zone,
 * and sends completion {@code Response} back to the {@code Scheduler}.
 * Implements Runnable to execute in a separate thread.
 */
public class DroneSubsystem implements Runnable {

    /**
     * State Machine Object to handle the state of the drone */
    public DroneState currentState;

    /**
     * datagram sockets for network communication
     */
    private DatagramSocket sendSocket, receiveSocket;
    /**
     * scheduler instance for managing fire requests and responses
     */
    // private Scheduler scheduler;
    /**
     * current fire request assigned to the drone
     */
    private FireRequest currTask;
    /**
     * unique identifier for the drone
     */
    private int droneId;
    /**
     * maximum velocity of the drone in meters per second
     */
    private final float maxVelocity = 20;
    /**
     * x and y coordinates representing drone position
     */
    private float xPos;
    private float yPos;

    // TODO: Add drone attributes such as battery, acceleration etc.

    /**
     * creates a drone subsystem instance with the shared scheduler
     */
    public DroneSubsystem() {
        this.currentState = new DroneIdle();
        this.droneId = 0;
        this.currTask = null;

        try {
            sendSocket = new DatagramSocket();
            receiveSocket = new DatagramSocket(Scheduler.DRONE_SUBSYSTEM_PORT);
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @param newState to change the DroneState machine object representing the current state of the drone.
     * Invoked by the DroneState state machine when changing the state of the drone
     */
    public void setState(DroneState newState) {
        System.out.print("* DRONE STATE CHANGE * " + this.currentState.display() + " -> ");
        this.currentState = newState;
        System.out.println(this.currentState.display());
    }

    /**
     * @return currentState of the drone.
     * invoked in the context of checking the current state:
     * {@code drone.getCurrentState() instanceof DroneActive}
     */
    public DroneState getCurrentState() {
        return this.currentState;
    }

    /**
     * @param event leading to the DroneState machine object changing state from the DroneEvent enum list.
     * invoked in the context of changing the current state of the drone given the passed event parameter:
     * {@code drone.handleEvent(DroneEvent.NEW_FIRE_REQUEST)}
     */
    public void handleEvent(DroneEvent event) {
        this.currentState.handleEvent(this, event);
    }

    /**
     * returns the scheduler instance
     * @return scheduler instance
     */
//    public Scheduler getScheduler() {
//        return scheduler;
//    }

    /**
     * returns the drone id
     * @return drone id
     */
    public float getDroneId() {
        return droneId;
    }


    /**
     * the current fire request assigned to the drone is returned,
     * and a new fire request is swapped in
     * @return currTask - the previous fire request assigned to this drone
     */
    public FireRequest setCurrTask( FireRequest newTask )
    {
        FireRequest temp = this.currTask;
        this.currTask = newTask;
        return temp;
    }

    /**
     * returns the current fire request assigned to the drone
     * @return current fire request
     */
    public FireRequest getCurrTask() {
        return currTask;
    }

    private static class Task {
        public LocalTime time;
        public int zoneId;
        public String eventType;
        public String severity;
    }

    /**
     * thread function for the drone.
     * continuously receives tasks, simulates travel, and sends updates to the {@code Scheduler}
     * managing fire requests
     */
    public void run() {

        // TODO: determine a proper condition for thread lifespan
        while(true) {
            // Convert these steps from this format to sending UDP messages instead

            currTask = scheduler.takeRequest(this);
            System.out.println("Drone " + this.droneId + ": received task: " + currTask.toString() + "\n");
            travel();
            scheduler.addResponse(new Response(currTask, "arrived_at_zone"), this);
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}

            scheduler.addResponse(new Response(currTask, "payload_dropped"), this);
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}

            scheduler.addResponse(new Response(currTask, "returned_to_base"), this);
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}

            scheduler.addResponse(new Response(currTask, "refill_complete"), this);
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}

            scheduler.addResponse(new Response(currTask, "completed"), this);

        }

    }

    /**
     * simulates drone travel to the fire location
     */
    private void travel() {
        // TODO: sleep for an amount of time equal to destination / maxVelocity

        // For now, arbitrary amount of sleep to simulate travel time
        System.out.println("Drone " + this.droneId + ": travelling to zone " + currTask.getZoneId() + "\n");
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        System.out.println("Drone " + this.droneId + ": arrived at zone " + currTask.getZoneId() + " ready to deploy\n");
    }

    /**
     * Send a UDP packet to the Scheduler.
     * @param request message to be sent.
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
     * Receive a UDP packet from the Scheduler.
     * @return the message received.
     */
    private String receivePacket(){
        byte data[] = new byte[Scheduler.DATA_BUFFER_SIZE];
        DatagramPacket receivePacket = new DatagramPacket(data, data.length);

        try {
            // Block until a datagram is received via socket
            receiveSocket.receive(receivePacket);
        } catch(IOException e) {
            throw new RuntimeException(e);
        }

        int len = receivePacket.getLength();

        // Return a String from the byte array
        return new String(data,0,len);
    }

}