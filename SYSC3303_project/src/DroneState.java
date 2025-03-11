enum DroneEvent {
    // ALL EVENTS GO HERE leading to state changes
    NEW_FIRE_REQUEST,
    PERMISSION_TO_DROP,
    PAYLOAD_DROPPED,
    PAYLOAD_DEPLOY_FAILURE,
    DEPLOY_FAILURE_ACKNOWLEDGED,
    RETURNED_TO_BASE,
    REFILL_COMPLETE,
    DRONE_STUCK,
    STUCK_RESOLVED
}

interface DroneState {
    public void handleEvent(DroneSubsystem drone, DroneEvent event);
    public String display();
}

class DroneIdle implements DroneState {

    public void handleEvent(DroneSubsystem drone, DroneEvent event) {
        if ( event.equals( DroneEvent.NEW_FIRE_REQUEST ) && drone.getCurrTask() != null ) {
            System.out.println("DRONE " + drone.getDroneId() + " is now engaging fire in zone " + drone.getCurrTask().getZoneId());
            drone.setState( new DroneActive(new DroneEngage()) );
        }
    }

    @Override
    public String display() {
        return "[IDLE]";
    }
}

//
//class DroneIdle implements DroneState {
//
//    public void handleEvent(DroneSubsystem drone, DroneEvent event) {
//        if ( event.equals( DroneEvent.NEW_FIRE_REQUEST ) && drone.getCurrTask() != null ) {
//            System.out.println("DRONE " + drone.getDroneId() + " is now engaging fire in zone " + drone.getCurrTask().getZoneId());
//            drone.setState( new DroneActive(new DroneEngage()) );
//        }
//    }
//
//    @Override
//    public String display() {
//        return "[IDLE]";
//    }
//}

class DroneActive implements DroneState {

    private DroneState subState;

    public DroneActive(DroneState substate) {
        this.subState = substate;
    }

    @Override
    public void handleEvent(DroneSubsystem drone, DroneEvent event) {
        this.subState.handleEvent(drone, event);
    }

    @Override
    public String display() {
        return "[ACTIVE]" + this.subState.display();
    }

    public DroneState getSubState() {
        return this.subState;
    }
}

class DroneEngage implements DroneState {

    @Override
    public void handleEvent(DroneSubsystem drone, DroneEvent event) {
        if ( event.equals( DroneEvent.PERMISSION_TO_DROP ) ) {
            // arrived at zone and is asking to open payload doors
            System.out.println("DRONE " + drone.getDroneId() + " is given permission to drop payload on fire in zone " + drone.getCurrTask().getZoneId());
            drone.setState( new DroneActive(new DroneDeploy()) );
        }
        else if ( event.equals( DroneEvent.NEW_FIRE_REQUEST ) ) {
            // assigned new fire request mid engagement
            System.out.println("DRONE " + drone.getDroneId() + " is now engaging fire in zone " + drone.getCurrTask().getZoneId());
            // sets the same state of engaging but to a new zone
            drone.setState( new DroneActive(new DroneEngage()) );
        }
        else if( event.equals( DroneEvent.DRONE_STUCK ) ) {
            // drone became stuck during active flight engagement
            System.out.println("DRONE " + drone.getDroneId() + " is stuck during engagement flight ");
            // set state to fault of stuck
            drone.setState( new DroneFault(new DroneFaultStuck()) );
        }
    }
    @Override
    public String display() {
        return "[ENGAGING]";
    }
}

class DroneDeploy implements DroneState {

    @Override
    public void handleEvent(DroneSubsystem drone, DroneEvent event) {
        if (event.equals( DroneEvent.PAYLOAD_DROPPED )) {
            // deployed payload successfully
            System.out.println("DRONE " + drone.getDroneId() + " has deployed the payload and is returning to base");
            drone.setState( new DroneActive(new DroneReturn()) );
        }
        else if (event.equals( DroneEvent.PAYLOAD_DEPLOY_FAILURE )) {
            // failed to deploy payload
            System.out.println("DRONE " + drone.getDroneId() + " has failed to deploy the payload");
            drone.setState( new DroneFault(new DroneFaultDeploy()) );
        }
    }
    @Override
    public String display() {
        return "[DEPLOYING]";
    }
}

class DroneReturn implements DroneState {
    @Override
    public void handleEvent(DroneSubsystem drone, DroneEvent event) {
        if (event.equals( DroneEvent.RETURNED_TO_BASE )) {
            // arrived at base and is now refilling
            System.out.println("DRONE " + drone.getDroneId() + " has returned to base and is now refilling payload");
            drone.setState( new DroneRefill() );
        }
        else if( event.equals( DroneEvent.DRONE_STUCK ) ) {
            // drone became stuck during active flight return
            System.out.println("DRONE " + drone.getDroneId() + " is stuck during return flight");
            // set state to fault of stuck
            drone.setState( new DroneFault(new DroneFaultStuck()) );
        }
    }
    @Override
    public String display() {
        return "[RETURNING]";
    }
}

class DroneRefill implements DroneState {

    @Override
    public void handleEvent(DroneSubsystem drone, DroneEvent event) {
        if (event.equals( DroneEvent.REFILL_COMPLETE )) {
            System.out.println("DRONE " + drone.getDroneId() + " has refilled its payload successfully and is now idle");
            drone.setState( new DroneIdle() );
        }
    }
    @Override
    public String display() {
        return "[REFILLING]";
    }
}

class DroneFault implements DroneState {

    private DroneState subState;

    public DroneFault(DroneState substate) {
        this.subState = substate;
    }

    @Override
    public void handleEvent(DroneSubsystem drone, DroneEvent event) {
        this.subState.handleEvent(drone, event);
    }

    @Override
    public String display() {
        return "[FAULT]" + this.subState.display();
    }

    public DroneState getSubState() {
        return this.subState;
    }
}

class DroneFaultStuck implements DroneState {
    @Override
    public void handleEvent(DroneSubsystem drone, DroneEvent event) {
        if (event.equals( DroneEvent.STUCK_RESOLVED )) {
            System.out.println("DRONE " + drone.getDroneId() + " is no longer stuck and is returning to base");
            drone.setState( new DroneActive(new DroneReturn()) );
        }
    }

    @Override
    public String display() {
        return "[STUCK]";
    }
}

class DroneFaultDeploy implements DroneState {
    @Override
    public void handleEvent(DroneSubsystem drone, DroneEvent event) {
        if (event.equals( DroneEvent.DEPLOY_FAILURE_ACKNOWLEDGED )) {
            System.out.println("DRONE " + drone.getDroneId() + " is returning to base following a deployment failure");
            drone.setState( new DroneActive(new DroneReturn()) );
        }
        else if ( event.equals( DroneEvent.PERMISSION_TO_DROP ) ) {
            // given instruction to drop payload again
            System.out.println("DRONE " + drone.getDroneId() + " is requested again to drop payload on fire in zone " + drone.getCurrTask().getZoneId() + " following deployment failure");
            drone.setState( new DroneActive(new DroneDeploy()) );
        }
    }

    @Override
    public String display() {
        return "[DEPLOY FAILURE]";
    }
}


