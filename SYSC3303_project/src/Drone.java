public class Drone implements Runnable
{
    /**
     * State Machine Object to handle the state of the drone */
    public DroneState currentState;
    /**
     * current fire request assigned to the drone */
    private FireRequest currTask;
    /**
     * unique identifier for the drone */
    private int droneId;
    /**
     * maximum velocity of the drone in meters per second */
    private final float maxVelocity = 20;
    /**
     * coordinates representing drone position */
    private double xPos,yPos = 0;
    /**
     * number of fire extinguisher balls currently loaded on the drone */
    private int payloadCount;
    /**
     * max payload the drone can carry of fire extinguisher balls */
    private final int MAX_PAYLOAD = 10;

    /**
     * used for when checking what request drone will send.
     * if {@code true} drone will safely send request of location without
     * disordering state context switching sequence.
     * <p>
     * will only change when is interrupted in the travel function in the DroneTravel state
     * <p>
     * see {@code Drone.setSendStatus()} & {@code Drone.checkSendStatus()}
     */
    private boolean sendStatus = false;

    // TODO: Add drone attributes such as battery, acceleration etc.

    /**
     * Pointer to the DroneSubsystem instance controlling all drones. Purpose
     * is so each drone can communicate with it in a thread safe way via
     * DroneSubsystem.requestQueue and DroneSubsystem.responseQueue*/
    private DroneSubsystem droneSubsystem;

    /**
     * Constructor automatically initializes the drone as IDLE, no fireRequest, and full payload
     * @param droneSubsystem shared object to interact with DroneSubsystem router invoking requestQueue and response queue
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
    public void travel() {

        // TODO: Read separate input file that has zone coordinate details. Maybe pass in these coordinates to the function instead, and move this logic elsewhere
        int finalX, finalY;
        switch (currTask.getZoneId()) {
            case 2:
                finalX = DroneSubsystem.zone2X;
                finalY = DroneSubsystem.zone2Y;
                break;
            case 3:
                finalX = DroneSubsystem.zone3X;
                finalY = DroneSubsystem.zone3Y;
                break;
            case 7:
                finalX = DroneSubsystem.zone7X;
                finalY = DroneSubsystem.zone7Y;
                break;
            default:
                finalX = 0;
                finalY = 0;
        }

        // Calculate distance from this Drone's current location to target location
        double distance = Math.sqrt( Math.pow( (finalX - this.xPos), 2 ) + Math.pow( (finalY - this.yPos), 2 ) );

        // Calculate duration of trip in milliseconds
        double travelTime = (distance/this.maxVelocity)*1000;

        // Calculate change in Drone X and Y coordinates per meter travelled
        double deltaX = ( this.xPos ) - ( this.xPos + ( ( finalX - this.xPos )*( 1000/this.maxVelocity ) / travelTime ) );
        double deltaY = ( this.yPos ) - ( this.yPos + ( ( finalY - this.yPos )*( 1000/this.maxVelocity ) / travelTime ) );

        double spentTime = 0;
        while (spentTime < travelTime) {

            // TODO: Change rate of update if too frequent and causing delay
            // Sleep for the time it takes to travel one meter
            try {
                Thread.sleep((long) (1000/this.maxVelocity));
            } catch (InterruptedException e) {
                // Interrupted: are we changing requests, or providing a status update?
                // sets flag for sending location status to scheduler
                setSendStatus();
                return;
            }

            spentTime += (1000/this.maxVelocity);
            this.xPos += deltaX;
            this.yPos += deltaY;

        }

        System.out.println("Drone " + this.droneId + ": arrived at zone " + currTask.getZoneId() + " ready to deploy\n");
    }

    /**
     checks {@code Drone.sendStatus} and resets it to false after - used for when drone sends request,
     will safely send request of location without disordering state context switching
     @return true if drone will send a request of location status update
     */
    private boolean checkSendStatus()
    {
        boolean output = this.sendStatus;
        this.sendStatus = false;
        return output;
    }

    /**
        sets {@code Drone.sendStatus} to true - used for when drone sends request,
        will safely send request of location without disordering state context switching
     */
    private void setSendStatus()
    {
        this.sendStatus = true;
    }

    /**
     * request format is:<p>
     *     DRONE_ID:STATE:REQUEST_BODY:X_POS:Y_POS
     @return String value of entire formatted request to send to scheduler based on current state
     and location.
     */
    private String makeRequest()
    {
        return this.droneId+":"+this.currentState.display()+":"+this.currentState.getRequest()+":"+(int) this.xPos+":"+(int) this.yPos;
    }

    /**
     * request format is:<p>
     *     DRONE_ID:STATE:[STATUS]:X_POS:Y_POS
     @return String value of entire formatted request to send to scheduler based on current state
     and location.
     */
    private String makeStatusRequest()
    {
        return this.droneId+":"+this.currentState.display()+":[STATUS]:"+(int) this.xPos+":"+(int) this.yPos;
    }

    /**
     * thread function for Drone
     *  1. checks if drone should send its location status as a request, otherwise, sends normal request based on state <p>
     *  2. adds the request to the router host based on the current state and next successful action<p>
     *  3. check the droneSubsystem for next instructions for this drone<p>
     *  4. handle instructions given<p>
     */
    @Override
    public void run()
    {
        while (true)
        {
            String request;
            // check if drone should send its location status as a request, otherwise, sends normal request based on state
            if( checkSendStatus() ) request = makeStatusRequest();
            else                    request = makeRequest();

            // adds the request to the router host
            this.droneSubsystem.addRequest( request );

            // check the droneSubsystem for next instructions for this drone
            String response = this.droneSubsystem.getResponse(this.droneId);

            // handle instructions given
            handleResponse(response);
        }
    }

    /**
     * Parse the incoming response and drone proceeds accordingly to a new state.
     * <p>
     * * Also handles if the scheduler tasked the drone to have a new fire request
     * and handles what happens to the old fire request
     * <p>
     * @param response the incoming response.
     */
    private void handleResponse(String response) {
        /*  Response types
            "ACK" in format ACK:DRONE_ID:STATE:REQUEST:X:Y      - for saying acknowledge
            "NEW" in format NEW:DRONE_ID:STATE:FIREREQUEST:X:Y  - for reassigning current task and state
         */
        String[] items = response.split(":");
//        if (items.length != 6) return;

        String schedulerInstructions = items[0];

        // get drone id     -   in expected format "RESPONSE:DRONE_ID:STATE:REQUEST:X:Y"
        int droneId = -1;
        try {droneId = Integer.parseInt(items[1]);}
        catch (NumberFormatException e) {
            System.out.println("ERROR: Invalid int parsing handleDroneResponse");
            return;
        }

        // get current state of this drone     -   in expected format "RESPONSE:DRONE_ID:STATE:REQUEST:X:Y"
        String droneState = items[2];


        // if schedulerInstructions is acknowledgement
        if( schedulerInstructions.equals("ACK") )
        {
            // proceed with drone request

            // get request of this drone and convert it to DroneEvent
            DroneEvent eventRequest;
            try { eventRequest = DroneEvent.valueOf(items[3]); }
            catch (IllegalArgumentException e) {
                System.out.println("ERROR: Unknown drone event: " + items[3]);
                return;
            }

            this.currentState.handleEvent(this, eventRequest);

        }
        else if( schedulerInstructions.equals("NEW") )
        {
            // new tasking for that drone
            // idle || doing something == rerouted
            // request = "NEW" in format NEW:DRONE_ID:STATE:FIREREQUEST:X:Y  - for reassigning current task and state

            // get request of this drone and convert it to DroneEvent
            String newFireRequest= items[3];

            // handles if currently has a fire request -> reassigning that
                // sending to scheduler or to subsystem
            setCurrTask( new FireRequest(newFireRequest) );

            this.currentState.handleEvent(this, DroneEvent.NEW_FIRE_REQUEST);

        }
    }

}
