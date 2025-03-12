import java.io.IOException;
import java.net.*;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The {@code DroneSubsystem} class manages multiple drone threads and handles communication
 * between drones and the {@code Scheduler} using UDP. It maintains a request queue for drones
 * to submit requests and a response queue for receiving instructions from the Scheduler.
 * <p>
 * Implements {@code Runnable} to execute in a separate thread
 */
public class DroneSubsystem implements Runnable {
    /**
     * datagram sockets for network communication
     */
    private DatagramSocket sendSocket, receiveSocket;
    /**
     * list of Drone objects being routed messages by DroneSubsystem process through direction
     * of the Scheduler
     */
    private final HashMap<Integer,Thread> drones = new HashMap<>();

    /**
     * flag for checking if the drones have been initialized after creating the DroneSubsystem Instance.
     * <p>{@code true} if drones have already been initialized */
    private boolean dronesInitialized;
    /**
     * thread safe queue for individual drone threads to send requests to the {@code Scheduler} through the
     * {@code DroneSubsystem}. <p>DroneSubsystem removes request from the queue and, without modifying it, sends
     * the request to the Scheduler within a datagram packet */
    private final ConcurrentLinkedQueue<String> requestQueue = new ConcurrentLinkedQueue<>();
    /**
     * thread safe hashmap for individual drone threads to remove responses from the {@code Scheduler} through the
     * {@code DroneSubsystem}. <p>DroneSubsystem receives responses from the Scheduler and, without modifying it,
     * puts the response (as the value) in this hashmap with the drone id (as the key) for a drone to check
     * if it has a response */
    private final ConcurrentHashMap<Integer, String> responseQueue = new ConcurrentHashMap<>();

    /**
     * Placeholder zone coordinates
     */
    public final static int zone2X = 100;
    public final static int zone2Y = 150;
    public final static int zone3X = 200;
    public final static int zone3Y = 100;
    public final static int zone7X = 350;
    public final static int zone7Y = 50;

    /**
     * creates a drone subsystem instance for routing messages to and from all drone threads and
     * the Scheduler
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

    /**
     * Method used by Drone instances to store a request for the Scheduler.
     * @param request the Drone's request.
     */
    public void addRequest(String request)
    {
        synchronized (requestQueue) {
            this.requestQueue.offer(request);
            this.requestQueue.notifyAll();
        }
    }

    /**
     * custom blocking take() method <p>
     * If queue is empty, waits until a request is available.
     * Removes head of queue (poll)
     * Uses wait() and notifyAll() for thread-safe blocking
     */
    public String getRequest()
    {
        synchronized (this.requestQueue)
        {
            while (this.requestQueue.isEmpty())
            {
                try {
                    this.requestQueue.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return "REQUESTQUEUE_EMPTY";
                }
            }
            return this.requestQueue.poll();
        }
    }

    /**
     * adds a response for a specific drone by the drone's id
     * Notifies waiting threads that a response is available.
     */
    public void addResponse(int droneId, String response)
    {
        synchronized (this.responseQueue)
        {
            this.responseQueue.put(droneId, response);
            this.responseQueue.notifyAll();
        }
    }

    /**
     * used by Drone instances to retrieve Scheduler responses
     * @param droneId the requesting Drones ID
     * @return the Scheduler response to the drone request
     */
    public String getResponse(int droneId)
    {
        synchronized (this.responseQueue)
        {
            while (!this.responseQueue.containsKey(droneId))
            {
                try {
                    this.responseQueue.wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            notifyAll();
            return this.responseQueue.get(droneId);
        }
    }

    /**
     * Method for instantiating all drone threads with unique drone id's and a
     * pointer to the drone Subsystem instance for invoking thread safe requests
     * and receiving responses from the scheduler via the {@code requestQueue} and
     * {@code responseQueue} <p>
     * A datagram packet is sent to the Scheduler to first register the drone and the response
     * acknowledgement enables the drone instance to be created
     * @param droneSubsystem pointer to the instance of the DroneSubsystem used in a static
     *                       context (main)
     * @param numberOfDrones the number of drone threads to be initialized in the system
     *
     */
    public void initializeAllDrones( DroneSubsystem droneSubsystem, int numberOfDrones )
    {
        // check if drones already initialized
        if( this.dronesInitialized ) return;

        int droneCounter = 0;

        for( int i = 0 ; i < numberOfDrones ; ++i )
        {
            // request in expected format "DRONE_ID:STATE:REQUEST:X:Y"
            String request = i+":[IDLE]:INIT:0:0";
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
     * thread function for the drone subsystem. calls the thread function for all drones in
     * {@code DroneSubsystem.drones} before proceeding into main loop.<p>
     * MAIN LOOP:<p>
     * 1. checks {@code DroneSubsystem.requestQueue} for requests from drones<p>
     * 2. sends drone requests unmodified in a datagram packet to the Scheduler<p>
     * 3. receives responses from scheduler and puts them unmodified in {@code DroneSubsystem.responseQueue}
     */
    public void run() {

        // TODO: determine a proper condition for thread lifespan

        // start thread functions for all drones
        Collection<Thread> allDrones = this.drones.values();
        for ( Thread drone : allDrones )
        {
            drone.start();
        }

        while(true)
        {
            // check request queue - communication from drones
            String request = getRequest();

            System.out.println("[DRONE SUBSYSTEM->SCHEDULER] handling drone request: " + request);

            // send udp packet direct to scheduler with drone request
            sendPacket(request);

            // receive response from scheduler
            String response = receivePacket();
            System.out.println("[SCHEDULER->DRONE SUBSYSTEM] received response: " + response);

            // handle the response and the drone its intended for
            handleDroneResponse(response);
        }
    }

    /**
     * handle the response passed to the drone subsystem from the scheduler
     * <p>
     * response from scheduler is parsed to obtain drone id, once obtained it
     * passes the full response (unmodified) to the response queue with the drone id as key
     * @param response String passed from Scheduler to DroneSubsystem to be
     *                  passed directely to drone
     */
    private void handleDroneResponse(String response)
    {
        /*  response types
            "ACK" in format ACK:DRONE_ID:STATE:REQUEST:X:Y      - for saying acknowledge
            "NEW" in format NEW:DRONE_ID:STATE:FIREREQUEST:X:Y  - for reassigning current task and state
         */
        String[] items = response.split(":");

        // get drone id     -   in expected format "RESPONSE:DRONE_ID:STATE:REQUEST:X:Y"
        int droneId = -1;
        try {droneId = Integer.parseInt(items[1]);}
        catch (NumberFormatException e) {
            System.out.println("ERROR: Invalid int parsing handleDroneResponse");
            return;
        }
        // add response to the response queue for drones to get
        addResponse(droneId, response);
        System.out.println(" DRONE SUBSYSTEM added response to shared queue for drone: " + droneId);
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

    public static void main(String[] args)
    {
        DroneSubsystem dss = new DroneSubsystem();
        dss.initializeAllDrones(dss, 1);

        Thread droneSubsystem = new Thread( dss );
        droneSubsystem.start();
    }

}