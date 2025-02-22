import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.*;

/**
 * {@code SchedulerTest} contains junit unit tests for the {@code Scheduler} class correctly
 * handling requests and processing responses.
 */
public class SchedulerTestOld {


    /**
     * tests the {@code addRequest} method
     * ensures that a fire request is correctly added to the scheduler
     */
    private Scheduler scheduler;
    @BeforeEach
    void setup() {
        scheduler = new Scheduler();
    }
    @Test
    public void Test_addRequest() {
        System.out.println("Test: adding Request to Scheduler");
        FireRequest request = new FireRequest("06:20:19", 3, "FIRE_DETECTED", "Low");
        scheduler.addRequest(request);

        DroneSubsystem drone = new DroneSubsystem(scheduler);
        scheduler.registerDrone(drone);
        Assertions.assertEquals(request, scheduler.takeRequest(drone));

    }

    /**
     * tests the {@code takeRequest} method
     * ensures that the request is correctly taken by the scheduler
     */
    @Test
    public void Test_takeRequest() {
        System.out.println("Test: taking Request from Scheduler");
        FireRequest request = new FireRequest("06:20:19", 3, "FIRE_DETECTED", "Low");
        scheduler.addRequest(request);

        DroneSubsystem drone = new DroneSubsystem(scheduler);
        scheduler.registerDrone(drone);
        Assertions.assertEquals(request, scheduler.takeRequest(drone));

    }

    /**
     * tests the {@code addResponse} method
     * ensures the response to the current task is sent to the scheduler
     */
    @Test
    public void Test_addResponse(){
        System.out.println("Test: adding response to scheduler");
        FireRequest request = new FireRequest("06:20:19", 3, "FIRE_DETECTED", "Low");
        scheduler.addRequest(request);
        Response response = new Response(request,"completed");
        scheduler.addResponse(response, new DroneSubsystem(scheduler));

        Assertions.assertEquals(scheduler.getCurrentResponse(),response);
    }

    /**
     * tests the {@code takeResponse} method
     * ensures the response to the current task being taken is correct
     */
    @Test
    public void Test_takeResponse(){
        System.out.println("Test: taking response from scheduler");
        FireRequest request = new FireRequest("06:20:19", 3, "FIRE_DETECTED", "Low");
        scheduler.addRequest(request);
        Response response = new Response(request,"completed");
        scheduler.addResponse(response, new DroneSubsystem(scheduler));

        Assertions.assertEquals(response, scheduler.takeResponse());

    }


}