import static org.junit.Assert.*;
import org.junit.*;

public class DroneSubsystemTest
{
    /**
     * UNIT TEST: Verify that a drone can switch states from IDLE TO ENGAGE */
    @Test
    public void UNIT_TEST_01()
    {
        System.out.println("\nVerify that a drone can switch states from IDLE TO ENGAGE");
        DroneSubsystem drone = new DroneSubsystem(null);

        assertTrue( drone.getCurrentState() instanceof DroneIdle );

        // test to see if switching drone to active with no fire request remains in idle
        drone.handleEvent( DroneEvent.NEW_FIRE_REQUEST );
        assertFalse( drone.getCurrentState() instanceof DroneActive );

        // add fire request so event can switch states as intended
        FireRequest task = new FireRequest("00:00:00", 7, "FIRE_DETECTED", "High");
        drone.setCurrTask(task);
        drone.handleEvent( DroneEvent.NEW_FIRE_REQUEST );

        assertTrue( drone.getCurrentState() instanceof DroneActive );
        assertTrue( ((DroneActive) drone.getCurrentState()).getSubState() instanceof DroneEngage );
    }

    /**
     * UNIT TEST: Verify that a drone can switch states from ENGAGE TO STUCK */
    @Test
    public void UNIT_TEST_02()
    {
        System.out.println("\nVerify that a drone can switch states from ENGAGE TO STUCK");

        DroneSubsystem drone = new DroneSubsystem(null);

        drone.setState( new DroneActive(new DroneEngage()) );

        assertTrue( drone.getCurrentState() instanceof DroneActive );
        assertTrue( ((DroneActive) drone.getCurrentState()).getSubState() instanceof DroneEngage );

        drone.handleEvent( DroneEvent.DRONE_STUCK );

        assertTrue( drone.getCurrentState() instanceof DroneFault );
        assertTrue( ((DroneFault) drone.getCurrentState()).getSubState() instanceof DroneFaultStuck );
    }

    /**
     * UNIT TEST: Verify that a drone can switch states from ENGAGE TO DRONE DEPLOY */
    @Test
    public void UNIT_TEST_03()
    {
        System.out.println("\nVerify that a drone can switch states from ENGAGE TO DRONE DEPLOY");
        DroneSubsystem drone = new DroneSubsystem(null);

        drone.setState( new DroneActive(new DroneEngage()) );

        assertTrue( drone.getCurrentState() instanceof DroneActive );
        assertTrue( ((DroneActive) drone.getCurrentState()).getSubState() instanceof DroneEngage );

        // add fire request so event can switch states as intended
        FireRequest task = new FireRequest("00:00:00", 7, "FIRE_DETECTED", "High");
        drone.setCurrTask(task);
        drone.handleEvent( DroneEvent.PERMISSION_TO_DROP );

        assertTrue( drone.getCurrentState() instanceof DroneActive );
        assertTrue( ((DroneActive) drone.getCurrentState()).getSubState() instanceof DroneDeploy );
    }

    /**
     * UNIT TEST: Verify that a drone can switch states from DRONE DEPLOY TO DRONE RETURN */
    @Test
    public void UNIT_TEST_04()
    {
        System.out.println("\nVerify that a drone can switch states from DRONE DEPLOY TO DRONE RETURN");
        DroneSubsystem drone = new DroneSubsystem(null);

        drone.setState( new DroneActive(new DroneDeploy()) );

        assertTrue( drone.getCurrentState() instanceof DroneActive );
        assertTrue( ((DroneActive) drone.getCurrentState()).getSubState() instanceof DroneDeploy );

        drone.handleEvent( DroneEvent.PAYLOAD_DROPPED );

        assertTrue( drone.getCurrentState() instanceof DroneActive );
        assertTrue( ((DroneActive) drone.getCurrentState()).getSubState() instanceof DroneReturn );
    }

