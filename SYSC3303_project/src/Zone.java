/**
 * Represents a rectangular zone area in a 2D coordinate system.
 * Used to define areas where fire incidents or drones may operate.
 */
public class Zone {
    /** Unique identifier for the zone */
    private int zoneId;
    /** X-coordinate of the top-left corner of the zone */
    private int startX;
    /** Y-coordinate of the top-left corner of the zone */
    private int startY;
    /** X-coordinate of the bottom-right corner of the zone */
    private int endX;
    /** Y-coordinate of the bottom-right corner of the zone */
    private int endY;
    /** character representation for corners of zone for display purposes (default '@') */
    char zoneChar = '@';

    /**
     * Constructs a new Zone with the given boundaries and ID.
     *
     * @param zoneId the unique identifier for this zone
     * @param startX the starting x-coordinate (top-left)
     * @param startY the starting y-coordinate (top-left)
     * @param endX the ending x-coordinate (bottom-right)
     * @param endY the ending y-coordinate (bottom-right)
     */
    public Zone(int zoneId, int startX, int startY, int endX, int endY) {
        this.zoneId = zoneId;
        this.startX = startX;
        this.startY = startY;
        this.endX = endX;
        this.endY = endY;
    }


    /**
     * @return the zone's unique identifier
     */
    public int getZoneId() { return zoneId; }
    /**
     * @return the starting x-coordinate of the zone
     */
    public int getStartX() { return startX; }
    /**
     * @return the starting y-coordinate of the zone
     */
    public int getStartY() { return startY; }
    /**
     * @return the ending x-coordinate of the zone
     */
    public int getEndX() { return endX; }
    /**
     * @return the ending y-coordinate of the zone
     */
    public int getEndY() { return endY; }

    /**
     * Returns a string representation of the zone.
     * If the {@code zoneChar} is not set (eg '@'), prints full zone information.
     * Otherwise, prints a shorthand with the zone ID and zoneChar.
     *
     * @return formatted string representing this zone
     */
    @Override
    public String toString() {
        if(zoneChar=='@') return "Zone{" +
                "zoneId=" + zoneId +
                ", start=(" + startX + "," + startY + ")" +
                ", end=(" + endX + "," + endY + ")" +
                '}';
        return String.format("Zone %d -> %c", zoneId, zoneChar);
    }
}
