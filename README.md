# Work Breakdown - Iteration 0
Data collection

### Files
- *Data Analysis.docx*
- *Data.xlsx*

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
- *SchedulerTestOld.java*: Contains the tests for sending request from fire incident subsystem class and sending response from drone subsystem class
- *Main.java*: Set up scheduler and starts the threads
- *Fireincidents.txt*: Contains the incidents information, used in FireIncidentSubsystem class
- *UML Class diagram*: Shows the relationships and structure of the classes
- *UML Sequence diagram*: Shows the interactions between the components



### Setup instruction
Run Main.java, make sure the directory of the input file is correct

## Test instruction
Run SchedulerTestOld, make sure Junit is added to the path





# Work Breakdown - Iteration 2

Updated Scheduler code + Scheduler test code - Dylan

State Machine Diagram - Jake

Updated DroneSubsystem code + javadoc + State tests - Mike

Sequence Diagram + Update the Class diagram UML + Update Readme - Damon

Updated Scheduler code + Scheduler test code - Andrew

Updated test classes, javadoc, updated Readme - Tina

### Files - Source code
- *DroneState.java*: Stores the states to be used for the drones
- *DroneSubsystem.java* Manages drones, interacts with Scheduler class
- *FireIncidentSubsystem.java*: Manages fire incidents, interacts with Scheduler class
- *FireRequest.java*: Data model, manages the variables for each incident
- *Main.java*: Set up scheduler and starts the threads
- *Response.java*: Data model, contains the status of a fire incident request
- *Scheduler.java*: Bridge between the Fire Incident Subsystem and the Drone Subsystem
- *SchedulerState.java*: Stores the states to be used for the scheduler


### Files - Test code
- *SchedulerTestOld.java*: Contains the test methods(iteration 1) for sending request from fire incident subsystem class and sending response from drone subsystem class
- *SchedulerTest.java*: Contains the test methods for the scheduler source code, focusing on the state transitions
- *FireIncidentSubsystemTest.java*: Contains the test methods for the fire incident subsystem class
- *DroneSubsystemTest.java*: Contains the test methods for the drone subsystem, focusing on the state transitions


### Files - Diagrams and others
- *README.md*: Contains the explanation of the project for this iteration (2), including names of the files, team members, work breakdown, and instruction to set and run the program
- *Fireincidents.txt*: Contains the incidents information, used in FireIncidentSubsystem class
- *UMLClassDiagram-iteration1*: Shows the relationships and structure of the classes for iteration 1
- *DroneSequenceDiagram-iteration1*: Shows interactions between the components for iteration 1
- *UMLClassDiagram-iteration2*: Shows the relationships and structure of the classes for iteration 2
- *UMLSequenceDiagram-iteration2*: Shows the interactions between the components for iteration 2
- *SchedulerStateDiagram-iteration2*: The behavioural diagram for the Scheduler that represents the transitions between the states on events/conditions
- *DroneStateDiagram-iteration2*: The behavioural diagram for the DroneSubsystem that represents the transitions between the states on events/conditions


### Setup instruction
Run Main.java, make sure the directory of the input file is correct

### Test instruction
Run SchedulerTestOld, FireIncidentSubsystemTest, DroneSubsystemTest, SchedulerTest.
Project uses Junit5.8.1, JDK22



# Work Breakdown - Iteration 3

### Work Breakdown
Set up remote procedure call - Jake

Done - Mike

Scheduler - Dylan, Damon

Update testing (unit and system) - Andrew

Update diagrams and readme file - Tina

### Files - Source code
- *DroneState.java*: Stores the states to be used for the drones
- *DroneSubsystem.java* Manages drones, interacts with Scheduler class
- *FireIncidentSubsystem.java*: Manages fire incidents, interacts with Scheduler class
- *FireRequest.java*: Data model, manages the variables for each incident
- *Main.java*: Set up scheduler and starts the threads
- *Response.java*: Data model, contains the status of a fire incident request
- *Scheduler.java*: Bridge between the Fire Incident Subsystem and the Drone Subsystem
- *SchedulerState.java*: Stores the states to be used for the scheduler
- *Zone.java*:Contains the zone information
- *Drone.java*: Contains information of the drone
- *DroneEvent.java*: Event information of the drone
- *DroneStatus.java*: Current status of the drone

### Files - Test code
- *SchedulerTest.java*: Contains the test methods for the scheduler source code, focusing on the state transitions
- *FireIncidentSubsystemTest.java*: Contains the test methods for the fire incident subsystem class
- *DroneSubsystemTest.java*: Contains the test methods for the drone subsystem, focusing on the state transitions

### Files - Diagrams and others
- *README.md*: Contains the explanation of the project for this iteration (2), including names of the files, team members, work breakdown, and instruction to set and run the program
- *Fireincidents.txt*: Contains the incidents information, used in FireIncidentSubsystem class
- *UMLClassDiagram-iteration1*: Shows the relationships and structure of the classes for iteration 1
- *DroneSequenceDiagram-iteration1*: Shows interactions between the components for iteration 1
- *UMLClassDiagram-iteration2*: Shows the relationships and structure of the classes for iteration 2
- *UMLSequenceDiagram-iteration2*: Shows the interactions between the components for iteration 2
- *SchedulerStateDiagram-iteration2*: The behavioural diagram for the Scheduler that represents the transitions between the states on events/conditions
- *DroneStateDiagram-iteration2*: The behavioural diagram for the DroneSubsystem that represents the transitions between the states on events/conditions
- *UMLClassDiagram-iteration3*: The overall structure of the project in iteration 3
- *zone_file.csv*: Zone id, Zone range (x and y)
- *run.bat*: command to run the program

### Setup instruction
Make sure the directory of the input file is correct

### Test instruction
Project uses Junit5.8.1, JDK22
