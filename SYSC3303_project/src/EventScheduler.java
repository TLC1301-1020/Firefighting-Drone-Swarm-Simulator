import java.text.ParseException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.PriorityQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.text.SimpleDateFormat;
import java.util.concurrent.TimeUnit;

/**
 * EventScheduler class that takes in Event objects, delays for an appropriate amount of time based on the Event time,
 * and stores Event objects once they are ready to be handled.
 */
public class EventScheduler {

    private PriorityQueue<Event> eventQueue;
    private PriorityQueue<Event> readyQueue;

    private final long simulationSpeed = 1;

    private final SimpleDateFormat formatter = new SimpleDateFormat("HH-mm-ss");
    private final long systemTime;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public EventScheduler() {
        try {
            systemTime = formatter.parse("10-00-00").getTime();
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
        this.eventQueue = new PriorityQueue<>();
        this.readyQueue = new PriorityQueue<>();
    }

    public void addEvent(Event event) {
        eventQueue.add(event);
    }

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

    public void start() {
        scheduler.execute(this::processEvents);
    }

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

    private void distributeEvent(Event event) {
        synchronized (readyQueue) {
            readyQueue.add(event);
            readyQueue.notifyAll();
        }
    }
}
