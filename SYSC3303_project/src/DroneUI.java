import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

class DroneUIApp {

    // Adjustable scaling factor: each character represents scale meters
    private static final int SCALE_METERS = 150;

    // Shared thread-safe queue for UI updates
    private static final BlockingQueue<UIUpdate> sharedQueue = new LinkedBlockingQueue<>();

    public static void main(String[] args) {
        // Load zones from CSV (zone_file.csv). Default settings if not found (but should not be used just for testing)
        List<Zone> zones = readZonesFromCSV("SYSC3303_project/src/zone_file.csv");
        if (zones.isEmpty()) {
            zones = getDefaultZones();
        }

        // Start the UI component thread
        Thread uiThread = new Thread(new UIComponent(sharedQueue, zones, SCALE_METERS));
        uiThread.start();

        int UI_RECEIVER_PORT = 6000; // define a port for UI updates
        Thread receiverThread = new Thread(new UIReceiver(sharedQueue, zones, UI_RECEIVER_PORT));
        receiverThread.start();
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

                if(isFaultedDrone(drone, update.faults)) {
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

        private boolean isFaultedDrone(DroneStatus drone, List<Fault> faults) {
            for (Fault fault : faults) {
                if (fault.droneId == drone.droneId) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Clears the console using ANSI escape codes (trying to keep it clean)
         */
        private void clearScreen() {
            System.out.print("\033[H\033[2J");
            System.out.flush();
        }
    }

    static class UIReceiver implements Runnable {
        private final BlockingQueue<UIUpdate> queue;
        private final List<Zone> zones;
        private final int port;

        public UIReceiver(BlockingQueue<UIUpdate> queue, List<Zone> zones, int port) {
            this.queue = queue;
            this.zones = zones;
            this.port = port;
        }

        @Override
        public void run() {
            try (DatagramSocket socket = new DatagramSocket(port)) {
                byte[] buffer = new byte[4096];
                while (true) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    String data = new String(packet.getData(), 0, packet.getLength());
                    UIUpdate update = parseUpdateFromString(data);
                    if (update != null) {
                        queue.put(update);
                    }
                }
            } catch (IOException | InterruptedException e) {
                System.err.println("UIReceiver error: " + e.getMessage());
            }
        }

        private UIUpdate parseUpdateFromString(String data) {
            List<DroneStatus> drones = new ArrayList<>();
            List<Fire> fires = new ArrayList<>();
            List<Fault> faults = new ArrayList<>();

            try {
                // Split by section
                String[] sections = data.split("];");

                for (String section : sections) {
                    if (section.startsWith("DRONES=[")) {
                        String body = section.substring("DRONES=[".length());
                        if (!body.trim().isEmpty()) {
                            String[] entries = body.split(";");
                            for (String entry : entries) {
                                if (entry.isEmpty()) continue;
                                String[] values = entry.replace("(", "").replace(")", "").split(",");
                                int id = Integer.parseInt(values[0].trim());
                                int x = Integer.parseInt(values[1].trim());
                                int y = Integer.parseInt(values[2].trim());
                                String fireStatus = values.length > 3 ? values[3].trim() : "";
                                drones.add(new DroneStatus(id, x, y, fireStatus));
                            }
                        }
                    } else if (section.startsWith("FIRES=[")) {
                        String body = section.substring("FIRES=[".length());
                        if (!body.trim().isEmpty()) {
                            String[] entries = body.split(";");
                            for (String entry : entries) {
                                if (entry.isEmpty()) continue;
                                String[] values = entry.replace("(", "").replace(")", "").split(",");
                                String id = values[0].trim();
                                String severity = values[1].trim();
                                int zoneId = Integer.parseInt(values[2].trim());
                                fires.add(new Fire(id, severity, zoneId));
                            }
                        }
                    } else if (section.startsWith("FAULTS=[")) {
                        String body = section.substring("FAULTS=[".length());
                        if (!body.trim().isEmpty()) {
                            String[] entries = body.split(";");
                            for (String entry : entries) {
                                if (entry.isEmpty()) continue;
                                String[] values = entry.replace("(", "").replace(")", "").split(",");
                                int droneId = Integer.parseInt(values[0].trim());
                                String message = values[1].trim();
                                faults.add(new Fault(droneId, message));
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Error parsing UIUpdate string: " + e.getMessage());
                return null;
            }

            return new UIUpdate(drones, fires, faults);
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