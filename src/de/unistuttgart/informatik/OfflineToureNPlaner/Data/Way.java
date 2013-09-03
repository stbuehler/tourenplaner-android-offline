package de.unistuttgart.informatik.OfflineToureNPlaner.Data;

import java.io.Serializable;

public final class Way implements Serializable {
	private static final long serialVersionUID = 1L;

	final Position[] waypoints;
	final int eucliddistance;
	final int traveltime;

	public Way(Position[] wayPoints, int euclidDistance, int travelTime) {
		waypoints = wayPoints;
		eucliddistance = euclidDistance;
		traveltime = travelTime;
	}

	public Position[] wayPoints() {
		return waypoints;
	}

	public int euclidDistance() {
		return eucliddistance;
	}

	public int travelTime() {
		return traveltime;
	}
}
