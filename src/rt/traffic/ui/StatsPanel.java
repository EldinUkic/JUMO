package rt.traffic.ui;

import javax.swing.*;
import java.awt.*;

/**
 * Zeigt Statistiken zur aktuellen Simulation an:
 * - Anzahl Lanes
 * - Anzahl Polygone
 * - Anzahl Junctions
 * - Anzahl Fahrzeuge
 * - Durchschnittsgeschwindigkeit
 * - (optional) Simulationszeit
 *
 * Die Werte werden über Setter-Methoden von außen aktualisiert.
 */
public class StatsPanel extends JPanel {

    private JLabel laneCountLabel;
    private JLabel polygonCountLabel;
    private JLabel junctionCountLabel;
    private JLabel vehicleCountLabel;
    private JLabel avgSpeedLabel;
    private JLabel simTimeLabel;

    public StatsPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setBackground(new Color(245, 245, 245));

        // Titel
        JLabel title = new JLabel("Simulation Stats");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        title.setAlignmentX(LEFT_ALIGNMENT);

        add(title);
        add(Box.createVerticalStrut(10));

        laneCountLabel = createStatLabel("Lanes: 0");
        polygonCountLabel = createStatLabel("Polygons: 0");
        junctionCountLabel = createStatLabel("Junctions: 0");
        vehicleCountLabel = createStatLabel("Vehicles: 0");
        avgSpeedLabel = createStatLabel("Avg Speed: 0.00 m/s");
        simTimeLabel = createStatLabel("Sim Time: 0.0 s");

        add(laneCountLabel);
        add(polygonCountLabel);
        add(junctionCountLabel);
        add(Box.createVerticalStrut(5));
        add(vehicleCountLabel);
        add(avgSpeedLabel);
        add(simTimeLabel);

        add(Box.createVerticalGlue());
    }

    private JLabel createStatLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setAlignmentX(LEFT_ALIGNMENT);
        lbl.setFont(lbl.getFont().deriveFont(13f));
        return lbl;
    }

    // ----- Setter, die von MapView/MainWindow aufgerufen werden -----

    public void setLaneCount(int count) {
        laneCountLabel.setText("Lanes: " + count);
    }

    public void setPolygonCount(int count) {
        polygonCountLabel.setText("Polygons: " + count);
    }

    public void setJunctionCount(int count) {
        junctionCountLabel.setText("Junctions: " + count);
    }

    public void setVehicleCount(int count) {
        vehicleCountLabel.setText("Vehicles: " + count);
    }

    /**
     * Durchschnittsgeschwindigkeit in m/s (oder was VehicleServices liefert).
     */
    public void setAverageSpeed(double speed) {
        avgSpeedLabel.setText(String.format("Avg Speed: %.2f m/s", speed));
    }

    /**
     * Simulationszeit in Sekunden. Aktuell noch ein Stub – du kannst später
     * aus dem Backend die tatsächliche Sim-Zeit setzen.
     */
    public void setSimTime(double timeSeconds) {
        simTimeLabel.setText(String.format("Sim Time: %.1f s", timeSeconds));
    }
}
