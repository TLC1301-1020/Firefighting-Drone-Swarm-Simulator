import static org.junit.Assert.*;
import org.junit.*;

public class SchedulerTest {

    @Test
    public void testRequest() {
        Scheduler scheduler = new Scheduler();
        FireRequest request = new FireRequest("06:20:19", 3, "FIRE_DETECTED", "Low");
        scheduler.addRequest(request);
        assertEquals(request, scheduler.takeRequest());
    }

    @Test
    public void testResponse() {
        Scheduler scheduler = new Scheduler();
        FireRequest request = new FireRequest("06:20:19", 3, "FIRE_DETECTED", "Low");
        scheduler.addRequest(request);
        
        Response response = scheduler.takeResponse();
        scheduler.addResponse(response);
        assertEquals(response, scheduler.takeResponse());
    }
}