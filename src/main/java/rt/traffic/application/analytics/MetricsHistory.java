// Author: Enur redzepi
/**
 * MetricsHistory:
 * Eine klasse die als Hilfe für das Zeichnen dienen wird in dem es eine
 * maximale anzahl an letzten Datensätzen hält innerhalb von listen.
 * Zunächst unterstützt es nur die Anzahl Fahrzeuge und die Durchschnittliche geschwindigkeit, sowie 
 * die Simulationszeit. 
 * 
 * 
 */
package rt.traffic.application.analytics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MetricsHistory {

	private final int maxSize;

	private final List<Double> times = new ArrayList<>();

	private final List<Double> avgSpeeds = new ArrayList<>();

	private final List<Integer> vehicleCounts = new ArrayList<>();

	/**
	 * Constructor 
	 * @param maxSize max amount of stored entry set by the main structurer
	 */
	public MetricsHistory(int maxSize) {
		this.maxSize = Math.max(1, maxSize);
	}

	/**
	 * @param simTimeSeconds simulatin time
	 * @param metrics        our data in a Metrics objekt
	 */
	public void addSnapshot(double simTimeSeconds, Metrics metrics) {
		if (metrics == null) {
			return;
		}
		// If the Metrics object contains data fill them in the lists
		times.add(simTimeSeconds);
		avgSpeeds.add(metrics.avgSpeedPerSecond);
		vehicleCounts.add(metrics.vehicleCount);

		// If the amount is bigger than maxSize delete the first entry and add the newest as the last one on all lists
		while (times.size() > maxSize) {
			times.remove(0);
			avgSpeeds.remove(0);
			vehicleCounts.remove(0);
		}
	}

	// standard getter methods
	/**
	 * @return returns a list with the times which is not manipulateable 
	 */
	public List<Double> getTimes() {
		return Collections.unmodifiableList(times);
	}

	/**
	 * @return returns a list with the average speeds which is not manipulateable
	 */
	public List<Double> getAvgSpeeds() {
		return Collections.unmodifiableList(avgSpeeds);
	}

	/**
	 * @return returns a list with the amount of cars which is not manipulateable
	 */
	public List<Integer> getVehicleCounts() {
		return Collections.unmodifiableList(vehicleCounts);
	}

	/**
	 * reset method fo the history storage
	 * clears the complete lists
	 */
	public void reset() {
		times.clear();
		avgSpeeds.clear();
		vehicleCounts.clear();
	}
}
