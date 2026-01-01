package rt.traffic.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.WindowConstants;

import org.eclipse.sumo.libtraci.Lane;
import org.eclipse.sumo.libtraci.Simulation;

import rt.traffic.application.analytics.AnalyticsExecution;
import rt.traffic.application.analytics.Metrics;
import rt.traffic.application.analytics.TrafficTracking;
import rt.traffic.application.analytics.VehicleTracking;
import rt.traffic.backend.Sim;
import rt.traffic.backend.traciServices.TrafficLights.TrafficLightServices;
import rt.traffic.backend.traciServices.TrafficLights.TrafficLightServices.TrafficLightSnapshot;
import rt.traffic.backend.traciServices.Vehicle.CarInjectionService;
import rt.traffic.backend.traciServices.Vehicle.StressTestServices;
import rt.traffic.backend.traciServices.Vehicle.VehicleServices;

/**
 * MainWindow:
 *
 * Zentrale GUI-Klasse der Anwendung.
 *
 * Aufgaben dieser Klasse:
 * - Baut das komplette Hauptfenster der Simulation auf (JFrame)
 * * CENTER: MapView (Karte mit Straßen, Fahrzeugen und Ampeln)
 * * RIGHT: StatsPanel (Live-Zahlen zur Simulation)
 * * LEFT: TrafficLightControlPanel (manuelle Ampelsteuerung)
 * * TOP: Steuerleiste mit Buttons (Start, Stop, Step, Spawn, Export, Toggle
 * Panels)
 *
 * - Verbindet GUI und Backend:
 * * steuert die Simulation (sim.play / sim.pause / Simulation.step)
 * * holt regelmäßig Live-Daten über TraCI (Fahrzeuge, Ampelzustände)
 * * verteilt diese Daten an MapView und StatsPanel
 *
 * - Live-Update-Logik:
 * * eigener Swing-Timer (150 ms)
 * * prüft defensiv, ob TraCI verbunden ist
 * * verhindert Crashes und Log-Spam bei Verbindungsabbrüchen
 *
 * - Ampeln:
 * * bekommt LIVE-Ampelzustände aus dem Backend (TraCI)
 * * reicht sie an MapView (Zeichnung der Haltelinienfarben) weiter
 * * synchronisiert sie mit dem TrafficLightControlPanel
 * * erlaubt manuelle Phasenwahl pro TL-ID
 *
 * - Analytics / Export:
 * * sammelt aktuelle Simulationsdaten (TrafficTracking)
 * * triggert Berechnung der Metrics
 * * exportiert Ergebnisse als PDF und CSV
 *
 * - Sauberes Beenden:
 * * fängt Window-Close ab
 * * stoppt alle Timer
 * * ruft sim.shutdown() auf
 * * stellt sicher, dass SUMO/TraCI wirklich beendet wird
 *
 * - Stress Test (GUI):
 * * Button in der Top-Bar: "Stress Test: OFF/ON"
 * * Beim Einschalten fragt die GUI "Vehicles per route" ab und ruft:
 * sim.configureStressTest(vpr);
 * sim.toggleStressTest();
 * -> Backend queued dann EINMALIG Fahrzeuge (durch stressExecutedOnce Schutz)
 * * Beim Ausschalten ruft die GUI:
 * sim.toggleStressTest();
 *
 * Idee allgemein:
 * - MainWindow enthält keine Zeichenlogik und keine Simulation-Logik im Detail.
 * - Es ist der "Koordinator":
 * * UI-Events → Backend
 * * Backend-Daten → UI
 * - Dadurch bleiben MapView, StatsPanel und Backend klar getrennt und
 * austauschbar.
 */

public class MainWindow extends JFrame {

    // Backend-Steuerung (Start/Stop/Shutdown der Simulation)
    private final Sim sim;

    // Unsere Kartenanzeige (Zeichnet Netz + Fahrzeuge + Ampel-Overlays)
    private final MapView mapView;

    // Rechte Seite: simple Zahlen/Infos (z.B. Vehicle Count, Avg Speed, Sim Time)
    private final StatsPanel statsPanel;

    // Buttons zum Ein-/Ausblenden
    private final JButton toggleStatsButton;
    private final JButton toggleTlPanelButton;

    // Wrapper links, damit man das komplette Panel hide/show kann
    private final JPanel tlPanelWrapper;

    // Links: Ampel-Panel (nur Phasen-UI)
    private final TrafficLightControlPanel tlControlPanel;

    // Export
    private final JButton exportMetricsButton;

    // ✅ Stress Test Button (GUI)
    private final JButton stressTestButton;

    // ✅ UI merkt sich Stress-Status (Sim hat in eurer Version keinen Getter)
    private boolean stressUiEnabled = false;

