package rt.traffic.backend.traciServices.Vehicle;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import rt.traffic.config.SumoPath;

/*
 * RoutePreloader
 *
 * Liest die aktive rou.xml und baut daraus eine Liste an Routen.
 *
 * Wird gebraucht für:
 * - GUI (Dropdown / Auswahl)
 * - CarInjectionService (Route.add(...) Registrierung)
 *
 * Die Datei wird nur einmal gelesen und dann gecached.
 */
public final class RoutePreloader {

    /*
     * RouteInfo ist das Datenobjekt, das wir überall verwenden.
     *
     * routeId : die ID, die wir später in SUMO registrieren (z.B. r_veh0)
     * edges : die Edge-Liste für Route.add(...)
     * displayName : schöner Name für die GUI (Route 1, Route 2, ...)
     */
    public static final class RouteInfo {

        public final String routeId;
        public final List<String> edges;
        public final String displayName;

        public RouteInfo(String routeId, List<String> edges, String displayName) {
            this.routeId = routeId;
            this.edges = edges;
            this.displayName = displayName;
        }

        /*
         * JComboBox zeigt standardmäßig toString() an.
         */
        @Override
        public String toString() {
            return displayName;
        }
    }

    /*
     * Cache: nachdem wir einmal geladen haben, geben wir immer das zurück.
     */
    private static List<RouteInfo> cached = null;

    private RoutePreloader() {
    }

    /*
     * Hauptmethode: Routen aus rou.xml laden.
     * Wenn schon geladen, wird direkt der Cache zurückgegeben.
     */
    public static List<RouteInfo> loadRoutes() {

        if (cached != null) {
            return cached;
        }

        synchronized (RoutePreloader.class) {

            if (cached != null) {
                return cached;
            }

            String rouPath = SumoPath.getRouPath();
            File file = new File(rouPath);

            if (!file.exists()) {
                throw new IllegalStateException("rou.xml nicht gefunden: " + rouPath);
            }

            List<RouteInfo> routes = new ArrayList<>();

            try {
                Document doc = DocumentBuilderFactory
                        .newInstance()
                        .newDocumentBuilder()
                        .parse(file);

                doc.getDocumentElement().normalize();

                // Wir lesen alle <vehicle> und nehmen deren <route edges="...">
                NodeList vehicles = doc.getElementsByTagName("vehicle");

                int routeCounter = 0;

                for (int i = 0; i < vehicles.getLength(); i++) {

                    Element vehicle = (Element) vehicles.item(i);
                    String vehicleId = vehicle.getAttribute("id");

                    if (vehicleId == null || vehicleId.isBlank()) {
                        continue;
                    }

                    Element routeElement = firstRouteElement(vehicle);
                    if (routeElement == null) {
                        continue;
                    }

                    String edgesStr = routeElement.getAttribute("edges");
                    if (edgesStr == null || edgesStr.isBlank()) {
                        continue;
                    }

                    List<String> edges = splitEdges(edgesStr);

                    // Route-ID bauen wir aus der Vehicle-ID
                    String routeId = "r_" + vehicleId;

                    // Anzeigename für GUI
                    routeCounter++;
                    String displayName = "Route " + routeCounter;

                    routes.add(new RouteInfo(routeId, edges, displayName));
                }

            } catch (Exception ex) {
                throw new RuntimeException("Fehler beim Lesen der rou.xml: " + rouPath, ex);
            }

            if (routes.isEmpty()) {
                throw new IllegalStateException("Keine Routen in rou.xml gefunden: " + rouPath);
            }

            cached = Collections.unmodifiableList(routes);
            return cached;
        }
    }

    // -------------------------------------------------------------------------
    // intern
    // -------------------------------------------------------------------------

    /*
     * Holt das erste <route> Element aus einem <vehicle>.
     * Wir erwarten hier genau eine Route pro Vehicle-Eintrag.
     */
    private static Element firstRouteElement(Element vehicle) {
        NodeList routeNodes = vehicle.getElementsByTagName("route");
        if (routeNodes.getLength() == 0) {
            return null;
        }
        return (Element) routeNodes.item(0);
    }

    /*
     * edges="a b c d" -> ["a","b","c","d"]
     */
    private static List<String> splitEdges(String edgesString) {
        String[] parts = edgesString.trim().split("\\s+");
        List<String> result = new ArrayList<>(parts.length);

        for (String p : parts) {
            if (!p.isBlank()) {
                result.add(p.trim());
            }
        }

        return result;
    }
}
