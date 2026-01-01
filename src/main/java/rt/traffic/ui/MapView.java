package rt.traffic.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import rt.traffic.config.SumoPath;

/**
 * MapView:
 * - Zeichnet SUMO-Netz (osm.net.xml) + Polygone (osm.poly.xml)
 * - Zoom & Pan (Mausrad / Drag)
 * - Straßenfläche pro Lane (asphalt) -> zoomstabil (Stroke in Meter)
 * - NUR gestrichelte Innenlinien zwischen Fahrspuren (Dash in Meter)
 * - Haltelinien:
 * * zoomstabil (Länge/Offset in Meter)
 * * für JEDE Lane, die in osm.net.xml eine Connection mit tl/linkIndex hat
 * * Farbe kommt bevorzugt aus LIVE TraCI-State (via MainWindow ->
 * setLiveTrafficLightStates)
 * * Fallback: UI-only tlLogic Animation (wenn Live nicht gesetzt)
 *
 * - Fahrzeuge:
 * * Dreiecke mit fester "zufälliger" Farbe pro Vehicle-ID (Farbnamen)
 * * kleiner gemacht (Clamp-Werte reduziert)
 *
 * Idee allgemein:
 * - Wir rechnen ALLES in "Welt-Koordinaten" (SUMO Meter) und mappen erst beim
 * Zeichnen nach Pixel.
 * - Dadurch bleiben Strichstärken/Abstände/Größen beim Zoomen proportional und
 * wirken "zoomstabil".
 */
public class MapView extends JPanel {

    // =========================
    // Datenklassen
    // =========================

    // LaneData speichert pro Lane:
    // - laneId / edgeId / laneIndex
    // - pointsWorld: Polyline als Liste von Punkten (SUMO Meter)
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

    // TlPhase ist eine Phase aus der <tlLogic> in osm.net.xml:
    // - durS: Dauer in Sekunden
    // - state: String wie "GrGr..." (pro LinkIndex ein Buchstabe)
    private static class TlPhase {
        final double durS;
        final String state;

        TlPhase(double durS, String state) {
            this.durS = durS;
            this.state = state;
        }
    }

    // LinkSig verbindet eine Lane mit einer Ampel:
    // - tlId: welche Traffic Light ID
    // - linkIndex: welcher Index im state String (entspricht einer Connection)
    private static class LinkSig {
        final String tlId;
        final int linkIndex;

        LinkSig(String tlId, int linkIndex) {
            this.tlId = tlId;
            this.linkIndex = linkIndex;
        }
    }

    // ==========================================================
    // Public UI-API für Phasen (für MainWindow)
    // ==========================================================

    // Das ist bewusst "public", damit MainWindow/TL-Control-Panel die Phasenliste
    // anzeigen kann.
    // (Index, Dauer, State) reichen fürs UI.
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

    /**
     * Liefert Phasen (Index, Dauer, State) für eine tlLogic-ID aus der NET-Datei.
     *
     * Warum diese Methode existiert:
     * - Das TL-Control-Panel will Phasen "einfach" anzeigen.
     * - Wir geben dafür eine UI-freundliche Liste zurück, ohne dass das Panel
     * in unsere internen TlPhase-Strukturen greifen muss.
     */
    public java.util.List<UiTlPhase> getUiPhasesFor(String tlId) {
        java.util.List<UiTlPhase> out = new java.util.ArrayList<>();
        java.util.List<TlPhase> phases = tlPhases.get(tlId);
        if (phases == null)
            return out;

        for (int i = 0; i < phases.size(); i++) {
            TlPhase p = phases.get(i);
            out.add(new UiTlPhase(i, p.durS, p.state));
        }
        return out;
    }

    // =========================
    // Map data
    // =========================

    // lanesByEdge:
    // - Key: edgeId
    // - Value: alle Lanes dieser Edge, sortiert nach laneIndex (0,1,2,...)
    private final Map<String, java.util.List<LaneData>> lanesByEdge = new HashMap<>();

    // Polygone aus osm.poly.xml (Gebäude/Grünflächen usw.)
    private final java.util.List<java.util.List<Point2D.Double>> polygonShapes = new ArrayList<>();

    // Junction-Punkte (nur für Stats, optional für Marker)
    private final java.util.List<Point2D.Double> junctionPoints = new ArrayList<>();

    // Junction-Flächen (shape) aus osm.net.xml, wir füllen sie schwarz
    private final java.util.List<java.util.List<Point2D.Double>> junctionPolygons = new ArrayList<>();

    // Fahrzeuge Position + Winkel (Winkel ist in "Screen-Richtung", weil Y beim
    // Zeichnen invertiert ist)
    private final Map<String, Point2D.Double> vehiclePositions = new HashMap<>();
    private final Map<String, Point2D.Double> lastVehiclePositions = new HashMap<>();
    private final Map<String, Double> vehicleAngles = new HashMap<>();

    // ✅ Fahrzeugfarben (fixe Farbnamen pro ID)
    // - vehicleColorNames: ID -> Name (z.B. "RED")
    // - vehicleColors: ID -> tatsächliches Color Objekt
    private final Map<String, String> vehicleColorNames = new HashMap<>();
    private final Map<String, Color> vehicleColors = new HashMap<>();

