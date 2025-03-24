/**
 * Event class to store timed events such as new FireRequests and new DroneFaults
 */
public class Event {
    private Object event;
    private String time;

    public Event(Object event, String time) {
        this.event = event;
        this.time = time;
    }

    public String getEventTime() {
        return time;
    }

    public Object getEvent() {
        return event;
    }
}
