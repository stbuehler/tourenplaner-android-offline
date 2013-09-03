package de.unistuttgart.informatik.OfflineToureNPlaner.MapsForge;

import org.mapsforge.android.maps.MapView;
import org.mapsforge.android.maps.Projection;
import org.mapsforge.android.maps.overlay.ItemizedOverlay;
import org.mapsforge.core.GeoPoint;
import org.mapsforge.core.MercatorProjection;

import de.unistuttgart.informatik.OfflineToureNPlaner.Data.Position;
import de.unistuttgart.informatik.OfflineToureNPlaner.Data.Way;
import de.unistuttgart.informatik.OfflineToureNPlaner.Events.IDispatcher;
import de.unistuttgart.informatik.OfflineToureNPlaner.Events.Listener;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathDashPathEffect;
import android.graphics.Point;
import android.graphics.PointF;
import android.os.Handler;
import android.os.Looper;

public class CustomOverlay extends ItemizedOverlay<NodeOverlayItem> {
	private final de.unistuttgart.informatik.OfflineToureNPlaner.Data.State state;

	public static GeoPoint toPoint(Position pos) {
		return pos == null ? null : new GeoPoint(pos.latitudeE6(), pos.longitudeE6());
	}
	public static Position toPosition(GeoPoint point) {
		return point == null ? null : Position.fromE6(point.latitudeE6, point.longitudeE6);
	}

	// Position Overlay stuff
	private final Listener<Position> pos_watcher;
	private final NodeOverlayItem[] items;
	private int dragging = -1;
	private Position dragSave;

	// Way Overlay stuff
	private final Listener<Way> way_watcher;
	private final Paint wayPaint;
	private WayZoom wayZoom;

	// for some stupid reason mapsforge sends some events from background threads
	private Handler eventHandler;

	public CustomOverlay(Context context, final de.unistuttgart.informatik.OfflineToureNPlaner.Data.State state) {
		super(null);
		eventHandler = new Handler();
		this.state = state;

		// Position Overlay stuff
		items = new NodeOverlayItem[3];
		items[0] = new NodeOverlayItem(context, NodeOverlayItem.MarkerType.START);
		items[1] = new NodeOverlayItem(context, NodeOverlayItem.MarkerType.END);
		items[2] = new NodeOverlayItem(context, NodeOverlayItem.MarkerType.GPS);
		items[0].setPosition(state.getStart());
		items[1].setPosition(state.getEnd());
		items[2].setPosition(state.getGps());

		pos_watcher = new Listener<Position>() {
			@Override
			public void handle(Position param, IDispatcher<Position> dispatcher) {
				items[0].setPosition(state.getStart());
				items[1].setPosition(state.getEnd());
				Position gps = state.getGps();
				items[2].setPosition(gps);
				if (state.getFollowGps() && gps != null) {
					internalMapView.setCenter(toPoint(gps));
				}
				requestRedraw();
			}
		};

		state.getDispatcher_start().add(pos_watcher);
		state.getDispatcher_end().add(pos_watcher);
		state.getDispatcher_gps().add(pos_watcher);

		// Way Overlay stuff
		/* paint arrow "path" */
		final Path arrowPath = new Path();
		arrowPath.moveTo(3.f, 0.f);
		arrowPath.lineTo(0.f, -3.f);
		arrowPath.lineTo(5.f, -3.f);
		arrowPath.lineTo(8.f, 0.f);
		arrowPath.lineTo(5.f, 3.f);
		arrowPath.lineTo(0.f, 3.f);

		wayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		wayPaint.setStyle(Paint.Style.STROKE);
		wayPaint.setColor(Color.BLUE);
		wayPaint.setAlpha(128);
		wayPaint.setPathEffect(new PathDashPathEffect(arrowPath, 8.f, -3.f, PathDashPathEffect.Style.MORPH));

		way_watcher = new Listener<Way>() {
			@Override
			public void handle(Way param, IDispatcher<Way> dispatcher) {
				android.util.Log.d("CustomOverlay", "updating way");
				if (param != null) {
					wayZoom = new WayZoom(param);
				} else {
					wayZoom = null;
				}
				requestRedraw();
			}
		};

		wayZoom = null;
		if (state.getWay() != null) wayZoom = new WayZoom(state.getWay());

		state.getDispatcher_way().add(way_watcher);
	}

