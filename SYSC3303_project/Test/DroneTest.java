import org.junit.jupiter.api.Test;

import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.DatagramSocket;

public class DroneTest
{
    void resetDroneSubsystem(DroneSubsystem dss) throws NoSuchFieldException, IllegalAccessException
    {
        Field sendSocketField = DroneSubsystem.class.getDeclaredField("sendSocket");
        sendSocketField.setAccessible(true);

        // access the socket instance from the field
        DatagramSocket sendSocket = (DatagramSocket) sendSocketField.get(dss);

        // Close the socket
        if (sendSocket != null && !sendSocket.isClosed())
        {
            sendSocket.close();
        }

        // access the socket instance from the field
        Field receiveSocketField = DroneSubsystem.class.getDeclaredField("receiveSocket");
        receiveSocketField.setAccessible(true);
        DatagramSocket receiveSocket = (DatagramSocket) receiveSocketField.get(dss);
        if (receiveSocket != null && !receiveSocket.isClosed())
        {
            receiveSocket.close();
        }
    }

    /**
     * UNIT TEST: verify is default for instantiating default constructor fireRequest Objects */
    @Test
    void testFireRequestIsDefault() throws Exception
    {
        FireRequest fr = new FireRequest();
        assertEquals("FireRequest{time=0, zone=-1, event=0, severity=0}", fr.toString());
        assertTrue(fr.isDefault());
    }

    /**
     * UNIT TEST: checks if drone is correctly initialized to interact with system */
    @Test
    void testDroneInitializesAllFields() throws Exception
    {
        DroneSubsystem dss = new DroneSubsystem();

        Drone drone = new Drone(dss, 1);

        // verify currentState is DroneIdle
        assertTrue(drone.getCurrentState() instanceof DroneIdle);

        // verify drone id
        assertEquals(1, drone.getDroneId());

        // verify currTask is default
        Field currTaskField = Drone.class.getDeclaredField("currTask");
        currTaskField.setAccessible(true);
        FireRequest currTask = (FireRequest)currTaskField.get(drone);
        assertNotNull(currTask);
        assertTrue(currTask.isDefault());

        // verify xPos and yPos == 0.0
        Field xPosField = Drone.class.getDeclaredField("xPos");
        xPosField.setAccessible(true);
        Field yPosField = Drone.class.getDeclaredField("yPos");
        yPosField.setAccessible(true);
        assertTrue(0.0 == (double) xPosField.get(drone));
        assertTrue(0.0 == (double) yPosField.get(drone));

        // verify payloadCount == 10 (MAX_PAYLOAD)
        Field payloadCountField = Drone.class.getDeclaredField("payloadCount");
        payloadCountField.setAccessible(true);
        assertEquals(10, payloadCountField.get(drone));

        // verify sendStatus == false
        Field sendStatusField = Drone.class.getDeclaredField("sendStatus");
        sendStatusField.setAccessible(true);
        assertFalse((boolean) sendStatusField.get(drone));

        // verify continueTravel == true
        Field continueTravelField = Drone.class.getDeclaredField("continueTravel");
        continueTravelField.setAccessible(true);
        assertTrue((boolean) continueTravelField.get(drone));

        // verify dss is not null
        Field droneSubsystem = Drone.class.getDeclaredField("droneSubsystem");
        droneSubsystem.setAccessible(true);
        assertNotNull(droneSubsystem);

        resetDroneSubsystem(dss);
    }

    /**
     * UNIT TEST: checks if drone correctly stores the assigned task and can retreive it */
    @Test
    void test_setCurrTask_getCurrTask() throws NoSuchFieldException, IllegalAccessException {
        DroneSubsystem dss = new DroneSubsystem();
        Drone drone = new Drone(dss, 1);

        // verify default task
        FireRequest initialTask = drone.getCurrTask();
        assertNotNull(initialTask);
        assertTrue(initialTask.toString().equals("FireRequest{time=0, zone=-1, event=0, severity=0}"));

        // make new task using full string constructor
        FireRequest newTask = new FireRequest("FireRequest{time=10-30-15, zone=4, event=FIRE_DETECTED, severity=High}");

        // set new task and retrieve the old one
        FireRequest oldTask = drone.setCurrTask(newTask);

        // oldTask should be the initial one
        assertTrue(oldTask.toString().equals("FireRequest{time=0, zone=-1, event=0, severity=0}"));

        // Now getCurrTask should return the new one
        FireRequest currentTask = drone.getCurrTask();
        assertTrue(currentTask.toString().equals("FireRequest{time=10-30-15, zone=4, event=FIRE_DETECTED, severity=High}"));

        resetDroneSubsystem(dss);
    }

