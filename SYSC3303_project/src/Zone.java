// Added zone class to store zone information into zone objects/zones
public class Zone {
    private int zoneId;
    private int startX;
    private int startY;
    private int endX;
    private int endY;

    public Zone(int zoneId, int startX, int startY, int endX, int endY) {
        this.zoneId = zoneId;
        this.startX = startX;
        this.startY = startY;
        this.endX = endX;
        this.endY = endY;
    }

    public int getZoneId() { return zoneId; }
    public int getStartX() { return startX; }
    public int getStartY() { return startY; }
    public int getEndX() { return endX; }
    public int getEndY() { return endY; }

    @Override
    public String toString() {
        return "Zone{" +
                "zoneId=" + zoneId +
                ", start=(" + startX + "," + startY + ")" +
                ", end=(" + endX + "," + endY + ")" +
                '}';
    }
}
