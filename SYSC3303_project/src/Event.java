/**
 * Represents an event with a specific time, such as a FireRequest or DroneFault
 * <p>
 * The Event class stores the event details and the time it occurred, allowing
 * for easy tracking and processing of timed events
 */
public class Event {
    private Object event;
    private String time;

    /**
     * Constructs an Event with the specified event and time
     *
     * @param event The event (e.g., FireRequest, DroneFault) to be stored
     * @param time  The time the event occurred, represented as a string
     */
    public Event(Object event, String time) {
        this.event = event;
        this.time = time;
    }

    //Getters
    public String getEventTime() {
        return time;
    }

    public Object getEvent() {
        return event;
    }
}
