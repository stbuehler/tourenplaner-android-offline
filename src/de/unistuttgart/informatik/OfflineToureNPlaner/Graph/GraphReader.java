package de.unistuttgart.informatik.OfflineToureNPlaner.Graph;

import java.io.File;
import java.io.IOException;

import com.carrotsearch.hppc.IntArrayDeque;
import com.carrotsearch.hppc.IntOpenHashSet;
import com.carrotsearch.hppc.cursors.IntCursor;

import de.unistuttgart.informatik.OfflineToureNPlaner.Data.Position;

import android.util.Log;

final public class GraphReader implements java.io.Closeable {
	static final boolean DEBUG = false;

	RandomInputStream file;
	FileNCache ncache;

	/* header */
	final int baseLon, baseLat, cellWidth, cellHeight, gridWidth, gridHeight;
	final int blockSize, blockCount;
	final int coreBlock;
	final int edgeCount;

	final long offsetNodeGeo, offsetNodeEdges, offsetEdges, offsetEdgesDetails;
	final long strideNodeGeoBlock, strideNodeEdgesBlock;
	final long strideEdge = 8;
	final long strideEdgeDetails = 16;

	static long align4k(long offset) {
		return (offset + 4095) & ~4095;
	}

	public GraphReader(File graphFile) throws IOException {
		final java.io.RandomAccessFile rndAccFile;
		try {
			rndAccFile = new java.io.RandomAccessFile(graphFile, "r");
			file = new RandomInputStream.RandAccFile(rndAccFile);
		} catch (java.io.FileNotFoundException e) {
			Log.e("Offline TP", graphFile.toString() + " not found");
			throw e;
		}
		try {
			// read header
			// check magic+version
			int magic1 = file.readInt();
			int magic2 = file.readInt();

			if ((0xFD377A58 == magic1 && 0x5A00 == (magic2 >>> 16))
				|| (0x69647864 == magic1 && 0x65666C00 == magic2)) {
				// xz header:  0xFD, '7', 'z', 'X', 'Z', 0x00
				try {
					// file = new XZRandomInputStreamPure(rndAccFile);
					file = new XZRandomInputStream(graphFile);
				} catch (IOException e) {
					Log.e("Offline TP", "Couldn't open xz archive " + graphFile.toString() + ": " + e.getMessage());
					throw e;
				}
				magic1 = file.readInt();
				magic2 = file.readInt();
			}
			
			if (0x4348474F != magic1 || 0x66665450 != magic2) throw new IOException("Bad file: wrong magic values in file header");
			final int version = file.readInt();
			if (1 != version) throw new IOException("Bad file: unknown version " + version);

			// base grid
			baseLon = file.readInt();
			baseLat = file.readInt();
			cellWidth = file.readInt();
			cellHeight = file.readInt();
			gridWidth = file.readInt();
			gridHeight = file.readInt();

			// counts+sizes
			blockSize = file.readInt();
			blockCount = file.readInt();
			coreBlock = file.readInt();
			edgeCount = file.readInt();

			strideNodeGeoBlock = strideNodeEdgesBlock = (1+blockSize) * 8;
			offsetNodeGeo = 4096;
			offsetNodeEdges = align4k(offsetNodeGeo + blockCount*strideNodeGeoBlock);
			offsetEdges = align4k(offsetNodeEdges + blockCount*strideNodeEdgesBlock);
			offsetEdgesDetails = align4k(offsetEdges + edgeCount*strideEdge);
			if (DEBUG) Log.d("Offline TP", "-- graph: offsets: " + offsetNodeGeo + " " + offsetNodeEdges + " " + offsetEdges + " " + offsetEdgesDetails + ", stride: " + strideNodeGeoBlock);
			if (DEBUG) Log.d("Offline TP", "-- graph: blocksize: " + blockSize + " blocks: " + blockCount + " edges: " + edgeCount);
			if (DEBUG) Log.d("Offline TP", "-- graph: min file size: " + (offsetEdgesDetails + edgeCount * strideEdgeDetails) + ", have: " + file.length());
		} catch (IOException e) {
			if (DEBUG) Log.d("Offline TP", "reading graph header failed: " + e.getMessage());
			close();
			throw e;
		}
	}

	@Override
	public void close() throws IOException {
		try {
			if (file != null) file.close();
		} finally {
			file = null;
			ncache = null;
		}
	}

