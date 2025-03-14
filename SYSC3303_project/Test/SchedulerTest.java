//import org.junit.jupiter.api.Assertions;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//
//import java.util.List;
//
//import static org.junit.jupiter.api.Assertions.*;
//
///**
// * Tests for the Scheduler class
// */
//class SchedulerTest {
//
//    private Scheduler scheduler;
//    private DroneSubsystem drone1;
//    private DroneSubsystem drone2;
//    private FireRequest request1;
//    private FireRequest request2;
//
//    /**
//     * Set up data before each test case
//     */
//    @BeforeEach
//    void setup() {
//        scheduler = new Scheduler();
//        drone1 = new DroneSubsystem(scheduler);
//        drone2 = new DroneSubsystem(scheduler);
//        scheduler.registerDrone(drone1);
//        scheduler.registerDrone(drone2);
//        request1 = new FireRequest("06:30:00", 1, "FIRE_DETECTED", "High");
//        request2 = new FireRequest("06:35:00", 2, "FIRE_DETECTED", "Medium");
//    }
//
//    /**
//     * Testing state transition from Idle to Processing
//     */
//    @Test
//    public void testSetState_IdleToProcessData() {
//        SchedulerState idleState = new Idle();
//        SchedulerState processingState = new ProcessData(new ReceiveData());
//
//        scheduler.setState(idleState);
//        assertEquals("[IDLE]", scheduler.getCurrentState().display());
//
//        scheduler.setState(processingState);
//        assertEquals("[PROCESSING][RECEIVING DATA]", scheduler.getCurrentState().display());
//    }
//
//    /**
//     * Testing state transition from Processing to Task Drone
//     */
//    @Test
//    public void testSetState_ProcessDataToTaskDrone() {
//        SchedulerState processingState = new ProcessData(new ReceiveData());
//        SchedulerState taskDroneState = new ProcessData(new TaskDrone());
//
//        scheduler.setState(processingState);
//        assertEquals("[PROCESSING][RECEIVING DATA]", scheduler.getCurrentState().display());
//
//        scheduler.setState(taskDroneState);
//        assertEquals("[PROCESSING][TASKING DRONE]", scheduler.getCurrentState().display());
//    }
//
//    /**
//     * Testing state transition from Task Drone to Send Data
//     */
//    @Test
//    public void testSetState_TaskDroneToSendData() {
//        SchedulerState taskDroneState = new ProcessData(new TaskDrone());
//        SchedulerState sendDataState = new SendData();
//
//        scheduler.setState(taskDroneState);
//        assertEquals("[PROCESSING][TASKING DRONE]", scheduler.getCurrentState().display());
//
//        scheduler.setState(sendDataState);
//        assertEquals("[SENDING DATA]", scheduler.getCurrentState().display());
//    }
//
//    /**
//     * Testing state transition from Processing to Idle
//     */
//    @Test
//    public void testSetState_BackToIdle() {
//        SchedulerState processingState = new ProcessData(new ReceiveData());
//        SchedulerState idleState = new Idle();
//
//        scheduler.setState(processingState);
//        assertEquals("[PROCESSING][RECEIVING DATA]", scheduler.getCurrentState().display());
//
//        scheduler.setState(idleState);
//        assertEquals("[IDLE]", scheduler.getCurrentState().display());
//    }
//
//    /**
//     * Testing registering one drone
//     */
//    @Test
//    public void testRegisterSingleDrone() {
//        DroneSubsystem drone = new DroneSubsystem(scheduler);
//
//        scheduler.registerDrone(drone);
//
//        List<DroneSubsystem> registeredDrones = scheduler.getDrones();
//        assertEquals(3, registeredDrones.size());
//        assertTrue(registeredDrones.contains(drone));
//    }
//
//    /**
//     * Testing registering multiple drones
//     */
//    @Test
//    public void testRegisterMultipleDrones() {
//        DroneSubsystem drone1 = new DroneSubsystem(scheduler);
//        DroneSubsystem drone2 = new DroneSubsystem(scheduler);
//        DroneSubsystem drone3 = new DroneSubsystem(scheduler);
//
//        scheduler.registerDrone(drone1);
//        scheduler.registerDrone(drone2);
//        scheduler.registerDrone(drone3);
//
//        List<DroneSubsystem> registeredDrones = scheduler.getDrones();
//        assertEquals(5, registeredDrones.size());
//        assertTrue(registeredDrones.contains(drone1));
//        assertTrue(registeredDrones.contains(drone2));
//        assertTrue(registeredDrones.contains(drone3));
//    }
//
//    /**
//     * Testing registering drone multiple times
//     */
//    @Test
//    public void testRegisterSameDroneTwice() {
//        DroneSubsystem drone = new DroneSubsystem(scheduler);
//
//        scheduler.registerDrone(drone);
//        scheduler.registerDrone(drone);
//
//        List<DroneSubsystem> registeredDrones = scheduler.getDrones();
//        assertEquals(3, registeredDrones.size());
//    }
//
//    /**
//     * tests the {@code addRequest} method
//     * ensures that a fire request is correctly added to the scheduler
//     */
//    @Test
//    void addRequest() {
//        System.out.println("Test: adding Request to Scheduler");
//        FireRequest request = new FireRequest("06:20:19", 3, "FIRE_DETECTED", "Low");
//        scheduler.addRequest(request);
//
//        DroneSubsystem drone = new DroneSubsystem(scheduler);
//        scheduler.registerDrone(drone);
//        Assertions.assertEquals(request, scheduler.takeRequest(drone));
//    }
//
//    /**
//     * Testing assigning single request
//     */
//    @Test
//    public void testAssignSingleRequestToAvailableDrone() {
//        scheduler.addRequest(request1);
//
//        assertNull(drone1.getCurrTask());
//        assertNull(drone2.getCurrTask());
//
//        scheduler.assignRequests();
//
//        boolean requestAssigned = request1.equals(drone1.getCurrTask()) || request1.equals(drone2.getCurrTask());
//        assertTrue(requestAssigned, "Request should be assigned to an available drone.");
//    }
//
//    /**
//     * Testing assigning multiple requests
//     */
//    @Test
//    public void testAssignMultipleRequestsToAvailableDrones() {
//        scheduler.addRequest(request1);
//        scheduler.addRequest(request2);
//
//        scheduler.assignRequests();
//
//        assertNotNull(drone1.getCurrTask());
//        assertNotNull(drone2.getCurrTask());
//        assertNotEquals(drone1.getCurrTask(), drone2.getCurrTask(), "Each drone should receive a different request.");
//    }
//
//    /**
//     * Testing requests remain in the queue when no drones are available
//     */
//    @Test
//    public void testNoAssignmentWhenNoAvailableDrones() {
//        drone1.setCurrTask(new FireRequest("06:25:00", 3, "FIRE_DETECTED", "Low"));
//        drone2.setCurrTask(new FireRequest("06:26:00", 4, "FIRE_DETECTED", "Medium"));
//
//        scheduler.addRequest(request1);
//        scheduler.assignRequests();
//
//        assertEquals(1, scheduler.getRequestQueueSize(), "Request should remain in queue if no drones are available.");
//    }
//
//    /**
//     * tests the {@code takeRequest} method
//     * ensures that the request is correctly taken by the scheduler
//     */
//    @Test
//    void takeRequest() {
//        System.out.println("Test: taking Request from Scheduler");
//        FireRequest request = new FireRequest("06:20:19", 3, "FIRE_DETECTED", "Low");
//        scheduler.addRequest(request);
//
//        DroneSubsystem drone = new DroneSubsystem(scheduler);
//        scheduler.registerDrone(drone);
//        Assertions.assertEquals(request, scheduler.takeRequest(drone));
//    }
//    /**
//     * tests the {@code addResponse} method
//     * ensures the response to the current task is sent to the scheduler
//     */
//    @Test
//    void addResponse() {
//        System.out.println("Test: adding response to scheduler");
//        FireRequest request = new FireRequest("06:20:19", 3, "FIRE_DETECTED", "Low");
//        scheduler.addRequest(request);
//        Response response = new Response(request,"completed");
//        scheduler.addResponse(response, new DroneSubsystem(scheduler));
//
//        Assertions.assertEquals(scheduler.getCurrentResponse(),response);
//    }
//    /**
//     * tests the {@code takeResponse} method
//     * ensures the response to the current task being taken is correct
//     */
//    @Test
//    void takeResponse() {
//        System.out.println("Test: taking response from scheduler");
//        FireRequest request = new FireRequest("06:20:19", 3, "FIRE_DETECTED", "Low");
//        scheduler.addRequest(request);
//        Response response = new Response(request,"completed");
//        scheduler.addResponse(response, new DroneSubsystem(scheduler));
//
//        Assertions.assertEquals(response, scheduler.takeResponse());
//    }
//}