    // Analytics-Ausführung (berechnet aus TrafficTracking -> Metrics)
    private final AnalyticsExecution analytics = new AnalyticsExecution();

    // Timer für Live-Updates (Vehicles + TL States)
    private final javax.swing.Timer vehicleTimer;

    // Wird erst true, wenn TraCI wirklich erreichbar ist (sonst knallt
    // Simulation.getTime())
    private boolean traciReady = false;

    // Nur damit wir bei Verbindungsproblemen nicht jede 150ms die Konsole
    // vollspammen
    private long lastLogMs = 0;

    /**
     * Baut das komplette GUI-Fenster auf:
     * - Center: MapView
     * - Right: StatsPanel
     * - Left: TrafficLightControlPanel
     * - Top: Buttons (Start/Stop/Step/Spawn/Export + Toggle Panels)
     *
     * Wichtig: Das Fenster hat DO_NOTHING_ON_CLOSE, damit wir beim Schließen sauber
     * den Timer stoppen und sim.shutdown() aufrufen können (damit SUMO/TraCI
     * wirklich endet).
     */
    public MainWindow(Sim sim) {
        super("Real-Time Traffic Simulation");
        this.sim = sim;

        // Wir fangen das Schließen selber ab (siehe WindowListener unten)
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setLayout(new BorderLayout());
        setSize(1220, 780);

        // ==========================================================
        // CENTER: Map
        // ==========================================================
        mapView = new MapView();
        add(mapView, BorderLayout.CENTER);

        // ==========================================================
        // RIGHT: Stats
        // ==========================================================
        statsPanel = new StatsPanel();
        statsPanel.setPreferredSize(new Dimension(260, 0));
        add(statsPanel, BorderLayout.EAST);

        // MapView soll StatsPanel direkt updaten können (z.B. Lane/Polygon Counts beim
        // Load)
        mapView.setStatsPanel(statsPanel);

        // ==========================================================
        // LEFT: Traffic Light Control
        // ==========================================================
        tlControlPanel = new TrafficLightControlPanel(mapView);

        tlPanelWrapper = new JPanel(new BorderLayout());
        tlPanelWrapper.setPreferredSize(new Dimension(340, 0));
        tlPanelWrapper.add(tlControlPanel, BorderLayout.CENTER);
        add(tlPanelWrapper, BorderLayout.WEST);

        // ==========================================================
        // TOP BAR: Buttons
        // ==========================================================
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        add(topBar, BorderLayout.NORTH);

        JButton btnStart = new JButton("Start");
        // sim.play() startet die Simulation (Backend-Thread / SUMO)
        btnStart.addActionListener(e -> safeCall("sim.play()", sim::play));

        JButton btnStop = new JButton("Stop");
        // sim.pause() pausiert die Simulation
        btnStop.addActionListener(e -> safeCall("sim.pause()", sim::pause));

        JButton btnStep = new JButton("Step");
        // Step: 1 Schritt über Sim (damit Spawns auch verarbeitet werden)
        btnStep.addActionListener(e -> safeCall("sim.stepOnce()", () -> {
            sim.stepOnce();
            updateVehiclesSafely();
        }));

        JButton btnSpawn = new JButton("Spawn...");
        // Öffnet ein kleines Dialog-UI zum Spawnen mehrerer Fahrzeuge auf einer Route
        btnSpawn.addActionListener(e -> openSpawnDialog());

        // Export Metrics (PDF + CSV)
        exportMetricsButton = new JButton("Export metrics (PDF + CSV)");
        exportMetricsButton.addActionListener(e -> exportMetricsPdfAndCsv());

        // ✅ Stress Test Button (GUI):
        // - OFF -> fragt vehicles/route, konfiguriert und toggled ON (Backend spawnt
        // einmalig)
        // - ON -> toggled OFF
        stressTestButton = new JButton("Stress Test: OFF");
        stressTestButton.addActionListener(e -> toggleStressTestFromGui());

        toggleTlPanelButton = new JButton("Hide TL panel");
        toggleTlPanelButton.addActionListener(e -> toggleTlPanel());

        toggleStatsButton = new JButton("Hide stats");
        toggleStatsButton.addActionListener(e -> toggleStats());

        topBar.add(btnStart);
        topBar.add(btnStop);
        topBar.add(btnStep);
        topBar.add(btnSpawn);

        // Abstandhalter, damit die Buttons optisch gruppiert sind
        topBar.add(Box.createHorizontalStrut(10));
        topBar.add(exportMetricsButton);

        // ✅ Stress Test Button in die TopBar (eigener Block)
        topBar.add(Box.createHorizontalStrut(10));
        topBar.add(stressTestButton);

        topBar.add(Box.createHorizontalStrut(10));
        topBar.add(toggleTlPanelButton);
        topBar.add(toggleStatsButton);

        // ==========================================================
        // LIVE UPDATE TIMER
        // ==========================================================
        // Alle 150ms: Vehicles & TrafficLights ziehen und Map/Stats updaten.
        // Wenn TraCI nicht verbunden ist, macht updateVehiclesSafely() einfach nichts.
        vehicleTimer = new javax.swing.Timer(150, e -> updateVehiclesSafely());
        vehicleTimer.start();

        // ==========================================================
        // WINDOW CLOSE
        // ==========================================================
        addWindowListener(new WindowAdapter() {
            /**
             * Wird ausgelöst, wenn der User das Fenster schließen will.
             * Wir machen hier bewusst "sauberes Aufräumen":
             * - Timer stoppen
             * - internes TL-Panel Timer stoppen
             * - sim.shutdown() aufrufen (damit SUMO wirklich beendet wird)
             * - Fenster schließen + Prozess beenden
             */
            @Override
            public void windowClosing(WindowEvent e) {
                System.out.println("[UI] Window closing → shutting down simulation");

                // Timer stoppen, damit keine weiteren Live-Calls passieren
                try {
                    if (vehicleTimer.isRunning())
                        vehicleTimer.stop();
                } catch (Throwable ignored) {
                }

                // TL Panel hat einen eigenen Timer (nur fürs UI-Refresh der Labels)
                try {
                    tlControlPanel.stopInternalTimer();
                } catch (Throwable ignored) {
                }

                // Backend sauber herunterfahren (TraCI/SUMO beenden)
                try {
                    sim.shutdown();
                } catch (Throwable t) {
                    System.err.println("[UI] Error during sim.shutdown(): " + t.getMessage());
                }

                // Fenster schließen + Prozess beenden (damit nichts "hängen bleibt")
                dispose();
                System.exit(0);
            }
        });
    }

