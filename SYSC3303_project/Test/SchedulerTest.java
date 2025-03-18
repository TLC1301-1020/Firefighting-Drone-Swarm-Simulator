import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SchedulerTest {
    private Scheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new Scheduler();
    }


    @Test
    void testParseZoneFile() {
        // Assuming a test file exists
        Scheduler.zoneMap.clear();
        scheduler.parseZoneFile("SYSC3303_project/src/zone_file.csv");
        assertFalse(Scheduler.zoneMap.isEmpty(), "Zone map should be populated");
    }

    @Test
    void testRegisterDrone() {
        String response = scheduler.registerDrone(1);
        assertEquals("ACK", response, "Drone should be registered successfully");
    }

    @Test
    void testSelectDrone() {
        FireRequest request = new FireRequest("3");
        int droneId = scheduler.selectDrone(request);
        assertEquals(-1, droneId, "Should return -1 when no drones available");
    }

//    @Test
//    void testFindClosestIdleDrone() {
//        Zone testZone = new Zone(1, 0, 0, 100, 100);
//        int droneId = scheduler.findClosestIdleDrone(testZone);
//        assertEquals(-1, droneId, "Should return -1 when no idle drones available");
//    }
//
//    @Test
//    void testWillPassThrough() {
//        DroneStatus drone = new DroneStatus(1);
//        boolean result = scheduler.willPassThrough(drone, 3);
//        assertFalse(result, "Drone should not pass through without zone data");
//    }
//
//    @Test
//    void testSetState() {
//        SchedulerState newState = new ProcessData(new ReceiveData());
//        scheduler.setState(newState);
//        assertEquals(newState, scheduler.getCurrentState(), "Scheduler state should update");
//    }
}