package rt.traffic.ui.set;

import javax.swing.*;
import java.awt.*;

import org.eclipse.sumo.libtraci.StringVector;
import org.eclipse.sumo.libtraci.TrafficLight;

public class TrafficLightSetPanel extends JPanel {

    private final DefaultListModel<String> listModel;
    private final JList<String> list;
    private final JButton reloadBtn;

    public TrafficLightSetPanel() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));

        reloadBtn = new JButton("Reload traffic lights");
        listModel = new DefaultListModel<>();
        list = new JList<>(listModel);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(reloadBtn);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(list), BorderLayout.CENTER);

        reloadBtn.addActionListener(e -> loadTrafficLights());

        loadTrafficLights();
    }

    private void loadTrafficLights() {
        listModel.clear();

        try {
            StringVector ids = TrafficLight.getIDList();

            if (ids.size() == 0) {
                listModel.addElement("<no traffic lights found>");
                return;
            }

            for (int i = 0; i < ids.size(); i++) {
                String id = ids.get(i);
                // Nummerierung: "1) id"
                listModel.addElement((i + 1) + ") " + id);
            }

        } catch (Error err) {
            listModel.addElement("<SUMO connection closed>");
            err.printStackTrace();
        } catch (Exception ex) {
            listModel.addElement("<error loading traffic lights>");
            ex.printStackTrace();

            JOptionPane.showMessageDialog(
                    this,
                    "Fehler beim Laden der Traffic Lights:\n" + ex.getMessage(),
                    "TrafficLight-Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }
}
