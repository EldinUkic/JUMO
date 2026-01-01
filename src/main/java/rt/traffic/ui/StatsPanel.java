package rt.traffic.ui;

import java.awt.Color;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * StatsPanel zeigt einfache Live-Zahlen zur Simulation an.
 *
 * Gedanke dahinter:
 * - Die GUI hat links die Map, oben die Buttons, und rechts diese kompakte
 * "Info-Spalte".
 * - StatsPanel macht selbst keine Berechnungen und liest keine Dateien.
 * - Es ist nur eine Anzeige, die von außen gefüttert wird:
 * - MapView setzt beim Laden z.B. Lane/Polygon/Junction Counts
 * - MainWindow setzt regelmäßig Vehicle Count, Avg Speed, Sim Time
 */
public class StatsPanel extends JPanel {

    // Diese Labels sind die "Anzeige-Felder".
    // Wir ändern später nur den Text (setText), die Labels bleiben dieselben
    // Objekte.
    private JLabel laneCountLabel;
    private JLabel polygonCountLabel;
    private JLabel junctionCountLabel;
    private JLabel vehicleCountLabel;
    private JLabel avgSpeedLabel;
    private JLabel simTimeLabel;

    /**
     * Baut das Panel-Layout einmalig auf:
     * - BoxLayout Y_AXIS: alles untereinander
     * - etwas Padding + helles Background
     * - Title oben
     * - darunter die Labels
     *
     * Danach wird nur noch per Setter der Text aktualisiert.
     */
    public StatsPanel() {
        // BoxLayout: vertikale Liste
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        // Innenabstand, damit nichts am Rand klebt
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // leichte Abhebung vom Map-Hintergrund
        setBackground(new Color(245, 245, 245));

        // Titel
        JLabel title = new JLabel("Simulation Stats");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        title.setAlignmentX(LEFT_ALIGNMENT);

        add(title);
        add(Box.createVerticalStrut(10)); // kleiner Abstand unter dem Titel

        // Startwerte sind 0, werden später von MapView/MainWindow überschrieben
        laneCountLabel = createStatLabel("Lanes: 0");
        polygonCountLabel = createStatLabel("Polygons: 0");
        junctionCountLabel = createStatLabel("Junctions: 0");
        vehicleCountLabel = createStatLabel("Vehicles: 0");
        avgSpeedLabel = createStatLabel("Avg Speed: 0.00 m/s");
        simTimeLabel = createStatLabel("Sim Time: 0.0 s");

        add(laneCountLabel);
        add(polygonCountLabel);
        add(junctionCountLabel);

        // Mini-Trennung: oben "Map-Daten", darunter "Live-Daten"
        add(Box.createVerticalStrut(5));

        add(vehicleCountLabel);
        add(avgSpeedLabel);
        add(simTimeLabel);

        // Glue schiebt alles nach oben, damit unten "Luft" bleibt
        add(Box.createVerticalGlue());
    }

    // Baut ein Label mit einheitlichem Stil:
    // - links ausgerichtet (damit alles sauber untereinander steht)
    // - etwas kleinere Schrift als der Titel
    private JLabel createStatLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setAlignmentX(LEFT_ALIGNMENT);
        lbl.setFont(lbl.getFont().deriveFont(13f));
        return lbl;
    }

    // ----- Setter, die von MapView/MainWindow aufgerufen werden -----

    /**
     * Setzt die Anzahl der Lanes (Fahrspuren).
     * Kommt typischerweise aus MapView nach dem Parsen der net.xml.
     */
    public void setLaneCount(int count) {
        laneCountLabel.setText("Lanes: " + count);
    }

    /**
     * Setzt die Anzahl der Polygone (z.B. Gebäude/Flächen aus poly.xml).
     * Kommt typischerweise aus MapView nach dem Parsen der poly.xml.
     */
    public void setPolygonCount(int count) {
        polygonCountLabel.setText("Polygons: " + count);
    }

    /**
     * Setzt die Anzahl der Junctions (Knoten/Kreuzungen).
     * Kommt typischerweise aus MapView nach dem Parsen der net.xml.
     */
    public void setJunctionCount(int count) {
        junctionCountLabel.setText("Junctions: " + count);
    }

    /**
     * Setzt die aktuelle Anzahl der Fahrzeuge.
     * Kommt typischerweise aus MainWindow (nach VehicleServices.getVehicleList()).
     */
    public void setVehicleCount(int count) {
        vehicleCountLabel.setText("Vehicles: " + count);
    }

    /**
     * Setzt die Durchschnittsgeschwindigkeit.
     *
     * Erwartung:
     * - speed ist in m/s (oder genau das, was VehicleServices liefert)
     *
     * Warum String.format:
     * - Wir begrenzen auf 2 Nachkommastellen, damit es ruhig aussieht und nicht
     * "flackert".
     */
    public void setAverageSpeed(double speed) {
        avgSpeedLabel.setText(String.format("Avg Speed: %.2f m/s", speed));
    }

    /**
     * Setzt die Simulationszeit in Sekunden.
     *
     * Erwartung:
     * - timeSeconds ist Sekunden (z.B. Simulation.getTime() von TraCI)
     *
     * Warum nur 1 Nachkommastelle:
     * - Reicht für Anzeige und wirkt stabiler, wenn wir häufig updaten.
     */
    public void setSimTime(double timeSeconds) {
        simTimeLabel.setText(String.format("Sim Time: %.1f s", timeSeconds));
    }
}
