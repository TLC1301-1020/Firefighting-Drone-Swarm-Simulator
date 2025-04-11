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
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Unit and system tests for the {@link Scheduler} class.
 * <p>
 * Verifies functionality including fire request assignment, drone registration,
 * socket communication setup, and zone file parsing.
 */
class SchedulerTest {
    /** instance of scheduler to be used for each test */
    private Scheduler scheduler;

    /** creates instance of scheduler to be used for each test */
    @BeforeEach
    void setUp() {
        scheduler = new Scheduler();
    }

    /**
        after each test function, calls Scheduler.shutdown() for graceful exit of 3 scheduler threads and their sockets */
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

    /** UNIT TEST: testing method isNewRequestMoreSevere to check which fire incident is more severe by severity type */
    @Test
    public void testNewRequestHasHigherSeverity() {
        FireRequest current = new FireRequest("12:00", 1, "FIRE_DETECTED", "Moderate", "1");
        FireRequest incoming = new FireRequest("12:01", 2, "FIRE_DETECTED", "High", "2");

        assertTrue(scheduler.isNewRequestMoreSevere(current, incoming));
    }

    /** UNIT TEST: testing method isNewRequestMoreSevere to check if equal severity fires are recognized by severity type */
    @Test
    public void testNewRequestHasEqualSeverity() {
        FireRequest current = new FireRequest("12:00", 1, "FIRE_DETECTED", "Moderate", "1");
        FireRequest incoming = new FireRequest("12:01", 2, "FIRE_DETECTED", "Moderate", "2");

        assertTrue(scheduler.isNewRequestMoreSevere(current, incoming));
    }

    /** UNIT TEST: testing method isNewRequestMoreSevere to check if lower severity fires are recognized by severity type */
    @Test
    public void testNewRequestHasLowerSeverity() {
        FireRequest current = new FireRequest("12:00", 1, "FIRE_DETECTED", "High", "1");
        FireRequest incoming = new FireRequest("12:01", 2, "FIRE_DETECTED", "Moderate", "2");

        assertFalse(scheduler.isNewRequestMoreSevere(current, incoming));
    }

    /** UNIT TEST: testing method isNewRequestMoreSevere to check if unknown severity fires are recognized by severity type and handling occurs */
    @Test
    public void testHandlesUnknownSeverityGracefully() {
        FireRequest current = new FireRequest("12:00", 1, "FIRE_DETECTED", "High", "1");
        FireRequest incoming = new FireRequest("12:01", 2, "FIRE_DETECTED", "Unknown", "2");

        assertFalse(scheduler.isNewRequestMoreSevere(current, incoming));
    }

    /** UNIT TEST: testing method isNewRequestMoreSevere to check if unknown severity fires are recognized by severity type and handling occurs for both */
    @Test
    public void testBothSeveritiesUnknown() {
        FireRequest current = new FireRequest("12:00", 1, "FIRE_DETECTED", "Blah", "1");
        FireRequest incoming = new FireRequest("12:01", 2, "FIRE_DETECTED", "Blah", "2");

        assertTrue(scheduler.isNewRequestMoreSevere(current, incoming)); // both default to 0 -> equal
    }

