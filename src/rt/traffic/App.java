package rt.traffic;

import javax.swing.SwingUtilities;
import org.eclipse.sumo.libtraci.Simulation;
import rt.traffic.ui.*;

public class App {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            StartWindow w = new StartWindow();
            w.setLocationRelativeTo(null);
            w.setVisible(true);
        });
    }
}