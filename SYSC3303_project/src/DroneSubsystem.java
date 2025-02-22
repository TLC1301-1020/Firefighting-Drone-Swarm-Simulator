import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

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
     * datagram sockets and packets for network communication
     */
    private DatagramPacket sendPacket, receivePacket;
    private DatagramSocket sendSocket, receiveSocket;
    /**
     * scheduler instance for managing fire requests and responses
     */
    private Scheduler scheduler;
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
     * @param scheduler the {@code Scheduler} managing all fire requests
     */
    public DroneSubsystem(Scheduler scheduler) {
        this.currentState = new DroneIdle();
        this.scheduler = scheduler;
        this.droneId = 0;
        this.currTask = null;
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
    public Scheduler getScheduler() {
        return scheduler;
    }

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
            // Future project iteration
            // receiveTask();
            // travel();
            // sendUpdate();

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

//    public void sendUpdate() {
//        // TODO: send detailed update with drone information to Scheduler
//
//        String update = "Task completed!";
//        byte data[] = update.getBytes();
//        try {
//            sendSocket = new DatagramSocket();
//        } catch (SocketException e) {
//            throw new RuntimeException(e);
//        }
//        sendPacket = new DatagramPacket(data, data.length, receivePacket.getAddress(), receivePacket.getPort());
//
//        System.out.println("Drone: Sending packet:");
//        System.out.println("To host: " + sendPacket.getAddress());
//        System.out.println("Destination host port: " + sendPacket.getPort());
//        int len = sendPacket.getLength();
//        System.out.println("Length: " + len);
//        System.out.print("Containing: ");
//        System.out.println(new String(sendPacket.getData(),0,len));
//
//        try {
//            sendSocket.send(sendPacket);
//        } catch (IOException e) {
//            e.printStackTrace();
//            System.exit(1);
//        }
//
//        sendSocket.close();
//        receiveSocket.close();
//    }

//    private void receiveTask() {
//        int serverPort = 9876;
//
//        byte data[] = new byte[1000];
//
//        try {
//            receiveSocket = new DatagramSocket(serverPort);
//        } catch (SocketException e) {
//            throw new RuntimeException(e);
//        }
//
//        receivePacket = new DatagramPacket(data, data.length);
//        System.out.println("Drone " + droneId + " waiting for instructions.");
//
//        // Block until we have received instructions from the scheduler
//        try {
//            receiveSocket.receive(receivePacket);
//        } catch (IOException e) {
//            System.out.print("IO Exception: likely:");
//            System.out.println("Receive Socket Timed Out.\n" + e);
//            e.printStackTrace();
//            System.exit(1);
//        }
//
//        // Process data received
//        System.out.println("Drone " + droneId + ": Packet received:");
//        System.out.println("From host: " + receivePacket.getAddress());
//        System.out.println("Host port: " + receivePacket.getPort());
//
//        // Form a String from the byte array.
//        System.out.print("Containing: " );
//        int len = receivePacket.getLength();
//        String received = new String(data,0,len);
//        System.out.println(received + "\n");
//
//        // Form the currTask object based on received data
//        // processTask(received);
//    }

//    private void processTask(String received) {
//        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
//
//        // Remove all space characters and split by commas
//        received = received.replaceAll("\\s+", "");
//        String[] taskValues = received.split(",");
//
//        currTask.time = LocalTime.parse(taskValues[0], formatter);
//        currTask.zoneId = Integer.parseInt(taskValues[1]);
//        currTask.eventType = taskValues[2];
//        currTask.severity = taskValues[3];
//    }

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

}