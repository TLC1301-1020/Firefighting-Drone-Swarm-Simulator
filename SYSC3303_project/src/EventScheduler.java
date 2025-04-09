import java.text.ParseException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.text.SimpleDateFormat;
import java.util.concurrent.TimeUnit;

/**
 * The {@code EventScheduler} class manages the scheduling and processing of {@code Event} objects
 * <p>
 * It processes the events based on their scheduled times, and distributes them to be handled once they are ready
 * The simulation speed can be adjusted, allowing events to be processed at different speeds
 * </p>
 */
public class EventScheduler {

    // Queues to store events to be distributed, and events to be sent
    private Queue<Event> eventQueue;
    private Queue<Event> readyQueue;

    // Simulation speed can be changed to speed up the processing of Events
    public static final long simulationSpeed = 120;

    private final SimpleDateFormat formatter = new SimpleDateFormat("HH-mm-ss");
    private final long systemTime;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    /**
     * Constructs an {@code EventScheduler} instance with an initial system time
     * The system time is set to "10-00-00" by default
     */
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
     * Adds an {@code Event} to the event queue for processing
     *
     * @param event The event to process
     */
    public void addEvent(Event event) {
        eventQueue.add(event);
    }

    /**
     * Retrieves and removes the next {@code Event} that is ready to be handled
     * Blocks until an event is available
     *
     * @return The next ready event
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
     * Starts the scheduler and begins processing the events
     */
    public void start() {
        scheduler.execute(this::processEvents);
    }

    /**
     * Processes events by scheduling them with delays based on their times
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
     * Distributes the event to the {@code readyQueue} once it is ready for processing
     *
     * @param event The event that is ready to be processed
     */
    private void distributeEvent(Event event) {
        synchronized (readyQueue) {
            readyQueue.add(event);
            readyQueue.notifyAll();
        }
    }
    /**
     * Retrieves the event queue, which holds the events awaiting processing
     *
     * @return The event queue
     */
    public Queue<Event> getEventQueue() {
        return eventQueue;
    }

    /**
     * Retrieves the queue of ready events that are ready to be processed
     *
     * @return The ready queue containing events that can be processed
     */
    public Queue<Event> getReadyQueue(){
        return readyQueue;
    }

}