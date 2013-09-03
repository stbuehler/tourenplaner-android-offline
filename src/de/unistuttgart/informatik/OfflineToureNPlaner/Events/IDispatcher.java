package de.unistuttgart.informatik.OfflineToureNPlaner.Events;

public interface IDispatcher<Param> {
	void add(Listener<Param> watcher);

	void remove(Listener<Param> watcher);
}
