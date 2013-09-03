package de.unistuttgart.informatik.OfflineToureNPlaner.Data;

import de.funroll_loops.osmfind.libosmfind.GeoPoint;
import de.funroll_loops.osmfind.libosmfind.OsmItem;

public class OsmFindResult {
	private final String label;
	private final Position position;

	public OsmFindResult(OsmItem item) {
		StringBuilder buildLabel = new StringBuilder();
		for (int i = 0, l = item.strCount(); i < l; ++i) {
			if (i > 0) buildLabel.append(", ");
			buildLabel.append(item.strAt(i));
		}
		label = buildLabel.toString();
		final GeoPoint p = item.geoPointAt(0);
		position = Position.from(p.lat, p.lon);
	}

	public OsmFindResult(String label, Position position) {
		this.label = label;
		this.position = position;
	}

	public String getLabel() {
		return label;
	}

	public Position getPosition() {
		return position;
	}

	@Override
	public String toString() {
		return position.toString() + ": " + label;
	}
}
