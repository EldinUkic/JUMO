package rt.traffic.backend;

import org.eclipse.sumo.libtraci.Simulation;
import org.eclipse.sumo.libtraci.StringVector;
import org.eclipse.sumo.libtraci.Vehicle;

import rt.traffic.backend.traciServices.VehicleServices;

public class Sim {

    private final String cfgFile;
    private final boolean useGui;
    private boolean autoRun = false;
    private boolean backendStarted = false;
    private Thread loopThread;
    

    // Konstruktor
    public Sim(String cfgPath, boolean useGui) {
        this.cfgFile = cfgPath;
        this.useGui = useGui;
    }

    // SUMO starten
    public void run() {
        System.out.println("========== SimulationApp Start ==========");
        System.out.println("Config: " + cfgFile);
        System.out.println("=========================================\n");

        Simulation.preloadLibraries();

        String sumoExec = useGui ? "sumo-gui" : "sumo";
        Simulation.start(new StringVector(new String[] {
                sumoExec,
                "-c", cfgFile,
                "--start",
                "--step-length", "0.1",
        }));

        backendStarted = true;
        System.out.println("[INFO] SUMO-Backend gestartet (" + sumoExec + ").");
    }

    // Auto-Loop (Play)
    public void play() {
        // Backend starten, falls noch nicht läuft
        if (!backendStarted) {
            run();
        }

        if (autoRun) {
            System.out.println("[SIM] Läuft schon.");
            return;
        }

        autoRun = true;
        System.out.println("[SIM] Play gestartet.");

        loopThread = new Thread(() -> {
            try {
                while (autoRun) {

                    Simulation.step();                // 1 Schritt
                    VehicleServices.vehiclePull();
 
                    VehicleServices.applySpawns(Simulation.getTime());
                    
                    Thread.sleep(100);                // 100 ms warten
                }
                System.out.println("[SIM] Loop-Thread beendet.");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        loopThread.start();
    }

    // Pause: Auto-Loop stoppen
    public void pause() {
        if (!autoRun) {
            System.out.println("[SIM] Ist schon pausiert.");
            return;
        }

        autoRun = false;
        System.out.println("[SIM] Pause (Auto-Loop gestoppt).");

        // kurz warten, bis Thread wirklich weg ist
        if (loopThread != null) {
            try {
                loopThread.join(500);
            } catch (InterruptedException e) {
                // Ignorieren oder loggen
                System.out.println("[SIM] Pause-Join unterbrochen.");
            }
        }
    }

    // Ein einzelner Step (für Step-Button)
    public void stepOnce() {
        if (!backendStarted) {
            run();
        }

        try {
            Simulation.step();
            double t = Simulation.getTime();
            System.out.println("[SIM] Einzelstep, t = " + t);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Restart (wie in deinem AppMain benutzt)
    public void restart() {
        shutdown();
        run();
    }

    // Alles sauber beenden
    public void shutdown() {
        System.out.println("[SIM] Stop wird ausgeführt...");

        autoRun = false;

        if (loopThread != null) {
            try {
                loopThread.join(500);       // ⚠️ InterruptedException → try/catch
            } catch (InterruptedException e) {
                System.out.println("[SIM] Stop-Join unterbrochen.");
            }
        }

        try {
            Simulation.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        backendStarted = false;
        loopThread = null;

        System.out.println("[SIM] Stop fertig.");
    }
}
