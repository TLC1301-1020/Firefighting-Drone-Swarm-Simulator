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

    /**
     * datagram sockets and packets for network communication
     */
    private DatagramPacket sendPacket, receivePacket;
    private DatagramSocket sendSocket, receiveSocket;

    /**
     * Constructs a FireIncidentSubsystem with a given scheduler.
     *
     * @param scheduler The scheduler responsible for managing fire incident requests.
     */
    public FireIncidentSubsystem(Scheduler scheduler) {
        this.scheduler = scheduler;
        this.tasks = new ArrayList<>();
    }


    /**
     * thread function for the fire incident subsystem
     * reads fire incidents, sends requests, and processes responses
     */
    public void run(){


        readInputFile(inputFile);
        while(!tasks.isEmpty()){

            // sendIncident();
            // receiveUpdate();

            scheduler.addRequest(tasks.remove(0));
            scheduler.takeResponse();
        }
        System.exit(0);

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
    public void sendIncident(){
        int serverPort = 9876;
        if(tasks.isEmpty()){
            System.out.println("No tasks to send.");
            return;
        }

        try{
            sendSocket = new DatagramSocket();
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }

        try {
            InetAddress serverAddress = InetAddress.getByName("localhost");
            FireRequest task = tasks.remove(0);
            String taskData = String.format("%s,%d,%s,%s",
                    task.getTime(), task.getZoneId(), task.getEventType(), task.getSeverity());
            byte[] data = taskData.getBytes();

            sendPacket = new DatagramPacket(data, data.length, serverAddress, serverPort);
            sendSocket.send(sendPacket);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
        sendSocket.close();
        receiveSocket.close();

    }

    /**
     * Receives updates from the scheduler via a UDP packet.
     * Waits for an incoming message and prints the received update.
     */
    private void receiveUpdate(){
        int serverPort = 9876;

        try{
            receiveSocket = new DatagramSocket(serverPort);
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
        byte[] buffer = new byte[1024];

        receivePacket = new DatagramPacket(buffer, buffer.length);
        System.out.println("Waiting for updates from Scheduler.");

        try{
            receiveSocket.receive(receivePacket);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }

        String receiveMessage = new String(receivePacket.getData(),0,receivePacket.getLength());
        System.out.println("FireIncident received update: " + receiveMessage);

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

    public DatagramPacket getSendPacket() {
        return sendPacket;
    }

    public DatagramPacket getReceivePacket() {
        return receivePacket;
    }

    public DatagramSocket getSendSocket() {
        return sendSocket;
    }

    public DatagramSocket getReceiveSocket() {
        return receiveSocket;
    }
}
