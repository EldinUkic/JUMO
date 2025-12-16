package rt.traffic.backend;

import org.eclipse.sumo.libtraci.Simulation;
import org.eclipse.sumo.libtraci.StringVector;
import org.eclipse.sumo.libtraci.Vehicle;

import rt.traffic.backend.traciServices.VehicleServices;
import rt.traffic.backend.traciServices.TrafficLightServices;

/**
 * Sim:
 * Diese Klasse ist der "Motor" deiner SUMO-Simulation.
 *
 * Was macht sie?
 * - Startet SUMO (mit oder ohne GUI) mit einer Config-Datei.
 * - Kann die Simulation laufen lassen (Play), anhalten (Pause) oder einmalig
 *   weiter schalten (stepOnce).
 * - Holt pro Tick LIVE-Daten:
 *   - Fahrzeuge (VehicleServices.vehiclePull)
 *   - Ampeln   (TrafficLightServices.trafficLightPull)
 * - Kann eine simple Regel anwenden, die eine Ampelphase umschaltet:
 *   "Wenn auf Edge X >= threshold Fahrzeuge sind → grün, sonst rot."
 * - Kann Spawns anwenden (VehicleServices.applySpawns), damit Fahrzeuge entstehen.
 *
 * NEU (aus Sim_neu):
 * - Kann einmalig alle TrafficLights ausgeben (printAllTrafficLights)
 * - Kann einen Stress-Test auslösen:
 *   -> queued pro Route X Fahrzeuge (einmalig), damit es richtig voll wird 🚗🚗🚗
 *
 * Wichtige Zustände:
 * - backendStarted: SUMO läuft schon oder nicht.
 * - autoRun: Loop läuft gerade (Play) oder nicht (Pause).
 * - loopThread: eigener Thread, der alle 100ms Simulation.step() macht.
 */
public class Sim {

    // =========================
    // BASIS-EINSTELLUNGEN
    // =========================

    // Config-Datei für SUMO (z.B. .sumocfg)
    private final String cfgFile;

    // true = sumo-gui, false = sumo (headless)
    private final boolean useGui;

    // Auto-Loop an/aus (Play/Pause)
    private boolean autoRun = false;

    // Merker: Backend (SUMO) schon gestartet?
    private boolean backendStarted = false;

    // Thread für den Auto-Loop
    private Thread loopThread;

    // =========================
    // DEMO / EINMAL-AUSGABEN
    // =========================

    // NEU: Damit wir TrafficLights nur 1x drucken (und nicht jede 100ms nerven)
    private boolean printedTrafficLightsOnce = false;

    // =========================
    // TRAFFIC RULE (AMPEL-REGEL)
    // =========================

    // Regel an/aus
    private boolean ruleEnabled = false;

    // Welche Ampel soll gesteuert werden?
    private String ruleTlId = null;

    // Welche Edge wird gezählt?
    private String ruleEdgeId = null;

    // Ab wie vielen Fahrzeugen schalten wir um?
    private int ruleThreshold = 5;

    // welche Phasen sollen gesetzt werden?
    // (Phase-Index aus SUMO/tlLogic)
    private int ruleGreenPhase = 1;
    private int ruleRedPhase = 0;

    // Damit die Regel nicht jede Millisekunde spammt
    private long lastRuleApplyMs = 0;
    private long ruleIntervalMs = 1000;

    // =========================
    // STRESS TEST (NEU)
    // =========================
    // Idee: Ein Knopf im Menü -> "mach voll hier" 😄

    // Stress-Test an/aus
    private boolean stressEnabled = false;

    // Damit Stress nicht dauerhaft spammt (nur 1x ausführen pro Aktivierung)
    private boolean stressExecutedOnce = false;

    // Wie viele Fahrzeuge pro Route sollen erzeugt werden?
    private int stressVehiclesPerRoute = 100;

    /**
     * Konstruktor
     *
     * @param cfgPath Pfad zur SUMO-Config (.sumocfg)
     * @param useGui  true = SUMO GUI starten, false = ohne GUI
     */
    public Sim(String cfgPath, boolean useGui) {
        this.cfgFile = cfgPath;
        this.useGui = useGui;
    }

