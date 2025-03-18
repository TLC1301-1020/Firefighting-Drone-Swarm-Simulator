import java.util.Arrays;

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
    private final float maxVelocity = 200;
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
        this.currTask = new FireRequest();
        this.payloadCount = MAX_PAYLOAD; // initialed with full payload
    }

    /**
     * @param newState to change the DroneState machine object representing the current state of the drone.
     * Invoked by the DroneState state machine when changing the state of the drone
     */
    public void setState(DroneState newState) {
        DroneState oldState = this.currentState;
        this.currentState = newState;
        System.out.println("* DRONE STATE CHANGE * " + oldState.display() + " -> " + this.currentState.display());
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
    public int getDroneId() {
        return droneId;
    }

    /**
     * the current fire request assigned to the drone is returned,
     * and a new fire request is swapped in
     * @return currTask - the previous fire request assigned to this drone
     */
    public FireRequest setCurrTask( FireRequest newTask )
    {
        if( this.currTask == null ) System.out.println(" OLD TASK IS NULL ");
        else {
//            System.out.println("\n[ DRONE ] OLD TASK ASSIGNED :           "+ this.currTask.toString());
        }
        FireRequest temp = this.currTask;
        this.currTask = newTask;
//        System.out.println("[ DRONE ] NEW TASK ASSIGNED :           "+ this.currTask.toString() + "\n");
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
     * @return true if travel is interrupted
     */
    public boolean travel() {
        int finalX, finalY;
        // Retrieve the zone from the FireIncidentSubsystem's static zoneMap using the current fire request's zone ID.
        Zone zone = droneSubsystem.getZone(currTask.getZoneId());
        System.out.println( " \nTRAVEL ZONE : "  + zone.toString() );
        if (zone != null) {
            // Calculate the center of the zone as the target destination.
            finalX = (zone.getStartX() + zone.getEndX()) / 2;
            finalY = (zone.getStartY() + zone.getEndY()) / 2;
        } else {
            finalX = 0;
            finalY = 0;
        }
        System.out.println( " \nTRAVEL ZONE : "  + finalX + "," + finalY );

        // Calculate distance from current position to target.
        double distance = Math.sqrt(Math.pow(finalX - this.xPos, 2) + Math.pow(finalY - this.yPos, 2));
        // Calculate travel time in milliseconds.
        double travelTime = (distance / this.maxVelocity) * 1000;

        // Determine the time per step (simulate one "step" of travel)
        double stepTime = 1000 / this.maxVelocity; // in milliseconds
        // Calculate the number of steps to reach the destination.
        double steps = travelTime / stepTime;
        // Compute change in X and Y per step.
        double deltaX = (finalX - this.xPos) / steps;
        double deltaY = (finalY - this.yPos) / steps;

//        System.out.println("\n[ DRONE TRAVEL DEBUG ]");
//        System.out.println(" - Drone ID: " + this.droneId);
//        System.out.println(" - Current Position: (" + this.xPos + ", " + this.yPos + ")");
//        System.out.println(" - Target Zone Position: (" + finalX + ", " + finalY + ")");
//        System.out.println(" - Distance to Target: " + distance + " meters");
//        System.out.println(" - Max Velocity: " + this.maxVelocity + " m/s");
//        System.out.println(" - Estimated Travel Time: " + travelTime + " ms");
//        System.out.println(" - Step Time (per update cycle): " + stepTime + " ms");
//        System.out.println(" - Number of Steps: " + steps);
//        System.out.println("\n[ DRONE TRAVEL ] travel : deltaX=" + deltaX + ", deltaY=" + deltaY);

        double spentTime = 0;
        while (spentTime < travelTime) {
            try {
                Thread.sleep((long) stepTime);
            } catch (InterruptedException e) {
                System.out.println("\n[ DRONE TRAVEL ] Drone " + this.droneId + " interrupted during travel. Sending status update.");
                // Immediately send a status update with the current location and task.
                setSendStatus();
                return true;
            }
            spentTime += stepTime;
            this.xPos += deltaX;
            this.yPos += deltaY;
            System.out.println(" [ DRONE TRAVEL ] Drone " + this.droneId + " is at         (" + this.xPos + "," +this.yPos + ") ");
        }
        System.out.println("\n [ DRONE TRAVEL ] Drone " + this.droneId + ": arrived at zone " + currTask.getZoneId() + " ready to deploy\n");
        return false;
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
     *     DRONE_ID:STATE:REQUEST_BODY:X_POS:Y_POS:CURRTASK
     @return String value of entire formatted request to send to scheduler based on current state
     and location.
     */
    private String makeRequest()
    {
        return this.droneId+":"+this.currentState.display()+":"+this.currentState.getRequest()+":"+(int) this.xPos+":"+(int) this.yPos+":"+this.currTask.toString();
    }

    /**
     * request format is:<p>
     *     DRONE_ID:STATE:STATUS:X_POS:Y_POS:CURRTASK
     @return String value of entire formatted request to send to scheduler based on current state
     and location.
     */
    private String makeStatusRequest()
    {
        return this.droneId+":"+this.currentState.display()+":"+DroneEvent.STATUS+":"+(int) this.xPos+":"+(int) this.yPos+":"+this.currTask.toString();
    }

    public String getLocation() {return "("+ this.xPos + "," + this.yPos+")";}

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
//            System.out.println("\n[ DRONE RUN ] starting DRONE RUN id key: " + this.droneId );
            String request;
            // check if drone should send its location status as a request, otherwise, sends normal request based on state
            if( checkSendStatus() ) request = makeStatusRequest();
            else                    request = makeRequest();

//            System.out.println("\n[ DRONE RUN ] making request DRONE RUN id key: " + this.droneId + " :         " + request);

            // adds the request to the router host
            this.droneSubsystem.addRequest( request );

            try
            {
                Thread.sleep(1);
                System.out.println("[ DRONE ]       is interrupted 1 =    " + Thread.currentThread().isInterrupted());
            } catch (InterruptedException e) { System.out.println("Travel interrupt Exception Caught"); }

            // check the droneSubsystem for next instructions for this drone
            String response = this.droneSubsystem.getResponse(this.droneId);
//            System.out.println("\n[ DRONE RUN ] got response from drone subsystem with id key: " + this.droneId + " :         " + response );
            // handle instructions given, will trigger travel()
            handleResponse(response);

            System.out.println("[ DRONE ]       is interrupted 2 =    " + Thread.currentThread().isInterrupted());

//            try{
//                Thread.sleep(1000);
//            } catch (InterruptedException e) {
//                // sleep interrupted
//            }
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

        String[] items = response.split(":");
        System.out.println("\n[ DRONE ] Items of fire request as an array: \n     " + Arrays.toString(items));

        String schedulerInstructions = items[0];

        // get drone id     -   in expected format "RESPONSE:DRONE_ID:STATE:REQUEST:X:Y"
        int droneId = -1;
        try {droneId = Integer.parseInt(items[1]);}
        catch (NumberFormatException e) {
            System.out.println("ERROR: Invalid int parsing handleDroneResponse");
            return;
        }

        if (schedulerInstructions.contains("STATUS"))
        {
            System.out.println("\n[ DRONE ] scheduler keyword is STATUS, returning from handleResponse:         " + Arrays.toString(items)+ "\n     ");
            return;
        }

        String droneState = items[2]; // get current state of this drone

        if (schedulerInstructions.equals("WAIT")) {
            System.out.println("\n[ DRONE ]   WAIT order -> drone " + droneId + " waiting for new task...");
            try{
                Thread.sleep(4000);
            } catch (InterruptedException e) {
                // sleep interrupted
            }
            String newResponse;
            do {
                try{
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // sleep interrupted
                }
                newResponse = droneSubsystem.getResponse(droneId);

            } while (newResponse.startsWith("WAIT")); // Continue if response still indicates WAIT
            handleResponse(newResponse);
        }

        // if schedulerInstructions is acknowledgement
        else if (schedulerInstructions.equals("ACK")) {

            // get the event request
            DroneEvent eventRequest;
            try {
                eventRequest = DroneEvent.valueOfEvent(items[3]);
            } catch (IllegalArgumentException e) {
                System.out.println("ERROR: Unknown drone event: " + items[3]);
                return;
            }
            if ( eventRequest.equals(DroneEvent.STATUS) )
            {
                // hit if Scheduler responds with ACK when it receives and processes the drones location. drone waits for next instruction
                System.out.println("\n[ DRONE ]   ACK  STATUS order -> drone " + droneId + "    DroneState.handleEvent called from current state ...    " +eventRequest.toString() );
                this.currentState.handleEvent(this, DroneEvent.STATUS);
            }
            else
            {
                System.out.println("\n[ DRONE ]   ACK  order -> drone " + droneId + " fulfilling event request ...    " +eventRequest.toString() );
                if( eventRequest.equals(DroneEvent.NEW_FIRE_REQUEST) )
                {
                    System.out.println("\n\n\n\n******************** SHOULD NEVER HIT as NEW_FIRE_REQUEST assignment moved **************   " + toString() + "\n\n\n\n");
                    String newFireRequest= items[6];
                    // handles if currently has a fire request
                    FireRequest newTask = new FireRequest(newFireRequest);
                    setCurrTask( newTask );
                }
                this.currentState.handleEvent(this, eventRequest);
            }
        }
        else if( schedulerInstructions.equals("NEW") )
        {
            // request = "NEW" in format NEW:DRONE_ID:STATE:FIREREQUEST:X:Y  - for reassigning current task and state
            String newFireRequest = items[6];
            System.out.println("\n[ DRONE ]   NEW  order -> drone " + droneId + " fulfilling new fire request ...    " +newFireRequest );
            FireRequest newTask = new FireRequest(newFireRequest);
            setCurrTask( newTask );

            // send an indication that this drone is now traveling expecting no response
            this.currentState.handleEvent(this, DroneEvent.NEW_FIRE_REQUEST);
            // set drone to travel
//            boolean travelIsInterrupted = travel();
        }
    }

    public void setBasePosition()
    {
        this.xPos = 0;
        this.yPos = 0;
    }
}
