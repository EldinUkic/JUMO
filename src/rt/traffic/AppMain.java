package rt.traffic;

import java.util.Scanner;
// NEU:
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

import javax.swing.SwingUtilities;

import rt.traffic.backend.Sim;
import rt.traffic.backend.traciServices.VehicleServices;
import rt.traffic.ui.MainWindow;

// NEU: Analytics-Imports
import rt.traffic.application.analytics.AnalyticsExecution;
import rt.traffic.application.analytics.Metrics;
import rt.traffic.application.analytics.TrafficTracking;
import rt.traffic.application.analytics.VehicleTracking;

// NEU: für aktuelle Simulationszeit
import org.eclipse.sumo.libtraci.Simulation;

import org.eclipse.sumo.libtraci.Lane;

// MEIN TEIL IST PROVISORISCH ELDIN !!!!!!!

public class AppMain {

    public static void main(String[] args) {

        // Pfad zu deiner SUMO-Konfiguration (anpassen falls nötig)
        String cfgPath = "src/traffic/infrastructure/sumo/osm.sumocfg";
        boolean useGui = false; // sumo-gui benutzen

        // Backend + Simulation vorbereiten
        Sim sim = new Sim(cfgPath, useGui);

        // NEU: Analytics-Instanz
        AnalyticsExecution analytics = new AnalyticsExecution();

        // === Unsere eigene Swing-GUI (MainWindow) starten und Sim übergeben ===
        SwingUtilities.invokeLater(() -> {
            MainWindow w = new MainWindow(sim);
            w.setLocationRelativeTo(null); // zentrieren
            w.setVisible(true);
        });

        // SUMO-GUI und Simulation starten (blockiert im Main-Thread,
        // GUI läuft über den EDT parallel)
        sim.run();

        // Terminal-Steuerung (optional, kann man später auch entfernen)
        Scanner scanner = new Scanner(System.in);

        System.out.println("=== Traffic Simulation Control ===");
        System.out.println("1 = Play (Auto-Loop starten)");
        System.out.println("2 = Pause (Auto-Loop stoppen)");
        System.out.println("3 = Restart Simulation (neu laden)");
        System.out.println("4 = Exit (SUMO schließen)");
        System.out.println("5 = Spawn-Menu (Fahrzeuge erzeugen)");
        System.out.println("6 = Show Live Vehicles");
        // NEU:
        System.out.println("7 = Show Analytics (Konsole)");
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

                // NEU: Analytics-Ausgabe
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

                default:
                    System.out.println("Ungültig. Bitte 1–7 eingeben.");
            }
        }
        scanner.close();
    }

    // ==========================================================
    // NEU: TrafficTracking aus der laufenden Simulation bauen
    // ==========================================================
    private static TrafficTracking buildTrafficTrackingFromBackend() {
        // 1) Aktuelle Sim-Zeit
        double simTimeSeconds = Simulation.getTime();

        // 2) VehicleServices -> VehicleTracking umwandeln
        List<VehicleTracking> vehicles = new ArrayList<>();
        for (VehicleServices v : VehicleServices.getVehicleList()) {
            vehicles.add(new VehicleTracking(
                    v.id,
                    v.edgeId,
                    v.speed   // speed ist in m/s, passt zu speedMetersPerSecond
            ));
        }

        // 3) Kantenlängen-Map aufbauen (ECHTE SUMO-WERTE)
        Map<String, Double> edgeLengthInMeters = new HashMap<>();

        for (VehicleTracking v : vehicles) {
            String edgeId = v.edgeId;
            if (edgeId == null || edgeId.isEmpty() || edgeLengthInMeters.containsKey(edgeId)) {
                continue;
            }

            double lengthMeters = 100.0; // Fallback, falls irgendwas schiefgeht

            try {
                // In SUMO heißen die Lanes meistens edgeId_0, edgeId_1, ...
                // Alle Lanes einer Edge haben dieselbe Länge → eine reicht für die Edge.
                String laneId = edgeId + "_0";
                lengthMeters = Lane.getLength(laneId);
            } catch (Exception e) {
                System.err.println("[Analytics] Konnte Länge für Edge '" + edgeId
                        + "' nicht aus SUMO holen, benutze Fallback 100m.");
            }

            edgeLengthInMeters.put(edgeId, lengthMeters);
        }

        return new TrafficTracking(simTimeSeconds, vehicles, edgeLengthInMeters);
    }

    // ==========================================================
    // NEU: Ausgabe der Metrics im Terminal
    // ==========================================================
    private static void printMetricsToConsole(Metrics m) {
        System.out.println("\n=== Analytics / Metrics ===");

        // --- globale Kennzahlen ---
        System.out.printf("Average speed: %.2f m/s (%.2f km/h)%n",
                m.avgSpeedPerSecond, m.getAverageSpeedKmh());
        System.out.println("Vehicle count:      " + m.vehicleCount);
        System.out.println("Stopped vehicles:   " + m.stoppedVehicleCount);
        // getStoppedRatio() liefert 0.0 - 1.0 -> für Prozent mit 100 multiplizieren
        System.out.printf("Stopped ratio:      %.2f%%%n", m.getStoppedRatio() * 100.0);
        System.out.println();
        System.out.println("Finished trips:     " + m.finishedTripCount);
        System.out.printf("Average travel time: %.1f s%n", m.averageTravelTimeSeconds);
        System.out.printf("Min travel time:     %.1f s%n", m.minTravelTimeSeconds);
        System.out.printf("Max travel time:     %.1f s%n", m.maxTravelTimeSeconds);
        System.out.println();
        System.out.println("Short trips  (< 60 s):      " + m.shortTripsCount);
        System.out.println("Medium trips (60–300 s):    " + m.mediumTripsCount);
        System.out.println("Long trips   (> 300 s):     " + m.longTripsCount);
        System.out.println();

        // --- Per-Edge-Metriken ---
        System.out.println("=== Per-Edge metrics ===");
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
        System.out.println();

        // --- Stau-Kanten nach neuer 60%-Regel ---
        System.out.println("=== Congested edges (>= 60% stopped & min vehicles) ===");
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
