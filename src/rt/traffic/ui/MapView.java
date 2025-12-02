package rt.traffic.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import traffic.infrastructure.sumo.SumoPath;
import org.w3c.dom.*;

import java.io.File;
import java.util.*;

/**
 * Zeichnet das Netz aus osm.net.xml und die Polygone aus osm.poly.xml.
 * (Hier mache ich also die komplette grafische Darstellung des
 * SUMO-Kartennetzes)
 */

public class MapView extends JPanel {

    // Liste aller Fahrspurlinien (jede Lane ist eine Liste von Punkten)
    private final java.util.List<java.util.List<Point2D.Double>> laneShapes = new ArrayList<>();

    // Liste aller Polygone (Gebäude, Flächen etc.)
    private final java.util.List<java.util.List<Point2D.Double>> polygonShapes = new ArrayList<>();

    // Liste aller Junction-Punkte (für Marker)
    private final java.util.List<Point2D.Double> junctionPoints = new ArrayList<>();

    // Minimale und maximale Koordinaten → brauche ich für Skalierung auf das
    // Fenster
    private double minX = Double.POSITIVE_INFINITY;
    private double maxX = Double.NEGATIVE_INFINITY;
    private double minY = Double.POSITIVE_INFINITY;
    private double maxY = Double.NEGATIVE_INFINITY;

    // Referenz auf StatsPanel, um Werte wie Lane-Anzahl usw. direkt zu setzen
    private StatsPanel StatsPanel;

    public MapView() {

        // Hintergrundfarbe (leichtes Grau)
        setBackground(new Color(250, 250, 250));

        try {
            // Dateien werden hier direkt geladen.
            // Pfade sind momentan absolute Pfade aus meinem lokalen Projekt.
            loadNetFile(SumoPath.netPath);
            loadPolyFile(SumoPath.polyPath);

            // Zur Kontrolle auf der Konsole
            System.out.println("Loaded lanes: " + laneShapes.size());
            System.out.println("Loaded polygons: " + polygonShapes.size());
            System.out.println("Loaded junctions: " + junctionPoints.size());
        } catch (Exception e) {
            e.printStackTrace(); // falls XML kaputt oder nicht gefunden
        }
    }

    public void setStatsPanel(StatsPanel StatsPanel) {
        this.StatsPanel = StatsPanel;

        // Direkt nach Laden einmal die Werte ins Panel schreiben
        if (StatsPanel != null) {
            StatsPanel.setLaneCount(laneShapes.size());
            StatsPanel.setPolygonCount(polygonShapes.size());
            StatsPanel.setJunctionCount(junctionPoints.size());
            StatsPanel.setVehicleCount(0); // noch kein Simulationscode, daher 0
            StatsPanel.setSimTime(0.0); // gleiche Idee: Startzeit = 0
        }
    }

    // Aktualisiert die min/max Bounds, damit ich später das Zeichnen skalieren kann
    private void updateBounds(double x, double y) {
        if (x < minX)
            minX = x;
        if (x > maxX)
            maxX = x;
        if (y < minY)
            minY = y;
        if (y > maxY)
            maxY = y;
    }

    // Lädt die SUMO-Netzdatei — hier kommen die Straßen und Junctions her
    private void loadNetFile(String path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new File(path));
        doc.getDocumentElement().normalize();

        // Alle <edge> Elemente — enthalten Lanes
        NodeList edgeNodes = doc.getElementsByTagName("edge");
        for (int i = 0; i < edgeNodes.getLength(); i++) {
            Node node = edgeNodes.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE)
                continue;
            Element edgeElem = (Element) node;

            // Interne Kanten (SUMO generiert die für Logik, aber wir wollen sie NICHT
            // zeichnen)
            if (edgeElem.hasAttribute("function")) {
                if ("internal".equals(edgeElem.getAttribute("function"))) {
                    continue; // interne → überspringen
                }
            }

