import java.io.IOException;
import java.net.*;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * {@code DroneSubsystem} class simulates a drone responding to fire incidents.
 * Class receives {@code FireRequest}, simulates traveling to the fire location zone,
 * and sends completion {@code Response} back to the {@code Scheduler}.
 * Implements Runnable to execute in a separate thread.
 */
public class DroneSubsystem implements Runnable {


    /**
     * datagram sockets for network communication
     */
    private DatagramSocket sendSocket, receiveSocket;
    /**
     * list of Drone objects being controlled by DroneSubsystem process through direction
     * of the scheduler
     */
    private final HashMap<Integer,Thread> drones = new HashMap<>();

    private boolean dronesInitialized;

    private final ConcurrentLinkedQueue<String> requestQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<Integer, String> responseQueue = new ConcurrentHashMap<>();

    /**
     * creates a drone subsystem instance with the shared scheduler
     */
    public DroneSubsystem()
    {
        try {
            sendSocket = new DatagramSocket();
            receiveSocket = new DatagramSocket(Scheduler.DRONE_SUBSYSTEM_PORT);
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
    }

    // drones will submit requests here
    public void addRequest(String request)
    {
        this.requestQueue.offer(request);
    }

    // subsystem retrieves and processes request at head of queue
    public String pollRequest()
    {
        return this.requestQueue.poll();
    }

    // subsystem places response for a specific drone given by scheduler
    public void addResponse(int droneId, String response)
    {
        this.responseQueue.put(droneId, response);
    }

    // drones will retrieve its response here
    public String getResponse(int droneId)
    {
        return this.responseQueue.remove(droneId);
    }

    public void initializeAllDrones( DroneSubsystem droneSubsystem, int numberOfDrones )
    {
        // check if drones already initialized
        if( this.dronesInitialized ) return;

        int droneCounter = 0;

        for( int i = 0 ; i < numberOfDrones ; ++i )
        {
            // request in expected format "DRONE_ID:STATE:REQUEST:X:Y"
            String request = i+":DroneRefill:INIT:0:0";
            sendPacket(request);

            String response = receivePacket();
            if(response.contains("ACK"))
            {
                this.drones.put( i, new Thread(new Drone(droneSubsystem, i) ) );
                ++droneCounter;
                System.out.println( " DRONE " + i + " is online");
            }
            else System.out.println( " DRONE " + i + " failed to come online");
        }
        System.out.println( droneCounter + "/" + numberOfDrones + " are now online");
        this.dronesInitialized = true;
    }


    /**
     * thread function for the drone.
     * continuously receives tasks, simulates travel, and sends updates to the {@code Scheduler}
     * managing fire requests
     */
    public void run() {

        // TODO: determine a proper condition for thread lifespan

        // start thread functions for all drones
        Collection<Thread> allDrones = this.drones.values();
        for ( Thread drone : allDrones )
        {
            drone.start();
        }

        // TODO: if needs to make drones extend inherit for access to thread and object functions
//        Collection<Drone> allDrones = this.drones.values();
//        for ( Drone drone : allDrones )
//        {
//            (new Thread(drone)).start();
//        }
        // DRONE_ID:<STATE>:REQUEST:X:Y  -> when there is a request

        while(true)
        {
            // check request queue - communication from drones
            String request = this.requestQueue.poll();
            // return null
            System.out.println("[DRONE SUBSYSTEM->SCHEDULER] handling drone request: " + request);
//            String packagedRequest = handleDroneRequest(request);
            // send udp packet direct to scheduler with drone request
            sendPacket(request);

            // receive response from scheduler
            String response = receivePacket();
            System.out.println("[SCHEDULER->DRONE SUBSYSTEM] received response: " + response);

            // handle the response and the drone its intended for
            handleDroneResponse(response);
        }
    }

    // handle the response passed to the drone subsystem from the scheduler
    private void handleDroneResponse(String response)
    {
        /*  response types
            "ACK" in format ACK:DRONE_ID:STATE:REQUEST:X:Y      - for saying acknowledge
            "NEW" in format NEW:DRONE_ID:STATE:FIREREQUEST:X:Y  - for reassigning current task and state
         */
        String[] items = response.split(":");
//        if (items.length != 6) return;

        String schedulerInstructions = items[0];

        // get drone id     -   in expected format "RESPONSE:DRONE_ID:STATE:REQUEST:X:Y"
        int droneId = -1;
        try {droneId = Integer.parseInt(items[1]);}
        catch (NumberFormatException e) {
            System.out.println("ERROR: Invalid int parsing handleDroneResponse");
            return;
        }

        // get current state of this drone     -   in expected format "RESPONSE:DRONE_ID:STATE:REQUEST:X:Y"
        String droneState = items[2];

        // get request of this drone and convert it to DroneEvent
        DroneEvent eventRequest;
        try { eventRequest = DroneEvent.valueOf(items[3]); }
        catch (IllegalArgumentException e) {
            System.out.println("ERROR: Unknown drone event: " + items[3]);
            return;
        }

        // if schedulerInstructions is acknowledgement
        if( schedulerInstructions.equals("ACK") )
        {
            // proceed with drone request

        }
        else if( schedulerInstructions.equals("NEW") )
        {
            // new tasking for that drone

        }

        this.responseQueue.put(droneId, response);
        System.out.println(" DRONE SUBSYSTEM added response to shared queue for drone: " + droneId);
    }

//    /**
//     * simulates drone travel to the fire location
//     */
//    private void travel() {
//        // TODO: sleep for an amount of time equal to destination / maxVelocity
//
//        // For now, arbitrary amount of sleep to simulate travel time
//        System.out.println("Drone " + this.droneId + ": travelling to zone " + currTask.getZoneId() + "\n");
//        try {
//            Thread.sleep(5000);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }
//        System.out.println("Drone " + this.droneId + ": arrived at zone " + currTask.getZoneId() + " ready to deploy\n");
//    }

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

    public static void main(String[] args)
    {
        DroneSubsystem dss = new DroneSubsystem();
        dss.initializeAllDrones(dss, 1);

        Thread droneSubsystem = new Thread( dss );
        droneSubsystem.start();
    }

}