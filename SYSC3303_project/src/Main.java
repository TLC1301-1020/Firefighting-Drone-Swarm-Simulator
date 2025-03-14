///**
// * {@code Main} executable file for the fire-fighting drone embedded systems.
// */
//public class Main {
//
//    /**
//     * main method initializes the scheduler, starts subsystems, and runs the simulation
//     */
//    public static void main(String[] args) {
//        // Create the shared Scheduler
//        Scheduler scheduler = new Scheduler();
//
//        // Start the FireIncidentSubsystem
//        FireIncidentSubsystem fireIncidentSubsystem = new FireIncidentSubsystem(scheduler);
//        Thread fireThread = new Thread(fireIncidentSubsystem);
//        fireThread.start();
//
//        // Start the DroneSubsystem
//        DroneSubsystem droneSubsystem = new DroneSubsystem(scheduler);
//        scheduler.registerDrone(droneSubsystem);
//        Thread droneThread = new Thread(droneSubsystem);
//        droneThread.start();
//
//        System.out.println(" Fire Incident System is running...");
//        System.out.println(" Drone System is running...");
//    }
//}