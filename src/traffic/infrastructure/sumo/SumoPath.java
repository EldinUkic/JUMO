package traffic.infrastructure.sumo;

public class SumoPath {

    public static final String projectRoot = System.getProperty("user.dir");

    // Pfad zur SUMO-Konfigurationsdatei
    public static final String cfgPath = projectRoot + "/src/traffic/infrastructure/sumo/osm.sumocfg";
    // Pfad zur SUMO-Netzdatei
    public static final String netPath = projectRoot + "/src/traffic/infrastructure/sumo/osm.net.xml";
    // Pfad zur SUMO-Polygondatei
    public static final String polyPath = projectRoot + "/src/traffic/infrastructure/sumo/osm.poly.xml";
    // Pfad zu exports
    public static final String exports = projectRoot + "/src/exports/";

    public String getCfgPath() {
        return cfgPath;
    }

    public String getNetPath() {
        return netPath;
    }

    public String getPolyPath() {
        return polyPath;
    }

     public static String getExportPath() {
        return exports;
    }
}
