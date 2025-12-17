// Author: Enur Redzepi
/**Metrics:
 * - Berechnet Werte:
 * + Durchschnitliche Geschwindigkeit in KmH
 * + Anteil der stehenden Fahrzeuge
 * + Anteil der Fahrzeuge pro Kante
 * + Stau Kanten 
 * + Die Durchschnittliche Fahrtdauer aller Fahrzeuge
 * + Minimale Fahrtdauer insegesamt
 * + Maximale Fahrtdauer insgesamt
 * + Verteilung der Trips in :
 * ++Kurz
 * ++Mittel
 * ++Lang
 * + Anzahl der vollendeten Fahrten
 * Idee:
 * Auf der Basis von AnalyticsExecution berechneten wir hier die fehlenden Werte und bieten 2 Methoden zur Generierung der 
 * Reports als PDF und CSV.
 * 
*/
package rt.traffic.application.analytics;
import org.eclipse.sumo.libtraci.Simulation;// Imports for execution

import java.util.Map;
import java.util.List;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

// imports for PDF creation
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.contentstream.PDContentStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

// Our own Path finder class
import traffic.infrastructure.sumo.SumoPath;

public class Metrics {

	/**
	 * 60 % stopped means congestion
	 */
	private static final double DEFAULT_STOPPED_SHARE_THRESHOLD = 0.6;
	private static final int MIN_VEHICLES_FOR_CONGESTION = 10;

	
	// Average Speed in meter per second
	public double avgSpeedPerSecond;

	// Amount of vehicles
	public int vehicleCount;

	// Amount of stopped vehicles
	public int stoppedVehicleCount;

	// Amount vehicles per edge
	public Map<String, Integer> vehiclesPerEdge;

	// Amount of stopped vehicles per edge
	public Map<String, Integer> stoppedVehiclesPerEdge;

	// Densitiy per edge
	public Map<String, Double> densityPerEdge;

	// Amount of finished trips
	public int finishedTripCount;

	// Average triptime of all finished trips
	public double averageTravelTimeSeconds;

	// Shortest triptime in seconds
	public double minTravelTimeSeconds;

	// Biggest triptime in seconds
	public double maxTravelTimeSeconds;

	// Trips less than 60 seconds
	public int shortTripsCount;

	// Trips between [60,300] seconds
	public int mediumTripsCount;

	// Trips with more than 300 seconds
	public int longTripsCount;

	/**
	 * 
	 * @param avgSpeedPerSecond      average speed in meter per second
	 * @param vehicleCount           amount of vehicles right now
	 * @param stoppedVehicleCount    amount of stopped vehicles
	 * @param vehiclesPerEdge        amount of vehicles per edge (key)
	 * @param stoppedVehiclesPerEdge amount of stopped vehicle per edge (key)
	 * @param densityPerEdge         densitiy per edge
	 */
	// Constructor
	public Metrics(double avgSpeedPerSecond, int vehicleCount, int stoppedVehicleCount,
			Map<String, Integer> vehiclesPerEdge, Map<String, Integer> stoppedVehiclesPerEdge,
			Map<String, Double> densityPerEdge) {
		this.avgSpeedPerSecond = avgSpeedPerSecond;
		this.vehicleCount = vehicleCount;
		this.stoppedVehicleCount = stoppedVehicleCount;
		this.vehiclesPerEdge = vehiclesPerEdge;
		this.stoppedVehiclesPerEdge = stoppedVehiclesPerEdge;
		this.densityPerEdge = densityPerEdge;

		// These values are executed in this class
		this.finishedTripCount = 0;
		this.averageTravelTimeSeconds = 0.0;
		this.minTravelTimeSeconds = 0.0;
		this.maxTravelTimeSeconds = 0.0;
		this.shortTripsCount = 0;
		this.mediumTripsCount = 0;
		this.longTripsCount = 0;
	}

	/**
	 * @return propotion of stopped vehicles in the simu
	 */
	public double getStoppedRatio() {
		if (vehicleCount == 0) {
			return 0.0;
		}
		return (double) stoppedVehicleCount / (double) vehicleCount;
	}

	/**
	 * @return average speed converted in Km/h with m/s * 3.6
	 */
	public double getAverageSpeedKmh() {
		return avgSpeedPerSecond * 3.6;
	}

	/**
	 * @param an edgeId
	 * @return Ratio
	 * propotion of stopped vehicles on a choosen edge
	 */
	public double getStoppedRatioForEdge(String edgeId) {
		if (vehiclesPerEdge == null || stoppedVehiclesPerEdge == null) {
			return 0.0;
		}

		Integer total = vehiclesPerEdge.get(edgeId);
		if (total == null || total == 0) {
			return 0.0;
		}

		int stopped = stoppedVehiclesPerEdge.getOrDefault(edgeId, 0);
		return (double) stopped / (double) total;
	}

