/**
 * Interface for Drone context switching state pattern machine
 */
interface DroneState {

    /**
     * transitions to next state given the event passed in for the drone specified.
     * Also invokes any available actions for the drone.
     * @param drone the state is transitioning for this drone
     * @param event the event that causes the state transition
     */
    public void handleEvent(Drone drone, DroneEvent event);

    /**
     * @return string value for the current state of the drone
     */
    public String display();

    /**
     * when a drone is asking permission from the scheduler to
     * transission to the next state, it calls this function and
     * sends the contents as the request body in the UDP communication.<p>
     * This request corresponds to the next successful event a drone will
     * transition from. <p> see {@code enum DroneEvent} for the request body options
     * @return String value of the request body corresponding to the {@code enum DroneEvent}
     * */
    public String getRequest(Drone drone);
}


/**
 * state object for drone when it is in the "[IDLE]" state
 *      transitions to [IDLE]:  from DroneRefill only where Drone.currTask is set to default fire request
 *
 * */
class DroneIdle implements DroneState {


    /**
     * state transition event function for handling when there is a NEW_FIRE_REQUEST event for this drone
     * <p>1. sets state to DroneTravel
     * <p>2. calls Drone.travel() function
     * */
    @Override
    public void handleEvent(Drone drone, DroneEvent event) {
        if ( event.equals( DroneEvent.NEW_FIRE_REQUEST ) && drone.getCurrTask() != null ) {
            System.out.println("[ DRONE STATE ] drone " + drone.getDroneId() + " is now traveling to fire in zone " + drone.getCurrTask().getZoneId());
            drone.setState( new DroneActive(new DroneTravel()) );

            // if travel is interrupted, has to handle state transition (return to previous state)
            boolean travelInterrupted = drone.travel();
        }
        else if (  event.equals( DroneEvent.STATUS ) )
        {
            // returns to this point when scheduler acknowledges drones location and processes it. Waiting for next assignment from scheduler thread handling new assignments
            System.out.println("[ DRONE STATE ] drone " + drone.getDroneId() + " is waiting for next instruction after interrupt at    " + drone.getLocation());
        }
    }

    @Override
    public String display() {
        return "[IDLE]";
    }

    @Override
    public String getRequest(Drone drone) {
        return String.valueOf(DroneEvent.NEW_FIRE_REQUEST);
    }
}

class DroneActive implements DroneState {

    /**
     * Substate is the DroneState within the superstate DroneActive.
     * Substates for DroneActive include:
     * <p>DroneTravel, DroneDeploy, and DroneReturn
     * */
    private DroneState subState;

    public DroneActive(DroneState substate) {
        this.subState = substate;
    }

    @Override
    public void handleEvent(Drone drone, DroneEvent event) {
        this.subState.handleEvent(drone, event);
    }

    @Override
    public String display() {
        return "[ACTIVE]" + this.subState.display();
    }

    /**
     * Substate is the DroneState within the superstate DroneActive.
     * Substates for DroneActive include:
     * @return the substate of this Superstate:  DroneTravel, DroneDeploy, and DroneReturn
     * */
    public DroneState getSubState() {
        return this.subState;
    }

    @Override
    public String getRequest(Drone drone) {
        return this.subState.getRequest(drone);
    }
}

class DroneTravel implements DroneState
{

//    /**
//     constructor calls all entry and exit actions in sequence for this drone <
//     @param drone state belongs to this drone instance
//     */
//    public DroneTravel()
//    {
////        handleEntryAction( drone );
//    }

