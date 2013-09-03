package de.unistuttgart.informatik.OfflineToureNPlaner.Graph;

import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.IntObjectOpenHashMap;

public final class NestedSimpleGraph extends NestedGraph {
	/** (local) outgoing edges for a node */
	private final IntObjectOpenHashMap<IntArrayList> outEdges = new IntObjectOpenHashMap<IntArrayList>();

	/**
	 * Create empty graph without parent
	 */
	public NestedSimpleGraph() {
		super();
	}

	/**
	 * Create empty graph with parent
	 *
	 * @param parent parent graph
	 */
	public NestedSimpleGraph(NestedGraph parent) {
		super(parent);
	}

	public void prepareNode(int source, int outgoing_edges) {
		if (!outEdges.containsKey(source)) {
			IntArrayList outAdd = new IntArrayList(outgoing_edges * 3);
			outEdges.put(source, outAdd);
		} else {
			IntArrayList outAdd = outEdges.lget();
			outAdd.ensureCapacity(outAdd.size() + outgoing_edges * 3);
		}
	}

	/**
	 * Add edge to the graph - the only operation modifying the graph.
	 *
	 * @param source
	 * @param target
	 * @param dist
	 * @param edgeid
	 */
	public void addEdge(int source, int target, int dist, int edgeid) {
		IntArrayList outAdd;
		if (!outEdges.containsKey(source)) {
			outAdd = new IntArrayList();
			outEdges.put(source, outAdd);
		} else {
			outAdd = outEdges.lget();
		}
		outAdd.add(target);
		outAdd.add(dist);
		outAdd.add(edgeid);
	}

	/**
	 * (package) internal method for the {@link NestedGraphEdgeIterator iterator} to
	 * have access to the edges lists.
	 * <p>
	 * It doesn't use the parent graph - this is only about the local edges list.
	 *
	 * @param node the node to get the outgoing edges for
	 * @return null if this graph has no edges for the node, or the edges list.
	 */
	IntArrayList edges(int node) {
		if(!outEdges.containsKey(node)){
			return null;
		} else {
			return outEdges.lget();
		}
	}

	@Override
	boolean start(NestedGraphEdgeIterator iterator, int node) {
		if(!outEdges.containsKey(node)) return false;

		iterator.edges = outEdges.lget();
		iterator.curEdge = 0;

		return next(iterator);
	}

	@Override
	boolean next(NestedGraphEdgeIterator iterator) {
		final IntArrayList edges = iterator.edges;
		int edge = iterator.curEdge;
		if (edge >= edges.size()) return false;

		iterator.target = edges.get(edge++);
		iterator.dist = edges.get(edge++);
		iterator.edgeid = edges.get(edge++);
		iterator.curEdge = edge;

		return true;
	}
}
