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
        assertTrue(tasks.size()>1, "Should have atleast 1 fire incidents");

        for ( FireRequest fr : tasks )
        {
            assertEquals( "FIRE_DETECTED", tasks.get(0).getEventType());
            assertNotNull( fr.getTime() );
            assertTrue( fr.getZoneId()>0 );
            assertTrue( fr.getSeverity().equals("Low") || fr.getSeverity().equals("Moderate") || fr.getSeverity().equals("High"));
            assertNotNull( fr.getTime() );
        }
    }

    // Ensure zones are correctly parsed and stored
    @Test
    void testReadZoneFile() {
        FireIncidentSubsystem.zoneMap.clear();
        fireSubsystem.readZoneFile(zoneFile);

        assertFalse(FireIncidentSubsystem.zoneMap.isEmpty(), "Zone map should be populated");
        assertEquals(5, FireIncidentSubsystem.zoneMap.size(), "Should contain 5 zones");

        Zone zone1 = FireIncidentSubsystem.zoneMap.get(1);
        Zone zone2 = FireIncidentSubsystem.zoneMap.get(2);
        Zone zone3 = FireIncidentSubsystem.zoneMap.get(3);
        Zone zone4 = FireIncidentSubsystem.zoneMap.get(4);
        Zone zone5 = FireIncidentSubsystem.zoneMap.get(5);

        assertNotNull(zone1);
        assertNotNull(zone2);
        assertNotNull(zone3);
        assertNotNull(zone4);
        assertNotNull(zone5);

        assertEquals(0, zone1.getStartX());
        assertEquals(0, zone1.getStartY());
        assertEquals(0, zone2.getStartX());
        assertEquals(600, zone2.getStartY());
        assertEquals(0, zone3.getStartX());
        assertEquals(1500, zone3.getStartY());
        assertEquals(700, zone4.getStartX());
        assertEquals(0, zone4.getStartY());
        assertEquals(650, zone5.getStartX());
        assertEquals(600, zone5.getStartY());

        assertEquals(700, zone1.getEndX());
        assertEquals(600, zone1.getEndY());
        assertEquals(650, zone2.getEndX());
        assertEquals(1500, zone2.getEndY());
        assertEquals(1000, zone3.getEndX());
        assertEquals(1700, zone3.getEndY());
        assertEquals(1000, zone4.getEndX());
        assertEquals(600, zone4.getEndY());
        assertEquals(1000, zone5.getEndX());
        assertEquals(1500, zone5.getEndY());
    }

    // Ensure sendIncident() does not throw an exception
    @Test
    void testSendIncident() {
        String message = "FIRE_DATA_REQUEST";
        assertDoesNotThrow(() -> fireSubsystem.sendIncident(message), "sendIncident should not throw an exception");
    }

    // Ensure getTasks() returns the expected tasks
    @Test
    void testGetTasks() {
        fireSubsystem.readInputFile(inputFile);
        assertTrue(fireSubsystem.getTasks().size()>1, "Task list should contain atleast 1 incident");
    }

    // Ensure getSendSocket() and getReceiveSocket() return valid sockets
    @Test
    void testGetSockets() {
        assertNotNull(fireSubsystem.getSendSocket(), "Send socket should be initialized");
        assertNotNull(fireSubsystem.getReceiveSocket(), "Receive socket should be initialized");
    }

    // Checks the order of the tasks, should be in timestamp order
    @Test
    void testAddTasksInOrder(){
        // FireIncidentSubsystem fis = new FireIncidentSubsystem();
        //the order should be req2,req1,req3
        FireRequest req1 = new FireRequest("10-30-15", 1, "Fire", "High", "1");
        FireRequest req2 = new FireRequest("10-15-45", 2, "Fire", "Low", "2");
        FireRequest req3 = new FireRequest("12-25-45", 2, "Fire", "Low", "3");
        fireSubsystem.addTask(req1);
        fireSubsystem.addTask(req2);
        fireSubsystem.addTask(req3);

        Assertions.assertEquals(req1,fireSubsystem.getTasks().get(1),"This should be the second request in the task list.");
        Assertions.assertEquals(req2,fireSubsystem.getTasks().get(0), "This should be the first request in the task list.");
        Assertions.assertEquals(req3,fireSubsystem.getTasks().get(2), "This should be the third request in the task list.");
    }

}

