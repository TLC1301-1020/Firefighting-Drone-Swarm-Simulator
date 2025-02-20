# Work Breakdown - Iteration 1
UML Class Diagram - Mike

UML Sequence Diagram  - Dylan

Fire Incident Code - Tina

Drone Code - Jake

Scheduler Code - Damon

Testing Code - Andrew

### Files
- *Scheduler.java*: Bridge between the Fire Incident Subsystem and the Drone Subsystem
- *FireIncidentSubsystem.java*: Manages fire incidents, interacts with Scheduler class
- *FireRequest.java*: Data model, manages the variables for each incident
- *Response.java*: Data model, contains the status of a fire incident request
- *DroneSubsystem.java*: Manages drones, interacts with Scheduler class
- *SchedulerTest.java*: Contains the tests for sending request from fire incident subsystem class and sending response from drone subsystem class
- *Main.java*: Set up scheduler and starts the threads
- *Fireincidents.txt*: Contains the incidents information, used in FireIncidentSubsystem class
- *UML Class diagram*: Shows the relationships and structure of the classes
- *UML Sequence diagram*: Shows the interactions between the components



### Setup instruction
Run Main.java, make sure the directory of the input file is correct

## Test instruction
Run SchedulerTest, make sure Junit is added to the path





# Work Breakdown - Iteration 2

Scheduler code + javadoc - Dylan

State Machine Diagram - Jake

Updated DroneSubsystem code + javadoc + State tests - Mike

Sequence Diagram + Update the Class diagram UML + Update Readme - Damon

- Andrew

Updated test classes - Tina

### Files
- *Scheduler.java*: Bridge between the Fire Incident Subsystem and the Drone Subsystem
- *FireIncidentSubsystem.java*: Manages fire incidents, interacts with Scheduler class
- *FireRequest.java*: Data model, manages the variables for each incident
- *Response.java*: Data model, contains the status of a fire incident request
- *DroneSubsystem.java*: Manages drones, interacts with Scheduler class
- *SchedulerTest.java*: Contains the tests for sending request from fire incident subsystem class and sending response from drone subsystem class
- *FireIncidentSubsystemTest.java*: Contains the tests for the fireIncidentSubsystem class
- *DroneSubsystemTest.java*: Contains the tests for the dronesubsystem class, tests for the states
- *Main.java*: Set up scheduler and starts the threads
- *Fireincidents.txt*: Contains the incidents information, used in FireIncidentSubsystem class
- *UML Class diagram*: Shows the relationships and structure of the classes
- *UML Sequence diagram*: Shows the interactions between the components
- *State Machine diagram*: Shows the states

### Setup instruction
Run Main.java, make sure the directory of the input file is correct

# Test instruction
Run SchedulerTest, FireIncidentSubsystemTest, DroneSubsystemTest.
Make sure Junit is added to the path
