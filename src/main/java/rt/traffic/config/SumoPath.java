package rt.traffic.config;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SumoPath {

    private static final String projectRoot = System.getProperty("user.dir");
    private static final String exportRoot = projectRoot + "/exports/";

    // ==================================================
    // MAP PROFILE
    // ==================================================
    public static final class MapProfile {
        public final String name;
        public final String cfgPath;
        public final String netPath;
        public final String polyPath;
        public final String rouPath;

        public MapProfile(String name, String cfgPath, String netPath, String polyPath, String rouPath) {
            this.name = name;
            this.cfgPath = cfgPath;
            this.netPath = netPath;
            this.polyPath = polyPath;
            this.rouPath = rouPath;
        }
    }

    // ==================================================
    // REGISTERED MAPS
    // ==================================================
    private static final Map<String, MapProfile> MAPS = new LinkedHashMap<>();

    static {
        addMap(new MapProfile(
                "DEFAULT",
                projectRoot + "/src/main/resources/traffic/infrastructure/sumo/osm.sumocfg",
                projectRoot + "/src/main/resources/traffic/infrastructure/sumo/osm.net.xml",
                projectRoot + "/src/main/resources/traffic/infrastructure/sumo/osm.poly.xml",
                projectRoot + "/src/main/resources/traffic/infrastructure/sumo/osm.rou.xml"));

        // Weitere Maps später hier hinzufügen
    }

    private static MapProfile active = MAPS.get("DEFAULT");

    private SumoPath() {
    }

    private static void addMap(MapProfile p) {
        MAPS.put(p.name, p);
    }

    // ==================================================
    // ACTIVE MAP
    // ==================================================
    public static void useMap(String name) {
        MapProfile p = MAPS.get(name);
        if (p == null) {
            throw new IllegalArgumentException("Unknown map: " + name);
        }
        active = p;
    }

    // ==================================================
    // PATH GETTERS (Simulation / UI)
    // ==================================================
    public static String getCfgPath() {
        return active.cfgPath;
    }

    public static String getNetPath() {
        return active.netPath;
    }

    public static String getPolyPath() {
        return active.polyPath;
    }

    public static String getActiveMapName() {
        return active.name;
    }

    public static String getRouPath() {
        return active.rouPath;
    }

    // ==================================================
    // EXPORT PATH
    // ==================================================
    public static String getExportPath() {

        // Optional: Exports pro Map trennen
        String path = exportRoot + active.name + "/";

        File dir = new File(path);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        return path;
    }
}
