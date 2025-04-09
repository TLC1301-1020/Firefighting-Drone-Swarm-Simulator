/**
 * Represents the status of a drone, including its location, state, task, and battery level
 */
public class DroneStatus {
    private final int droneId;
    private String state;   // string
    private int x;
    private int y;
    private FireRequest currentTask;
    private double batteryLevel;
    private static final double MAX_BATTERY = 100.0;

    /**
     * Initializes a drone with ID, idle state, position (0,0), full battery, and no task
     *
     * @param droneId the unique ID of the drone
     */
    public DroneStatus(int droneId) {
        this.droneId = droneId;
        this.state = "[IDLE]";
        this.x = 0;
        this.y = 0;
        this.currentTask = new FireRequest();
        this.batteryLevel = MAX_BATTERY;
    }
    /** Charges the battery to 100%. */
    public void chargeBattery() { this.batteryLevel = MAX_BATTERY; }

    /**
     * Updates battery level based on Euclidean distance to new (x, y)
     *
     * @param x new X coordinate
     * @param y new Y coordinate
     */
    public void updateBattery(int x, int y) {
        double distance = Math.sqrt(Math.pow(x - this.x, 2) + Math.pow(y - this.y, 2));
        double consumptionRate = 0.000015;
        batteryLevel -= distance * consumptionRate;
        batteryLevel = Math.max(batteryLevel, 0);
    }

    /**
     * @return a string showing drone ID, state, location, and task
     */
    public String toString(){
        return this.droneId+":"+this.state+":"+this.x+":"+this.y + ":" + this.currentTask.toString();
    }

    // Setters
    public void setLocation(int x, int y)
    {
        this.x = x;
        this.y = y;
    }
    public void setState(String newState) { this.state = newState; }
    public void setCurrentTask(FireRequest fr) { this.currentTask = fr; }

    // Getters
    public int getDroneId() { return droneId; }
    public String getState() { return state; }
    public int getX() { return this.x; }
    public int getY() { return this.y; }
    public String getBatteryLevel() { return String.format("%.2f%%", this.batteryLevel); }
    public FireRequest getCurrentTask() { return currentTask; }


}
