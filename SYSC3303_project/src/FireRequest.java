public class FireRequest {
    private String time;
    private int zoneId;
    private String eventType;
    private String severity;

    public FireRequest(String time, int zoneId, String eventType, String severity) {
        this.time = time;
        this.zoneId = zoneId;
        this.eventType = eventType;
        this.severity = severity;
    }

    // Getters and toString()
    public String getTime() {
        return time;
    }
    public int getZoneId() {
        return zoneId;
    }
    public String getEventType() {
        return eventType;
    }
    public String getSeverity() {
        return severity;
    }

    @Override
    public String toString() {
        return String.format(
                "FireRequest{time=%s, zone=%d, event=%s, severity=%s}",
                time, zoneId, eventType, severity
        );
    }
}