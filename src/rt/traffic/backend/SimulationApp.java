package rt.traffic.backend;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import traffic.infrastructure.sumo.*;
import rt.traffic.ui.*;

public class SimulationApp {

    public void run() {

        SumoBackend backend = null;

        try {
            boolean useGUI = false; // nur Backend starten

            System.out.println("========== SimulationApp Start ==========");
            System.out.println("Config: " + SumoPath.cfgPath);
            System.out.println("=========================================\n");

            // ===== BACKEND STARTEN =====
            try {
                backend = new SumoBackend(SumoPath.cfgPath, useGUI);
                backend.start();
                System.out.println("[INFO] SUMO-Backend gestartet.\n");
            } catch (Exception backendError) {
                showError("Backend-Fehler",
                        "Fehler beim Starten des SUMO-Backends:\n" + backendError.getMessage());
                backendError.printStackTrace();
                return;
            }
            // ===== GUI STARTEN =====
            SwingUtilities.invokeLater(() -> {
                try {
                    MainWindow w = new MainWindow();
                    w.setLocationRelativeTo(null);
                    w.setVisible(true);
                } catch (Exception guiError) {
                    showError("GUI-Fehler",
                            "Fehler beim Starten der GUI:\n" + guiError.getMessage());
                    guiError.printStackTrace();
                }
            });

            System.out.println("[INFO] Simulation läuft (GUI gestartet)...\n");
            Thread.sleep(Long.MAX_VALUE);

        } catch (InterruptedException e) {

            System.out.println("\n[INFO] Programm unterbrochen");
            Thread.currentThread().interrupt();

        } catch (Exception e) {

            showError("Unerwarteter Fehler",
                    "Es ist ein unerwarteter Fehler aufgetreten:\n" + e.getMessage());
            e.printStackTrace();

        } finally {

            if (backend != null) {
                System.out.println("\n[INFO] Fahre Backend herunter...");
                try {
                    backend.shutdown();
                } catch (Exception shutdownError) {
                    shutdownError.printStackTrace();
                }
                System.out.println("[INFO] Backend beendet.");
            }
        }
    }

    private static void showError(String title, String message) {
        JOptionPane.showMessageDialog(
                null,
                message,
                title,
                JOptionPane.ERROR_MESSAGE);
    }
}
