import org.junit.jupiter.api.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@SuppressWarnings("ALL")
public class DroneLogAnalyzerTest {
    private String logLine;
    private DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private List<Object> logs = new ArrayList<>();

    /**
     * Clears all info after each test。
     */
    @AfterEach
    void cleanUp(){
        DroneLogAnalyzer.getSubsystemLogs().clear();
        DroneLogAnalyzer.getProcessFaultLogs().clear();
        DroneLogAnalyzer.getDrones().clear();
        DroneLogAnalyzer.getDroneLogs().clear();
        DroneLogAnalyzer.getSchedulerListenerLogs().clear();
    }
    /**
     * Tests the `parse` method of the `LogEntry` class.
     * Verifies correct parsing of a log line into a `LogEntry` object,
     * checking if the timestamp, component, thread type, and event
     * are parsed correctly.
     */
    @Test
    public void logEntryParse() throws ClassNotFoundException {
        Class<?> logEntryClass = Class.forName("DroneLogAnalyzer$LogEntry");
        try {
            logLine = "[12:34:56.789] [INFO] [COMPONENT] [THREADTYPE] Drone arrived at checkpoint";
            Method parseMethod = logEntryClass.getDeclaredMethod("parse", String.class);
            Object logEntry = parseMethod.invoke(null, logLine);
            Assertions.assertNotNull(logEntry, "Parsed LogEntry should not be null");
            Field timestampField = logEntryClass.getDeclaredField("timestamp");
            Field componentField = logEntryClass.getDeclaredField("component");
            Field threadTypeField = logEntryClass.getDeclaredField("threadType");
            Field eventField = logEntryClass.getDeclaredField("event");

            timestampField.setAccessible(true);
            componentField.setAccessible(true);
            threadTypeField.setAccessible(true);
            eventField.setAccessible(true);

            Assertions.assertEquals(LocalTime.parse("12:34:56.789"), timestampField.get(logEntry));
            Assertions.assertEquals("COMPONENT", componentField.get(logEntry));
            Assertions.assertEquals("THREADTYPE", threadTypeField.get(logEntry));
            Assertions.assertEquals("Drone arrived at checkpoint", eventField.get(logEntry));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    /**
     * Tests the `readLogs` method of the `DroneLogAnalyzer` class.
     * This test creates a temporary log file, writes sample log entries into it,
     * and then calls the `readLogs` method to read and parse the log entries.
     * It verifies the parsing functionality by checking that the component, thread type,
     * and event are correctly extracted and assigned to the respective lists (`schedulerListenerLogs`,
     * `subsystemLogs`, `processFaultLogs`, and `droneLogs`).
     * The test checks the correctness of the parsed data by using assertions.
     */
    @Test
    public void TestReadLogs() throws IOException {
        Object logEntry;
        String component, threadType, event;
        File tempLog = File.createTempFile("test_log", ".txt");
        tempLog.deleteOnExit();
        try (FileWriter writer = new FileWriter(tempLog)) {
            writer.write("[12:00:00.000] [INFO] [DroneSubsystem] [ListeningToScheduler] Drone ready\n");
            writer.write("[12:01:00.000] [INFO] [DroneSubsystem] [ProcessFaults] Drone fault\n");
            writer.write("[12:02:00.000] [INFO] [DroneSubsystem] [run] General event\n");
            writer.write("[12:03:00.000] [INFO] [Drone] [1] Drone initialized\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        DroneLogAnalyzer.readLogs(tempLog.getAbsolutePath());
        try {
            Class<?> logEntryClass = Class.forName("DroneLogAnalyzer$LogEntry");

            Field componentField = logEntryClass.getDeclaredField("component");
            Field threadTypeField = logEntryClass.getDeclaredField("threadType");
            Field eventField = logEntryClass.getDeclaredField("event");
            componentField.setAccessible(true);
            threadTypeField.setAccessible(true);
            eventField.setAccessible(true);

            //ListeningToScheduler
            logEntry = DroneLogAnalyzer.getSchedulerListenerLogs().get(0);
            component = (String) componentField.get(logEntry);
            threadType = (String) threadTypeField.get(logEntry);
            event = (String) eventField.get(logEntry);

            Assertions.assertEquals("DroneSubsystem", component);
            Assertions.assertEquals("ListeningToScheduler", threadType);
            Assertions.assertEquals("Drone ready", event);
            Assertions.assertEquals(1, DroneLogAnalyzer.getSchedulerListenerLogs().size());

            //Subsystem
            logEntry = DroneLogAnalyzer.getSubsystemLogs().get(0);
            component = (String) componentField.get(logEntry);
            threadType = (String) threadTypeField.get(logEntry);
            event = (String) eventField.get(logEntry);
            Assertions.assertEquals("DroneSubsystem", component);
            Assertions.assertEquals("run", threadType);
            Assertions.assertEquals("General event", event);
            Assertions.assertEquals(1, DroneLogAnalyzer.getSubsystemLogs().size());

            //ProcessFault
            logEntry = DroneLogAnalyzer.getProcessFaultLogs().get(0);
            component = (String) componentField.get(logEntry);
            threadType = (String) threadTypeField.get(logEntry);
            event = (String) eventField.get(logEntry);
            Assertions.assertEquals("DroneSubsystem", component);
            Assertions.assertEquals("ProcessFaults", threadType);
            Assertions.assertEquals("Drone fault", event);
            Assertions.assertEquals(1, DroneLogAnalyzer.getProcessFaultLogs().size());

            //DroneLogs
            logEntry = DroneLogAnalyzer.getDroneLogs().get(0);
            component = (String) componentField.get(logEntry);
            threadType = (String) threadTypeField.get(logEntry);
            event = (String) eventField.get(logEntry);
            Assertions.assertEquals("Drone", component);
            Assertions.assertEquals("1", threadType);
            Assertions.assertEquals("Drone initialized", event);
            Assertions.assertEquals(1, DroneLogAnalyzer.getDroneLogs().size());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    /**
     * Tests the `averageDeploy` method of the `DroneLogAnalyzer` class.
     * This test creates several `LogEntry` objects representing drone deployment events
     * and verifies that the `averageDeploy` method correctly calculates the average deployment time.
     * It checks three cases:
     * 1. Correct event order, where the deployment time is calculated correctly.
     * 2. Incorrect event order, where the method should return 0 as deployment time.
     * 3. No logs, where the method should return 0 for average deployment time.
     */
    @Test
    public void TestAverageDeploy(){
        try {
            Object log1, log2;
            double deployTime;
            Class<?> logEntryClass = Class.forName("DroneLogAnalyzer$LogEntry");
            Constructor<?> constructor = logEntryClass.getDeclaredConstructor(LocalTime.class, String.class, String.class, String.class);
            constructor.setAccessible(true);
            log1 = constructor.newInstance(
                    LocalTime.parse("12:34:56.000", formatter),
                    "Drone",
                    "0",
                    "Deploying"
            );
            log2 = constructor.newInstance(
                    LocalTime.parse("12:34:57.000", formatter),
                    "Drone",
                    "0",
                    "Deployment of payload complete"
            );
            logs.add(log1);
            logs.add(log2);
            deployTime = (double) DroneLogAnalyzer.class
                    .getMethod("averageDeploy", List.class)
                    .invoke(null, logs);
            Assertions.assertEquals(1, deployTime);
            logs.clear();
            //wrong order event
            log1 = constructor.newInstance(
                    LocalTime.parse("12:34:58.000", formatter),
                    "Drone",
                    "0",
                    "Deploying"
            );
            log2 = constructor.newInstance(
                    LocalTime.parse("12:34:57.000", formatter),
                    "Drone",
                    "0",
                    "Deployment of payload complete"
            );
            logs.add(log1);
            logs.add(log2);
            deployTime = (double) DroneLogAnalyzer.class
                    .getMethod("averageDeploy", List.class)
                    .invoke(null, logs);
            Assertions.assertEquals(0, deployTime);
            logs.clear();

            //no log
            deployTime = (double) DroneLogAnalyzer.class
                    .getMethod("averageDeploy", List.class)
                    .invoke(null, logs);
            Assertions.assertEquals(0, deployTime);

        }catch (Exception e) {
            throw new RuntimeException(e);
        }

    }
    /**
     * Tests the `droneRunTime` method of the `DroneLogAnalyzer` class.
     * This test verifies the calculation of the runtime for a drone based on its log entries.
     * It checks three cases:
     * 1. Correct log entries for assigned and returned events, where runtime is calculated correctly.
     * 2. Incorrect log entries where the timestamps are the same, resulting in a runtime of 0.
     * 3. A case where the return timestamp is earlier than the assignment timestamp, resulting in 0 runtime.
     */
    @Test
    public void TestDroneRunTime() {
        try {
            Object log1, log2;
            double runtime;
            Class<?> logEntryClass = Class.forName("DroneLogAnalyzer$LogEntry");
            Constructor<?> constructor = logEntryClass.getDeclaredConstructor(LocalTime.class, String.class, String.class, String.class);
            constructor.setAccessible(true);
            log1 = constructor.newInstance(
                    LocalTime.parse("12:34:56.000", formatter),
                    "Drone",
                    "0",
                    "Assigned"
            );
            log2 = constructor.newInstance(
                    LocalTime.parse("12:34:57.000", formatter),
                    "Drone",
                    "0",
                    "Arrived back at base"
            );
            logs.add(log1);
            logs.add(log2);

            runtime = (double) DroneLogAnalyzer.class
                    .getMethod("droneRunTime", List.class)
                    .invoke(null, logs);
            Assertions.assertEquals(1.0, runtime);
            logs.clear();

            log1 = constructor.newInstance(
                    LocalTime.parse("12:34:56.000", formatter),
                    "Drone",
                    "0",
                    "Assigned"
            );
            log2 = constructor.newInstance(
                    LocalTime.parse("12:34:56.000", formatter),
                    "Drone",
                    "0",
                    "Arrived back at base"
            );
            runtime = (double) DroneLogAnalyzer.class
                    .getMethod("droneRunTime", List.class)
                    .invoke(null, logs);
            Assertions.assertEquals(0.0, runtime);
            logs.clear();

            //testing < 0 runtime
            log1 = constructor.newInstance(
                    LocalTime.parse("12:34:56.000", formatter),
                    "Drone",
                    "0",
                    "Assigned"
            );

            // Log entry for "Arrived back at base"
            log2 = constructor.newInstance(
                    LocalTime.parse("12:34:55.000", formatter),
                    "Drone",
                    "0",
                    "Arrived back at base"
            );
            runtime = (double) DroneLogAnalyzer.class
                    .getMethod("droneRunTime", List.class)
                    .invoke(null, logs);
            Assertions.assertEquals(0.0, runtime);
            logs.clear();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    /**
     * Tests the `droneUtilization` method of the `DroneLogAnalyzer` class.
     * This test verifies the calculation of drone utilization based on its log entries and given lifetime.
     * It checks multiple cases:
     * 1. **Normal utilization**: Verifies the correct utilization when logs are provided and the lifetime is non-zero.
     * 2. **Lifetime = 0**: Verifies that the utilization is 0 when the lifetime is 0.
     * 3. **Lifetime < 0**: Verifies that the utilization is 0 when the lifetime is negative.
     * 4. **Empty logs**: Verifies that the utilization is 0 when no logs are available.
     * 5. **Negative timestamp order**: Verifies that the utilization is 0 if the timestamps are in the wrong order (return time is earlier than the assignment time).
     */
    @Test
    public void TestDroneUtilization(){
        try {
            double utilization;
            Class<?> logEntryClass = Class.forName("DroneLogAnalyzer$LogEntry");
            Constructor<?> constructor = logEntryClass.getDeclaredConstructor(LocalTime.class, String.class, String.class, String.class);
            constructor.setAccessible(true);

            logs.add(constructor.newInstance(
                    LocalTime.parse("12:34:56.000", formatter),
                    "Drone",
                    "0",
                    "Assigned"
            ));
            logs.add(constructor.newInstance(
                    LocalTime.parse("12:34:57.000", formatter),
                    "Drone",
                    "0",
                    "Arrived back at base"
            ));

            //test normal utilization
            utilization = (double) DroneLogAnalyzer.class
                    .getMethod("droneUtilization", List.class, double.class)
                    .invoke(null, logs, 5.0);
            Assertions.assertEquals(0.2, utilization, 0.001);

            //test lifetime =0
            utilization = (double) DroneLogAnalyzer.class
                    .getMethod("droneUtilization", List.class, double.class)
                    .invoke(null, logs, 0.0);
            Assertions.assertEquals(0.0, utilization, 0.001);

            //test lifetime < 0
            utilization = (double) DroneLogAnalyzer.class
                    .getMethod("droneUtilization", List.class, double.class)
                    .invoke(null, logs, -1);
            Assertions.assertEquals(0.0, utilization, 0.001);
            logs.clear();

            //test empty logs
            utilization = (double) DroneLogAnalyzer.class
                    .getMethod("droneUtilization", List.class, double.class)
                    .invoke(null, logs, 2);
            Assertions.assertEquals(0.0, utilization, 0.001);

            logs.add(constructor.newInstance(
                    LocalTime.parse("12:34:56.000", formatter),
                    "Drone",
                    "0",
                    "Assigned"
            ));
            logs.add(constructor.newInstance(
                    LocalTime.parse("12:34:57.000", formatter),
                    "Drone",
                    "0",
                    "Arrived back at base"
            ));

            //test negative timestamp
            logs.add(constructor.newInstance(
                    LocalTime.parse("12:34:58.000", formatter),
                    "Drone",
                    "0",
                    "Assigned"
            ));
            logs.add(constructor.newInstance(
                    LocalTime.parse("12:34:57.000", formatter),
                    "Drone",
                    "0",
                    "Arrived back at base"
            ));
            utilization = (double) DroneLogAnalyzer.class
                    .getMethod("droneUtilization", List.class, double.class)
                    .invoke(null, logs, 2);
            Assertions.assertEquals(0.0, utilization, 0.001);
            logs.clear();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    /**
     * Tests the `subsystemLatency` method of the `DroneLogAnalyzer` class.
     * This test verifies the calculation of average latency between "Waiting" and "Received" events for the "DroneSubsystem".
     * It checks multiple scenarios:
     * 1. **Normal case**: Verifies correct average latency calculation when valid logs with "Waiting" and "Received" events are available.
     * 2. **Empty logs**: Verifies that the latency is 0 when no logs are available.
     * 3. **No completed requests**: Verifies that the latency is 0 when no "Received" event exists for a "Waiting" event.
     * 4. **Incorrect event order**: Verifies that the latency is 0 when the "Received" event occurs before the "Waiting" event.
     */
    @Test
    public void TestSubsystemLatency(){
        double avgLatency;
        try {
            // Prepare sample logs
            Object log;
            Class<?> logEntryClass = Class.forName("DroneLogAnalyzer$LogEntry");

            Constructor<?> constructor = logEntryClass.getDeclaredConstructor(LocalTime.class, String.class, String.class, String.class);
            constructor.setAccessible(true);
            Field subsystemLogsField = DroneLogAnalyzer.class.getDeclaredField("subsystemLogs");
            subsystemLogsField.setAccessible(true);
            logs = (List<Object>) subsystemLogsField.get(null);

            //normal case test
            log = constructor.newInstance(
                    LocalTime.parse("12:34:56.000", formatter),
                    "DroneSubsystem",
                    "run",
                    "Waiting"
            );
            logs.add(log);
            log = constructor.newInstance(
                    LocalTime.parse("12:34:58.500", formatter),
                    "DroneSubsystem",
                    "run",
                    "Received"
            );
            logs.add(log);
            log = constructor.newInstance(
                    LocalTime.parse("12:35:00.000", formatter),
                    "DroneSubsystem",
                    "0",
                    "Waiting"
            );
            logs.add(log);
            log = constructor.newInstance(
                    LocalTime.parse("12:35:04.000", formatter),
                    "DroneSubsystem",
                    "0",
                    "Received"
            );
            logs.add(log);
            avgLatency = DroneLogAnalyzer.subsystemLatency();
            Assertions.assertEquals(3.25, avgLatency, 0.001);

            //empty log test
            logs.clear();
            avgLatency = DroneLogAnalyzer.subsystemLatency();
            Assertions.assertEquals(0.0, avgLatency, 0.001);

            //no request completed
            log = constructor.newInstance(
                    LocalTime.parse("12:34:56.000", formatter),
                    "DroneSubsystem",
                    "run",
                    "Waiting"
            );
            avgLatency = DroneLogAnalyzer.subsystemLatency();
            Assertions.assertEquals(0.0, avgLatency, 0.001);
            logs.clear();

            //both wait and received but wrong order
            log = constructor.newInstance(
                    LocalTime.parse("12:33:00.500", formatter),
                    "DroneSubsystem",
                    "run",
                    "Received"
            );
            avgLatency = DroneLogAnalyzer.subsystemLatency();
            Assertions.assertEquals(0.0, avgLatency, 0.001);
            logs.clear();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    /**
     * Tests the `throughput` method of the `DroneLogAnalyzer` class.
     * This test verifies the calculation of throughput between two events for the "Drone" component.
     * It checks multiple scenarios:
     * 1. **Normal case**: Verifies correct throughput calculation when logs with valid timestamps are available.
     * 2. **Lifetime = 0**: Verifies that throughput is 0 when the provided lifetime is 0.
     * 3. **Lifetime < 0**: Verifies that throughput is 0 when the provided lifetime is negative.
     * 4. **Empty log**: Verifies that throughput is 0 when no logs are available.
     * 5. **Empty event message**: Verifies that throughput is 0 when one of the event messages is empty.
     */
    @Test
    public void TestThroughput(){
        try {
            double throughput;
            Class<?> logEntryClass = Class.forName("DroneLogAnalyzer$LogEntry");
            Constructor<?> constructor = logEntryClass.getDeclaredConstructor(LocalTime.class, String.class, String.class, String.class);
            constructor.setAccessible(true);
            //testing normal timestamp
            logs.add(constructor.newInstance(
                    LocalTime.parse("12:34:56.000", formatter),
                    "Drone",
                    "0",
                    "Assigned"
            ));
            logs.add(constructor.newInstance(
                    LocalTime.parse("12:34:57.000", formatter),
                    "Drone",
                    "0",
                    "Arrived back at base"
            ));
            //normal lifetime
            throughput = (double) DroneLogAnalyzer.class
                    .getMethod("throughput", List.class, double.class, String.class, String.class)
                    .invoke(null, logs, 2.0, "Assigned", "Arrived back at base");
            Assertions.assertEquals(0.5, throughput, 0.001);
            //lifetime = 0
            throughput = (double) DroneLogAnalyzer.class
                    .getMethod("throughput", List.class, double.class, String.class, String.class)
                    .invoke(null, logs, 0, "Assigned", "Arrived back at base");
            Assertions.assertEquals(0, throughput, 0.001);
            //lifetime <0
            throughput = (double) DroneLogAnalyzer.class
                    .getMethod("throughput", List.class, double.class, String.class, String.class)
                    .invoke(null, logs, -2.0, "Assigned", "Arrived back at base");
            Assertions.assertEquals(0, throughput, 0.001);
            logs.clear();
            //empty log
            throughput = (double) DroneLogAnalyzer.class
                    .getMethod("throughput", List.class, double.class, String.class, String.class)
                    .invoke(null, logs, 2.0, "Assigned", "Arrived back at base");
            Assertions.assertEquals(0, throughput, 0.001);
            //empty one of the message
            throughput = (double) DroneLogAnalyzer.class
                    .getMethod("throughput", List.class, double.class, String.class, String.class)
                    .invoke(null, logs, 2.0, "", "Arrived back at base");
            Assertions.assertEquals(0, throughput, 0.001);

            logs.clear();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    /**
     * Tests the `utilization` method of the `DroneLogAnalyzer` class.
     * This test verifies the calculation of utilization between two events for the "Drone" component.
     * It checks multiple scenarios:
     * 1. **Normal case**: Verifies correct utilization calculation when logs with valid timestamps are available.
     * 2. **Lifetime = 0**: Verifies that utilization is 0 when the provided lifetime is 0.
     * 3. **Lifetime < 0**: Verifies that utilization is 0 when the provided lifetime is negative.
     * 4. **Empty log**: Verifies that utilization is 0 when no logs are available.
     * 5. **Empty event message**: Verifies that utilization is 0 when one of the event messages is empty.
     */
    @Test
    public void TestUtilization() {
        try {
            double utilization;
            Class<?> logEntryClass = Class.forName("DroneLogAnalyzer$LogEntry");
            Constructor<?> constructor = logEntryClass.getDeclaredConstructor(LocalTime.class, String.class, String.class, String.class);
            constructor.setAccessible(true);

            //testing normal timestamp
            logs.add(constructor.newInstance(
                    LocalTime.parse("12:34:56.000", formatter),
                    "Drone",
                    "0",
                    "Assigned"
            ));
            logs.add(constructor.newInstance(
                    LocalTime.parse("12:34:57.000", formatter),
                    "Drone",
                    "0",
                    "Arrived back at base"
            ));
            //normal lifetime
            utilization = (double) DroneLogAnalyzer.class
                    .getMethod("utilization", List.class, double.class, String.class, String.class)
                    .invoke(null, logs, 2.0, "Assigned", "Arrived back at base");
            Assertions.assertEquals(0.5, utilization, 0.001);
            //lifetime = 0
            utilization = (double) DroneLogAnalyzer.class
                    .getMethod("utilization", List.class, double.class, String.class, String.class)
                    .invoke(null, logs, 0, "Assigned", "Arrived back at base");
            Assertions.assertEquals(0, utilization, 0.001);
            //lifetime <0
            utilization = (double) DroneLogAnalyzer.class
                    .getMethod("utilization", List.class, double.class, String.class, String.class)
                    .invoke(null, logs, -2.0, "Assigned", "Arrived back at base");
            Assertions.assertEquals(0, utilization, 0.001);
            logs.clear();
            //empty log
            utilization = (double) DroneLogAnalyzer.class
                    .getMethod("utilization", List.class, double.class, String.class, String.class)
                    .invoke(null, logs, 2.0, "Assigned", "Arrived back at base");
            Assertions.assertEquals(0, utilization, 0.001);
            //empty one of the message
            utilization = (double) DroneLogAnalyzer.class
                    .getMethod("utilization", List.class, double.class, String.class, String.class)
                    .invoke(null, logs, 2.0, "", "Arrived back at base");
            Assertions.assertEquals(0, utilization, 0.001);

            logs.clear();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    /**
     * Tests the `calculateTotalTime` method of the `DroneLogAnalyzer` class.
     * This test verifies the correct calculation of total time between the "Assigned" and "Arrived back at base" events for the "Drone" component.
     * It checks multiple scenarios:
     * 1. **Normal case**: Verifies the correct calculation of total time when valid timestamps are provided.
     * 2. **Empty log**: Verifies that the total time is 0 when no logs are available.
     * 3. **Incorrect timestamp order**: Verifies that total time is 0 when the "Arrived back at base" timestamp is earlier than the "Assigned" timestamp.
     */
    @Test
    public void TestCalculateTotalTime(){
        try {
            double lifetime;
            Class<?> logEntryClass = Class.forName("DroneLogAnalyzer$LogEntry");
            Constructor<?> constructor = logEntryClass.getDeclaredConstructor(LocalTime.class, String.class, String.class, String.class);
            constructor.setAccessible(true);

            //testing normal timestamp
            logs.add(constructor.newInstance(
                    LocalTime.parse("12:34:56.000", formatter),
                    "Drone",
                    "0",
                    "Assigned"
            ));
            logs.add(constructor.newInstance(
                    LocalTime.parse("12:34:57.000", formatter),
                    "Drone",
                    "0",
                    "Arrived back at base"
            ));
            //normal lifetime
            lifetime = (double) DroneLogAnalyzer.class
                    .getMethod("calculateTotalTime", List.class)
                    .invoke(null, logs);
            Assertions.assertEquals(1.0, lifetime, 0.001);

            //empty log
            logs.clear();

            lifetime = (double) DroneLogAnalyzer.class
                    .getMethod("calculateTotalTime", List.class)
                    .invoke(null, logs);
            Assertions.assertEquals(0, lifetime, 0.001);

            //wrong timestamp
            logs.add(constructor.newInstance(
                    LocalTime.parse("12:34:56.000", formatter),
                    "Drone",
                    "0",
                    "Assigned"
            ));
            logs.add(constructor.newInstance(
                    LocalTime.parse("12:34:55.000", formatter),
                    "Drone",
                    "0",
                    "Arrived back at base"
            ));
            lifetime = (double) DroneLogAnalyzer.class
                    .getMethod("calculateTotalTime", List.class)
                    .invoke(null, logs);
            Assertions.assertEquals(0.0, lifetime, 0.001);
            logs.clear();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    /**
     * Tests the `sortDrones` method of the `DroneLogAnalyzer` class.
     * This test verifies that the `sortDrones` method correctly sorts the drone logs and organizes them into a `HashMap` where the key is the drone identifier.
     * The test checks the following scenarios:
     * 1. **Before sorting**: Verifies the correct initial state of the drone logs list and checks that the logs are in the expected order.
     * 2. **After sorting**: Verifies that the logs are correctly grouped by the drone identifier and that the `HashMap` structure is updated as expected.
     */
    @Test
    public void TestSortDrones(){
        try {
            Class<?> logEntryClass = Class.forName("DroneLogAnalyzer$LogEntry");
            Constructor<?> constructor = logEntryClass.getDeclaredConstructor(LocalTime.class, String.class, String.class, String.class);
            constructor.setAccessible(true);
            Field droneLogsField = DroneLogAnalyzer.class.getDeclaredField("droneLogs");
            droneLogsField.setAccessible(true);
            logs = (List<Object>) droneLogsField.get(null);
            Object log1 = constructor.newInstance(
                    LocalTime.parse("12:34:56.000", formatter),
                    "Drone",
                    "0",
                    "Assigned"
            );
            Object log2 = constructor.newInstance(
                    LocalTime.parse("12:34:55.000", formatter),
                    "Drone",
                    "2",
                    "Arrived back at base"
            );
            Object log3 = constructor.newInstance(
                    LocalTime.parse("12:34:57.000", formatter),
                    "Drone",
                    "0",
                    "Arrived back at base"
            );
            Object log4 = constructor.newInstance(
                    LocalTime.parse("12:34:55.000", formatter),
                    "Drone",
                    "1",
                    "Arrived back at base"
            );
            logs.add(log1);
            logs.add(log2);
            logs.add(log3);
            logs.add(log4);

            Assertions.assertEquals(4,DroneLogAnalyzer.getDroneLogs().size());
            Assertions.assertEquals(log1,DroneLogAnalyzer.getDroneLogs().get(0));
            Assertions.assertEquals(log2,DroneLogAnalyzer.getDroneLogs().get(1));
            Assertions.assertEquals(log3,DroneLogAnalyzer.getDroneLogs().get(2));
            Assertions.assertEquals(log4,DroneLogAnalyzer.getDroneLogs().get(3));

            Field droneHashMapField = DroneLogAnalyzer.class.getDeclaredField("drones");
            droneHashMapField.setAccessible(true);

            @SuppressWarnings("unchecked")
            HashMap<String, List<Object>> drones = (HashMap<String, List<Object>>) droneHashMapField.get(null);
            DroneLogAnalyzer.sortDrones();

            Assertions.assertEquals(3,drones.size());
            // log1 and log3 - drone 0
            Assertions.assertTrue(drones.containsKey("0"));
            Assertions.assertEquals(2,drones.get("0").size());
            Assertions.assertTrue(drones.get("0").contains(log1));
            Assertions.assertTrue(drones.get("0").contains(log3));
            // log4 - drone 1
            Assertions.assertTrue(drones.containsKey("1"));
            Assertions.assertEquals(1,drones.get("1").size());
            Assertions.assertTrue(drones.get("1").contains(log4));
            // log2 - drone 2
            Assertions.assertTrue(drones.containsKey("2"));
            Assertions.assertEquals(1,drones.get("2").size());
            Assertions.assertTrue(drones.get("2").contains(log2));
            Assertions.assertFalse(drones.containsKey("3"));

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