    /**
     * UNIT TEST: checks if drone correctly travels on state transition NEW_FIRE_REQUEST, sets send status, continues traveling with state transition to Continue
     * arrives at zone, and sends permission to drop to scheduler*/
    @Test
    void test_drone_travel() throws NoSuchFieldException, IllegalAccessException, InvocationTargetException, NoSuchMethodException
    {
        DroneSubsystem dss = new DroneSubsystem();
        Drone drone = new Drone(dss, 1);

        // Create and store the zone
        int zoneId = 4;
        Zone zone = new Zone(zoneId, 700, 0, 1000, 600); // center should be (850, 300)
        DroneSubsystem.zoneMap.put(zoneId, zone);

        // Assign fire request corresponding to that zone
        FireRequest task = new FireRequest("FireRequest{time=10-30-15, zone=4, event=FIRE_DETECTED, severity=High}");
        drone.setCurrTask(task);
        drone.setBasePosition();

        // set state
        drone.setState(new DroneActive(new DroneTravel()));
        // make drone travel from event
        drone.currentState.handleEvent(drone, DroneEvent.NEW_FIRE_REQUEST);

        // verify sendStatus == true
        Field sendStatusField = Drone.class.getDeclaredField("sendStatus");
        sendStatusField.setAccessible(true);
        assertTrue((boolean) sendStatusField.get(drone));

        // verify checkSendStatus returns == true
        Method checkSendStatus = Drone.class.getDeclaredMethod("checkSendStatus");
        checkSendStatus.setAccessible(true);
        assertTrue((boolean) checkSendStatus.invoke(drone));

        // verify continueTravel == true
        Field continueTravelField = Drone.class.getDeclaredField("continueTravel");
        continueTravelField.setAccessible(true);
        assertTrue((boolean) continueTravelField.get(drone));

        // verify makeStatusRequest makes a proper status request
        Method makeStatusRequest = Drone.class.getDeclaredMethod("makeStatusRequest");
        makeStatusRequest.setAccessible(true);
        String result = (String) makeStatusRequest.invoke(drone);

        // assert the status to be sent is the  (ignoring location)
        assertTrue(result.contains("1:[ACTIVE][TRAVELING]:STATUS:"));
        assertTrue(result.contains(":FireRequest{time=10-30-15, zone=4, event=FIRE_DETECTED, severity=High}"));

        // set xPos and yPos to arrive at zone centre
        Field xPosField = Drone.class.getDeclaredField("xPos");
        xPosField.setAccessible(true);
        Field yPosField = Drone.class.getDeclaredField("yPos");
        yPosField.setAccessible(true);

        yPosField.set(drone, 299);
        xPosField.set(drone, 849);

        // make drone travel on continue event
        drone.currentState.handleEvent(drone, DroneEvent.CONTINUING);

        // verify a proper request is made for permission to drop
        Method makeRequest = Drone.class.getDeclaredMethod("makeRequest");
        makeRequest.setAccessible(true);
        String result2 = (String) makeRequest.invoke(drone);
        assertTrue(result2.equals("1:[ACTIVE][TRAVELING]:PERMISSION_TO_DROP:850:300:FireRequest{time=10-30-15, zone=4, event=FIRE_DETECTED, severity=High}"));

        // verify send status is now false, continue travel option is now false when arrived at zone
        assertFalse((boolean) sendStatusField.get(drone));
        assertFalse((boolean) continueTravelField.get(drone));

        resetDroneSubsystem(dss);
    }

