package rt.traffic.ui;

import javax.swing.*;
import java.awt.*;
import rt.traffic.ui.set.TrafficLightSetPanel;
import rt.traffic.ui.set.VehicleSetPanel;

public class SetWindow extends JPanel {

    private JButton trafficHeader;
    private JButton vehicleHeader;
    private JPanel trafficContent;
    private JPanel vehicleContent;

    public SetWindow() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        // Abschnitt 1: Traffic Lights
        trafficHeader = createSectionHeader("Traffic Lights");
        trafficContent = new JPanel(new BorderLayout());
        trafficContent.add(new TrafficLightSetPanel(), BorderLayout.CENTER);

        // Abschnitt 2: Vehicles
        vehicleHeader = createSectionHeader("Vehicles");
        vehicleContent = new JPanel(new BorderLayout());
        vehicleContent.add(new VehicleSetPanel(), BorderLayout.CENTER);

        // Standard: Traffic auf, Vehicles zu
        setSectionExpanded(trafficHeader, trafficContent, true);
        setSectionExpanded(vehicleHeader, vehicleContent, false);

        // Header-Logik
        trafficHeader.addActionListener(e -> toggleSection(trafficHeader, trafficContent));
        vehicleHeader.addActionListener(e -> toggleSection(vehicleHeader, vehicleContent));

        // Alles nacheinander einbauen (wie Outline / Timeline / Java Projects)
        add(trafficHeader);
        add(trafficContent);
        add(Box.createVerticalStrut(5)); // kleiner Abstand
        add(vehicleHeader);
        add(vehicleContent);
    }

    private JButton createSectionHeader(String title) {
        JButton btn = new JButton("▾ " + title);
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setFont(btn.getFont().deriveFont(Font.BOLD, 12f));
        return btn;
    }

    private void setSectionExpanded(JButton header, JPanel content, boolean expanded) {
        content.setVisible(expanded);
        if (expanded) {
            header.setText("▾ " + header.getText().substring(2)); // Pfeil runter
        } else {
            header.setText("▸ " + header.getText().substring(2)); // Pfeil rechts
        }
    }

    private void toggleSection(JButton header, JPanel content) {
        boolean nowVisible = !content.isVisible();
        setSectionExpanded(header, content, nowVisible);
        revalidate();
        repaint();
    }
}