            // Jetzt alle Lanes dieser Edge
            NodeList laneNodes = edgeElem.getElementsByTagName("lane");
            for (int j = 0; j < laneNodes.getLength(); j++) {
                Element laneElem = (Element) laneNodes.item(j);
                String shapeAttr = laneElem.getAttribute("shape");

                if (shapeAttr == null || shapeAttr.isEmpty())
                    continue;

                // shape="x1,y1 x2,y2 x3,y3 ..." → hier parsen
                String[] pairs = shapeAttr.trim().split(" ");
                java.util.List<Point2D.Double> polyline = new ArrayList<>();

                for (String pair : pairs) {
                    String[] xy = pair.split(",");
                    if (xy.length != 2)
                        continue;
                    double x = Double.parseDouble(xy[0]);
                    double y = Double.parseDouble(xy[1]);

                    polyline.add(new Point2D.Double(x, y));

                    // Für spätere Skalierung aufnehmen
                    updateBounds(x, y);
                }

                // Nur Linien mit mindestens 2 Punkten zeichne ich
                if (polyline.size() >= 2) {
                    laneShapes.add(polyline);
                }
            }
        }

        // Junctions (Knotenpunkte im SUMO, werden als Punkte gezeichnet)
        NodeList juncNodes = doc.getElementsByTagName("junction");
        for (int i = 0; i < juncNodes.getLength(); i++) {
            Node node = juncNodes.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE)
                continue;
            Element juncElem = (Element) node;

            // Falls Koordinaten fehlen → ignorieren
            if (!juncElem.hasAttribute("x") || !juncElem.hasAttribute("y"))
                continue;

            double x = Double.parseDouble(juncElem.getAttribute("x"));
            double y = Double.parseDouble(juncElem.getAttribute("y"));

            junctionPoints.add(new Point2D.Double(x, y));

            // Bounds aktualisieren
            updateBounds(x, y);
        }
    }

    // Lädt die Polygon-Datei (Gebäude etc.)
    private void loadPolyFile(String path) throws Exception {
        File f = new File(path);
        if (!f.exists()) {
            System.out.println("Poly file not found: " + path);
            return; // keine Polygone → trotzdem weiterzeichnen
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(f);
        doc.getDocumentElement().normalize();

        NodeList polyNodes = doc.getElementsByTagName("poly");
        for (int i = 0; i < polyNodes.getLength(); i++) {
            Node node = polyNodes.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE)
                continue;

            Element polyElem = (Element) node;
            String shapeAttr = polyElem.getAttribute("shape");

            if (shapeAttr == null || shapeAttr.isEmpty())
                continue;

            // shape="x1,y1 x2,y2 ..." parsen
            String[] pairs = shapeAttr.trim().split(" ");
            java.util.List<Point2D.Double> poly = new ArrayList<>();

            for (String pair : pairs) {
                String[] xy = pair.split(",");
                if (xy.length != 2)
                    continue;

                double x = Double.parseDouble(xy[0]);
                double y = Double.parseDouble(xy[1]);

                poly.add(new Point2D.Double(x, y));
            }

            // Polygone brauchen mindestens 3 Punkte
            if (poly.size() >= 3) {
                polygonShapes.add(poly);
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        // Falls nie Bounds gesetzt wurden → gar nichts zeichnen
        if (minX == Double.POSITIVE_INFINITY) {
            return;
        }

        Graphics2D g2 = (Graphics2D) g;

        // Kantenglättung (Anti-Aliasing)
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        int width = getWidth();
        int height = getHeight();

        // Weltgrößen (SUMO-Koordinaten)
        double worldW = maxX - minX;
        double worldH = maxY - minY;

        // Verhältnis zwischen Welt und Bildschirm
        double scaleX = width / worldW;
        double scaleY = height / worldH;

        // Gleicher Maßstab für beide Achsen → nichts verzerren
        double scale = Math.min(scaleX, scaleY);

        // Zentrierung: Offset berechnen
        double offsetX = (width - worldW * scale) / 2.0;
        double offsetY = (height - worldH * scale) / 2.0;

        // Hilfsfunktion für Weltpunkt → Bildpunkt
        java.util.function.Function<Point2D.Double, Point2D.Double> toScreen = p -> {
            double sx = (p.x - minX) * scale + offsetX;

            // Wichtig: Y-Achse ist invertiert, weil Java oben = 0 ist
            double sy = height - ((p.y - minY) * scale + offsetY);

            return new Point2D.Double(sx, sy);
        };

        // ----- 1) Polygone (Gebäude) -----
        g2.setColor(new Color(180, 210, 180)); // dezentes Grün
        for (java.util.List<Point2D.Double> poly : polygonShapes) {
            Path2D path = new Path2D.Double();
            boolean first = true;

            for (Point2D.Double p : poly) {
                Point2D.Double sp = toScreen.apply(p);

                if (first) {
                    path.moveTo(sp.x, sp.y);
                    first = false;
                } else {
                    path.lineTo(sp.x, sp.y);
                }
            }

            path.closePath();
            g2.fill(path);
        }

        // ----- 2) Straßen (Lanes) -----
        g2.setColor(Color.DARK_GRAY);
        g2.setStroke(new BasicStroke(2.0f)); // Straßenbreite 2px

        for (java.util.List<Point2D.Double> lane : laneShapes) {
            Path2D path = new Path2D.Double();
            boolean first = true;

            for (Point2D.Double p : lane) {
                Point2D.Double sp = toScreen.apply(p);

                if (first) {
                    path.moveTo(sp.x, sp.y);
                    first = false;
                } else {
                    path.lineTo(sp.x, sp.y);
                }
            }

            g2.draw(path);
        }

        // ----- 3) Junction-Punkte (gelbe Punkte) -----
        g2.setColor(Color.YELLOW);
        for (Point2D.Double jp : junctionPoints) {
            Point2D.Double sp = toScreen.apply(jp);
            int r = 6; // Radius

            g2.fillOval((int) (sp.x - r / 2.0), (int) (sp.y - r / 2.0), r, r);
        }

        // ----- Optional: erste Kreuzung rot hervorheben -----
        if (!junctionPoints.isEmpty()) {
            Point2D.Double sp = toScreen.apply(junctionPoints.get(0));
            int r = 8;
            g2.setColor(Color.RED);
            g2.fillOval((int) (sp.x - r / 2.0), (int) (sp.y - r / 2.0), r, r);
        }
    }
}
