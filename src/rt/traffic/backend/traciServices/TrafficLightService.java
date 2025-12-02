package rt.traffic.backend.traciServices;

import org.eclipse.sumo.libtraci.StringVector;
import org.eclipse.sumo.libtraci.TrafficLight;

public class TrafficLightService {

    public StringVector getAllIds() {
        return TrafficLight.getIDList();
    }

    public int getPhase(String id) {
        return TrafficLight.getPhase(id);
    }

    public void setPhase(String id, int phase) {
        TrafficLight.setPhase(id, phase);
    }
}
