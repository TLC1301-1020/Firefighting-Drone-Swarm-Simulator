import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.*;
import java.net.DatagramSocket;


class DroneSubsystemTest {
    private DroneSubsystem droneSubsystem;

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
}
