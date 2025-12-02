package rt.traffic.ui.set;

import javax.swing.*;
import java.awt.*;

public class VehicleSetPanel extends JPanel {

    public VehicleSetPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));

        JLabel label = new JLabel("Vehicle controls coming soon...");
        label.setHorizontalAlignment(SwingConstants.CENTER);

        add(label, BorderLayout.CENTER);
    }
}
