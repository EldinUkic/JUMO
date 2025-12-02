package rt.traffic.ui;

import javax.swing.*;
import java.awt.*;

public class StatsPanel extends JPanel {

    // Diese Labels zeigen die Zahlen aus dem MapView bzw. später aus der Simulation
    // an
    private JLabel lanesLabel; // Anzahl der Lanes (Straßenstücke)
    private JLabel polysLabel; // Anzahl der gezeichneten Polygone
    private JLabel junctionsLabel; // Anzahl der Junctions (Knotenpunkte)
    private JLabel vehiclesLabel; // Anzahl der Fahrzeuge (kommt später aus TraaS/SUMO)
    private JLabel simTimeLabel; // aktuelle Simulationszeit

    public StatsPanel() {
        // Hintergrundfarbe: leichtes Grau für klare Abgrenzung zur Map
        setBackground(new Color(245, 245, 245));

        // Layout für geordnete vertikale Darstellung
        setLayout(new GridBagLayout());

        // Layout-Konfigurator für jede einzelne Zeile
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0; // Alle Labels in Spalte 0
        gbc.anchor = GridBagConstraints.WEST; // Links ausgerichtet
        gbc.insets = new Insets(4, 20, 4, 10); // Außenabstände

        // Titel am oberen Rand ("Statistics") – fett und größer
        JLabel title = new JLabel("Statistics");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));

        gbc.gridy = 0; // oberste Position
        gbc.insets = new Insets(20, 20, 10, 10); // extra Abstand über dem Titel
        add(title, gbc);

        // Danach wieder normale Abstände für alle anderen Labels
        gbc.insets = new Insets(4, 20, 4, 10);

        // Alle Labels mit Startwerten (werden durch MapView gesetzt)
        lanesLabel = new JLabel("Lanes: 0");
        polysLabel = new JLabel("Polygons: 0");
        junctionsLabel = new JLabel("Junctions: 0");
        vehiclesLabel = new JLabel("Vehicles: 0");
        simTimeLabel = new JLabel("Sim Time: 0.0s");

        // Alle Labels nacheinander einfügen, jeweils eine Zeile tiefer
        gbc.gridy = 1;
        add(lanesLabel, gbc);
        gbc.gridy = 2;
        add(polysLabel, gbc);
        gbc.gridy = 3;
        add(junctionsLabel, gbc);
        gbc.gridy = 4;
        add(vehiclesLabel, gbc);
        gbc.gridy = 5;
        add(simTimeLabel, gbc);
    }

    // Wird von MapView oder später von der Simulation aufgerufen
    public void setLaneCount(int n) {
        lanesLabel.setText("Lanes: " + n);
    }

    public void setPolygonCount(int n) {
        polysLabel.setText("Polygons: " + n);
    }

    public void setJunctionCount(int n) {
        junctionsLabel.setText("Junctions: " + n);
    }

    // Platzhalter – echte Fahrzeugdaten kommen später über SUMO/TraaS
    public void setVehicleCount(int n) {
        vehiclesLabel.setText("Vehicles: " + n);
    }

    // Zeigt die aktuelle Simulationszeit in Sekunden mit 1 Nachkommastelle
    public void setSimTime(double seconds) {
        simTimeLabel.setText(String.format("Sim Time: %.1fs", seconds));
    }
}