	public void detach() {
		state.getDispatcher_start().remove(pos_watcher);
		state.getDispatcher_end().remove(pos_watcher);
		state.getDispatcher_gps().remove(pos_watcher);
		state.getDispatcher_way().remove(way_watcher);
	}

	@Override
	protected String getThreadName() {
		return "CustomOverlay";
	}

	@Override
	public boolean onLongPress(final GeoPoint geoPoint, MapView mapView) {
		eventHandler.post(new Runnable() {
			@Override
			public void run() {
				if (state.getGps() != null) {
					if (state.getEnd() == null) {
						state.setEnd(toPosition(geoPoint));
					} else if (state.getStart() == null) {
						state.setStart(toPosition(geoPoint));
					}
				} else if (state.getStart() == null) {
					state.setStart(toPosition(geoPoint));
				} else if (state.getEnd() == null) {
					state.setEnd(toPosition(geoPoint));
				}
			}
		});
		return true;
	}

	@Override
	protected boolean onDragStart(int index) {
		assert Looper.myLooper() == Looper.getMainLooper(): "not in UI thread";
		if (index > 1) return false;
		if (dragging != -1) onDragCancel();
		dragging = index;
		dragSave = items[dragging].getPosition();
		state.setIsDragging(true);
		return true;
	}

	@Override
	public void onDragMove(GeoPoint geoPoint, MapView mapView) {
		assert Looper.myLooper() == Looper.getMainLooper(): "not in UI thread";
		Position p = toPosition(geoPoint);

		if (dragging == 0) {
			state.setStart(p);
		} else if (dragging == 1) {
			state.setEnd(p);
		}
	}

	@Override
	public void onDragStop(GeoPoint geoPoint, MapView mapView) {
		assert Looper.myLooper() == Looper.getMainLooper(): "not in UI thread";
		Position p = toPosition(geoPoint);

		int wasDragging = dragging;
		dragging = -1;
		dragSave = null;

		state.setIsDragging(false);
		if (wasDragging == 0) {
			state.setStart(p);
		} else if (wasDragging == 1) {
			state.setEnd(p);
		}
	}

	@Override
	public void onDragCancel() {
		assert Looper.myLooper() == Looper.getMainLooper(): "not in UI thread";

		int wasDragging = dragging;
		Position p = dragSave;
		dragging = -1;
		dragSave = null;

		state.setIsDragging(false);
		if (wasDragging == 0) {
			state.setStart(p);
		} else if (wasDragging == 1) {
			state.setEnd(p);
		}
	}

	@Override
	protected void drawOverlayBitmap(Canvas canvas, Point drawPosition, Projection projection, byte zoomLevel) {
		final WayZoom wz = wayZoom;
		if (wz != null) {
			final Path path = new Path();
			GeoPoint topLeft = projection.fromPixels(0, 0);
			GeoPoint bottomRight = projection.fromPixels(this.internalMapView.getWidth(), this.internalMapView.getHeight());
			final PointF drawPos = new PointF((float) MercatorProjection.longitudeToPixelX(topLeft.getLongitude(), zoomLevel),
					(float) MercatorProjection.latitudeToPixelY(topLeft.getLatitude(), zoomLevel));
			wz.setupPath(path, drawPos, zoomLevel, toPosition(topLeft), toPosition(bottomRight));
			canvas.drawPath(path, wayPaint);
		}

		super.drawOverlayBitmap(canvas, drawPosition, projection, zoomLevel);
	}

	@Override
	protected NodeOverlayItem createItem(int ndx) {
		if (ndx < 0 || ndx >= items.length) return null;
		return items[ndx];
	}

	@Override
	public int size() {
		return 3;
	}
}
