package de.unistuttgart.informatik.OfflineToureNPlaner.Graph;

import com.carrotsearch.hppc.IntArrayList;

/**
 * Iterator to iterate over the outgoing edges
 * of a node in a {@link NestedGraph graph}.
 * <p>
 * Also responsible for traversing the {@link NestedGraph#parent parent graph}.
 * <p>
 * Use like this:
 * <pre>
 *     {@code i = new NestedGraphEdgeIterator(g, node);
 *     while (i.next()) {
 *         // use i.target, i.dist, i.edgeid
 *     }
 * </pre>
*/
public final class NestedGraphEdgeIterator {
	/** contains the edge information after {@link #next()} returned true */
	public int target, dist, edgeid;

	/** data available for the graph in */
	IntArrayList edges;
	int curEdge, lastEdge;


	/** the graph the current {@link #edges} list is from */
	private NestedGraph g;

	/** the node id; needed to lookup the edges list in parent graphs */
	private int node;

	private boolean loaded = false;

	/**
	 * create empty iterator
	 */
	public NestedGraphEdgeIterator() {
		g = null;
	}
	/**
	 * @see #load(NestedGraph, int)
	 */
	public NestedGraphEdgeIterator(NestedGraph g, int node) {
		load(g, node);
	}

	/**
	 * Iterate the outgoing edges for {@code node} in graph {@code g}
	 * @param g
	 * @param node
	 */
	public void load(NestedGraph g, int node) {
		this.g = g;
		this.node = node;
		loaded = false;
	}

	/**
	 * find the first (parent) graph which has an edge for node
	 */
	private boolean loadGraph() {
		loaded = false;
		for (;;) {
			edges = null;
			if (null == g) return false;

			if (g.start(this, node)) {
				loaded = true;
				return true;
			}
			g = g.parent;
		}
	}

	/**
	 * load next edge
	 *
	 * @return returns whether next edge was loaded (otherwise reached end of list)
	 * @see #target
	 * @see #dist
	 * @see #edgeid
	 */
	public boolean next() {
		if (!loaded) return loadGraph();
		if (g.next(this)) return true;
		g = g.parent;
		return loadGraph();
	}
}