	/**
	 * @param edgeId id of the chosen edge
	 * @return value of the key edgeId in the map therefore the density (vehicles per km)
	 */
	public double getDensityForEdge(String edgeId) {
		if (densityPerEdge == null || !densityPerEdge.containsKey(edgeId)) {
			return 0.0;
		}
		return densityPerEdge.get(edgeId);
	}

	
	/**
	 * @param edgeId the edgeId to be checked
	 * @return if congestion :true , else false
	 */
	public boolean isEdgeCongested(String edgeId) {
		if (vehiclesPerEdge == null || stoppedVehiclesPerEdge == null) {
			return false;
		}

		Integer total = vehiclesPerEdge.get(edgeId);
		if (total == null || total < MIN_VEHICLES_FOR_CONGESTION) {
			// Zu wenig Fahrzeuge, um sinnvoll von "Stau" zu sprechen
			return false;
		}

		int stopped = stoppedVehiclesPerEdge.getOrDefault(edgeId, 0);
		double stoppedShare = (double) stopped / (double) total;

		return stoppedShare >= DEFAULT_STOPPED_SHARE_THRESHOLD;
	}

	/**
	 * find all edges with congestion
	 *
	 * @return a list of these edges with congestion
	 */
	public List<String> getCongestedEdges() {
		List<String> result = new ArrayList<>();

		if (vehiclesPerEdge == null || vehiclesPerEdge.isEmpty() || stoppedVehiclesPerEdge == null) {
			return result;
		}

		for (String edgeId : vehiclesPerEdge.keySet()) {
			if (isEdgeCongested(edgeId)) {
				result.add(edgeId);
			}
		}

		return result;
	}



	public void exportToPdf() throws IOException {
    // 1) Export Ordner
    String exportDir = SumoPath.getExportPath();
    Path dirPath = Paths.get(exportDir);
    Files.createDirectories(dirPath);

    // 2) Dateiname (Locale.US damit kein Komma im Namen)
    String fileName = "traffic_metrics_" + String.format(java.util.Locale.US, "%.2f", Simulation.getTime()) + ".pdf";
    Path fullPath = dirPath.resolve(fileName);

    // 3) Content als Lines bauen
    List<String> lines = buildPdfLines();

    // 4) PDF schreiben (mit Auto-Seitenumbruch)
    try (PDDocument document = new PDDocument()) {
        writeLinesWithPageBreak(document, lines);
        document.save(fullPath.toString());
    }

    System.out.println("[Metrics] PDF exportiert nach: " + fullPath);
}