    /**
     * UNIT TEST: tests drone will not travel when at the zone target coordinates, tests drone will deploy full payload and transitions states
     * and follow up with correct request
     *  <p>* will print nan as drone does not move * */
    @Test
    void test_drone_deploy() throws NoSuchFieldException, IllegalAccessException, InvocationTargetException, NoSuchMethodException
    {
        DroneSubsystem dss = new DroneSubsystem();
        Drone drone = new Drone(dss, 1);

        // Create and store the zone
        int zoneId = 4;
        Zone zone = new Zone(zoneId, 700, 0, 1000, 600); // center should be (850, 300)
        DroneSubsystem.zoneMap.put(zoneId, zone);

        // Assign fire request corresponding to that zone
        FireRequest task = new FireRequest("FireRequest{time=10-30-15, zone=4, event=FIRE_DETECTED, severity=High}");
        drone.setCurrTask(task);

        // set state
        drone.setState(new DroneActive(new DroneTravel()));

        // set xPos and yPos to arrive at zone centre
        Field xPosField = Drone.class.getDeclaredField("xPos");
        xPosField.setAccessible(true);
        Field yPosField = Drone.class.getDeclaredField("yPos");
        yPosField.setAccessible(true);

        // set drone to currently be at zone
        yPosField.set(drone, 300);
        xPosField.set(drone, 850);

        // check if it will travel
        drone.travel();

        // check drone did not move
        assertTrue( (double) xPosField.get(drone) == 850.0 );
        assertTrue( (double) yPosField.get(drone) == 300.0 );

        // verify send status is now false, continue travel option is now false when arrived at zone
        Field sendStatusField = Drone.class.getDeclaredField("sendStatus");
        sendStatusField.setAccessible(true);
        assertFalse((boolean) sendStatusField.get(drone));

        Field continueTravelField = Drone.class.getDeclaredField("continueTravel");
        continueTravelField.setAccessible(true);
        assertFalse((boolean) continueTravelField.get(drone));

        Field payloadCount = Drone.class.getDeclaredField("payloadCount");
        payloadCount.setAccessible(true);
        assertEquals( 10, (int) payloadCount.get(drone));

        // make drone drope payload
        drone.currentState.handleEvent(drone, DroneEvent.PERMISSION_TO_DROP);

        // verify a proper request is made for PAYLOAD_DROPPED
        Method makeRequest = Drone.class.getDeclaredMethod("makeRequest");
        makeRequest.setAccessible(true);
        String result2 = (String) makeRequest.invoke(drone);

        assertTrue(result2.equals("1:[ACTIVE][DEPLOYING]:PAYLOAD_DROPPED:850:300:FireRequest{time=10-30-15, zone=4, event=FIRE_DETECTED, severity=High}"));

        assertEquals( 0, (int) payloadCount.get(drone));

        resetDroneSubsystem(dss);
    }

    /**
     * UNIT TEST: to verify the drone returns from the zone it deployed and handles state transitions and correctly refills once returned
     */
    @Test
    void test_drone_returnTravel() throws Exception
    {
        DroneSubsystem dss = new DroneSubsystem();
        Drone drone = new Drone(dss, 1);

        //
        drone.setState(new DroneActive(new DroneDeploy()));

        // Create and store the zone
        int zoneId = 4;
        Zone zone = new Zone(zoneId, 700, 0, 1000, 600); // center should be (850, 300)
        DroneSubsystem.zoneMap.put(zoneId, zone);

        // Assign fire request corresponding to that zone
        FireRequest task = new FireRequest("FireRequest{time=10-30-15, zone=4, event=FIRE_DETECTED, severity=High}");
        drone.setCurrTask(task);

        //
        Field xPosField = Drone.class.getDeclaredField("xPos");
        Field yPosField = Drone.class.getDeclaredField("yPos");
        xPosField.setAccessible(true);
        yPosField.setAccessible(true);
        xPosField.set(drone, 850.0);
        yPosField.set(drone, 300.0);

        Field payloadCount = Drone.class.getDeclaredField("payloadCount");
        payloadCount.setAccessible(true);
        payloadCount.set(drone,0); // empty payload

        // verify a proper request is made for PAYLOAD_DROPPED
        Method makeRequest = Drone.class.getDeclaredMethod("makeRequest");
        makeRequest.setAccessible(true);
        String result1 = (String) makeRequest.invoke(drone);

        assertTrue(result1.equals("1:[ACTIVE][DEPLOYING]:PAYLOAD_DROPPED:850:300:FireRequest{time=10-30-15, zone=4, event=FIRE_DETECTED, severity=High}"));

        drone.currentState.handleEvent(drone, DroneEvent.PAYLOAD_DROPPED); // triggers returnTravel()

        // verify sendStatus == true
        Field sendStatusField = Drone.class.getDeclaredField("sendStatus");
        sendStatusField.setAccessible(true);
        assertTrue((boolean) sendStatusField.get(drone));

        // verify checkSendStatus returns == true
        Method checkSendStatus = Drone.class.getDeclaredMethod("checkSendStatus");
        checkSendStatus.setAccessible(true);
        assertTrue((boolean) checkSendStatus.invoke(drone));

        // verify makeStatusRequest makes a proper status request
        Method makeStatusRequest = Drone.class.getDeclaredMethod("makeStatusRequest");
        makeStatusRequest.setAccessible(true);
        String result2 = (String) makeStatusRequest.invoke(drone);

        // assert the status to be sent is the  (ignoring location)
        assertTrue(result2.contains("1:[ACTIVE][RETURNING]:RETURN_STATUS:"));
        assertFalse(result2.contains("1:[ACTIVE][RETURNING]:STATUS:"));
        assertTrue(result2.contains(":FireRequest{time=10-30-15, zone=4, event=FIRE_DETECTED, severity=High}"));

        xPosField.set(drone, 0.0);
        yPosField.set(drone, 0.0);

        String result3 = (String) makeRequest.invoke(drone);

        assertEquals(result3, "1:[ACTIVE][RETURNING]:RETURNED_TO_BASE:0:0:FireRequest{time=10-30-15, zone=4, event=FIRE_DETECTED, severity=High}");

        // After return travel, verify xPos and yPos are back at base (0, 0)
        double xFinal = (double) xPosField.get(drone);
        double yFinal = (double) yPosField.get(drone);
        assertEquals(0.0, xFinal, 0.01);
        assertEquals(0.0, yFinal, 0.01);

        // invoke next state, drone refill
        drone.handleEvent(DroneEvent.RETURNED_TO_BASE);

        // check the payload count is filled after refill
        assertEquals( 10, (int) payloadCount.get(drone));

        resetDroneSubsystem(dss);
    }

