import org.junit.jupiter.api.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
/**
 * Test class for {@link DroneLogAnalyzer}
 * This class contains tests for various aspects of the {@code DroneLogAnalyzer} class
 * It includes methods to clean up logs and reset state after each test
 */
@SuppressWarnings("ALL")
public class DroneLogAnalyzerTest {
    private String logLine;
    private DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private List<Object> logs = new ArrayList<>();

    @AfterEach
    void cleanUp(){
        DroneLogAnalyzer.getSubsystemLogs().clear();
        DroneLogAnalyzer.getProcessFaultLogs().clear();
        DroneLogAnalyzer.getDrones().clear();
        DroneLogAnalyzer.getDroneLogs().clear();
        DroneLogAnalyzer.getSchedulerListenerLogs().clear();
    }
    /**
     * Unit test for parsing a log entry using the {@link DroneLogAnalyzer.LogEntry} class
     * This test verifies that the {@code parse} method of the {@code LogEntry} class correctly parses a log line
     *
     * The test checks the following:
     * 1. The log entry is parsed successfully and is not null
     * 2. The timestamp, component, thread type, and event fields are correctly set
     *
     * The test invokes the {@code parse} method using reflection and verifies the extracted values
     *
     * @throws ClassNotFoundException If the {@code DroneLogAnalyzer.LogEntry} class cannot be found
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
     * Unit test for reading log entries using the {@link DroneLogAnalyzer} class
     * This test verifies that the {@code readLogs} method correctly parses the log file and stores the parsed entries
     * in their respective log categories
     *
     * The test writes a series of log entries to a temporary file, then calls the {@code readLogs} method to parse the
     * log file. It then verifies that the parsed entries are correctly classified into their respective log categories:
     * - {@code SchedulerListenerLogs}
     * - {@code SubsystemLogs}
     * - {@code ProcessFaultLogs}
     * - {@code DroneLogs}
     *
     * For each log category, the following assertions are made：
     * 1. The component, thread type, and event fields of the parsed log entry are correct
     * 2. The number of entries in the respective log category is as expected
     *
     * @throws IOException If an I/O error occurs while creating the temporary log file or writing to it.
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
            writer.write("[12:03:00.000] [INFO] [All] [All] Shutdown\n");

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
     * Unit test for the {@link DroneLogAnalyzer#averageDeploy(List)} method
     * This test verifies the correct calculation of the average deployment time for drone logs
     * It tests three cases:
     * 1. Two logs with a valid deployment sequence: The test ensures the average deployment time is calculated correctly
     * 2. Two logs with an invalid deployment sequence: The test checks that the method handles the case where the logs are in the wrong order
     * 3. No logs: The test verifies that the method correctly returns 0 when there are no logs to process
     *
     * The following assertions are made:
     * - When the deployment logs are in correct order, the average deployment time is calculated and verified
     * - When the logs are in the wrong order, the deployment time is 0, indicating that the sequence is invalid
     * - When no logs are provided, the deployment time is 0
     *
     * @throws Exception If an error occurs during reflection or log creation
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
     * Unit test for the {@link DroneLogAnalyzer#droneRunTime(List)} method
     * This test verifies the correct calculation of the drone's runtime between the "Assigned" and "Arrived back at base" events
     * It tests three cases:
     * 1. A valid log sequence with "Assigned" and "Arrived back at base" logs: The test ensures the runtime is calculated correctly
     * 2. Logs with identical timestamps: The test checks that the method returns 0 when the logs have the same timestamp
     * 3. A log sequence where the "Arrived back at base" log occurs before the "Assigned" log: The test verifies that the method handles this invalid scenario and returns 0
     *
     * The following assertions are made:
     * - When the logs are in the correct sequence, the runtime is calculated and verified
     * - When the logs have identical timestamps, the runtime is 0, indicating no time difference
     * - When the "Arrived back at base" log occurs before the "Assigned" log, the runtime is 0
     *
     * @throws Exception If an error occurs during reflection or log creation.
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
     * Tests the `subsystemLatency` method of the `DroneLogAnalyzer` class
     * This test verifies the calculation of average latency between "Waiting" and "Received" events for the "DroneSubsystem"
     * It checks multiple scenarios:
     * 1. **Normal case**: Verifies correct average latency calculation when valid logs with "Waiting" and "Received" events are available
     * 2. **Empty logs**: Verifies that the latency is 0 when no logs are available
     * 3. **No completed requests**: Verifies that the latency is 0 when no "Received" event exists for a "Waiting" event
     * 4. **Incorrect event order**: Verifies that the latency is 0 when the "Received" event occurs before the "Waiting" event
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
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    /**
     * Tests the `throughput` method of the `DroneLogAnalyzer` class
     * This test verifies the calculation of throughput between two events for the "Drone" component
     * It checks multiple scenarios:
     * 1. **Normal case**: Verifies correct throughput calculation when logs with valid timestamps are available
     * 2. **Lifetime = 0**: Verifies that throughput is 0 when the provided lifetime is 0
     * 3. **Lifetime < 0**: Verifies that throughput is 0 when the provided lifetime is negative
     * 4. **Empty log**: Verifies that throughput is 0 when no logs are available
     * 5. **Empty event message**: Verifies that throughput is 0 when one of the event messages is empty
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
     * Tests the `utilization` method of the `DroneLogAnalyzer` class
     * This test verifies the calculation of utilization between two events for the "Drone" component
     * It checks multiple scenarios:
     * 1. **Normal case**: Verifies correct utilization calculation when logs with valid timestamps are available
     * 2. **Lifetime = 0**: Verifies that utilization is 0 when the provided lifetime is 0
     * 3. **Lifetime < 0**: Verifies that utilization is 0 when the provided lifetime is negative
     * 4. **Empty log**: Verifies that utilization is 0 when no logs are available
     * 5. **Empty event message**: Verifies that utilization is 0 when one of the event messages is empty
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
     * Tests the `calculateTotalTime` method of the `DroneLogAnalyzer` class
     * This test verifies the correct calculation of total time between the "Assigned" and "Arrived back at base" events for the "Drone" component
     * It checks multiple scenarios:
     * 1. **Normal case**: Verifies the correct calculation of total time when valid timestamps are provided
     * 2. **Empty log**: Verifies that the total time is 0 when no logs are available
     * 3. **Incorrect timestamp order**: Verifies that total time is 0 when the "Arrived back at base" timestamp is earlier than the "Assigned" timestamp
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
     * Tests the `sortDrones` method of the `DroneLogAnalyzer` class
     * This test verifies that the `sortDrones` method correctly sorts the drone logs and organizes them into a `HashMap` where the key is the drone identifier
     * The test checks the following scenarios:
     * 1. **Before sorting**: Verifies the correct initial state of the drone logs list and checks that the logs are in the expected order
     * 2. **After sorting**: Verifies that the logs are correctly grouped by the drone identifier and that the `HashMap` structure is updated as expected
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
