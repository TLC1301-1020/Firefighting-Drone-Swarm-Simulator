public class DroneStatus {
    private final int droneId;
    private String state;   // string
    private int x;
    private int y;
    private FireRequest currentTask;
    private double batteryLevel;
    private static final double MAX_BATTERY = 100.0;

    /**
     * initializes drone status for drone object with given drone id,
     * current state as "[IDLE]" and location at (0,0)
     @param droneId drone id to be given to drone when initialized
     */
    public DroneStatus(int droneId) {
        this.droneId = droneId;
        this.state = "[IDLE]";
        this.x = 0;
        this.y = 0;
        this.currentTask = new FireRequest();
        this.batteryLevel = MAX_BATTERY;
    }

    public int getDroneId() { return droneId; }
    public String getState() { return state; }
    public void setState(String newState) { this.state = newState; }
    public int getX() { return this.x; }
    public int getY() { return this.y; }
    public void setLocation(int x, int y)
    {
        this.x = x;
        this.y = y;
    }
    public FireRequest getCurrentTask() { return currentTask; }
    public void setCurrentTask(FireRequest fr) { this.currentTask = fr; }
    public void chargeBattery() { this.batteryLevel = MAX_BATTERY; }
    public String getBatteryLevel() { return String.format("%.2f%%", this.batteryLevel); }
    public void updateBattery(int x, int y) {
        double distance = Math.sqrt(Math.pow(x - this.x, 2) + Math.pow(y - this.y, 2));
        double consumptionRate = 0.000015;
        batteryLevel -= distance * consumptionRate;
        batteryLevel = Math.max(batteryLevel, 0);
    }
    public String toString()
    {
        return this.droneId+":"+this.state+":"+this.x+":"+this.y + ":" + this.currentTask.toString();
    }
}