    /**
     * UNIT TEST: to verify the drone switches to idle after refilling
     */
    @Test
    void test_drone_refill_switch_to_Idle() throws Exception
    {
        DroneSubsystem dss = new DroneSubsystem();
        Drone drone = new Drone(dss, 1);

        //
        drone.setState(new DroneRefill());

        // Create and store the zone
        int zoneId = 4;
        Zone zone = new Zone(zoneId, 700, 0, 1000, 600); // center should be (850, 300)
        DroneSubsystem.zoneMap.put(zoneId, zone);

        // Assign fire request corresponding to that zone
        FireRequest task = new FireRequest("FireRequest{time=10-30-15, zone=4, event=FIRE_DETECTED, severity=High}");
        drone.setCurrTask(task);

        //
        Field xPosField = Drone.class.getDeclaredField("xPos");
        Field yPosField = Drone.class.getDeclaredField("yPos");
        xPosField.setAccessible(true);
        yPosField.setAccessible(true);
        xPosField.set(drone, 0.0);
        yPosField.set(drone, 0.0);

        Field payloadCount = Drone.class.getDeclaredField("payloadCount");
        payloadCount.setAccessible(true);
        payloadCount.set(drone,10); // empty payload

        // verify a proper request is made for PAYLOAD_DROPPED
        Method makeRequest = Drone.class.getDeclaredMethod("makeRequest");
        makeRequest.setAccessible(true);
        String result1 = (String) makeRequest.invoke(drone);

        System.out.println(result1);
        assertTrue(result1.equals("1:[REFILLING]:REFILL_COMPLETE:0:0:FireRequest{time=10-30-15, zone=4, event=FIRE_DETECTED, severity=High}"));

        drone.currentState.handleEvent(drone, DroneEvent.REFILL_COMPLETE);

        // check if drone is at default state
        assertEquals( "[IDLE]", drone.getCurrentState().display() );
        assertTrue( drone.getCurrTask().isDefault() );
        assertTrue( (double)xPosField.get(drone)== 0.0 && (double)yPosField.get(drone)== 0.0 );
        assertTrue( drone.currentState.getRequest(drone).equals("NEW_FIRE_REQUEST") );

        // check request to send to scheduler is the following
        String result2 = (String) makeRequest.invoke(drone);

        assertTrue(result2.equals("1:[IDLE]:NEW_FIRE_REQUEST:0:0:FireRequest{time=0, zone=-1, event=0, severity=0}"));

        resetDroneSubsystem(dss);
    }




}