	public void openNCache(int slots) throws IOException, InterruptedException {
		ncache = new FileNCache(file, slots);
	}

	public void closeNCache() {
		ncache = null;
	}

	public NestedGraph loadCoreGraph() throws IOException, InterruptedException {
		final long startTime = System.currentTimeMillis();

		if (-1 == coreBlock) return null;
		assert(coreBlock < blockCount);

		final int coreNodes = blockSize*(blockCount - coreBlock);
		int[] edgeOffsets = new int[coreNodes+1];
		FileCache cache = new FileCache(file);

		cache.seek(offsetNodeEdges + coreBlock * strideNodeEdgesBlock + 4);
		final int firstEdge = cache.readInt();
		{
			int lastIncEdgesOffset = firstEdge;
			int node = 0;
			for (int block = coreBlock; block < blockCount; ++block) {
				cache.seek(offsetNodeEdges + block * strideNodeEdgesBlock + 4);
				for (int ndx = 0; ndx < blockSize; ++ndx) {
					int outEdgesOffset = cache.readInt();
					edgeOffsets[node++] = outEdgesOffset - firstEdge;
					assert lastIncEdgesOffset == outEdgesOffset : "Can't handle incoming edges";
					lastIncEdgesOffset = cache.readInt();
				}
			}
			assert lastIncEdgesOffset == edgeCount;
			edgeOffsets[coreNodes] = edgeCount - firstEdge;
		}

		int[] edgeData = new int[(edgeCount - firstEdge) * 2];
		file.seek(offsetEdges + strideEdge*firstEdge);
		file.readIntArray(edgeData, 0, edgeData.length);

		if (DEBUG) Log.d("Offline TP", "<- Loaded " + (edgeCount - firstEdge) + " core edges in " + (System.currentTimeMillis() - startTime) + "ms");

		return new NestedCompactCoreGraph(coreBlock, blockSize, firstEdge, edgeOffsets, edgeData);
	}

	/** result of {@link #directGeoLoad(int)} in degree*1E7 */
	public int directGeo_lat, directGeo_lon;
	/** load node with given id and store coords in {@link #directGeo_lat} and {@link #directGeo_lon} */
	public void directGeoLoad(int nodeID) throws IOException, InterruptedException {
		int block = nodeID >>> 10;
		nodeID = (nodeID & 1023);
		long offset = offsetNodeGeo + block * strideNodeGeoBlock + 8 * (nodeID + 1);
		if (ncache != null) {
			ncache.seek(offset);
			directGeo_lon = ncache.readInt();
			directGeo_lat = ncache.readInt();
		} else {
			file.seek(offset);
			directGeo_lon = file.readInt();
			directGeo_lat = file.readInt();
		}
	}

	public int directEdgeDetails_euclid;
	public int directEdgeDetails_shortcut1;
	public int directEdgeDetails_shortcut2;
	public int directEdgeDetails_shortcutnode;
	public void directEdgeDetailsLoad(int edge) throws IOException, InterruptedException {
		long offset = offsetEdgesDetails + edge * strideEdgeDetails;
		try {
			if (ncache != null) {
				ncache.seek(offset);
				directEdgeDetails_euclid = ncache.readInt();
				directEdgeDetails_shortcut1 = ncache.readInt();
				directEdgeDetails_shortcut2 = ncache.readInt();
				directEdgeDetails_shortcutnode = ncache.readInt();
			} else {
				file.seek(offset);
				directEdgeDetails_euclid = file.readInt();
				directEdgeDetails_shortcut1 = file.readInt();
				directEdgeDetails_shortcut2 = file.readInt();
				directEdgeDetails_shortcutnode = file.readInt();
			}
		} catch (IOException e) {
			Log.e("GraphReader", "edge: " + edge + " offset: " + offset);
			throw e;
		}
	}

	/**
	 * output debug helper to split nodeID into block and index within block
	 * @param nodeID
	 * @return String describing nodeID
	 */
	public static String nodeIDStr(int nodeID) {
		return ("(" + (nodeID >>> 10) + " @@ " + (nodeID & 1023) + " - " + nodeID + ")");
	}

