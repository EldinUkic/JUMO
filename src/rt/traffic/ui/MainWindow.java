package rt.traffic.ui;

import rt.traffic.backend.Sim;
import rt.traffic.backend.traciServices.VehicleServices;
import rt.traffic.backend.traciServices.VehicleServices.SpawnRequest;
import rt.traffic.backend.traciServices.TrafficLightServices;
import rt.traffic.backend.traciServices.TrafficLightServices.TrafficLightSnapshot;

// Analytics
import rt.traffic.application.analytics.AnalyticsExecution;
import rt.traffic.application.analytics.Metrics;
import rt.traffic.application.analytics.TrafficTracking;
import rt.traffic.application.analytics.VehicleTracking;

// SUMO / TraCI
import org.eclipse.sumo.libtraci.Simulation;
import org.eclipse.sumo.libtraci.Lane;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.ListSelectionModel;

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

public class MainWindow extends JFrame {

    private final Sim sim;

    private final MapView mapView;
    private final StatsPanel statsPanel;

    private final JButton toggleStatsButton;

    private final JButton toggleTlPanelButton;
    private final JPanel tlPanelWrapper;
    private final TrafficLightControlPanel tlControlPanel;

    // Export
    private final JButton exportMetricsButton;
    private final AnalyticsExecution analytics = new AnalyticsExecution();

    private final javax.swing.Timer vehicleTimer;

    private boolean traciReady = false;
    private long lastLogMs = 0;

