package rt.traffic.backend.traciServices.TrafficLights;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.sumo.libtraci.StringVector;
import org.eclipse.sumo.libtraci.TrafficLight;

import rt.traffic.backend.traciServices.Vehicle.VehicleServices;

public class TrafficLightServices {

    private static List<TrafficLightSnapshot> trafficLightList = new ArrayList<>();

    // =======================================================
    // RULE STATE (war vorher in Sim) -> jetzt hier drin
    // =======================================================
    private static boolean ruleEnabled = false;
    private static String ruleTlId = null;
    private static String ruleEdgeId = null;
    private static int ruleThreshold = 5;

    // Phase-Index aus SUMO/tlLogic
    private static int ruleGreenPhase = 1;
    private static int ruleRedPhase = 0;

    // damit es nicht spammt
    private static long lastRuleApplyMs = 0;
    private static long ruleIntervalMs = 1000;

    public static class TrafficLightSnapshot {
        public final String tlId;
        public final int phaseIndex;
        public final String state;
        public final String programId;

        public TrafficLightSnapshot(String tlId, int phaseIndex, String state, String programId) {
            this.tlId = tlId;
            this.phaseIndex = phaseIndex;
            this.state = state;
            this.programId = programId;
        }
    }

    public static void trafficLightPull() {
        StringVector ids = TrafficLight.getIDList();
        List<TrafficLightSnapshot> result = new ArrayList<>();

        for (int i = 0; i < ids.size(); i++) {
            String tlId = ids.get(i);
            int phase = TrafficLight.getPhase(tlId);
            String state = TrafficLight.getRedYellowGreenState(tlId);
            String program = TrafficLight.getProgram(tlId);

            result.add(new TrafficLightSnapshot(tlId, phase, state, program));
        }

        trafficLightList = result;
    }

    public static List<TrafficLightSnapshot> getTrafficLightList() {
        return Collections.unmodifiableList(trafficLightList);
    }

    public static void printAllTrafficLights() {
        System.out.println("=== TRAFFIC LIGHTS (SNAPSHOT) ===");
        for (TrafficLightSnapshot tl : trafficLightList) {
            System.out.println("ID=" + tl.tlId
                    + ", phase=" + tl.phaseIndex
                    + ", state=" + tl.state
                    + ", program=" + tl.programId);
        }
    }

    // --- Helper für Regel: aktuelle Phase abfragen ---
    public static int getPhase(String tlId) {
        return TrafficLight.getPhase(tlId);
    }

    // ---- Steuer-Funktionen ----
    public static void setPhase(String tlId, int phaseIndex) {
        TrafficLight.setPhase(tlId, phaseIndex);
    }

    public static void setProgram(String tlId, String programId) {
        TrafficLight.setProgram(tlId, programId);
    }

    public static void setState(String tlId, String state) {
        TrafficLight.setRedYellowGreenState(tlId, state);
    }

    // =======================================================
    // RULE API (für AppMain/MainWindow)
    // =======================================================

    /** Konfiguriert die Ampel-Regel. */
    public static void configureRule(String tlId, String edgeId, int threshold) {
        ruleTlId = tlId;
        ruleEdgeId = edgeId;
        ruleThreshold = Math.max(1, threshold);

        System.out.println("[RULE] configured: tlId=" + ruleTlId
                + ", edgeId=" + ruleEdgeId
                + ", threshold=" + ruleThreshold);
    }

    /** Schaltet Regel an/aus. */
    public static void toggleRule() {
        ruleEnabled = !ruleEnabled;
        System.out.println("[RULE] enabled=" + ruleEnabled);
    }

    public static boolean isRuleEnabled() {
        return ruleEnabled;
    }

    /** Optional: Phasen ändern (falls deine tlLogic anders ist). */
    public static void setRulePhases(int redPhase, int greenPhase) {
        ruleRedPhase = redPhase;
        ruleGreenPhase = greenPhase;
    }

    /** Optional: wie oft die Regel maximal laufen darf (ms). */
    public static void setRuleIntervalMs(long intervalMs) {
        ruleIntervalMs = Math.max(50, intervalMs);
    }

    /**
     * Call after each Simulation.step() (oder per Swing Timer).
     * Macht:
     * - trafficLightPull()
     * - applyRuleIfEnabled()
     */
    public static void tickRule() {
        trafficLightPull();
        applyRuleIfEnabled();
    }

    // =======================================================
    // RULE IMPLEMENTATION
    // =======================================================
    private static void applyRuleIfEnabled() {
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
            int current = getPhase(ruleTlId);
            if (current != targetPhase) {
                setPhase(ruleTlId, targetPhase);
                System.out.println("[RULE] tl=" + ruleTlId + " edge=" + ruleEdgeId
                        + " vehicles=" + vehiclesOnEdge + " threshold=" + ruleThreshold
                        + " -> phase " + targetPhase);
            }
        } catch (Throwable t) {
            System.err.println("[RULE] Fehler: " + t.getMessage());
        }
    }
}