    @Override
    public void handleEvent(Drone drone, DroneEvent event)
    {
        if ( event.equals( DroneEvent.CONTINUING ) )
        {
            // continuing to answer the fire request after interrupted
            System.out.println("[ DRONE STATE ] drone " + drone.getDroneId() + " is continuing to answer the fire request after interrupted: " + drone.getCurrTask().getZoneId());
            drone.setState( new DroneActive(new DroneTravel()) );

            // if travel is interrupted, has to handle state transition (return to previous state)
            boolean travelInterrupted = drone.travel();

        }
        else if ( event.equals( DroneEvent.PERMISSION_TO_DROP ) )
        {
            // arrived at zone and is asking to open payload doors
            System.out.println("[ DRONE STATE ] drone " + drone.getDroneId() + " is given permission to drop payload on fire in zone " + drone.getCurrTask().getZoneId());
            drone.setState( new DroneActive(new DroneDeploy()) );

            // drop payload in 3 steps
            drone.activatePayloadDoors(); // open doors
            drone.dropPayload();            // drop payload
            drone.activatePayloadDoors(); // close doors
        }
        else if ( event.equals( DroneEvent.NEW_FIRE_REQUEST ) )
        {
            // assigned new fire request mid-travel
            System.out.println("[ DRONE STATE ] drone " + drone.getDroneId() + " at " + drone.getLocation()+ " has a new fire request and is now traveling to fire in zone " + drone.getCurrTask().getZoneId());
            // sets the same state of traveling but to a new zone
            drone.setState( new DroneActive(new DroneTravel()) );

            // if travel is interrupted, has to handle state transition (return to previous state)
            boolean travelInterrupted = drone.travel();
        }
        else if( event.equals( DroneEvent.DRONE_STUCK ) )
        {
            // drone became stuck during active flight travel
            System.out.println("[ DRONE STATE ] drone " + drone.getDroneId() + " is stuck during travel flight ");
            // set state to fault of stuck
            drone.setState( new DroneFault(new DroneFaultStuck()) );
        }
    }
    @Override
    public String display() {
        return "[TRAVELING]";
    }

    @Override
    public String getRequest(Drone drone) {
        // ITERATION 4
        if(drone.getIsStuck()) return String.valueOf(DroneEvent.DRONE_STUCK);

        if (drone.getContinueTravel()) {
            return String.valueOf(DroneEvent.CONTINUING);
        }

        return String.valueOf(DroneEvent.PERMISSION_TO_DROP);
    }
}

class DroneDeploy implements DroneState {

    @Override
    public void handleEvent(Drone drone, DroneEvent event) {
        if (event.equals( DroneEvent.PAYLOAD_DROPPED )) {
            // deployed payload successfully
            System.out.println("[ DRONE STATE ] drone " + drone.getDroneId() + " has deployed the payload and is returning to base");
            drone.setState( new DroneActive(new DroneReturn()) );

            // return travel
            drone.returnTravel();
        }
        else if (event.equals( DroneEvent.PAYLOAD_DEPLOY_FAILURE )) {
            // failed to deploy payload
            System.out.println("[ DRONE STATE ] drone " + drone.getDroneId() + " has failed to deploy the payload");
            drone.setState( new DroneFault(new DroneFaultDeploy()) );
        }
    }
    @Override
    public String display() {
        return "[DEPLOYING]";
    }

    @Override
    public String getRequest(Drone drone) {
        // ITERATION 4
        if(drone.getIsJammed()) return String.valueOf(DroneEvent.PAYLOAD_DEPLOY_FAILURE);

        return String.valueOf(DroneEvent.PAYLOAD_DROPPED);
    }
}

class DroneReturn implements DroneState {
    @Override
    public void handleEvent(Drone drone, DroneEvent event) {
        if ( event.equals( DroneEvent.RETURN_STATUS ) )
        {
            // continuing to return to base
            System.out.println("[ DRONE STATE ] drone " + drone.getDroneId() + " is continuing to return to base after sending status update" );
            drone.setState( new DroneActive(new DroneReturn()) );

            // if travel is interrupted, has to keep returning
            drone.returnTravel();
        }
        else if (event.equals( DroneEvent.RETURNED_TO_BASE )) {
            // arrived at base and is now refilling
            drone.setBasePosition();

            if (drone.getIsJammed()) {
                System.out.println("[ DRONE STATE ] drone "+drone.getDroneId()+" has returned to base following a deployment failure. Shutting down...");
                drone.setAlive();
            }
            // If the current task is default, transition to idle.
            else if (drone.getCurrTask().isDefault()) {
                System.out.println("[ DRONE STATE ] drone "+drone.getDroneId()+" has no active task; transitioning to IDLE state.");
                drone.setState(new DroneIdle());
            } else {
                System.out.println("[ DRONE STATE ] drone "+drone.getDroneId()+" active task exists; transitioning to DroneRefill state.");
                drone.setState(new DroneRefill());

                // load payload
                drone.loadPayload();
            }
            /*
            System.out.println("DRONE " + drone.getDroneId() + " has returned to base and is now refilling payload");
            drone.setState( new DroneRefill() );
            */
        }
        else if( event.equals( DroneEvent.DRONE_STUCK ) ) {
            // drone became stuck during active flight return
            System.out.println("[ DRONE STATE ] drone " + drone.getDroneId() +" is stuck during return flight");
            // set state to fault of stuck
            drone.setState( new DroneFault(new DroneFaultStuck()) );
        }
    }
    @Override
    public String display() {
        return "[RETURNING]";
    }

