import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class FireIncidentSubsystem extends Thread {
    private LocalTime time;
    private int zoneId;
    private String eventType;
    private String severity;
    // Lock for synchronization
    private static final Object lock = new Object();

    public FireIncidentSubsystem(LocalTime time, int zoneId, String eventType, String severity) {
        this.time = time;
        this.zoneId = zoneId;
        this.eventType = eventType;
        this.severity = severity;
    }

    @Override
    public String toString() {
        return String.format("%s, %d, %s, %s", time, zoneId, eventType, severity);
    }

    //read an incident from a file line
    public static FireIncidentSubsystem readIncidentFromFile(String line) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        try {
            String[] parts = line.split(",\\s*");
            LocalTime time = LocalTime.parse(parts[0], formatter);
            int zoneId = Integer.parseInt(parts[1]);
            String eventType = parts[2];
            String severity = parts[3];

            return new FireIncidentSubsystem(time, zoneId, eventType, severity);
        } catch (Exception e) {
            System.out.println("Error parsing line: " + line);
            return null;
        }
    }

    //send the incident to the Scheduler
    public void sendIncident(String serverAddress, int serverPort) {
        try (DatagramSocket socket = new DatagramSocket()) {
            InetAddress serverAddr = InetAddress.getByName(serverAddress);
            byte[] sendData = this.toString().getBytes();
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddr, serverPort);
            socket.send(sendPacket);
            System.out.println("Sent incident: " + this.toString());
        } catch (IOException e) {
            System.out.println("Error sending UDP packet: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        //TODO: change this to input file dir
        String inputFile = "C:\\Users\\TinaC\\Downloads\\SYSC3303_project\\src\\fireincidents.txt";
        String serverAddress = "localhost";
        int serverPort = 9876;

        //TODO: sending message with synchronized()
//        try (BufferedReader br = new BufferedReader(new FileReader(inputFile))) {
//            String incidentLine;
//            while ((incidentLine = br.readLine()) != null) {
//                FireIncidentSubsystem incident = FireIncidentSubsystem.readIncidentFromFile(incidentLine);
//                if (incident != null) {
//                    synchronized (lock) {
//                        incident.sendIncident(serverAddress,serverPort);
//                        lock.wait();
//                    }
//                }
//            }
//        } catch (IOException | InterruptedException e) {
//            System.out.println("Error: " + e.getMessage());
//        }
//    }

        try (BufferedReader br = new BufferedReader(new FileReader(inputFile))) {
            String incidentLine;
            while ((incidentLine = br.readLine()) != null) {
                FireIncidentSubsystem incident = FireIncidentSubsystem.readIncidentFromFile(incidentLine);
                if (incident != null) {
                    incident.sendIncident(serverAddress,serverPort);
                }
            }
        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

}