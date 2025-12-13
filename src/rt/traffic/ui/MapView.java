package rt.traffic.ui;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import traffic.infrastructure.sumo.SumoPath;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * MapView:
 * - Zeichnet SUMO-Netz (osm.net.xml) + Polygone (osm.poly.xml)
 * - Zoom & Pan (Mausrad / Drag)
 * - Straßenfläche pro Lane (asphalt) -> zoomstabil (Stroke in Meter)
 * - NUR gestrichelte Innenlinien zwischen Fahrspuren (Dash in Meter)
 * - Haltelinien:
 *      * zoomstabil (Länge/Offset in Meter)
 *      * für JEDE Lane, die in osm.net.xml eine Connection mit tl/linkIndex hat
 *      * Farbe kommt bevorzugt aus LIVE TraCI-State (via MainWindow -> setLiveTrafficLightStates)
 *      * Fallback: UI-only tlLogic Animation (wenn Live nicht gesetzt)
 */
public class MapView extends JPanel {

    // =========================
    // Datenklassen
    // =========================
    private static class LaneData {
        final String laneId;
        final String edgeId;
        final int laneIndex;
        final java.util.List<Point2D.Double> pointsWorld;

        LaneData(String laneId, String edgeId, int laneIndex, java.util.List<Point2D.Double> pointsWorld) {
            this.laneId = laneId;
            this.edgeId = edgeId;
            this.laneIndex = laneIndex;
            this.pointsWorld = pointsWorld;
        }
    }

    private static class TlPhase {
        final double durS;
        final String state;
        TlPhase(double durS, String state) { this.durS = durS; this.state = state; }
    }

    private static class LinkSig {
        final String tlId;
        final int linkIndex;
        LinkSig(String tlId, int linkIndex) { this.tlId = tlId; this.linkIndex = linkIndex; }
    }

    // ==========================================================
    // ✅ Public UI-API für Phasen (für MainWindow)
    // ==========================================================
    public static class UiTlPhase {
        public final int index;
        public final double durationSeconds;
        public final String state;

        public UiTlPhase(int index, double durationSeconds, String state) {
            this.index = index;
            this.durationSeconds = durationSeconds;
            this.state = state;
        }
    }

    /** Liefert Phasen (Index, Dauer, State) für eine tlLogic-ID aus der NET-Datei. */
    public java.util.List<UiTlPhase> getUiPhasesFor(String tlId) {
        java.util.List<UiTlPhase> out = new java.util.ArrayList<>();
        java.util.List<TlPhase> phases = tlPhases.get(tlId);
        if (phases == null) return out;

        for (int i = 0; i < phases.size(); i++) {
            TlPhase p = phases.get(i);
            out.add(new UiTlPhase(i, p.durS, p.state));
        }
        return out;
    }

    // =========================
    // Map data
    // =========================
    private final Map<String, java.util.List<LaneData>> lanesByEdge = new HashMap<>();
    private final java.util.List<java.util.List<Point2D.Double>> polygonShapes = new ArrayList<>();
    private final java.util.List<Point2D.Double> junctionPoints = new ArrayList<>();
    private final java.util.List<java.util.List<Point2D.Double>> junctionPolygons = new ArrayList<>();

    // Fahrzeuge
    private final Map<String, Point2D.Double> vehiclePositions = new HashMap<>();
    private final Map<String, Point2D.Double> lastVehiclePositions = new HashMap<>();
    private final Map<String, Double> vehicleAngles = new HashMap<>();

    // Bounds
    private double minX = Double.POSITIVE_INFINITY;
    private double maxX = Double.NEGATIVE_INFINITY;
    private double minY = Double.POSITIVE_INFINITY;
    private double maxY = Double.NEGATIVE_INFINITY;

    private StatsPanel statsPanel;

    // =========================
    // Traffic Light mapping & UI phases
    // =========================
    private final Map<String, LinkSig> laneToSignal = new HashMap<>();
    private final Map<String, java.util.List<TlPhase>> tlPhases = new HashMap<>();
    private final Map<String, Integer> tlPhaseIndex = new HashMap<>();
    private final Map<String, Double> tlPhaseRemaining = new HashMap<>();
    private Timer tlAnimTimer;

