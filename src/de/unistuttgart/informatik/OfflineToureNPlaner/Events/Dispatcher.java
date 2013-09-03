package de.unistuttgart.informatik.OfflineToureNPlaner.Events;

import java.util.ArrayList;

public final class Dispatcher<Param> implements IDispatcher<Param> {
	private final ArrayList<Listener<Param>> watchers = new ArrayList<Listener<Param>>();

	public void dispatch(Param param) {
		// clone the list, so changes while running don't conflict with the iterator and don't lead to endless loops.
		@SuppressWarnings("unchecked")
		final ArrayList<Listener<Param>> watchers = (ArrayList<Listener<Param>>) this.watchers.clone();

		for (Listener<Param> watcher: watchers) {
			try {
				watcher.handle(param, this);
			} catch (RuntimeException e) {
				System.err.println("RuntimeException while handling event");
				e.printStackTrace();
			}
		}
	}

	public void clear() {
		watchers.clear();
	}

	@Override
	public void add(final Listener<Param> watcher) {
		if (!watchers.contains(watcher))
			watchers.add(watcher);
	}

	@Override
	public void remove(final Listener<Param> watcher) {
		watchers.remove(watcher);
	}
}
