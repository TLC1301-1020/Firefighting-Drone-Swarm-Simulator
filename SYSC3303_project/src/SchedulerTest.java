import static org.junit.Assert.*;
import org.junit.*;

/**
 * {@code SchedulerTest} contains junit unit tests for the {@code Scheduler} class correctly
 * handling requests and processing responses.
 */
public class SchedulerTest {

    /**
     * tests the {@code addRequest} and {@code takeRequest} methods
     * ensures that a fire request is correctly added to and retrieved from the scheduler
     */
    @Test
    public void testRequest() {
        System.out.println("Testing addRequest and takeRequest function:");
        Scheduler scheduler = new Scheduler();
        FireRequest request = new FireRequest("06:20:19", 3, "FIRE_DETECTED", "Low");
        scheduler.addRequest(request);
        assertEquals(request, scheduler.takeRequest());
    }

    /**
     * tests the {@code addResponse} and {@code takeResponse} methods
     * ensures that a response is correctly added to and retrieved from the scheduler
     */
    @Test
    public void testResponse() {
        System.out.println("Testing addResponse and takeResponse function:");
        Scheduler scheduler = new Scheduler();
        FireRequest request = new FireRequest("06:20:19", 3, "FIRE_DETECTED", "Low");
        scheduler.addRequest(request);
        System.out.println("added request" + request);
        Response response = new Response(request, "completed");

        scheduler.addResponse(response);
        assertEquals(response, scheduler.takeResponse());
    }
}
