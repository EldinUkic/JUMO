package rt.traffic.ui;

import rt.traffic.backend.Sim;
import rt.traffic.backend.traciServices.VehicleServices;
import rt.traffic.backend.traciServices.VehicleServices.SpawnRequest;

import org.eclipse.sumo.libtraci.Simulation;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainWindow extends JFrame {

    private final Sim sim;         // Referenz auf die Simulation (Start, Stop, Step)
    private MapView MapView;       // Kartenansicht (Straßennetz + Fahrzeuge)
    private StatsPanel StatsPanel; // rechts angezeigtes Statistik-Panel
    private JButton toggleButton;  // Button zum Ein-/Ausblenden des Statistikbereichs

    // GUI-Update-Timer (wie oft Fahrzeuge & Stats aktualisiert werden)
    private javax.swing.Timer vehicleTimer;

    public MainWindow(Sim sim) {
        super("Real-Time Traffic Simulation"); // Fenstertitel setzen
        this.sim = sim;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); // Fenster schließen = Programm beenden
        setLayout(new BorderLayout()); // Layout: Norden, Süden, Westen, Osten, Mitte


        // ----- MapView -----
        // MapView ist meine Hauptanzeige, die mit osm.net.xml und osm.poly.xml
        // arbeitet.
        // Wird später in der Mitte angezeigt.
        MapView = new MapView();

        // ----- StatsPanel (rechts) -----
        StatsPanel = new StatsPanel();
        StatsPanel.setPreferredSize(new Dimension(260, 0)); // Breite 260px, Höhe egal
        add(StatsPanel, BorderLayout.EAST); // rechts ans Fenster anhängen

        // MapView bekommt Zugriff auf StatsPanel, damit Basiswerte gesetzt werden
        MapView.setStatsPanel(StatsPanel);

        // ----- Obere Leiste (Toolbar) -----
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        add(topBar, BorderLayout.NORTH);

        // === Steuerungs-Buttons wie in SUMO: Start / Stop / Step ===

        // Start: Auto-Loop / Simulation laufen lassen
        JButton btnStart = new JButton("Start");
        btnStart.addActionListener(e -> sim.play());

        // Stop: Auto-Loop anhalten
        JButton btnStop = new JButton("Stop");
        btnStop.addActionListener(e -> sim.pause());

        // Step: Ein einzelner Simulationsschritt
        JButton btnStep = new JButton("Step");
        btnStep.addActionListener(e -> {
            try {
                // Ein Simulationsschritt in SUMO
                Simulation.step();
                // Direkt danach die Vehicle-Liste aktualisieren
                VehicleServices.vehiclePull();
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(
                        this,
                        "Fehler bei Simulation.step():\n" + ex.getMessage(),
                        "Step-Fehler",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        });

        // Fahrzeuge spawnen (GUI-Dialog)
        JButton btnSpawn = new JButton("Spawn...");
        btnSpawn.addActionListener(e -> openSpawnDialog());

        // Hide/Show Stats
        toggleButton = new JButton("Hide stats"); // Startzustand: Stats sind sichtbar
        toggleButton.addActionListener(e -> toggleStats());

        // Buttons in die Toolbar einfügen
        topBar.add(btnStart);
        topBar.add(btnStop);
        topBar.add(btnStep);
        topBar.add(btnSpawn);
        topBar.add(Box.createHorizontalStrut(10));
        topBar.add(toggleButton);

        // ----- MapView in die Mitte -----
        add(MapView, BorderLayout.CENTER);

        // ----- Live-Update für Fahrzeuge & Stats -----
        int initialDelay = 150;
        vehicleTimer = new javax.swing.Timer(initialDelay, e -> {
            // Aktuelle Fahrzeugdaten aus dem Backend holen
            List<VehicleServices> vehicles = VehicleServices.getVehicleList();

            // Map von ID -> Position (px, py) für die MapView bauen
            Map<String, Point2D.Double> positions = new HashMap<>();
            for (VehicleServices v : vehicles) {
                positions.put(v.id, new Point2D.Double(v.px, v.py));
            }

            // Fahrzeugpositionen in der MapView aktualisieren
            MapView.updateVehiclePositions(positions);

            if (StatsPanel != null) {
                // Fahrzeuganzahl
                StatsPanel.setVehicleCount(vehicles.size());
                // Durchschnittsgeschwindigkeit
                double avgSpeed = VehicleServices.getAverageSpeed();
                StatsPanel.setAverageSpeed(avgSpeed);
                // SimTime könnt ihr später aus dem Backend setzen
                // StatsPanel.setSimTime(...);
            }
        });
        vehicleTimer.start();

        // Fenstergröße einstellen
        setSize(1220, 780);
    }

    // GUI-Dialog zum Spawnen von Vehicles (statt Terminal-Menü)
    private void openSpawnDialog() {
        // Routen aus SUMO holen
        java.util.List<String> routes = VehicleServices.getAllRouteIds();
        if (routes.isEmpty()) {
            JOptionPane.showMessageDialog(
                    this,
                    "Keine Routen gefunden. Ist eine .rou-Datei in SUMO geladen?",
                    "Keine Routen",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        // UI-Komponenten für den Dialog
        JComboBox<String> routeBox = new JComboBox<>(routes.toArray(new String[0]));
        routeBox.setSelectedIndex(0);

        double currentFactor = VehicleServices.getSpawnRateFactor();

        JSpinner rateSpinner = new JSpinner(
                new SpinnerNumberModel(currentFactor, 0.1, 10.0, 0.1)
        );

        JSpinner countSpinner = new JSpinner(
                new SpinnerNumberModel(5, 1, 500, 1)
        );

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("Route:"), gbc);

        gbc.gridx = 1;
        panel.add(routeBox, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        panel.add(new JLabel("Spawnrate-Faktor:"), gbc);

        gbc.gridx = 1;
        panel.add(rateSpinner, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        panel.add(new JLabel("Anzahl Fahrzeuge:"), gbc);

        gbc.gridx = 1;
        panel.add(countSpinner, gbc);

        int result = JOptionPane.showConfirmDialog(
                this,
                panel,
                "Fahrzeuge spawnen",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        // Eingaben auslesen
        String routeId = (String) routeBox.getSelectedItem();
        double factor = ((Number) rateSpinner.getValue()).doubleValue();
        int count = ((Number) countSpinner.getValue()).intValue();

        // Spawnrate setzen (wird in VehicleServices begrenzt)
        VehicleServices.setSpawnRateFactor(factor);

        // VehicleType aus SUMO holen
        String typeId = VehicleServices.getAnyVehicleTypeId();
        if (typeId.isEmpty()) {
            JOptionPane.showMessageDialog(
                    this,
                    "Kein gültiger VehicleType in SUMO gefunden.\nAbbruch.",
                    "Fehler",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        // SpawnRequests in die Queue legen
        for (int i = 0; i < count; i++) {
            String vehId = "gui_" + routeId + "_" + System.currentTimeMillis() + "_" + i;
            SpawnRequest req = new SpawnRequest(vehId, routeId, typeId, -1);
            VehicleServices.queueSpawn(req);
        }

        JOptionPane.showMessageDialog(
                this,
                "Es wurden " + count + " Fahrzeuge in die Spawn-Queue gelegt.",
                "Spawn erfolgreich",
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    // Diese Methode blendet rechts das Statistikpanel ein/aus
    private void toggleStats() {
        boolean visible = StatsPanel.isVisible(); // merken, ob es gerade sichtbar ist
        StatsPanel.setVisible(!visible); // Sichtbarkeit umschalten

        // Buttontext anpassen (wenn Panel vorher sichtbar war → jetzt "Show stats")
        toggleButton.setText(visible ? "Show stats" : "Hide stats");

        // Layout neu berechnen & neu zeichnen, damit das GUI korrekt aktualisiert wird
        revalidate();
        repaint();
    }
}