	private static int clipInt(int min, int max, int value) {
		return Math.max(min, Math.min(max, value));
	}
	private static int signumLong(long value) {
		return value > 0 ? 1 : value < 0 ? -1 : 0;
	}
	/**
	 * find NodeID closest to given point
	 *
	 * TODO: fix distance function (using longitude/latitude as euclidian distance now)
	 *
	 * stores coordinates of found point in directGeo_lat/directGeo_lon
	 *
	 * @param point
	 * @return
	 * @throws IOException
	 */
	public int findPoint(Position point) throws IOException, InterruptedException {
		final long startTime = System.currentTimeMillis();
		@SuppressWarnings("unused") // debug var
		int tried_nodes = 0;
		int lat = point.latitudeE7();
		int lon = point.longitudeE7();
		long minDist = Long.MAX_VALUE;
		int nodeID = -1, lastNodeID = 0;
		int nodeLat = 0, nodeLon = 0;
		int gridx = clipInt(0, gridWidth-1, (lon - baseLon)/cellWidth);
		int gridy = clipInt(0, gridHeight-1, (lat - baseLat)/cellHeight);
		final NodeGeoIterator i = new NodeGeoIterator();

		if (DEBUG) Log.d("Offline TP", "-> find point: lon=" + lon + " lat=" + lat + " in cell (" + gridx + "/" + gridy + ")");

		for (;;) {
			lastNodeID = nodeID;
			if (nodeID != -1) {
				gridy = clipInt(0, gridHeight-1, (nodeLat - baseLat)/cellHeight);
				gridx = clipInt(0, gridWidth-1, (nodeLon - baseLon)/cellWidth);
			}

			int base = gridy * gridWidth + gridx;
			i.continue_with(base);
			while (i.next()) {
				++tried_nodes;
				long diff_lat = lat - i.nodeLat, diff_lon = lon - i.nodeLon;
				long dist = diff_lat*diff_lat + diff_lon*diff_lon;
				if (dist < minDist) {
					minDist = dist;
					nodeID = i.nodeID; nodeLat = i.nodeLat; nodeLon = i.nodeLon;
				}
			}

			if (nodeID != lastNodeID) continue; // restart loop at new coords

			if (nodeID == -1) {
				/* empty grid cell - start at some random base point */
				nodeID = coreBlock << 10;
				directGeoLoad(nodeID);
				nodeLat = directGeo_lat;
				nodeLon = directGeo_lon;
				continue;
			}

			// search cells in direction from found node to searched position (in the other direction all points are farther away)
			int sx = signumLong(lon - nodeLon), sy = signumLong(lat - nodeLat);
			int[] tryBlocks = { -1, -1, -1, -1};
			{
				int k = 0;
				if (gridx + sx < 0 || gridx + sx >= gridWidth) sx = 0;
				if (gridy + sy < 0 || gridy + sy >= gridHeight) { sy = 0; } else { sy = sy * gridWidth; }
				if (sx != 0) tryBlocks[k++] = base + sx;
				if (sy != 0) tryBlocks[k++] = base + sy;
				if (sx != 0 && sy != 0) tryBlocks[k++] = base + sx + sy;
			}

			for (int k = 0; tryBlocks[k] != -1; ++k) {
				i.continue_with(tryBlocks[k]);
				while (i.next()) {
					++tried_nodes;
					long diff_lat = lat - i.nodeLat, diff_lon = lon - i.nodeLon;
					long dist = diff_lat*diff_lat + diff_lon*diff_lon;
					if (dist < minDist) {
						minDist = dist;
						nodeID = i.nodeID; nodeLat = i.nodeLat; nodeLon = i.nodeLon;
					}
				}
			}

			if (nodeID == lastNodeID) break;
		}

		directGeo_lon = nodeLon;
		directGeo_lat = nodeLat;
		if (DEBUG) Log.d("Offline TP", "<- find point: Searched through " + tried_nodes + " nodes in " + (System.currentTimeMillis() - startTime) + "ms");
		return nodeID;
	}

