package de.unistuttgart.informatik.OfflineToureNPlaner.Handlers;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;

import de.unistuttgart.informatik.OfflineToureNPlaner.Data.Position;
import de.unistuttgart.informatik.OfflineToureNPlaner.Data.State;
import de.unistuttgart.informatik.OfflineToureNPlaner.Data.Way;
import de.unistuttgart.informatik.OfflineToureNPlaner.Events.IDispatcher;
import de.unistuttgart.informatik.OfflineToureNPlaner.Events.Listener;
import de.unistuttgart.informatik.OfflineToureNPlaner.Graph.Dijkstra;
import de.unistuttgart.informatik.OfflineToureNPlaner.Graph.GraphReader;
import de.unistuttgart.informatik.OfflineToureNPlaner.Graph.NestedGraph;
import de.unistuttgart.informatik.OfflineToureNPlaner.Graph.NestedSimpleGraph;
import android.os.AsyncTask;
import android.util.Log;

public class ShortestPathHandler {
	final WeakReference<State> state;
	final GraphReader reader;
	final Listener<Position> watcher;
	final Listener<Boolean> folloGps_watcher;
	final Listener<Boolean> isDragging_watcher;
	Task task = null;

	private Position lastSearchStart = null;
	private Position lastSearchEnd = null;
	private Way lastResult = null;
	private State.WorkingReference workingReference = null;
	private boolean restartTaskAfterFinish = false;

	public ShortestPathHandler(String graphLocation, State state) throws IOException {
		this.state = new WeakReference<State>(state);
		reader = new GraphReader(new File(graphLocation));

		// No throws from here on - otherwise we'd have to undo the attach
		watcher = new Listener<Position>() {
			@Override
			public final void handle(Position param, IDispatcher<Position> dispatcher) {
				updateWay();
			}
		};
		folloGps_watcher = new Listener<Boolean>() {
			@Override
			public void handle(Boolean param, IDispatcher<Boolean> dispatcher) {
				updateWay();
			}
		};
		isDragging_watcher = new Listener<Boolean>() {
			@Override
			public void handle(Boolean param, IDispatcher<Boolean> dispatcher) {
				if (!param) updateWay();
			}
		};
		state.getDispatcher_start().add(watcher);
		state.getDispatcher_end().add(watcher);
		state.getDispatcher_gps().add(watcher);
		state.getDispatcher_followGps().add(folloGps_watcher);
		state.getDispatcher_isDragging().add(isDragging_watcher);
	}

	public void detach() {
		State s = state.get();
		if (s == null) return;
		s.getDispatcher_start().remove(watcher);
		s.getDispatcher_end().remove(watcher);
		s.getDispatcher_gps().remove(watcher);
		s.getDispatcher_followGps().remove(folloGps_watcher);
		s.getDispatcher_isDragging().remove(isDragging_watcher);
	}

	private void handleResult(Way result) {
		task = null;
		State s = state.get();
		if (s == null) return;
		State.WorkingReference r = workingReference;
		workingReference = null;
		s.setWay(result);
		if (r != null) r.stop();
		if (restartTaskAfterFinish) {
			restartTaskAfterFinish = false;
			updateWay();
		}
	}

	private void updateWay() {
		State st = state.get();
		if (st == null) return;

		final Position s = st.getStart(), e = st.getEnd(), g = st.getGps();
		Position useAsStart = null;
		if (st.getFollowGps()) {
			useAsStart = g != null ? g : s;
		} else {
			useAsStart = s != null ? s : g;
		}
		if (useAsStart == null || e == null) {
			if (task != null) {
				task.cancel(true);
				task = null;
			}
			handleResult(null);
			return;
		}

		if (useAsStart.equals(lastSearchStart) && e.equals(lastSearchEnd)) {
			handleResult(lastResult);
			return;
		}

		if ((st.getIsDragging() || (useAsStart == g && e.equals(lastSearchEnd))) && task != null) {
			// don't cancel task for gps updates or while dragging; instead restart task after it finished
			restartTaskAfterFinish = true;
			return;
		}

		if (task != null) {
			task.cancel(true);
			task = null;
		}
		lastSearchStart = useAsStart;
		lastSearchEnd = e;

		if (null == workingReference) {
			workingReference = st.startWorking();
		}
		task = new Task();
		task.execute(lastSearchStart, lastSearchEnd);
	}

	private class Task extends AsyncTask<Position, Void, Way> {
		@Override
		protected Way doInBackground(Position... positions) {
			assert 2 == positions.length : "Need exactly two positions to calculate shortest path for";
			try {
				return findWay(positions[0], positions[1]);
			} catch (IOException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			return null;
		}

		@Override
		protected final void onPostExecute(final Way result) {
			lastResult = result;
			handleResult(lastResult);
		}
	}

	/**
	 * The constant used to compute time traveled on an edge from it's
	 * non euclidian distance value by time=getDist(edgeId)*travelTimeConstant
	 * time is in seconds
	 */
	public static final double travelTimeConstant = 0.02769230769230769230769230769230769; /* 9 / 325 */

	private NestedGraph coreGraph = null;
	private synchronized Way findWay(Position geoStart, Position geoEnd) throws IOException, InterruptedException {
		try {
			long startTime = System.currentTimeMillis();

			Log.d("Offline TP", "** Search path from: " + geoStart.toString() + " -> " + geoEnd.toString());
			reader.openNCache(32); // each slot needs 4k memory

			// Log.d("Offline TP", "-- Searching start node...");
			int start = reader.findPoint(geoStart);

			// Log.d("Offline TP", "-- Searching destination node...");
			int dest = reader.findPoint(geoEnd);

			if (-1 == start || -1 == dest) {
				Log.e("Offline TP", "** Couldn't find nodes for start/destination points");
				return null;
			}

			Log.d("Offline TP", "** Search path from: " + start + " -> " + dest);

			// Log.d("Offline TP", "-- Loading core graph...");
			if (coreGraph == null) coreGraph = reader.loadCoreGraph();

			// Log.d("Offline TP", "-- Reading outgoing edges transitively from source...");
			NestedSimpleGraph outGraph = reader.createGraphWithoutCore(start, true);
			outGraph.setParent(coreGraph);
			// NestedSimpleGraph graph = new NestedSimpleGraph(coreGraph);
			// reader.fillGraphWithoutCore(graph, start, true);

			// Log.d("Offline TP", "-- Reading incoming edges transitively from destination...");
			NestedSimpleGraph inGraph = reader.createGraphWithoutCore(dest, false);
			inGraph.setParent(outGraph);

			// Log.d("Offline TP", "-- Search shortest path: running Dijkstra...");
			Dijkstra dijkstra = new Dijkstra(inGraph);
			if (!dijkstra.run(start, dest)) {
				Log.e("Offline TP", "** Dijkstra didn't find a path");
				return null;
			}

			// Log.d("Offline TP", "-- Expanding shortcuts...");
			reader.expandShortcuts(dijkstra.path_nodes, dijkstra.path_edges);

			// Log.d("Offline TP", "-- Loading coords for path...");
			reader.loadWayCoords();

			final Position[] points = new Position[reader.way_coords.length/2];
			for (int i = 0; i < points.length; ++i) {
				points[i] = Position.fromE7(reader.way_coords[2*i], reader.way_coords[2*i+1]);
			}
			Way way = new Way(points, reader.path_euclid_length, (int) (dijkstra.path_length * travelTimeConstant));

			Log.d("Offline TP", "** completed in " + (System.currentTimeMillis() - startTime) + "ms");

			return way;
		} finally {
			reader.closeNCache();
		}
	}
}
