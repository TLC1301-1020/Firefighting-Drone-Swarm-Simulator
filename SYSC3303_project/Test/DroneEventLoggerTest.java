import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;


public class DroneEventLoggerTest {
    private DroneEventLogger logger;
    @BeforeEach
    public void setUp(){
         logger = DroneEventLogger.getInstance();
         logger.getEventBuffer().clear();
    }
    /**
     * Tests the DroneEventClass constructor and getters.
     */
    @Test
    public void TestDroneEventClass(){
        //initialize check
        DroneEventClass test = new DroneEventClass("LEVEL","COMPONENT","THREADTAG","TEST");
        Assertions.assertEquals("LEVEL",test.getLevel());
        Assertions.assertEquals("COMPONENT",test.getComponent());
        Assertions.assertEquals("THREADTAG",test.getThreadTag());
        Assertions.assertEquals("TEST",test.getMessage());
    }
    /**
     * Verifies that the log method correctly adds an event to the buffer.
     */
    @Test
    public void TestLog(){
        logger.log("level", "component", "threadTag", "message");
        Assertions.assertEquals(1, logger.getEventBuffer().size());
        Assertions.assertEquals("level",logger.getEventBuffer().get(0).getLevel());
        Assertions.assertEquals("component",logger.getEventBuffer().get(0).getComponent());
        Assertions.assertEquals("threadTag",logger.getEventBuffer().get(0).getThreadTag());
        Assertions.assertEquals("message",logger.getEventBuffer().get(0).getMessage());

    }
    /**
     * Verifies the info method adds an event with correct details to the buffer.
     */
    @Test
    public void TestInfo(){
        logger.info("component","threadTag","message");
        Assertions.assertEquals(1, logger.getEventBuffer().size());
        Assertions.assertEquals("INFO",logger.getEventBuffer().get(0).getLevel());
        Assertions.assertEquals("component",logger.getEventBuffer().get(0).getComponent());
        Assertions.assertEquals("threadTag",logger.getEventBuffer().get(0).getThreadTag());
        Assertions.assertEquals("message",logger.getEventBuffer().get(0).getMessage());
    }
    /**
     * Verifies the debug method adds an event with correct details to the buffer.
     */
    @Test
    public void TestDebug(){
        logger.debug("component","threadTag","message");
        Assertions.assertEquals(1, logger.getEventBuffer().size());
        Assertions.assertEquals("DEBUG",logger.getEventBuffer().get(0).getLevel());
        Assertions.assertEquals("component",logger.getEventBuffer().get(0).getComponent());
        Assertions.assertEquals("threadTag",logger.getEventBuffer().get(0).getThreadTag());
        Assertions.assertEquals("message",logger.getEventBuffer().get(0).getMessage());
    }
    /**
     * Verifies the warn method adds an event with correct details to the buffer.
     */
    @Test
    public void TestWarn(){
        logger.warn("component","threadTag","message");
        Assertions.assertEquals(1, logger.getEventBuffer().size());
        Assertions.assertEquals("WARN",logger.getEventBuffer().get(0).getLevel());
        Assertions.assertEquals("component",logger.getEventBuffer().get(0).getComponent());
        Assertions.assertEquals("threadTag",logger.getEventBuffer().get(0).getThreadTag());
        Assertions.assertEquals("message",logger.getEventBuffer().get(0).getMessage());
    }
    /**
     * Verifies the error method adds an event with correct details to the buffer.
     */
    @Test
    public void TestError(){
        logger.error("component","threadTag","message");
        Assertions.assertEquals(1, logger.getEventBuffer().size());
        Assertions.assertEquals("ERROR",logger.getEventBuffer().get(0).getLevel());
        Assertions.assertEquals("component",logger.getEventBuffer().get(0).getComponent());
        Assertions.assertEquals("threadTag",logger.getEventBuffer().get(0).getThreadTag());
        Assertions.assertEquals("message",logger.getEventBuffer().get(0).getMessage());
    }
    /**
     * Verifies the error method (with exception) adds an event with correct details to the buffer.
     */
    @Test
    public void TestErrorException(){
        Exception e = new Exception();
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        logger.error("component","threadTag","message", e);
        Assertions.assertEquals(1, logger.getEventBuffer().size());
        Assertions.assertEquals("ERROR",logger.getEventBuffer().get(0).getLevel());
        Assertions.assertEquals("component",logger.getEventBuffer().get(0).getComponent());
        Assertions.assertEquals("threadTag",logger.getEventBuffer().get(0).getThreadTag());
        Assertions.assertEquals("message" + "\n" + sw,logger.getEventBuffer().get(0).getMessage());
    }


}