    // ----------------------------------------------------------
    // Stress Test (GUI)
    // ----------------------------------------------------------

    /**
     * Stress Test Toggle aus der GUI:
     *
     * Verhalten:
     * - Wenn TraCI nicht ready ist: Hinweis anzeigen und abbrechen (sonst gibt es
     * keine Routen/VehicleTypes).
     * - Wenn Stress OFF:
     * * Vehicles-per-route abfragen
     * * sim.configureStressTest(vpr)
     * * sim.toggleStressTest() -> ON
     * * UI Text auf "ON"
     * - Wenn Stress ON:
     * * sim.toggleStressTest() -> OFF
     * * UI Text auf "OFF"
     *
     * Hinweis zum Backend:
     * - Euer Backend queued Fahrzeuge nur EINMALIG pro Einschalten
     * (stressExecutedOnce).
     * - Wenn erneut eingeschaltet wird, setzt toggleStressTest() stressExecutedOnce
     * wieder auf false.
     */
    private void toggleStressTestFromGui() {
        safeCall("Stress Test", () -> {
            // Ohne TraCI macht es keinen Sinn (keine Routen/VehicleTypes abrufbar)
            if (!ensureTraciReady()) {
                JOptionPane.showMessageDialog(
                        this,
                        "TraCI noch nicht verbunden.\nStarte die Simulation kurz und versuche es erneut.");
                return;
            }

            // OFF -> ON (mit Konfiguration)
            if (!stressUiEnabled) {
                int vpr = askVehiclesPerRoute();
                if (vpr <= 0)
                    return; // abgebrochen

                // Backend konfigurieren + einschalten
                StressTestServices.configureStressTest(vpr);
                StressTestServices.toggleStressTest();

                stressUiEnabled = true;
                stressTestButton.setText("Stress Test: ON");

                JOptionPane.showMessageDialog(
                        this,
                        "Stress Test eingeschaltet.\n"
                                + "Vehicles per route: " + vpr + "\n"
                                + "(Wird im Backend einmalig gequeued/spawned.)",
                        "Stress Test",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            // ON -> OFF
            StressTestServices.toggleStressTest();

            stressUiEnabled = false;
            stressTestButton.setText("Stress Test: OFF");

            JOptionPane.showMessageDialog(
                    this,
                    "Stress Test ausgeschaltet.",
                    "Stress Test",
                    JOptionPane.INFORMATION_MESSAGE);
        });
    }

    /**
     * Dialog: Vehicles per route abfragen.
     *
     * - Default: 100
     * - Min: 1
     * - Max: 2000 (kannst du bei Bedarf erhöhen)
     */
    private int askVehiclesPerRoute() {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(100, 1, 2000, 10));

        JPanel panel = new JPanel(new GridLayout(1, 2));
        panel.add(new JLabel("Vehicles per route:"));
        panel.add(spinner);

        int res = JOptionPane.showConfirmDialog(
                this,
                panel,
                "Configure Stress Test",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE);

        if (res != JOptionPane.OK_OPTION)
            return -1;

        Object v = spinner.getValue();
        if (v instanceof Integer)
            return (Integer) v;
        return 100;
    }

    // ----------------------------------------------------------
    // TraCI Connection Guard
    // ----------------------------------------------------------

    // TraCI ist manchmal beim GUI-Start noch nicht ready.
    // Wir testen einmal Simulation.getTime().
    // Wenn es knallt ("Not connected"), lassen wir Live Updates aus.
    private boolean ensureTraciReady() {
        if (traciReady)
            return true;

        try {
            Simulation.getTime();
            traciReady = true;
            // System.out.println("[UI] TraCI connected -> live updates enabled");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    // ----------------------------------------------------------
    // Live Update Cycle (Vehicles + TrafficLights)
    // ----------------------------------------------------------

    private void updateVehiclesSafely() {
        // Wenn TraCI nicht da ist: einfach nichts tun (kein Popup-Spam, kein Crash)
        if (!ensureTraciReady())
            return;

        try {
            // =========================
            // Vehicles
            // =========================
            // Pull: Backend füllt/aktualisiert die Vehicle-Liste
            VehicleServices.vehiclePull();

            // Danach lesen wir die Daten aus VehicleServices
            List<VehicleServices> vehicles = VehicleServices.getVehicleList();

            // MapView braucht Positionen als: id -> (x,y)
            Map<String, Point2D.Double> positions = new HashMap<>();
            for (VehicleServices v : vehicles) {
                // px/py kommen aus TraCI (SUMO Koordinaten)
                positions.put(v.id, new Point2D.Double(v.px, v.py));
            }

            // Fahrzeuge auf der Karte updaten
            mapView.updateVehiclePositions(positions);

            // StatsPanel: Vehicle Count + Avg Speed
            statsPanel.setVehicleCount(vehicles.size());
            statsPanel.setAverageSpeed(VehicleServices.getAverageSpeed());

            // Sim Time (wenn verfügbar)
            try {
                statsPanel.setSimTime(Simulation.getTime());
            } catch (Throwable ignored) {
            }

            // =========================
            // Traffic lights
            // =========================
            pushLiveTrafficLightStatesToMapAndPanel();

        } catch (Throwable t) {
            // Häufigster Fall: TraCI ist weg oder noch nicht connected
            String msg = String.valueOf(t.getMessage()).toLowerCase();
            if (msg.contains("not connected"))
                traciReady = false;

            // Logging drosseln: max alle ~1.5s eine Meldung
            long now = System.currentTimeMillis();
            if (now - lastLogMs > 1500) {
                lastLogMs = now;
                System.err.println("[UI] Live update failed: " + t);
            }
        }
    }

    private void pushLiveTrafficLightStatesToMapAndPanel() {
        try {
            // Pull: Backend aktualisiert Snapshot-Liste
            TrafficLightServices.trafficLightPull();
            List<TrafficLightSnapshot> tls = TrafficLightServices.getTrafficLightList();

            // Für MapView wollen wir: tlId -> state String (z.B. "GrGr...")
            // LinkedHashMap damit Reihenfolge stabil bleibt (besser fürs Debuggen/UI)
            Map<String, String> liveStates = new LinkedHashMap<>();
            for (TrafficLightSnapshot s : tls) {
                if (s != null && s.tlId != null && s.state != null) {
                    liveStates.put(s.tlId, s.state);
                }
            }

            // MapView kann die States nutzen, um Ampeln einzuzeichnen
            mapView.setLiveTrafficLightStates(liveStates);

            // TL Panel zeigt die aktuelle Auswahl und Phase/State an
            tlControlPanel.updateFromSnapshot(tls);

        } catch (Throwable ignored) {
            // bewusst still: wenn TL Pull mal nicht geht, soll die ganze GUI nicht
            // rumheulen
        }
    }

    // ==========================================================
    // Export Metrics (PDF + CSV) ✅ fixed IOException handling
    // ==========================================================

    private void exportMetricsPdfAndCsv() {
        safeCall("Export metrics", () -> {
            // Export macht nur Sinn, wenn TraCI verbunden ist (sonst keine Daten)
            if (!ensureTraciReady()) {
                JOptionPane.showMessageDialog(
                        this,
                        "TraCI noch nicht verbunden.\nStarte die Simulation kurz und versuche es erneut.");
                return;
            }

            // Wir bauen "TrafficTracking" aus den Live-Daten vom Backend
            TrafficTracking tracking = buildTrafficTrackingFromBackend();

            try {
                // Analytics berechnet daraus die Metrics (z.B. Auslastung, etc.)
                Metrics metrics = analytics.executeMetrics(tracking);

                // Beide Exporte: werfen bei euch IOException -> catch separat
                metrics.exportToPdf();
                metrics.exportToCsv();

                JOptionPane.showMessageDialog(
                        this,
                        "Metrics exportiert:\n- PDF\n- CSV",
                        "Export erfolgreich",
                        JOptionPane.INFORMATION_MESSAGE);

            } catch (java.io.IOException io) {
                // IO Problems: Pfad/Datei/Permission/locked file usw.
                io.printStackTrace();
                JOptionPane.showMessageDialog(
                        this,
                        "Export fehlgeschlagen (IO):\n" + io.getMessage(),
                        "Export-Fehler",
                        JOptionPane.ERROR_MESSAGE);

            } catch (Exception ex) {
                // Alles andere (Analytics/Logic)
                ex.printStackTrace();
                JOptionPane.showMessageDialog(
                        this,
                        "Export fehlgeschlagen:\n" + ex.getMessage(),
                        "Export-Fehler",
                        JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private static TrafficTracking buildTrafficTrackingFromBackend() {
        // Sim time (wenn TraCI nicht verfügbar: bleibt 0.0)
        double simTime = 0.0;
        try {
            simTime = Simulation.getTime();
        } catch (Throwable ignored) {
        }

        // Vehicles -> VehicleTracking:
        // Wir "mappen" hier nur das, was Analytics braucht:
        // id, edgeId, speed
        List<VehicleTracking> vehicles = new ArrayList<>();
        try {
            for (VehicleServices v : VehicleServices.getVehicleList()) {
                vehicles.add(new VehicleTracking(v.id, v.edgeId, v.speed));
            }
        } catch (Throwable ignored) {
        }

        // Edge lengths (meters):
        // Analytics braucht Längen pro Edge. Die holen wir (wenn möglich) aus
        // Lane.getLength(edgeId + "_0").
        // Fallback ist 100m, falls Lane-ID nicht existiert oder TraCI spinnt.
        Map<String, Double> edgeLengths = new HashMap<>();
        for (VehicleTracking v : vehicles) {
            String edgeId = v.edgeId;

            // Skip: leere edgeId oder bereits berechnet
            if (edgeId == null || edgeId.isEmpty() || edgeLengths.containsKey(edgeId))
                continue;

            double len = 100.0; // fallback
            try {
                // SUMO legt meist Lane IDs so an: <edgeId>_0, <edgeId>_1, ...
                // Wir nehmen _0 als "repräsentative" Länge für den Edge.
                len = Lane.getLength(edgeId + "_0");
            } catch (Throwable ignored) {
            }
            edgeLengths.put(edgeId, len);
        }

        return new TrafficTracking(simTime, vehicles, edgeLengths);
    }

    // ==========================================================
    // UI helpers
    // ==========================================================

    // safeCall: damit Buttons nicht "hart crashen", sondern ein Popup zeigen
    private void safeCall(String label, Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            t.printStackTrace();
            JOptionPane.showMessageDialog(
                    this,
                    "Fehler bei: " + label + "\n\n" + t.getMessage(),
                    "Fehler",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    // Rechtses StatsPanel ein-/ausblenden
    private void toggleStats() {
        boolean visible = statsPanel.isVisible();
        statsPanel.setVisible(!visible);

        // Button Text anpassen, damit man direkt checkt was als nächstes passiert
        toggleStatsButton.setText(visible ? "Show stats" : "Hide stats");

        // Swing: Layout neu berechnen + neu zeichnen
        revalidate();
        repaint();
    }

    // Linkes TrafficLightPanel ein-/ausblenden
    private void toggleTlPanel() {
        boolean visible = tlPanelWrapper.isVisible();
        tlPanelWrapper.setVisible(!visible);

        toggleTlPanelButton.setText(visible ? "Show TL panel" : "Hide TL panel");

        revalidate();
        repaint();
    }

    private void openSpawnDialog() {

        var routes = rt.traffic.backend.traciServices.Vehicle.RoutePreloader.loadRoutes();

        if (routes.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Keine Routen in rou.xml gefunden.");
            return;
        }

        // UI Controls
        JComboBox<rt.traffic.backend.traciServices.Vehicle.RoutePreloader.RouteInfo> routeBox = new JComboBox<>(
                routes.toArray(new rt.traffic.backend.traciServices.Vehicle.RoutePreloader.RouteInfo[0]));

        JSpinner countSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 10_000, 1));

        JButton okBtn = new JButton("OK");
        JButton cancelBtn = new JButton("Abbrechen");

        JPanel form = new JPanel(new GridLayout(2, 2, 10, 10));
        form.add(new JLabel("Route:"));
        form.add(routeBox);
        form.add(new JLabel("Anzahl:"));
        form.add(countSpinner);

        JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));
        buttons.add(cancelBtn);
        buttons.add(okBtn);

        JPanel root = new JPanel(new java.awt.BorderLayout(10, 10));
        root.setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 12, 12, 12));
        root.add(form, java.awt.BorderLayout.CENTER);
        root.add(buttons, java.awt.BorderLayout.SOUTH);

        // Dialog
        JDialog dialog = new JDialog(this, "Fahrzeuge spawnen", true);
        dialog.setContentPane(root);
        dialog.pack();
        dialog.setLocationRelativeTo(this);

        // ✅ Live-Highlight: sofort beim Öffnen + bei jeder Auswahländerung
        java.util.function.Consumer<Void> applyHighlight = (v) -> {
            var sel = (rt.traffic.backend.traciServices.Vehicle.RoutePreloader.RouteInfo) routeBox.getSelectedItem();
            if (sel != null)
                mapView.highlightEdges(sel.edges);
        };

        // beim Öffnen direkt
        applyHighlight.accept(null);

        // bei jeder Änderung
        routeBox.addActionListener(e -> {
            var sel = (rt.traffic.backend.traciServices.Vehicle.RoutePreloader.RouteInfo) routeBox.getSelectedItem();
            if (sel != null)
                mapView.highlightEdges(sel.edges);
        });

        // Ergebnis-Holder
        final boolean[] ok = { false };

        okBtn.addActionListener(e -> {
            ok[0] = true;
            dialog.dispose();
        });

        cancelBtn.addActionListener(e -> {
            ok[0] = false;
            dialog.dispose();
        });

        // ✅ Wichtig: Wenn User das Fenster über X schließt -> Highlight weg
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        dialog.setVisible(true);

        // ✅ Nach dem Schließen immer Highlight entfernen
        mapView.clearHighlight();

        // Cancel?
        if (!ok[0])
            return;

        var selected = (rt.traffic.backend.traciServices.Vehicle.RoutePreloader.RouteInfo) routeBox.getSelectedItem();
        if (selected == null)
            return;

        int count = (Integer) countSpinner.getValue();

        // ✅ EINZIGER GUI -> BACKEND CALL
        CarInjectionService.requestSpawn(selected.routeId, count);
    }

    // ==========================================================
    // LEFT PANEL (phases only)
    // ==========================================================

    private static class TrafficLightControlPanel extends JPanel {

        // MapView gibt uns "UI-Phasen" pro TL-ID (aus euren geparsten Programmen)
        private final MapView mapView;

        private final JComboBox<String> tlIdBox;

        // Live Labels (nur Anzeige)
        private final JLabel liveStateLabel;
        private final JLabel livePhaseLabel;
        private final JLabel liveProgramLabel;

        // Liste der "easy" Phasen
        private final DefaultListModel<PhaseItem> phaseListModel;
        private final JList<PhaseItem> phaseList;

        private final JButton btnApplyPhase;

        // Timer nur fürs UI (Labels refreshen)
        private final javax.swing.Timer uiTimer;

        // Letzte TraCI Snapshots vom Backend
        private List<TrafficLightSnapshot> lastSnapshots = new ArrayList<>();

        // Damit wir nicht jedes mal das ComboBox Model neu setzen wenn sich nichts
        // geändert hat
        private Set<String> lastIdSet = new LinkedHashSet<>();

        // Schutz: Wenn wir programmgesteuert tlIdBox ändern, sollen ActionEvents
        // ignoriert werden
        private boolean suppressComboEvents = false;

        TrafficLightControlPanel(MapView mapView) {
            super(new BorderLayout());
            this.mapView = mapView;

            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            JLabel title = new JLabel("Traffic Lights");
            title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));

            // -------------------------
            // Top Bereich: Auswahl + Live Info
            // -------------------------
            JPanel top = new JPanel();
            top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

            JPanel row1 = new JPanel(new BorderLayout(8, 8));
            row1.add(new JLabel("TL-ID:"), BorderLayout.WEST);

            tlIdBox = new JComboBox<>(new String[] {});
            row1.add(tlIdBox, BorderLayout.CENTER);

            top.add(title);
            top.add(Box.createVerticalStrut(10));
            top.add(row1);

            liveStateLabel = new JLabel("Live state: -");
            livePhaseLabel = new JLabel("Live phase: -");
            liveProgramLabel = new JLabel("Program: -");

            // kleine Schrift, weil links sonst zu voll wird
            liveStateLabel.setFont(liveStateLabel.getFont().deriveFont(13f));
            livePhaseLabel.setFont(livePhaseLabel.getFont().deriveFont(13f));
            liveProgramLabel.setFont(liveProgramLabel.getFont().deriveFont(13f));

            top.add(Box.createVerticalStrut(10));
            top.add(liveStateLabel);
            top.add(livePhaseLabel);
            top.add(liveProgramLabel);

            add(top, BorderLayout.NORTH);

            // -------------------------
            // Center Bereich: Phasenliste + Apply
            // -------------------------
            JPanel center = new JPanel();
            center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

            JLabel phasesLbl = new JLabel("Select a phase (easy):");
            phasesLbl.setFont(phasesLbl.getFont().deriveFont(Font.BOLD, 13f));

            center.add(phasesLbl);
            center.add(Box.createVerticalStrut(6));

            phaseListModel = new DefaultListModel<>();
            phaseList = new JList<>(phaseListModel);
            phaseList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

            JScrollPane sp = new JScrollPane(phaseList);
            sp.setPreferredSize(new Dimension(300, 230));

            center.add(sp);
            center.add(Box.createVerticalStrut(8));

            btnApplyPhase = new JButton("Apply selected phase");
            center.add(btnApplyPhase);

            add(center, BorderLayout.CENTER);

            // Button: setzt die gewählte Phase in SUMO (TraCI call)
            btnApplyPhase.addActionListener(e -> applySelectedPhase());

            // Wenn TL-ID geändert wird: Phasenliste neu aufbauen + Labels aktualisieren
            tlIdBox.addActionListener(e -> {
                if (suppressComboEvents)
                    return;
                rebuildPhaseList();
                updateLabelsFromCurrentSelection();
            });

            // Timer: Live Labels regelmäßig aktualisieren (auch wenn keine UI Interaktion
            // passiert)
            uiTimer = new javax.swing.Timer(600, e -> {
                if (isShowing())
                    updateLabelsFromCurrentSelection();
            });
            uiTimer.start();
        }

        // Stoppt den internen UI Timer (z.B. beim Fenster schließen)
        void stopInternalTimer() {
            try {
                uiTimer.stop();
            } catch (Throwable ignored) {
            }
        }

        // Wird vom MainWindow regelmäßig aufgerufen, wenn neue Snapshots da sind
        void updateFromSnapshot(List<TrafficLightSnapshot> snapshots) {
            if (snapshots == null)
                return;

            lastSnapshots = snapshots;

            // TL-IDs aus Snapshots sammeln
            Set<String> ids = new LinkedHashSet<>();
            for (TrafficLightSnapshot s : snapshots) {
                if (s != null && s.tlId != null)
                    ids.add(s.tlId);
            }

            // Nur wenn sich die ID-Menge geändert hat, bauen wir die ComboBox neu (sonst
            // flackert es)
            if (!ids.equals(lastIdSet)) {
                lastIdSet = ids;

                // aktuelle Auswahl merken, damit sie nach dem Model-Update gleich bleibt
                String current = getSelectedTlId();

                suppressComboEvents = true;
                try {
                    DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>(ids.toArray(new String[0]));
                    tlIdBox.setModel(model);

                    // Wenn die alte Auswahl noch existiert: wieder setzen
                    if (current != null && ids.contains(current)) {
                        tlIdBox.setSelectedItem(current);
                    } else if (model.getSize() > 0) {
                        // sonst einfach erstes Element auswählen
                        tlIdBox.setSelectedIndex(0);
                    }
                } finally {
                    suppressComboEvents = false;
                }

                // Wenn TL-Liste neu ist, müssen wir auch die Phase-Liste neu bauen
                rebuildPhaseList();
            }

            // Labels (state/phase/program) updaten
            updateLabelsFromCurrentSelection();
        }

        private void updateLabelsFromCurrentSelection() {
            TrafficLightSnapshot sel = findSnapshot(getSelectedTlId());

            if (sel != null) {
                liveStateLabel.setText("Live state: " + sel.state);
                livePhaseLabel.setText("Live phase: " + sel.phaseIndex);
                liveProgramLabel.setText("Program: " + (sel.programId != null ? sel.programId : "-"));
            } else {
                liveStateLabel.setText("Live state: -");
                livePhaseLabel.setText("Live phase: -");
                liveProgramLabel.setText("Program: -");
            }
        }

        private String getSelectedTlId() {
            Object o = tlIdBox.getSelectedItem();
            return (o instanceof String) ? (String) o : null;
        }

        private TrafficLightSnapshot findSnapshot(String tlId) {
            if (tlId == null)
                return null;

            for (TrafficLightSnapshot s : lastSnapshots) {
                if (s != null && tlId.equals(s.tlId))
                    return s;
            }
            return null;
        }

        private void rebuildPhaseList() {
            phaseListModel.clear();

            String tlId = getSelectedTlId();
            if (tlId == null || tlId.isBlank())
                return;

            try {
                // MapView liefert pro TL-ID eine Liste von UI-Phasen
                List<MapView.UiTlPhase> phases = mapView.getUiPhasesFor(tlId);

                if (phases == null || phases.isEmpty()) {
                    phaseListModel.addElement(new PhaseItem(-1, "(No phases for this TL-ID)", 0, ""));
                    return;
                }

                for (MapView.UiTlPhase p : phases) {
                    // Wir bauen einen “friendly” Namen:
                    // - Typ (Green/Yellow/Red/Mixed)
                    // - Dauer
                    // - Zählung wie viele G/Y/R im State String vorkommen
                    String friendly = buildFriendlyPhaseName(p.state, (int) p.durationSeconds);
                    phaseListModel.addElement(new PhaseItem(p.index, friendly, (int) p.durationSeconds, p.state));
                }

                // Default: erste Phase auswählen (damit der Apply Button direkt Sinn macht)
                if (phaseListModel.size() > 0)
                    phaseList.setSelectedIndex(0);

            } catch (Throwable t) {
                // Wenn getUiPhasesFor() gerade nicht kann (z.B. noch nicht geladen)
                phaseListModel.addElement(new PhaseItem(-1, "(Phase list unavailable: " + t.getMessage() + ")", 0, ""));
            }
        }

        private static String buildFriendlyPhaseName(String state, int durS) {
            if (state == null)
                state = "";

            // Wir zählen G/Y/R im state String, damit man als Mensch schnell sieht:
            // “wie viel Grün / Gelb / Rot steckt in dieser Phase”
            int g = 0, y = 0, r = 0, other = 0;
            for (int i = 0; i < state.length(); i++) {
                char c = state.charAt(i);

                if (c == 'G' || c == 'g')
                    g++;
                else if (c == 'y' || c == 'Y')
                    y++;
                else if (c == 'r' || c == 'R')
                    r++;
                else
                    other++;
            }

            // Grobe Klassifizierung (nicht perfekt, aber schnell verständlich)
            String type;
            if (y > 0 && g == 0)
                type = "Transition (YELLOW)";
            else if (g > 0 && y == 0)
                type = "Go (GREEN)";
            else if (r > 0 && g == 0 && y == 0)
                type = "Stop (RED)";
            else if (g > 0 && y > 0)
                type = "Mixed (GREEN+YELLOW)";
            else
                type = "Mixed";

            // Label enthält:
            // - Typ + Dauer
            // - Counts
            // - original state string (damit man exakt sieht was SUMO liefert)
            return type
                    + " | dur=" + durS + "s"
                    + " | G:" + g + " Y:" + y + " R:" + r
                    + (other > 0 ? (" ?:" + other) : "")
                    + " | state=" + state;
        }

        private void applySelectedPhase() {
            String tlId = getSelectedTlId();
            if (tlId == null) {
                JOptionPane.showMessageDialog(this, "Keine TL-ID ausgewählt.");
                return;
            }

            PhaseItem item = phaseList.getSelectedValue();
            if (item == null || item.phaseIndex < 0) {
                JOptionPane.showMessageDialog(this, "Bitte eine gültige Phase auswählen.");
                return;
            }

            try {
                // TraCI call: setzt die Phase direkt in SUMO
                TrafficLightServices.setPhase(tlId, item.phaseIndex);
            } catch (Throwable t) {
                JOptionPane.showMessageDialog(this, "Fehler beim Setzen der Phase:\n" + t.getMessage());
            }
        }

        private static class PhaseItem {
            final int phaseIndex;
            final String label;
            final int durationS;
            final String state;

            PhaseItem(int phaseIndex, String label, int durationS, String state) {
                this.phaseIndex = phaseIndex;
                this.label = label;
                this.durationS = durationS;
                this.state = state;
            }

            /**
             * Was in der JList angezeigt wird.
             * Für "Dummy"/Error Einträge (phaseIndex < 0) zeigen wir nur den Text.
             * Für echte Phasen: "Phase X — <friendly label>"
             */
            @Override
            public String toString() {
                if (phaseIndex < 0)
                    return label;
                return "Phase " + phaseIndex + " — " + label;
            }
        }
    }
}
