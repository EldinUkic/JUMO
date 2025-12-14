// Author: Enur Redzepi
/**AnalyticsExecution:
 * - Berechnet Werte:
 * + Anzahl Fahrzeuge
 * + Anzahl stoppende Fahrzeuge
 * + Anzahl fahrzeug pro Straße
 * + Anzahl stoppende Fahrzeuge pro Straße
 * + Verkehrsdichte per Straße
 * 
 * Idee:
 * Berecne die Bais Daten und erstelle daraus ein Metrics Objekt.
 * 
*/
package rt.traffic.application.analytics;

// imports for execution
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AnalyticsExecution {

	// Vehicle-Id -> Start time of the vehicle in the simulation
	private final Map<String, Double> vehicleStartTime = new HashMap<>();
	private final Set<String> lastSeenVehicleIds = new HashSet<>();
	private final List<Double> finishedTravelTimes = new ArrayList<>();

	/**
	 * Execute all analytics and metrics for the current simulation
	 *
	 * @param data the stored data about time, vehicles and edges
	 * @return Metrics object with all calculated values: - Average travel time (in
	 *         seconds) - Minimal trip time - Maximal trip time - Short trips (60
	 *         seconds <=) - Medium trips (300 seconds <=) - Long trips (300 seconds
	 *         >) These intervalls are hard coded maybe i will create a way for the
	 *         user to enter these
	 */
	public Metrics executeMetrics(TrafficTracking data) {

		// 0. Handle "no data" cases
		if (data == null || data.vehicles == null || data.vehicles.isEmpty()) {
			// No vehicles available
			return new Metrics(0.0, 0, 0, new HashMap<>(), new HashMap<>(), new HashMap<>());
		}

		// Base counters
		double sumSpeed = 0.0;
		int count = 0;
		int stoppedVehicleCount = 0;

		Map<String, Integer> vehiclesPerEdge = new HashMap<>();
		Map<String, Integer> stoppedPerEdge = new HashMap<>();
		Map<String, Double> densityPerEdge = new HashMap<>();

		// Current simulation time
		double simTimeRightNow = data.simTimeSeconds;

		// All vehicleIds that are present in this tick
		Set<String> currentVehiclesId = new HashSet<>();

		// 1. Standing and driving vehicles, IDs and counts per edge
		for (int i = 0; i < data.vehicles.size(); i++) {
			VehicleTracking v = data.vehicles.get(i);
			sumSpeed = sumSpeed + v.speedMetersPerSecond;
			count++;

			boolean isStopped = v.speedMetersPerSecond <= 0.1;
			if (isStopped) {
				// Then it is a standing vehicle
				stoppedVehicleCount++;
			}

			String edgeId = v.edgeId;
			if (edgeId != null) {
				// Count all vehicles per edge
				int current = vehiclesPerEdge.getOrDefault(edgeId, 0);
				vehiclesPerEdge.put(edgeId, current + 1);

				if (isStopped) {
					// Count standing vehicles per edge
					int currentStopped = stoppedPerEdge.getOrDefault(edgeId, 0);
					stoppedPerEdge.put(edgeId, currentStopped + 1); 
				}
			}

			if (v.id != null) {
				currentVehiclesId.add(v.id);
				// Remember start time if vehicle is seen for the first time
				vehicleStartTime.putIfAbsent(v.id, simTimeRightNow);
			}
		}

		// 2. Find vehicles which are finished there trips
		Set<String> finishedIds = new HashSet<>(lastSeenVehicleIds);
		finishedIds.removeAll(currentVehiclesId); // vehicles that were seen last tick, but not now anymore

		for (String id : finishedIds) {
			// get the start time and remove id from the map
			Double startTime = vehicleStartTime.remove(id);
			if (startTime != null) {
				double travelTime = simTimeRightNow - startTime;
				if (travelTime < 0) {
					travelTime = 0;
				}
				finishedTravelTimes.add(travelTime);
			}
		}

		// prepare lastSeenVehicleIds for next iteration
		lastSeenVehicleIds.clear();
		lastSeenVehicleIds.addAll(currentVehiclesId);

		// 3. Average speed
		double averageSpeed = 0.0;
		if (count > 0) {
			averageSpeed = sumSpeed / count;
		}

		// 4. Density per edge
		if (data.edgeLengthinMeters != null && !data.edgeLengthinMeters.isEmpty()) {
			for (Map.Entry<String, Integer> entry : vehiclesPerEdge.entrySet()) {
				String edgeId = entry.getKey();
				int vehiclesOnEdge = entry.getValue();

				Double lengthMeters = data.edgeLengthinMeters.get(edgeId);
				if (lengthMeters == null || lengthMeters <= 0.0) {
					continue; // No length means no density
				}

				double km = lengthMeters / 1000.0; // convert to km
				double density = vehiclesOnEdge / km; // vehicles per km
				densityPerEdge.put(edgeId, density);
			}
		}

		// 5. Trip time execution
		int finishedTripCount = finishedTravelTimes.size(); // amount of finished trips

		double averageTravelTimeSeconds = 0.0;
		double minTripTime = 0.0;
		double maxTripTime = 0.0;
		int shortTrips = 0;
		int mediumTrips = 0;
		int longTrips = 0;
		if (finishedTripCount > 0) {
			double sumTripTime = 0.0;
			minTripTime = Double.POSITIVE_INFINITY;
			maxTripTime = 0.0;

			for (int i = 0; i < finishedTravelTimes.size(); i++) {
				double t = finishedTravelTimes.get(i);
				sumTripTime = sumTripTime + t;

				if (t < minTripTime) {
					minTripTime = t;
				}
				if (t > maxTripTime) {
					maxTripTime = t;
				}

				if (t < 60.0) {
					shortTrips++;
				} else if (t <= 300.0) {
					mediumTrips++;
				} else {
					longTrips++;
				}
			}

			averageTravelTimeSeconds = sumTripTime / finishedTripCount;
		}
		
		

		// 6. Create and return Metrics object with all values
		Metrics metrics = new Metrics(averageSpeed, count, stoppedVehicleCount, vehiclesPerEdge, stoppedPerEdge,
				densityPerEdge);

		metrics.finishedTripCount = finishedTripCount;
		metrics.averageTravelTimeSeconds = averageTravelTimeSeconds;
		metrics.minTravelTimeSeconds = minTripTime;
		metrics.maxTravelTimeSeconds = maxTripTime;
		metrics.shortTripsCount = shortTrips;
		metrics.mediumTripsCount = mediumTrips;
		metrics.longTripsCount = longTrips;

		return metrics;
	}
}