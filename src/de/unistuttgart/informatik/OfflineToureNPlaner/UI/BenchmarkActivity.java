package de.unistuttgart.informatik.OfflineToureNPlaner.UI;

import java.io.File;
import java.io.IOException;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import de.unistuttgart.informatik.OfflineToureNPlaner.R;
import de.unistuttgart.informatik.OfflineToureNPlaner.Data.Position;
import de.unistuttgart.informatik.OfflineToureNPlaner.Graph.Dijkstra;
import de.unistuttgart.informatik.OfflineToureNPlaner.Graph.GraphReader;
import de.unistuttgart.informatik.OfflineToureNPlaner.Graph.NestedGraph;
import de.unistuttgart.informatik.OfflineToureNPlaner.Graph.NestedSimpleGraph;

public class BenchmarkActivity extends Activity {
	private TextView resultView;
	private BenchmarkTask task;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_benchmark);

		final Button button = (Button) findViewById(R.id.run);
		button.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				if (task != null) {
					return;
					// task.cancel(true);
					// task = null;
				}

				final String graphLocation = PreferenceManager.getDefaultSharedPreferences(BenchmarkActivity.this).getString("graphfile", "");

				if (graphLocation.isEmpty()) {
					Toast.makeText(BenchmarkActivity.this, "Graph file not set yet", Toast.LENGTH_LONG).show();
				} else {
					try {
						task = new BenchmarkTask(graphLocation);
					} catch (Exception e) {
						Log.e("Offline ToureNPlaner", "Couldn't read graph file: " + e.getLocalizedMessage());
						Toast.makeText(BenchmarkActivity.this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
						return;
					}
				}

				task.execute();
			}
		});

		resultView = (TextView) findViewById(R.id.result);
	}

	private class BenchmarkTask extends AsyncTask<Void, String, String> {
		private final StringBuilder result;
		private final GraphReader reader;
		private final NestedGraph coreGraph;

		public BenchmarkTask(String graphLocation) throws IOException, InterruptedException {
			result = new StringBuilder();
			reader = new GraphReader(new File(graphLocation));
			coreGraph = reader.loadCoreGraph();
		}

		@SuppressWarnings("unused")
		private boolean run_way(final Position from, final Position to) throws InterruptedException {
			try {
				reader.openNCache(32); // each slot needs 4k memory

				int start = reader.findPoint(from);
				int dest = reader.findPoint(to);

				if (-1 == start || -1 == dest) {
					result.append("Couldn't find nodes for start/destination points\n");
					return false;
				}

				NestedGraph graph;
				if (true) {
					final NestedSimpleGraph outGraph = reader.createGraphWithoutCore(start, true);
					outGraph.setParent(coreGraph);
					final NestedSimpleGraph inGraph = reader.createGraphWithoutCore(dest, false);
					inGraph.setParent(outGraph);

					graph = inGraph;
				} else {
					final NestedSimpleGraph g = new NestedSimpleGraph(coreGraph);
					reader.fillGraphWithoutCore(g, start, true);
					reader.fillGraphWithoutCore(g, dest, false);
					graph = g;
				}

				Dijkstra dijkstra = new Dijkstra(graph);
				if (!dijkstra.run(start, dest)) {
					result.append("Dijkstra didn't find a path\n");
					return false;
				}

				reader.expandShortcuts(dijkstra.path_nodes, dijkstra.path_edges);
				reader.loadWayCoords();

				return true;
			} catch (IOException e) {
				result.append("run failed: " + e.getMessage() + "\n");
			} finally {
				reader.closeNCache();
			}

			return false;
		}

		private void benchmark_way(final Position from, final Position to) throws InterruptedException {
			result.append("Benchmarking way " + from.toString() + " -> " + to.toString() + "\n");

			final int RUNS = 6;
			final long runtime[] = new long[RUNS];
			int finished;
			final StringBuilder progress = new StringBuilder("Running: ");
			for (finished = 0; finished < RUNS; ++finished) {
				publishProgress(result.toString() + progress.toString());
				System.gc();
				System.gc();
				Thread.sleep(20);
				long startTime = System.currentTimeMillis();
				if (!run_way(from, to)) break;
				runtime[finished] = System.currentTimeMillis() - startTime;
				progress.append(String.format("%6d ms ", runtime[finished]));
			}

			if (RUNS == finished) {
				// Drop the first run!
				long sum = 0;
				for (int i = 1; i < RUNS; ++i) {
					sum += runtime[i];
				}
				result.append(progress.toString());
				result.append(String.format("\nAverage: %6d ms\n", sum/(RUNS-1)));
			}
			result.append("\n");
			publishProgress(result.toString());
		}

		@Override
		protected synchronized String doInBackground(Void... params) {
			try {
				// Informatik -> Stuttgart HBF
				benchmark_way(Position.from(48.7456169, 9.1070623), Position.from(48.7831573, 9.1816587));
	
				// Informatik -> Karlsruhe
				benchmark_way(Position.from(48.7456169, 9.1070623), Position.from(49.0107460, 8.4040517));
	
				// Informatik -> München
				benchmark_way(Position.from(48.7456169, 9.1070623), Position.from(48.1370124, 1.5758237));
	
				// Informatik -> Berlin
				benchmark_way(Position.from(48.7456169, 9.1070623), Position.from(52.5199928, 3.4385576));
	
				// Informatik -> Hamburg
				benchmark_way(Position.from(48.7456169, 9.1070623), Position.from(53.5438613, 0.0104999));
	
				// Aachen -> Berlin
				benchmark_way(Position.from(50.7773246, 6.0779156), Position.from(52.5199928, 3.4385576));

				return result.toString();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return null;
			}
		}

		@Override
		protected void onPostExecute(String result) {
			resultView.setText(result);
			task = null;
		}

		@Override
		protected void onProgressUpdate(String... result) {
			resultView.setText(result[0] + " ...");
		}
	}

}
