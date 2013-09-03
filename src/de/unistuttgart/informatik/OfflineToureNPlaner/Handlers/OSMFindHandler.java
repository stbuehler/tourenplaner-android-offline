package de.unistuttgart.informatik.OfflineToureNPlaner.Handlers;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.graphics.RectF;
import android.os.AsyncTask;

import de.unistuttgart.informatik.OfflineToureNPlaner.Data.OsmFindResult;
import de.unistuttgart.informatik.OfflineToureNPlaner.Data.Position;

import de.funroll_loops.osmfind.libosmfind.ItemSet;
import de.funroll_loops.osmfind.libosmfind.OsmComplete;

public class OSMFindHandler {
	OsmComplete complete;

	public static class OSMEnergizeException extends Exception {
		private static final long serialVersionUID = 5103684090629740395L;
		OSMEnergizeException(String msg) {
			super(msg);
		}
	}

	private static boolean initialized = false;
	private static synchronized void initialize(Context ctx) throws OSMEnergizeException {
		if (initialized) return;
		initialized = true;

		File cacheDir = ctx.getCacheDir();
		File tmpDir = new File(cacheDir, "osmfind-tmp");
		if (tmpDir.isDirectory()) {
			File[] files = tmpDir.listFiles();
			if (null != files) for (File f: files) {
				if (f.isFile()) f.delete();
			}
		}
		tmpDir.mkdirs();

		if (!tmpDir.isDirectory()) {
			android.util.Log.e("Offline TP", "Cannot create tmp directory: " + tmpDir.getAbsolutePath());
			throw new OSMEnergizeException("Cannot create tmp directory");
		}
		String tmpFilePrefix = tmpDir.getAbsolutePath() + "/tmpfile";
		de.funroll_loops.osmfind.libosmfind.NativeHelpers.setTempFilePrefix(tmpFilePrefix);
	}

	public OSMFindHandler(Context ctx, String baseDirectory) throws OSMEnergizeException {
		initialize(ctx);
		if (!new File(baseDirectory).isDirectory()) throw new OSMEnergizeException("No (existing) directory given");
		complete = new OsmComplete();
		complete.setFilePrefix(baseDirectory);
		if (!complete.energize()) throw new OSMEnergizeException("OsmComplete#energize failed");
	}

	/** removes slow parts */
	static public String queryFilter(String query) {
		String[] components = query.split(" +");
		StringBuilder sb = new StringBuilder();
		for (String s: components) {
			if (s.length() < 3) continue;
			if (sb.length() > 0) sb.append(" ");
			sb.append(s);
		}
		return sb.toString();
	}

	static public String geoRectToFilter(RectF geoRect) {
		if (null == geoRect) return "";
		// lat_min(double);lat_max(double);lon_min(double);lon_max(double)[;approx(bool, default false)]
		return String.format(java.util.Locale.US, " $GEO[%f;%f;%f;%f;1]", geoRect.bottom, geoRect.top, geoRect.left, geoRect.right);
	}

	private AsyncTask<Void, OsmFindResult, List<OsmFindResult>> osmFindTask;
	private String lastQuery;
	private RectF lastQueryGeoRect;
	private List<OsmFindResult> lastResult;
	public final int MAX_RESULTS = 20;

	public interface OnOsmFound {
		void onOsmFound(List<OsmFindResult> results);
		void onOsmFoundProgress(OsmFindResult result, List<OsmFindResult> partialResults);
	}

	public void osmfind_cancel() {
		if (null != osmFindTask) {
			osmFindTask.cancel(true);
			osmFindTask = null;
			lastResult = null;
			lastQuery = null;
			lastQueryGeoRect = null;
		}
	}

