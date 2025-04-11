# Final Submission: SYSC3303 Project Group 9

## Overview

This project simulates a fire incident management system where drones are dispatched to handle fire emergencies. The system consists of three main subsystems: **FireIncidentSubsystem**, **DroneSubsystem**, and **Scheduler**. These subsystems interact to efficiently manage fire incidents, dispatch drones, and handle events through state-driven designs, ensuring seamless coordination between the components.

You can watch a demonstration of the project here:

[Project Video](https://youtu.be/3Yo4EDRgf9k)

---

## Features

- **FireIncidentSubsystem**: Handles fire incidents, initiates requests to the Scheduler for drone deployment.
- **DroneSubsystem**: Manages drones, their states, and operations. It coordinates with the Scheduler for task assignments and fault management.
- **Scheduler**: Serves as the central coordinator between the Fire Incident and Drone Subsystems, managing task execution based on incoming requests and predefined conditions.
- **Logging and Analytics**: Provides real-time logging of drone activities and fire incident statuses, with built-in analyzers for system performance.
- **Event Handling**: Manages timed events such as fire incidents and drone faults, ensuring correct sequence and synchronization.
- **UI**: Basic user interface displays real-time information about drones and incidents, offering visibility into system operations.

---

## File Structure

### **Source Code**
- **Drone.java**: Contains drone information such as ID, type, and operational status.
- **DroneEvent.java**: Captures event-related details for drones (e.g., faults, operational updates).
- **DroneEventClass.java**: Represents specific event details for the drone subsystem.
- **DroneLogAnalyzer.java**: Analyzes and provides insights into drone and subsystem performance metrics.
- **DroneState.java**: Stores and manages the various states of drones during operation.
- **DroneStatus.java**: Tracks the current operational status of each drone (e.g., active, idle).
- **DroneSubsystem.java**: Manages drones, their states, and interactions with the Scheduler.
- **DroneUI.java**: Implements the user interface for displaying drone status and events.
- **Event.java**: Defines a timed event, such as a fire incident or a drone fault.
- **EventScheduler.java**: Manages the scheduling and deployment of events according to timestamps.
- **FireIncidentLogAnalyzer.java**: Calculates and provides metrics for fire incident subsystem performance.
- **FireIncidentSubsystem.java**: Manages fire incidents and interacts with the Scheduler for drone assignments.
- **FireRequest.java**: Data model that holds information about each fire incident request.
- **Response.java**: Data model that captures the status of a fire incident request.
- **Scheduler.java**: Coordinates the interactions between the Fire Incident and Drone Subsystems.
- **SchedulerEventClass.java**: Defines the event structure for the Scheduler.
- **SchedulerEventLogger.java**: Contains the method for logging.
- **SchedulerLogAnalyzer.java**: Calculates and tracks Scheduler performance metrics.
- **SchedulerState.java**: Maintains and updates the states of the Scheduler.
- **Zone.java**: Contains information about the different zones in which drones operate.

### **Test Code**
- **DroneEventLoggerTest.java**: Tests the methods of the DroneEventLogger class for logging events.
- **DroneLogAnalyzerTest.java**: Contains test cases for validating the DroneLogAnalyzer.
- **DroneSubsystemTest.java**: Tests for the DroneSubsystem, focusing on state transitions and drone management.
- **DroneTest.java**: Tests the behavior and state transitions of individual drones.
- **EventTest.java**: Verifies the functionality of the EventScheduler class.
- **FireIncidentSubsystemTest.java**: Tests the FireIncidentSubsystem class, ensuring fire incidents are managed correctly.
- **SchedulerTest.java**: Tests the Scheduler, focusing on state transitions and event handling.

### **Other Files**
- **Fireincidents.txt**: Contains fire incident data used by the FireIncidentSubsystem.
- **Faults.txt**: Contains drone fault data for testing the DroneSubsystem.
- **zone_file.csv**: Data for zones (ID, range, etc.).
- **drone_event_log.txt**: Log file capturing drone subsystem events.
- **scheduler_event_log.txt**: Log file capturing Scheduler events.
- **firesubsystem_logs.txt**: Log file for fire subsystem activities.

---

## Setup Instructions

1. Ensure that the **Zone file** and **FireIncident input file** are located in the correct directory.
2. Run the **Scheduler**, **DroneSubsystem**, and **FireIncidentSubsystem** in sequence.
- Modify **fireincidents.txt** and **Faults.txt** to control fire incident and drone fault timings.
- Adjust the **simulationSpeed** attribute in **EventScheduler** to control the overall simulation speed.

---

## Test Instructions

- The project uses **JUnit 5.8.1** and **JDK 22**.
- To execute the tests:
    - **SchedulerTest.java**: Tests the Scheduler's state transitions and event handling.
    - **DroneSubsystemTest.java**: Validates the DroneSubsystem functionality and state changes.
    - **FireIncidentSubsystemTest.java**: Tests the FireIncidentSubsystem's behavior and interactions.
    - **DroneEventLoggerTest.java**: Verifies logging functionality in the DroneEventLogger.
    - **DroneLogAnalyzerTest.java**: Ensures correct analysis of drone subsystem metrics.

---

## Additional Notes

- All issues from previous iterations have been addressed and resolved.
- The project is now ready for final deployment and evaluation.




# Previous Iterations

## Work Breakdown - Iteration 0
Data collection

### Files
- *Data Analysis.docx*
- *Data.xlsx*

## Work Breakdown - Iteration 1
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

### Test instruction
Run SchedulerTestOld, make sure Junit is added to the path





## Work Breakdown - Iteration 2

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



## Work Breakdown - Iteration 3

### Work Breakdown
Set up remote procedure call - Jake

Done - Mike

Scheduler - Dylan, Damon

Update testing (unit and system) - Andrew

Update diagrams and readme file - Tina

Debug code - Dylan, Jake, Mike

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
- *README.md*: Contains the explanation of the project for this iteration (3), including names of the files, team members, work breakdown, and instruction to set and run the program
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
Make sure the directory of the Zone file and FireIncident input file is correct

### Test instruction
Project uses Junit5.8.1, JDK22

### Note
Some issues to check for: https://docs.google.com/document/d/1yOJuDtkl1yb6xlGa3G38a8q0eshFcxU1HcMObruX3mc/edit?tab=t.0

## Work Breakdown - Iteration 4

### Work Breakdown

Handling Drone Faults - Dylan, Tina, Jake

Scheduler Refactoring - Dylan, Jake, Mike

Update testing (unit and system) - Mike

Update diagrams and readme file - Jake, Tina, Mike

UI (Future Iteration) - Damon, Andrew

### Files - Source code
- *DroneState.java*: Stores the states to be used for the drones
- *DroneSubsystem.java* Manages drones, interacts with Scheduler class
- *FireIncidentSubsystem.java*: Manages fire incidents, interacts with Scheduler class
- *FireRequest.java*: Data model, manages the variables for each incident
- *Response.java*: Data model, contains the status of a fire incident request
- *Scheduler.java*: Bridge between the Fire Incident Subsystem and the Drone Subsystem
- *SchedulerState.java*: Stores the states to be used for the scheduler
- *Zone.java*:Contains the zone information
- *Drone.java*: Contains information of the drone
- *DroneEvent.java*: Event information of the drone
- *DroneStatus.java*: Current status of the drone
- *Event.java*: Stores a timed event such as a drone fault or a fire incident
- *EventScheduler.java*: Handles the proper deployment of Events based on its timestamp

### Files - Test code
- *SchedulerTest.java*: Contains the test methods for the scheduler source code, focusing on the state transitions
- *FireIncidentSubsystemTest.java*: Contains the test methods for the fire incident subsystem class
- *DroneSubsystemTest.java*: Contains the test methods for the drone subsystem, focusing on the state transitions
- *DroneTest.java*: Contains the test methods for the drones, focusing on the state transitions

### Files - Diagrams and others
- *README.md*: Contains the explanation of the project for this iteration (4), including names of the files, team members, work breakdown, and instruction to set and run the program
- *Fireincidents.txt*: Contains the incident events, used in FireIncidentSubsystem class
- *Faults.txt*: Contains the drone fault events, used in DroneSubsystem class
- *UMLClassDiagram-iteration1*: Shows the relationships and structure of the classes for iteration 1
- *DroneSequenceDiagram-iteration1*: Shows interactions between the components for iteration 1
- *UMLClassDiagram-iteration2*: Shows the relationships and structure of the classes for iteration 2
- *UMLSequenceDiagram-iteration2*: Shows the interactions between the components for iteration 2
- *SchedulerStateDiagram-iteration2*: The behavioural diagram for the Scheduler that represents the transitions between the states on events/conditions
- *DroneStateDiagram-iteration2*: The behavioural diagram for the DroneSubsystem that represents the transitions between the states on events/conditions
- *UMLClassDiagram-iteration3*: The overall structure of the project in iteration 3
- *DroneClassDiagramIteration4.png*: Drone's overall structure, for iteration 4
- *DroneSubsystemClassDiagramIteration4.png*: DroneSubsystem's overall structure for iteration 4
- *SchedulerClasssDiagramIteration4.png*: Scheduler's overall structure for iteration 4
- *TimingDiagram-iteration4.png*: Shows the behaviour of the objects over time
- *zone_file.csv*: Zone id, Zone range (x and y)
- *run.bat*: command to run the program

### Setup instruction
- Make sure the directory of the Zone file and FireIncident input file is correct
- Run Scheduler, DroneSubsystem, FireIncidentSubsystem in that order
- Change incident timing through fireincidents.txt, Drone fault timing through Faults.txt
- Change overall timing simulation speed through the simulationSpeed attribute located in EventScheduler

### Test instruction
Project uses Junit5.8.1, JDK22

### Note
The issues from the previous iteration are resolved


## Work Breakdown - Iteration 5

### Work Breakdown

DroneSubsystem Event logger, analyzer, and tests - Jake, Tina

Scheduler Event logger, analyzer, and tests - Mike, Dylan

FireIncidentSubsystem Event logger and analyzer UI - Andrew, Damon

Debugging, Refactoring, Update tests, and README - all

### Files - Source code
- *Drone.java*: Contains information of the drone
- *DroneEvent.java*: Status of the drone
- *DroneEventClass.java*: The drone event's information
- *DroneLogAnalyzer.java*: Calculates the metrics of the drones, subsystem threads
- *DroneState.java*: Stores the states to be used for the drones
- *DroneStatus.java*: Current status of the drone
- *DroneSubsystem.java* Manages drones, interacts with Scheduler class
- *DroneUI.java*: UI code for the program

- *Event.java*: Stores a timed event such as a drone fault or a fire incident
- *EventScheduler.java*: Handles the proper deployment of Events based on its timestamp
- *FireIncidentLogAnalyzer.java*: Calculates the metrics for the FireIncidentSubsystem side
- *FireIncidentSubsystem.java*: Manages fire incidents, interacts with Scheduler class
- *FireRequest.java*: Data model, manages the variables for each incident
- *Response.java*: Data model, Contains the status of a fire incident request

- *Scheduler.java*: Bridge between the Fire Incident Subsystem and the Drone Subsystem
- *SchedulerEventClass.java*: Contains the scheduler's event information
- *SchedulerEventLogger.java*: Contains the method for logging
- *SchedulerLogAnalyzer.java*: Calculates the metrics for the Scheduler side
- *SchedulerState.java*: Stores the states to be used for the scheduler
- *Zone.java*: Contains the zone information

### Files - Test code
- *DroneEventLoggerTest.java*: Contains the test methods for the methods in DroneEventLogger class
- *DroneLogAnalyzerTest.java*: Contains the test methods for the methods in DroneLogAnalyzer class
- *DroneSubsystemTest.java*: Contains the test methods for the drone subsystem, focusing on the state transitions
- *DroneTest.java*: Contains the test methods for the drones, focusing on the state transitions
- *EventTest.java*: Contains the test methods for the EventScheduler
- *FireIncidentSubsystemTest.java*: Contains the test methods for the fire incident subsystem class
- *SchedulerTest.java*: Contains the test methods for the scheduler source code, focusing on the state transitions

### Files - Diagrams and others
- *Fireincidents.txt*: Contains the incident events, used in FireIncidentSubsystem class
- *Faults.txt*: Contains the drone fault events, used in DroneSubsystem class
- *UMLClassDiagram-iteration1*: Shows the relationships and structure of the classes for iteration 1
- *DroneSequenceDiagram-iteration1*: Shows interactions between the components for iteration 1
- *UMLClassDiagram-iteration2*: Shows the relationships and structure of the classes for iteration 2
- *UMLSequenceDiagram-iteration2*: Shows the interactions between the components for iteration 2
- *SchedulerStateDiagram-iteration2*: The behavioural diagram for the Scheduler that represents the transitions between the states on events/conditions
- *DroneStateDiagram-iteration2*: The behavioural diagram for the DroneSubsystem that represents the transitions between the states on events/conditions
- *UMLClassDiagram-iteration3*: The overall structure of the project in iteration 3
- *DroneClassDiagramIteration4.png*: Drone's overall structure, for iteration 4
- *DroneSubsystemClassDiagramIteration4.png*: DroneSubsystem's overall structure for iteration 4
- *SchedulerClasssDiagramIteration4.png*: Scheduler's overall structure for iteration 4
- *TimingDiagram-iteration4.png*: Shows the behaviour of the objects over time

- *README.md*: Contains the explanation of the project for this iteration (5), including names of the files, team members, work breakdown, and instruction to set and run the program
- *zone_file.csv*: Zone id, Zone range (x and y)
- *run.bat*: Command to run the programs
- *drone_event_log.txt*: The log file for the droneSubsystem (including the drones)
- *scheduler_event_log.txt*: The log file for the Scheduler
- *firesubsystem_logs.txt*: The log file for the fireSubsystem
### Setup instruction
- Make sure the directory of the Zone file and FireIncident input file is correct
- Run Scheduler, DroneSubsystem, FireIncidentSubsystem in that order
- Change incident timing through fireincidents.txt, Drone fault timing through Faults.txt
- Change overall timing simulation speed through the simulationSpeed attribute located in EventScheduler

### Test instruction
Project uses Junit5.8.1, JDK22