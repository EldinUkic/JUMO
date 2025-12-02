package rt.traffic.ui;

import rt.traffic.ui.*;
import javax.swing.*;
import java.awt.*;

public class MainWindow extends JFrame {

    private MapView MapView; // meine Kartenansicht, die das Straßennetz (OSM) zeichnet
    private StatsPanel StatsPanel; // rechts angezeigtes Statistik-Panel
    private JButton toggleButton; // Button zum Ein-/Ausblenden des Statistikbereichs

    public MainWindow() {
        super("Real-Time Traffic Simulation"); // Fenstertitel setzen

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); // Fenster schließen = Programm beenden
        setLayout(new BorderLayout()); // Layout: Norden, Süden, Westen, Osten, Mitte

        // ----- Set -----

        SetWindow SetWindow = new SetWindow();
        SetWindow.setPreferredSize(new Dimension(260, 0)); // gleiche Breite wie rechts
        add(SetWindow, BorderLayout.WEST);

        // ----- MapView -----
        // MapView ist meine Hauptanzeige, die mit osm.net.xml und osm.poly.xml
        // arbeitet.
        // Wird später in der Mitte angezeigt.
        MapView = new MapView();

        // ----- StatsPanel -----
        // Das Statistikpanel kommt rechts hin und bekommt eine feste Breite.
        StatsPanel = new StatsPanel();
        StatsPanel.setPreferredSize(new Dimension(260, 0)); // Breite 260px, Höhe egal
        add(StatsPanel, BorderLayout.EAST); // rechts ans Fenster anhängen

        // MapView bekommt Zugriff auf StatsPanel, damit ich Live-Daten aktualisieren
        // kann
        MapView.setStatsPanel(StatsPanel);

        // ----- Obere Leiste (Toolbar) -----
        // Hier baue ich oben eine kleine Leiste mit einem Button zum Ein-/Ausblenden
        // der Statistiken.
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT)); // Button links ausrichten

        toggleButton = new JButton("Hide stats"); // Startzustand: Stats sind sichtbar
        toggleButton.addActionListener(e -> toggleStats()); // Beim Klick -> toggleStats() ausführen
        topBar.add(toggleButton);

        add(topBar, BorderLayout.NORTH); // Oben im Fenster hinzufügen

        // ----- MapView in die Mitte -----
        // Das ist meine Hauptgrafikfläche (Straßen, Polygone, Fahrzeuge…)
        add(MapView, BorderLayout.CENTER);

        // Fenstergröße einstellen
        setSize(1220, 780);
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
