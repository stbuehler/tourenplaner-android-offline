package de.unistuttgart.informatik.OfflineToureNPlaner.Graph;

public final class NestedCompactCoreGraph extends NestedGraph {
	private final int coreBlock;
	private final int blockSize;
	private final int firstEdgeID;
	private final int[] edgesIndex;
	private final int[] edgeDetails;

	/**
	 * Create graph without parent
	 */
	public NestedCompactCoreGraph(int coreBlock, int blockSize, int firstEdgeID, int[] edgesIndex, int[] edgeDetails) {
		super();
		this.coreBlock = coreBlock;
		this.blockSize = blockSize;
		this.firstEdgeID = firstEdgeID;
		this.edgesIndex = edgesIndex;
		this.edgeDetails = edgeDetails;
	}

	public NestedCompactCoreGraph(int coreBlock, int blockSize, int firstEdgeID, int[] edgesIndex, int[] edgeDetails, NestedGraph parent) {
		super(parent);
		this.coreBlock = coreBlock;
		this.blockSize = blockSize;
		this.firstEdgeID = firstEdgeID;
		this.edgesIndex = edgesIndex;
		this.edgeDetails = edgeDetails;
	}

	@Override
	boolean start(NestedGraphEdgeIterator iterator, int node) {
		if ((node & 1023) >= blockSize) return false;
		int ndx = ((node >>> 10) - coreBlock) * blockSize + (node & 1023);
		if (ndx < 0 || (ndx + 1) >= edgesIndex.length) return false;
		int firstEdge = edgesIndex[ndx];
		int lastEdge = edgesIndex[ndx+1];
		if (firstEdge >= lastEdge) return false;

		iterator.curEdge = firstEdge * 2;
		iterator.lastEdge = lastEdge * 2;

		return next(iterator);
	}

	@Override
	boolean next(NestedGraphEdgeIterator iterator) {
		int edge = iterator.curEdge;
		if (edge >= iterator.lastEdge) return false;

		iterator.edgeid = firstEdgeID + (edge / 2);
		iterator.target = edgeDetails[edge++];
		iterator.dist = edgeDetails[edge++];
		iterator.curEdge = edge;

		return true;
	}
}
