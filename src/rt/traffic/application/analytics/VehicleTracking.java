// Author: Enur Redzepi 
/**
 * VehicleTracking:
 * Diese Klasse bildet ein einzelnes Fahrzeug zu einem bestimmten Zeitpunkt der Simulation ab
 * Sie hält:
 * - Fahrzeug Namen
 * - Die Kante auf der das Fahrzeug ist
 * - und die Geschwindigkeit
 */
package rt.traffic.application.analytics;

public class VehicleTracking {
	public String id; // Unique id for each vehicle
	public String edgeId; // Unique id for each street
	public double speedMetersPerSecond; // Speed in mps

	/**
	 * 
	 * @param id		unqiue vehicle ID 
	 * @param edgeId		ID of the edge where the vehicle is driving
	 * @param speedMetersPerSecond	Speed in meter per second
	 */
	// Constructor with 3 parameter
	public VehicleTracking(String id, String edgeId, double speedMetersPerSecond) {
		this.id = id;
		this.edgeId = edgeId;
		this.speedMetersPerSecond = speedMetersPerSecond;
	}

}