import java.util.Arrays;

public class Drone extends Thread
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
    private final float maxVelocity = 400;
    /**
     * number of deltaX deltaY increment the drone travels before sending a location update to scheduler  */
    private final float TRAVEL_INCREMENTS = 30;
    /**
     *  time for doors to open or close in ms */
    private final int APPARATUS_DOORS_MOVE_TIME = 10;
    /**
     *  drop time for each FEB to drop in ms */
    private final int PAYLOAD_DROP_TIME = 30;
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

    /**
     * Used by the DroneTravel state getRequest() to send back the appropriate DroneEvent
     */
    private boolean continueTravel = true;

    /**
     * Booleans to determine whether this Drone should encounter a respective fault
     */
    private boolean isStuck = false;
    private Object jammedLock = new Object();
    private boolean isJammed = false;
    private boolean packetLoss = false;

    /**
     * Boolean to determine lifespan of this Drone thread, flipped by an unrecoverable fault
     */
    private boolean alive = true;

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

        System.out.println("* DRONE STATE CHANGE * " + oldState.display() + " -> " + this.currentState.display() + "\n\n");
        DroneEventLogger.getInstance().info("Drone", String.valueOf(this.droneId), "State Change: " + oldState.display() + "->" + this.currentState.display());
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
        DroneEventLogger.getInstance().info("Drone", String.valueOf(this.droneId), "Assigned Task: " + this.currTask.toString());
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
     * Getter for continueTravel
     * @return value of continueTravel
     */
    public boolean getContinueTravel() { return continueTravel; }


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

        DroneEventLogger.getInstance().info("Drone", String.valueOf(this.droneId), "Beginning travel to zone " + zone.toString());

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

        double spentTime = 0;
        for ( int i = 0 ; (i < TRAVEL_INCREMENTS) ; ++i )
        {
            try {
                Thread.sleep((long) stepTime);
            } catch (InterruptedException e) {
                System.out.println("\n[ DRONE TRAVEL ]      Drone " + this.droneId + " interrupted during travel. Sending status update.");
                // Immediately send a status update with the current location and task.
//                setSendStatus();
                return true;
            }
            spentTime += stepTime;
            this.xPos += deltaX;
            this.yPos += deltaY;
            System.out.println(" [ DRONE TRAVEL ]       Drone " + this.droneId + " is at         (" + String.format("%.2f",this.xPos) + "," +String.format("%.2f",this.yPos) + ") ");
        }
        if(spentTime>=travelTime)
        {
            this.xPos=finalX;
            this.yPos=finalY;
            System.out.println("\n [ DRONE TRAVEL ]     Drone " + this.droneId + ": arrived at zone " + currTask.getZoneId() + " ready to deploy\n");

            DroneEventLogger.getInstance().info("Drone", String.valueOf(this.droneId), "Arrived at zone " + zone.toString());

            // Set boolean to determine that we actually arrived in the zone
            this.continueTravel = false;
        }
        else
        {
            setSendStatus();
            System.out.println("\n [ DRONE TRAVEL ]     Drone " + this.droneId + ": sending status \n");
        }
        return false;
    }

    /**
     * Simulates the drone's payload doors opening and closing, checks for faults */
    public void activatePayloadDoors()
    {
        System.out.println("[ DRONE ]      Drone " + this.droneId + ": ACTIVATING FEB APPARATUS DOORS ");
        DroneEventLogger.getInstance().info("Drone", String.valueOf(this.droneId), "Opening payload doors");

        // check fault
        try {
            Thread.sleep(APPARATUS_DOORS_MOVE_TIME);
        } catch (InterruptedException e) {
            System.out.println("\n[ DRONE ]      Drone " + this.droneId + ": interrupted during activateApparatusDoors");
            // fault
        }
    }

    /**
     * Simulates the drone dropping its payload one at a time, decrements the payloadCount as they drop, checks for faults */
    public void dropPayload()
    {
        if(this.payloadCount==0)
        {
            System.out.println("[ DRONE ]      Drone " + this.droneId + ": FAULT DROPPING PAYLOAD - EMPTY PAYLOAD");
            // fault
        }

        DroneEventLogger.getInstance().info("Drone", String.valueOf(this.droneId), "Deploying payload");

        while (this.payloadCount>0)
        {
            if(checkJammedFault()){
                System.out.println("[ DRONE ]      Drone " + this.droneId + ": FAULT DROPPING PAYLOAD - JAMMED FAULT");
                return;
            }

            // check for fault (stuck?)
            System.out.println("[ DRONE ]      Drone " + this.droneId + ": DROPPING PAYLOAD #" +this.payloadCount);
            this.payloadCount--;
            try {
                Thread.sleep(PAYLOAD_DROP_TIME);
            } catch (InterruptedException e) {
                System.out.println("\n[ DRONE ]      Drone " + this.droneId + ": interrupted during dropPayload");
                // fault
            }
        }

        DroneEventLogger.getInstance().info("Drone", String.valueOf(this.droneId), "Deployment of payload complete");
    }

    /**
     * Simulates the drone loading payload, sets the payload count to MAX_PAYLOAD */
    public void loadPayload()
    {
        DroneEventLogger.getInstance().info("Drone", String.valueOf(this.droneId), "Refilling payload");

        System.out.println("[ DRONE ]      Drone " + this.droneId + ": LOADING PAYLOAD FROM " +this.payloadCount + " FEBs ");
        try {
            Thread.sleep(2*APPARATUS_DOORS_MOVE_TIME);
        } catch (InterruptedException e) {
            System.out.println("\n[ DRONE ]      Drone " + this.droneId + ": interrupted during activateApparatusDoors");
            // fault
        }
        this.payloadCount = MAX_PAYLOAD;
        System.out.println("[ DRONE ]      Drone " + this.droneId + ": LOADED TO " +this.payloadCount + " FEBs ");

        DroneEventLogger.getInstance().info("Drone", String.valueOf(this.droneId), "Payload refilled");
    }


    /**
     * simulates drone travel back to the (0, 0) coordinate (base) once it has dropped its payload
     * and cannot be interrupted during return travel
     */
    public void returnTravel()
    {
        int finalX = 0;
        int finalY = 0;
        System.out.println( " \nTRAVEL BACK : "  + finalX + "," + finalY + "    FEB count :" + this.payloadCount);

        DroneEventLogger.getInstance().info("Drone", String.valueOf(this.droneId), "Beginning travel to base");

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

        double spentTime = 0;
        for ( int i = 0 ; (i < TRAVEL_INCREMENTS) ; ++i )
        {
            try {
                Thread.sleep((long) stepTime);
            } catch (InterruptedException e) {
                System.out.println("\n[ DRONE RETURN ]      Drone " + this.droneId + ": interrupted during return travel. Sending status update.");
                setSendStatus();
            }
            spentTime += stepTime;
            this.xPos += deltaX;
            this.yPos += deltaY;
            if( this.xPos < 0 ) break; // exits travel function if returned
            System.out.println(" [ DRONE RETURN ]       Drone " + this.droneId + ": is at         (" + String.format("%.2f",this.xPos) + "," +String.format("%.2f",this.yPos) + ") ");
        }
        if(spentTime>=travelTime)
        {
            this.xPos=finalX;
            this.yPos=finalY;
            System.out.println("\n [ DRONE RETURN ]     Drone " + this.droneId + ": arrived back at base ready to refill\n");

            DroneEventLogger.getInstance().info("Drone", String.valueOf(this.droneId), "Arrived back at base");
        }
        else
        {
            setSendStatus();
            System.out.println("\n [ DRONE RETURN ]     Drone " + this.droneId + ": sending status \n");
        }
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
        return this.droneId+":"+this.currentState.display()+":"+this.currentState.getRequest(this)+":"+(int) this.xPos+":"+(int) this.yPos+":"+this.currTask.toString();
    }

    /**
     * request format is:<p>
     *     DRONE_ID:STATE:STATUS:X_POS:Y_POS:CURRTASK
     @return String value of entire formatted request to send to scheduler based on current state
     and location.
     */
    private String makeStatusRequest()
    {
        DroneEvent status = DroneEvent.STATUS;
        if(this.currentState.display().equals("[ACTIVE][RETURNING]") ) status = DroneEvent.RETURN_STATUS;    // check if this is a return or travel status
        return this.droneId+":"+this.currentState.display()+":"+status+":"+(int) this.xPos+":"+(int) this.yPos+":"+this.currTask.toString();
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
        while (alive)
        {
            // Determine whether this Drone should keep running
            if (!alive) {
                return;
            }
//            System.out.println("\n[ DRONE RUN ] starting DRONE RUN id key: " + this.droneId );
            String request;

//            System.out.println("\n[ DRONE RUN ] making request DRONE RUN id key: " + this.droneId + " :         " + request);

            // If this drone is injected with a PACKET_LOSS fault, send corrupted packet instead
            if (packetLoss) {
                request = this.droneId+":"+this.currentState.display()+":"+"CORRUPTED_PACKET"+":"+(int) this.xPos+":"+(int) this.yPos+":"+this.currTask.toString();
                packetLoss = false;
            }
            // check if drone should send its location status as a request, otherwise, sends normal request based on state
            else if (checkSendStatus()) {
                request = makeStatusRequest();
            }
            else {
                request = makeRequest();
            }

            // adds the request to the router host
            this.droneSubsystem.addRequest( request );


            // check the droneSubsystem for next instructions for this drone
            String response = this.droneSubsystem.getResponse(this.droneId);
            if (response == null) {
                System.out.println("[ DRONE ] Drone " + droneId + " shutting down.");
                return;
            }

            DroneEventLogger.getInstance().info("Drone", String.valueOf(this.droneId), "Received a Scheduler response");
//            System.out.println("\n[ DRONE RUN ] got response from drone subsystem with id key: " + this.droneId + " :         " + response );

            // First check to see if Scheduler needs a resend of last request
            if (response.contains("RESEND")) {
                continue;
            } else if ( response.equals("SHUTDOWN"))
            {
                // shutdown received from DSS
                setAlive();
                break;
            }

            // handle instructions given, will trigger travel()
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

        String[] items = response.split(":");
        System.out.println("[ DRONE ] Items of fire request as an array:                        " + Arrays.toString(items));

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
            this.sendStatus = true;

            while (true) {
                try {
                    Thread.sleep(500);  // wait before pinging
                } catch (InterruptedException e) {
                    // interrupted
                }

                // Send STATUS to Scheduler to check for assignment
                String statusRequest = makeStatusRequest();
                droneSubsystem.addRequest(statusRequest);

                String newResponse = droneSubsystem.getResponse(droneId);

                if (newResponse == null) {
                    System.out.println("[ DRONE " + droneId + " ] Shutdown detected while waiting for new task.");
                    return;
                }

                if (newResponse.startsWith("WAIT")) {
                    continue; // still waiting, continue loop
                } else if (newResponse.startsWith("ACK") && newResponse.contains("STATUS")) {
                    // still just getting status acks, ignore
                    System.out.println("[ DRONE ] Still waiting... got STATUS ACK.");
                    continue;
                } else {
                    // this must be a NEW or a non-status ACK (like NEW_FIRE_REQUEST)
                    handleResponse(newResponse);
                    break;
                }
            }
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
                System.out.println("\n[ DRONE ]   ACK  STATUS order -> drone " + droneId + "    DroneState.handleEvent called from current state (" + this.currentState.display() + ")  ...    " +eventRequest.toString() );
                this.currentState.handleEvent(this, DroneEvent.STATUS);
            }
            else if ( eventRequest.equals(DroneEvent.RETURN_STATUS) )
            {
                // hit if Scheduler responds with ACK when it receives and processes the drones location on return travel. drone waits for next instruction
                System.out.println("\n[ DRONE ]   ACK  RETURN_STATUS order -> drone " + droneId + "    DroneState.handleEvent called from current state (" + this.currentState.display() + ")  ...    " +eventRequest.toString() );
                this.currentState.handleEvent(this, DroneEvent.RETURN_STATUS);
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

            // Set boolean to continue travelling
            continueTravel = true;

            // send an indication that this drone is now traveling expecting no response
            this.currentState.handleEvent(this, DroneEvent.NEW_FIRE_REQUEST);
            // set drone to travel
//            boolean travelIsInterrupted = travel();
        }
    }

    /**
     * Set the isStuck boolean.
     */
    public void setStuckFault() {
        this.isStuck = true;
    }

    /**
     * Set the isJammed boolean.
     */
    public void setJammedFault() {
        synchronized (jammedLock){
            this.isJammed = true;
        }
    }

    public boolean checkJammedFault(){
        boolean value = false;
        synchronized (jammedLock){
            value = this.isJammed;
        }
        return value;
    }

    /**
     * Set the packetLoss boolean.
     */
    public void setPacketLossFault() {
        this.packetLoss = true;
    }

    /**
     * Set the alive boolean to false.
     */
    public void setAlive(boolean val) {
        this.alive = val;
    }

    public void setBasePosition()
    {
        this.xPos = 0;
        this.yPos = 0;
    }
    public boolean getIsJammed(){
        return isJammed;
    }
    public boolean getIsStuck(){
        return isStuck;
    }
}
