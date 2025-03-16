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
     */
    public void travel() {
        int finalX, finalY;
        // Retrieve the zone from the FireIncidentSubsystem's static zoneMap using the current fire request's zone ID.
        Zone zone = FireIncidentSubsystem.zoneMap.get(currTask.getZoneId());
        if (zone != null) {
            // Calculate the center of the zone as the target destination.
            finalX = (zone.getStartX() + zone.getEndX()) / 2;
            finalY = (zone.getStartY() + zone.getEndY()) / 2;
        } else {
            finalX = 0;
            finalY = 0;
        }

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

        System.out.println("\n[ DRONE TRAVEL ] travel : deltaX=" + deltaX + ", deltaY=" + deltaY);

        double spentTime = 0;
        while (spentTime < travelTime) {
            try {
                Thread.sleep((long) stepTime);
            } catch (InterruptedException e) {
                System.out.println("\n [ DRONE TRAVEL ] Drone " + this.droneId + " interrupted during travel. Sending status update.");
                // Immediately send a status update with the current location and task.
                this.droneSubsystem.addRequest(makeStatusRequest());
                setSendStatus();
                return;
            }
            spentTime += stepTime;
            this.xPos += deltaX;
            this.yPos += deltaY;
            System.out.println("\n [ DRONE TRAVEL ] Drone " + this.droneId + " is at         (" + this.xPos + "," +this.yPos + ") \n");
        }
        System.out.println("\n [ DRONE TRAVEL ] Drone " + this.droneId + ": arrived at zone " + currTask.getZoneId() + " ready to deploy\n");
    }

    /*
    public void travel() {
        int finalX, finalY;
        // Retrieve the zone from the FireIncidentSubsystem's zoneMap.
        Zone zone = FireIncidentSubsystem.zoneMap.get(currTask.getZoneId());
        if (zone != null) {
            // Use the center of the zone as the target.
            finalX = (zone.getStartX() + zone.getEndX()) / 2;
            finalY = (zone.getStartY() + zone.getEndY()) / 2;
        } else {
            finalX = 0;
            finalY = 0;
        }

        // Calculate distance and travel time
        double distance = Math.sqrt(Math.pow(finalX - this.xPos, 2) + Math.pow(finalY - this.yPos, 2));
        double travelTime = (distance / this.maxVelocity) * 1000; // in milliseconds

        // Record starting positions
        double startX = this.xPos;
        double startY = this.yPos;
        double startTime = System.currentTimeMillis();

        // Update position until travel time is reached
        while (System.currentTimeMillis() - startTime < travelTime) {
            if (Thread.currentThread().isInterrupted()) {
                System.out.println("Drone " + this.droneId + " interrupted during travel. Sending status update.");
                this.droneSubsystem.addRequest(makeStatusRequest());
                setSendStatus();
                return;
            }
            try {
                Thread.sleep(100); // Update every 100ms
            } catch (InterruptedException e) {
                System.out.println("Drone " + this.droneId + " interrupted during travel (sleep). Sending status update.");
                this.droneSubsystem.addRequest(makeStatusRequest());
                setSendStatus();
                return;
            }
            double elapsed = System.currentTimeMillis() - startTime;
            double fraction = Math.min(elapsed / travelTime, 1.0); // Ensure it does not exceed 1.0
            this.xPos = startX + fraction * (finalX - startX);
            this.yPos = startY + fraction * (finalY - startY);
        }
        // Ensure final position is exactly at the target.
        this.xPos = finalX;
        this.yPos = finalY;
        System.out.println("Drone " + this.droneId + ": arrived at zone " + currTask.getZoneId() + " ready to deploy\n");
    }*/


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
        return this.droneId+":"+this.currentState.display()+":"+this.currentState.getRequest()+":"+(int) this.xPos+":"+(int) this.yPos+":"+this.currTask.toString();
    }

    /**
     * request format is:<p>
     *     DRONE_ID:STATE:STATUS:X_POS:Y_POS
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

            // check the droneSubsystem for next instructions for this drone
            String response = this.droneSubsystem.getResponse(this.droneId);
//            System.out.println("\n[ DRONE RUN ] got response from drone subsystem with id key: " + this.droneId + " :         " + response );
            // handle instructions given
            handleResponse(response);
            try{
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                // sleep interrupted
            }
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
            "ACK" in format ACK:DRONE_ID:STATE:REQUEST:X:Y:CURR_TASK      - for saying acknowledge
            "NEW" in format NEW:DRONE_ID:STATE:FIREREQUEST:X:Y:CURR_TASK  - for reassigning current task and state
            "WAIT" in format WAIT:DRONE_ID:STATE:REQUEST:X:Y:CURR_TASK    - for blocking after requesting a new fire request
         */