	public void osmfind(final String inQuery, final RectF geoRect, final OnOsmFound onFound) {
		final String query = queryFilter(inQuery);
		if (lastQuery != null && lastQuery.equals(query) &&
				(geoRect == null && lastQueryGeoRect == null || geoRect != null && geoRect.equals(lastQueryGeoRect))) {
			if (lastResult != null) {
				for (OsmFindResult i: lastResult) {
					onFound.onOsmFoundProgress(i, lastResult);
				}
				onFound.onOsmFound(lastResult);
			}
			/* if lastResult == null task is still running */
			return;
		}
		if (null != osmFindTask) {
			osmFindTask.cancel(true);
			osmFindTask = null;
		}
		final ArrayList<OsmFindResult> results = new ArrayList<OsmFindResult>();
		lastResult = null;
		lastQuery = query;
		lastQueryGeoRect = geoRect;
		osmFindTask = new AsyncTask<Void, OsmFindResult, List<OsmFindResult>>() {
			@Override
			protected List<OsmFindResult> doInBackground(Void... params) {
				synchronized (complete) {
					if (Thread.interrupted()) {
						Thread.currentThread().interrupt();
						return null;
					}

					final String geoQuery = query + geoRectToFilter(geoRect);
					long startTs = System.currentTimeMillis();
					ItemSet set = complete.complete(geoQuery); // , MAX_RESULTS);
					// ItemSet set = complete.simpleComplete(geoQuery, MAX_RESULTS); // simple complete too slow with geo query
					android.util.Log.d("OsmFind", "completed '" + geoQuery + "' in " + (System.currentTimeMillis() - startTs) + " ms (~" + set.size() + " results)");

					int have = 0;
					// ArrayList<OsmFindResult> localResults = new ArrayList<OsmFindResult>(MAX_RESULTS);
					ArrayList<Position> geoQueryPositions = new ArrayList<Position>(MAX_RESULTS);
					for (int i = 0, l = set.size(); have < MAX_RESULTS && i < l; ++i) {
						if (Thread.interrupted()) {
							Thread.currentThread().interrupt();
							break;
						}
						if (set.at(i).posCount() == 0 || set.at(i).strCount() == 0) continue;
						final OsmFindResult r = new OsmFindResult(set.at(i));
						publishProgress(r);
						geoQueryPositions.add(r.getPosition());
						// localResults.add(r);
						++have;
					}
					set.close();

					if (geoRect != null && have < MAX_RESULTS) {
						if (Thread.interrupted()) {
							Thread.currentThread().interrupt();
							return null;
						}

						startTs = System.currentTimeMillis();
						set = complete.simpleComplete(query, MAX_RESULTS - have, 3);
						android.util.Log.d("OsmFind", "completed '" + query + "' in " + (System.currentTimeMillis() - startTs) + " ms (~" + set.size() + " results)");
						for (int i = 0, l = set.size(); have < MAX_RESULTS && i < l; ++i) {
							if (Thread.interrupted()) {
								Thread.currentThread().interrupt();
								break;
							}
							if (set.at(i).posCount() == 0 || set.at(i).strCount() == 0) continue;
							final OsmFindResult r = new OsmFindResult(set.at(i));

							// if we already found an item at that position in the geo restricted lookup,
							// this item is in the geo restricted area - doesn't have to be exactly the same,
							// but it should have been found in the geo restricted search too.
							if (geoQueryPositions.indexOf(r.getPosition()) >= 0) continue;
							publishProgress(r);
							// localResults.add(r);
							++have;
						}
						set.close();
					}
					return null; // localResults;
				}
			}

			@Override
			protected void onProgressUpdate (OsmFindResult... values) {
				results.ensureCapacity(values.length);
				for (int i = 0, l = values.length; i < l; ++i) {
					results.add(values[i]);
					onFound.onOsmFoundProgress(values[i], results);
				}
			}

			@Override
			protected final void onPostExecute(List<OsmFindResult> values) {
				if (values != null) results.addAll(values);
				lastResult = results;
				android.util.Log.d("OfflineTP", "search returned " + lastResult.size() + " results");
				onFound.onOsmFound(lastResult);
			}
		};
		osmFindTask.execute();
	}

}
