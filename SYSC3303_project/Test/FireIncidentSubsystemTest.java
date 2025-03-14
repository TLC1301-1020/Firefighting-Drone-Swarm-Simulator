//import org.junit.jupiter.api.Assertions;
//import org.junit.jupiter.api.*;
//
//public class FireIncidentSubsystemTest {
//    /**
//     * tests {@code readInputFile} method
//     * the task list is initially empty
//     * the input incident file should be read and the incidents should be stored in the task list
//     * ensures the incidents are stored correctly
//     */
//    @Test
//    public void Test_ReadInputFile(){
//        Scheduler scheduler = new Scheduler();
//        FireIncidentSubsystem fireincidents = new FireIncidentSubsystem(scheduler);
//
//        System.out.println("Before reading - Task list should be empty");
//        Assertions.assertTrue(fireincidents.getTasks().isEmpty());
//        String inputFile = "SYSC3303_project/src/fireincidents.txt";
//
//        fireincidents.readInputFile(inputFile);
//
//        System.out.println("After reading - Task list should not be empty");
//        Assertions.assertFalse(fireincidents.getTasks().isEmpty());
//
//        FireRequest taskOne = new FireRequest("10:30:15", 7, "FIRE_DETECTED", "High");
//        Assertions.assertEquals(fireincidents.getTasks().get(0).toString(), taskOne.toString());
//
//        FireRequest taskTwo = new FireRequest("14:10:00", 3, "FIRE_DETECTED", "Moderate");
//        Assertions.assertEquals(fireincidents.getTasks().get(1).toString(), taskTwo.toString());
//
//        FireRequest taskThree = new FireRequest("14:16:03", 2, "FIRE_DETECTED", "Moderate");
//        Assertions.assertEquals(fireincidents.getTasks().get(2).toString(), taskThree.toString());
//    }
//
//}
