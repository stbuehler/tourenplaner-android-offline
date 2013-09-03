package de.unistuttgart.informatik.OfflineToureNPlaner.MapsForge;

import org.mapsforge.android.maps.overlay.ItemizedOverlay;
import org.mapsforge.android.maps.overlay.OverlayItem;

import de.unistuttgart.informatik.OfflineToureNPlaner.R;
import de.unistuttgart.informatik.OfflineToureNPlaner.Data.Position;

import android.content.Context;
import android.graphics.drawable.Drawable;

public final class NodeOverlayItem extends OverlayItem {
	public static enum MarkerType {
		START, END, GPS
	}

	private static Drawable imageMarkerStart = null;
	private static Drawable imageMarkerEnd = null;
	private static Drawable imageMarkerGPS = null;

	private static synchronized Drawable loadImage(Context context, MarkerType markerType) {
		if (imageMarkerStart == null) {
			imageMarkerStart = context.getResources().getDrawable(R.drawable.markerstart);
			ItemizedOverlay.boundCenterBottom(imageMarkerStart);
			imageMarkerEnd = context.getResources().getDrawable(R.drawable.markerend);
			ItemizedOverlay.boundCenterBottom(imageMarkerEnd);
			imageMarkerGPS = context.getResources().getDrawable(R.drawable.holo_light_10_device_access_location_searching);
			ItemizedOverlay.boundCenter(imageMarkerGPS);
		}
		switch (markerType) {
		case START:
			return imageMarkerStart;
		case END:
			return imageMarkerEnd;
		case GPS:
			return imageMarkerGPS;
		}
		return null;
	}

	private Position position;

	public NodeOverlayItem(Context context, MarkerType markerType) {
		super(null, null, null, loadImage(context, markerType));
	}

	public Position getPosition() {
		return position;
	}

	public void setPosition(Position position) {
		this.position = position;
		setPoint(CustomOverlay.toPoint(position));
	}
}
