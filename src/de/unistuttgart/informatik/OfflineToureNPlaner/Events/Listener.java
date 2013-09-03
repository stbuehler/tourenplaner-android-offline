package de.unistuttgart.informatik.OfflineToureNPlaner.Events;

public interface Listener<Param> {
	void handle(Param param, IDispatcher<Param> dispatcher);
}