//        System.out.println("\n[ DRONE ] handleResponse called for :         " + response);
        String[] items = response.split(":");
        System.out.println("\n[ DRONE ] Items of fire request as an array: \n     " + Arrays.toString(items));
//        byte counter = 0;
//        for ( String item : items )
//        {
//            System.out.println( ++counter + ".    "+item);
//        }

//        if (items.length != 6) return;
        String schedulerInstructions = items[0];

        // get drone id     -   in expected format "RESPONSE:DRONE_ID:STATE:REQUEST:X:Y"
        int droneId = -1;
        try {droneId = Integer.parseInt(items[1]);}
        catch (NumberFormatException e) {
            System.out.println("ERROR: Invalid int parsing handleDroneResponse");
            return;
        }

        // get current state of this drone     -   in expected format "RESPONSE:DRONE_ID:STATE:REQUEST:X:Y:CURRTASK"
        String droneState = items[2];

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
            // If the response has at least 7 fields and the 7th field is "COMPLETED", process as payload dropped.
            //  "RESPONSE:DRONE_ID:STATE:REQUEST:X:Y:CURRTASK"

            // get the event request
            DroneEvent eventRequest;
            try {
                eventRequest = DroneEvent.valueOfEvent(items[3]);
            } catch (IllegalArgumentException e) {
                System.out.println("ERROR: Unknown drone event: " + items[3]);
                return;
            }

            if (items.length >= 7 && items[6].trim().equals("COMPLETED")) {
//                System.out.println("[ DRONE ] Received ACK with COMPLETED status.");
                System.out.println("\n[ DRONE ]   ACK COMPLETED order -> drone " + droneId + " drone returning from fire after ...    " +eventRequest.toString() );

                this.currentState.handleEvent(this, DroneEvent.PAYLOAD_DROPPED);
            } else {
                // Existing handling for other ACK types:
//                DroneEvent eventRequest;
//                try {
//                    eventRequest = DroneEvent.valueOfEvent(items[3]);
//                } catch (IllegalArgumentException e) {
//                    System.out.println("ERROR: Unknown drone event: " + items[3]);
//                    return;
//                }
                System.out.println("\n[ DRONE ]   ACK  order -> drone " + droneId + " fulfilling event request ...    " +eventRequest.toString() );

//                System.out.println("[ DRONE ] received event req ACK in handleResponse :         "+ eventRequest.toString());

                if( eventRequest.equals(DroneEvent.NEW_FIRE_REQUEST) )
                {
                    String newFireRequest= items[6]; //actually in item 6
                    // handles if currently has a fire request -> reassigning that
                    // sending to scheduler or to subsystem
                    FireRequest newTask = new FireRequest(newFireRequest);

                    setCurrTask( newTask );
                }
                this.currentState.handleEvent(this, eventRequest);
            }
        }

        else if( schedulerInstructions.equals("NEW") )
        {
            // new tasking for that drone
            // idle || doing something == rerouted
            // request = "NEW" in format NEW:DRONE_ID:STATE:FIREREQUEST:X:Y  - for reassigning current task and state

            // get request of this drone and convert it to DroneEvent
            String newFireRequest = items[6]; //actually in item 6

            System.out.println("\n[ DRONE ]   NEW  order -> drone " + droneId + " fulfilling new fire request ...    " +newFireRequest );

            // handles if currently has a fire request -> reassigning that
            // sending to scheduler or to subsystem
            FireRequest newTask = new FireRequest(newFireRequest);
            setCurrTask( newTask );

            this.currentState.handleEvent(this, DroneEvent.NEW_FIRE_REQUEST);

        }
    }

}
