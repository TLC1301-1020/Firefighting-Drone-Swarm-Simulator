import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.util.Queue;

/**
 * Unit test class for EventScheduler and Event functionality
 * <p>
 * <ul>
 *     <li>Validates correct instantiation of {@code Event} and {@code EventScheduler}.</li>
 *     <li>Verifies queue operations in {@link EventScheduler}</li>
 *     <li>Ensures the internal scheduling logic correctly moves events from the event queue to the ready queue based on time.</li>
 * </ul>
 */
class EventSchedulerTest {

    /** A reusable Event object used for test initialization. */
    private Event event;
    /** The EventScheduler instance being tested. */
    private EventScheduler eventScheduler = new EventScheduler();
    /**
     * Test class for the Event class to verify that the Event object
     * correctly stores and retrieves the event and event time.
     */
    @Test
    public void TestEvent() {
        String expectedTime = "10-20-15";
        FireRequest fireRequest = new FireRequest();
        event = new Event(fireRequest, expectedTime);

        assertEquals(expectedTime, event.getEventTime(), "Event time should be the same as the one passed in the constructor.");
        assertEquals(fireRequest, event.getEvent(), "Event object should be the same as the one passed in the constructor.");
    }
    /**
     * Test class for initializing EventScheduler.
     * This test ensures that the EventScheduler is properly initialized with empty event queues.
     */
    @Test
    public void TestInitEventScheduler() {
        assertNotNull(eventScheduler, "EventScheduler should be initialized.");
        assertTrue(eventScheduler.getEventQueue().isEmpty(),"The queue should be empty.");
        assertTrue(eventScheduler.getReadyQueue().isEmpty(), "The queue should be empty.");

    }
    /**
     * Test for adding an event to EventScheduler.
     * This test verifies that when an event is added using the addEvent method,
     * it is correctly placed in the eventQueue.
     */
    @Test
    public void TestAddEvent() {
        String expectedTime = "10-20-15";
        FireRequest fireRequest = new FireRequest();
        event = new Event(fireRequest, expectedTime);

        eventScheduler.addEvent(event);
        assertEquals(1, eventScheduler.getEventQueue().size(), "The queue should have one event.");
    }

    /**
     * Test class for processing events in EventScheduler.
     * This test ensures that the processEvents method correctly processes events
     * by moving them from the event queue to the ready queue.
     */
    @Test
    public void TestProcessEvents() throws Exception {

        // Arrange the time to initial start time in EventScheduler()
        Event event = new Event(new FireRequest(), "10-00-00");
        eventScheduler.addEvent(event);

        // using reflection to access the processEvents()
        Method processEventsMethod = EventScheduler.class.getDeclaredMethod("processEvents");
        processEventsMethod.setAccessible(true);

        processEventsMethod.invoke(eventScheduler);

        // Wait for event processing to complete
        Thread.sleep(2000);

        Queue<Event> readyQueue = eventScheduler.getReadyQueue();
        assertFalse(readyQueue.isEmpty(), "Ready queue should contain the event after processing.");
        Queue<Event> eventQueue = eventScheduler.getEventQueue();
        assertTrue(eventQueue.isEmpty(), "Event queue should be empty.");
    }
    /**
     * Test class for the get event method in EventScheduler.
     * This test ensures that when an event is processed, it will be polled
     * from the readyQueue using the getEvent() method.
     */
    @Test
    public void TestGetEvent() throws Exception {
        // Arrange
        EventScheduler eventScheduler = new EventScheduler();

        // Add an event directly to the readyQueue
        Event event = new Event(new FireRequest(), "10-00-00");
        eventScheduler.addEvent(event);
        // using reflection to access the processEvents()
        Method processEventsMethod = EventScheduler.class.getDeclaredMethod("processEvents");
        processEventsMethod.setAccessible(true);
        processEventsMethod.invoke(eventScheduler);

        //wait for the process to finish
        Thread.sleep(2000);

        Event result = eventScheduler.getEvent();

        assertNotNull(result, "The event should not be null when retrieved.");
        assertEquals(event, result, "The event retrieved should be the same as the one added.");
        assertTrue(eventScheduler.getReadyQueue().isEmpty(),"The event should be polled from the queue.");
    }
}