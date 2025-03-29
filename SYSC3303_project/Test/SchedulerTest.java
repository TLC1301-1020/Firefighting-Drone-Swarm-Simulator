//import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.DatagramSocket;
import java.util.HashMap;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

class SchedulerTest {
    private Scheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new Scheduler();
    }

    /**
        after each test function, calls Scheduler.shutdown() for gracefull exit of 3 scheduler threads */
    @AfterEach
    void tearDown() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        try
        {
            Thread.sleep(500);
        } catch (InterruptedException e) {
        }
        Method shutdown = Scheduler.class.getDeclaredMethod("shutdown");
        shutdown.setAccessible(true);
        shutdown.invoke(scheduler);
    }

    @Test
    public void testNewRequestHasHigherSeverity() {
        FireRequest current = new FireRequest("12:00", 1, "FIRE_DETECTED", "Moderate", "1");
        FireRequest incoming = new FireRequest("12:01", 2, "FIRE_DETECTED", "High", "2");

        assertTrue(scheduler.isNewRequestMoreSevere(current, incoming));
    }

    @Test
    public void testNewRequestHasEqualSeverity() {
        FireRequest current = new FireRequest("12:00", 1, "FIRE_DETECTED", "Moderate", "1");
        FireRequest incoming = new FireRequest("12:01", 2, "FIRE_DETECTED", "Moderate", "2");

        assertTrue(scheduler.isNewRequestMoreSevere(current, incoming));
    }

    @Test
    public void testNewRequestHasLowerSeverity() {
        FireRequest current = new FireRequest("12:00", 1, "FIRE_DETECTED", "High", "1");
        FireRequest incoming = new FireRequest("12:01", 2, "FIRE_DETECTED", "Moderate", "2");

        assertFalse(scheduler.isNewRequestMoreSevere(current, incoming));
    }

    @Test
    public void testHandlesUnknownSeverityGracefully() {
        FireRequest current = new FireRequest("12:00", 1, "FIRE_DETECTED", "High", "1");
        FireRequest incoming = new FireRequest("12:01", 2, "FIRE_DETECTED", "Unknown", "2");

        assertFalse(scheduler.isNewRequestMoreSevere(current, incoming));
    }

    @Test
    public void testBothSeveritiesUnknown() {
        FireRequest current = new FireRequest("12:00", 1, "FIRE_DETECTED", "Blah", "1");
        FireRequest incoming = new FireRequest("12:01", 2, "FIRE_DETECTED", "Blah", "2");

        assertTrue(scheduler.isNewRequestMoreSevere(current, incoming)); // both default to 0 -> equal
    }

    @Test
    void testHandleDroneRequest_DRONE_STUCK_addsBackToQueueAndSetsOffline() throws Exception {
        int droneId = 1;
        FireRequest task = new FireRequest("FireRequest{time=14-16-03, zone=2, event=FIRE_DETECTED, severity=Moderate, id=2A}");

        scheduler.registerDrone(droneId);
        DroneStatus drone = scheduler.getDrones().get(droneId);
        drone.setState("[TRAVELING]");
        drone.setCurrentTask(task);

        String request = droneId + ":[TRAVELING]:DRONE_STUCK:26:85:" + task;

        scheduler.handleDroneRequest(request);

        // Give the queue a chance to be updated
        Thread.sleep(50);

        // Access the private requestQueue field via reflection
        Field field = Scheduler.class.getDeclaredField("requestQueue");
        field.setAccessible(true);
        Queue<FireRequest> queue = (Queue<FireRequest>) field.get(scheduler);

        synchronized (queue) {
            boolean exists = queue.stream().anyMatch(req -> req.toString().equals(task.toString()));
            assertTrue(exists, "FireRequest should be re-added to the request queue");
        }

        assertEquals("[OFFLINE]", drone.getState(), "Drone should be marked as OFFLINE");
    }

    @Test
    void testHandleDroneRequest_PAYLOAD_DEPLOY_FAILURE_addsBackToQueueAndSetsOffline() throws Exception {
        int droneId = 2;
        FireRequest task = new FireRequest("FireRequest{time=14-16-03, zone=2, event=FIRE_DETECTED, severity=Moderate, id=3A}");

        scheduler.registerDrone(droneId);
        DroneStatus drone = scheduler.getDrones().get(droneId);
        drone.setState("[DEPLOYING]");
        drone.setCurrentTask(task);

        String request = droneId + ":[DEPLOYING]:PAYLOAD_DEPLOY_FAILURE:50:40:" + task;

        // Act
        scheduler.handleDroneRequest(request);

        // Give the queue a chance to be updated
        Thread.sleep(50);

        // Access the private requestQueue field via reflection
        Field field = Scheduler.class.getDeclaredField("requestQueue");
        field.setAccessible(true);
        Queue<FireRequest> queue = (Queue<FireRequest>) field.get(scheduler);

        synchronized (queue) {
            boolean exists = queue.stream().anyMatch(req -> req.toString().equals(task.toString()));
            assertTrue(exists, "FireRequest should be re-added to the request queue");
        }

        assertEquals("[OFFLINE]", drone.getState(), "Drone should be marked as OFFLINE");
    }

    /**
     *
     * UNIT TEST: sequential drone requests testing handleDroneRequest responses with testing coupled DroneStatus OBJ updates as requests come
     */
    @Test
    public void test_handleDroneRequest() throws InvocationTargetException, IllegalAccessException, NoSuchMethodException, NoSuchFieldException {
        Method handleDroneRequest = Scheduler.class.getDeclaredMethod("handleDroneRequest", String.class);
        handleDroneRequest.setAccessible(true);

        String req0 = "1:[IDLE]:INIT:0:0:0";

        String res0 = (String) handleDroneRequest.invoke(this.scheduler, req0);
        assertEquals("ACK:1:[IDLE]:INIT:0:0:0", res0);

        // check drone status obj created + its state
        Field drones = Scheduler.class.getDeclaredField("drones");
        drones.setAccessible(true);
        HashMap<Integer, DroneStatus> allDrones = (HashMap<Integer, DroneStatus>) drones.get(this.scheduler);
        DroneStatus drone = allDrones.get(1);

        // check default drone status object is in idle state when not assigned
        assertNotNull(drone);
        assertEquals("1:[IDLE]:0:0:FireRequest{time=0, zone=-1, event=0, severity=0, id=0}", drone.toString());
        assertTrue(drone.getCurrentTask().isDefault());


        // handle first waiting drone request
        String req1 = "1:[IDLE]:NEW_FIRE_REQUEST:0:0:FireRequest{time=0, zone=-1, event=0, severity=0, id=0}";

        String res1 = (String) handleDroneRequest.invoke(this.scheduler, req1);

        // check response from scheduler should be...
        assertEquals("WAIT:1:[IDLE]:NEW_FIRE_REQUEST:0:0:FireRequest{time=0, zone=-1, event=0, severity=0, id=0}", res1);     // ***
        // check drone status obj
        allDrones = (HashMap<Integer, DroneStatus>) drones.get(this.scheduler);
        drone = allDrones.get(1);
        assertEquals("1:[IDLE]:0:0:FireRequest{time=0, zone=-1, event=0, severity=0, id=0}", drone.toString());
        assertTrue(drone.getCurrentTask().isDefault());


        // handle followup waiting drone request
        String req2 = "1:[IDLE]:STATUS:0:0:FireRequest{time=0, zone=-1, event=0, severity=0, id=0}";
        String res2 = (String) handleDroneRequest.invoke(this.scheduler, req2);

        // check response from scheduler should be...
        assertEquals("ACK:1:[IDLE]:STATUS:0:0:FireRequest{time=0, zone=-1, event=0, severity=0, id=0}", res2);                // ***
        // check drone status obj
        allDrones = (HashMap<Integer, DroneStatus>) drones.get(this.scheduler);
        drone = allDrones.get(1);
        assertEquals("1:[IDLE]:0:0:FireRequest{time=0, zone=-1, event=0, severity=0, id=0}", drone.toString()); // <- DRONE STATUS DATA IS
        assertTrue(drone.getCurrentTask().isDefault());


        // handle new fire request on waiting drone request
        Field assignments = Scheduler.class.getDeclaredField("pendingAssignments");
        assignments.setAccessible(true);
        ConcurrentLinkedQueue<Scheduler.DroneAssignment> pendingAssignments = (ConcurrentLinkedQueue<Scheduler.DroneAssignment>) assignments.get(this.scheduler);
        pendingAssignments.add(new Scheduler.DroneAssignment(1, new FireRequest("FireRequest{time=10-30-15, zone=4, event=FIRE_DETECTED, severity=High, id=1A}")));

        String req3 = "1:[IDLE]:STATUS:0:0:FireRequest{time=0, zone=-1, event=0, severity=0, id=0}";
        String res3 = (String) handleDroneRequest.invoke(this.scheduler, req3);

        // check response from scheduler should be...
        assertEquals("NEW:1:[IDLE]:NEW_FIRE_REQUEST:0:0:FireRequest{time=10-30-15, zone=4, event=FIRE_DETECTED, severity=High, id=1A}", res3);                // ***
        // check drone status obj for the following coupled data: 1:[IDLE]:0:0:FireRequest{time=10-30-15, zone=4, event=FIRE_DETECTED, severity=High}
        allDrones = (HashMap<Integer, DroneStatus>) drones.get(this.scheduler);
        drone = allDrones.get(1);
        assertEquals("1:[IDLE]:0:0:FireRequest{time=10-30-15, zone=4, event=FIRE_DETECTED, severity=High, id=1A}", drone.toString()); // <- DRONE STATUS DATA IS
        assertFalse(drone.getCurrentTask().isDefault());
        assertEquals( "FireRequest{time=10-30-15, zone=4, event=FIRE_DETECTED, severity=High, id=1A}",drone.getCurrentTask().toString());


        // handle travel status update (no interrupt)
        String req4 = "1:[ACTIVE][TRAVELING]:STATUS:28:9:FireRequest{time=10-30-15, zone=4, event=FIRE_DETECTED, severity=High, id=1A}";
        String res4 = (String) handleDroneRequest.invoke(this.scheduler, req4);

        // check response from scheduler should be...
        assertEquals("ACK:1:[ACTIVE][TRAVELING]:STATUS:28:9:FireRequest{time=10-30-15, zone=4, event=FIRE_DETECTED, severity=High, id=1A}", res4);             // ***
        // check drone status obj for the following coupled data: 1:[ACTIVE][TRAVELING]:STATUS:28:9:FireRequest{time=10-30-15, zone=4, event=FIRE_DETECTED, severity=High}
        allDrones = (HashMap<Integer, DroneStatus>) drones.get(this.scheduler);
        drone = allDrones.get(1);
        assertEquals("1:[TRAVELING]:28:9:FireRequest{time=10-30-15, zone=4, event=FIRE_DETECTED, severity=High, id=1A}", drone.toString()); // <- DRONE STATUS DATA IS
        assertFalse(drone.getCurrentTask().isDefault());
        assertEquals( "FireRequest{time=10-30-15, zone=4, event=FIRE_DETECTED, severity=High, id=1A}",drone.getCurrentTask().toString());


        // handle drone arrive at zone
        String req5 = "1:[ACTIVE][TRAVELING]:PERMISSION_TO_DROP:850:300:FireRequest{time=10-30-15, zone=4, event=FIRE_DETECTED, severity=High, id=1A}";
        String res5 = (String) handleDroneRequest.invoke(this.scheduler, req5);

        // check response from scheduler should be...
        assertEquals("ACK:1:[ACTIVE][TRAVELING]:PERMISSION_TO_DROP:850:300:FireRequest{time=10-30-15, zone=4, event=FIRE_DETECTED, severity=High, id=1A}", res5);             // ***
        // check drone status obj for the following coupled data:
        allDrones = (HashMap<Integer, DroneStatus>) drones.get(this.scheduler);
        drone = allDrones.get(1);
        assertEquals("1:[TRAVELING]:850:300:FireRequest{time=10-30-15, zone=4, event=FIRE_DETECTED, severity=High, id=1A}", drone.toString()); // <- DRONE STATUS DATA IS
        assertFalse(drone.getCurrentTask().isDefault());
        assertEquals( "FireRequest{time=10-30-15, zone=4, event=FIRE_DETECTED, severity=High, id=1A}",drone.getCurrentTask().toString());


        // handle drone payload dropped
        String req6 = "1:[ACTIVE][DEPLOYING]:PAYLOAD_DROPPED:850:300:FireRequest{time=10-30-15, zone=4, event=FIRE_DETECTED, severity=High, id=1A}";
        String res6 = (String) handleDroneRequest.invoke(this.scheduler, req6);

        // check response from scheduler should be...
        assertEquals("ACK:1:[ACTIVE][DEPLOYING]:PAYLOAD_DROPPED:850:300:FireRequest{time=10-30-15, zone=4, event=FIRE_DETECTED, severity=High, id=1A}", res6);             // ***
        // check drone status obj for the following coupled data:
        allDrones = (HashMap<Integer, DroneStatus>) drones.get(this.scheduler);
        drone = allDrones.get(1);
        assertEquals("1:[DEPLOYING]:850:300:FireRequest{time=10-30-15, zone=4, event=FIRE_DETECTED, severity=High, id=1A}", drone.toString()); // <- DRONE STATUS DATA IS
        assertFalse(drone.getCurrentTask().isDefault());
        assertEquals( "FireRequest{time=10-30-15, zone=4, event=FIRE_DETECTED, severity=High, id=1A}",drone.getCurrentTask().toString());


        // handle drone return travel status
        String req7 = "1:[ACTIVE][RETURNING]:RETURN_STATUS:821:290:FireRequest{time=10-30-15, zone=4, event=FIRE_DETECTED, severity=High, id=1A}";
        String res7 = (String) handleDroneRequest.invoke(this.scheduler, req7);

        // check response from scheduler should be...
        assertEquals("ACK:1:[ACTIVE][RETURNING]:RETURN_STATUS:821:290:FireRequest{time=10-30-15, zone=4, event=FIRE_DETECTED, severity=High, id=1A}", res7);             // ***
        // check drone status obj for the following coupled data:
        allDrones = (HashMap<Integer, DroneStatus>) drones.get(this.scheduler);
        drone = allDrones.get(1);
        assertEquals("1:[RETURNING]:821:290:FireRequest{time=10-30-15, zone=4, event=FIRE_DETECTED, severity=High, id=1A}", drone.toString()); // <- DRONE STATUS DATA IS
        assertFalse(drone.getCurrentTask().isDefault());
        assertEquals( "FireRequest{time=10-30-15, zone=4, event=FIRE_DETECTED, severity=High, id=1A}",drone.getCurrentTask().toString());


        // handle drone returned to based
        String req8 = "1:[ACTIVE][RETURNING]:RETURNED_TO_BASE:0:0:FireRequest{time=10-30-15, zone=4, event=FIRE_DETECTED, severity=High, id=1A}";
        String res8 = (String) handleDroneRequest.invoke(this.scheduler, req8);

        // check response from scheduler should be...
        assertEquals("ACK:1:[ACTIVE][RETURNING]:RETURNED_TO_BASE:0:0:FireRequest{time=10-30-15, zone=4, event=FIRE_DETECTED, severity=High, id=1A}", res8);             // ***
        // check drone status obj for the following coupled data:
        allDrones = (HashMap<Integer, DroneStatus>) drones.get(this.scheduler);
        drone = allDrones.get(1);
        assertEquals("1:[RETURNING]:0:0:FireRequest{time=10-30-15, zone=4, event=FIRE_DETECTED, severity=High, id=1A}", drone.toString()); // <- DRONE STATUS DATA IS
        assertFalse(drone.getCurrentTask().isDefault());
        assertEquals( "FireRequest{time=10-30-15, zone=4, event=FIRE_DETECTED, severity=High, id=1A}",drone.getCurrentTask().toString());


        // handle drone refill
        String req9 = "1:[REFILLING]:REFILL_COMPLETE:0:0:FireRequest{time=10-30-15, zone=4, event=FIRE_DETECTED, severity=High, id=1A}";
        String res9 = (String) handleDroneRequest.invoke(this.scheduler, req9);

        // check response from scheduler should be...
        assertEquals("ACK:1:[REFILLING]:REFILL_COMPLETE:0:0:FireRequest{time=10-30-15, zone=4, event=FIRE_DETECTED, severity=High, id=1A}", res9);             // ***
        // check drone status obj for the following coupled data:
        allDrones = (HashMap<Integer, DroneStatus>) drones.get(this.scheduler);
        drone = allDrones.get(1);
        assertEquals("1:[REFILLING]:0:0:FireRequest{time=0, zone=-1, event=0, severity=0, id=0}", drone.toString()); // <- DRONE STATUS DATA IS
        assertTrue(drone.getCurrentTask().isDefault());
        assertEquals( "FireRequest{time=0, zone=-1, event=0, severity=0, id=0}",drone.getCurrentTask().toString());


        // handle drone return to IDLE
        String req10 = "1:[IDLE]:NEW_FIRE_REQUEST:0:0:FireRequest{time=0, zone=-1, event=0, severity=0, id=0}";
        String res10 = (String) handleDroneRequest.invoke(this.scheduler, req10);

        // check response from scheduler should be...
        assertEquals("WAIT:1:[IDLE]:NEW_FIRE_REQUEST:0:0:FireRequest{time=0, zone=-1, event=0, severity=0, id=0}", res10);             // ***
        // check drone status obj for the following coupled data:
        allDrones = (HashMap<Integer, DroneStatus>) drones.get(this.scheduler);
        drone = allDrones.get(1);
        assertEquals("1:[IDLE]:0:0:FireRequest{time=0, zone=-1, event=0, severity=0, id=0}", drone.toString()); // <- DRONE STATUS DATA IS
        assertTrue(drone.getCurrentTask().isDefault());
        assertEquals( "FireRequest{time=0, zone=-1, event=0, severity=0, id=0}",drone.getCurrentTask().toString());
    }

    /**
     *
     * UNIT TEST: sequential drone requests testing handleDroneRequest responses with testing coupled DroneStatus OBJ updates as requests come
     */
    @Test
    public void test_handleDroneRequest_with_InterruptReassignment() throws InvocationTargetException, IllegalAccessException, NoSuchMethodException, NoSuchFieldException {
        Method handleDroneRequest = Scheduler.class.getDeclaredMethod("handleDroneRequest", String.class);
        handleDroneRequest.setAccessible(true);

        String req0 = "1:[IDLE]:INIT:0:0:0";
        String res0 = (String) handleDroneRequest.invoke(this.scheduler, req0);
        assertEquals("ACK:1:[IDLE]:INIT:0:0:0", res0);

        // check drone status obj created + its state
        Field drones = Scheduler.class.getDeclaredField("drones");
        drones.setAccessible(true);
        HashMap<Integer, DroneStatus> allDrones = (HashMap<Integer, DroneStatus>) drones.get(this.scheduler);
        DroneStatus drone = allDrones.get(1);

        // check default drone status object is in idle state when not assigned
        assertNotNull(drone);
        assertEquals("1:[IDLE]:0:0:FireRequest{time=0, zone=-1, event=0, severity=0, id=0}", drone.toString());
        assertTrue(drone.getCurrentTask().isDefault());

        // handle first waiting drone request
        String req1 = "1:[IDLE]:NEW_FIRE_REQUEST:0:0:FireRequest{time=0, zone=-1, event=0, severity=0, id=0}";
        String res1 = (String) handleDroneRequest.invoke(this.scheduler, req1);
        // check response from scheduler should be...
        assertEquals("WAIT:1:[IDLE]:NEW_FIRE_REQUEST:0:0:FireRequest{time=0, zone=-1, event=0, severity=0, id=0}", res1);     // ***


        // handle new fire request on waiting drone request
        Field assignments = Scheduler.class.getDeclaredField("pendingAssignments");
        assignments.setAccessible(true);
        ConcurrentLinkedQueue<Scheduler.DroneAssignment> pendingAssignments = (ConcurrentLinkedQueue<Scheduler.DroneAssignment>) assignments.get(this.scheduler);
        pendingAssignments.add(new Scheduler.DroneAssignment(1, new FireRequest("FireRequest{time=10-30-15, zone=4, event=FIRE_DETECTED, severity=Low, id=1A}")));

        String req3 = "1:[IDLE]:STATUS:0:0:FireRequest{time=0, zone=-1, event=0, severity=0, id=0}";
        String res3 = (String) handleDroneRequest.invoke(this.scheduler, req3);

        // check response from scheduler should be...
        assertEquals("NEW:1:[IDLE]:NEW_FIRE_REQUEST:0:0:FireRequest{time=10-30-15, zone=4, event=FIRE_DETECTED, severity=Low, id=1A}", res3);                // ***
        // check drone status obj for the following coupled data: 1:[IDLE]:0:0:FireRequest{time=10-30-15, zone=4, event=FIRE_DETECTED, severity=High}
        allDrones = (HashMap<Integer, DroneStatus>) drones.get(this.scheduler);
        drone = allDrones.get(1);
        assertEquals("1:[IDLE]:0:0:FireRequest{time=10-30-15, zone=4, event=FIRE_DETECTED, severity=Low, id=1A}", drone.toString()); // <- DRONE STATUS DATA IS
        assertFalse(drone.getCurrentTask().isDefault());
        assertEquals( "FireRequest{time=10-30-15, zone=4, event=FIRE_DETECTED, severity=Low, id=1A}",drone.getCurrentTask().toString());


        // handle travel status update (no interrupt)
        String req4 = "1:[ACTIVE][TRAVELING]:STATUS:28:9:FireRequest{time=10-30-15, zone=4, event=FIRE_DETECTED, severity=Low, id=1A}";
        String res4 = (String) handleDroneRequest.invoke(this.scheduler, req4);

        // check response from scheduler should be...
        assertEquals("ACK:1:[ACTIVE][TRAVELING]:STATUS:28:9:FireRequest{time=10-30-15, zone=4, event=FIRE_DETECTED, severity=Low, id=1A}", res4);             // ***
        // check drone status obj for the following coupled data: 1:[ACTIVE][TRAVELING]:STATUS:28:9:FireRequest{time=10-30-15, zone=4, event=FIRE_DETECTED, severity=High}
        allDrones = (HashMap<Integer, DroneStatus>) drones.get(this.scheduler);
        drone = allDrones.get(1);
        assertEquals("1:[TRAVELING]:28:9:FireRequest{time=10-30-15, zone=4, event=FIRE_DETECTED, severity=Low, id=1A}", drone.toString()); // <- DRONE STATUS DATA IS
        assertFalse(drone.getCurrentTask().isDefault());
        assertEquals( "FireRequest{time=10-30-15, zone=4, event=FIRE_DETECTED, severity=Low, id=1A}",drone.getCurrentTask().toString());


        pendingAssignments = (ConcurrentLinkedQueue<Scheduler.DroneAssignment>) assignments.get(this.scheduler);
        pendingAssignments.add(new Scheduler.DroneAssignment(1, new FireRequest("FireRequest{time=10-31-15, zone=5, event=FIRE_DETECTED, severity=Moderate, id=2A}")));

        String req5 = "1:[ACTIVE][TRAVELING]:STATUS:50:25:FireRequest{time=10-30-15, zone=4, event=FIRE_DETECTED, severity=Low, id=1A}";
        String res5 = (String) handleDroneRequest.invoke(this.scheduler, req5);

        // check response from scheduler should be...
        assertEquals("NEW:1:[TRAVELING]:NEW_FIRE_REQUEST:50:25:FireRequest{time=10-31-15, zone=5, event=FIRE_DETECTED, severity=Moderate, id=2A}", res5);             // ***
        // check drone status obj for the following coupled data: 1:[ACTIVE][TRAVELING]:STATUS:28:9:FireRequest{time=10-30-15, zone=4, event=FIRE_DETECTED, severity=High}
        allDrones = (HashMap<Integer, DroneStatus>) drones.get(this.scheduler);
        drone = allDrones.get(1);
        assertEquals("1:[TRAVELING]:50:25:FireRequest{time=10-31-15, zone=5, event=FIRE_DETECTED, severity=Moderate, id=2A}", drone.toString()); // <- DRONE STATUS DATA IS
        assertFalse(drone.getCurrentTask().isDefault());
        assertEquals( "FireRequest{time=10-31-15, zone=5, event=FIRE_DETECTED, severity=Moderate, id=2A}",drone.getCurrentTask().toString());
    }


    /**
     * SYSTEM TEST: test that an idle drone can be assigned a new fire request (a new pendingAssignment) when there are no traveling drones
     */
    @Test
    public void test_assignFireRequest_idleDroneAssigned() throws NoSuchFieldException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        Method assignFireRequest = Scheduler.class.getDeclaredMethod("assignFireRequest", FireRequest.class);
        assignFireRequest.setAccessible(true);

        FireRequest request = new FireRequest("FireRequest{time=10-30-15, zone=4, event=FIRE_DETECTED, severity=Moderate}");

        // create drone in idle state
        Field dronesField = Scheduler.class.getDeclaredField("drones");
        dronesField.setAccessible(true);
        HashMap<Integer, DroneStatus> drones = (HashMap<Integer, DroneStatus>) dronesField.get(this.scheduler);

        DroneStatus idleDrone = new DroneStatus(1);
        idleDrone.setState("[IDLE]");
        drones.put(1, idleDrone);

        // invoke assignFireRequest
        boolean result = (boolean) assignFireRequest.invoke(this.scheduler, request);

        // check fire request added
        assertTrue(result);

        // check new request was not added to queue // check request queue is now empty
        Field requestQueueField = Scheduler.class.getDeclaredField("requestQueue");
        requestQueueField.setAccessible(true);
        Queue<FireRequest> requestQueue = (Queue<FireRequest>) requestQueueField.get(this.scheduler);
        assertFalse(requestQueue.contains(request));
        assertTrue(requestQueue.isEmpty());

        // check drone task is updated correctly
        DroneStatus updatedDrone = drones.get(1);
        assertEquals(request.toString(), updatedDrone.getCurrentTask().toString());

        // check assignment is in pendingAssignments for handleDroneRequest to take on handling
        Field pendingAssignmentsField = Scheduler.class.getDeclaredField("pendingAssignments");
        pendingAssignmentsField.setAccessible(true);
        ConcurrentLinkedQueue<Scheduler.DroneAssignment> pendingAssignments = (ConcurrentLinkedQueue<Scheduler.DroneAssignment>) pendingAssignmentsField.get(this.scheduler);

        assertFalse(pendingAssignments.isEmpty());
        Scheduler.DroneAssignment assignment = pendingAssignments.peek();
        assertEquals(1, assignment.droneId);
        assertEquals(request.toString(), assignment.request.toString());
    }


    /**
     * SYSTEM TEST: test that a traveling drone is reassigned to a new fire request if it is not is new request zone but is on the way to a further request zone
     */
    @Test
    public void test_assignFireRequest_travelingDroneReassigned() throws Exception {

        Method assignFireRequest = Scheduler.class.getDeclaredMethod("assignFireRequest", FireRequest.class);
        assignFireRequest.setAccessible(true);

        // create 2 competing firerequests
        FireRequest oldRequest = new FireRequest("FireRequest{time=10-30-00, zone=2, event=FIRE_DETECTED, severity=Low}");
        FireRequest newRequest = new FireRequest("FireRequest{time=10-31-15, zone=4, event=FIRE_DETECTED, severity=Moderate}");

        // make 2 zones to match
        Scheduler.zoneMap.put(2, new Zone(2, 100, 100, 200, 200));  // old zone
        Scheduler.zoneMap.put(4, new Zone(4, 50, 50, 150, 150));    // new zone

        // add drone in traveling state
        Field dronesField = Scheduler.class.getDeclaredField("drones");
        dronesField.setAccessible(true);
        HashMap<Integer, DroneStatus> drones = (HashMap<Integer, DroneStatus>) dronesField.get(this.scheduler);

        DroneStatus travelingDrone = new DroneStatus(1);
        travelingDrone.setState("[TRAVELING]");
        travelingDrone.setCurrentTask(oldRequest);
        travelingDrone.setLocation(20, 20); // in neither zones
        drones.put(1, travelingDrone);

        // invoke assignFireRequest
        boolean result = (boolean) assignFireRequest.invoke(this.scheduler, newRequest);

        // check fireRequest was reassigned/assigned to a drone
        assertTrue(result);

        // check new request was not added to queue // check old fire request was stored in request queue
        Field requestQueueField = Scheduler.class.getDeclaredField("requestQueue");
        requestQueueField.setAccessible(true);
        Queue<FireRequest> requestQueue = (Queue<FireRequest>) requestQueueField.get(this.scheduler);
        assertFalse(requestQueue.contains(newRequest));
        assertTrue(requestQueue.contains(oldRequest));

        // check drone task is the new task
        DroneStatus updatedDrone = drones.get(1);
        assertEquals(newRequest.toString(), updatedDrone.getCurrentTask().toString());

        // check that the followup assignment for the old fire request was not given again to the drone that was reassigned (no new assignment made for that drone)
        Field pendingAssignmentsField = Scheduler.class.getDeclaredField("pendingAssignments");
        pendingAssignmentsField.setAccessible(true);
        ConcurrentLinkedQueue<Scheduler.DroneAssignment> pendingAssignments = (ConcurrentLinkedQueue<Scheduler.DroneAssignment>) pendingAssignmentsField.get(this.scheduler);

        assertFalse(pendingAssignments.isEmpty());
        Scheduler.DroneAssignment assignment = pendingAssignments.peek();
        assertEquals(1, assignment.droneId);
        assertEquals(newRequest.toString(), assignment.request.toString());
    }

    /**
     * SYSTEM TEST: test that a traveling drone is reassigned to a new fire request that is already in the NEW REQUEST zone
     */
    @Test
    public void test_assignFireRequest_travelingDroneReassigned_inZone() throws Exception {

        Method assignFireRequest = Scheduler.class.getDeclaredMethod("assignFireRequest", FireRequest.class);
        assignFireRequest.setAccessible(true);

        // create 2 fire requests (old and new)
        FireRequest oldRequest = new FireRequest("FireRequest{time=10-30-00, zone=3, event=FIRE_DETECTED, severity=Low}");
        FireRequest newRequest = new FireRequest("FireRequest{time=10-31-15, zone=1, event=FIRE_DETECTED, severity=Moderate}");

        // define 4 touching zones
        Scheduler.zoneMap.put(1, new Zone(1, 0, 0, 50, 50));     // zone1
        Scheduler.zoneMap.put(2, new Zone(2, 0, 50, 50, 100));   // left middle
        Scheduler.zoneMap.put(3, new Zone(3, 50, 50, 100, 100)); // zone3
        Scheduler.zoneMap.put(4, new Zone(4, 50, 0, 100, 50));   // right middle

        // add a drone currently traveling to zone3 but is in zone1
        Field dronesField = Scheduler.class.getDeclaredField("drones");
        dronesField.setAccessible(true);
        HashMap<Integer, DroneStatus> drones = (HashMap<Integer, DroneStatus>) dronesField.get(this.scheduler);

        DroneStatus travelingDrone = new DroneStatus(1);
        travelingDrone.setState("[TRAVELING]");
        travelingDrone.setCurrentTask(oldRequest);
        travelingDrone.setLocation(25, 25); // inside zone1 (on path to zone3)
        drones.put(1, travelingDrone);

        // invoke assignFireRequest
        boolean result = (boolean) assignFireRequest.invoke(this.scheduler, newRequest);

        // check fireRequest was reassigned to a drone
        assertTrue(result);

        // check new request was not added to queue // check old fire request was stored in request queue
        Field requestQueueField = Scheduler.class.getDeclaredField("requestQueue");
        requestQueueField.setAccessible(true);
        Queue<FireRequest> requestQueue = (Queue<FireRequest>) requestQueueField.get(this.scheduler);
        assertFalse(requestQueue.contains(newRequest));
        assertTrue(requestQueue.contains(oldRequest));

        // check drone now has the new task
        DroneStatus updatedDrone = drones.get(1);
        assertEquals(newRequest.toString(), updatedDrone.getCurrentTask().toString());

        // check that the assignment was placed in pendingAssignments to be handled (removed + assigned) when handleDroneRequest is called
        Field pendingAssignmentsField = Scheduler.class.getDeclaredField("pendingAssignments");
        pendingAssignmentsField.setAccessible(true);
        ConcurrentLinkedQueue<Scheduler.DroneAssignment> pendingAssignments = (ConcurrentLinkedQueue<Scheduler.DroneAssignment>) pendingAssignmentsField.get(this.scheduler);

        assertFalse(pendingAssignments.isEmpty());
        Scheduler.DroneAssignment assignment = pendingAssignments.peek();
        assertEquals(1, assignment.droneId);
        assertEquals(newRequest.toString(), assignment.request.toString());
    }

    /**
     * SYSTEM TEST: test that a traveling drone is not reassigned to a new fire request if that drone is already in the zone to drop its current request payload.
     */
    @Test
    public void test_assignFireRequest_travelingDroneInvalidReassigned_alreadyInZone() throws Exception {

        Method assignFireRequest = Scheduler.class.getDeclaredMethod("assignFireRequest", FireRequest.class);
        assignFireRequest.setAccessible(true);

        // create 2 fire requests (old and new)
        FireRequest oldRequest = new FireRequest("FireRequest{time=10-30-00, zone=1, event=FIRE_DETECTED, severity=Moderate}");
        FireRequest newRequest = new FireRequest("FireRequest{time=10-31-15, zone=3, event=FIRE_DETECTED, severity=Moderate}");

        // define 4 touching zones
        Scheduler.zoneMap.put(1, new Zone(1, 0, 0, 50, 50));     // zone1
        Scheduler.zoneMap.put(2, new Zone(2, 0, 50, 50, 100));   // left middle
        Scheduler.zoneMap.put(3, new Zone(3, 50, 50, 100, 100)); // zone3
        Scheduler.zoneMap.put(4, new Zone(4, 50, 0, 100, 50));   // right middle

        // add a drone currently traveling to zone3 but is in zone1
        Field dronesField = Scheduler.class.getDeclaredField("drones");
        dronesField.setAccessible(true);
        HashMap<Integer, DroneStatus> drones = (HashMap<Integer, DroneStatus>) dronesField.get(this.scheduler);

        DroneStatus travelingDrone = new DroneStatus(1);
        travelingDrone.setState("[TRAVELING]");
        travelingDrone.setCurrentTask(oldRequest);
        travelingDrone.setLocation(24, 24); // inside zone1 (on path to zone3)
        drones.put(1, travelingDrone);

        // add new request for zone1
        Field requestQueueField = Scheduler.class.getDeclaredField("requestQueue");
        requestQueueField.setAccessible(true);
        Queue<FireRequest> requestQueue = (Queue<FireRequest>) requestQueueField.get(this.scheduler);
        requestQueue.offer(newRequest);

        // invoke assignFireRequest
        boolean result = (boolean) assignFireRequest.invoke(this.scheduler, newRequest);

        // check fireRequest was reassigned to a drone
        assertFalse(result);

        // check new request was not removed from queue
        assertTrue(requestQueue.contains(newRequest));

        // check drone now has the old task
        DroneStatus updatedDrone = drones.get(1);
        assertEquals(oldRequest.toString(), updatedDrone.getCurrentTask().toString());

        // check new request was re-added (not removed) to queue
        requestQueue = (Queue<FireRequest>) requestQueueField.get(this.scheduler);
        FireRequest remaining = requestQueue.peek();
        assertTrue(remaining.equals(newRequest));

        // check that the assignment was not placed in pendingAssignments and pending assignments is empty
        Field pendingAssignmentsField = Scheduler.class.getDeclaredField("pendingAssignments");
        pendingAssignmentsField.setAccessible(true);
        ConcurrentLinkedQueue<Scheduler.DroneAssignment> pendingAssignments = (ConcurrentLinkedQueue<Scheduler.DroneAssignment>) pendingAssignmentsField.get(this.scheduler);
        assertTrue(pendingAssignments.isEmpty());
    }

    /**
     * SYSTEM TEST: test that a traveling drone is reassigned to a new fire request if that drone WILL PASS THROUGH the zone of the same severity
     */
    @Test
    public void test_assignFireRequest_travelingDroneReassigned_byWillPassThrough() throws Exception {

        Method assignFireRequest = Scheduler.class.getDeclaredMethod("assignFireRequest", FireRequest.class);
        assignFireRequest.setAccessible(true);

        // create 2 fire requests (old and new)
        FireRequest oldRequest = new FireRequest("FireRequest{time=10-30-00, zone=4, event=FIRE_DETECTED, severity=Moderate, id=1A}");
        FireRequest newRequest = new FireRequest("FireRequest{time=10-31-15, zone=3, event=FIRE_DETECTED, severity=Moderate, id=2A}");

        // define 4 touching zones
        Scheduler.zoneMap.put(1, new Zone(1, 0, 0, 50, 50));     // zone1
        Scheduler.zoneMap.put(2, new Zone(2, 0, 50, 50, 100));   // left middle
        Scheduler.zoneMap.put(3, new Zone(3, 50, 50, 100, 100)); // zone3
        Scheduler.zoneMap.put(4, new Zone(4, 100, 100, 150, 150)); // zone3

        // add a drone currently traveling to zone3 but is in zone1
        Field dronesField = Scheduler.class.getDeclaredField("drones");
        dronesField.setAccessible(true);
        HashMap<Integer, DroneStatus> drones = (HashMap<Integer, DroneStatus>) dronesField.get(this.scheduler);

        DroneStatus travelingDrone = new DroneStatus(1);
        travelingDrone.setState("[TRAVELING]");
        travelingDrone.setCurrentTask(oldRequest);
        travelingDrone.setLocation(24, 24); // inside zone1 (on path to zone3)
        drones.put(1, travelingDrone);

        // invoke assignFireRequest
        boolean result = (boolean) assignFireRequest.invoke(this.scheduler, newRequest);

        // check fireRequest was reassigned to a drone
        assertTrue(result);

        // check drone now has the new task
        DroneStatus updatedDrone = drones.get(1);
        assertEquals(newRequest.toString(), updatedDrone.getCurrentTask().toString());

        // check new request was not added to queue // check old fire request was stored in request queue
        Field requestQueueField = Scheduler.class.getDeclaredField("requestQueue");
        requestQueueField.setAccessible(true);
        Queue<FireRequest> requestQueue = (Queue<FireRequest>) requestQueueField.get(this.scheduler);
        assertFalse(requestQueue.contains(newRequest));
        assertTrue(requestQueue.contains(oldRequest));

        // check that the assignment was placed in pendingAssignments to be handled (removed + assigned) when handleDroneRequest is called
        Field pendingAssignmentsField = Scheduler.class.getDeclaredField("pendingAssignments");
        pendingAssignmentsField.setAccessible(true);
        ConcurrentLinkedQueue<Scheduler.DroneAssignment> pendingAssignments = (ConcurrentLinkedQueue<Scheduler.DroneAssignment>) pendingAssignmentsField.get(this.scheduler);
        assertFalse(pendingAssignments.isEmpty());
        Scheduler.DroneAssignment da = pendingAssignments.poll();

        assertEquals(1, da.droneId);
        assertEquals( "FireRequest{time=10-31-15, zone=3, event=FIRE_DETECTED, severity=Moderate, id=2A}", da.request.toString());
    }


    /**
     * SYSTEM TEST: test that a traveling drone is not reassigned to a new fire request if that drone WONT PASS THROUGH the zone of the same severity
     */
    @Test
    public void test_assignFireRequest_travelingDroneReassigned_byWontPassThrough() throws Exception {

        Method assignFireRequest = Scheduler.class.getDeclaredMethod("assignFireRequest", FireRequest.class);
        assignFireRequest.setAccessible(true);

        // create 2 fire requests (old and new)
        FireRequest oldRequest = new FireRequest("FireRequest{time=10-31-15, zone=3, event=FIRE_DETECTED, severity=Moderate}");
        FireRequest newRequest = new FireRequest("FireRequest{time=10-30-00, zone=4, event=FIRE_DETECTED, severity=Moderate}");

        // define 4 touching zones
        Scheduler.zoneMap.put(1, new Zone(1, 0, 0, 50, 50));     // zone1
        Scheduler.zoneMap.put(2, new Zone(2, 0, 50, 50, 100));   // left middle
        Scheduler.zoneMap.put(3, new Zone(3, 50, 50, 100, 100)); // zone3
        Scheduler.zoneMap.put(4, new Zone(4, 100, 100, 150, 150)); // zone3

        // add a drone currently traveling to zone3 but is in zone1
        Field dronesField = Scheduler.class.getDeclaredField("drones");
        dronesField.setAccessible(true);
        HashMap<Integer, DroneStatus> drones = (HashMap<Integer, DroneStatus>) dronesField.get(this.scheduler);

        DroneStatus travelingDrone = new DroneStatus(1);
        travelingDrone.setState("[TRAVELING]");
        travelingDrone.setCurrentTask(oldRequest);
        travelingDrone.setLocation(24, 24); // inside zone1 (on path to zone3)
        drones.put(1, travelingDrone);

        // add new request for zone1
        Field requestQueueField = Scheduler.class.getDeclaredField("requestQueue");
        requestQueueField.setAccessible(true);
        Queue<FireRequest> requestQueue = (Queue<FireRequest>) requestQueueField.get(this.scheduler);
        requestQueue.offer(newRequest);

        // invoke assignFireRequest
        boolean result = (boolean) assignFireRequest.invoke(this.scheduler, newRequest);

        // check fireRequest was not reassigned to a drone
        assertFalse(result);

        // check drone now has the old task
        DroneStatus updatedDrone = drones.get(1);
        assertEquals(oldRequest.toString(), updatedDrone.getCurrentTask().toString());

        // check new request was re-added to queue
        requestQueue = (Queue<FireRequest>) requestQueueField.get(this.scheduler);
        FireRequest remaining = requestQueue.peek();
        assertTrue(remaining.equals(newRequest));

        // check that the assignment was not placed in pendingAssignments to so when handleDroneRequest is called nothing is  (removed + assigned)
        Field pendingAssignmentsField = Scheduler.class.getDeclaredField("pendingAssignments");
        pendingAssignmentsField.setAccessible(true);
        ConcurrentLinkedQueue<Scheduler.DroneAssignment> pendingAssignments = (ConcurrentLinkedQueue<Scheduler.DroneAssignment>) pendingAssignmentsField.get(this.scheduler);
        assertTrue(pendingAssignments.isEmpty());
    }


    /**
     * SYSTEM TEST: test that makeFireRequests() correctly generates multiple FireRequest objects
     * for each severity level: Low (1), Moderate (2), High (3).
     */
    @Test
    public void test_makeFireRequests_generatesCorrectCount() throws Exception {

        // Access the private makeFireRequests method
        Method makeFireRequests = Scheduler.class.getDeclaredMethod("makeFireRequests", String.class);
        makeFireRequests.setAccessible(true);

        // Prepare test inputs
        String requestLow = "FireRequest{time=12:00, zone=1, event=FIRE_DETECTED, severity=Low, id=1}";
        String requestModerate = "FireRequest{time=12:01, zone=2, event=FIRE_DETECTED, severity=Moderate, id=2}";
        String requestHigh = "FireRequest{time=12:02, zone=3, event=FIRE_DETECTED, severity=High, id=3}";

        // Invoke and assert LOW (should create 1)
        Queue<FireRequest> lowQueue = (Queue<FireRequest>) makeFireRequests.invoke(scheduler, requestLow);
        assertEquals(1, lowQueue.size());
        assertTrue(lowQueue.stream().allMatch(fr -> fr.getSeverity().equals("Low")));
        // Check sub-IDs are appended correctly (optional)
        String[] expectedIds1 = {"1A"};
        int i = 0;
        for (FireRequest fr : lowQueue) {
            System.out.println(fr);
            assertEquals(expectedIds1[i++], fr.getId());
        }

        // Invoke and assert MODERATE (should create 2)
        Queue<FireRequest> moderateQueue = (Queue<FireRequest>) makeFireRequests.invoke(scheduler, requestModerate);
        assertEquals(2, moderateQueue.size());
        assertTrue(moderateQueue.stream().allMatch(fr -> fr.getSeverity().equals("Moderate")));

        // Check sub-IDs are appended correctly (optional)
        String[] expectedIds2 = {"2A", "2B"};
        i = 0;
        for (FireRequest fr : moderateQueue) {
            System.out.println(fr);
            assertEquals(expectedIds2[i++], fr.getId());
        }

        // Invoke and assert HIGH (should create 3)
        Queue<FireRequest> highQueue = (Queue<FireRequest>) makeFireRequests.invoke(scheduler, requestHigh);
        assertEquals(3, highQueue.size());
        assertTrue(highQueue.stream().allMatch(fr -> fr.getSeverity().equals("High")));

        // Check sub-IDs are appended correctly (optional)
        String[] expectedIds3 = {"3A", "3B", "3C"};
        i = 0;
        for (FireRequest fr : highQueue) {
            System.out.println(fr);
            assertEquals(expectedIds3[i++], fr.getId());
        }
    }

//    @Test
//    void testParseZoneFile() {
//        // Assuming a test file exists
//        Scheduler.zoneMap.clear();
//        scheduler.parseZoneFile("SYSC3303_project/src/zone_file.csv");
//        assertFalse(Scheduler.zoneMap.isEmpty(), "Zone map should be populated");
//    }
//
//    @Test
//    void testRegisterDrone() {
//        String response = scheduler.registerDrone(1);
//        assertEquals("ACK", response, "Drone should be registered successfully");
//    }
//
//    @Test
//    void testSelectDrone() {
//        FireRequest request = new FireRequest("3");
//        int droneId = scheduler.selectDrone(request);
//        assertEquals(-1, droneId, "Should return -1 when no drones available");
//    }

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