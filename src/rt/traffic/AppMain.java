package rt.traffic;

import java.util.Scanner;

import javax.swing.SwingUtilities;

import rt.traffic.backend.Sim;
import rt.traffic.backend.traciServices.VehicleServices;
import rt.traffic.ui.MainWindow;

public class AppMain {

    public static void main(String[] args) {

        // Pfad zu deiner SUMO-Konfiguration (anpassen falls nötig)
        String cfgPath = "src/traffic/infrastructure/sumo/osm.sumocfg";
        boolean useGui = true; // sumo-gui benutzen

        // Backend + Simulation vorbereiten
        Sim sim = new Sim(cfgPath, useGui);

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

                default:
                    System.out.println("Ungültig. Bitte 1–6 eingeben.");
            }
        }
        scanner.close();
    }
}
