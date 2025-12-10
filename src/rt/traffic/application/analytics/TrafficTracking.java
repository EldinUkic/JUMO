// Author: Enur Redzepi 1407686
// packages: java.util.List
//			 java.util.Map

package rt.traffic.application.analytics;

import java.util.Map;
import java.util.List;

public class TrafficTracking {
	public double simTimeSeconds; // Simulation time in seconds
	public List<VehicleTracking> vehicles; // A list which contains all existing vehicles
	public Map<String, Double> edgeLengthinMeters; // A map for the edge-IDs

	/**
	 * 
	 * @param simTimeSeconds   Simulation time in seconds
	 * @param vehicles         A list of all existing vehicles
	 * @param edgeLengthMeters A mapping of the Edge-ID on length in meter
	 */

	// Constructor with 3 parameters
	public TrafficTracking(double simTimeSeconds, List<VehicleTracking> vehicles,
			Map<String, Double> edgeLengthMeters) {
		this.simTimeSeconds = simTimeSeconds;
		this.vehicles = vehicles;
		this.edgeLengthinMeters = edgeLengthMeters;
	}
}