	public void exportToCsv() throws IOException {
		// 1) Export-Ordner aus SumoPath holen
		String exportDir = SumoPath.getExportPath();

		// 2) Ordner sicherstellen
		Path dirPath = Paths.get(exportDir);
		Files.createDirectories(dirPath);

		// 3) Dateinamen festlegen (z.B. mit Zeitstempel)
		String fileName = "traffic_metrics_" + String.format("%.2f", Simulation.getTime()) + ".csv";

		// 4) Vollständigen Pfad bauen
		Path path = dirPath.resolve(fileName);

		// 5) CSV schreiben
		try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(path))) {
			String sep = ";";

			writer.println("# Summary");
			writer.println("metric" + sep + "value");
			writer.println("Simulation Time" + sep + String.format("%.2f", Simulation.getTime()) + "s");
			writer.println("Average vehicle Speed in Kmh" + sep + String.format("%.2f", getAverageSpeedKmh()));
			writer.println("Vehicle Count" + sep + vehicleCount);
			writer.println("Amount of stopped vehicles" + sep + stoppedVehicleCount);
			writer.println("Ratio of stopped Vehicles" + sep + String.format("%.2f", getStoppedRatio() * 100.0));
			writer.println("Amount of finieshed trips" + sep + finishedTripCount);
			writer.println("Average Travel Time in Seconds" + sep + String.format("%.1f", averageTravelTimeSeconds));
			writer.println("Minimal Travel Time in seconds" + sep + String.format("%.1f", minTravelTimeSeconds));
			writer.println("Maximal Travel Time in seconds" + sep + String.format("%.1f", maxTravelTimeSeconds));

			writer.println(); // Leerzeile


			writer.println("# Per edge metrics");
			writer.println("edgeId" + sep + "vehiclesOnEdge" + sep + "stoppedVehiclesOnEdge" + sep + "densityVehiclesPerKm");

			if (vehiclesPerEdge != null) {
				for (Map.Entry<String, Integer> entry : vehiclesPerEdge.entrySet()) {
					String edgeId = entry.getKey();
					int vehiclesOnEdge = entry.getValue();
					int stoppedOnEdge = 0;
					if (stoppedVehiclesPerEdge != null) {
						stoppedOnEdge = stoppedVehiclesPerEdge.getOrDefault(edgeId, 0);
					}
					double density = getDensityForEdge(edgeId);

					writer.printf(
							"%s%s%d%s%d%s%.3f%n",
							edgeId, sep,
							vehiclesOnEdge, sep,
							stoppedOnEdge, sep,
							density
					);
				}
			}

			writer.println();

			
			writer.println("# Congested edges (stopped share >= "
					+ (int) (DEFAULT_STOPPED_SHARE_THRESHOLD * 100)
					+ "%, minVehicles = " + MIN_VEHICLES_FOR_CONGESTION + ")");
			writer.println("edgeId");

			List<String> congestedEdges = getCongestedEdges();
			for (String edgeId : congestedEdges) {
				writer.println(edgeId);
			}
		}

		System.out.println("[Metrics] CSV exportiert nach: " + path.toAbsolutePath());
	}

	private List<String> buildPdfLines() {
    List<String> lines = new ArrayList<>();

    lines.add("Traffic Metrics Report");
    lines.add(String.format(java.util.Locale.US, "Current Simulation Time: %.2f sec", Simulation.getTime()));
    lines.add(String.format(java.util.Locale.US, "Average speed: %.2f km/h", getAverageSpeedKmh()));
    lines.add("Amount of vehicles: " + vehicleCount);
    lines.add("Amount of stopped vehicles: " + stoppedVehicleCount);
    lines.add(String.format(java.util.Locale.US, "Stopped ratio: %.2f %%", getStoppedRatio() * 100.0));

    lines.add(""); // blank line

    lines.add(String.format(java.util.Locale.US, "Finished trips: %d", finishedTripCount));
    lines.add(String.format(java.util.Locale.US, "Average travel time: %.1f s", averageTravelTimeSeconds));
    lines.add(String.format(java.util.Locale.US, "Min travel time: %.1f s", minTravelTimeSeconds));
    lines.add(String.format(java.util.Locale.US, "Max travel time: %.1f s", maxTravelTimeSeconds));

    lines.add("");
    lines.add("Congested edges:");

    List<String> congestedEdges = getCongestedEdges();
    if (congestedEdges == null || congestedEdges.isEmpty()) {
        lines.add("None");
    } else {
        for (String edgeId : congestedEdges) {
            lines.add("- " + edgeId);
        }
    }

    lines.add("");
    lines.add("Per edge metrics :");

    if (vehiclesPerEdge == null || vehiclesPerEdge.isEmpty()) {
        lines.add("No per edge data available right now !");
    } else {
		// Iterate over the Map vehiclesPerEdge
        for (Map.Entry<String, Integer> entry : vehiclesPerEdge.entrySet()) {
            String edgeId = entry.getKey(); 		// Get the Key of the Map
            int vehiclesOnEdge = entry.getValue(); 	// Get the Value of the key

            int stoppedOnEdge = 0;
            if (stoppedVehiclesPerEdge != null) {
				// Get the Amount of stopped vehicles in the Current iteration of the for loop
                stoppedOnEdge = stoppedVehiclesPerEdge.getOrDefault(edgeId, 0); // Default is 0 !
            }

			// Get densitiy with my Method
            double density = getDensityForEdge(edgeId);

			// Print the data of the current Edge in this iteration
            lines.add(String.format(java.util.Locale.US,
                    "%s: vehicles=%d, stopped=%d, density=%.1f veh per km",
                    edgeId, vehiclesOnEdge, stoppedOnEdge, density));
        }
    }

    	return lines;
	}

	private void writeLinesWithPageBreak(PDDocument document, List<String> lines) throws IOException {
    final float x = 50f;
    final float topY = 750f;
    final float bottomY = 60f;

    // row height
    final float leading = 15f;

    // Use everywhere HELEVETICA
    PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    float fontSize = 12f;

    float y = topY;

    PDPageContentStream cs = null;

    try {
        cs = startNewPage(document, x, y, font, fontSize);

        for (String line : lines) {
            // Page break BEFORE writing line
            if (y - leading < bottomY) {
                cs.endText();
                cs.close();

                y = topY;
                cs = startNewPage(document, x, y, font, fontSize);
            }

            cs.showText(line == null ? "" : line);
            cs.newLineAtOffset(0, -leading);
            y -= leading;
        }

        cs.endText();
        cs.close();

    } finally {
        // safety in case of exceptions
        if (cs != null) {
            try { cs.close(); } catch (Exception ignore) {}
        }
    }
}

	private PDPageContentStream startNewPage(PDDocument document, float x, float y,
                                        PDType1Font font, float fontSize) throws IOException {

    PDPage page = new PDPage();
    document.addPage(page);

    PDPageContentStream cs = new PDPageContentStream(document, page);
    cs.beginText();
    cs.setFont(font, fontSize);
    cs.newLineAtOffset(x, y);

    return cs;
	}

	
}