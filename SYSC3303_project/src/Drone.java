public class Drone implements Runnable
{
    /**
     * State Machine Object to handle the state of the drone */
    public DroneState currentState;
    /**
     * current fire request assigned to the drone
     */
    private FireRequest currTask;
    /**
     * unique identifier for the drone
     */
    private int droneId;
    /**
     * maximum velocity of the drone in meters per second
     */
    private final float maxVelocity = 20;
    /**
     * x and y coordinates representing drone position
     */
    private float xPos;
    private float yPos;

    private int payloadCount;
    private final int MAX_PAYLOAD = 10;

    // TODO: Add drone attributes such as battery, acceleration etc.

    private DroneSubsystem droneSubsystem;

    /**
     * Constructor automatically initializes the drone as IDLE, no fireRequest, and full payload
     * @param droneSubsystem shared object to interact with DroneSubsystem controller invoking requestQueue and response queue
     * @param id drone initialized with this id
     */
    public Drone( DroneSubsystem droneSubsystem, int id )
    {
        this.droneSubsystem = droneSubsystem;
        this.droneId = id;
        this.currentState = new DroneIdle(); // initialized state as is idle (has payload loaded)
        this.currTask = null;
        this.payloadCount = MAX_PAYLOAD; // initialed with full payload
    }

    /**
     * @param newState to change the DroneState machine object representing the current state of the drone.
     * Invoked by the DroneState state machine when changing the state of the drone
     */
    public void setState(DroneState newState) {
        System.out.print("* DRONE STATE CHANGE * " + this.currentState.display() + " -> ");
        this.currentState = newState;
        System.out.println(this.currentState.display());
    }

    /**
     * @return currentState of the drone.
     * invoked in the context of checking the current state:
     * {@code drone.getCurrentState() instanceof DroneActive}
     */
    public DroneState getCurrentState() {
        return this.currentState;
    }

    /**
     * @param event leading to the DroneState machine object changing state from the DroneEvent enum list.
     * invoked in the context of changing the current state of the drone given the passed event parameter:
     * {@code drone.handleEvent(DroneEvent.NEW_FIRE_REQUEST)}
     */
    public void handleEvent(DroneEvent event) {
        this.currentState.handleEvent(this, event);
    }

    /**
     * returns the drone id
     * @return drone id
     */
    public float getDroneId() {
        return droneId;
    }

    /**
     * the current fire request assigned to the drone is returned,
     * and a new fire request is swapped in
     * @return currTask - the previous fire request assigned to this drone
     */
    public FireRequest setCurrTask( FireRequest newTask )
    {
        FireRequest temp = this.currTask;
        this.currTask = newTask;
        return temp;
    }

    /**
     * returns the current fire request assigned to the drone
     * @return current fire request
     */
    public FireRequest getCurrTask() {
        return currTask;
    }

    /**
     * simulates drone travel to the fire location
     */
    private void travel() {
        // TODO: sleep for an amount of time equal to destination / maxVelocity

        // For now, arbitrary amount of sleep to simulate travel time
        System.out.println("Drone " + this.droneId + ": travelling to zone " + currTask.getZoneId() + "\n");
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        System.out.println("Drone " + this.droneId + ": arrived at zone " + currTask.getZoneId() + " ready to deploy\n");
    }

    private String makeRequest()
    {
        return this.droneId+":"+this.currentState.display()+":"+this.currentState.getRequest();
    }

    /**
     * thread function for Drone
     *  1. adds the request to the controller based on the current state and next successful action
     *  2. check the droneSubsystem for next instructions for this drone
     *  3. handle instructions given
     */
    @Override
    public void run()
    {
        while (true)
        {
            // adds the request to the controller based on the current state and next successful action
            this.droneSubsystem.addRequest( makeRequest() );

            // check the droneSubsystem for next instructions for this drone
            String response = this.droneSubsystem.getResponse();

            // handle instructions given
            handleResponse(response);
        }
    }

}
