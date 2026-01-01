package rt.traffic.backend.traciServices.Vehicle;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/*
 * StressTestServices
 *
 * Lasttest-Helfer:
 * - legt eine große Menge Spawn-Requests in kurzer Zeit ab
 * - verteilt diese zufällig auf alle vorhandenen Routen
 *
 * Wichtig:
 * - es wird nur "gequeued"
 * - das echte Spawnen macht CarInjectionService.applySpawn() im Sim-Step
 * - der Test läuft absichtlich nur einmal pro Aktivierung
 */
public final class StressTestServices {

    // an/aus Schalter
    private static boolean enabled = false;

    // damit es nicht bei jedem Tick erneut feuert
    private static boolean executedOnce = false;

    // wie viele Fahrzeuge insgesamt gequeued werden sollen
    private static int totalVehicles = 1000;

    private StressTestServices() {
    }

    /*
     * Setzt die Anzahl der Fahrzeuge für den Stress-Test.
     */
    public static void configureStressTest(int total) {
        totalVehicles = Math.max(1, total);
        System.out.println("[STRESS] totalVehicles=" + totalVehicles);
    }

    /*
     * Schaltet den Stress-Test an/aus.
     * Beim Einschalten wird executedOnce zurückgesetzt.
     */
    public static void toggleStressTest() {
        enabled = !enabled;

        if (enabled) {
            executedOnce = false;
        }

        System.out.println("[STRESS] enabled=" + enabled);
    }

    /*
     * Nur Status-Getter (z.B. GUI).
     */
    public static boolean isEnabled() {
        return enabled;
    }

    /*
     * Name bleibt so, falls es irgendwo schon benutzt wird.
     */
    public static void tickStressTest() {
        applyStressTest();
    }

    /*
     * Wird pro Sim-Step aufgerufen.
     * Wenn enabled=true und noch nicht ausgeführt -> queue die Fahrzeuge.
     */
    public static void applyStressTest() {

        if (!enabled)
            return;
        if (executedOnce)
            return;

        List<RoutePreloader.RouteInfo> routes = RoutePreloader.loadRoutes();
        if (routes.isEmpty()) {
            System.out.println("[STRESS] No routes found.");
            executedOnce = true;
            return;
        }

        // VehicleType kommt jetzt aus CarInjection (VehicleServices hat das nicht mehr)
        String typeId = getDefaultTypeId();
        if (typeId.isBlank()) {
            System.out.println("[STRESS] No vehicle type found.");
            executedOnce = true;
            return;
        }

        for (int i = 0; i < totalVehicles; i++) {
            int idx = ThreadLocalRandom.current().nextInt(routes.size());
            String routeId = routes.get(idx).routeId;

            CarInjectionService.requestSpawn(routeId, typeId, 1);
        }

        System.out.println("[STRESS] Queued " + totalVehicles
                + " vehicles across " + routes.size() + " routes.");

        executedOnce = true;
    }

    /*
     * Default Type für StressTest.
     * Muss zu deinen VehicleTypes passen.
     */
    private static String getDefaultTypeId() {
        return "veh_passenger";
    }
}
