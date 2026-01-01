package rt.traffic.backend.traciServices.Vehicle;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ThreadLocalRandom;

import org.eclipse.sumo.libtraci.Route;
import org.eclipse.sumo.libtraci.Simulation;
import org.eclipse.sumo.libtraci.StringVector;
import org.eclipse.sumo.libtraci.Vehicle;

/*
 * CarInjectionService
 *
 * Diese Klasse ist der einzige Ort, der Fahrzeuge in SUMO "reinspawnt".
 *
 * Idee:
 * - GUI / CLI ruft nur requestSpawn(...) auf (legt Wunsch ab)
 * - applySpawn() wird pro Sim-Step aufgerufen (arbeitet Wünsche ab)
 *
 * Damit bleibt die Simulation stabil, auch wenn viele Fahrzeuge angefragt werden.
 */
public final class CarInjectionService {

    /*
     * Interner Auftrag in der Queue.
     * routeId: wohin
     * typeId : welcher Fahrzeugtyp
     * remaining: wie viele fehlen noch
     */
    private static class SpawnRequest {
        String routeId;
        String typeId;
        int remaining;

        SpawnRequest(String routeId, String typeId, int count) {
            this.routeId = routeId;
            this.typeId = typeId;
            this.remaining = count;
        }
    }

    /*
     * Queue für Spawn-Aufträge (FIFO).
     * requestSpawn legt rein, applySpawn arbeitet ab.
     */
    private static final Deque<SpawnRequest> queue = new ArrayDeque<>();

    /*
     * Damit die Routen nur einmal in SUMO registriert werden.
     */
    private static boolean routesRegistered = false;

    // Utility-Klasse
    private CarInjectionService() {
    }

    /*
     * Standard-Spawn: nutzt default VehicleType.
     * (muss zu deinen VehicleTypes passen)
     */
    public static void requestSpawn(String routeId, int count) {
        requestSpawn(routeId, "veh_passenger", count);
    }

    /*
     * Legt einen Spawn-Wunsch ab.
     * Wichtig: Hier passiert noch kein Vehicle.add().
     */
    public static void requestSpawn(String routeId, String typeId, int count) {

        // Basic Checks (damit wir keinen Müll in die Queue legen)
        if (routeId == null || routeId.isBlank())
            return;
        if (typeId == null || typeId.isBlank())
            return;
        if (count <= 0)
            return;

        queue.addLast(new SpawnRequest(routeId, typeId, count));
    }

    /*
     * Wird pro Sim-Step aufgerufen.
     *
     * Was passiert hier:
     * - registriert Routen einmalig in SUMO (falls noch nicht passiert)
     * - spawnt pro Step maximal maxPerStep Fahrzeuge
     * - arbeitet die Queue Stück für Stück ab
     */
    public static void applySpawn() {

        registerRoutesOnce();

        // Limit pro Step, damit SUMO nicht einfriert
        int maxPerStep = 50;
        int spawnedThisStep = 0;

        // aktuelle Sim-Zeit als Basis
        double now = Simulation.getTime();

        // Solange wir noch Luft haben und noch Aufträge da sind
        while (spawnedThisStep < maxPerStep && !queue.isEmpty()) {

            SpawnRequest request = queue.peekFirst();

            // 1 Fahrzeug spawnen
            spawnOneVehicle(request, now);

            // Auftrag runterzählen
            request.remaining--;
            spawnedThisStep++;

            // Wenn Auftrag fertig ist, raus aus der Queue
            if (request.remaining <= 0) {
                queue.removeFirst();
            }
        }
    }

    // -------------------------------------------------------------------------
    // intern
    // -------------------------------------------------------------------------

    /*
     * Spawnt genau ein Fahrzeug anhand des Requests.
     * Der eigentliche Vehicle.add(...) Call ist nur hier, damit es übersichtlich
     * bleibt.
     */
    private static void spawnOneVehicle(SpawnRequest request, double now) {

        // Eindeutige ID (reicht für unsere Zwecke)
        String vehicleId = "inj_" + System.nanoTime();

        // SUMO erwartet depart als String
        String departTime = String.format(java.util.Locale.US, "%.2f", now);

        Vehicle.add(
                vehicleId,
                request.routeId,
                request.typeId,
                departTime,
                "best",
                "random",
                "max",
                "current");
    }

    /*
     * Lädt Routen aus dem RoutePreloader und registriert sie in SUMO.
     * Passiert genau einmal pro Programmstart.
     */
    private static void registerRoutesOnce() {

        if (routesRegistered)
            return;

        List<RoutePreloader.RouteInfo> routes = RoutePreloader.loadRoutes();

        for (RoutePreloader.RouteInfo route : routes) {
            StringVector edges = new StringVector();
            for (String edgeId : route.edges) {
                edges.add(edgeId);
            }
            Route.add(route.routeId, edges);
        }

        routesRegistered = true;
    }

    public static void openSpawnMenu(Scanner scanner) {

        System.out.println("=== SPAWN MENU ===");

        // 1) Routen holen
        List<RoutePreloader.RouteInfo> routes = RoutePreloader.loadRoutes();
        if (routes.isEmpty()) {
            System.out.println("Keine Routen gefunden. Prüfe deine rou.xml.");
            return;
        }

        // 2) VehicleType wählen (Default wie in requestSpawn ohne typeId)
        System.out.print("VehicleType (Enter = veh_passenger): ");
        String typeIdInput = scanner.nextLine().trim();
        String typeId = typeIdInput.isBlank() ? "veh_passenger" : typeIdInput;

        // 3) Routen anzeigen
        System.out.println("Routen:");
        for (int i = 0; i < routes.size(); i++) {
            RoutePreloader.RouteInfo r = routes.get(i);
            System.out.println("[" + i + "] " + r.displayName + " -> " + r.routeId);
        }
        System.out.println("[r] random Route");

        // 4) Route auswählen
        System.out.print("Route wählen (Index oder r): ");
        String inputRoute = scanner.nextLine().trim();

        boolean randomRoute = inputRoute.equalsIgnoreCase("r");
        int routeIndex = -1;

        if (!randomRoute) {
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
        }

        // 5) Anzahl abfragen
        System.out.print("Wie viele Fahrzeuge spawnen? ");
        String inputCount = scanner.nextLine().trim();

        int count;
        try {
            count = Integer.parseInt(inputCount);
        } catch (NumberFormatException e) {
            System.out.println("Ungültige Zahl.");
            return;
        }

        if (count <= 0) {
            System.out.println("Muss größer als 0 sein.");
            return;
        }

        // 6) Requests in die Queue legen
        if (!randomRoute) {
            String routeId = routes.get(routeIndex).routeId;

            // Wenn der User Enter gedrückt hat, nehmen wir den Default-Request
            if (typeId.equals("veh_passenger")) {
                requestSpawn(routeId, count);
            } else {
                requestSpawn(routeId, typeId, count);
            }

            System.out.println("In Queue gelegt: " + count + " Fahrzeuge auf " + routeId);

        } else {
            for (int i = 0; i < count; i++) {
                int idx = ThreadLocalRandom.current().nextInt(routes.size());
                String routeId = routes.get(idx).routeId;

                if (typeId.equals("veh_passenger")) {
                    requestSpawn(routeId, 1);
                } else {
                    requestSpawn(routeId, typeId, 1);
                }
            }

            System.out.println("In Queue gelegt: " + count + " Fahrzeuge zufällig auf " + routes.size() + " Routen");
        }

        System.out.println("Spawns passieren im Sim-Step über applySpawn().");
    }

}
