package rt.traffic.backend.traciServices;

import org.eclipse.sumo.libtraci.Vehicle;   // SUMO Fahrzeug-API
import org.eclipse.sumo.libtraci.TraCIPosition;
import org.eclipse.sumo.libtraci.Route;
import org.eclipse.sumo.libtraci.StringVector;
import org.eclipse.sumo.libtraci.VehicleType;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class VehicleServices {

    // =========================
    // LIVE-FAHRZEUGLISTE
    // =========================

    private static List<VehicleServices> VehicleList = new ArrayList<>();

    // Vehicle-Daten (Snapshot pro Step)
    public final String id;
    public final String edgeId;
    public final String routeId;
    public final String typeId;
    public final double speed;
    public final double px;
    public final double py;

    public VehicleServices(String id,
                           String edgeId,
                           String routeId,
                           String typeId,
                           double speed,
                           double px,
                           double py) {
        this.id = id;
        this.edgeId = edgeId;
        this.routeId = routeId;
        this.typeId = typeId;
        this.speed = speed;
        this.px = px;
        this.py = py;
    }

    // Pull Vehicle, pro Sim-Step vom Backend aufgerufen
    public static void vehiclePull() {
        StringVector idVec = Vehicle.getIDList();

        List<VehicleServices> result = new ArrayList<>();

        for (int i = 0; i < idVec.size(); i++) {
            String id = idVec.get(i);
            String edgeId = Vehicle.getRoadID(id);
            String routeId = Vehicle.getRouteID(id);
            String typeId = Vehicle.getTypeID(id);
            double speed = Vehicle.getSpeed(id);
            TraCIPosition pos = Vehicle.getPosition(id);
            double px = pos.getX();
            double py = pos.getY();

            result.add(new VehicleServices(id, edgeId, routeId, typeId, speed, px, py));
        }
        VehicleList = result;
    }

    // === Zugriffs-Methoden ===

    public static List<VehicleServices> getVehicleList() {
        return VehicleList;
    }

    public static VehicleServices getVehicleById(String vehicleId) {
        for (VehicleServices v : VehicleList) {
            if (v.id.equals(vehicleId)) {
                return v;
            }
        }
        return null;
    }

    public static int getVehicleSum() {
        return VehicleList.size();
    }

    public static List<VehicleServices> getVehicleOnEdge(String edgeId) {
        List<VehicleServices> result = new ArrayList<>();
        for (VehicleServices v : VehicleList) {
            if (edgeId.equals(v.edgeId)) {
                result.add(v);
            }
        }
        return result;
    }

    public static double getAverageSpeed() {
        if (VehicleList.isEmpty()) return 0.0;
        double sum = 0.0;
        for (VehicleServices v : VehicleList) {
            sum += v.speed;
        }
        return sum / VehicleList.size();
    }

    public static void printAllVehicles() {
        System.out.println("=== LIVE-FAHRZEUGE ===");
        for (VehicleServices v : VehicleList) {
            System.out.println(
                    "ID=" + v.id +
                    ", Edge=" + v.edgeId +
                    ", Route=" + v.routeId +
                    ", Type=" + v.typeId +
                    ", Speed=" + v.speed +
                    ", Pos=(" + v.px + ", " + v.py + ")"
            );
        }
    }

    // ---------------------------------------------------------------------------
    // SPAWN / INJECTION
    // ---------------------------------------------------------------------------

    public static class SpawnRequest {
        public final String vehicleId;
        public final String routeId;
        public final String typeId;
        public final double departTime; // -1 = sofort

        public SpawnRequest(String vehicleId, String routeId, String typeId, double departTime) {
            this.vehicleId = vehicleId;
            this.routeId = routeId;
            this.typeId = typeId;
            this.departTime = departTime;
        }
    }

    private static final List<SpawnRequest> spawnQueue = new ArrayList<>();

    private static double spawnRateFactor = 1.0;

    public static void queueSpawn(SpawnRequest request) {
        spawnQueue.add(request);
        System.out.println("[QUEUE] " + request.vehicleId +
                " (Route=" + request.routeId +
                ", Type=" + request.typeId +
                ") QueueSize=" + spawnQueue.size());
    }

    public static void setSpawnRateFactor(double factor) {
        if (factor < 0.1) factor = 0.1;
        if (factor > 10.0) factor = 10.0;
        spawnRateFactor = factor;
        System.out.println("[SPAWN-RATE] Faktor gesetzt auf " + spawnRateFactor);
    }

    public static double getSpawnRateFactor() {
        return spawnRateFactor;
    }

    // Wird vom Sim-Loop nach jedem Simulation.step() aufgerufen
    public static void applySpawns(double currentSimTime) {
        if (spawnQueue.isEmpty()) {
            return;
        }

        System.out.println("[APPLY] currentSimTime=" + currentSimTime +
                " QueueSize=" + spawnQueue.size());

        List<SpawnRequest> remaining = new ArrayList<>();

        for (SpawnRequest req : spawnQueue) {
            boolean due =
                    (req.departTime < 0) ||
                    (req.departTime <= currentSimTime);

            if (due && shouldSpawnNow()) {
                spawnNow(req);
            } else {
                remaining.add(req);
            }
        }

        spawnQueue.clear();
        spawnQueue.addAll(remaining);
    }

    private static boolean shouldSpawnNow() {
        if (spawnRateFactor >= 1.0) {
            return true;
        } else {
            return Math.random() < spawnRateFactor;
        }
    }
private static void spawnNow(SpawnRequest req) {
    System.out.println("[SPAWN NOW] vehId=" + req.vehicleId +
            " routeId=" + req.routeId +
            " typeId=" + req.typeId +
            " departTime=" + req.departTime);

    // depart vorbereiten: -1 -> "now", sonst Zeit als Zahl
    String depart;
    if (req.departTime < 0) {
        depart = "now";
    } else {
        depart = Double.toString(req.departTime);
    }

    // Gültige Defaults für SUMO
    String departLane  = "free";         // freie Spur wählen
    String departPos   = "random_free";  // zufällige freie Position
    String departSpeed = "max";          // max. erlaubte Geschwindigkeit
    String arrivalLane = "current";      // aktuelle Lane beibehalten

    Vehicle.add(
            req.vehicleId,   // vehID
            req.routeId,     // routeID
            req.typeId,      // typeID
            depart,          // depart
            departLane,      // departLane
            departPos,       // departPos
            departSpeed,     // departSpeed
            arrivalLane      // arrivalLane
    );
}


    // ---------------------------------------------------------------------------
    // ROUTE- UND TYP-HILFSMETHODEN
    // ---------------------------------------------------------------------------

    public static List<String> getAllRouteIds() {
        StringVector vec = Route.getIDList();
        List<String> routes = new ArrayList<>();

        for (int i = 0; i < vec.size(); i++) {
            routes.add(vec.get(i));
        }
        return routes;
    }

    public static void printAllRouteIds() {
        List<String> routes = getAllRouteIds();
        System.out.println("Verfügbare Routen:");
        for (int i = 0; i < routes.size(); i++) {
            System.out.println("[" + i + "] " + routes.get(i));
        }
    }

    // Einen gültigen VehicleType aus SUMO holen (z.B. den ersten)
    public static String getAnyVehicleTypeId() {
        StringVector vec = VehicleType.getIDList();
        if (vec.isEmpty()) {
            System.out.println("[WARN] Keine VehicleTypes in SUMO gefunden.");
            return "";
        }
        String typeId = vec.get(0);
        System.out.println("[INFO] Verwende VehicleType: " + typeId);
        return typeId;
    }

    public static void printAllVehicleTypeIds() {
        StringVector vec = VehicleType.getIDList();
        System.out.println("Verfügbare VehicleTypes:");
        for (int i = 0; i < vec.size(); i++) {
            System.out.println("[" + i + "] " + vec.get(i));
        }
    }

    // ---------------------------------------------------------------------------
    // Terminal-Menü für manuelles Spawnen
    // ---------------------------------------------------------------------------

    public static void openSpawnMenu(Scanner scanner) {
        System.out.println("=== SPAWN-MENÜ ===");

        List<String> routes = getAllRouteIds();
        if (routes.isEmpty()) {
            System.out.println("Keine Routen gefunden. Hast du eine .rou geladen?");
            return;
        }

        System.out.println("Verfügbare Routen:");
        for (int i = 0; i < routes.size(); i++) {
            System.out.println("[" + i + "] " + routes.get(i));
        }

        System.out.print("Route-Index eingeben: ");
        String inputRoute = scanner.nextLine();
        int routeIndex;
        try {
            routeIndex = Integer.parseInt(inputRoute);
        } catch (NumberFormatException e) {
            System.out.println("Ungültige Eingabe.");
            return;
        }

        if (routeIndex < 0 || routeIndex >= routes.size()) {
            System.out.println("Index außerhalb des Bereichs.");
            return;
        }

        String routeId = routes.get(routeIndex);
        System.out.println("Gewählte Route: " + routeId);

        System.out.print("Spawnrate-Faktor (z.B. 1.0, 2.0, 0.5): ");
        String inputRate = scanner.nextLine();
        try {
            double factor = Double.parseDouble(inputRate);
            VehicleServices.setSpawnRateFactor(factor);
        } catch (NumberFormatException e) {
            System.out.println("Ungültige Zahl, Spawnrate bleibt: "
                    + VehicleServices.getSpawnRateFactor());
        }

        System.out.print("Wie viele Fahrzeuge spawnen (Queue)? ");
        String inputCount = scanner.nextLine();
        int count;
        try {
            count = Integer.parseInt(inputCount);
        } catch (NumberFormatException e) {
            System.out.println("Ungültige Zahl, breche ab.");
            return;
        }

        // NEU: echten VehicleType aus SUMO holen statt "car"
        String typeId = getAnyVehicleTypeId();
        if (typeId.isEmpty()) {
            System.out.println("Kein gültiger VehicleType gefunden, breche Spawn ab.");
            return;
        }

        for (int i = 0; i < count; i++) {
            String vehId = "cli_" + routeId + "_" + System.currentTimeMillis() + "_" + i;
            SpawnRequest req = new SpawnRequest(vehId, routeId, typeId, -1);
            VehicleServices.queueSpawn(req);
        }

        System.out.println("Es wurden " + count + " Fahrzeuge in die Spawn-Queue gelegt.");
        System.out.println("Sie werden beim nächsten applySpawns() verarbeitet.");
    }
}
