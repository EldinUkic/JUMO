package rt.traffic.backend.traciServices;

import org.eclipse.sumo.libtraci.TrafficLight;
import org.eclipse.sumo.libtraci.StringVector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TrafficLightServices {

    private static List<TrafficLightSnapshot> trafficLightList = new ArrayList<>();

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
}
