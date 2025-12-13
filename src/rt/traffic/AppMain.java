package rt.traffic;

import java.util.Scanner;

import javax.swing.SwingUtilities;

import rt.traffic.backend.Sim;
import rt.traffic.backend.traciServices.TrafficLightServices;
import rt.traffic.backend.traciServices.VehicleServices;
import rt.traffic.ui.MainWindow;

// Analytics-Imports
import rt.traffic.application.analytics.AnalyticsExecution;
import rt.traffic.application.analytics.Metrics;
import rt.traffic.application.analytics.TrafficTracking;
import rt.traffic.application.analytics.VehicleTracking;

// Für aktuelle Simulationszeit
import org.eclipse.sumo.libtraci.Simulation;
import org.eclipse.sumo.libtraci.Lane;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

public class AppMain {

    public static void main(String[] args) {

        String cfgPath = "src/traffic/infrastructure/sumo/osm.sumocfg";
        boolean useGui = true;

        Sim sim = new Sim(cfgPath, useGui);

        AnalyticsExecution analytics = new AnalyticsExecution();

        SwingUtilities.invokeLater(() -> {
            MainWindow w = new MainWindow(sim);
            w.setLocationRelativeTo(null);
            w.setVisible(true);
        });

        // Backend starten (startet SUMO + TraCI)
        sim.run();

        Scanner scanner = new Scanner(System.in);

        System.out.println("=== Traffic Simulation Control ===");
        System.out.println("1  = Play (Auto-Loop starten)");
        System.out.println("2  = Pause (Auto-Loop stoppen)");
        System.out.println("3  = Restart Simulation (neu laden)");
        System.out.println("4  = Exit (SUMO schließen)");
        System.out.println("5  = Spawn-Menu (Fahrzeuge erzeugen)");
        System.out.println("6  = Show Live Vehicles");
        System.out.println("7  = Show Analytics (Konsole)");
        System.out.println("8  = Show Traffic Lights (IDs/Snapshot)");
        System.out.println("9  = Configure Traffic Rule");
        System.out.println("10 = Toggle Traffic Rule ON/OFF");
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
                        System.out.println("[Analytics] Fehler beim Erzeugen der Tracking-Daten:");
                        e.printStackTrace();
                    }
                    break;

                case "8":
                    // Snapshot ziehen + ausgeben
                    TrafficLightServices.trafficLightPull();
                    TrafficLightServices.printAllTrafficLights();
                    break;

                case "9":
                    System.out.print("TrafficLight ID: ");
                    String tlId = scanner.nextLine().trim();

                    System.out.print("Edge ID: ");
                    String edgeId = scanner.nextLine().trim();

                    System.out.print("Threshold (vehicles on edge): ");
                    int threshold;
                    try {
                        threshold = Integer.parseInt(scanner.nextLine().trim());
                    } catch (NumberFormatException e) {
                        System.out.println("Ungültige Zahl.");
                        break;
                    }

                    sim.configureRule(tlId, edgeId, threshold);
                    break;

                case "10":
                    sim.toggleRule();
                    break;

                default:
                    System.out.println("Ungültig. Bitte 1–10 eingeben.");
                    break;
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
            if (edgeId == null || edgeId.isEmpty() || edgeLengthInMeters.containsKey(edgeId)) continue;

            double lengthMeters = 100.0;
            try {
                lengthMeters = Lane.getLength(edgeId + "_0");
            } catch (Exception e) {
                System.err.println("[Analytics] Konnte Länge für Edge '" + edgeId
                        + "' nicht aus SUMO holen, benutze Fallback 100m.");
            }

            edgeLengthInMeters.put(edgeId, lengthMeters);
        }

        return new TrafficTracking(simTimeSeconds, vehicles, edgeLengthInMeters);
    }

    private static void printMetricsToConsole(Metrics m) {
        System.out.println("\n=== Analytics / Metrics ===");

        System.out.printf("Average speed: %.2f m/s (%.2f km/h)%n",
                m.avgSpeedPerSecond, m.getAverageSpeedKmh());
        System.out.println("Vehicle count:      " + m.vehicleCount);
        System.out.println("Stopped vehicles:   " + m.stoppedVehicleCount);
        System.out.printf("Stopped ratio:      %.2f%%%n", m.getStoppedRatio() * 100.0);

        System.out.println("\n=== Per-Edge metrics ===");
        if (m.vehiclesPerEdge == null || m.vehiclesPerEdge.isEmpty()) {
            System.out.println("(Keine per-Edge Daten vorhanden)");
        } else {
            for (Map.Entry<String, Integer> entry : m.vehiclesPerEdge.entrySet()) {
                String edgeId = entry.getKey();
                int vehiclesOnEdge = entry.getValue();

                int stoppedOnEdge = 0;
                if (m.stoppedVehiclesPerEdge != null) {
                    stoppedOnEdge = m.stoppedVehiclesPerEdge.getOrDefault(edgeId, 0);
                }

                double density = m.getDensityForEdge(edgeId);

                System.out.printf(
                        "Edge=%s | vehicles=%d | stopped=%d | density=%.3f veh/km%n",
                        edgeId, vehiclesOnEdge, stoppedOnEdge, density
                );
            }
        }

        System.out.println("\n=== Congested edges (>= 60% stopped & min vehicles) ===");
        List<String> congestedEdges = m.getCongestedEdges();
        if (congestedEdges.isEmpty()) {
            System.out.println("(Keine staugefährdeten Kanten nach 60%-Regel)");
        } else {
            for (String edgeId : congestedEdges) {
                System.out.println("Congested edge: " + edgeId);
            }
        }

        System.out.println("=== Ende Metrics ===\n");
    }
}
