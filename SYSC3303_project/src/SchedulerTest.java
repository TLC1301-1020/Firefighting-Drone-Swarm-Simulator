import static org.junit.Assert.*;
import org.junit.*;

public class SchedulerTest {

    @Test
    public void testRequest() {
        System.out.println("Testing addRequest and takRequest function:");
        Scheduler scheduler = new Scheduler();
        FireRequest request = new FireRequest("06:20:19", 3, "FIRE_DETECTED", "Low");
        scheduler.addRequest(request);
        assertEquals(request, scheduler.takeRequest());
    }

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
