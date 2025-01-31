import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class DroneSubsystem implements Runnable {
    private DatagramPacket sendPacket, receivePacket;
    private DatagramSocket sendSocket, receiveSocket;

    private Scheduler scheduler;
    private FireRequest currTask;

    private float droneId;
    private final float maxVelocity = 20;
    private float xPos;
    private float yPos;
    // TODO: Add drone attributes such as battery, acceleration etc.

    public DroneSubsystem(Scheduler scheduler) {
        this.scheduler = scheduler;
        this.droneId = 0;
        this.currTask = null;
    }

    public Scheduler getScheduler() {
        return scheduler;
    }

    public float getDroneId() {
        return droneId;
    }

    public FireRequest getCurrTask() {
        return currTask;
    }

    private static class Task {
        public LocalTime time;
        public int zoneId;
        public String eventType;
        public String severity;
    }

    public void run() {

        // TODO: determine a proper condition for thread lifespan
        while(true) {
            // Future project iteration
            // receiveTask();
            // travel();
            // sendUpdate();

            currTask = scheduler.takeRequest();
            travel();
            Response response = new Response(currTask, "completed");
            scheduler.addResponse(response);
        }

    }

    public void sendUpdate() {
        // TODO: send detailed update with drone information to Scheduler

        String update = "Task completed!";
        byte data[] = update.getBytes();
        try {
            sendSocket = new DatagramSocket();
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
        sendPacket = new DatagramPacket(data, data.length, receivePacket.getAddress(), receivePacket.getPort());

        System.out.println("Drone: Sending packet:");
        System.out.println("To host: " + sendPacket.getAddress());
        System.out.println("Destination host port: " + sendPacket.getPort());
        int len = sendPacket.getLength();
        System.out.println("Length: " + len);
        System.out.print("Containing: ");
        System.out.println(new String(sendPacket.getData(),0,len));

        try {
            sendSocket.send(sendPacket);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }

        sendSocket.close();
        receiveSocket.close();
    }

    private void receiveTask() {
        int serverPort = 9876;

        byte data[] = new byte[1000];

        try {
            receiveSocket = new DatagramSocket(serverPort);
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }

        receivePacket = new DatagramPacket(data, data.length);
        System.out.println("Drone " + droneId + " waiting for instructions.");

        // Block until we have received instructions from the scheduler
        try {
            receiveSocket.receive(receivePacket);
        } catch (IOException e) {
            System.out.print("IO Exception: likely:");
            System.out.println("Receive Socket Timed Out.\n" + e);
            e.printStackTrace();
            System.exit(1);
        }

        // Process data received
        System.out.println("Drone " + droneId + ": Packet received:");
        System.out.println("From host: " + receivePacket.getAddress());
        System.out.println("Host port: " + receivePacket.getPort());

        // Form a String from the byte array.
        System.out.print("Containing: " );
        int len = receivePacket.getLength();
        String received = new String(data,0,len);
        System.out.println(received + "\n");

        // Form the currTask object based on received data
        // processTask(received);
    }

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

    private void travel() {
        // TODO: sleep for an amount of time equal to destination / maxVelocity

        // For now, arbitrary amount of sleep to simulate travel time
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}