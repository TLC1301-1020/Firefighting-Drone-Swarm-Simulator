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
     * Tests the LogEntry class' parse method.
     * Verifies correct parsing of a log line into a LogEntry object.
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
     * Tests reading logs from a file and parsing them into different log categories.
     * Verifies the correct parsing of components, thread types, and events for each log category.
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
     * Tests the {@link DroneLogAnalyzer#averageDeploy(List)} method.
     * Verifies correct deployment time calculation and handling of event order and empty logs.
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
     * Tests the drone runtime calculation based on log entries.
     * Verifies the runtime for different scenarios: valid runtime, zero runtime, and invalid runtime (negative).
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
     * Tests the drone utilization calculation based on log entries and varying lifetimes.
     * Verifies the utilization for normal, zero, negative, and empty logs scenarios.
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
     * Tests the subsystem latency calculation in the DroneLogAnalyzer class.
     * Verifies latency calculation for normal, empty log, incomplete request, and incorrect log order scenarios.
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
     * Tests the throughput calculation in the DroneLogAnalyzer class.
     * Verifies throughput calculation for normal, zero lifetime, negative lifetime, empty log, and missing message scenarios.
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
     * Tests the utilization calculation in the DroneLogAnalyzer class.
     * Verifies utilization calculation for normal, zero lifetime, negative lifetime, empty log, and missing message scenarios.
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
     * Tests the total time calculation in the DroneLogAnalyzer class.
     * Verifies total time calculation for normal timestamps, empty logs, and incorrect timestamps scenarios.
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
     * Tests the sorting and grouping of drone logs by drone ID in the DroneLogAnalyzer class.
     * Verifies that logs are sorted correctly and grouped by the drone ID.
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