    @Override
    public String getRequest(Drone drone) {
        if(drone.getIsStuck()) return String.valueOf(DroneEvent.DRONE_STUCK);

        return String.valueOf(DroneEvent.RETURNED_TO_BASE);
    }
}

class DroneRefill implements DroneState {

    @Override
    public void handleEvent(Drone drone, DroneEvent event) {
        if (event.equals( DroneEvent.REFILL_COMPLETE )) {
            System.out.println("[ DRONE STATE ] drone "+drone.getDroneId()+" has refilled its payload successfully and is now idle");

            // set a default fire request
            drone.setCurrTask(new FireRequest());
//            System.out.println("[ DRONE STATE ] drone "+drone.getDroneId()+" has no active task; transitioning to IDLE state.");
            drone.setState(new DroneIdle());

//            if (drone.getCurrTask().isDefault()) {
//                System.out.println("[ DRONE STATE ] drone "+drone.getDroneId()+" has no active task; transitioning to IDLE state.");
//                drone.setState(new DroneIdle());
//            } else {
//                // For now, transition to idle.
//                drone.setState(new DroneIdle());}
        }
    }
    @Override
    public String display() {
        return "[REFILLING]";
    }

    @Override
    public String getRequest(Drone drone) {
        //TODO: ITERATION 4
        if(drone.getIsJammed()) return String.valueOf(DroneEvent.PAYLOAD_DEPLOY_FAILURE);

        return String.valueOf(DroneEvent.REFILL_COMPLETE);
    }
}

class DroneFault implements DroneState {

    private DroneState subState;

    public DroneFault(DroneState substate) {
        this.subState = substate;
    }

    @Override
    public void handleEvent(Drone drone, DroneEvent event) {
        this.subState.handleEvent(drone, event);
    }

    @Override
    public String display() {
        return "[FAULT]" + this.subState.display();
    }

    public DroneState getSubState() {
        return this.subState;
    }

    @Override
    public String getRequest(Drone drone) {
        return this.subState.getRequest(drone);
    }
}

class DroneFaultStuck implements DroneState {
    @Override
    public void handleEvent(Drone drone, DroneEvent event) {
        if (event.equals( DroneEvent.STUCK_RESOLVED )) {
            System.out.println("[ DRONE STATE ] drone "+drone.getDroneId()+" is no longer stuck and is returning to base");
            drone.setState( new DroneActive(new DroneReturn()) );
        }
        else if (event.equals( DroneEvent.DRONE_STUCK )) {
            System.out.println("[ DRONE STATE ] drone "+drone.getDroneId()+" is still stuck. Shutting down drone...");
            drone.setAlive();
        }
    }

    @Override
    public String display() {
        return "[STUCK]";
    }

    @Override
    public String getRequest(Drone drone) {
        if (drone.getIsStuck()) {
            return String.valueOf(DroneEvent.DRONE_STUCK);
        }
        return String.valueOf(DroneEvent.STUCK_RESOLVED);
    }
}

class DroneFaultDeploy implements DroneState {
    @Override
    public void handleEvent(Drone drone, DroneEvent event) {
        if (event.equals( DroneEvent.PAYLOAD_DEPLOY_FAILURE )) {
            System.out.println("[ DRONE STATE ] drone "+drone.getDroneId()+" is returning to base following a deployment failure");
            drone.setState( new DroneActive(new DroneReturn()) );
            drone.returnTravel(); // drone returns
        }
        else if ( event.equals( DroneEvent.PERMISSION_TO_DROP ) ) {
            // given instruction to drop payload again
            System.out.println("[ DRONE STATE ] drone "+drone.getDroneId()+" is requested again to drop payload on fire in zone " + drone.getCurrTask().getZoneId() + " following deployment failure");
            drone.setState( new DroneActive(new DroneDeploy()) );
        }
    }

    @Override
    public String display() {
        return "[DEPLOY FAILURE]";
    }

    @Override
    public String getRequest(Drone drone) {
        return String.valueOf(DroneEvent.PAYLOAD_DEPLOY_FAILURE);
    }
}