    // Bounds der Welt (min/max), damit wir alles passend in die Panel-Größe
    // skalieren können
    private double minX = Double.POSITIVE_INFINITY;
    private double maxX = Double.NEGATIVE_INFINITY;
    private double minY = Double.POSITIVE_INFINITY;
    private double maxY = Double.NEGATIVE_INFINITY;

    // Wird von MainWindow gesetzt, damit wir initiale Counts reinschreiben können
    private StatsPanel statsPanel;

    // =========================
    // Route/Lane Highlight (Spawn Dialog)✅
    // =========================
    private final Set<String> highlightedEdges = new HashSet<>();

    public void highlightEdges(java.util.List<String> edgeIds) {
        highlightedEdges.clear();
        if (edgeIds != null)
            highlightedEdges.addAll(edgeIds);
        repaint();
    }

    public void clearHighlight() {
        highlightedEdges.clear();
        repaint();
    }

    // =========================
    // Traffic Light mapping & UI phases
    // =========================

    // laneToSignal: fromLaneId -> (tlId, linkIndex)
    // Beispiel Key: "<edgeId>_<laneIndex>"
    private final Map<String, LinkSig> laneToSignal = new HashMap<>();

    // tlPhases: tlId -> Liste von Phasen aus der NET-Datei
    private final Map<String, java.util.List<TlPhase>> tlPhases = new HashMap<>();

    // Fallback-Animation: wir merken pro tlId:
    // - welcher Phase-Index gerade aktiv ist
    // - wie viel Zeit in der aktuellen Phase noch übrig ist
    private final Map<String, Integer> tlPhaseIndex = new HashMap<>();
    private final Map<String, Double> tlPhaseRemaining = new HashMap<>();
    private Timer tlAnimTimer;

    // ✅ LIVE TraCI states: tlId -> "rGrG..."
    private final Map<String, String> liveTlStates = new HashMap<>();
    private boolean useLiveTlStates = false;

    /**
     * Wird aus MainWindow regelmäßig gesetzt (Snapshot aus TraCI).
     *
     * Verhalten:
     * - Wir speichern die Map lokal (copy).
     * - useLiveTlStates wird true, sobald mindestens 1 Eintrag vorhanden ist.
     * - repaint() damit die Haltelinien sofort die neuen Farben zeigen.
     */
    public void setLiveTrafficLightStates(Map<String, String> tlIdToState) {
        liveTlStates.clear();
        if (tlIdToState != null)
            liveTlStates.putAll(tlIdToState);

        // Wenn nichts drin ist, schalten wir automatisch auf Fallback-Animation zurück
        useLiveTlStates = (tlIdToState != null && !tlIdToState.isEmpty());

        repaint();
    }

    // =========================
    // Zoom & Pan
    // =========================

    // zoomFactor multipliziert die Grundskalierung (baseScale)
    private double zoomFactor = 1.0;
    private static final double MIN_ZOOM = 0.2;
    private static final double MAX_ZOOM = 5.0;

    // panX / panY sind Pixel-Offsets (weil wir das UI per Drag verschieben)
    private double panX = 0.0;
    private double panY = 0.0;

    // Speichert den letzten Drag-Punkt (damit wir dx/dy rechnen können)
    private Point lastDragPoint = null;

    // =========================
    // VISUELLE PARAMETER (WELT in Metern)
    // =========================

    // Diese Werte sind bewusst in "Meter" angegeben.
    // Beim Zeichnen werden sie mit scale in Pixel umgerechnet.
    private static final double LANE_WIDTH_M = 3.2;
    private static final double INNER_LINE_WIDTH_M = 0.18;

    private static final double DASH_ON_M = 6.0;
    private static final double DASH_OFF_M = 6.0;

    private static final double STOPLINE_WIDTH_M = 0.25;
    private static final double STOPLINE_OFFSET_M = 0.3;
    private static final double STOPLINE_LENGTH_M = 3.5;

    // =========================
    // FAHRZEUG-GRÖßE (WELT in Metern)
    // =========================
    private static final double VEHICLE_LENGTH_M = 1.5;
    private static final double VEHICLE_WIDTH_M = 1.4;

