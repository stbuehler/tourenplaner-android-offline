package de.unistuttgart.informatik.OfflineToureNPlaner.MapsForge;

import java.io.File;
import java.security.InvalidParameterException;
import java.util.List;

import org.mapsforge.android.maps.IMapActivity;
import org.mapsforge.android.maps.MapView;
import org.mapsforge.android.maps.overlay.Overlay;
import org.mapsforge.core.GeoPoint;
import org.mapsforge.core.MercatorProjection;
import org.mapsforge.map.reader.header.FileOpenResult;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.RectF;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import android.widget.TableLayout.LayoutParams;

import de.unistuttgart.informatik.OfflineToureNPlaner.R;
import de.unistuttgart.informatik.OfflineToureNPlaner.Data.Position;
import de.unistuttgart.informatik.OfflineToureNPlaner.Data.State;
import de.unistuttgart.informatik.OfflineToureNPlaner.UI.StateContext;

public class MapViewFragment extends Fragment {
	protected MapView mapView;
	protected CustomOverlay overlay;
	protected State state;
	protected Bundle savedState;

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		if (!(activity instanceof StateContext)) throw new InvalidParameterException("unexpected activity");
		this.state = ((StateContext) activity).getState();
	}

	private class MapViewContext extends ContextWrapper implements IMapActivity {
		private int lastId;
		public MapViewContext(Context base) {
			super(base);
			lastId = 0;
		}

		@Override
		public Context getContext() {
			return this;
		}

		@Override
		public int getMapViewId() {
			return lastId++;
		}

		@Override
		public void registerMapView(MapView arg0) {
		}
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putSerializable("mapCenter", mapView.getMapPosition().getMapCenter());
		outState.putByte("mapZoom", mapView.getMapPosition().getZoomLevel());
	}

	public void restoreState() {
		if (savedState != null) {
			mapView.setCenter((GeoPoint) savedState.getSerializable("mapCenter"));
			byte zoomDiff = (byte) (savedState.getByte("mapZoom") - mapView.getMapPosition().getZoomLevel());
			mapView.zoom(zoomDiff, (float) Math.pow(2, zoomDiff));
		}
	}

	@Override
	public View onCreateView (LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		mapView = new MapView(new MapViewContext(this.getActivity()));
		mapView.setLayoutParams(new LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT));
		mapView.setClickable(true);
		mapView.setLongClickable(true);
		mapView.setBuiltInZoomControls(true);
		mapView.getMapZoomControls().setZoomControlsGravity(Gravity.TOP | Gravity.RIGHT);

		overlay = new CustomOverlay(this.getActivity(), state);
		final List<Overlay> overlays = mapView.getOverlays();
		synchronized (overlays) {
			overlays.add(overlay);
		}

		this.savedState = savedInstanceState;
		restoreState();

		return mapView;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		android.util.Log.d("MapViewFragment", "destroy");
		if (overlay != null) {
			overlay.detach();
			overlay.interrupt();
			overlay = null;
		}
		if (mapView != null) {
			mapView.getOverlays().clear();
			mapView.destroy();
			mapView = null;
		}
		System.gc();
	}

	@Override
	public void onPause() {
		super.onPause();
		if (mapView != null) mapView.onPause();
	}

	@Override
	public void onResume() {
		super.onResume();
		if (mapView != null) mapView.onResume();

		final String offlineMapLocation = PreferenceManager.getDefaultSharedPreferences(getActivity()).getString("mapfile", "");

		mapView.setVisibility(View.VISIBLE);
		if (offlineMapLocation.isEmpty() || !new File(offlineMapLocation).exists()) {
			Toast.makeText(this.getActivity(), "No map file set or map file doesn't exist", Toast.LENGTH_LONG).show();
			mapView.setVisibility(View.GONE);
		} else {
			FileOpenResult result;
			try {
				result = mapView.setMapFile(new File(offlineMapLocation));
			} catch (Exception e) {
				result = new FileOpenResult(getResources().getString(R.string.map_file_error));
			}
			if (!result.isSuccess()) {
				Log.e("Offline ToureNPlaner", "Couldn't find map file: " + result.getErrorMessage());
				Toast.makeText(this.getActivity(), result.getErrorMessage(), Toast.LENGTH_LONG).show();
				mapView.setVisibility(View.GONE);
			}
		}

		restoreState();
		if (!mapView.getMapPosition().isValid()) {
			mapView.setCenter(new GeoPoint(48.7456550, 9.1070070));
		}
	}

	public void setCenter(Position pos) {
		mapView.setCenter(CustomOverlay.toPoint(pos));
	}

	public RectF getMapVisibleRect() {
		GeoPoint center = mapView.getMapPosition().getMapCenter();
		byte zoomLevel = mapView.getMapPosition().getZoomLevel();
		int pixWidth = 4096, pixHeight = 4096; // current width/height values are too small due to IME resize
		double pixX = MercatorProjection.longitudeToPixelX(center.getLongitude(), zoomLevel);
		double pixY = MercatorProjection.latitudeToPixelY(center.getLatitude(), zoomLevel);
		final RectF result = new RectF(
				(float) MercatorProjection.pixelXToLongitude(pixX - pixWidth/2, zoomLevel),
				(float) MercatorProjection.pixelYToLatitude(pixY - pixHeight/2, zoomLevel),
				(float) MercatorProjection.pixelXToLongitude(pixX + pixWidth/2, zoomLevel),
				(float) MercatorProjection.pixelYToLatitude(pixY + pixHeight/2, zoomLevel));
		return result;
	}
}
