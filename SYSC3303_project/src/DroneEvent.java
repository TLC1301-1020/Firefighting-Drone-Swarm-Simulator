 /**
 * Enum representing events that trigger state transitions for drones
 */
public enum DroneEvent
{
    OLD_FIRE_REQUEST("OLD_FIRE_REQUEST"),
    NEW_FIRE_REQUEST("NEW_FIRE_REQUEST"),
    PERMISSION_TO_DROP("PERMISSION_TO_DROP"),
    PAYLOAD_DROPPED("PAYLOAD_DROPPED"),
    PAYLOAD_DEPLOY_FAILURE("PAYLOAD_DEPLOY_FAILURE"),
    DEPLOY_FAILURE_ACKNOWLEDGED("DEPLOY_FAILURE_ACKNOWLEDGED"),
    RETURNED_TO_BASE("RETURNED_TO_BASE"),
    REFILL_COMPLETE("REFILL_COMPLETE"),
    DRONE_STUCK("DRONE_STUCK"),
    STUCK_RESOLVED("STUCK_RESOLVED"),
    STATUS("STATUS"),
    CONTINUING("CONTINUING"),
    RETURN_STATUS("RETURN_STATUS");

    /** String representation of the event */
    private final String value;

    DroneEvent(String value)
    {
        this.value = value;
    }
    /**
     * Returns the string value of the event
     * @return the event as a string
     */
    @Override
    public String toString()
    {
        return this.value;
    }
    /**
     * Parses a string to match a valid DroneEvent
     * @param stringEvent the string to parse
     * @return the corresponding DroneEvent
     * @throws IllegalArgumentException if the string doesn't match any event
     */
    public static DroneEvent valueOfEvent(String stringEvent) {
        if (stringEvent == null) {
            throw new IllegalArgumentException("Event string cannot be null");
        }

        switch (stringEvent.trim().toUpperCase()) {
            case "OLD_FIRE_REQUEST": return OLD_FIRE_REQUEST;
            case "NEW_FIRE_REQUEST": return NEW_FIRE_REQUEST;
            case "PERMISSION_TO_DROP": return PERMISSION_TO_DROP;
            case "PAYLOAD_DROPPED": return PAYLOAD_DROPPED;
            case "PAYLOAD_DEPLOY_FAILURE": return PAYLOAD_DEPLOY_FAILURE;
            case "DEPLOY_FAILURE_ACKNOWLEDGED": return DEPLOY_FAILURE_ACKNOWLEDGED;
            case "RETURNED_TO_BASE": return RETURNED_TO_BASE;
            case "REFILL_COMPLETE": return REFILL_COMPLETE;
            case "DRONE_STUCK": return DRONE_STUCK;
            case "STUCK_RESOLVED": return STUCK_RESOLVED;
            case "STATUS": return STATUS;
            case "CONTINUING": return CONTINUING;
            case "RETURN_STATUS": return RETURN_STATUS;
            default:
                throw new IllegalArgumentException("Unknown event: " + stringEvent);
        }
    }
}
