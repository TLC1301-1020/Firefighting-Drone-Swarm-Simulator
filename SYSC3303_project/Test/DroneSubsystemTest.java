import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.*;
import java.net.DatagramSocket;
import java.util.ArrayList;
import java.util.List;

/**
 * Unit tests for the {@link DroneSubsystem} class
 * This class contains setup and teardown methods for testing the functionality of the DroneSubsystem
 * It ensures that the DroneSubsystem is initialized and cleaned up correctly before and after each test
 */
class DroneSubsystemTest {
    private DroneSubsystem droneSubsystem;
    private final String faultFile = "SYSC3303_project/src/Faults.txt";
    /**
     * Initializes a new instance of {@link DroneSubsystem} before each test
     */
    @BeforeEach
    void setUp() {
        droneSubsystem = new DroneSubsystem();
    }
    /**
     * Closes the receive and send sockets of {@link DroneSubsystem} after each test to clean up resources
     */
    @AfterEach
    void tearDown() {
        if (droneSubsystem.getReceiveSocket() != null) {
            droneSubsystem.getReceiveSocket().close();
        }
        if (droneSubsystem.getSendSocket() != null) {
            droneSubsystem.getSendSocket().close();
        }
    }
    /**
     * Test the {@link DroneSubsystem#addRequest(String)} and {@link DroneSubsystem#getRequest()} methods
     *
     * <p>This test ensures that requests are added to the subsystem and retrieved in the correct order.</p>
     */
    @Test
    void testAddAndRetrieveRequest() {
        String request1 = "1:[IDLE]:INIT:0:0:0";
        String request2 = "2:[TRAVELING]:NEW_FIRE_REQUEST:100:200:3";

        droneSubsystem.addRequest(request1);
        droneSubsystem.addRequest(request2);

        assertEquals(request1, droneSubsystem.getRequest());
        assertEquals(request2, droneSubsystem.getRequest());
    }

    /**
     * Test the {@link DroneSubsystem#addResponse(int, String)} and {@link DroneSubsystem#getResponse(int)} methods
     *
     * <p>This test ensures that responses are added and retrieved for the correct drone IDs.</p>
     */
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

    /**
     * Test proper closure of sockets in the {@link DroneSubsystem}
     *
     * <p>This test verifies that both the send and receive sockets are properly closed after calling {@link DatagramSocket#close()}.</p>
     */
    @Test
    void testSocketClosure() {
        DatagramSocket sendSocket = droneSubsystem.getSendSocket();
        DatagramSocket receiveSocket = droneSubsystem.getReceiveSocket();

        droneSubsystem.getReceiveSocket().close();
        droneSubsystem.getSendSocket().close();

        assertTrue(sendSocket.isClosed());
        assertTrue(receiveSocket.isClosed());
    }
    /**
     * Test the reading of fault data from a file
     *
     * <p>This test ensures that faults are correctly read from a file, and verifies that the fault list contains valid event types.</p>
     */
    @Test
    void testReadFaults(){
        assertTrue(droneSubsystem.getFaults().isEmpty(), "Fault list should be empty before reading.");

        droneSubsystem.readFaultFile(faultFile);
        assertFalse(droneSubsystem.getFaults().isEmpty(), "Fault list should not be empty after reading.");

        ArrayList<Event> faultList = droneSubsystem.getFaults();

        for ( Event e : faultList )
        {
            String event = (String) e.getEvent();
            assertTrue( event.contains("DRONE_STUCK") || event.contains("NOZZLE_JAMMED") || event.contains("PACKET_LOSS") );
        }
    }
}
