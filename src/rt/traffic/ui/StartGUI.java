package rt.traffic.ui;

import javax.swing.SwingUtilities;

/**
 * Separater Einstiegspunkt für die reine GUI.
 * Wird später in AppMain eingebunden, sodass SUMO + GUI parallel starten.
 */
public class StartGUI {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            MainWindow w = new MainWindow();
            w.setLocationRelativeTo(null);
            w.setVisible(true);
        });
    }
}
