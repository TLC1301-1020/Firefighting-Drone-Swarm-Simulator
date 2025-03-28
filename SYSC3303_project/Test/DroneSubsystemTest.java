import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.*;
import java.net.DatagramSocket;
import java.util.ArrayList;
import java.util.List;


class DroneSubsystemTest {
    private DroneSubsystem droneSubsystem;
    private final String faultFile = "SYSC3303_project/src/Faults.txt";

    @BeforeEach
    void setUp() {
        droneSubsystem = new DroneSubsystem();
    }

    @AfterEach
    void tearDown() {
        if (droneSubsystem.getReceiveSocket() != null) {
            droneSubsystem.getReceiveSocket().close();
        }
        if (droneSubsystem.getSendSocket() != null) {
            droneSubsystem.getSendSocket().close();
        }
    }

    // Test that requests are added and retrieved properly
    @Test
    void testAddAndRetrieveRequest() {
        String request1 = "1:[IDLE]:INIT:0:0:0";
        String request2 = "2:[TRAVELING]:NEW_FIRE_REQUEST:100:200:3";

        droneSubsystem.addRequest(request1);
        droneSubsystem.addRequest(request2);

        assertEquals(request1, droneSubsystem.getRequest());
        assertEquals(request2, droneSubsystem.getRequest());
    }

    // Test that responses are added and retrieved properly
    @Test
    void testAddAndRetrieveResponse() {
        int droneId1 = 1, droneId2 = 2;
        String response1 = "ACK:1:REGISTERED";
        String response2 = "NEW:2:TRAVELING:NEW_FIRE_REQUEST:100:200:3";

        droneSubsystem.addResponse(droneId1, response1);
        droneSubsystem.addResponse(droneId2, response2);

        assertEquals(response1, droneSubsystem.getResponse(droneId1));
        assertEquals(response2, droneSubsystem.getResponse(droneId2));
    }

    // Test proper closing of sockets
    @Test
    void testSocketClosure() {
        DatagramSocket sendSocket = droneSubsystem.getSendSocket();
        DatagramSocket receiveSocket = droneSubsystem.getReceiveSocket();

        droneSubsystem.getReceiveSocket().close();
        droneSubsystem.getSendSocket().close();

        assertTrue(sendSocket.isClosed());
        assertTrue(receiveSocket.isClosed());
    }
    @Test
    void testReadFaults(){
        assertTrue(droneSubsystem.getFaults().isEmpty(), "Fault list should be empty before reading.");

        droneSubsystem.readFaultFile(faultFile);
        assertFalse(droneSubsystem.getFaults().isEmpty(), "Fault list should not be empty after reading.");

        ArrayList<Event> faultList = droneSubsystem.getFaults();
        assertEquals(3, faultList.size(), "Should have 2 faults in the list.");

        Event event1 = new Event("DRONE_STUCK:0","10-30-15");
        Event event2 = new Event("NOZZLE_JAMMED:0","10-30-55");
        Event event3 = new Event("PACKET_LOSS:0","10-31-55");
        assertEquals(event1.getEventTime(),droneSubsystem.getFaults().get(0).getEventTime(), "The time of two events should be the same.");
        assertEquals(event2.getEventTime(),droneSubsystem.getFaults().get(1).getEventTime(), "The time of two events should be the same.");
        assertEquals(event3.getEventTime(),droneSubsystem.getFaults().get(2).getEventTime(), "The time of two events should be the same.");

        assertEquals(event1.getEvent(),droneSubsystem.getFaults().get(0).getEvent(),"The type of fault of two events should be the same.");
        assertEquals(event2.getEvent(),droneSubsystem.getFaults().get(1).getEvent(),"The type of fault of two events should be the same.");
        assertEquals(event3.getEvent(),droneSubsystem.getFaults().get(2).getEvent(),"The type of fault of two events should be the same.");
    }
}
