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

import java.io.IOException;// Imports for execution
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.eclipse.sumo.libtraci.Simulation;

import rt.traffic.config.SumoPath;

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
	 *         propotion of stopped vehicles on a choosen edge
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
	 * @return value of the key edgeId in the map therefore the density (vehicles
	 *         per km)
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
		// 1) Export-Ordner aus SumoPath holen
		String exportDir = SumoPath.getExportPath();

		// 2) Ordner sicherstellen
		Path dirPath = Paths.get(exportDir);
		Files.createDirectories(dirPath);

		// 3) Dateinamen festlegen (z.B. mit Zeitstempel)
		String fileName = "traffic_metrics_" + String.format("%.2f", Simulation.getTime()) + ".pdf";

		// 4) Vollständigen Pfad bauen
		Path fullPath = dirPath.resolve(fileName);

		// 5) PDF erzeugen & speichern
		try (PDDocument document = new PDDocument()) {
			PDPage page = new PDPage();
			document.addPage(page);

			try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {

				contentStream.beginText();
				contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 16);
				contentStream.newLineAtOffset(50, 750);
				contentStream.showText("Traffic Metrics Report");

				contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);

				newLine(contentStream, 0, -30,
						String.format("Simulation Time: %.2f sec", Simulation.getTime()));
				newLine(contentStream, 0, -30,
						String.format("Average speed: %.2f km/h", getAverageSpeedKmh()));
				newLine(contentStream, 0, -15,
						"Vehicle count: " + vehicleCount);
				newLine(contentStream, 0, -15,
						"Stopped vehicles: " + stoppedVehicleCount);
				newLine(contentStream, 0, -15,
						String.format("Stopped ratio: %.2f %%%%", getStoppedRatio() * 100.0));

				newLine(contentStream, 0, -25,
						String.format("Finished trips: %d", finishedTripCount));
				newLine(contentStream, 0, -15,
						String.format("Average travel time: %.1f s", averageTravelTimeSeconds));
				newLine(contentStream, 0, -15,
						String.format("Min travel time: %.1f s", minTravelTimeSeconds));
				newLine(contentStream, 0, -15,
						String.format("Max travel time: %.1f s", maxTravelTimeSeconds));

				newLine(contentStream, 0, -30,
						"Congested edges (>= 60% stopped & min vehicles):");

				List<String> congestedEdges = getCongestedEdges();
				if (congestedEdges == null || congestedEdges.isEmpty()) {
					newLine(contentStream, 0, -15, "None");
				} else {
					for (String edgeId : congestedEdges) {
						newLine(contentStream, 0, -15, "- " + edgeId);
					}
				}

				newLine(contentStream, 0, -30, "Per-edge metrics (vehicles / density):");

				if (vehiclesPerEdge == null || vehiclesPerEdge.isEmpty()) {
					newLine(contentStream, 0, -15, "No per-edge data available.");
				} else {
					for (Map.Entry<String, Integer> entry : vehiclesPerEdge.entrySet()) {
						String edgeId = entry.getKey();
						int vehiclesOnEdge = entry.getValue();

						int stoppedOnEdge = 0;
						if (stoppedVehiclesPerEdge != null) {
							stoppedOnEdge = stoppedVehiclesPerEdge.getOrDefault(edgeId, 0);
						}

						double density = getDensityForEdge(edgeId); // veh/km

						String line = String.format(
								"%s: vehicles = %d, stopped = %d, density= %.1f veh per km",
								edgeId, vehiclesOnEdge, stoppedOnEdge, density);
						newLine(contentStream, 0, -15, line);
					}
				}

				contentStream.endText();
			}

			document.save(fullPath.toString());
		}

		System.out.println("[Metrics] PDF exportiert nach: " + fullPath);
	}

	// Newline methode
	private void newLine(PDPageContentStream cs, float dx, float dy, String text) throws IOException {
		cs.newLineAtOffset(dx, dy);
		cs.showText(text);
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
			writer.println(
					"edgeId" + sep + "vehiclesOnEdge" + sep + "stoppedVehiclesOnEdge" + sep + "densityVehiclesPerKm");

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
							density);
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
}