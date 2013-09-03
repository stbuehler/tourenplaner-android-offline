package de.unistuttgart.informatik.OfflineToureNPlaner.Data;

import java.io.ObjectStreamException;
import java.io.Serializable;

import de.unistuttgart.informatik.OfflineToureNPlaner.Events.Dispatcher;
import de.unistuttgart.informatik.OfflineToureNPlaner.Events.IDispatcher;

/**
 * Application "state"
 */
public class State implements Serializable {
	private static final long serialVersionUID = 1L;

	private final Dispatcher<Boolean> dispatcher_followGps = new Dispatcher<Boolean>();
	private final Dispatcher<Position> dispatcher_start = new Dispatcher<Position>();
	private final Dispatcher<Position> dispatcher_end = new Dispatcher<Position>();
	private final Dispatcher<Position> dispatcher_gps = new Dispatcher<Position>();
	private final Dispatcher<Way> dispatcher_way = new Dispatcher<Way>();
	private final Dispatcher<Boolean> dispatcher_working = new Dispatcher<Boolean>();
	private final Dispatcher<Boolean> dispatcher_isDragging = new Dispatcher<Boolean>();

	private static class Store implements Serializable {
		private static final long serialVersionUID = 1L;

		boolean followGps = true;
		Position start, end, gps;
		Way way;

		private Object readResolve() throws ObjectStreamException {
			return new State(this);
		}
	}

	private final Store store;
	private int workers = 0;
	private boolean isDragging = false;

	public State() {
		store = new Store();
	}
	State(Store store) {
		this.store = store;
	}

	public IDispatcher<Boolean> getDispatcher_followGps() {
		return dispatcher_followGps;
	}

	public IDispatcher<Position> getDispatcher_start() {
		return dispatcher_start;
	}

	public IDispatcher<Position> getDispatcher_end() {
		return dispatcher_end;
	}

	public IDispatcher<Position> getDispatcher_gps() {
		return dispatcher_gps;
	}

	public IDispatcher<Way> getDispatcher_way() {
		return dispatcher_way;
	}

	public IDispatcher<Boolean> getDispatcher_working() {
		return dispatcher_working;
	}

	public IDispatcher<Boolean> getDispatcher_isDragging() {
		return dispatcher_isDragging;
	}

	public boolean getFollowGps() {
		return this.store.followGps;
	}

	public void setFollowGps(boolean followGps) {
		this.store.followGps = followGps;
		dispatcher_followGps.dispatch(this.store.followGps);
	}

	public Position getStart() {
		return store.start;
	}

	public void setStart(Position start) {
		if (Position.equals(this.store.start, start)) return;
		this.store.start = start;
		dispatcher_start.dispatch(this.store.start);
	}

	public Position getEnd() {
		return store.end;
	}

	public void setEnd(Position end) {
		if (Position.equals(this.store.end, end)) return;
		this.store.end = end;
		dispatcher_end.dispatch(this.store.end);
	}

	public Position getGps() {
		return store.gps;
	}

	public void setGps(Position gps) {
		if (Position.equals(this.store.gps, gps)) return;
		this.store.gps = gps;
		dispatcher_gps.dispatch(this.store.gps);
	}

	public Way getWay() {
		return store.way;
	}

	public void setWay(Way way) {
		if (way == this.store.way) return;
		this.store.way = way;
		dispatcher_way.dispatch(this.store.way);
	}

	public boolean getIsDragging() {
		return isDragging;
	}

	public void setIsDragging(boolean isDragging) {
		if (isDragging == this.isDragging) return;
		this.isDragging = isDragging;
		dispatcher_isDragging.dispatch(this.isDragging);
	}

	public final class WorkingReference {
		private boolean finished = false;
		public void stop() {
			if (finished) return;
			finished = true;
			stopWorking();
		}
	}
	public WorkingReference startWorking() {
		assert workers >= 0;
		if (0 == workers++) dispatcher_working.dispatch(true);
		return new WorkingReference();
	}
	/* internal */
	void stopWorking() {
		assert workers > 0;
		if (0 == --workers) dispatcher_working.dispatch(false);
	}

	private Object writeReplace() throws ObjectStreamException {
		return store;
	}
}
