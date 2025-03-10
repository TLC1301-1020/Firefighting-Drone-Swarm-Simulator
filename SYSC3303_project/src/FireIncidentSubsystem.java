import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * The FireIncidentSubsystem handles fire incident requests and communicates with the Scheduler.
 * It reads incident data from a file, sends requests to the scheduler, and receives updates.
 * Implements Runnable to execute in a separate thread.
 */
public class FireIncidentSubsystem implements Runnable {
    /**
     * the file for fire events
     */
    private String inputFile = "SYSC3303_project/src/fireincidents.txt";

    /**
     * scheduler instance for managing fire requests
     */
    private Scheduler scheduler;
    /**
     * list of fire requests created from the data received in the input file
     * <a href="file:../src/fireincedents.txt">/src/fireincedents.txt</a>
     */
    private List<FireRequest> tasks;

    private DatagramSocket receiveSocket, sendSocket;

    /**
     * Constructs a FireIncidentSubsystem with a given scheduler.
     */
    public FireIncidentSubsystem() {
        this.tasks = new ArrayList<>();

        try {
            sendSocket = new DatagramSocket();
            receiveSocket = new DatagramSocket(Scheduler.FIRE_INCIDENT_SUBSYSTEM_PORT);
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * thread function for the fire incident subsystem
     * reads fire incidents, sends requests, and processes responses
     */
    public void run(){

        readInputFile(inputFile);

        // Send all of our requests read from file
        while(!tasks.isEmpty()){

            sendIncident(tasks.remove(0).toString());

            // This should just be an acknowledgement
            System.out.println(receiveUpdate());

        }

        // Now we request and wait for future Scheduler updates
        while(true) {

            // Request the scheduler for updates
            sendIncident("FIRE_DATA_REQUEST");

            // This should be an update that a drone has completed a FireRequest
            System.out.println(receiveUpdate());
        }

    }

    /**
     * @param inputFile read the incidents' detail from the inputFile
     * and stores them as FireRequest objects.
     * Each line in the file represents a fire incident with details separated by commas.
     */
    public void readInputFile(String inputFile) {
        try (BufferedReader reader = new BufferedReader(new FileReader(inputFile))) {
            String line;

            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                String time = parts[0].trim();
                int zoneId = Integer.parseInt(parts[1].trim());
                String eventType = parts[2].trim();
                String severity = parts[3].trim();

                FireRequest task = new FireRequest(time, zoneId, eventType, severity);
                tasks.add(task);

                System.out.println("Adding task: " + task);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * Sends an incident request to the scheduler.
     * Extracts the next available fire request, formats the data, and sends it via a UDP packet.
     */
    public void sendIncident(String request){
        byte msg[] = request.toString().getBytes();
        DatagramPacket packet;

        try {
            packet = new DatagramPacket(msg, msg.length, InetAddress.getLocalHost(), Scheduler.FIRE_TO_SCHEDULER_PORT);
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
     * Receives updates from the scheduler via a UDP packet.
     * Waits for an incoming message and prints the received update.
     */
    private String receiveUpdate(){
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

    public static void main(String[] args) {
        FireIncidentSubsystem fis = new FireIncidentSubsystem();
        Thread thread = new Thread(fis);
        thread.start();
    }

    /**
     * Getters
     *
     * @return The Scheduler instance associated with this subsystem.
     */
    public Scheduler getScheduler() {
        return scheduler;
    }

    /**
     * Getter
     *
     * @return list of fire requests
     */
    public List<FireRequest> getTasks() {
        return tasks;
    }

//    public DatagramPacket getSendPacket() {
//        return sendPacket;
//    }
//
//    public DatagramPacket getReceivePacket() {
//        return receivePacket;
//    }

    public DatagramSocket getSendSocket() {
        return sendSocket;
    }

    public DatagramSocket getReceiveSocket() {
        return receiveSocket;
    }
}
