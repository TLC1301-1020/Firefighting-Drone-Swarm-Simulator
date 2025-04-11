import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.*;
import java.io.*;

/**
 * Unit test class for FireIncidentLogAnalyzer
 * <p>
 * test verifies that the log analyzer correctly parses and reports key metrics
 * from a simulated fire incident log file
 */
class FireIncidentLogAnalyzerTest {

    /** Name of the test log file used during testing */
    private final String logFileName = "firesubsystem_logs.txt";

    /**
     * Sets up a sample log file before each test.
     * The file includes timestamp of entries that simulate fire incidents and idle periods
     */
    @BeforeEach
    void setUp() throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFileName))) {
            writer.write("10:00:00.000 - Sending Incident - Incident1\n");
            writer.write("10:00:05.000 - STS Idle Start - Waiting for tasks\n");
            writer.write("10:00:10.000 - STS Idle End - Task available\n");
            writer.write("10:00:15.000 - Sending Incident - Incident2\n");
        }
    }

    /**
     * Cleans up by deleting the test log file after each test.
     */
    @AfterEach
    void tearDown() {
        File file = new File(logFileName);
        if (file.exists()) {
            file.delete();
        }
    }

    /**
     * Tests the output of FireIncidentLogAnalyzer to ensure
     * it contains expected metrics: start time, end time, idle time, incident count, and utilization.
     */
    @Test
    void testAnalyzerMainOutput() {
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outContent));

        FireIncidentLogAnalyzer.main(new String[]{logFileName});

        System.setOut(originalOut);
        String output = outContent.toString();

        assertTrue(output.contains("Start Time:"), "Output should contain 'Start Time:'");
        assertTrue(output.contains("End Time:"), "Output should contain 'End Time:'");
        assertTrue(output.contains("Lifetime (ms):"), "Output should contain 'Lifetime (ms):'");
        assertTrue(output.contains("Total fire incidents sent out:"), "Output should contain 'Total fire incidents sent out:'");
        assertTrue(output.contains("Total idle time (ms):"), "Output should contain 'Total idle time (ms):'");
        assertTrue(output.contains("Utilization (% active):"), "Output should contain 'Utilization (% active):'");
    }
}
