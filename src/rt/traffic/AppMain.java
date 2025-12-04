package rt.traffic;

import java.util.List;
import java.util.Scanner;

import org.eclipse.sumo.libtraci.Simulation;
import rt.traffic.backend.*;
import rt.traffic.backend.traciServices.*;
import rt.traffic.backend.traciServices.VehicleServices.SpawnRequest;

public class AppMain {

    public static void main(String[] args) {

        // Pfad zu deiner SUMO-Konfiguration (anpassen falls nötig)
        String cfgPath = "src/traffic/infrastructure/sumo/osm.sumocfg";
        boolean useGui = true; // sumo-gui benutzen

        // Backend + Simulation
        Sim sim = new Sim(cfgPath, useGui);

        // SUMO-GUI direkt starten
        sim.run();

        // Terminal-Steuerung
        Scanner scanner = new Scanner(System.in);

        System.out.println("=== Traffic Simulation Control ===");
        System.out.println("1 = Play (Auto-Loop starten)");
        System.out.println("2 = Pause (Auto-Loop stoppen)");
        System.out.println("3 = Restart Simulation (neu laden)");
        System.out.println("4 = Single Step (ein Schritt)");
        System.out.println("5 = Exit (SUMO schließen)");
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
                    System.out.println("Ungültig. Bitte 1–5 eingeben.");
            }
        }
        scanner.close();
    }
}
