package rt.traffic.backend;

import org.eclipse.sumo.libtraci.Simulation;
import org.eclipse.sumo.libtraci.StringVector;

import rt.traffic.backend.traciServices.VehicleServices;
import rt.traffic.backend.traciServices.TrafficLightServices;

public class Sim {

    private final String cfgFile;
    private final boolean useGui;

    private boolean autoRun = false;
    private boolean backendStarted = false;
    private Thread loopThread;

    // ===== Traffic Control Settings (Runtime) =====
    private boolean ruleEnabled = false;
    private String ruleTlId = null;
    private String ruleEdgeId = null;
    private int ruleThreshold = 5;

    // welche Phasen sollen gesetzt werden?
    private int ruleGreenPhase = 1;
    private int ruleRedPhase = 0;

    private long lastRuleApplyMs = 0;
    private long ruleIntervalMs = 1000;

    public Sim(String cfgPath, boolean useGui) {
        this.cfgFile = cfgPath;
        this.useGui = useGui;
    }

    public void run() {
        if (backendStarted) return;

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

    public void play() {
        if (!backendStarted) run();

        if (autoRun) {
            System.out.println("[SIM] Läuft schon.");
            return;
        }

        autoRun = true;
        System.out.println("[SIM] Play gestartet.");

        loopThread = new Thread(() -> {
            try {
                while (autoRun) {
                    Simulation.step();

                    VehicleServices.vehiclePull();
                    TrafficLightServices.trafficLightPull();

                    // Regel anwenden
                    applyTrafficRuleIfEnabled();

                    VehicleServices.applySpawns(Simulation.getTime());

                    Thread.sleep(100);
                }
                System.out.println("[SIM] Loop-Thread beendet.");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "SimulationApp-Thread");

        loopThread.start();
    }

    public void pause() {
        if (!autoRun) {
            System.out.println("[SIM] Ist schon pausiert.");
            return;
        }

        autoRun = false;
        System.out.println("[SIM] Pause (Auto-Loop gestoppt).");

        if (loopThread != null) {
            try {
                loopThread.join(500);
            } catch (InterruptedException e) {
                System.out.println("[SIM] Pause-Join unterbrochen.");
            }
        }
    }

    // ===== Einfache regelbasierte Ampelsteuerung =====
    private void applyTrafficRuleIfEnabled() {
        if (!ruleEnabled) return;
        if (ruleTlId == null || ruleTlId.isBlank()) return;
        if (ruleEdgeId == null || ruleEdgeId.isBlank()) return;

        long now = System.currentTimeMillis();
        if (now - lastRuleApplyMs < ruleIntervalMs) return;
        lastRuleApplyMs = now;

        int vehiclesOnEdge = VehicleServices.getVehicleOnEdge(ruleEdgeId).size();

        int targetPhase = (vehiclesOnEdge >= ruleThreshold) ? ruleGreenPhase : ruleRedPhase;

        // Nur setzen, wenn Phase wirklich wechseln muss (verhindert Spam)
        try {
            int current = TrafficLightServices.getPhase(ruleTlId);
            if (current != targetPhase) {
                TrafficLightServices.setPhase(ruleTlId, targetPhase);
                System.out.println("[RULE] tl=" + ruleTlId + " edge=" + ruleEdgeId
                        + " vehicles=" + vehiclesOnEdge + " threshold=" + ruleThreshold
                        + " → phase " + targetPhase);
            }
        } catch (Throwable t) {
            System.err.println("[RULE] Fehler beim Anwenden: " + t.getMessage());
        }
    }

    public void configureRule(String tlId, String edgeId, int threshold) {
        this.ruleTlId = tlId;
        this.ruleEdgeId = edgeId;
        this.ruleThreshold = Math.max(1, threshold);

        System.out.println("[RULE] configured: tlId=" + ruleTlId
                + ", edgeId=" + ruleEdgeId
                + ", threshold=" + ruleThreshold
                + ", greenPhase=" + ruleGreenPhase
                + ", redPhase=" + ruleRedPhase);
    }

    public void toggleRule() {
        this.ruleEnabled = !this.ruleEnabled;
        System.out.println("[RULE] enabled=" + ruleEnabled);
    }

    public void stepOnce() {
        if (!backendStarted) run();

        try {
            Simulation.step();
            VehicleServices.vehiclePull();
            TrafficLightServices.trafficLightPull();

            // Regel auch im Einzelstep anwenden
            applyTrafficRuleIfEnabled();

            VehicleServices.applySpawns(Simulation.getTime());

            System.out.println("[SIM] Einzelstep, t = " + Simulation.getTime());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void restart() {
        shutdown();
        run();
    }

    public void shutdown() {
        System.out.println("[SIM] Stop wird ausgeführt...");

        autoRun = false;

        if (loopThread != null) {
            try {
                loopThread.join(500);
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
