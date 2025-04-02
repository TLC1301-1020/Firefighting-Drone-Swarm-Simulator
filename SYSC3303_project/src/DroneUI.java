import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class DroneUI {

    // Adjustable scaling factor: each character represents scale meters
    private static final int SCALE_METERS = 150;

    // Shared thread-safe queue for UI updates
    private static final BlockingQueue<UIUpdate> sharedQueue = new LinkedBlockingQueue<>();

    public static BlockingQueue<UIUpdate> getSharedQueue() {
        return sharedQueue;
    }


    public static void main(String[] args) {
        List<Zone> zones = readZonesFromCSV("SYSC3303_project/src/zone_file.csv");
        if (zones.isEmpty()) {
            zones = getDefaultZones();
        }

        // Start UI thread
        Thread uiThread = new Thread(new UIComponent(sharedQueue, zones, SCALE_METERS));
        uiThread.start();

        Scheduler scheduler = new Scheduler(sharedQueue);
    }


    /**
     * Data model representing a Zone
     */
    static class Zone {
        int zoneId;
        int startX;
        int startY;
        int endX;
        int endY;

        public Zone(int zoneId, int startX, int startY, int endX, int endY) {
            this.zoneId = zoneId;
            this.startX = startX;
            this.startY = startY;
            this.endX = endX;
            this.endY = endY;
        }

        @Override
        public String toString() {
            return String.format("Zone %d: (%d,%d) to (%d,%d)", zoneId, startX, startY, endX, endY);
        }
    }

    /**
     * Data model representing the status of a drone
     */
    static class DroneStatus {
        int droneId;
        int x; // in meters
        int y; // in meters
        String fireStatus; // "High", "Moderate", "Low" or empty if not on fire

        public DroneStatus(int droneId, int x, int y, String fireStatus) {
            this.droneId = droneId;
            this.x = x;
            this.y = y;
            this.fireStatus = fireStatus;
        }
    }

    /**
     * Data model representing an active fire.
     */
    static class Fire {
        String fireId;
        String severity; // "High", "Moderate", or "Low"
        int zoneId;      // The zone in which the fire is located

        public Fire(String fireId, String severity, int zoneId) {
            this.fireId = fireId;
            this.severity = severity;
            this.zoneId = zoneId;
        }
    }

    /**
     * Data model representing a fault
     */
    static class Fault {
        int droneId;
        String faultDescription;

        public Fault(int droneId, String faultDescription) {
            this.droneId = droneId;
            this.faultDescription = faultDescription;
        }
    }

    /**
     * Container for an update to the UI.
     */
    static class UIUpdate {
        List<DroneStatus> droneStatuses;
        List<Fire> activeFires;
        List<Fault> faults;

        public UIUpdate(List<DroneStatus> droneStatuses, List<Fire> activeFires, List<Fault> faults) {
            this.droneStatuses = droneStatuses;
            this.activeFires = activeFires;
            this.faults = faults;
        }
    }

    /**
     * The UIComponent listens for new updates on the shared queue, and
     * refreshes the ASCII UI whenever an update is received.
     */
    static class UIComponent implements Runnable {
        private final BlockingQueue<UIUpdate> queue;
        private final List<Zone> zones;
        private final int scale;
        // Calculate grid dimensions based on zone data (static zones)
        private final int gridWidth;
        private final int gridHeight;

        public UIComponent(BlockingQueue<UIUpdate> queue, List<Zone> zones, int scale) {
            this.queue = queue;
            this.zones = zones;
            this.scale = scale;
            int maxX = 0;
            int maxY = 0;
            // Determine overall boundaries from zone data
            for (Zone zone : zones) {
                maxX = Math.max(maxX, zone.endX);
                maxY = Math.max(maxY, zone.endY);
            }
            // Calculate grid dimensions (each cell represents scale meters)
            this.gridWidth = (int) Math.ceil((double) maxX / scale);
            this.gridHeight = (int) Math.ceil((double) maxY / scale);
        }

        @Override
        public void run() {
            while (true) {
                try {
                    // Block until an update is available
                    UIUpdate update = queue.take();
                    refreshUI(update);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.err.println("UIComponent interrupted.");
                    break;
                }
            }
        }

        /**
         * Clears the console and prints the updated UI.
         */
        private void refreshUI(UIUpdate update) {
            clearScreen();
            // 1. Print Zone Information and Scale Indicator
            System.out.println("=== ZONE INFO ===");
            for (Zone zone : zones) {
                System.out.println(zone);
            }
            System.out.println();

            // 2. Print the Grid Map with Drone Positions
            // Create a 2D array to represent the grid cells
            String[][] grid = new String[gridHeight][gridWidth];
            // Initialize grid cells with a dot
            for (int i = 0; i < gridHeight; i++) {
                Arrays.fill(grid[i], " . ");
            }
            // Place drones on the grid
            // If multiple drones are in the same cell display an asterisk
            Map<String, String> cellMap = new HashMap<>();
            for (DroneStatus drone : update.droneStatuses) {
                int gridX = (int) Math.round((double) drone.x / scale);
                int gridY = (int) Math.round((double) drone.y / scale);
                // Ensure the coordinates are within grid bounds
                if (gridX < 0 || gridX >= gridWidth || gridY < 0 || gridY >= gridHeight) {
                    continue;
                }
                String key = gridY + "," + gridX;
                if (cellMap.containsKey(key)) {
                    cellMap.put(key, " * ");
                } else {
                    cellMap.put(key, String.format("%3d", drone.droneId));
                }
            }
            // Update grid cells from the map
            for (Map.Entry<String, String> entry : cellMap.entrySet()) {
                String[] parts = entry.getKey().split(",");
                int y = Integer.parseInt(parts[0]);
                int x = Integer.parseInt(parts[1]);
                grid[y][x] = entry.getValue();
            }
            // Print grid rows from top (highest y) to bottom
            System.out.println("=== MAP ===");
            System.out.println(String.format("Scale: 1 char = %dm", scale));
            for (int row = gridHeight - 1; row >= 0; row--) {
                for (int col = 0; col < gridWidth; col++) {
                    System.out.print(grid[row][col]);
                }
                System.out.println();
            }
            System.out.println();

            // 3. Print Active Fires List
            System.out.println("=== ACTIVE FIRES ===");
            if (update.activeFires.isEmpty()) {
                System.out.println("No active fires");
            } else {
                for (Fire fire : update.activeFires) {
                    System.out.println(String.format("- %s: %s (Zone %d)", fire.fireId, fire.severity, fire.zoneId));
                }
            }
            System.out.println();

            // 4. Print Faults List
            System.out.println("=== ACTIVE FAULTS ===");
            if (update.faults.isEmpty()) {
                System.out.println("No faults");
            } else {
                for (Fault fault : update.faults) {
                    System.out.println(String.format("- Drone %d: %s", fault.droneId, fault.faultDescription));
                }
            }
            System.out.println();

            // 5. Print Stats Section
            System.out.println(String.format("Stats: Zones: %d | Active Drones: %d", zones.size(), update.droneStatuses.size()));
            System.out.println();
        }

        /**
         * Clears the console using ANSI escape codes (trying to keep it clean)
         */
        private void clearScreen() {
            System.out.print("\033[H\033[2J");
            System.out.flush();
        }
    }

    /**
     * The SimulationDriver simulates the scheduler by periodically generating
     * test updates and sending them to the shared queue. In the future this will become a bridge to
     * bridge the UI to the scheduler.
     */
    static class SimulationDriver implements Runnable {
        private final BlockingQueue<UIUpdate> queue;
        private final List<Zone> zones;
        private final int scale;
        private final Random random = new Random();
        // Define a set of possible fire severities and fault messages
        private final String[] severities = {"High", "Moderate", "Low"};
        private final String[] faultMessages = {"Drone stuck mid-flight", "Nozzle jammed", "Packet loss or corrupted messages"};
        // Define a fixed list of drone IDs for simulation
        private final int[] droneIds = {1, 2, 3, 4, 5};

        public SimulationDriver(BlockingQueue<UIUpdate> queue, List<Zone> zones, int scale) {
            this.queue = queue;
            this.zones = zones;
            this.scale = scale;
        }

        @Override
        public void run() {
            while (true) {
                try {
                    // Sleep for a random interval between 1 to 3 seconds
                    Thread.sleep((1 + random.nextInt(3)) * 1000);

                    // Generate random drone statuses
                    List<DroneStatus> drones = new ArrayList<>();
                    for (int id : droneIds) {
                        // For simulation, assume x in [0, maxX] and y in [0, maxY]
                        int maxX = getMaxX(zones);
                        int maxY = getMaxY(zones);
                        int x = random.nextInt(maxX + 1);
                        int y = random.nextInt(maxY + 1);
                        // With 50% chance, assign a fire status
                        String fireStatus = "";
                        if (random.nextBoolean()) {
                            fireStatus = severities[random.nextInt(severities.length)];
                        }
                        drones.add(new DroneStatus(id, x, y, fireStatus));
                    }

                    // Generate random active fires
                    List<Fire> fires = new ArrayList<>();
                    // With 50% probability, create 1-2 active fires
                    if (random.nextBoolean()) {
                        int numFires = 1 + random.nextInt(2);
                        for (int i = 0; i < numFires; i++) {
                            // Choose a random zone from the available zones
                            Zone zone = zones.get(random.nextInt(zones.size()));
                            String fireId = "Fire-" + (char)('A' + i);
                            String severity = severities[random.nextInt(severities.length)];
                            fires.add(new Fire(fireId, severity, zone.zoneId));
                        }
                    }

                    // Generate random faults
                    List<Fault> faults = new ArrayList<>();
                    // With 30% probability, add a fault
                    if (random.nextInt(100) < 30) {
                        int droneId = droneIds[random.nextInt(droneIds.length)];
                        String faultDescription = faultMessages[random.nextInt(faultMessages.length)];
                        faults.add(new Fault(droneId, faultDescription));
                    }

                    // Create the update object
                    UIUpdate update = new UIUpdate(drones, fires, faults);

                    // Push the update into the shared queue
                    queue.put(update);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.err.println("SimulationDriver interrupted.");
                    break;
                }
            }
        }

        /**
         * Helper to get the maximum X value from the zone list.
         */
        private int getMaxX(List<Zone> zones) {
            int max = 0;
            for (Zone zone : zones) {
                max = Math.max(max, zone.endX);
            }
            return max;
        }

        /**
         * Helper to get the maximum Y value from the zone list.
         */
        private int getMaxY(List<Zone> zones) {
            int max = 0;
            for (Zone zone : zones) {
                max = Math.max(max, zone.endY);
            }
            return max;
        }
    }

    /**
     * Reads zone data from a CSV file
     * Expected format:
     * Zone ID,Zone Start,Zone End
     * 7,(0;0),(700;600)
     * 3,(0;600),(650;1500)
     * 2,(650;1500),(800;1800)
     *
     * If the file is not found or there's an error and an empty list is returned
     */
    private static List<Zone> readZonesFromCSV(String filename) {
        List<Zone> zones = new ArrayList<>();
        File file = new File(filename);
        if (!file.exists()) {
            System.err.println("CSV file not found: " + filename);
            return zones;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            // Read and ignore header
            if ((line = br.readLine()) == null) {
                return zones;
            }
            // Read next lines
            while ((line = br.readLine()) != null) {
                // Remove any spaces and split by comma
                String[] parts = line.split(",");
                if (parts.length < 3) continue;
                try {
                    int zoneId = Integer.parseInt(parts[0].trim());
                    // Remove parentheses and split coordinates by ;
                    String startStr = parts[1].trim().replace("(", "").replace(")", "");
                    String[] startCoords = startStr.split(";");
                    int startX = Integer.parseInt(startCoords[0].trim());
                    int startY = Integer.parseInt(startCoords[1].trim());
                    String endStr = parts[2].trim().replace("(", "").replace(")", "");
                    String[] endCoords = endStr.split(";");
                    int endX = Integer.parseInt(endCoords[0].trim());
                    int endY = Integer.parseInt(endCoords[1].trim());
                    zones.add(new Zone(zoneId, startX, startY, endX, endY));
                } catch (Exception e) {
                    System.err.println("Error parsing line: " + line);
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading CSV file: " + e.getMessage());
        }
        return zones;
    }

    /**
     * Provides default zone data in case the CSV file is not available (only for testing, should never reach here)
     */
    private static List<Zone> getDefaultZones() {
        List<Zone> defaultZones = new ArrayList<>();
        defaultZones.add(new Zone(7, 0, 0, 700, 600));
        defaultZones.add(new Zone(3, 0, 600, 650, 1500));
        defaultZones.add(new Zone(2, 650, 1500, 800, 1800));
        return defaultZones;
    }
}