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
     * unique string id of the fire request with 2 parts. <P>Main id is a numerical value based on the order it was passed
     * from fire incident sub system to Scheduler eg: '1' - is the first fire recorded <P>
     * Second part is  appended to main id and is a character representing the distinct fire requests that was created for that fire based on severity.
     * <P>Fires by severity will have the following fire requests made:<P> High - 3x w. codes:{A,B,C} <P>Moderate - 2x w. codes:{A,B}<P>Low - 1x w. codes:{A}
     */
    private String id;

    /**
     * create a fire request instance with specified details
     * @param time the time of the fire incident
     * @param zoneId the identifier of the affected zone
     * @param eventType the type of fire event
     * @param severity the severity level of the fire
     * @param id the unique string id of this fire mission
     */
    public FireRequest(String time, int zoneId, String eventType, String severity, String id) {
        this.time = time;
        this.zoneId = zoneId;
        this.eventType = eventType;
        this.severity = severity;
        this.id = id;
    }

    /**
     * default constructor creates a default fire request when Drone or DroneStatus has no active fire request
     */
    public FireRequest() {
        this.time = "0";
        this.zoneId = -1;
        this.eventType = "0";
        this.severity = "0";
        this.id = "0";
    }

    /**
     * Create a FireRequest instance using a String passed in and parsed based on specific formatting: <P>
     *     eg: 'FireRequest{time=10-30-00, zone=1, event=FIRE_DETECTED, severity=Moderate, id=1A}'
     * </P>
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
                this.severity.equals("0") &&
                this.id.equals("0");
    }

    /**
     * @return String id of this fire request
     */
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