    // ✅ LIVE TraCI states: tlId -> "rGrG..."
    private final Map<String, String> liveTlStates = new HashMap<>();
    private boolean useLiveTlStates = false;

    /** Wird aus MainWindow regelmäßig gesetzt (Snapshot aus TraCI). */
    public void setLiveTrafficLightStates(Map<String, String> tlIdToState) {
        liveTlStates.clear();
        if (tlIdToState != null) liveTlStates.putAll(tlIdToState);
        useLiveTlStates = (tlIdToState != null && !tlIdToState.isEmpty());
        repaint();
    }

    // =========================
    // Zoom & Pan
    // =========================
    private double zoomFactor = 1.0;
    private static final double MIN_ZOOM = 0.2;
    private static final double MAX_ZOOM = 5.0;

    private double panX = 0.0;
    private double panY = 0.0;
    private Point lastDragPoint = null;

    // =========================
    // VISUELLE PARAMETER (WELT in Metern)
    // =========================
    private static final double LANE_WIDTH_M = 3.2;
    private static final double INNER_LINE_WIDTH_M = 0.18;

    private static final double DASH_ON_M  = 6.0;
    private static final double DASH_OFF_M = 6.0;

    private static final double STOPLINE_WIDTH_M = 0.25;
    private static final double STOPLINE_OFFSET_M = 0.7;
    private static final double STOPLINE_LENGTH_M = 3.5;

    // =========================
    // FAHRZEUG-GRÖßE (WELT in Metern)
    // =========================
    private static final double VEHICLE_LENGTH_M = 4.5;
    private static final double VEHICLE_WIDTH_M  = 2.0;

    public MapView() {
        setBackground(new Color(250, 250, 250));

        try {
            loadNetFile(SumoPath.netPath);
            loadPolyFile(SumoPath.polyPath);

            int laneCount = lanesByEdge.values().stream().mapToInt(java.util.List::size).sum();
            System.out.println("Loaded lanes: " + laneCount);
            System.out.println("Loaded edges: " + lanesByEdge.size());
            System.out.println("Loaded polygons: " + polygonShapes.size());
            System.out.println("Loaded junction polygons: " + junctionPolygons.size());
            System.out.println("Loaded tlLogics: " + tlPhases.size());
            System.out.println("Lane->Signal mappings: " + laneToSignal.size());
        } catch (Exception e) {
            e.printStackTrace();
        }

        initMouseControls();
        startTrafficLightAnimation(); // Fallback-Animation
    }

