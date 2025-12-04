package rt.traffic.backend.traciServices;

import org.eclipse.sumo.libtraci.Vehicle;   // SUMO Fahrzeug-API
import org.eclipse.sumo.libtraci.TraCIPosition;
import org.eclipse.sumo.libtraci.TrafficLight;
import org.eclipse.sumo.libtraci.StringVector;
import java.util.ArrayList;
import java.util.List;

public class TrafficLightServices {

    private static List<TrafficLightServices> TrafficLightList = new ArrayList<>();
    
    private String tlId;
    private String state;
    private int phase; 
    private double px;
    private double py;

    public TrafficLightServices(String tlId, int phase, double px, double py) {
        this.tlId = tlId;
        this.phase = phase;
        this.px = px;
        this.py = py;
    }


    public static void trafficLightPull() {

        StringVector idTL = TrafficLight.getIDList();
        
        List<TrafficLightServices> result = new ArrayList<>();

        for (int i = 0; i < idTL.size(); i++) {
            String tlId = idTL.get(i);
            int phase = TrafficLight.getPhase(tlId);
            var pos = TrafficLight.; // pos ist ein double[]
            double px = pos.getX();
            double py = pos.getY();

            result.add(new TrafficLightServices(tlId, phase, px, py));
        }
    
    }
}