    /**
     * UNIT TEST: Verify that a drone can switch states from DRONE DEPLOY TO DRONE FAULT DEPLOY */
    @Test
    public void UNIT_TEST_05()
    {
        System.out.println("\nVerify that a drone can switch states from DRONE DEPLOY TO DRONE FAULT DEPLOY");
        DroneSubsystem drone = new DroneSubsystem(null);

        drone.setState( new DroneActive(new DroneDeploy()) );

        assertTrue( drone.getCurrentState() instanceof DroneActive );
        assertTrue( ((DroneActive) drone.getCurrentState()).getSubState() instanceof DroneDeploy );

        drone.handleEvent( DroneEvent.PAYLOAD_DEPLOY_FAILURE );

        assertTrue( drone.getCurrentState() instanceof DroneFault );
        assertTrue( ((DroneFault) drone.getCurrentState()).getSubState() instanceof DroneFaultDeploy );
    }

    /**
     * UNIT TEST: Verify that a drone can switch states from DRONE FAULT DEPLOY TO DEPLOY */
    @Test
    public void UNIT_TEST_06()
    {
        System.out.println("\nVerify that a drone can switch states from DRONE FAULT DEPLOY TO DEPLOY");
        DroneSubsystem drone = new DroneSubsystem(null);

        drone.setState( new DroneFault(new DroneFaultDeploy()) );

        assertTrue( drone.getCurrentState() instanceof DroneFault );
        assertTrue( ((DroneFault) drone.getCurrentState()).getSubState() instanceof DroneFaultDeploy );

        // add fire request so event can switch states as intended
        FireRequest task = new FireRequest("00:00:00", 7, "FIRE_DETECTED", "High");
        drone.setCurrTask(task);
        drone.handleEvent( DroneEvent.PERMISSION_TO_DROP );

        assertTrue( drone.getCurrentState() instanceof DroneActive );
        assertTrue( ((DroneActive) drone.getCurrentState()).getSubState() instanceof DroneDeploy );
    }

    /**
     * UNIT TEST: Verify that a drone can switch states from DRONE FAULT DEPLOY TO RETURN */
    @Test
    public void UNIT_TEST_07()
    {
        System.out.println("\nVerify that a drone can switch states from DRONE FAULT DEPLOY TO RETURN");
        DroneSubsystem drone = new DroneSubsystem(null);

        drone.setState( new DroneFault(new DroneFaultDeploy()) );

        assertTrue( drone.getCurrentState() instanceof DroneFault );
        assertTrue( ((DroneFault) drone.getCurrentState()).getSubState() instanceof DroneFaultDeploy );

        drone.handleEvent( DroneEvent.DEPLOY_FAILURE_ACKNOWLEDGED );

        assertTrue( drone.getCurrentState() instanceof DroneActive );
        assertTrue( ((DroneActive) drone.getCurrentState()).getSubState() instanceof DroneReturn );
    }

    /**
     * UNIT TEST: Verify that a drone can switch states from RETURN TO REFILL */
    @Test
    public void UNIT_TEST_08()
    {
        System.out.println("\nVerify that a drone can switch states from RETURN TO REFILL");
        DroneSubsystem drone = new DroneSubsystem(null);

        drone.setState( new DroneActive(new DroneReturn()) );

        assertTrue( drone.getCurrentState() instanceof DroneActive );
        assertTrue( ((DroneActive) drone.getCurrentState()).getSubState() instanceof DroneReturn );

        drone.handleEvent( DroneEvent.RETURNED_TO_BASE );

        assertTrue( drone.getCurrentState() instanceof DroneRefill );
    }

