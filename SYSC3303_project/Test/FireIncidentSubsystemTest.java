import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

class FireIncidentSubsystemTest {
    private FireIncidentSubsystem fireSubsystem;
    private final String inputFile = "SYSC3303_project/src/fireincidents.txt";
    private final String zoneFile = "SYSC3303_project/src/zone_file.csv";

    @BeforeEach
    void setUp() {
        fireSubsystem = new FireIncidentSubsystem();
    }

    @AfterEach
    void tearDown() {
        if (fireSubsystem.getReceiveSocket() != null) {
            fireSubsystem.getReceiveSocket().close();
        }
        if (fireSubsystem.getSendSocket() != null) {
            fireSubsystem.getSendSocket().close();
        }
    }

    // Ensure fire incidents are read correctly into tasks
    @Test
    void testReadInputFile() {
        assertTrue(fireSubsystem.getTasks().isEmpty(), "Task list should be empty before reading");

        fireSubsystem.readInputFile(inputFile);
        assertFalse(fireSubsystem.getTasks().isEmpty(), "Task list should not be empty after reading");

        List<FireRequest> tasks = fireSubsystem.getTasks();
        assertEquals(3, tasks.size(), "Should have 3 fire incidents");

        assertEquals(new FireRequest("10-30-15", 7, "FIRE_DETECTED", "High").toString(), tasks.get(0).toString());
        assertEquals(new FireRequest("14-10-00", 3, "FIRE_DETECTED", "Moderate").toString(), tasks.get(1).toString());
        assertEquals(new FireRequest("14-16-03", 2, "FIRE_DETECTED", "Moderate").toString(), tasks.get(2).toString());
    }

    // Ensure zones are correctly parsed and stored
    @Test
    void testReadZoneFile() {
        FireIncidentSubsystem.zoneMap.clear();
        fireSubsystem.readZoneFile(zoneFile);

        assertFalse(FireIncidentSubsystem.zoneMap.isEmpty(), "Zone map should be populated");
        assertEquals(3, FireIncidentSubsystem.zoneMap.size(), "Should contain 3 zones");

        Zone zone7 = FireIncidentSubsystem.zoneMap.get(7);
        Zone zone3 = FireIncidentSubsystem.zoneMap.get(3);
        Zone zone2 = FireIncidentSubsystem.zoneMap.get(2);

        assertNotNull(zone7);
        assertEquals(0, zone7.getStartX());
        assertEquals(0, zone7.getStartY());
        assertEquals(700, zone7.getEndX());
        assertEquals(600, zone7.getEndY());

        assertNotNull(zone3);
        assertEquals(0, zone3.getStartX());
        assertEquals(600, zone3.getStartY());
        assertEquals(650, zone3.getEndX());
        assertEquals(1500, zone3.getEndY());

        assertNotNull(zone2);
        assertEquals(650, zone2.getStartX());
        assertEquals(1500, zone2.getStartY());
        assertEquals(800, zone2.getEndX());
        assertEquals(1800, zone2.getEndY());
    }

    // Ensure sendIncident() does not throw an exception
    @Test
    void testSendIncident() {
        String message = "FIRE_DATA_REQUEST";
        assertDoesNotThrow(() -> fireSubsystem.sendIncident(message), "sendIncident should not throw an exception");
    }

    // Ensure receiveUpdate() handles timeouts correctly
    @Test
    void testReceiveUpdateTimeout() {
        assertEquals("No updates available", fireSubsystem.receiveUpdate(), "Should return timeout message");
    }

    // Ensure getTasks() returns the expected tasks
    @Test
    void testGetTasks() {
        fireSubsystem.readInputFile(inputFile);
        assertEquals(3, fireSubsystem.getTasks().size(), "Task list should contain 3 incidents");
    }

    // Ensure getSendSocket() and getReceiveSocket() return valid sockets
    @Test
    void testGetSockets() {
        assertNotNull(fireSubsystem.getSendSocket(), "Send socket should be initialized");
        assertNotNull(fireSubsystem.getReceiveSocket(), "Receive socket should be initialized");
    }
}