package de.unistuttgart.informatik.OfflineToureNPlaner.UI;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnActionExpandListener;
import android.view.Window;

import android.widget.SearchView;
import android.widget.SearchView.OnCloseListener;

import de.unistuttgart.informatik.OfflineToureNPlaner.R;
import de.unistuttgart.informatik.OfflineToureNPlaner.Data.OsmFindResult;
import de.unistuttgart.informatik.OfflineToureNPlaner.Data.Position;
import de.unistuttgart.informatik.OfflineToureNPlaner.Data.State;
import de.unistuttgart.informatik.OfflineToureNPlaner.Data.Way;
import de.unistuttgart.informatik.OfflineToureNPlaner.Events.IDispatcher;
import de.unistuttgart.informatik.OfflineToureNPlaner.Events.Listener;
import de.unistuttgart.informatik.OfflineToureNPlaner.Handlers.NodeSearchHandler;
import de.unistuttgart.informatik.OfflineToureNPlaner.Handlers.OSMFindHandler;
import de.unistuttgart.informatik.OfflineToureNPlaner.Handlers.OSMFindHandler.OSMEnergizeException;
import de.unistuttgart.informatik.OfflineToureNPlaner.Handlers.OSMFindHandler.OnOsmFound;
import de.unistuttgart.informatik.OfflineToureNPlaner.Handlers.ShortestPathHandler;
import de.unistuttgart.informatik.OfflineToureNPlaner.MapsForge.MapViewFragment;
import de.unistuttgart.informatik.OfflineToureNPlaner.UI.Adapters.SearchResultAdapter;
import android.app.Activity;
import android.app.FragmentManager;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.SearchView.OnQueryTextListener;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements StateContext, OnSharedPreferenceChangeListener {
	private MapViewFragment mapsforgeFragment;

	private State state;
	private NodeSearchHandler nodeSearchHandler;
	private ShortestPathHandler shortestPathHandler;

	private OSMFindHandler osmFindHandler;
	private State.WorkingReference searchWorkingReference = null;

	private MenuItem searchItem;
	private ListView resultListView;

	private TextView textoverlayView;
	private Listener<Way> way_watcher;

	private Listener<Boolean> progress_watcher, followGps_watcher;

	private MenuItem followGpsItem, activateGpsItem;
	private LocationHandler locationHandler;
	private Drawable iconGpsFound, iconGpsSearching, iconGpsOff;
	private Toast gpsToast;

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putSerializable("state", state);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		if (savedInstanceState != null) {
			state = (State) savedInstanceState.getSerializable("state");
		} else {
			state = new State();
		}
		final Resources r = getResources();
		iconGpsFound = r.getDrawable(R.drawable.holo_dark_10_device_access_location_found);
		iconGpsSearching = r.getDrawable(R.drawable.holo_dark_10_device_access_location_searching);
		iconGpsOff = r.getDrawable(R.drawable.holo_dark_10_device_access_location_off);
		locationHandler = new LocationHandler(state, this);

		mapsforgeFragment = null;
		nodeSearchHandler = null;
		shortestPathHandler = null;

		/* this will restore previous attached fragments, so {@link #state} has to be initialized before */
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setDefaultKeyMode(DEFAULT_KEYS_SEARCH_LOCAL);
		setContentView(R.layout.activity_main);
		setProgressBarIndeterminate(true);
		setProgressBarIndeterminateVisibility(false);

		progress_watcher = new Listener<Boolean>() {
			@Override
			public void handle(Boolean param, IDispatcher<Boolean> dispatcher) {
				setProgressBarIndeterminateVisibility(param.booleanValue());
			}
		};
		state.getDispatcher_working().add(progress_watcher);

		followGps_watcher = new Listener<Boolean>() {
			@Override
			public void handle(Boolean param, IDispatcher<Boolean> dispatcher) {
				updateGPSIcon();
			}
		};
		state.getDispatcher_followGps().add(followGps_watcher);

		resultListView = (ListView) findViewById(R.id.search_results);
		resultListView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				OsmFindResult selectedItem = (OsmFindResult) resultListView.getAdapter().getItem(position);
				doSearch(null);
				searchItem.collapseActionView();
				state.setEnd(selectedItem.getPosition());
				if (mapsforgeFragment != null) {
					state.setFollowGps(false);
					mapsforgeFragment.setCenter(state.getEnd());
				}
			}
		});

		textoverlayView = (TextView) findViewById(R.id.textoverlay);
		way_watcher = new Listener<Way>() {
			@Override
			public void handle(Way param, IDispatcher<Way> dispatcher) {
				updateTextoverlayView();
			}
		};
		state.getDispatcher_way().add(way_watcher);

		handleSearch(getIntent());

		PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);
	}

	@Override
	protected void onNewIntent(Intent intent) {
		handleSearch(intent);
	}

	private void doSearch(String query) {
		final Context ctx = this;

		if (query == null || query.isEmpty()) {
			if (null != osmFindHandler) osmFindHandler.osmfind_cancel();
			resultListView.setAdapter(null);
			resultListView.setVisibility(View.GONE);

			if (searchWorkingReference != null) {
				State.WorkingReference r = searchWorkingReference;
				searchWorkingReference = null;
				r.stop();
			}
			return;
		}

		android.util.Log.d("OfflineTP", "searching for " + query);
		if (null == osmFindHandler) {
			final String osmFindDir = PreferenceManager.getDefaultSharedPreferences(this).getString("osmfinddirectory", "");
			try {
				osmFindHandler = new OSMFindHandler(this, osmFindDir);
			} catch (OSMEnergizeException e) {
				e.printStackTrace();
			}
		}
		if (null == osmFindHandler) {
			Toast.makeText(this, "Search not available", Toast.LENGTH_LONG).show();
			return;
		}

		final SearchResultAdapter adapter = new SearchResultAdapter(ctx);

		if (searchWorkingReference == null) {
			searchWorkingReference = state.startWorking();
		}
		// geo search too slow for now
		osmFindHandler.osmfind(query, null /*mapsforgeFragment.getMapVisibleRect()*/, new OnOsmFound() {
			@Override
			public void onOsmFound(List<OsmFindResult> results) {
				if (searchWorkingReference != null) {
					State.WorkingReference r = searchWorkingReference;
					searchWorkingReference = null;
					r.stop();
				}
				adapter.setData(results);
				if (adapter.getCount() == 0) {
					Toast.makeText(ctx, "Didn't find anything", Toast.LENGTH_LONG).show();
					resultListView.setVisibility(View.GONE);
					resultListView.setAdapter(null);
				} else {
					resultListView.setVisibility(View.VISIBLE);
					resultListView.setAdapter(adapter);
				}
				resultListView.invalidate();
			}
			@Override
			public void onOsmFoundProgress(OsmFindResult result, List<OsmFindResult> results) {
				adapter.setData(results);
				if (adapter.getCount() > 0) {
					resultListView.setVisibility(View.VISIBLE);
					resultListView.setAdapter(adapter);
					resultListView.invalidate();
				}
			}
		});
	}

	private void handleSearch(Intent intent) {
		if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
			final String query = intent.getStringExtra(SearchManager.QUERY);
			doSearch(query);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_main, menu);

		final SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
		final SearchView searchView = (SearchView) menu.findItem(R.id.search).getActionView();
		searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
		searchView.setSubmitButtonEnabled(true);
		searchView.setQueryRefinementEnabled(true);

		followGpsItem = menu.findItem(R.id.action_follow_gps);
		activateGpsItem = menu.findItem(R.id.action_activate_gps);
		if (!PreferenceManager.getDefaultSharedPreferences(this).getBoolean("activegps", true)) {
			followGpsItem.setEnabled(false);
			activateGpsItem.setChecked(false);
			state.setFollowGps(false);
			state.setGps(null);
		} else {
			locationHandler.start();
		}
		updateGPSIcon();

		searchItem = menu.findItem(R.id.search);
		searchItem.setOnActionExpandListener(new OnActionExpandListener() {
			@Override
			public boolean onMenuItemActionCollapse(MenuItem arg0) {
				android.util.Log.d("OfflineTP", "collapsed search");
				doSearch(null);
				return true;
			}

			@Override
			public boolean onMenuItemActionExpand(MenuItem arg0) {
				return true;
			}
		});

		searchView.setOnQueryTextListener(new OnQueryTextListener() {
			@Override
			public boolean onQueryTextSubmit(String arg0) {
				android.util.Log.d("OfflineTP", "current query text: '" + arg0 + "'");
				if (arg0.isEmpty()) {
					android.util.Log.d("OfflineTP", "hide search results");
					doSearch(null);
					searchView.setIconified(true);
				}
				return false;
			}

			@Override
			public boolean onQueryTextChange(String arg0) {
				android.util.Log.d("OfflineTP", "text change: current query text: '" + arg0 + "'");
				if (arg0.length() > 3 || arg0.isEmpty()) doSearch(arg0);
				return false;
			}
		});

		searchView.setOnCloseListener(new OnCloseListener() {
			@Override
			public boolean onClose() {
				android.util.Log.d("OfflineTP", "Closed search");
				doSearch(null);
				return true;
			}
		});

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.action_clear:
			state.setStart(null);
			state.setEnd(null);
			return true;
		case R.id.action_follow_gps:
			if (!activateGpsItem.isChecked()) return true;
			state.setFollowGps(!state.getFollowGps());
			updateGPSIcon();
			if (state.getFollowGps()) {
				final Position gps = state.getGps();
				state.setGps(null);
				state.setGps(gps);
			}
			return true;
		case R.id.action_activate_gps:
			boolean status = !activateGpsItem.isChecked();
			activateGpsItem.setChecked(status);
			followGpsItem.setEnabled(status);
			if (!status) {
				locationHandler.stop();
				state.setGps(null);
			} else {
				locationHandler.start();
			}
			PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean("activegps", item.isChecked()).apply();
			return true;
		case R.id.action_about:
			Intent about = new Intent(this, AboutActivity.class);
			startActivity(about);
			return true;
		case R.id.action_settings:
			Intent settings = new Intent(this, SettingsActivity.class);
			startActivity(settings);
			return true;
		case R.id.action_benchmark:
			Intent benchmark = new Intent(this, BenchmarkActivity.class);
			startActivity(benchmark);
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	protected void onStart() {
		super.onStart();

		if (mapsforgeFragment == null) {
			/* if activity got restored, the fragment probably already exists - don't replace it, just get the reference */
			FragmentManager m = getFragmentManager();
			mapsforgeFragment = (MapViewFragment) m.findFragmentByTag("mapsforgeFragment");
			if (mapsforgeFragment == null) {
				/* a new activity instance - create fragment */
				mapsforgeFragment = new MapViewFragment();
				getFragmentManager().beginTransaction().add(R.id.fragment_root, mapsforgeFragment, "mapsforgeFragment").commit();
			}
		} else {
			getFragmentManager().beginTransaction().attach(mapsforgeFragment).commit();
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (nodeSearchHandler != null) {
			nodeSearchHandler.detach();
			nodeSearchHandler = null;
		}
		if (shortestPathHandler == null) {
			shortestPathHandler.detach();
			shortestPathHandler = null;
		}
		if (state != null) {
			if (way_watcher != null) {
				state.getDispatcher_way().remove(way_watcher);
				way_watcher = null;
			}
			if (progress_watcher != null) {
				state.getDispatcher_working().remove(progress_watcher);
				progress_watcher = null;
			}
			if (followGps_watcher != null) {
				state.getDispatcher_followGps().remove(followGps_watcher);
				followGps_watcher = null;
			}
			state = null;
		}
		mapsforgeFragment = null;
		osmFindHandler = null;
		PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);
		locationHandler.stop();
	}

	@Override
	public void onResume() {
		super.onResume();
		final String graphLocation = PreferenceManager.getDefaultSharedPreferences(this).getString("graphfile", "");

		if (graphLocation.isEmpty()) {
			Toast.makeText(this, "Graph file not set yet", Toast.LENGTH_LONG).show();
		} else {
			try {
				if (nodeSearchHandler == null) nodeSearchHandler = new NodeSearchHandler(graphLocation, state);
				if (shortestPathHandler == null) shortestPathHandler = new ShortestPathHandler(graphLocation, state);
			} catch (IOException e) {
				Log.e("Offline ToureNPlaner", "Couldn't read graph file: " + e.getLocalizedMessage());
				Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
			}
		}
		updateTextoverlayView();
	}

	@Override
	public State getState() {
		if (null == state) throw new RuntimeException("state not initialized yet");
		return state;
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (key.equals("graphfile")) {
			if (nodeSearchHandler != null) {
				nodeSearchHandler.detach();
				nodeSearchHandler = null;
			}
			if (shortestPathHandler != null) {
				shortestPathHandler.detach();
				shortestPathHandler = null;
			}
			if (state != null) {
				final String graphLocation = PreferenceManager.getDefaultSharedPreferences(this).getString("graphfile", "");

				try {
					nodeSearchHandler = new NodeSearchHandler(graphLocation, state);
					shortestPathHandler = new ShortestPathHandler(graphLocation, state);
				} catch (IOException e) {
					Log.e("Offline ToureNPlaner", "Couldn't read graph file: " + e.getLocalizedMessage());
					Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
				}
			}
		} else if (key.equals("osmfinddirectory")) {
			if (null != osmFindHandler) {
				osmFindHandler.osmfind_cancel();
				osmFindHandler = null;
			}
			final String osmFindDir = PreferenceManager.getDefaultSharedPreferences(this).getString("osmfinddirectory", "");
			try {
				osmFindHandler = new OSMFindHandler(this, osmFindDir);
			} catch (OSMEnergizeException e) {
				e.printStackTrace();
			}
			if (null == osmFindHandler) {
				Toast.makeText(this, "Search not available", Toast.LENGTH_LONG).show();
				return;
			}
		}
	}

	void updateTextoverlayView() {
		final Locale locale = Locale.getDefault();
		Way way = state.getWay();
		if (way == null) {
			textoverlayView.setText("");
			return;
		}
		String dist = String.format(locale, "%.1f km", way.euclidDistance() / 1000.0);
		String time;
		if (way.travelTime() >= 3600) {
			int minutes = way.travelTime() / 60;
			time = String.format(locale, "%d h %d min", minutes / 60, minutes % 60);
		} else if (way.travelTime() >= 60) {
			time = String.format(locale, "%.1f min", way.travelTime() / 60.0);
		} else {
			time = String.format(locale, "%d s", way.travelTime());
		}
		textoverlayView.setText(String.format(locale, getResources().getString(R.string.way_text_overlay), dist, time));
	}

	private void setGPSIcon(Drawable icon, int alertMessageID) {
		if (followGpsItem.getIcon() != icon) {
			followGpsItem.setIcon(icon);
			if (null != gpsToast) {
				gpsToast.setText(alertMessageID);
				gpsToast.setDuration(Toast.LENGTH_LONG);
			} else {
				gpsToast = Toast.makeText(this, alertMessageID, Toast.LENGTH_LONG);
			}
			gpsToast.show();
		}
	}

	void updateGPSIcon() {
		if (null == followGpsItem || null == state) return;
		if (activateGpsItem.isChecked() && state.getFollowGps()) {
			if (null != state.getGps()) {
				setGPSIcon(iconGpsFound, R.string.using_gps);
			} else {
				setGPSIcon(iconGpsSearching, R.string.searching_gps);
			}
		} else {
			setGPSIcon(iconGpsOff, R.string.unused_gps);
		}
	}

	private class LocationHandler implements LocationListener  {
		private final State state;
		private final LocationManager locManager;
		private Location currentLocation;

		public LocationHandler(State state, Context context) {
			this.state = state;
			locManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
		}

		public void start() {
			locManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
			currentLocation = locManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
			android.util.Log.d("OfflineTP", "currentLocation: " + currentLocation);
			state.setGps(Position.from(currentLocation));
			updateGPSIcon();
		}

		public void stop() {
			locManager.removeUpdates(this);
			state.setGps(null);
			updateGPSIcon();
		}

		@Override
		public void onLocationChanged(Location location) {
			android.util.Log.d("OfflineTP", "location: " + location);
			if (location == null) return;
			if (currentLocation != null // have a current location
					&& (location.getTime() - currentLocation.getTime() < 10000) // less than 10 seconds old
					&& (location.getAccuracy() - currentLocation.getAccuracy() < 5.0f)) { // doesn't add more than 5m accuracy
				// => throw away
				return;
			}
			currentLocation = location;
			state.setGps(Position.from(currentLocation));
			updateGPSIcon();
		}

		@Override
		public void onProviderDisabled(String arg0) {
			state.setGps(null);
			updateGPSIcon();
		}

		@Override
		public void onProviderEnabled(String arg0) {
		}

		@Override
		public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
		}
	}
}
