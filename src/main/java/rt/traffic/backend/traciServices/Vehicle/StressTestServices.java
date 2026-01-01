package rt.traffic.backend.traciServices.Vehicle;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/*
 * StressTestServices
 *
 * Dauer-Stress-Test:
 * - queued regelmäßig Fahrzeuge über Zeit
 * - läuft dauerhaft solange enabled=true
 *
 * Wichtig:
 * - tickStressTest() MUSS pro Sim-Step aufgerufen werden
 * - echtes Spawnen macht CarInjectionService.applySpawn()
 */
public final class StressTestServices {

    // an/aus Schalter
    private static boolean enabled = false;

    // wie viele Fahrzeuge PRO Intervall
    private static int totalVehicles = 10;

    // alle wie viele Sim-Ticks gespawnt wird
    private static final int INTERVAL_TICKS = 30;

    // interner Tick-Zähler
    private static int tickCounter = 0;

    private StressTestServices() {
    }

    /*
     * Setzt die Anzahl der Fahrzeuge PRO Intervall.
     * (Name bleibt aus Kompatibilitätsgründen gleich)
     */
    public static void configureStressTest(int total) {
        totalVehicles = Math.max(1, total);
        System.out.println("[STRESS] vehiclesPerInterval=" + totalVehicles);
    }

    /*
     * Schaltet den Stress-Test an/aus.
     */
    public static void toggleStressTest() {
        enabled = !enabled;
        tickCounter = 0;

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
     * MUSS pro Sim-Step aufgerufen werden.
     */
    public static void tickStressTest() {
        applyStressTest();
    }

    /*
     * Wird pro Sim-Step aufgerufen.
     * Spawnt alle INTERVAL_TICKS einen Burst.
     */
    public static void applyStressTest() {

        if (!enabled)
            return;

        tickCounter++;

        // noch nicht Zeit
        if (tickCounter < INTERVAL_TICKS)
            return;

        // Zeit erreicht → Burst
        tickCounter = 0;

        List<RoutePreloader.RouteInfo> routes = RoutePreloader.loadRoutes();
        if (routes.isEmpty()) {
            System.out.println("[STRESS] No routes found.");
            return;
        }

        String typeId = getDefaultTypeId();
        if (typeId.isBlank()) {
            System.out.println("[STRESS] No vehicle type found.");
            return;
        }

        for (int i = 0; i < totalVehicles; i++) {
            int idx = ThreadLocalRandom.current().nextInt(routes.size());
            String routeId = routes.get(idx).routeId;

            // nur queue, kein direktes Spawn
            CarInjectionService.requestSpawn(routeId, typeId, 1);
        }

        System.out.println("[STRESS] Queued " + totalVehicles
                + " vehicles across " + routes.size() + " routes.");
    }

    /*
     * Default Type für StressTest.
     */
    private static String getDefaultTypeId() {
        return "veh_passenger";
    }
}