	/**
	 * Find all (transitive) reachable (either outgoing or incoming) edges from source and
	 * add them to graph
	 *
	 * @param graph Graph to add edges to
	 * @param source NodeID to start from
	 * @param out true => outgoing edges; false => incoming edges
	 * @throws IOException
	 */
	public void fillGraphWithoutCore(NestedSimpleGraph graph, int source, boolean out) throws IOException, InterruptedException {
		final long startTime = System.currentTimeMillis();
		@SuppressWarnings("unused") // debug var
		int edges = 0;
		final NodeEdgesIterator i = new NodeEdgesIterator();
		final IntOpenHashSet have = new IntOpenHashSet();
		final IntArrayDeque todo = new IntArrayDeque();
		if ((source >>> 10) < coreBlock) todo.addLast(source); // don't add edges from core nodes

		while (!todo.isEmpty()) {
			final int[] todoRun = todo.toArray();
			java.util.Arrays.sort(todoRun);
			todo.clear();

			for (final int n: todoRun) {
				edges += i.load(n, out);
				while (i.next()) {
					int peer = i.edgePeer;
	
					if (out) {
						graph.addEdge(n, peer, i.edgeDist, i.edgeID);
					} else {
						graph.addEdge(peer, n, i.edgeDist, i.edgeID);
					}
	
					// don't add core nodes; only add new nodes to the todo list
					if ((peer >>> 10) < coreBlock && have.add(peer)) todo.addLast(peer);
				}
			}
		}
		if (DEBUG) Log.d("Offline TP", "<- found " + edges + (out ? " out" : "  in") + " edges from node " + source + " in " + (System.currentTimeMillis() - startTime) + "ms");
	}

	/**
	 * Find all (transitive) reachable (either outgoing or incoming) edges from source and
	 * add them to graph
	 *
	 * @param graph Graph to add edges to
	 * @param source NodeID to start from
	 * @param out true => outgoing edges; false => incoming edges
	 * @throws IOException
	 */
	public NestedSimpleGraph createGraphWithoutCore(int source, boolean out) throws IOException, InterruptedException {
		final long startTime = System.currentTimeMillis();
		final NestedSimpleGraph graph = new NestedSimpleGraph();
		@SuppressWarnings("unused") // debug var
		int edges = 0;
		final NodeEdgesIterator i = new NodeEdgesIterator();
		final IntOpenHashSet have = new IntOpenHashSet();
		final IntArrayDeque todo = new IntArrayDeque();
		if ((source >>> 10) < coreBlock) todo.addLast(source); // don't add edges from core nodes

		if (out) {
			while (!todo.isEmpty()) {
				final int[] todoRun = todo.toArray();
				java.util.Arrays.sort(todoRun);
				todo.clear();

				for (final int n: todoRun) {
					if ((n >>> 10) >= coreBlock) continue; // skip edges from core nodes
	
					int nodeEdges = i.load(n, out);
					edges += nodeEdges;
					graph.prepareNode(n, nodeEdges);
					while (i.next()) {
						int peer = i.edgePeer;
						graph.addEdge(n, peer, i.edgeDist, i.edgeID);
	
						// don't add core nodes; only add new nodes to the todo list
						if ((peer >>> 10) < coreBlock && have.add(peer)) todo.addLast(peer);
					}
				}
			}
		} else {
			while (!todo.isEmpty()) {
				final int[] todoRun = todo.toArray();
				java.util.Arrays.sort(todoRun);
				todo.clear();

				for (final int n: todoRun) {
					if ((n >>> 10) >= coreBlock) continue; // skip edges from core nodes
					edges += i.load(n, out);
					while (i.next()) {
						int peer = i.edgePeer;
						graph.addEdge(peer, n, i.edgeDist, i.edgeID);
	
						// don't add core nodes; only add new nodes to the todo list
						if ((peer >>> 10) < coreBlock && have.add(peer)) todo.addLast(peer);
					}
				}
			}
		}
		if (DEBUG) Log.d("Offline TP", "<- found " + edges + (out ? " out" : "  in") + " edges from node " + source + " in " + (System.currentTimeMillis() - startTime) + "ms");
		return graph;
	}

	/** result of {@link #expandShortcuts(IntArrayDeque, IntArrayDeque)} */
	public IntArrayDeque path_nodes = new IntArrayDeque();
	/** result of {@link #expandShortcuts(IntArrayDeque, IntArrayDeque)} */
	public int path_euclid_length;

