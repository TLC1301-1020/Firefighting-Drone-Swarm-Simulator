
enum SchedulerEvent{
    REQUEST_RECEIVED,
    DRONE_CHOSEN,
    DRONE_ARRIVED,
    DRONE_STUCK,
    DRONE_DEPLOY_FAILURE,
    RESPONSE_SENT,
    DRONE_FINISHED
}

public interface SchedulerState {
    void handleEvent(Scheduler scheduler, SchedulerEvent event);
    String display();
}

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

class ProcessData implements SchedulerState {
    private SchedulerState subState;

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
