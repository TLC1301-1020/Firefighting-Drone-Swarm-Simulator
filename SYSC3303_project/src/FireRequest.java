/**
 * {@code FireRequest} represents a fire incident request handled by the scheduler.
 * it contains details such as time, location, event type, and severity.
 * Implements Runnable to execute in a separate thread.
 */
public class FireRequest {
    /**
     * time when the fire incident occurred
     */
    private String time;
    /**
     * id to distinguish the fire incident zone the request is made for
     */
    private int zoneId;
    /**
     * type of event the request corresponds to: FIRE_DETECTED
     */
    private String eventType;
    /**
     * severity level of the fire incident: Low, Moderate, High
     */
    private String severity;

    /**
     * create a fire request instance with specified details
     * @param time the time of the fire incident
     * @param zoneId the identifier of the affected zone
     * @param eventType the type of fire event
     * @param severity the severity level of the fire
     */
    public FireRequest(String time, int zoneId, String eventType, String severity) {
        this.time = time;
        this.zoneId = zoneId;
        this.eventType = eventType;
        this.severity = severity;
    }

    // Getters and toString()

    /**
     * Getter
     *
     * @return time - the time of the fire incident
     */
    public String getTime() {
        return time;
    }

    /**
     * Getter
     *
     * @return zoneId - the zone id the request is made for
     */
    public int getZoneId() {
        return zoneId;
    }

    /**
     * Getter
     *
     * @return eventType - the type of fire event
     */
    public String getEventType() {
        return eventType;
    }

    /**
     * Getter
     *
     * @return severity - the severity level of the fire
     */
    public String getSeverity() {
        return severity;
    }

    /**
     * creates a string representation of the fire request object
     * @return formatted string representing the fire request
     */
    @Override
    public String toString() {
        return String.format(
                "FireRequest{time=%s, zone=%d, event=%s, severity=%s}",
                time, zoneId, eventType, severity
        );
    }
}