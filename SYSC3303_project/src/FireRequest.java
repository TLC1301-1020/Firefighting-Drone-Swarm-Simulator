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

    private String id;

    /**
     * create a fire request instance with specified details
     * @param time the time of the fire incident
     * @param zoneId the identifier of the affected zone
     * @param eventType the type of fire event
     * @param severity the severity level of the fire
     */
    public FireRequest(String time, int zoneId, String eventType, String severity, String id) {
        this.time = time;
        this.zoneId = zoneId;
        this.eventType = eventType;
        this.severity = severity;
        this.id = id;
    }

    public FireRequest() {
        this.time = "0";
        this.zoneId = -1;
        this.eventType = "0";
        this.severity = "0";
        this.id = "0";
    }

    /**
     * Create a FireRequest instance using a String representation created by toString().
     * @param request the String representation.
     */
    public FireRequest(String request) {
        request = request.replace("FireRequest{", "").replace("}", "");
        String[] parts = request.split(", ");

        for (String part : parts) {
            String[] value = part.split("=");
            switch (value[0]) {
                case "time":
                    this.time = value[1];
                    break;
                case "zone":
                    this.zoneId = Integer.parseInt(value[1]);
                    break;
                case "event":
                    this.eventType = value[1];
                    break;
                case "severity":
                    this.severity = value[1];
                    break;
                case "id":
                    this.id = value[1];
                    break;
            }
        }
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
     * Determines if this FireRequest is the default (i.e. empty) request.
     * @return true if this is the default FireRequest; false otherwise.
     */
    public boolean isDefault() {
        return this.zoneId == -1 &&
                this.time.equals("0") &&
                this.eventType.equals("0") &&
                this.severity.equals("0");
    }

    public String getId() {return this.id;}

    /**
     * creates a string representation of the fire request object
     * @return formatted string representing the fire request
     */
    @Override
    public String toString() {
        return String.format(
                "FireRequest{time=%s, zone=%d, event=%s, severity=%s, id=%s}",
                time, zoneId, eventType, severity, id
        );
    }
}