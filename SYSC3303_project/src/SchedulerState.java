/**
 * Enumeration representing all possible events the Scheduler can respond to.
 */
enum SchedulerEvent {
    /** A fire request has been received by the scheduler. */
    REQUEST_RECEIVED,
    /** A drone has been selected to handle a fire request. */
    DRONE_CHOSEN,
    /** A drone has arrived at the fire location. */
    DRONE_ARRIVED,
    /** A drone has encountered an issue and is stuck. */
    DRONE_STUCK,
    /** A drone failed to deploy its payload. */
    DRONE_DEPLOY_FAILURE,
    /** The scheduler has finished processing and is sending a response. */
    RESPONSE_SENT,
    /** A drone has completed its mission and returned. */
    DRONE_FINISHED
}

/**
 * Interface representing a state in the Scheduler's finite state machine.
 * Each state defines how to handle an incoming event and how to display its status.
 */
public interface SchedulerState {
    /**
     * Handles a given event based on the current state
     *
     * @param scheduler The scheduler instance whose state may be updated
     * @param event The event to be processed
     */
    void handleEvent(Scheduler scheduler, SchedulerEvent event);
    /**
     * @return A string describing the current state
     */
    String display();
}

/**
 * Represents the idle state of the Scheduler.
 * The scheduler remains idle until a request is received
 */
class Idle implements SchedulerState {
    @Override
    public void handleEvent(Scheduler scheduler, SchedulerEvent event) {
        if (event.equals(SchedulerEvent.REQUEST_RECEIVED)) {
            System.out.println("Scheduler received a request and is now processing it.");
            scheduler.setState(new ProcessData(new ReceiveData()));
        }
    }

    @Override
    public String display() {
        return "[IDLE]";
    }
}

/**
 * Composite state representing an active processing phase.
 * Delegates events to an inner sub state (eg receiving or sending data).
 */
class ProcessData implements SchedulerState {
    private SchedulerState subState;

    /**
     * Constructs a ProcessData state with a given substate.
     *
     * @param subState The substate (ReceiveData / SendData / ProcessData) to delegate events to
     */
    public ProcessData(SchedulerState subState) {
        this.subState = subState;
    }

    @Override
    public void handleEvent(Scheduler scheduler, SchedulerEvent event) {
        subState.handleEvent(scheduler, event);
    }

    @Override
    public String display() {
        return "[PROCESSING]" + subState.display();
    }
}

/**
 * Substate of ProcessData for receiving data or fire requests.
 */
class ReceiveData implements SchedulerState {
    @Override
    public void handleEvent(Scheduler scheduler, SchedulerEvent event) {
        if (event.equals(SchedulerEvent.DRONE_CHOSEN)) {
            System.out.println("Drone chosen for the fire request.");
            scheduler.setState(new ProcessData(new TaskDrone()));
        }
    }

    @Override
    public String display() {
        return "[RECEIVING DATA]";
    }
}


/**
 * Substate of ProcessData for assigning and managing a drone's task.
 */
class TaskDrone implements SchedulerState {
    @Override
    public void handleEvent(Scheduler scheduler, SchedulerEvent event) {
        if (event.equals(SchedulerEvent.DRONE_ARRIVED)) {
            System.out.println("Drone arrived at fire location.");
        } else if (event.equals(SchedulerEvent.DRONE_STUCK)) {
            System.out.println("Drone is stuck.");
            scheduler.setState(new Idle());
        } else if (event.equals(SchedulerEvent.DRONE_DEPLOY_FAILURE)) {
            System.out.println("Drone deployment failure, returning to idle.");
            scheduler.setState(new Idle());
        } else if (event.equals(SchedulerEvent.RESPONSE_SENT)) {
            System.out.println("Request processed, sending response.");
            scheduler.setState(new ProcessData(new SendData()));
        }
    }

    @Override
    public String display() {
        return "[TASKING DRONE]";
    }
}

/**
 * Substate of ProcessData for sending response data back after a drone task is completed.
 */
class SendData implements SchedulerState {
    @Override
    public void handleEvent(Scheduler scheduler, SchedulerEvent event) {
        if (event.equals(SchedulerEvent.DRONE_FINISHED)) {
            System.out.println("Drone has completed the task and returned.");
            scheduler.setState(new Idle());
        }
    }

    @Override
    public String display() {
        return "[SENDING DATA]";
    }
}