	/**
	 * traverse the given path, expanding shortcuts, storing the visited nodeIDs in {@link #path_nodes}.<br>
	 * Also store the total euclid length of the path in {@link #path_euclid_length}
	 *
	 * @param path_nodes NodeIDs on the path: <pre>n_0 .. n_k</pre>
	 * @param path_edges EdgeIDs for the edges between the nodes: <pre>e_1 .. e_k</pre>
	 * @throws IOException
	 */
	public void expandShortcuts(IntArrayDeque path_nodes, IntArrayDeque path_edges) throws IOException, InterruptedException {
		final long startTime = System.currentTimeMillis();
		final int had_nodes = path_nodes.size();
		this.path_nodes.clear();
		path_euclid_length = 0;

		// if (DEBUG) Log.d("Offline TP", "expandShortcuts: start node: " + sc_nodes.getFirst());
		// start node is the start node - nothing to expand
		this.path_nodes.addLast(path_nodes.removeFirst());

		while (!path_nodes.isEmpty()) {
			// remove edge; whether expanded or not, we don't need it in the list anymore
			int edgeID = path_edges.removeFirst();
			// only read the next node: if we need to expand the edge, we need to push another node before it
			int targetID = path_nodes.getFirst();
			// if (DEBUG) Log.d("Offline TP", "expandShortcuts: expanding edge: " + edgeID + " target node: " + nodeIDStr(targetID));

			for (;;) {
				directEdgeDetailsLoad(edgeID);
				if (-1 == directEdgeDetails_shortcutnode) {
					path_euclid_length += directEdgeDetails_euclid;
					// now move the node to the final list
					path_nodes.removeFirst();
					this.path_nodes.addLast(targetID);
					break;
				} else {
					// expanding shortcut
					// if (DEBUG) Log.d("Offline TP", "expandShortcuts: shortcut edges: " + directEdgeDetails_shortcut1 + " " + directEdgeDetails_shortcut2 + " node: " + nodeIDStr(directEdgeDetails_shortcutnode));
					// store second part of shortcut in the list
					path_edges.addFirst(directEdgeDetails_shortcut2);
					// handle first part in next round
					targetID = directEdgeDetails_shortcutnode;
					path_nodes.addFirst(targetID); // current target node needs to be first in the sc_nodes list
					edgeID = directEdgeDetails_shortcut1;
				}
			}
		}

		if (DEBUG) Log.d("Offline TP", "<- expanded " + (this.path_nodes.size() - had_nodes) + " shortcuts in " + (System.currentTimeMillis() - startTime) + "ms");
	}

	/**
	 * result of {@link #loadWayCoords()}.<br>
	 * latitude/longitude pairs in GeoPoint style (degree * 1E7)
	 */
	public int[] way_coords;

	/**
	 * store the coordinates of the nodes in {@link #path_nodes} as latitude/longitude pairs
	 * (in Position format: degree * 1E7) in {@link #way_coords}.
	 * @throws IOException
	 */
	public void loadWayCoords() throws IOException, InterruptedException {
		final long startTime = System.currentTimeMillis();

		way_coords = new int[path_nodes.size()*2];
		int pos = 0;
		for (IntCursor n: path_nodes) {
			directGeoLoad(n.value);
			way_coords[pos++] = directGeo_lat;
			way_coords[pos++] = directGeo_lon;
		}

		if (DEBUG) Log.d("Offline TP", "<- loaded way coordinates for " + this.path_nodes.size() + " nodes in " + (System.currentTimeMillis() - startTime) + "ms");
	}

	/**
	 * Iterates over all nodes of a block and its linked successors
	 * <p>
	 * Use like this:
	 * <pre>
	 *    {@code i = new NodeGeoIterator(blockStart);
	 *     while (i.next()) {
	 *         // use i.nodeID, i.nodeLon, i.nodeLat
	 *     }
	 * </pre>
	 */
	final class NodeGeoIterator {
		private FileCache cache = new FileCache(file);

		private int block; /** current block to search next item in */
		private int nextBlock; /** (if item != 0): next block after current block */
		private int item; /** index of next item in current block. if item == 0: need to load block information first */
		private int count; /** (if item != 0): count of items in current block */
		private long offset; /** (if item != 0): file offset for the next item's longitude/latitude data */

		/**
		 * set of visited blocks, so we can start from a list of blocks to search (by
		 * calling continue_with() after a block finished), but not search blocks
		 * more than once.
		 */
		private IntOpenHashSet visited = new IntOpenHashSet();

		/** contains the loaded node information after {@link #next()} returned true */
		public int nodeID, nodeLon, nodeLat;

		public NodeGeoIterator() throws IOException, InterruptedException {
			this(-1);
		}

		/**
		 * @see #load(int)
		 */
		public NodeGeoIterator(int blockStart) throws IOException, InterruptedException {
			continue_with(blockStart);
		}

