package rt.traffic.backend;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.sumo.libtraci.Simulation;
import org.eclipse.sumo.libtraci.StringVector;

import rt.traffic.backend.traciServices.Vehicle.CarInjectionService;
import rt.traffic.backend.traciServices.Vehicle.StressTestServices;
import rt.traffic.backend.traciServices.Vehicle.VehicleServices;

/**
 * Sim
 *
 * - Startet SUMO (headless oder GUI)
 * - Kontrolliert Play / Pause / Step
 * - EINZIGE Stelle, die TraCI aktiv benutzt
 */
public class Sim {

    private final String cfgFile;
    private final boolean useGui;

    private boolean autoRun = false;
    private boolean backendStarted = false;

    private Thread loopThread;

    public Sim(String cfgPath, boolean useGui) {
        this.cfgFile = cfgPath;
        this.useGui = useGui;
    }

    /*
     * ==========================================================
     * START
     * ==========================================================
     */
    public void run() {
        if (backendStarted)
            return;

        printStartupBanner();

        Simulation.preloadLibraries();

        String sumoExec = useGui ? "sumo-gui" : "sumo";

        // 🔧 SUMO-Argumente (ruhig & sauber)
        List<String> args = new ArrayList<>();
        args.add(sumoExec);
        args.add("-c");
        args.add(cfgFile);
        args.add("--step-length");
        args.add("0.1");

        // 🔇 LOG-SPAM AUS
        args.add("--no-warnings");
        args.add("--no-step-log");
        args.add("--log");
        args.add("/dev/null");
        args.add("--error-log");
        args.add("/dev/null");
        args.add("--message-log");
        args.add("/dev/null");

        Simulation.start(new StringVector(args.toArray(new String[0])));

        backendStarted = true;
        System.out.println("[SIM] Backend ready ✓ (" + sumoExec + ")\n");
    }

    private void printStartupBanner() {
        System.out.println();
        System.out.println("========================================");
        System.out.println("        JUMO TRAFFIC SIMULATION");
        System.out.println("========================================");
        System.out.println("[CFG]  " + cfgFile);
        System.out.println("[MODE] " + (useGui ? "sumo-gui" : "headless"));
        System.out.println("========================================\n");
    }

    /*
     * ==========================================================
     * PLAY / LOOP
     * ==========================================================
     */
    public void play() {
        if (!backendStarted)
            run();

        if (autoRun) {
            System.out.println("[SIM] Already running.");
            return;
        }

        autoRun = true;
        System.out.println("[SIM] ▶ Play");

        loopThread = new Thread(() -> {
            try {
                while (autoRun) {

                    // 1) Queue Stress-Test (kein TraCI-heavy)
                    StressTestServices.tickStressTest();

                    // 2) Spawn Requests anwenden
                    CarInjectionService.applySpawn();

                    // 3) Simulationsschritt
                    Simulation.step();

                    // 4) Snapshot ziehen
                    VehicleServices.vehiclePull();

                    Thread.sleep(100);
                }
            } catch (Exception e) {
                System.out.println("[SIM] Loop crashed:");
                e.printStackTrace();
            }
        }, "Sim-Loop");

        loopThread.start();
    }

    /*
     * ==========================================================
     * PAUSE / STEP
     * ==========================================================
     */
    public void pause() {
        if (!autoRun) {
            System.out.println("[SIM] Already paused.");
            return;
        }

        autoRun = false;
        System.out.println("[SIM] ⏸ Paused");

        try {
            if (loopThread != null)
                loopThread.join(500);
        } catch (InterruptedException ignore) {
        }
    }

    public void stepOnce() {
        if (!backendStarted)
            run();

        try {
            StressTestServices.tickStressTest();
            CarInjectionService.applySpawn();
            Simulation.step();
            VehicleServices.vehiclePull();

            System.out.println("[SIM] Step → t=" + Simulation.getTime());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /*
     * ==========================================================
     * STOP / RESTART
     * ==========================================================
     */
    public void restart() {
        shutdown();
        run();
    }

    public void shutdown() {
        System.out.println("[SIM] Shutdown...");

        autoRun = false;

        try {
            if (loopThread != null)
                loopThread.join(500);
        } catch (InterruptedException ignore) {
        }

        try {
            Simulation.close();
        } catch (Exception ignore) {
        }

        backendStarted = false;
        loopThread = null;

        System.out.println("[SIM] Stopped.\n");
    }

    /*
     * ==========================================================
     * STATUS
     * ==========================================================
     */
    public boolean isBackendStarted() {
        return backendStarted;
    }

    public boolean isAutoRunning() {
        return autoRun;
    }
}
