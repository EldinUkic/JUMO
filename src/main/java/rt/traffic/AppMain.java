package rt.traffic;

import java.util.Scanner;

import javax.swing.SwingUtilities;

import rt.traffic.backend.Sim;
import rt.traffic.backend.traciServices.TrafficLights.TrafficLightServices;
import rt.traffic.backend.traciServices.Vehicle.CarInjectionService;
import rt.traffic.backend.traciServices.Vehicle.StressTestServices;
import rt.traffic.backend.traciServices.Vehicle.VehicleServices;
import rt.traffic.config.SumoPath;
import rt.traffic.ui.MainWindow;

public class AppMain {

    public static void main(String[] args) {

        /*
         * 1) Karte auswählen
         */
        SumoPath.useMap("DEFAULT");

        /*
         * 2) Simulation erstellen
         */
        boolean useSumoGui = false; // echtes SUMO-GUI (nicht Swing)
        String cfgPath = SumoPath.getCfgPath();

        Sim sim = new Sim(cfgPath, useSumoGui);

        /*
         * 3) Swing-GUI starten
         */
        SwingUtilities.invokeLater(() -> {
            MainWindow w = new MainWindow(sim);
            w.setLocationRelativeTo(null);
            w.setVisible(true);
        });

        /*
         * 4) Backend / Simulation starten
         */
        sim.run();

        /*
         * 5) Konsolensteuerung starten
         */
        runConsoleMenu(sim);
    }

    /*
     * Zentrales Konsolenmenü.
     * Dient nur zur Steuerung / Debug / Tests.
     */
    private static void runConsoleMenu(Sim sim) {

        Scanner scanner = new Scanner(System.in);

        System.out.println("=== Traffic Simulation Control ===");
        System.out.println("1  = Play");
        System.out.println("2  = Pause");
        System.out.println("3  = Restart Simulation");
        System.out.println("4  = Exit");
        System.out.println("5  = Spawn Menu (Console)");
        System.out.println("6  = Show Live Vehicles (Console)");
        System.out.println("7  = Show Traffic Lights (Console)");
        System.out.println("8  = Stress: Set total vehicles");
        System.out.println("9  = Stress: Toggle ON/OFF");
        System.out.println("s  = Step Once");
        System.out.println("----------------------------------");

        boolean running = true;

        while (running) {
            System.out.print("Eingabe: ");
            String input = scanner.nextLine().trim();

            try {
                switch (input) {

                    case "1" -> sim.play();

                    case "2" -> sim.pause();

                    case "3" -> sim.restart();

                    case "4" -> {
                        sim.shutdown();
                        running = false;
                    }

                    /*
                     * Spawning über die Injection-Logik
                     */
                    case "5" -> CarInjectionService.openSpawnMenu(scanner);

                    /*
                     * Live-Fahrzeuge aus dem letzten Snapshot anzeigen
                     */
                    case "6" -> VehicleServices.printAllVehicles();

                    /*
                     * Traffic Lights auslesen und anzeigen
                     */
                    case "7" -> {
                        TrafficLightServices.trafficLightPull();
                        TrafficLightServices.printAllTrafficLights();
                    }

                    /*
                     * Stress-Test konfigurieren
                     */
                    case "8" -> {
                        System.out.print("Total vehicles: ");
                        int n = Integer.parseInt(scanner.nextLine().trim());
                        StressTestServices.configureStressTest(n);
                    }

                    /*
                     * Stress-Test an/aus
                     */
                    case "9" -> StressTestServices.toggleStressTest();

                    /*
                     * Einzelnen Sim-Step ausführen
                     */
                    case "s", "S" -> sim.stepOnce();

                    default -> System.out.println("Ungültige Eingabe.");
                }

            } catch (Exception e) {
                System.out.println("[MENU] Fehler: " + e.getMessage());
            }
        }

        scanner.close();
    }
}
