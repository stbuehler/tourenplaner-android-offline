package de.unistuttgart.informatik.OfflineToureNPlaner.Graph;

/**
 * Graph, consisting of a list of edges per node (identified by
 * arbitrary nodeIDs); storing an arbitrary "distance"
 * and "edgeID" per edge (and a target nodeID of course).
 * <p>
 * Graphs can be nested; the resulting graph is a union of
 * the graph and its parent.<br>
 * The {@link NestedGraphEdgeIterator iterator} will list
 * own edges before the parent edges.
 * <p>
 * The parent graph can be modified even while it is used
 * in a sub graph.
 */
public abstract class NestedGraph {
	/** parent graph; only used in the {@link NestedGraphEdgeIterator iterator} */
	NestedGraph parent;

	/**
	 * Create graph without parent
	 */
	public NestedGraph() {
		this.parent = null;
	}

	/**
	 * Create graph with parent
	 *
	 * @param parent parent graph
	 */
	public NestedGraph(NestedGraph parent) {
		this.parent = parent;
	}

	abstract boolean start(NestedGraphEdgeIterator iterator, int node);
	abstract boolean next(NestedGraphEdgeIterator iterator);

	public NestedGraph getParent() {
		return parent;
	}

	public void setParent(NestedGraph parent) {
		this.parent = parent;
	}
}