    public MainWindow(Sim sim) {
        super("Real-Time Traffic Simulation");
        this.sim = sim;

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setLayout(new BorderLayout());
        setSize(1220, 780);

        // CENTER: Map
        mapView = new MapView();
        add(mapView, BorderLayout.CENTER);

        // RIGHT: Stats
        statsPanel = new StatsPanel();
        statsPanel.setPreferredSize(new Dimension(260, 0));
        add(statsPanel, BorderLayout.EAST);
        mapView.setStatsPanel(statsPanel);

        // LEFT: Traffic Light Control
        tlControlPanel = new TrafficLightControlPanel(mapView);

        tlPanelWrapper = new JPanel(new BorderLayout());
        tlPanelWrapper.setPreferredSize(new Dimension(340, 0));
        tlPanelWrapper.add(tlControlPanel, BorderLayout.CENTER);
        add(tlPanelWrapper, BorderLayout.WEST);

        // TOP BAR
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        add(topBar, BorderLayout.NORTH);

        JButton btnStart = new JButton("Start");
        btnStart.addActionListener(e -> safeCall("sim.play()", sim::play));

        JButton btnStop = new JButton("Stop");
        btnStop.addActionListener(e -> safeCall("sim.pause()", sim::pause));

        JButton btnStep = new JButton("Step");
        btnStep.addActionListener(e -> safeCall("Simulation.step()", () -> {
            Simulation.step();
            VehicleServices.vehiclePull();
            updateVehiclesSafely();
        }));

        JButton btnSpawn = new JButton("Spawn...");
        btnSpawn.addActionListener(e -> openSpawnDialog());

        // Export Metrics (PDF + CSV)
        exportMetricsButton = new JButton("Export metrics (PDF + CSV)");
        exportMetricsButton.addActionListener(e -> exportMetricsPdfAndCsv());

        toggleTlPanelButton = new JButton("Hide TL panel");
        toggleTlPanelButton.addActionListener(e -> toggleTlPanel());

        toggleStatsButton = new JButton("Hide stats");
        toggleStatsButton.addActionListener(e -> toggleStats());

        topBar.add(btnStart);
        topBar.add(btnStop);
        topBar.add(btnStep);
        topBar.add(btnSpawn);
        topBar.add(Box.createHorizontalStrut(10));
        topBar.add(exportMetricsButton);
        topBar.add(Box.createHorizontalStrut(10));
        topBar.add(toggleTlPanelButton);
        topBar.add(toggleStatsButton);

        // LIVE UPDATE TIMER
        vehicleTimer = new javax.swing.Timer(150, e -> updateVehiclesSafely());
        vehicleTimer.start();

        // WINDOW CLOSE
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                System.out.println("[UI] Window closing → shutting down simulation");

                try { if (vehicleTimer.isRunning()) vehicleTimer.stop(); } catch (Throwable ignored) {}
                try { tlControlPanel.stopInternalTimer(); } catch (Throwable ignored) {}

                try { sim.shutdown(); } catch (Throwable t) {
                    System.err.println("[UI] Error during sim.shutdown(): " + t.getMessage());
                }

                dispose();
                System.exit(0);
            }
        });
    }

    private boolean ensureTraciReady() {
        if (traciReady) return true;
        try {
            Simulation.getTime();
            traciReady = true;
            System.out.println("[UI] TraCI connected -> live updates enabled");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private void updateVehiclesSafely() {
        if (!ensureTraciReady()) return;

        try {
            // Vehicles
            VehicleServices.vehiclePull();
            List<VehicleServices> vehicles = VehicleServices.getVehicleList();

            Map<String, Point2D.Double> positions = new HashMap<>();
            for (VehicleServices v : vehicles) {
                positions.put(v.id, new Point2D.Double(v.px, v.py));
            }
            mapView.updateVehiclePositions(positions);

            statsPanel.setVehicleCount(vehicles.size());
            statsPanel.setAverageSpeed(VehicleServices.getAverageSpeed());
            try { statsPanel.setSimTime(Simulation.getTime()); } catch (Throwable ignored) {}

            // Traffic lights
            pushLiveTrafficLightStatesToMapAndPanel();

        } catch (Throwable t) {
            String msg = String.valueOf(t.getMessage()).toLowerCase();
            if (msg.contains("not connected")) traciReady = false;

            long now = System.currentTimeMillis();
            if (now - lastLogMs > 1500) {
                lastLogMs = now;
                System.err.println("[UI] Live update failed: " + t);
            }
        }
    }

    private void pushLiveTrafficLightStatesToMapAndPanel() {
        try {
            TrafficLightServices.trafficLightPull();
            List<TrafficLightSnapshot> tls = TrafficLightServices.getTrafficLightList();

            Map<String, String> liveStates = new LinkedHashMap<>();
            for (TrafficLightSnapshot s : tls) {
                if (s != null && s.tlId != null && s.state != null) {
                    liveStates.put(s.tlId, s.state);
                }
            }

            mapView.setLiveTrafficLightStates(liveStates);
            tlControlPanel.updateFromSnapshot(tls);

        } catch (Throwable ignored) {}
    }

    // ==========================================================
    // Export Metrics (PDF + CSV)  ✅ fixed IOException handling
    // ==========================================================
    private void exportMetricsPdfAndCsv() {
        safeCall("Export metrics", () -> {
            if (!ensureTraciReady()) {
                JOptionPane.showMessageDialog(this,
                        "TraCI noch nicht verbunden.\nStarte die Simulation kurz und versuche es erneut.");
                return;
            }

            TrafficTracking tracking = buildTrafficTrackingFromBackend();

            try {
                Metrics metrics = analytics.executeMetrics(tracking);

                // beide Exporte (werfen bei euch IOException)
                metrics.exportToPdf();
                metrics.exportToCsv();

                JOptionPane.showMessageDialog(this,
                        "Metrics exportiert:\n- PDF\n- CSV",
                        "Export erfolgreich",
                        JOptionPane.INFORMATION_MESSAGE);

            } catch (java.io.IOException io) {
                io.printStackTrace();
                JOptionPane.showMessageDialog(this,
                        "Export fehlgeschlagen (IO):\n" + io.getMessage(),
                        "Export-Fehler",
                        JOptionPane.ERROR_MESSAGE);

            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this,
                        "Export fehlgeschlagen:\n" + ex.getMessage(),
                        "Export-Fehler",
                        JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private static TrafficTracking buildTrafficTrackingFromBackend() {
        // Sim time
        double simTime = 0.0;
        try { simTime = Simulation.getTime(); } catch (Throwable ignored) {}

        // Vehicles -> VehicleTracking
        List<VehicleTracking> vehicles = new ArrayList<>();
        try {
            for (VehicleServices v : VehicleServices.getVehicleList()) {
                vehicles.add(new VehicleTracking(v.id, v.edgeId, v.speed));
            }
        } catch (Throwable ignored) {}

        // Edge lengths (meters)
        Map<String, Double> edgeLengths = new HashMap<>();
        for (VehicleTracking v : vehicles) {
            String edgeId = v.edgeId;
            if (edgeId == null || edgeId.isEmpty() || edgeLengths.containsKey(edgeId)) continue;

            double len = 100.0; // fallback
            try {
                // lane id: edgeId_0 usually exists
                len = Lane.getLength(edgeId + "_0");
            } catch (Throwable ignored) {}

            edgeLengths.put(edgeId, len);
        }

        return new TrafficTracking(simTime, vehicles, edgeLengths);
    }

    // ==========================================================
    // UI helpers
    // ==========================================================
    private void safeCall(String label, Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            t.printStackTrace();
            JOptionPane.showMessageDialog(
                    this,
                    "Fehler bei: " + label + "\n\n" + t.getMessage(),
                    "Fehler",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void toggleStats() {
        boolean visible = statsPanel.isVisible();
        statsPanel.setVisible(!visible);
        toggleStatsButton.setText(visible ? "Show stats" : "Hide stats");
        revalidate();
        repaint();
    }

    private void toggleTlPanel() {
        boolean visible = tlPanelWrapper.isVisible();
        tlPanelWrapper.setVisible(!visible);
        toggleTlPanelButton.setText(visible ? "Show TL panel" : "Hide TL panel");
        revalidate();
        repaint();
    }

    private void openSpawnDialog() {
        List<String> routes;
        try {
            routes = VehicleServices.getAllRouteIds();
        } catch (Throwable t) {
            JOptionPane.showMessageDialog(this, "Routen nicht verfügbar: " + t.getMessage());
            return;
        }

        if (routes.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Keine Routen gefunden.");
            return;
        }

        JComboBox<String> routeBox = new JComboBox<>(routes.toArray(new String[0]));
        JSpinner countSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 500, 1));

        JPanel panel = new JPanel(new GridLayout(2, 2));
        panel.add(new JLabel("Route:"));
        panel.add(routeBox);
        panel.add(new JLabel("Anzahl:"));
        panel.add(countSpinner);

        if (JOptionPane.showConfirmDialog(
                this, panel, "Fahrzeuge spawnen",
                JOptionPane.OK_CANCEL_OPTION
        ) != JOptionPane.OK_OPTION) return;

        String routeId = (String) routeBox.getSelectedItem();
        int count = (Integer) countSpinner.getValue();

        String typeId = VehicleServices.getAnyVehicleTypeId();
        if (typeId.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Kein VehicleType gefunden.");
            return;
        }

        for (int i = 0; i < count; i++) {
            VehicleServices.queueSpawn(
                    new SpawnRequest(
                            "gui_" + routeId + "_" + System.nanoTime(),
                            routeId,
                            typeId,
                            -1
                    )
            );
        }
    }

    // ==========================================================
    // LEFT PANEL (phases only)
    // ==========================================================
    private static class TrafficLightControlPanel extends JPanel {

        private final MapView mapView;

        private final JComboBox<String> tlIdBox;
        private final JLabel liveStateLabel;
        private final JLabel livePhaseLabel;
        private final JLabel liveProgramLabel;

        private final DefaultListModel<PhaseItem> phaseListModel;
        private final JList<PhaseItem> phaseList;
        private final JButton btnApplyPhase;

        private final javax.swing.Timer uiTimer;

        private List<TrafficLightSnapshot> lastSnapshots = new ArrayList<>();

        private Set<String> lastIdSet = new LinkedHashSet<>();
        private boolean suppressComboEvents = false;

        TrafficLightControlPanel(MapView mapView) {
            super(new BorderLayout());
            this.mapView = mapView;

            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            JLabel title = new JLabel("Traffic Lights");
            title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));

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

            liveStateLabel.setFont(liveStateLabel.getFont().deriveFont(13f));
            livePhaseLabel.setFont(livePhaseLabel.getFont().deriveFont(13f));
            liveProgramLabel.setFont(liveProgramLabel.getFont().deriveFont(13f));

            top.add(Box.createVerticalStrut(10));
            top.add(liveStateLabel);
            top.add(livePhaseLabel);
            top.add(liveProgramLabel);

            add(top, BorderLayout.NORTH);

            // Center
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

            btnApplyPhase.addActionListener(e -> applySelectedPhase());

            tlIdBox.addActionListener(e -> {
                if (suppressComboEvents) return;
                rebuildPhaseList();
                updateLabelsFromCurrentSelection();
            });

            uiTimer = new javax.swing.Timer(600, e -> {
                if (isShowing()) updateLabelsFromCurrentSelection();
            });
            uiTimer.start();
        }

        void stopInternalTimer() {
            try { uiTimer.stop(); } catch (Throwable ignored) {}
        }

        void updateFromSnapshot(List<TrafficLightSnapshot> snapshots) {
            if (snapshots == null) return;
            lastSnapshots = snapshots;

            Set<String> ids = new LinkedHashSet<>();
            for (TrafficLightSnapshot s : snapshots) {
                if (s != null && s.tlId != null) ids.add(s.tlId);
            }

            if (!ids.equals(lastIdSet)) {
                lastIdSet = ids;

                String current = getSelectedTlId();

                suppressComboEvents = true;
                try {
                    DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>(ids.toArray(new String[0]));
                    tlIdBox.setModel(model);

                    if (current != null && ids.contains(current)) {
                        tlIdBox.setSelectedItem(current);
                    } else if (model.getSize() > 0) {
                        tlIdBox.setSelectedIndex(0);
                    }
                } finally {
                    suppressComboEvents = false;
                }

                rebuildPhaseList();
            }

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
            if (tlId == null) return null;
            for (TrafficLightSnapshot s : lastSnapshots) {
                if (s != null && tlId.equals(s.tlId)) return s;
            }
            return null;
        }

        private void rebuildPhaseList() {
            phaseListModel.clear();

            String tlId = getSelectedTlId();
            if (tlId == null || tlId.isBlank()) return;

            try {
                List<MapView.UiTlPhase> phases = mapView.getUiPhasesFor(tlId);
                if (phases == null || phases.isEmpty()) {
                    phaseListModel.addElement(new PhaseItem(-1, "(No phases for this TL-ID)", 0, ""));
                    return;
                }

                for (MapView.UiTlPhase p : phases) {
                    String friendly = buildFriendlyPhaseName(p.state, (int) p.durationSeconds);
                    phaseListModel.addElement(new PhaseItem(p.index, friendly, (int) p.durationSeconds, p.state));
                }

                if (phaseListModel.size() > 0) phaseList.setSelectedIndex(0);

            } catch (Throwable t) {
                phaseListModel.addElement(new PhaseItem(-1, "(Phase list unavailable: " + t.getMessage() + ")", 0, ""));
            }
        }

        private static String buildFriendlyPhaseName(String state, int durS) {
            if (state == null) state = "";
            int g = 0, y = 0, r = 0, other = 0;

            for (int i = 0; i < state.length(); i++) {
                char c = state.charAt(i);
                if (c == 'G' || c == 'g') g++;
                else if (c == 'y' || c == 'Y') y++;
                else if (c == 'r' || c == 'R') r++;
                else other++;
            }

            String type;
            if (y > 0 && g == 0) type = "Transition (YELLOW)";
            else if (g > 0 && y == 0) type = "Go (GREEN)";
            else if (r > 0 && g == 0 && y == 0) type = "Stop (RED)";
            else if (g > 0 && y > 0) type = "Mixed (GREEN+YELLOW)";
            else type = "Mixed";

            return type + " | dur=" + durS + "s"
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

            @Override
            public String toString() {
                if (phaseIndex < 0) return label;
                return "Phase " + phaseIndex + " — " + label;
            }
        }
    }
}
