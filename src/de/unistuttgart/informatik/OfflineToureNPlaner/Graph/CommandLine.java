package de.unistuttgart.informatik.OfflineToureNPlaner.Graph;

import java.io.File;
import java.io.IOException;

import de.unistuttgart.informatik.OfflineToureNPlaner.Data.Position;

import android.util.Log;

public class CommandLine {
	public static void main(String[] args) {
		try {
			GraphReader reader = null;
			try {
				reader = new GraphReader(new File("/home/stefan/diplomarbeit/data/germany-szhk-jan29.ch.result"));
				reader.openNCache(16);
				int start = reader.findPoint(Position.fromE6(48710628, 9241326));
				int dest = reader.findPoint(Position.fromE6(53525447, 10075375));
				if (-1 == start || -1 == dest)  {
					Log.e("Offline TP", "** Couldn't find nodes for start/dest points");
					return;
				}

				NestedGraph coreGraph = reader.loadCoreGraph();

				Log.d("Offline TP", "** Search path from: " + start + " -> " + dest);

				NestedSimpleGraph outGraph = reader.createGraphWithoutCore(start, true);
				NestedSimpleGraph inGraph = reader.createGraphWithoutCore(dest, false);
				outGraph.setParent(coreGraph);
				inGraph.setParent(outGraph);

				Dijkstra dijkstra = new Dijkstra(inGraph);
				if (!dijkstra.run(start, dest)) {
					Log.e("Offline TP", "** Dijkstra didn't find a path");
					return;
				}

				reader.expandShortcuts(dijkstra.path_nodes, dijkstra.path_edges);
				reader.loadWayCoords();

				Log.d("Offline TP", String.format("** Found way: %1.1f km", reader.path_euclid_length/1000.0));
			} finally {
				if (reader != null) reader.close();
			}
		} catch (IOException e) {
			Log.e("Offline TP", "IOException", e);
		} catch (InterruptedException e) {
		}
	}

}
