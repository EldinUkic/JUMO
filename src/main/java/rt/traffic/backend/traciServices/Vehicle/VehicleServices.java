package rt.traffic.backend.traciServices.Vehicle;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.sumo.libtraci.Simulation;
import org.eclipse.sumo.libtraci.StringVector;
import org.eclipse.sumo.libtraci.TraCIPosition;
import org.eclipse.sumo.libtraci.Vehicle;

/*
 * VehicleServices
 *
 * - Snapshot aller Fahrzeuge pro Sim-Step
 * - GUI liest nur Snapshot (immutable)
 *
 * Fixes:
 * - activeIds gepflegt über departed/arrived (+ teleport)
 * - Iteration über Kopie (keine ConcurrentModification)
 * - VehicleList = immutable snapshot (UI-safe)
 */
public final class VehicleServices {

    // UI-sicher: Liste wird nur ersetzt, nie verändert
    private static volatile List<VehicleServices> VehicleList = List.of();

    // Nur intern: stabile Menge "aktiver" IDs
    private static final Set<String> activeIds = new HashSet<>();

    // Snapshot-Daten pro Fahrzeug
    public final String id;
    public final String edgeId;
    public final String routeId;
    public final String typeId;
    public final double speed;
    public final double px;
    public final double py;

    private VehicleServices(
            String id,
            String edgeId,
            String routeId,
            String typeId,
            double speed,
            double px,
            double py) {
        this.id = id;
        this.edgeId = edgeId;
        this.routeId = routeId;
        this.typeId = typeId;
        this.speed = speed;
        this.px = px;
        this.py = py;
    }

    /*
     * Pro Sim-Step aufrufen (ideal: NACH Simulation.step()).
     */
    public static void vehiclePull() {

        // 1) activeIds updaten (departed/arrived + teleport)
        try {
            // neu in die Sim
            StringVector departed = Simulation.getDepartedIDList();
            for (int i = 0; i < departed.size(); i++) {
                activeIds.add(departed.get(i));
            }

            // aus der Sim raus (normal angekommen)
            StringVector arrived = Simulation.getArrivedIDList();
            for (int i = 0; i < arrived.size(); i++) {
                activeIds.remove(arrived.get(i));
            }

            // teleport-start / teleport-end -> ebenfalls raus (sonst "not known" spam)
            // (Methodennamen hängen von SUMO/libtraci Version ab)
            try {
                StringVector startTeleport = Simulation.getStartingTeleportIDList();
                for (int i = 0; i < startTeleport.size(); i++) {
                    activeIds.remove(startTeleport.get(i));
                }
            } catch (Throwable ignore) {
                // falls API nicht existiert -> einfach ignorieren
            }

            try {
                StringVector endTeleport = Simulation.getEndingTeleportIDList();
                for (int i = 0; i < endTeleport.size(); i++) {
                    activeIds.remove(endTeleport.get(i));
                }
            } catch (Throwable ignore) {
                // falls API nicht existiert -> einfach ignorieren
            }

        } catch (Exception ignore) {
            // wenn Simulation gerade nicht bereit ist -> skip
        }

        // 2) Snapshot bauen: über Kopie iterieren (kein ConcurrentModification)
        List<String> idsSnapshot = new ArrayList<>(activeIds);

        List<VehicleServices> result = new ArrayList<>(idsSnapshot.size());
        List<String> toRemove = new ArrayList<>();

        for (String id : idsSnapshot) {
            try {
                // schneller Existenz-Check
                String edgeId = Vehicle.getRoadID(id);
                if (edgeId == null || edgeId.isBlank()) {
                    continue;
                }

                TraCIPosition pos = Vehicle.getPosition(id);
                double speed = Vehicle.getSpeed(id);

                // nice-to-have: separat absichern
                String routeId = "";
                String typeId = "";
                try {
                    routeId = Vehicle.getRouteID(id);
                } catch (Exception ignore) {
                }
                try {
                    typeId = Vehicle.getTypeID(id);
                } catch (Exception ignore) {
                }

                result.add(new VehicleServices(
                        id,
                        edgeId,
                        routeId,
                        typeId,
                        speed,
                        pos.getX(),
                        pos.getY()));

            } catch (Exception ex) {
                // "is not known" -> einmal rauswerfen, dann ist Ruhe
                toRemove.add(id);
            }
        }

        // 3) Aufräumen (nach der Schleife!)
        for (String id : toRemove) {
            activeIds.remove(id);
        }

        // 4) UI-sicherer Snapshot (immutable)
        VehicleList = List.copyOf(result);
    }

    // -------------------------------------------------------------------------
    // Getter für GUI
    // -------------------------------------------------------------------------

    public static List<VehicleServices> getVehicleList() {
        return VehicleList;
    }

    public static VehicleServices getVehicleById(String vehicleId) {
        for (VehicleServices v : VehicleList) {
            if (v.id.equals(vehicleId)) {
                return v;
            }
        }
        return null;
    }

    public static int getVehicleSum() {
        return VehicleList.size();
    }

    public static List<VehicleServices> getVehicleOnEdge(String edgeId) {
        List<VehicleServices> result = new ArrayList<>();
        for (VehicleServices v : VehicleList) {
            if (edgeId.equals(v.edgeId)) {
                result.add(v);
            }
        }
        return result;
    }

    public static double getAverageSpeed() {
        if (VehicleList.isEmpty())
            return 0.0;

        double sum = 0.0;
        for (VehicleServices v : VehicleList) {
            sum += v.speed;
        }
        return sum / VehicleList.size();
    }

    public static void printAllVehicles() {
        System.out.println("=== LIVE-FAHRZEUGE ===");
        for (VehicleServices v : VehicleList) {
            System.out.println(
                    "ID=" + v.id +
                            ", Edge=" + v.edgeId +
                            ", Route=" + v.routeId +
                            ", Type=" + v.typeId +
                            ", Speed=" + v.speed +
                            ", Pos=(" + v.px + ", " + v.py + ")");
        }
    }
}
