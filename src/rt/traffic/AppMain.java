package rt.traffic;

import java.util.Scanner;
import javax.swing.SwingUtilities;

import rt.traffic.backend.Sim;
import rt.traffic.backend.traciServices.TrafficLightServices;
import rt.traffic.backend.traciServices.VehicleServices;
import rt.traffic.ui.MainWindow;

// Analytics
import rt.traffic.application.analytics.AnalyticsExecution;
import rt.traffic.application.analytics.Metrics;
import rt.traffic.application.analytics.TrafficTracking;
import rt.traffic.application.analytics.VehicleTracking;

// SUMO
import org.eclipse.sumo.libtraci.Simulation;
import org.eclipse.sumo.libtraci.Lane;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

/**
 * AppMain
 *
 * Einstiegspunkt der Anwendung.
 *
 * Aufgaben:
 * - Startet Simulation + Backend
 * - Startet Swing-GUI
 * - Stellt ein Konsolen-Menü für Debug / Steuerung bereit
 * - Führt Analytics-Auswertung aus
 */
public class AppMain {

    public static void main(String[] args) {

        // =========================
        // SIMULATION SETUP
        // =========================

        String cfgPath = "src/traffic/infrastructure/sumo/osm.sumocfg";
        boolean useGui = false; // true = sumo-gui, false = headless

        Sim sim = new Sim(cfgPath, useGui);
        AnalyticsExecution analytics = new AnalyticsExecution();

        // =========================
        // GUI STARTEN
        // =========================

        SwingUtilities.invokeLater(() -> {
            MainWindow w = new MainWindow(sim);
            w.setLocationRelativeTo(null);
            w.setVisible(true);
        });

        // =========================
        // BACKEND STARTEN
        // =========================

        sim.run();

        Scanner scanner = new Scanner(System.in);

        // =========================
        // KONSOLE-MENÜ
        // =========================

        System.out.println("=== Traffic Simulation Control ===");
        System.out.println("1  = Play (Auto-Loop starten)");
        System.out.println("2  = Pause (Auto-Loop stoppen)");
        System.out.println("3  = Restart Simulation");
        System.out.println("4  = Exit");
        System.out.println("5  = Spawn-Menu");
        System.out.println("6  = Show Live Vehicles");
        System.out.println("7  = Show Analytics");
        System.out.println("8  = Show Traffic Lights");
        System.out.println("9  = Configure Traffic Rule");
        System.out.println("10 = Toggle Traffic Rule");
        System.out.println("11 = Configure Stress Test");
        System.out.println("12 = Toggle Stress Test");
        System.out.println("-----------------------------------");

        boolean running = true;

        while (running) {
            System.out.print("Eingabe: ");
            String input = scanner.nextLine().trim();

            switch (input) {

                case "1":
                    sim.play();
                    break;

                case "2":
                    sim.pause();
                    break;

                case "3":
                    sim.restart();
                    break;

                case "4":
                    System.out.println("Beende Programm...");
                    sim.shutdown();
                    running = false;
                    break;

                case "5":
                    VehicleServices.openSpawnMenu(scanner);
                    break;

                case "6":
                    VehicleServices.printAllVehicles();
                    break;

                case "7":
                    try {
                        TrafficTracking tracking = buildTrafficTrackingFromBackend();
                        Metrics metrics = analytics.executeMetrics(tracking);
                        printMetricsToConsole(metrics);
                        metrics.exportToPdf();
                        metrics.exportToCsv();
                    } catch (Exception e) {
                        System.out.println("[Analytics] Fehler:");
                        e.printStackTrace();
                    }
                    break;

                case "8":
                    TrafficLightServices.trafficLightPull();
                    TrafficLightServices.printAllTrafficLights();
                    break;

                case "9": {
                    System.out.print("TrafficLight ID: ");
                    String tlId = scanner.nextLine().trim();

                    System.out.print("Edge ID: ");
                    String edgeId = scanner.nextLine().trim();

                    System.out.print("Threshold: ");
                    try {
                        int threshold = Integer.parseInt(scanner.nextLine().trim());
                        sim.configureRule(tlId, edgeId, threshold);
                    } catch (NumberFormatException e) {
                        System.out.println("Ungültige Zahl.");
                    }
                    break;
                }

                case "10":
                    sim.toggleRule();
                    break;

                case "11":
                    System.out.print("Vehicles per route: ");
                    try {
                        int vpr = Integer.parseInt(scanner.nextLine().trim());
                        sim.configureStressTest(vpr);
                    } catch (NumberFormatException e) {
                        System.out.println("Ungültige Zahl.");
                    }
                    break;

                case "12":
                    sim.toggleStressTest();
                    break;

                default:
                    System.out.println("Ungültig. Bitte 1–12 eingeben.");
            }
        }

        scanner.close();
    }

    // ==========================================================
    // TrafficTracking aus laufender Simulation bauen
    // ==========================================================
    private static TrafficTracking buildTrafficTrackingFromBackend() {

        double simTimeSeconds = 0.0;
        try {
            simTimeSeconds = Simulation.getTime();
        } catch (Throwable ignored) {}

        List<VehicleTracking> vehicles = new ArrayList<>();
        for (VehicleServices v : VehicleServices.getVehicleList()) {
            vehicles.add(new VehicleTracking(v.id, v.edgeId, v.speed));
        }

        Map<String, Double> edgeLengthInMeters = new HashMap<>();

        for (VehicleTracking v : vehicles) {
            String edgeId = v.edgeId;
            if (edgeId == null || edgeId.isEmpty() || edgeLengthInMeters.containsKey(edgeId))
                continue;

            double lengthMeters = 100.0;
            try {
                lengthMeters = Lane.getLength(edgeId + "_0");
            } catch (Exception e) {
                System.err.println("[Analytics] Fallback length for edge " + edgeId);
            }

            edgeLengthInMeters.put(edgeId, lengthMeters);
        }

        return new TrafficTracking(simTimeSeconds, vehicles, edgeLengthInMeters);
    }

    // ==========================================================
    // Metrics im Terminal ausgeben
    // ==========================================================
    private static void printMetricsToConsole(Metrics m) {

        System.out.println("\n=== Analytics / Metrics ===");

        System.out.printf("Average speed: %.2f m/s (%.2f km/h)%n",
                m.avgSpeedPerSecond, m.getAverageSpeedKmh());

        System.out.println("Vehicle count:    " + m.vehicleCount);
        System.out.println("Stopped vehicles: " + m.stoppedVehicleCount);
        System.out.printf("Stopped ratio:    %.2f%%%n", m.getStoppedRatio() * 100.0);

        System.out.println("\n=== Per-Edge metrics ===");
        if (m.vehiclesPerEdge == null || m.vehiclesPerEdge.isEmpty()) {
            System.out.println("(Keine Daten)");
        } else {
            for (Map.Entry<String, Integer> e : m.vehiclesPerEdge.entrySet()) {
                String edgeId = e.getKey();
                int vehiclesOnEdge = e.getValue();
                int stopped = m.stoppedVehiclesPerEdge.getOrDefault(edgeId, 0);
                double density = m.getDensityForEdge(edgeId);

                System.out.printf(
                        "Edge=%s | vehicles=%d | stopped=%d | density=%.3f veh/km%n",
                        edgeId, vehiclesOnEdge, stopped, density
                );
            }
        }

        System.out.println("=== Ende Metrics ===\n");
    }
}