    // -----------------------------
    // Mouse controls
    // -----------------------------
    private void initMouseControls() {
        addMouseWheelListener((MouseWheelEvent e) -> {
            int rotation = e.getWheelRotation();

            double oldZoom = zoomFactor;
            if (rotation < 0) zoomFactor *= 1.1;
            else if (rotation > 0) zoomFactor /= 1.1;

            if (zoomFactor < MIN_ZOOM) zoomFactor = MIN_ZOOM;
            if (zoomFactor > MAX_ZOOM) zoomFactor = MAX_ZOOM;

            if (Math.abs(zoomFactor - oldZoom) > 1e-6) repaint();
        });

        MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) lastDragPoint = e.getPoint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                lastDragPoint = null;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (lastDragPoint != null && SwingUtilities.isLeftMouseButton(e)) {
                    Point p = e.getPoint();
                    int dx = p.x - lastDragPoint.x;
                    int dy = p.y - lastDragPoint.y;

                    panX += dx;
                    panY += dy;

                    lastDragPoint = p;
                    repaint();
                }
            }
        };

        addMouseListener(mouseAdapter);
        addMouseMotionListener(mouseAdapter);
    }

    // -----------------------------
    // Stats hook
    // -----------------------------
    public void setStatsPanel(StatsPanel statsPanel) {
        this.statsPanel = statsPanel;

        if (statsPanel != null) {
            int laneCount = lanesByEdge.values().stream().mapToInt(java.util.List::size).sum();
            statsPanel.setLaneCount(laneCount);
            statsPanel.setPolygonCount(polygonShapes.size());
            statsPanel.setJunctionCount(junctionPoints.size());
            statsPanel.setVehicleCount(0);
            statsPanel.setSimTime(0.0);
        }
    }

    // -----------------------------
    // Vehicles update
    // -----------------------------
    public void updateVehiclePositions(Map<String, Point2D.Double> newPositions) {
        Set<String> stillThere = new HashSet<>(newPositions.keySet());

        lastVehiclePositions.keySet().removeIf(id -> !stillThere.contains(id));
        vehicleAngles.keySet().removeIf(id -> !stillThere.contains(id));

        for (Map.Entry<String, Point2D.Double> entry : newPositions.entrySet()) {
            String id = entry.getKey();
            Point2D.Double current = entry.getValue();
            Point2D.Double prev = lastVehiclePositions.get(id);

            if (prev != null) {
                double dxWorld = current.x - prev.x;
                double dyWorld = current.y - prev.y;
                double len2 = dxWorld * dxWorld + dyWorld * dyWorld;

                if (len2 > 1e-8) {
                    double angleScreen = Math.atan2(-dyWorld, dxWorld);
                    vehicleAngles.put(id, angleScreen);
                }
            } else {
                vehicleAngles.putIfAbsent(id, 0.0);
            }

            lastVehiclePositions.put(id, current);
        }

        vehiclePositions.clear();
        vehiclePositions.putAll(newPositions);
        repaint();
    }

    // =========================
    // Traffic light fallback animation
    // =========================
    private void startTrafficLightAnimation() {
        if (tlAnimTimer != null) tlAnimTimer.stop();
        if (tlPhases.isEmpty()) return;

        tlAnimTimer = new Timer(100, e -> {
            advanceTrafficLights(0.1);
            repaint();
        });
        tlAnimTimer.start();
    }

    private void advanceTrafficLights(double dt) {
        for (Map.Entry<String, java.util.List<TlPhase>> entry : tlPhases.entrySet()) {
            String tlId = entry.getKey();
            java.util.List<TlPhase> phases = entry.getValue();
            if (phases == null || phases.isEmpty()) continue;

            int idx = tlPhaseIndex.getOrDefault(tlId, 0);
            idx = Math.max(0, Math.min(idx, phases.size() - 1));

            double rem = tlPhaseRemaining.getOrDefault(tlId, phases.get(idx).durS);
            rem -= dt;

            while (rem <= 0.0 && !phases.isEmpty()) {
                idx = (idx + 1) % phases.size();
                rem += phases.get(idx).durS;
            }

            tlPhaseIndex.put(tlId, idx);
            tlPhaseRemaining.put(tlId, rem);
        }
    }

    // ✅ Color aus LIVE TraCI State (wenn vorhanden), sonst Fallback aus tlLogic-Animation
    private Color getSignalColor(String tlId, int linkIndex) {
        String liveState = useLiveTlStates ? liveTlStates.get(tlId) : null;
        if (liveState != null && !liveState.isEmpty() && linkIndex >= 0 && linkIndex < liveState.length()) {
            return colorForStateChar(liveState.charAt(linkIndex));
        }

        java.util.List<TlPhase> phases = tlPhases.get(tlId);
        if (phases == null || phases.isEmpty()) return new Color(200, 200, 200);

        int idx = tlPhaseIndex.getOrDefault(tlId, 0);
        idx = Math.max(0, Math.min(idx, phases.size() - 1));

        String state = phases.get(idx).state;
        if (state == null || state.isEmpty()) return new Color(200, 200, 200);
        if (linkIndex < 0 || linkIndex >= state.length()) return new Color(200, 200, 200);

        return colorForStateChar(state.charAt(linkIndex));
    }

    private Color colorForStateChar(char s) {
        switch (s) {
            case 'G':
            case 'g':
                return Color.GREEN;
            case 'y':
            case 'Y':
                return Color.YELLOW;
            case 'r':
            case 'R':
                return Color.RED;
            default:
                return new Color(200, 200, 200);
        }
    }

    // -----------------------------
    // Helpers
    // -----------------------------
    private void updateBounds(double x, double y) {
        if (x < minX) minX = x;
        if (x > maxX) maxX = x;
        if (y < minY) minY = y;
        if (y > maxY) maxY = y;
    }

    private static java.util.List<Point2D.Double> parseShapePoints(String shapeAttr) {
        java.util.List<Point2D.Double> pts = new ArrayList<>();
        if (shapeAttr == null || shapeAttr.isEmpty()) return pts;

        String[] pairs = shapeAttr.trim().split(" ");
        for (String pair : pairs) {
            String[] xy = pair.split(",");
            if (xy.length != 2) continue;
            double x = Double.parseDouble(xy[0]);
            double y = Double.parseDouble(xy[1]);
            pts.add(new Point2D.Double(x, y));
        }
        return pts;
    }

    private static String extractEdgeIdFromLaneId(String laneId) {
        int idx = laneId.lastIndexOf('_');
        if (idx <= 0) return laneId;
        return laneId.substring(0, idx);
    }

    private static int extractLaneIndexFromLaneId(String laneId) {
        int idx = laneId.lastIndexOf('_');
        if (idx < 0 || idx == laneId.length() - 1) return 0;
        try {
            return Integer.parseInt(laneId.substring(idx + 1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static Path2D toPath(java.util.List<Point2D.Double> pts) {
        Path2D path = new Path2D.Double();
        boolean first = true;
        for (Point2D.Double p : pts) {
            if (first) {
                path.moveTo(p.x, p.y);
                first = false;
            } else {
                path.lineTo(p.x, p.y);
            }
        }
        return path;
    }

    private static java.util.List<Point2D.Double> midline(java.util.List<Point2D.Double> a, java.util.List<Point2D.Double> b) {
        int n = Math.min(a.size(), b.size());
        java.util.List<Point2D.Double> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(new Point2D.Double(
                    (a.get(i).x + b.get(i).x) * 0.5,
                    (a.get(i).y + b.get(i).y) * 0.5
            ));
        }
        return out;
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private void drawStopLineWorld(
            Graphics2D g2,
            Point2D.Double p1World,
            Point2D.Double p2World,
            java.util.function.Function<Point2D.Double, Point2D.Double> toScreen
    ) {
        double dx = p2World.x - p1World.x;
        double dy = p2World.y - p1World.y;
        double len = Math.hypot(dx, dy);
        if (len < 1e-6) return;

        double ux = dx / len;
        double uy = dy / len;

        double cx = p2World.x - ux * STOPLINE_OFFSET_M;
        double cy = p2World.y - uy * STOPLINE_OFFSET_M;

        double nx = -uy;
        double ny = ux;

        double half = STOPLINE_LENGTH_M / 2.0;

        Point2D.Double aWorld = new Point2D.Double(cx + nx * half, cy + ny * half);
        Point2D.Double bWorld = new Point2D.Double(cx - nx * half, cy - ny * half);

        Point2D.Double a = toScreen.apply(aWorld);
        Point2D.Double b = toScreen.apply(bWorld);

        g2.draw(new Line2D.Double(a, b));
    }

    // -----------------------------
    // Loading
    // -----------------------------
    private void loadNetFile(String path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new File(path));
        doc.getDocumentElement().normalize();

        // 1) Edges & Lanes
        NodeList edgeNodes = doc.getElementsByTagName("edge");
        for (int i = 0; i < edgeNodes.getLength(); i++) {
            Node node = edgeNodes.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) continue;
            Element edgeElem = (Element) node;

            // internal edges nicht zeichnen (nur echte Straßen)
            if (edgeElem.hasAttribute("function") && "internal".equals(edgeElem.getAttribute("function"))) {
                continue;
            }

            NodeList laneNodes = edgeElem.getElementsByTagName("lane");
            for (int j = 0; j < laneNodes.getLength(); j++) {
                Element laneElem = (Element) laneNodes.item(j);

                String laneId = laneElem.getAttribute("id");
                if (laneId == null || laneId.isEmpty()) continue;

                String shapeAttr = laneElem.getAttribute("shape");
                if (shapeAttr == null || shapeAttr.isEmpty()) continue;

                java.util.List<Point2D.Double> polyline = parseShapePoints(shapeAttr);
                if (polyline.size() < 2) continue;

                for (Point2D.Double p : polyline) updateBounds(p.x, p.y);

                String edgeId = extractEdgeIdFromLaneId(laneId);
                int laneIndex = extractLaneIndexFromLaneId(laneId);

                LaneData ld = new LaneData(laneId, edgeId, laneIndex, polyline);
                lanesByEdge.computeIfAbsent(edgeId, k -> new ArrayList<>()).add(ld);
            }
        }

        for (java.util.List<LaneData> list : lanesByEdge.values()) {
            list.sort(Comparator.comparingInt(a -> a.laneIndex));
        }

        // 2) Junction polygons
        NodeList juncNodes = doc.getElementsByTagName("junction");
        for (int i = 0; i < juncNodes.getLength(); i++) {
            Node node = juncNodes.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) continue;
            Element juncElem = (Element) node;

            if (juncElem.hasAttribute("x") && juncElem.hasAttribute("y")) {
                double x = Double.parseDouble(juncElem.getAttribute("x"));
                double y = Double.parseDouble(juncElem.getAttribute("y"));
                junctionPoints.add(new Point2D.Double(x, y));
                updateBounds(x, y);
            }

            String type = juncElem.getAttribute("type");
            if ("internal".equals(type)) continue;

            if (juncElem.hasAttribute("shape")) {
                java.util.List<Point2D.Double> poly = parseShapePoints(juncElem.getAttribute("shape"));
                for (Point2D.Double p : poly) updateBounds(p.x, p.y);
                if (poly.size() >= 3) junctionPolygons.add(poly);
            }
        }

        // 3) tlLogic -> Phasen
        NodeList tlNodes = doc.getElementsByTagName("tlLogic");
        for (int i = 0; i < tlNodes.getLength(); i++) {
            Node n = tlNodes.item(i);
            if (n.getNodeType() != Node.ELEMENT_NODE) continue;
            Element tl = (Element) n;

            String tlId = tl.getAttribute("id");
            if (tlId == null || tlId.isEmpty()) continue;

            java.util.List<TlPhase> phases = new ArrayList<>();
            NodeList phaseNodes = tl.getElementsByTagName("phase");
            for (int p = 0; p < phaseNodes.getLength(); p++) {
                Node pn = phaseNodes.item(p);
                if (pn.getNodeType() != Node.ELEMENT_NODE) continue;
                Element ph = (Element) pn;

                String durStr = ph.getAttribute("duration");
                String state = ph.getAttribute("state");
                if (durStr == null || durStr.isEmpty()) continue;
                if (state == null || state.isEmpty()) continue;

                double dur = Double.parseDouble(durStr);
                phases.add(new TlPhase(dur, state));
            }

            if (!phases.isEmpty()) {
                tlPhases.put(tlId, phases);
                tlPhaseIndex.put(tlId, 0);
                tlPhaseRemaining.put(tlId, phases.get(0).durS);
            }
        }

        // 4) connections -> tl/linkIndex Mapping pro fromLane
        NodeList connNodes = doc.getElementsByTagName("connection");
        for (int i = 0; i < connNodes.getLength(); i++) {
            Node n = connNodes.item(i);
            if (n.getNodeType() != Node.ELEMENT_NODE) continue;
            Element c = (Element) n;

            if (!c.hasAttribute("tl") || !c.hasAttribute("linkIndex")) continue;

            String tlId = c.getAttribute("tl");
            String fromEdge = c.getAttribute("from");
            String fromLaneStr = c.getAttribute("fromLane");
            String linkIndexStr = c.getAttribute("linkIndex");

            if (tlId == null || tlId.isEmpty()) continue;
            if (fromEdge == null || fromEdge.isEmpty()) continue;
            if (fromLaneStr == null || fromLaneStr.isEmpty()) continue;
            if (linkIndexStr == null || linkIndexStr.isEmpty()) continue;

            int fromLane;
            int linkIndex;
            try {
                fromLane = Integer.parseInt(fromLaneStr);
                linkIndex = Integer.parseInt(linkIndexStr);
            } catch (NumberFormatException ex) {
                continue;
            }

            String laneId = fromEdge + "_" + fromLane;
            laneToSignal.putIfAbsent(laneId, new LinkSig(tlId, linkIndex));
        }
    }

    private void loadPolyFile(String path) throws Exception {
        File f = new File(path);
        if (!f.exists()) {
            System.out.println("Poly file not found: " + path);
            return;
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(f);
        doc.getDocumentElement().normalize();

        NodeList polyNodes = doc.getElementsByTagName("poly");
        for (int i = 0; i < polyNodes.getLength(); i++) {
            Node node = polyNodes.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) continue;

            Element polyElem = (Element) node;
            String shapeAttr = polyElem.getAttribute("shape");
            if (shapeAttr == null || shapeAttr.isEmpty()) continue;

            java.util.List<Point2D.Double> poly = parseShapePoints(shapeAttr);
            if (poly.size() >= 3) polygonShapes.add(poly);
        }
    }

    // -----------------------------
    // Paint
    // -----------------------------
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (minX == Double.POSITIVE_INFINITY) return;

        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int width = getWidth();
        int height = getHeight();

        double worldW = maxX - minX;
        double worldH = maxY - minY;

        double scaleX = width / worldW;
        double scaleY = height / worldH;

        double baseScale = Math.min(scaleX, scaleY);
        final double scale = baseScale * zoomFactor;

        double offsetX = (width - worldW * scale) / 2.0;
        double offsetY = (height - worldH * scale) / 2.0;

        final java.util.function.Function<Point2D.Double, Point2D.Double> toScreen = p -> {
            double sx = (p.x - minX) * scale + offsetX + panX;
            double sy = height - ((p.y - minY) * scale + offsetY) + panY;
            return new Point2D.Double(sx, sy);
        };

        float laneStrokePx  = (float) (LANE_WIDTH_M * scale);
        float innerStrokePx = (float) (INNER_LINE_WIDTH_M * scale);
        float stopStrokePx  = (float) (STOPLINE_WIDTH_M * scale);

        laneStrokePx  = clamp(laneStrokePx, 1.5f, 40.0f);
        innerStrokePx = clamp(innerStrokePx, 0.8f, 8.0f);
        stopStrokePx  = clamp(stopStrokePx, 1.0f, 10.0f);

        float vehLenPx = (float) (VEHICLE_LENGTH_M * scale);
        float vehWidPx = (float) (VEHICLE_WIDTH_M  * scale);

        vehLenPx = clamp(vehLenPx, 4.0f, 40.0f);
        vehWidPx = clamp(vehWidPx, 2.5f, 22.0f);

        // 1) Polygone
        g2.setColor(new Color(180, 210, 180));
        for (java.util.List<Point2D.Double> poly : polygonShapes) {
            Path2D path = new Path2D.Double();
            boolean first = true;
            for (Point2D.Double p : poly) {
                Point2D.Double sp = toScreen.apply(p);
                if (first) { path.moveTo(sp.x, sp.y); first = false; }
                else { path.lineTo(sp.x, sp.y); }
            }
            path.closePath();
            g2.fill(path);
        }

        // 2) Junction-Flächen schwarz
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(1.0f));
        for (java.util.List<Point2D.Double> jpoly : junctionPolygons) {
            Path2D path = new Path2D.Double();
            boolean first = true;
            for (Point2D.Double p : jpoly) {
                Point2D.Double sp = toScreen.apply(p);
                if (first) { path.moveTo(sp.x, sp.y); first = false; }
                else { path.lineTo(sp.x, sp.y); }
            }
            path.closePath();
            g2.fill(path);
        }

        // 3) Asphalt pro Lane
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(laneStrokePx, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        Map<String, java.util.List<java.util.List<Point2D.Double>>> edgeLaneScreens = new HashMap<>();

        for (Map.Entry<String, java.util.List<LaneData>> entry : lanesByEdge.entrySet()) {
            String edgeId = entry.getKey();
            java.util.List<LaneData> lanes = entry.getValue();
            if (lanes == null || lanes.isEmpty()) continue;

            java.util.List<java.util.List<Point2D.Double>> screens = new ArrayList<>();

            for (LaneData lane : lanes) {
                java.util.List<Point2D.Double> laneScreen = new ArrayList<>(lane.pointsWorld.size());
                for (Point2D.Double wp : lane.pointsWorld) laneScreen.add(toScreen.apply(wp));

                screens.add(laneScreen);
                g2.draw(toPath(laneScreen));
            }

            edgeLaneScreens.put(edgeId, screens);
        }

        // 4) Innenlinien gestrichelt
        float dashOnPx  = (float) (DASH_ON_M  * scale);
        float dashOffPx = (float) (DASH_OFF_M * scale);

        dashOnPx = Math.max(2.0f, dashOnPx);
        dashOffPx = Math.max(2.0f, dashOffPx);

        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(
                innerStrokePx,
                BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_ROUND,
                10f,
                new float[]{dashOnPx, dashOffPx},
                0f
        ));

        for (Map.Entry<String, java.util.List<java.util.List<Point2D.Double>>> entry : edgeLaneScreens.entrySet()) {
            java.util.List<java.util.List<Point2D.Double>> lanes = entry.getValue();
            if (lanes == null || lanes.size() < 2) continue;

            for (int i = 0; i < lanes.size() - 1; i++) {
                java.util.List<Point2D.Double> mid = midline(lanes.get(i), lanes.get(i + 1));
                g2.draw(toPath(mid));
            }
        }

        // 5) ✅ Haltelinien für ALLE lanes, die in laneToSignal gemappt sind
        //    (=> macht "joinedS_cluster_..." vollständig sichtbar)
        g2.setStroke(new BasicStroke(stopStrokePx, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));

        for (java.util.List<LaneData> lanes : lanesByEdge.values()) {
            for (LaneData lane : lanes) {

                String laneIdKey = lane.edgeId + "_" + lane.laneIndex;
                LinkSig sig = laneToSignal.get(laneIdKey);
                if (sig == null) continue; // nur wenn diese Spur wirklich signalisiert ist

                int n = lane.pointsWorld.size();
                if (n < 2) continue;

                Color col = getSignalColor(sig.tlId, sig.linkIndex);
                g2.setColor(col);

                Point2D.Double p1 = lane.pointsWorld.get(n - 2);
                Point2D.Double p2 = lane.pointsWorld.get(n - 1);

                drawStopLineWorld(g2, p1, p2, toScreen);
            }
        }

        // 6) Fahrzeuge (Dreiecke)
        if (!vehiclePositions.isEmpty()) {
            g2.setColor(Color.MAGENTA);

            double size = vehLenPx;
            double widthTri = vehWidPx;

            for (Map.Entry<String, Point2D.Double> entry : vehiclePositions.entrySet()) {
                String id = entry.getKey();
                Point2D.Double worldPos = entry.getValue();
                Point2D.Double sp = toScreen.apply(worldPos);

                double angle = vehicleAngles.getOrDefault(id, 0.0);

                double tipX = sp.x + Math.cos(angle) * size;
                double tipY = sp.y + Math.sin(angle) * size;

                double backDist = size * 0.6;
                double baseCX = sp.x - Math.cos(angle) * backDist;
                double baseCY = sp.y - Math.sin(angle) * backDist;

                double nx = -Math.sin(angle);
                double ny = Math.cos(angle);
                double halfW = widthTri / 2.0;

                double leftX = baseCX + nx * halfW;
                double leftY = baseCY + ny * halfW;
                double rightX = baseCX - nx * halfW;
                double rightY = baseCY - ny * halfW;

                int[] xs = {(int) Math.round(tipX), (int) Math.round(leftX), (int) Math.round(rightX)};
                int[] ys = {(int) Math.round(tipY), (int) Math.round(leftY), (int) Math.round(rightY)};

                g2.fillPolygon(xs, ys, 3);
            }
        }
    }
}