    /** SYSTEM TEST: testing method registerDrone + handleDroneRequest for DRONE_STUCK to check if scheduler correctly handles drone stuck event
     * and sets the drone offline and fire request is preserved for another drone to used  */
    @Test
    void testHandleDroneRequest_DRONE_STUCK_addsBackToQueueAndSetsOffline() throws Exception {
        int droneId = 1;
        FireRequest task = new FireRequest("FireRequest{time=14-16-03, zone=2, event=FIRE_DETECTED, severity=Moderate, id=2A}");

        scheduler.registerDrone(droneId);
        DroneStatus drone = scheduler.getDrones().get(droneId);
        drone.setState("[TRAVELING]");
        drone.setCurrentTask(task);

        String request = droneId + ":[ACTIVE][TRAVELING]:DRONE_STUCK:26:85:" + task;

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

    /** SYSTEM TEST: testing method registerDrone + handleDroneRequest for PAYLOAD_DEPLOY_FAILURE to check if scheduler correctly handles drone payload deploy failure event
     * and sets the drone offline and fire request is preserved for another drone to used  */
    @Test
    void testHandleDroneRequest_PAYLOAD_DEPLOY_FAILURE_addsBackToQueueAndSetsOffline() throws Exception {
        int droneId = 2;
        FireRequest task = new FireRequest("FireRequest{time=14-16-03, zone=2, event=FIRE_DETECTED, severity=Moderate, id=3A}");

        scheduler.registerDrone(droneId);
        DroneStatus drone = scheduler.getDrones().get(droneId);
        drone.setState("[DEPLOYING]");
        drone.setCurrentTask(task);

        String request = droneId + ":[ACTIVE][DEPLOYING]:PAYLOAD_DEPLOY_FAILURE:50:40:" + task;

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
     * SYSTEM TEST: sequential drone requests testing handleDroneRequest responses with testing coupled DroneStatus OBJ updates as requests come
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
     * SYSTEM TEST: sequential drone requests testing handleDroneRequest responses with testing coupled DroneStatus OBJ updates as requests come
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

        // create 2 competing fire requests
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
     * SYSTEM TEST: test that a traveling drone is not reassigned to a new fire request if that drone WON'T PASS THROUGH the zone of the same severity
     */
    @Test
    public void test_assignFireRequest_travelingDroneReassigned_byWontPassThrough() throws Exception {

        Method assignFireRequest = Scheduler.class.getDeclaredMethod("assignFireRequest", FireRequest.class);
        assignFireRequest.setAccessible(true);

        // create 2 fire requests (old and new)
        FireRequest oldRequest = new FireRequest("FireRequest{time=10-31-15, zone=3, event=FIRE_DETECTED, severity=Moderate, id=1A}");
        FireRequest newRequest = new FireRequest("FireRequest{time=10-30-00, zone=4, event=FIRE_DETECTED, severity=Moderate, id=2A}");

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
}

/**
 * Unit tests for the {@link SchedulerLogAnalyzer} class.
 * Verifies time conversion, duration calculations, and subsystem metric calculations
 */
class SchedulerLogAnalyzerTest
{

    /**
     * Tests conversion of LocalTime into seconds using {@code convertToTimeValue()}.
     */
    @Test
    public void testConvertToTimeValue_variousTimes() {
        LocalTime time = LocalTime.of(10, 0, 0, 0);
        assertEquals(36000.0, SchedulerLogAnalyzer.convertToTimeValue(time), 1e-9);

        time = LocalTime.of(0, 1, 30, 500_000_000);
        assertEquals(90.5, SchedulerLogAnalyzer.convertToTimeValue(time), 1e-9);

        time = LocalTime.of(23, 59, 59, 999_000_000);
        assertEquals(86399.999, SchedulerLogAnalyzer.convertToTimeValue(time), 1e-6);
    }

    /**
     * Tests duration calculation in various edge cases using {@code calculateTimeDuration()}:
     * including 0 duration, under a second precision, and time boundary carry over
     */
    @Test
    public void testCalculateTimeDuration_intricateCases() {
        // 1. Zero duration
        var entry1 = new SchedulerLogAnalyzer().new ParsedLogEntry(
                LocalTime.parse("10:00:00.000"), "INFO", "Scheduler", "SD", "Start"
        );
        var entry2 = new SchedulerLogAnalyzer().new ParsedLogEntry(
                LocalTime.parse("10:00:00.000"), "INFO", "Scheduler", "SD", "End"
        );
        assertEquals(0.0, SchedulerLogAnalyzer.calculateTimeDuration(entry1, entry2), 1e-9);

        // 2. Short millisecond precision
        entry1 = new SchedulerLogAnalyzer().new ParsedLogEntry(
                LocalTime.parse("10:00:00.100"), "INFO", "Scheduler", "SD", "Start"
        );
        entry2 = new SchedulerLogAnalyzer().new ParsedLogEntry(
                LocalTime.parse("10:00:00.250"), "INFO", "Scheduler", "SD", "End"
        );
        assertEquals(0.150, SchedulerLogAnalyzer.calculateTimeDuration(entry1, entry2), 1e-6);

        // 3. Across minute boundary
        entry1 = new SchedulerLogAnalyzer().new ParsedLogEntry(
                LocalTime.parse("10:00:59.999"), "INFO", "Scheduler", "SD", "Start"
        );
        entry2 = new SchedulerLogAnalyzer().new ParsedLogEntry(
                LocalTime.parse("10:01:00.001"), "INFO", "Scheduler", "SD", "End"
        );
        assertEquals(0.002, SchedulerLogAnalyzer.calculateTimeDuration(entry1, entry2), 1e-6);

        // 4. Across hour boundary
        entry1 = new SchedulerLogAnalyzer().new ParsedLogEntry(
                LocalTime.parse("09:59:59.999"), "INFO", "Scheduler", "SD", "Start"
        );
        entry2 = new SchedulerLogAnalyzer().new ParsedLogEntry(
                LocalTime.parse("10:00:00.001"), "INFO", "Scheduler", "SD", "End"
        );
        assertEquals(0.002, SchedulerLogAnalyzer.calculateTimeDuration(entry1, entry2), 1e-6);

        // 5. Full second plus milliseconds
        entry1 = new SchedulerLogAnalyzer().new ParsedLogEntry(
                LocalTime.parse("10:00:01.123"), "INFO", "Scheduler", "SD", "Start"
        );
        entry2 = new SchedulerLogAnalyzer().new ParsedLogEntry(
                LocalTime.parse("10:00:03.456"), "INFO", "Scheduler", "SD", "End"
        );
        assertEquals(2.333, SchedulerLogAnalyzer.calculateTimeDuration(entry1, entry2), 1e-3);
    }

    /**
     * Tests {@code calcGeneralMetrics()} for correct throughput computation based on
     * start time, end time, and first fire incident time
     */
    @Test
    public void testCalcGeneralMetrics() throws Exception {
        SchedulerLogAnalyzer analyzer = new SchedulerLogAnalyzer();

        var startLog = analyzer.new ParsedLogEntry(
                LocalTime.parse("10:00:00.000"), "INFO", "Scheduler", "MAIN", "Scheduler is now online"
        );
        var fireLog = analyzer.new ParsedLogEntry(
                LocalTime.parse("10:00:10.000"), "INFO", "Scheduler", "SF", "Received fire message: id=1"
        );
        var endLog = analyzer.new ParsedLogEntry(
                LocalTime.parse("10:00:40.000"), "INFO", "Scheduler", "MAIN", "All threads have finished. Log file ready for analysis."
        );

        // set countFireRequests = 3 using reflection
        Field countFireRequestsField = SchedulerLogAnalyzer.class.getDeclaredField("countFireRequests");
        countFireRequestsField.setAccessible(true);
        countFireRequestsField.setInt(analyzer, 3);

        // invoke calcGeneralMetrics()
        Method calcGeneralMetrics = SchedulerLogAnalyzer.class.getDeclaredMethod(
                "calcGeneralMetrics",
                SchedulerLogAnalyzer.ParsedLogEntry.class,
                SchedulerLogAnalyzer.ParsedLogEntry.class,
                SchedulerLogAnalyzer.ParsedLogEntry.class
        );
        calcGeneralMetrics.setAccessible(true);
        double throughput = (double) calcGeneralMetrics.invoke(analyzer, startLog, endLog, fireLog);

        assertEquals(0.1, throughput, 0.0001); // 3 requests / 30 seconds = 0.1 /s
    }

    /**
     * Tests {@code calcSPMetrics()} for Scheduler thread "SP":
     * verifies correct lifetime, busy time, utilization, and average response time calculation.
     */
    @Test
    public void testCalcSPMetrics() throws Exception {
        SchedulerLogAnalyzer analyzer = new SchedulerLogAnalyzer();

        // Prepare mock log entries for SP thread
        var firstSPLog = analyzer.new ParsedLogEntry(
                LocalTime.parse("10:00:00.000"), "INFO", "Scheduler", "SP", "Attempting assignment: id=1A"
        );
        var lastSPLog = analyzer.new ParsedLogEntry(
                LocalTime.parse("10:00:10.000"), "INFO", "Scheduler", "SP", "this fire request: id=1A"
        );

        // Set mock response time list
        Field rtbtField = SchedulerLogAnalyzer.class.getDeclaredField("responseTimesByThread");
        rtbtField.setAccessible(true);
        Map<String, List<Double>> rtbtMap = new HashMap<>();
        List<Double> rtbtSP = new ArrayList<>();
        rtbtSP.add(2.0);
        rtbtSP.add(3.0);
        rtbtMap.put("SP", rtbtSP);
        rtbtField.set(analyzer, rtbtMap);

        // Invoke calcSPMetrics via reflection
        Method method = SchedulerLogAnalyzer.class.getDeclaredMethod(
                "calcSPMetrics",
                SchedulerLogAnalyzer.ParsedLogEntry.class,
                SchedulerLogAnalyzer.ParsedLogEntry.class,
                Map.class
        );
        method.setAccessible(true);

        double[] result = (double[]) method.invoke(analyzer, firstSPLog, lastSPLog, new HashMap<>());

        assertEquals(10.0, result[0], 0.001);  // lifetime
        assertEquals(5.0, result[1], 0.001);   // busy time (2.0 + 3.0)
        assertEquals(0.5, result[2], 0.001);   // utilization
        assertEquals(2.5, result[3], 0.001);   // average response time
    }

    /**
     * Tests {@code calcSFMetrics()} for Scheduler thread "SF":
     * ensures metrics are computed correctly from parsed logs and response mappings.
     */
    @Test
    public void testCalcSFMetrics() throws Exception {
        SchedulerLogAnalyzer analyzer = new SchedulerLogAnalyzer();

        // First and last SF log entries
        var firstFireIncident = analyzer.new ParsedLogEntry(
                LocalTime.parse("10:00:00.000"), "INFO", "Scheduler", "SF", "Received fire message: id=1"
        );
        var lastSFLog = analyzer.new ParsedLogEntry(
                LocalTime.parse("10:00:10.000"), "INFO", "Scheduler", "SF", "Sending to FireIncident Subsystem..."
        );

        // FireRequest log mappings
        Map<Integer, SchedulerLogAnalyzer.ParsedLogEntry> firstSFMap = new HashMap<>();
        Map<Integer, SchedulerLogAnalyzer.ParsedLogEntry> lastSFMap = new HashMap<>();

        firstSFMap.put(1, analyzer.new ParsedLogEntry(
                LocalTime.parse("10:00:01.000"), "INFO", "Scheduler", "SF", "Received fire message: id=1"
        ));
        lastSFMap.put(1, analyzer.new ParsedLogEntry(
                LocalTime.parse("10:00:04.000"), "INFO", "Scheduler", "SF", "Scheduler acknowledged id=1"
        ));

        firstSFMap.put(2, analyzer.new ParsedLogEntry(
                LocalTime.parse("10:00:05.000"), "INFO", "Scheduler", "SF", "Received fire message: id=2"
        ));
        lastSFMap.put(2, analyzer.new ParsedLogEntry(
                LocalTime.parse("10:00:09.000"), "INFO", "Scheduler", "SF", "Scheduler acknowledged id=2"
        ));

        // Set mock response time list container
        Field rtbtField = SchedulerLogAnalyzer.class.getDeclaredField("responseTimesByThread");
        rtbtField.setAccessible(true);
        Map<String, List<Double>> rtbtMap = new HashMap<>();
        rtbtMap.put("SF", new ArrayList<>());
        rtbtField.set(analyzer, rtbtMap);

        // Invoke method
        Method method = SchedulerLogAnalyzer.class.getDeclaredMethod(
                "calcSFMetrics",
                SchedulerLogAnalyzer.ParsedLogEntry.class,
                SchedulerLogAnalyzer.ParsedLogEntry.class,
                Map.class,
                Map.class
        );
        method.setAccessible(true);

        double[] result = (double[]) method.invoke(analyzer, firstFireIncident, lastSFLog, firstSFMap, lastSFMap);

        assertEquals(10.0, result[0], 0.001); // lifetime: 00 to 10
        assertEquals(7.0, result[1], 0.001);  // busy time: (3 + 4)
        assertEquals(0.7, result[2], 0.001);  // utilization
        assertEquals(3.5, result[3], 0.001);  // avg response time
    }

    /**
     * Tests {@code calcSDMetrics()} for Scheduler thread "SD":
     * verifies accurate lifetime, busy time, utilization, and average response time.
     */
    @Test
    public void testCalcSDMetrics() throws Exception {
        SchedulerLogAnalyzer analyzer = new SchedulerLogAnalyzer();

        var firstSDLog = analyzer.new ParsedLogEntry(
                LocalTime.parse("10:00:00.000"), "DEBUG", "Scheduler", "SD", "Received drone message: 1"
        );
        var lastSDLog = analyzer.new ParsedLogEntry(
                LocalTime.parse("10:00:20.000"), "DEBUG", "Scheduler", "SD", "Responding to drone with: ACK"
        );

        // Set mock response time list for SD
        Field rtbtField = SchedulerLogAnalyzer.class.getDeclaredField("responseTimesByThread");
        rtbtField.setAccessible(true);
        Map<String, List<Double>> rtbtMap = new HashMap<>();
        List<Double> rtbtSD = new ArrayList<>();
        rtbtSD.add(5.0);
        rtbtSD.add(3.0);
        rtbtMap.put("SD", rtbtSD);
        rtbtField.set(analyzer, rtbtMap);

        // Invoke calcSDMetrics
        Method method = SchedulerLogAnalyzer.class.getDeclaredMethod(
                "calcSDMetrics",
                SchedulerLogAnalyzer.ParsedLogEntry.class,
                SchedulerLogAnalyzer.ParsedLogEntry.class
        );
        method.setAccessible(true);

        double[] result = (double[]) method.invoke(analyzer, firstSDLog, lastSDLog);

        assertEquals(20.0, result[0], 0.001); // lifetime
        assertEquals(8.0, result[1], 0.001);  // busy time (5 + 3)
        assertEquals(0.4, result[2], 0.001);  // utilization
        assertEquals(4.0, result[3], 0.001);  // average response time
    }
}