    /**
     * UNIT TEST: Verify that a drone can switch states from RETURN TO STUCK */
    @Test
    public void UNIT_TEST_09()
    {
        System.out.println("\nVerify that a drone can switch states from RETURN TO STUCK");
        DroneSubsystem drone = new DroneSubsystem(null);

        drone.setState( new DroneActive(new DroneReturn()) );

        assertTrue( drone.getCurrentState() instanceof DroneActive );
        assertTrue( ((DroneActive) drone.getCurrentState()).getSubState() instanceof DroneReturn );

        drone.handleEvent( DroneEvent.DRONE_STUCK );

        assertTrue( drone.getCurrentState() instanceof DroneFault );
        assertTrue( ((DroneFault) drone.getCurrentState()).getSubState() instanceof DroneFaultStuck );
    }

    /**
     * UNIT TEST: Verify that a drone can switch states from STUCK TO STUCK RESOLVED */
    @Test
    public void UNIT_TEST_10()
    {
        System.out.println("\nVerify that a drone can switch states from STUCK TO STUCK RESOLVED");
        DroneSubsystem drone = new DroneSubsystem(null);

        drone.setState( new DroneFault(new DroneFaultStuck()) );

        assertTrue( drone.getCurrentState() instanceof DroneFault );
        assertTrue( ((DroneFault) drone.getCurrentState()).getSubState() instanceof DroneFaultStuck );

        drone.handleEvent( DroneEvent.STUCK_RESOLVED );

        assertTrue( drone.getCurrentState() instanceof DroneActive );
        assertTrue( ((DroneActive) drone.getCurrentState()).getSubState() instanceof DroneReturn );
    }

    /**
     * UNIT TEST: Verify that a drone can switch states from REFILL TO IDLE */
    @Test
    public void UNIT_TEST_11()
    {
        System.out.println("\nVerify that a drone can switch states from REFILL TO IDLE");
        DroneSubsystem drone = new DroneSubsystem(null);

        drone.setState( new DroneRefill() );

        assertTrue( drone.getCurrentState() instanceof DroneRefill );

        drone.handleEvent( DroneEvent.REFILL_COMPLETE );

        assertTrue( drone.getCurrentState() instanceof DroneIdle );
    }

    /**
     * INTEGRATION TEST: Verify that a drone Cycle States from Idle through answering a fire request,
     *  then returning to base to an idle state, able to receive another fire request */
    @Test
    public void STATE_CYCLE_TEST()
    {
        System.out.println("\nVerify that a drone Cycle States from Idle through answering a fire request," +
                " then returning to base to an idle state, able to receive another fire request");
        DroneSubsystem drone = new DroneSubsystem(null);

        assertTrue( drone.getCurrentState() instanceof DroneIdle );

        // add fire request so event can switch states as intended
        FireRequest task1 = new FireRequest("00:00:00", 7, "FIRE_DETECTED", "High");
        drone.setCurrTask(task1);
        drone.handleEvent( DroneEvent.NEW_FIRE_REQUEST );

        assertTrue( drone.getCurrentState() instanceof DroneActive );
        assertTrue( ((DroneActive) drone.getCurrentState()).getSubState() instanceof DroneEngage );

        drone.handleEvent( DroneEvent.PERMISSION_TO_DROP );

        assertTrue( drone.getCurrentState() instanceof DroneActive );
        assertTrue( ((DroneActive) drone.getCurrentState()).getSubState() instanceof DroneDeploy );

        drone.handleEvent( DroneEvent.PAYLOAD_DROPPED );

        assertTrue( drone.getCurrentState() instanceof DroneActive );
        assertTrue( ((DroneActive) drone.getCurrentState()).getSubState() instanceof DroneReturn );

        drone.handleEvent( DroneEvent.RETURNED_TO_BASE );

        assertTrue( drone.getCurrentState() instanceof DroneRefill );

        drone.handleEvent( DroneEvent.REFILL_COMPLETE );

        assertTrue( drone.getCurrentState() instanceof DroneIdle );

        // add fire request so event can switch states as intended
        FireRequest task2 = new FireRequest("01:00:00", 5, "FIRE_DETECTED", "Moderate");
        drone.setCurrTask(task2);
        drone.handleEvent( DroneEvent.NEW_FIRE_REQUEST );
    }

}