    /**
     * Konstruktor:
     * - Hintergrund setzen
     * - NET + POLY laden
     * - Debug-Ausgaben (Counts)
     * - Mouse-Controls initialisieren
     * - Fallback-Ampelanimation starten (nur wenn keine Live States gesetzt werden)
     */
    public MapView() {
        setBackground(new Color(250, 250, 250));

        try {
            loadNetFile(SumoPath.getNetPath());
            loadPolyFile(SumoPath.getPolyPath());

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

    // Zoom:
    // - rotation < 0: reinzoomen
    // - rotation > 0: rauszoomen
    // Danach clampen wir in [MIN_ZOOM, MAX_ZOOM].
    private void initMouseControls() {
        addMouseWheelListener((MouseWheelEvent e) -> {
            int rotation = e.getWheelRotation();

            double oldZoom = zoomFactor;

            // 1.1 ist ein angenehmer Zoom-Schritt (nicht zu aggressiv)
            if (rotation < 0)
                zoomFactor *= 1.1;
            else if (rotation > 0)
                zoomFactor /= 1.1;

            if (zoomFactor < MIN_ZOOM)
                zoomFactor = MIN_ZOOM;
            if (zoomFactor > MAX_ZOOM)
                zoomFactor = MAX_ZOOM;

            // repaint nur wenn sich wirklich was geändert hat (kleiner "Anti-Flimmer"
            // Schutz)
            if (Math.abs(zoomFactor - oldZoom) > 1e-6)
                repaint();
        });

        // Pan per Left-Click Drag:
        // - beim Press merken wir den Startpunkt
        // - beim Drag rechnen wir dx/dy in Pixel und addieren das auf panX/panY
        MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e))
                    lastDragPoint = e.getPoint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                lastDragPoint = null;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (lastDragPoint != null && SwingUtilities.isLeftMouseButton(e)) {
                    Point p = e.getPoint();

                    // dx/dy in Pixel
                    int dx = p.x - lastDragPoint.x;
                    int dy = p.y - lastDragPoint.y;

                    // Pan ist Pixel-Offset
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
    // Stats hook (MainWindow braucht das!)
    // -----------------------------

    /**
     * Damit MainWindow uns sein StatsPanel geben kann.
     *
     * Nutzen:
     * - Wir setzen direkt die initialen Counts (Lanes, Polys, Junctions).
     * - Vehicles und SimTime starten bei 0.
     *
     * Warum hier?
     * - MapView kennt die Daten nach loadNetFile/loadPolyFile.
     * - StatsPanel soll diese Werte anzeigen, ohne dass MainWindow sie neu
     * berechnen muss.
     */
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

    /**
     * MainWindow ruft das regelmäßig auf (mit den aktuellen Positionen aus TraCI).
     *
     * Was passiert hier genau:
     * 1) Wir entfernen IDs, die nicht mehr existieren (damit Maps nicht wachsen /
     * Memory leak).
     * 2) Für jedes Fahrzeug:
     * - Winkel aus Delta(Position) berechnen (damit das Dreieck in Fahrtrichtung
     * zeigt)
     * - feste Farbe pro ID sicherstellen (hashCode -> Index in Farbliste)
     * - lastVehiclePositions updaten
     * 3) vehiclePositions komplett ersetzen (damit paintComponent nur "aktuelles
     * Bild" sieht)
     * 4) repaint()
     */
    public void updateVehiclePositions(Map<String, Point2D.Double> newPositions) {
        if (newPositions == null)
            newPositions = new HashMap<>();

        // stillThere = Menge der IDs, die in der neuen Runde noch vorhanden sind
        Set<String> stillThere = new HashSet<>(newPositions.keySet());

        // Alles rauswerfen, was nicht mehr existiert:
        // - last positions
        // - angles
        // - colors
        // - color names
        lastVehiclePositions.keySet().removeIf(id -> !stillThere.contains(id));
        vehicleAngles.keySet().removeIf(id -> !stillThere.contains(id));
        vehicleColors.keySet().removeIf(id -> !stillThere.contains(id));
        vehicleColorNames.keySet().removeIf(id -> !stillThere.contains(id));

        for (Map.Entry<String, Point2D.Double> entry : newPositions.entrySet()) {
            String id = entry.getKey();
            Point2D.Double current = entry.getValue();
            Point2D.Double prev = lastVehiclePositions.get(id);

            if (prev != null) {
                // Bewegung in Weltkoordinaten (Meter)
                double dxWorld = current.x - prev.x;
                double dyWorld = current.y - prev.y;

                // len2 ist Länge^2 -> schneller als hypot, reicht zum "ist es bewegt?"
                double len2 = dxWorld * dxWorld + dyWorld * dyWorld;

                if (len2 > 1e-8) {
                    // Wichtig: Y ist beim Zeichnen invertiert (Screen-Y nach unten),
                    // deshalb -dyWorld, damit der Winkel optisch korrekt ist.
                    double angleScreen = Math.atan2(-dyWorld, dxWorld);
                    vehicleAngles.put(id, angleScreen);
                }
            } else {
                // Wenn wir keine vorherige Position haben, nehmen wir 0° als Start
                vehicleAngles.putIfAbsent(id, 0.0);
            }

            // ✅ feste "zufällige" Farbe pro ID
            ensureVehicleColor(id);

            // current wird zur "letzten Position" für die nächste Runde
            lastVehiclePositions.put(id, current);
        }

        // vehiclePositions ersetzt wir komplett:
        // so ist sicher, dass keine alten Fahrzeuge "stehen bleiben"
        vehiclePositions.clear();
        vehiclePositions.putAll(newPositions);

        repaint();
    }

    /**
     * Optional: kann man später für Filter/Stats nutzen (z.B. "zeige nur RED
     * vehicles").
     * Gibt den Farbnamen zurück, nicht das Color-Objekt.
     */
    public String getVehicleColorName(String vehicleId) {
        return vehicleColorNames.getOrDefault(vehicleId, "UNKNOWN");
    }

    // Legt pro Vehicle-ID einmalig eine Farbe fest.
    // Vorteil: die Farbe bleibt stabil, egal wie oft updateVehiclePositions()
    // kommt.
    private void ensureVehicleColor(String vehicleId) {
        if (vehicleColors.containsKey(vehicleId))
            return;

        String[] names = new String[] {
                "RED", "BLUE", "GREEN", "YELLOW", "ORANGE",
                "PINK", "CYAN", "PURPLE", "BROWN", "GRAY"
        };

        Color[] colors = new Color[] {
                Color.RED,
                new Color(0, 120, 255), // BLUE (etwas kräftiger)
                new Color(0, 170, 0), // GREEN
                Color.YELLOW,
                Color.ORANGE,
                Color.PINK,
                Color.CYAN,
                new Color(140, 0, 200), // PURPLE
                new Color(120, 70, 30), // BROWN
                Color.GRAY
        };

        // hashCode -> Index:
        // - Math.abs damit wir keinen negativen Index bekommen
        // - % names.length damit wir im Array bleiben
        int idx = Math.abs(vehicleId.hashCode()) % names.length;

        vehicleColorNames.put(vehicleId, names[idx]);
        vehicleColors.put(vehicleId, colors[idx]);
    }

    // =========================
    // Traffic light fallback animation
    // =========================

    // Startet eine simple UI-Animation, die tlPhases durchläuft.
    // Wird nur als Fallback genutzt, wenn keine Live TraCI States gesetzt werden.
    private void startTrafficLightAnimation() {
        if (tlAnimTimer != null)
            tlAnimTimer.stop();
        if (tlPhases.isEmpty())
            return;

        // 100ms Tick -> dt=0.1s (siehe advanceTrafficLights)
        tlAnimTimer = new Timer(100, e -> {
            advanceTrafficLights(0.1);
            repaint();
        });
        tlAnimTimer.start();
    }

    // advanceTrafficLights:
    // - reduziert remaining time
    // - wenn rem <= 0 -> nächste Phase, rem += duration
    // - am Ende werden Index + remaining gespeichert
    private void advanceTrafficLights(double dt) {
        for (Map.Entry<String, java.util.List<TlPhase>> entry : tlPhases.entrySet()) {
            String tlId = entry.getKey();
            java.util.List<TlPhase> phases = entry.getValue();
            if (phases == null || phases.isEmpty())
                continue;

            int idx = tlPhaseIndex.getOrDefault(tlId, 0);
            idx = Math.max(0, Math.min(idx, phases.size() - 1));

            double rem = tlPhaseRemaining.getOrDefault(tlId, phases.get(idx).durS);
            rem -= dt;

            // while statt if:
            // falls dt größer als die Phasendauer wäre, springen wir sauber über mehrere
            // Phasen
            while (rem <= 0.0 && !phases.isEmpty()) {
                idx = (idx + 1) % phases.size();
                rem += phases.get(idx).durS;
            }

            tlPhaseIndex.put(tlId, idx);
            tlPhaseRemaining.put(tlId, rem);
        }
    }

    // ✅ Color aus LIVE TraCI State (wenn vorhanden), sonst Fallback aus
    // tlLogic-Animation
    //
    // linkIndex ist der Index im state String. Der Buchstabe dort ist z.B.:
    // - 'G' / 'g' -> grün
    // - 'y' / 'Y' -> gelb
    // - 'r' / 'R' -> rot
    private Color getSignalColor(String tlId, int linkIndex) {
        // 1) Live State (TraCI) bevorzugt
        String liveState = useLiveTlStates ? liveTlStates.get(tlId) : null;
        if (liveState != null && !liveState.isEmpty() && linkIndex >= 0 && linkIndex < liveState.length()) {
            return colorForStateChar(liveState.charAt(linkIndex));
        }

        // 2) Fallback: tlLogic-Animation
        java.util.List<TlPhase> phases = tlPhases.get(tlId);
        if (phases == null || phases.isEmpty())
            return new Color(200, 200, 200);

        int idx = tlPhaseIndex.getOrDefault(tlId, 0);
        idx = Math.max(0, Math.min(idx, phases.size() - 1));

        String state = phases.get(idx).state;
        if (state == null || state.isEmpty())
            return new Color(200, 200, 200);
        if (linkIndex < 0 || linkIndex >= state.length())
            return new Color(200, 200, 200);

        return colorForStateChar(state.charAt(linkIndex));
    }

    // Übersetzt einen state-char in eine Farbe.
    // Alles Unbekannte wird grau (sieht man im Debug direkt: "da stimmt was nicht /
    // nicht definiert").
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

    // updateBounds hält unsere min/max Weltkoordinaten aktuell.
    // Das brauchen wir später, um die Karte passend ins Panel zu skalieren.
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

    // shape="x,y x,y x,y ..." -> Liste von Punkten
    private static java.util.List<Point2D.Double> parseShapePoints(String shapeAttr) {
        java.util.List<Point2D.Double> pts = new ArrayList<>();
        if (shapeAttr == null || shapeAttr.isEmpty())
            return pts;

        String[] pairs = shapeAttr.trim().split(" ");
        for (String pair : pairs) {
            String[] xy = pair.split(",");
            if (xy.length != 2)
                continue;

            double x = Double.parseDouble(xy[0]);
            double y = Double.parseDouble(xy[1]);

            pts.add(new Point2D.Double(x, y));
        }
        return pts;
    }

    // Lane-ID Format: edgeId_laneIndex (z.B. "E0_0")
    // Wir schneiden den letzten _Teil ab.
    private static String extractEdgeIdFromLaneId(String laneId) {
        int idx = laneId.lastIndexOf('_');
        if (idx <= 0)
            return laneId;
        return laneId.substring(0, idx);
    }

    // Aus "edgeId_3" wird laneIndex=3.
    // Wenn das nicht parsebar ist -> 0.
    private static int extractLaneIndexFromLaneId(String laneId) {
        int idx = laneId.lastIndexOf('_');
        if (idx < 0 || idx == laneId.length() - 1)
            return 0;
        try {
            return Integer.parseInt(laneId.substring(idx + 1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // toPath: aus einer Punktliste wird ein Path2D (Polyline)
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

    // midline: berechnet eine "Mittel-Linie" zwischen zwei Lane-Polylines.
    // Wir nehmen jeweils Punkt i aus laneA und laneB und mitteln:
    // mid = (a+b)/2.
    // Das geht gut, wenn beide Polylines ähnlich sampeln (bei SUMO ist das meist
    // ok).
    private static java.util.List<Point2D.Double> midline(java.util.List<Point2D.Double> a,
            java.util.List<Point2D.Double> b) {
        int n = Math.min(a.size(), b.size());
        java.util.List<Point2D.Double> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(new Point2D.Double(
                    (a.get(i).x + b.get(i).x) * 0.5,
                    (a.get(i).y + b.get(i).y) * 0.5));
        }
        return out;
    }

    // clamp: begrenzt Werte, damit Strichstärken nicht zu klein/zu groß werden.
    // Beispiel: bei starkem Zoom-Out würde laneStrokePx sonst fast 0 werden.
    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    // Zeichnet eine Haltelinie in Weltkoordinaten (Meter), aber tatsächlich in
    // Screen-Pixel.
    //
    // Idee:
    // - Wir nehmen die letzten beiden Punkte der Lane (p1,p2).
    // - Wir berechnen die Richtung (u) und ein Normalenvektor (n).
    // - Wir verschieben ein Stück nach hinten (STOPLINE_OFFSET_M), damit die Linie
    // vor dem Ende sitzt.
    // - Wir zeichnen eine Linie quer zur Fahrtrichtung mit Länge STOPLINE_LENGTH_M.
    private void drawStopLineWorld(
            Graphics2D g2,
            Point2D.Double p1World,
            Point2D.Double p2World,
            java.util.function.Function<Point2D.Double, Point2D.Double> toScreen) {
        double dx = p2World.x - p1World.x;
        double dy = p2World.y - p1World.y;

        double len = Math.hypot(dx, dy);
        if (len < 1e-6)
            return;

        // unit direction
        double ux = dx / len;
        double uy = dy / len;

        // "center point" etwas zurück vom Lane-Ende
        double cx = p2World.x - ux * STOPLINE_OFFSET_M;
        double cy = p2World.y - uy * STOPLINE_OFFSET_M;

        // Normalenvektor (quer zur Richtung)
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

    // loadNetFile:
    // - liest osm.net.xml
    // - sammelt lanes (pro edge)
    // - sammelt junction points + junction polygons
    // - sammelt tlLogic phases
    // - baut mapping fromLane -> (tlId, linkIndex) aus connection nodes
    private void loadNetFile(String path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new File(path));
        doc.getDocumentElement().normalize();

        // 1) Edges & Lanes
        NodeList edgeNodes = doc.getElementsByTagName("edge");
        for (int i = 0; i < edgeNodes.getLength(); i++) {
            Node node = edgeNodes.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE)
                continue;
            Element edgeElem = (Element) node;

            // internal edges nicht zeichnen (das sind SUMO "Hilfsstücke" in Junctions)
            if (edgeElem.hasAttribute("function") && "internal".equals(edgeElem.getAttribute("function"))) {
                continue;
            }

            NodeList laneNodes = edgeElem.getElementsByTagName("lane");
            for (int j = 0; j < laneNodes.getLength(); j++) {
                Element laneElem = (Element) laneNodes.item(j);

                String laneId = laneElem.getAttribute("id");
                if (laneId == null || laneId.isEmpty())
                    continue;

                String shapeAttr = laneElem.getAttribute("shape");
                if (shapeAttr == null || shapeAttr.isEmpty())
                    continue;

                java.util.List<Point2D.Double> polyline = parseShapePoints(shapeAttr);
                if (polyline.size() < 2)
                    continue;

                // Bounds updaten, damit die Karte später "fit" gerechnet werden kann
                for (Point2D.Double p : polyline)
                    updateBounds(p.x, p.y);

                String edgeId = extractEdgeIdFromLaneId(laneId);
                int laneIndex = extractLaneIndexFromLaneId(laneId);

                LaneData ld = new LaneData(laneId, edgeId, laneIndex, polyline);
                lanesByEdge.computeIfAbsent(edgeId, k -> new ArrayList<>()).add(ld);
            }
        }

        // Lanes pro Edge sortieren: 0,1,2,...
        for (java.util.List<LaneData> list : lanesByEdge.values()) {
            list.sort(Comparator.comparingInt(a -> a.laneIndex));
        }

        // 2) Junction polygons + Punkte
        NodeList juncNodes = doc.getElementsByTagName("junction");
        for (int i = 0; i < juncNodes.getLength(); i++) {
            Node node = juncNodes.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE)
                continue;
            Element juncElem = (Element) node;

            // Junction Center (für Stats, optional)
            if (juncElem.hasAttribute("x") && juncElem.hasAttribute("y")) {
                double x = Double.parseDouble(juncElem.getAttribute("x"));
                double y = Double.parseDouble(juncElem.getAttribute("y"));
                junctionPoints.add(new Point2D.Double(x, y));
                updateBounds(x, y);
            }

            // internal junctions ignorieren (die sind "Unsichtbar" / nur intern)
            String type = juncElem.getAttribute("type");
            if ("internal".equals(type))
                continue;

            // Junction-Flächen (shape) -> wir zeichnen sie später schwarz
            if (juncElem.hasAttribute("shape")) {
                java.util.List<Point2D.Double> poly = parseShapePoints(juncElem.getAttribute("shape"));
                for (Point2D.Double p : poly)
                    updateBounds(p.x, p.y);
                if (poly.size() >= 3)
                    junctionPolygons.add(poly);
            }
        }

        // 3) tlLogic -> Phasen
        NodeList tlNodes = doc.getElementsByTagName("tlLogic");
        for (int i = 0; i < tlNodes.getLength(); i++) {
            Node n = tlNodes.item(i);
            if (n.getNodeType() != Node.ELEMENT_NODE)
                continue;
            Element tl = (Element) n;

            String tlId = tl.getAttribute("id");
            if (tlId == null || tlId.isEmpty())
                continue;

            java.util.List<TlPhase> phases = new ArrayList<>();
            NodeList phaseNodes = tl.getElementsByTagName("phase");
            for (int p = 0; p < phaseNodes.getLength(); p++) {
                Node pn = phaseNodes.item(p);
                if (pn.getNodeType() != Node.ELEMENT_NODE)
                    continue;
                Element ph = (Element) pn;

                String durStr = ph.getAttribute("duration");
                String state = ph.getAttribute("state");
                if (durStr == null || durStr.isEmpty())
                    continue;
                if (state == null || state.isEmpty())
                    continue;

                double dur = Double.parseDouble(durStr);
                phases.add(new TlPhase(dur, state));
            }

            if (!phases.isEmpty()) {
                tlPhases.put(tlId, phases);

                // Default: Phase 0 starten
                tlPhaseIndex.put(tlId, 0);
                tlPhaseRemaining.put(tlId, phases.get(0).durS);
            }
        }

        // 4) connections -> tl/linkIndex Mapping pro fromLane
        NodeList connNodes = doc.getElementsByTagName("connection");
        for (int i = 0; i < connNodes.getLength(); i++) {
            Node n = connNodes.item(i);
            if (n.getNodeType() != Node.ELEMENT_NODE)
                continue;
            Element c = (Element) n;

            // Wir brauchen tl + linkIndex, sonst kann man keine Ampelfarbe ableiten
            if (!c.hasAttribute("tl") || !c.hasAttribute("linkIndex"))
                continue;

            String tlId = c.getAttribute("tl");
            String fromEdge = c.getAttribute("from");
            String fromLaneStr = c.getAttribute("fromLane");
            String linkIndexStr = c.getAttribute("linkIndex");

            if (tlId == null || tlId.isEmpty())
                continue;
            if (fromEdge == null || fromEdge.isEmpty())
                continue;
            if (fromLaneStr == null || fromLaneStr.isEmpty())
                continue;
            if (linkIndexStr == null || linkIndexStr.isEmpty())
                continue;

            int fromLane;
            int linkIndex;
            try {
                fromLane = Integer.parseInt(fromLaneStr);
                linkIndex = Integer.parseInt(linkIndexStr);
            } catch (NumberFormatException ex) {
                continue;
            }

            // Lane-ID nach SUMO Muster: "<fromEdge>_<fromLaneIndex>"
            String laneId = fromEdge + "_" + fromLane;

            // putIfAbsent: falls mehrfach Connections für eine Lane drin sind,
            // nehmen wir die erste (für unsere Haltelinie reicht das).
            laneToSignal.putIfAbsent(laneId, new LinkSig(tlId, linkIndex));
        }
    }

    // loadPolyFile:
    // - liest osm.poly.xml
    // - sammelt <poly shape="..."> in polygonShapes
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
            if (node.getNodeType() != Node.ELEMENT_NODE)
                continue;

            Element polyElem = (Element) node;
            String shapeAttr = polyElem.getAttribute("shape");
            if (shapeAttr == null || shapeAttr.isEmpty())
                continue;

            java.util.List<Point2D.Double> poly = parseShapePoints(shapeAttr);
            if (poly.size() >= 3)
                polygonShapes.add(poly);
        }
    }

    // -----------------------------
    // Paint
    // -----------------------------

    /**
     * Zeichnet die komplette Karte:
     * 1) Polygone (grün)
     * 2) Junction-Flächen (schwarz)
     * 3) Asphalt pro Lane (schwarz, dick)
     * 4) Innenlinien (weiß gestrichelt)
     * 5) Haltelinien (pro gemappter Lane, Farbe je nach Ampelstate)
     * 6) Fahrzeuge (farbige Dreiecke)
     *
     * Wichtiger Punkt:
     * - Wir bauen eine toScreen Funktion (Welt->Pixel).
     * - Alles was "in Metern" definiert ist, wird mit scale in Pixel übersetzt.
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        // Wenn keine Bounds da sind, wurde noch nichts geladen
        if (minX == Double.POSITIVE_INFINITY)
            return;

        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int width = getWidth();
        int height = getHeight();

        // Weltgröße (Meter)
        double worldW = maxX - minX;
        double worldH = maxY - minY;

        // Basis-Scale: "fit to window"
        double scaleX = width / worldW;
        double scaleY = height / worldH;
        double baseScale = Math.min(scaleX, scaleY);

        // finaler Scale = fit * zoomFactor
        final double scale = baseScale * zoomFactor;

        // Zentrierung, damit die Karte mittig ist
        double offsetX = (width - worldW * scale) / 2.0;
        double offsetY = (height - worldH * scale) / 2.0;

        // Welt->Screen Transformation:
        // - X: normal
        // - Y: invertiert (weil Screen Y nach unten wächst)
        // - panX/panY sind Pixel-Offsets (Drag)
        final java.util.function.Function<Point2D.Double, Point2D.Double> toScreen = p -> {
            double sx = (p.x - minX) * scale + offsetX + panX;
            double sy = height - ((p.y - minY) * scale + offsetY) + panY;
            return new Point2D.Double(sx, sy);
        };

        // Stroke in Pixel (aus Meter * scale)
        float laneStrokePx = (float) (LANE_WIDTH_M * scale);
        float innerStrokePx = (float) (INNER_LINE_WIDTH_M * scale);
        float stopStrokePx = (float) (STOPLINE_WIDTH_M * scale);

        // clamp: damit es bei starkem zoom nicht zu dünn / zu dick wird
        laneStrokePx = clamp(laneStrokePx, 1.5f, 40.0f);
        innerStrokePx = clamp(innerStrokePx, 0.8f, 8.0f);
        stopStrokePx = clamp(stopStrokePx, 1.0f, 10.0f);

        // ===== Vehicle size: WELT->PIXEL (zoomstabil) =====
        float vehLenPx = (float) (VEHICLE_LENGTH_M * scale);
        float vehWidPx = (float) (VEHICLE_WIDTH_M * scale);

        // ✅ kleiner gemacht (damit es bei hohem zoom nicht zu riesig ist)
        vehLenPx = clamp(vehLenPx, 3.0f, 22.0f);
        vehWidPx = clamp(vehWidPx, 2.0f, 12.0f);

        // 1) Polygone (grün)
        g2.setColor(new Color(180, 210, 180));
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

        // 2) Junction-Flächen schwarz
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(1.0f));
        for (java.util.List<Point2D.Double> jpoly : junctionPolygons) {
            Path2D path = new Path2D.Double();
            boolean first = true;
            for (Point2D.Double p : jpoly) {
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

        // 3) Asphalt pro Lane
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(laneStrokePx, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        // Wir speichern pro edgeId die already transformed lane polylines (Screen),
        // damit wir danach die Midlines (Innenlinien) dazwischen bauen können.
        Map<String, java.util.List<java.util.List<Point2D.Double>>> edgeLaneScreens = new HashMap<>();

        for (Map.Entry<String, java.util.List<LaneData>> entry : lanesByEdge.entrySet()) {
            String edgeId = entry.getKey();
            java.util.List<LaneData> lanes = entry.getValue();
            if (lanes == null || lanes.isEmpty())
                continue;

            java.util.List<java.util.List<Point2D.Double>> screens = new ArrayList<>();

            for (LaneData lane : lanes) {
                // Weltpunkte -> Screenpunkte
                java.util.List<Point2D.Double> laneScreen = new ArrayList<>(lane.pointsWorld.size());
                for (Point2D.Double wp : lane.pointsWorld)
                    laneScreen.add(toScreen.apply(wp));

                screens.add(laneScreen);

                // Lane als dicke schwarze Linie zeichnen
                g2.draw(toPath(laneScreen));
            }

            edgeLaneScreens.put(edgeId, screens);
        }

        // 3.5) HIGHLIGHT Overlay (Route-Auswahl / Spawn)
        // -------------------------------
        if (!highlightedEdges.isEmpty()) {
            Stroke oldStroke = g2.getStroke();
            Color oldColor = g2.getColor();

            // deutlich dicker als normaler Asphalt
            g2.setStroke(new BasicStroke(
                    clamp(laneStrokePx * 1.8f, 3.0f, 60.0f),
                    BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND));

            // "Leuchtfarbe"
            g2.setColor(new Color(255, 140, 0, 220)); // Orange

            for (String edgeId : highlightedEdges) {
                var lanes = lanesByEdge.get(edgeId);
                if (lanes == null)
                    continue;

                for (LaneData lane : lanes) {
                    List<Point2D.Double> laneScreen = new ArrayList<>(lane.pointsWorld.size());
                    for (Point2D.Double wp : lane.pointsWorld) {
                        laneScreen.add(toScreen.apply(wp));
                    }
                    g2.draw(toPath(laneScreen));
                }
            }

            g2.setColor(oldColor);
            g2.setStroke(oldStroke);
        }

        // 4) Innenlinien gestrichelt
        float dashOnPx = (float) (DASH_ON_M * scale);
        float dashOffPx = (float) (DASH_OFF_M * scale);

        // Dash sollte nie zu kurz werden, sonst sieht es wie "Punkte" aus
        dashOnPx = Math.max(2.0f, dashOnPx);
        dashOffPx = Math.max(2.0f, dashOffPx);

        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(
                innerStrokePx,
                BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_ROUND,
                10f,
                new float[] { dashOnPx, dashOffPx },
                0f));

        for (Map.Entry<String, java.util.List<java.util.List<Point2D.Double>>> entry : edgeLaneScreens.entrySet()) {
            java.util.List<java.util.List<Point2D.Double>> lanes = entry.getValue();
            if (lanes == null || lanes.size() < 2)
                continue;

            // Zwischen Lane i und Lane i+1 eine Mittellinie zeichnen
            for (int i = 0; i < lanes.size() - 1; i++) {
                java.util.List<Point2D.Double> mid = midline(lanes.get(i), lanes.get(i + 1));
                g2.draw(toPath(mid));
            }
        }

        // 5) Haltelinien für ALLE gemappten lanes (tl/linkIndex)
        g2.setStroke(new BasicStroke(stopStrokePx, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));

        for (java.util.List<LaneData> lanes : lanesByEdge.values()) {
            for (LaneData lane : lanes) {

                // key muss genauso gebaut werden wie in loadNetFile:
                // laneIdKey = "<edgeId>_<laneIndex>"
                String laneIdKey = lane.edgeId + "_" + lane.laneIndex;

                LinkSig sig = laneToSignal.get(laneIdKey);
                if (sig == null)
                    continue;

                int n = lane.pointsWorld.size();
                if (n < 2)
                    continue;

                // Ampelfarbe: Live bevorzugt, sonst Fallback-Animation
                Color col = getSignalColor(sig.tlId, sig.linkIndex);
                g2.setColor(col);

                // Wir nehmen die letzten 2 Punkte, weil das Ende der Lane meist an der Junction
                // liegt
                Point2D.Double p1 = lane.pointsWorld.get(n - 2);
                Point2D.Double p2 = lane.pointsWorld.get(n - 1);

                drawStopLineWorld(g2, p1, p2, toScreen);
            }
        }

        // 6) Fahrzeuge (Dreiecke) – jetzt kleiner + farbig
        if (!vehiclePositions.isEmpty()) {

            // size/widthTri sind Pixelwerte, kommen aus vehLenPx/vehWidPx (clamped)
            double size = vehLenPx; // Länge in Pixel
            double widthTri = vehWidPx; // Breite in Pixel

            for (Map.Entry<String, Point2D.Double> entry : vehiclePositions.entrySet()) {
                String id = entry.getKey();
                Point2D.Double worldPos = entry.getValue();
                Point2D.Double sp = toScreen.apply(worldPos);

                // Farbe pro ID (wenn nicht vorhanden -> MAGENTA als "Debug ich sehe es sofort")
                Color c = vehicleColors.getOrDefault(id, Color.MAGENTA);
                g2.setColor(c);

                // Winkel, den wir in updateVehiclePositions berechnet haben
                double angle = vehicleAngles.getOrDefault(id, 0.0);

                // Spitze des Dreiecks (vorne)
                double tipX = sp.x + Math.cos(angle) * size;
                double tipY = sp.y + Math.sin(angle) * size;

                // Basiszentrum liegt ein Stück hinter dem Mittelpunkt
                // 0.6 ist "wie spitz" das Dreieck ist (je größer, desto breiter/hinterer)
                double backDist = size * 0.6;
                double baseCX = sp.x - Math.cos(angle) * backDist;
                double baseCY = sp.y - Math.sin(angle) * backDist;

                // Normalenrichtung für die Basisbreite
                double nx = -Math.sin(angle);
                double ny = Math.cos(angle);
                double halfW = widthTri / 2.0;

                double leftX = baseCX + nx * halfW;
                double leftY = baseCY + ny * halfW;
                double rightX = baseCX - nx * halfW;
                double rightY = baseCY - ny * halfW;

                int[] xs = { (int) Math.round(tipX), (int) Math.round(leftX), (int) Math.round(rightX) };
                int[] ys = { (int) Math.round(tipY), (int) Math.round(leftY), (int) Math.round(rightY) };

                g2.fillPolygon(xs, ys, 3);
            }
        }
    }
}