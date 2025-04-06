import java.text.ParseException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.text.SimpleDateFormat;
import java.util.concurrent.TimeUnit;

/**
 * EventScheduler class that takes in Event objects, delays for an appropriate amount of time based on the Event time,
 * and stores Event objects once they are ready to be handled.
 */
public class EventScheduler {

    // Queues to store events to be distributed, and events to be sent
    private Queue<Event> eventQueue;
    private Queue<Event> readyQueue;

    // Simulation speed can be changed to speed up the processing of Events
    public final long simulationSpeed = 500;

    private final SimpleDateFormat formatter = new SimpleDateFormat("HH-mm-ss");
    private final long systemTime;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public EventScheduler() {
        // Initialize a system time that comes before any events
        try {
            systemTime = formatter.parse("10-00-00").getTime();
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
        this.eventQueue = new LinkedList<>();
        this.readyQueue = new LinkedList<>();
    }

    /**
     * Method to be used by the containing object to add Events that should be processed and waited on.
     * @param event Event to process.
     */
    public void addEvent(Event event) {
        eventQueue.add(event);
    }

    /**
     * Method to be used by the containing object to retrieve Events that are ready to be handled.
     * @return the Event that is ready to be handled.
     */
    public Event getEvent() {
        synchronized (readyQueue) {
            while (readyQueue.isEmpty()) {
                try {
                    readyQueue.wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            readyQueue.notifyAll();
            return readyQueue.poll();
        }
    }

    /**
     * Start the scheduler, and start processing Events.
     */
    public void start() {
        scheduler.execute(this::processEvents);
    }

    /**
     * Schedule the distribution of each Event in eventQueue using a delay based on the current system time.
     */
    private void processEvents() {
        while (!eventQueue.isEmpty()) {

            Event event = eventQueue.poll();
            long delay;

            try {
                delay = ( ( formatter.parse(event.getEventTime()).getTime() ) - systemTime ) / simulationSpeed;
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }

            scheduler.schedule(() -> distributeEvent(event), delay, TimeUnit.MILLISECONDS);

        }
    }

    /**
     * Add this event to the thread-safe readyQueue so that the containing object can receive them.
     * @param event Event that has been flagged as ready.
     */
    private void distributeEvent(Event event) {
        synchronized (readyQueue) {
            readyQueue.add(event);
            readyQueue.notifyAll();
        }
    }
    /**
     * Test class for the {@link EventScheduler} class's {@link EventScheduler#getEventQueue()} method.
     * This test ensures that the event queue is correctly initialized and can be accessed.
     */
    public Queue<Event> getEventQueue() {
        return eventQueue;
    }
    /**
     * Retrieves the queue of ready events.
     *
     * This method returns the {@code readyQueue} that holds the events that are ready to be processed or distributed.
     * This queue is used to manage events that have been processed and are awaiting further handling.
     *
     * @return A {@link Queue} of {@link Event} objects representing events that are ready to be processed.
     */
    public Queue<Event> getReadyQueue(){
        return readyQueue;
    }

}