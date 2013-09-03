package de.unistuttgart.informatik.OfflineToureNPlaner.Handlers;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;

import android.os.AsyncTask;
import de.unistuttgart.informatik.OfflineToureNPlaner.Graph.GraphReader;
import de.unistuttgart.informatik.OfflineToureNPlaner.Data.Position;
import de.unistuttgart.informatik.OfflineToureNPlaner.Data.State;
import de.unistuttgart.informatik.OfflineToureNPlaner.Events.IDispatcher;
import de.unistuttgart.informatik.OfflineToureNPlaner.Events.Listener;

public final class NodeSearchHandler {
	final WeakReference<State> state;
	final GraphReader reader;

	final BaseTask startPosTask, endPosTask;
	final Listener<Boolean> isDragging_watcher;

	public NodeSearchHandler(String graphLocation, State state) throws IOException {
		this.state = new WeakReference<State>(state);
		reader = new GraphReader(new File(graphLocation));

		// No throws from here on - otherwise we'd have to undo the attach
		state.getDispatcher_start().add(startPosTask = new StartPositionTask());
		state.getDispatcher_end().add(endPosTask = new EndPositionTask());
		isDragging_watcher = new Listener<Boolean>() {
			@Override
			public void handle(Boolean param, IDispatcher<Boolean> dispatcher) {
				State state = NodeSearchHandler.this.state.get();
				if (!param.booleanValue() && state != null) {
					startPosTask.handle(state.getStart(), null);
					endPosTask.handle(state.getEnd(), null);
				}
			}
		};
		state.getDispatcher_isDragging().add(isDragging_watcher);
	}

	public void detach() {
		State s = state.get();
		if (s == null) return;
		s.getDispatcher_start().remove(startPosTask);
		s.getDispatcher_end().remove(endPosTask);
		s.getDispatcher_isDragging().remove(isDragging_watcher);
	}

	private abstract class BaseTask implements Listener<Position> {
		private Position lastSearchPoint = null;
		private Position lastResult = null;
		private AsyncTask<Position, Void, Position> task = null;
		private State.WorkingReference workingReference;

		@Override
		public final void handle(Position param, IDispatcher<Position> dispatcher) {
			State s = state.get();
			if (s == null || s.getIsDragging()) return;

			if (task != null) {
				task.cancel(true);
				task = null;
			}
			if (param == null) return;
			if (param.equals(lastResult)) return;
			if (param.equals(lastSearchPoint) && lastResult != null) {
				updatePoint(lastResult);
				return;
			}
			lastSearchPoint = param;
			lastResult = null;

			if (workingReference == null) {
				workingReference = s.startWorking();
			}
			task = new AsyncTask<Position, Void, Position>() {
				@Override
				protected final Position doInBackground(Position... positions) {
					assert positions.length == 1 : "Search for exactly one position";

					try {
						int nodeID = reader.findPoint(positions[0]);
						if (nodeID == -1) return null;
						final Position position = Position.fromE7(reader.directGeo_lat, reader.directGeo_lon);
						return position;
					} catch (IOException e) {
						e.printStackTrace();
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}

					return null;
				}

				@Override
				protected final void onPostExecute(final Position result) {
					State.WorkingReference r = workingReference;
					workingReference = null;
					lastResult = result;
					updatePoint(lastResult);
					if (r != null) r.stop();
				}
			};
			task.execute(lastSearchPoint);
		}

		protected abstract void updatePoint(Position position);
	}

	private class StartPositionTask extends BaseTask {
		@Override
		protected void updatePoint(Position position) {
			State s = state.get();
			if (s == null) return;
			if (position != null) s.setStart(position);
		}
	}

	private class EndPositionTask extends BaseTask {
		@Override
		protected void updatePoint(Position position) {
			State s = state.get();
			if (s == null) return;
			if (position != null) s.setEnd(position);
		}
	}
}
