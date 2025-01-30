import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class FireIncidentSubsystem implements Runnable {
    private Scheduler scheduler;
    private List<FireRequest> tasks;

    private DatagramPacket sendPacket, receivePacket;
    private DatagramSocket sendSocket, receiveSocket;


    public FireIncidentSubsystem(Scheduler scheduler) {
        this.scheduler = scheduler;
        this.tasks = new ArrayList<>();
    }

    public void run(){
        while(true){

            readInputFile();

            sendIncident();
            receiveUpdate();

            /*TODO*/
            /*

            scheduler.addRequest(tasks.remove(0));
            scheduler.takeResponse();
            */
        }


    }

    //read and store all incidents from input file, as a FireRequest list
    public void readInputFile() {
        //TODO: change the directory if needed
        String inputFile = "C:\\Users\\TinaC\\Downloads\\SYSC3303_project\\src\\fireincidents.txt";
        try (BufferedReader reader = new BufferedReader(new FileReader(inputFile))) {
            String line;

            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                String time = parts[0];
                int zoneId = Integer.parseInt(parts[1]);
                String eventType = parts[2];
                String severity = parts[3];

                FireRequest task = new FireRequest(time, zoneId, eventType, severity);
                tasks.add(task);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //prepare and send incident information to Scheduler
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

    //receive the update from the Scheduler
    private void receiveUpdate(){
        int serverPort = 9876;

        byte data[] = new byte[1000];

        try{
            receiveSocket = new DatagramSocket(serverPort);
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }

        receivePacket = new DatagramPacket(data, data.length);
        System.out.println("Receiving updates from Scheduler.");

        try{
            receiveSocket.receive(receivePacket);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }

        System.out.println("Packet information:");
        System.out.println("Host: " + receivePacket.getAddress());
        System.out.println("Port: " + receivePacket.getPort());

        int len = receivePacket.getLength();
        String receiveMessage = new String(data,0,len);
        System.out.println(receiveMessage);

    }


}