    /**
     * Startet das SUMO Backend (nur einmal).
     * - preloadLibraries() lädt native Libs
     * - startet SUMO mit config, start, step-length
     */
    public void run() {
        if (backendStarted)
            return;

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

    /**
     * Play = startet den Auto-Loop.
     * Der Loop macht:
     * - Simulation.step()
     * - Live-Daten ziehen (Vehicles, TrafficLights)
     * - Regel anwenden (wenn aktiv)
     * - Stress-Test anwenden (wenn aktiv)
     * - (NEU) einmalig TrafficLights drucken
     * - Spawns anwenden
     * - kurz schlafen (100ms), damit CPU nicht komplett brennt 🔥
     */
    public void play() {
        if (!backendStarted)
            run();

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
                    TrafficLightServices.trafficLightPull();
                    applyTrafficRuleIfEnabled();

                    // NEU: Stress-Test anwenden
                    applyStressTestIfEnabled();

                    // NEU: einmalig alle TrafficLights anzeigen (Debug/Demo)
                    if (!printedTrafficLightsOnce) {
                        try {
                            TrafficLightServices.printAllTrafficLights();
                        } catch (Throwable t) {
                            System.err.println("[SIM] printAllTrafficLights Fehler: " + t.getMessage());
                        }
                        printedTrafficLightsOnce = true;
                    }

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

    /**
     * Pause = stoppt den Auto-Loop.
     * - autoRun wird false, der Thread hört auf
     * - join(...) wartet kurz, damit der Thread sauber endet
     */
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

    /**
     * Einfache regelbasierte Ampelsteuerung (nur wenn aktiviert):
     * - zählt Fahrzeuge auf ruleEdgeId
     * - wenn >= threshold -> greenPhase, sonst redPhase
     * - setzt Phase nur, wenn sie sich wirklich ändern muss (kein Spam)
     *
     * Safety:
     * - prüft erst, ob ruleEnabled und IDs gesetzt sind
     * - läuft maximal alle ruleIntervalMs Millisekunden
     */
    private void applyTrafficRuleIfEnabled() {
        if (!ruleEnabled)
            return;
        if (ruleTlId == null || ruleTlId.isBlank())
            return;
        if (ruleEdgeId == null || ruleEdgeId.isBlank())
            return;

        long now = System.currentTimeMillis();
        if (now - lastRuleApplyMs < ruleIntervalMs)
            return;
        lastRuleApplyMs = now;

        int vehiclesOnEdge = VehicleServices.getVehicleOnEdge(ruleEdgeId).size();
        int targetPhase = (vehiclesOnEdge >= ruleThreshold) ? ruleGreenPhase : ruleRedPhase;

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

    /**
     * =========================
     * STRESS TEST (NEU)
     * =========================
     * Was macht das?
     * - Holt alle Route-IDs
     * - Holt irgendeinen gültigen VehicleType
     * - queued pro Route "stressVehiclesPerRoute" Fahrzeuge
     *
     * Wichtig:
     * - läuft nur wenn stressEnabled == true
     * - wird pro Aktivierung nur 1x ausgeführt (stressExecutedOnce)
     */
    private void applyStressTestIfEnabled() {
        if (!stressEnabled)
            return;
        if (stressExecutedOnce)
            return;

        // 1) alle Routen holen
        var routes = VehicleServices.getAllRouteIds();
        if (routes.isEmpty()) {
            System.out.println("[STRESS] No routes found.");
            stressExecutedOnce = true;
            return;
        }

        // 2) irgendeinen gültigen VehicleType aus SUMO holen
        String typeId = VehicleServices.getAnyVehicleTypeId();
        if (typeId == null || typeId.isBlank()) {
            System.out.println("[STRESS] No vehicle type found.");
            stressExecutedOnce = true;
            return;
        }

        int totalQueued = 0;
        long now = System.currentTimeMillis();

        // 3) pro Route stressVehiclesPerRoute Fahrzeuge queue'n
        for (String routeId : routes) {
            for (int i = 0; i < stressVehiclesPerRoute; i++) {
                String vehId = "stress_" + routeId + "_" + now + "_" + i;

                VehicleServices.queueSpawn(
                        new VehicleServices.SpawnRequest(vehId, routeId, typeId, -1)
                );
                totalQueued++;
            }
        }

        System.out.println("[STRESS] Queued " + totalQueued + " vehicles ("
                + stressVehiclesPerRoute + " per route, routes=" + routes.size() + ").");

        // nur einmal ausführen, bis wieder ausgeschaltet wird
        stressExecutedOnce = true;
    }

      
    public void configureRule(String tlId, String edgeId, int threshold) {
    this.ruleTlId = tlId;
    this.ruleEdgeId = edgeId;
    this.ruleThreshold = Math.max(1, threshold);

    System.out.println("[RULE] configured: tlId=" + ruleTlId
            + ", edgeId=" + ruleEdgeId
            + ", threshold=" + ruleThreshold);
    }

    /**
     * Schaltet die Regel an/aus (Toggle).
     * Beispiel: false -> true -> false -> ...
     */
    public void toggleRule() {
    this.ruleEnabled = !this.ruleEnabled;
    System.out.println("[RULE] enabled=" + ruleEnabled);
    }

    /**
     * =========================
     * STRESS TEST API (NEU)
     * =========================
     * Diese Methoden sind für dein Menü/GUI gedacht.
     */

    /**
     * Setzt wie viele Fahrzeuge pro Route erzeugt werden sollen.
     */
    public void configureStressTest(int vehiclesPerRoute) {
        if (vehiclesPerRoute < 1)
            vehiclesPerRoute = 1;

        this.stressVehiclesPerRoute = vehiclesPerRoute;
        System.out.println("[STRESS] vehiclesPerRoute=" + this.stressVehiclesPerRoute);
    }

    /**
     * Stress-Test an/aus.
     * Wenn man ihn wieder anschaltet, darf er wieder 1x ausführen.
     */
    public void toggleStressTest() {
        this.stressEnabled = !this.stressEnabled;

        if (this.stressEnabled) {
            this.stressExecutedOnce = false;
        }

        System.out.println("[STRESS] enabled=" + this.stressEnabled);
    }

    /**
     * Macht genau EINEN Simulationsschritt (kein Auto-Loop).
     * - step
     * - live pulls
     * - Regel anwenden
     * - Stress-Test anwenden (NEU)
     * - spawns
     */
    public void stepOnce() {
        if (!backendStarted)
            run();

        try {
            Simulation.step();
            VehicleServices.vehiclePull();
            TrafficLightServices.trafficLightPull();

            // Regel auch im Einzelstep anwenden
            applyTrafficRuleIfEnabled();

            // NEU: Stress-Test auch im Einzelstep (damit Step-Button es auch triggert)
            applyStressTestIfEnabled();

            VehicleServices.applySpawns(Simulation.getTime());
            double t = Simulation.getTime();
            System.out.println("[SIM] Einzelstep, t = " + t);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Neustart:
     * - erst sauber shutdown
     * - dann run() erneut
     */
    public void restart() {
        shutdown();
        run();
    }

    /**
     * Stoppt alles sauber:
     * - autoRun aus
     * - Thread join
     * - Simulation.close()
     * - Flags zurücksetzen
     */
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