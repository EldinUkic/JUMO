package rt.traffic.ui;

import rt.traffic.backend.*;
import javax.swing.*;
import java.awt.*;

public class StartWindow extends JFrame {

    private final JButton btnStartDefault;
    private final JButton btnUploadMap;

    public StartWindow() {
        // Titel + Basis
        setTitle("Traffic Simulation Launcher");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(400, 200);
        setLocationRelativeTo(null); // zentrieren

        // Layout
        setLayout(new BorderLayout());

        // Panel für Buttons
        JPanel buttonPanel = new JPanel();
        // GridLayout(Zeilen, Spalten, HorizontalAbstand, VertikalAbstand)
        buttonPanel.setLayout(new GridLayout(2, 1, 10, 10));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Buttons erstellen
        btnStartDefault = new JButton("Start default map");
        btnUploadMap = new JButton("Upload map (coming soon)");

        // Upload-Button ausgrauen
        btnUploadMap.setEnabled(false);

        // Buttons ins Panel
        buttonPanel.add(btnStartDefault);
        buttonPanel.add(btnUploadMap);

        // Panel ins Fenster
        add(buttonPanel, BorderLayout.CENTER);

        // Events
        initActions();
    }

    private void initActions() {
        // Wenn auf "Start default map" geklickt wird
        btnStartDefault.addActionListener(e -> {

            // 1. Start-Fenster schließen
            dispose();

            // 2. SimulationApp NICHT im UI-Thread laufen lassen
            new Thread(() -> {
                try {
                    SimulationApp app = new SimulationApp();
                    app.run();
                } catch (Exception ex) {
                    ex.printStackTrace();
                    // Fehler anzeigen (ohne Fenster als Parent)
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(
                                null,
                                "Fehler beim Starten der Simulation:\n" + ex.getMessage(),
                                "Error",
                                JOptionPane.ERROR_MESSAGE);
                    });
                }
            }, "SimulationApp-Thread").start();
        });
    }
}