		/**
		 * start search from a new block, but don't revisit already visited blocks again
		 *
		 * @param blockStart block to start from
		 */
		public void continue_with(int blockStart) {
			block = blockStart;
			item = 0;
		}

		/**
		 * start search from a new block, and visit all blocks reachable from this
		 *
		 * @param blockStart block to start from
		 */
		public void load(int blockStart) {
			block = blockStart;
			item = 0;
			visited.clear();
		}

		/**
		 * load next node
		 *
		 * @return returns whether item was loaded (otherwise reached end of list)
		 * @throws IOException
		 */
		public boolean next() throws IOException, InterruptedException {
			if (0 == item) {
				/* find non empty block, starting with current block */
				for (count = 0; 0 == count; block = nextBlock) {
					if (-1 == block) return false;
					assert(block < blockCount);

					if (!visited.add(block)) {
						/* already seen this block; abort */
						block = -1;
						return false;
					}

					/* read block header */
					nodeID = (block << 10);
					offset = offsetNodeGeo + block * strideNodeGeoBlock;
					cache.seek(offset);
					nextBlock = cache.readInt();
					count = cache.readInt();
					assert(count <= blockSize);
					offset += 8;
				}
			} else {
				++nodeID;
			}
			nodeLon = cache.readInt();
			nodeLat = cache.readInt();

			offset += 8;
			++item;

			if (item >= count) {
				item = 0;
				block = nextBlock;
			}

			return true;
		}
	}

	/**
	 * Iterates over (outgoing or incoming) edges of a node.
	 * <p>
	 * Use like this:
	 * <pre>
	 *    {@code i = new NodeEdgesIterator(nodeID, true);
	 *    while (i.next()) {
	 *        // use i.edgePeer, i.edgeDist, i.edgeID
	 *    }
	 * </pre>
	 */
	final class NodeEdgesIterator {
		private int lastEdgeID; /** edge id to stop at */
		private FileNCache cache = new FileNCache(file, 16);

		/** contains the loaded edge information after {@link #next()} returned true */
		public int edgePeer, edgeDist, edgeID;

		public NodeEdgesIterator() throws IOException, InterruptedException {
			edgeID = lastEdgeID = 0;
		}

		/**
		 * @param nodeID        nodeID to iterate edges for
		 * @param out           true => iterate out edges; false => iterate in edges
		 * @throws IOException
		 */
		public NodeEdgesIterator(int nodeID, boolean out) throws IOException, InterruptedException {
			load(nodeID, out);
		}

		/**
		 * @param nodeID        nodeID to iterate edges for
		 * @param out           true => iterate out edges; false => iterate in edges
		 * @return              number of edges
		 * @throws IOException
		 */
		public int load(int nodeID, boolean out) throws IOException, InterruptedException {
			if (-1 == nodeID) {
				edgeID = lastEdgeID = 0;
				return 0;
			} else {
				int block = (nodeID >>> 10);
				nodeID = (nodeID & 1023);
				assert(block < blockCount);
				assert(nodeID < blockSize);

				/* in each block, the start index for one edge list is followed by
				 * another "start index" for the next list, which also marks the
				 * end of the list before
				 * (the last "start index" if followed by a simple end marker, which does not start a new list)
				 * also the block starts with a (reserved) value (0 for now)
				 * for each node we first have the index for outgoing edges, then incoming edges
				 */
				long head = offsetNodeEdges + (block * strideNodeEdgesBlock) + nodeID * 8 + (out ? 4 : 8);
				int firstEdge;
				if (ncache != null) {
					ncache.seek(head);
					firstEdge = ncache.readInt();
					lastEdgeID = ncache.readInt();
				} else {
					file.seek(head);
					firstEdge = file.readInt();
					lastEdgeID = file.readInt();
				}
				edgeID = firstEdge - 1; /* incremented by next() to get the current edgeID */
				if (firstEdge < lastEdgeID) cache.seek(offsetEdges + strideEdge*firstEdge);
				assert(firstEdge <= lastEdgeID);
				return lastEdgeID - firstEdge;
			}
		}

		/**
		 * load next edge
		 *
		 * @return returns whether item was loaded (otherwise reached end of list)
		 */
		public boolean next() throws IOException, InterruptedException {
			int e = edgeID + 1;
			if (e >= lastEdgeID) return false;
			edgeID = e;
			edgePeer = cache.readInt();
			edgeDist = cache.readInt();
			return true;
		}
	}
}
