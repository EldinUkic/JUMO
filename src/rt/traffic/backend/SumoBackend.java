package rt.traffic.backend;

import org.eclipse.sumo.libtraci.Simulation;
import org.eclipse.sumo.libtraci.StringVector;

import java.io.File;

public class SumoBackend {

    private final File cfgFile;
    private final boolean useGui;
    private boolean running = false;

    public SumoBackend(String cfgPath, boolean useGui) {
        this.cfgFile = new File(cfgPath);
        if (!cfgFile.exists()) {
            throw new IllegalArgumentException(
                    "Config-Datei nicht gefunden: " + cfgFile.getAbsolutePath());
        }
        this.useGui = useGui;
    }

    public void start() {
        if (running) {
            System.out.println("[LibtraciBackend] Läuft schon.");
            return;
        }

        // Native JNI-Libs laden
        Simulation.preloadLibraries();

        String sumoBinary = useGui ? "sumo-gui" : "sumo";

        StringVector args = new StringVector();
        args.add(sumoBinary);
        args.add("-c");
        args.add(cfgFile.getPath());
        args.add("--start"); // direkt loslaufen

        System.out.println("[LibtraciBackend] Starte SUMO mit CFG: " + cfgFile.getPath());
        Simulation.start(args);

        running = true;
    }

    public void step() {
        if (!running) {
            throw new IllegalStateException("Simulation läuft nicht. Erst start() aufrufen.");
        }
        Simulation.step();
    }

    public void shutdown() {
        if (!running)
            return;
        try {
            Simulation.close();
            System.out.println("[LibtraciBackend] Simulation geschlossen.");
        } catch (Exception e) {
            System.err.println("[LibtraciBackend] Fehler beim Schließen: " + e.getMessage());
        } finally {
            running = false;
        }
    }

    public boolean isRunning() {
        return running;
    }
}