package de.unistuttgart.informatik.OfflineToureNPlaner.MapsForge;

import java.util.Arrays;
import java.util.Locale;

import org.mapsforge.core.MercatorProjection;

import android.graphics.Path;
import android.graphics.PointF;

import de.unistuttgart.informatik.OfflineToureNPlaner.Data.Position;
import de.unistuttgart.informatik.OfflineToureNPlaner.Data.Way;

public class WayZoom {
	final int count; // how many points
	// x,y pixel pairs at zoom level 0
	// zoom level is only a *2^zoomlevel factor
	final double[] coords;
	// each point has a "mininum" zoom level needed to display it
	final byte[] visibleZoomLevel;

	final BoundingBoxTreeNode rootBoundingBoxNode;

	final static byte calcZoomLevels[] = { 20, 16, 14, 12, 8, 4, 0 };
	final static long zoomMinPixels = 16; // remove points if both (visible) neighbor nodes are less than this away

	public WayZoom(Way way) {
		final Position[] positions = way.wayPoints();
		count = positions.length;
		coords = new double[count * 2];
		for (int i = 0; i < count; ++i) {
			coords[2*i  ] = MercatorProjection.longitudeToPixelX(positions[i].longitude(), (byte) 0);
			coords[2*i+1] = MercatorProjection.latitudeToPixelY(positions[i].latitude(), (byte) 0);
		}

		visibleZoomLevel = new byte[count];

		if (count > 0) {
			byte prevLevel = 32;
			/* start by setting all points to be visible only at the highest level */
			Arrays.fill(visibleZoomLevel, prevLevel);
			/* start and end point must always be visible */
			visibleZoomLevel[0] = visibleZoomLevel[count-1] = 0;
			/* no calc for some levels "lvl" which points have to visible for the level range from "lvl" to (previous "lvl" - 1);
			 * these points get visibleZoomLevel[point] = lvl
			 * (calcZoomLevels are sorted descending, so small levels can overwrite this)
			 */
			for (byte lvl: calcZoomLevels) {
				long zoom = 1l << (prevLevel - 1);
				int previous = 0;
				int next = 1;
				while (next < count && visibleZoomLevel[next] > prevLevel) ++next; // skip items that are invisible on the previous level
				while (next < count - 1) {
					final int current = next++;
					while (next < count && visibleZoomLevel[next] > prevLevel) ++next; // skip items that are invisible on the previous level
					assert next < count; // visibleZoomLevel[count-1] is always 0, and <= prevLevel.

					if (dist(current, previous)*zoom >= zoomMinPixels || dist(current, next)*zoom >= zoomMinPixels) {
						// visible on current level, and next node can be omitted if close enough to current node
						visibleZoomLevel[current] = lvl;
						previous = current;
					}
					// else: hide this node on this level and below (don't update visibleZoomLevel),
					//       and compare next node against previous node (don't update previous)
				}
	
				prevLevel = lvl;
			}
		}

		rootBoundingBoxNode = createBoundingBox(0, count - 1);
	}

	private double dist(int a, int b) {
		final double dx = coords[2*a] - coords[2*b];
		final double dy = coords[2*a+1] - coords[2*b+1];
		return Math.sqrt(dx*dx+dy*dy);
	}

	private BoundingBoxTreeNode createBoundingBox(int first, int last) {
		if (first > last) return null;
		if (last - first > 4) {
			int mid = (first + last) / 2;
			return new BoundingBoxTreeNode(createBoundingBox(first, mid), createBoundingBox(mid, last));
		}
		// calculate bounding box for [first..last]
		double left = coords[2*first], right = left, top = coords[2*first+1], bottom = top;
		for (int i = first+1; i <= last; ++i) {
			left = Math.min(left, coords[2*i]);
			right = Math.max(right, coords[2*i]);
			bottom = Math.min(bottom, coords[2*i+1]);
			top = Math.max(top, coords[2*i+1]);
		}
		// to draw the complete path in the box we need the neighbor points outside too
		if (first > 0) --first;
		if (last < count - 1) ++last;
		return new BoundingBoxTreeNode(first, last, new BoundingBox(left, top, right, bottom));
	}

	private void moveTo(Path path, int node, PointF drawPosition, long zoom) {
		path.moveTo((float) (coords[node * 2] * zoom - drawPosition.x), (float) (coords[node * 2 + 1] * zoom - drawPosition.y));
	}

	private void lineTo(Path path, int node, PointF drawPosition, long zoom) {
		path.lineTo((float) (coords[node * 2] * zoom - drawPosition.x), (float) (coords[node * 2 + 1] * zoom - drawPosition.y));
	}

	private int addToPath(int prevNode, int first, int last, Path path, PointF drawPosition, byte drawZoomLevel) {
		long zoom = 1l << drawZoomLevel;
		if (first > prevNode) {
			moveTo(path, first, drawPosition, zoom);
		} else {
			first = prevNode;
		}
		for (int i = first + 1; i <= last; ++i) {
			if (i < last && visibleZoomLevel[i] > drawZoomLevel) continue;
			lineTo(path, i, drawPosition, zoom);
		}
		return last;
	}

	private int addToPath(int prevNode, BoundingBoxTreeNode node, Path path, PointF drawPosition, byte drawZoomLevel, BoundingBox box) {
		switch (BoundingBox.hit(node.box, box)) {
		case DISJUNCT:
			break;
		case PARTIAL:
			if (node.child1 != null) {
				prevNode = addToPath(prevNode, node.child1, path, drawPosition, drawZoomLevel, box);
				prevNode = addToPath(prevNode, node.child2, path, drawPosition, drawZoomLevel, box);
				break;
			}
			/* fall through! */
		default:
			prevNode = addToPath(prevNode, node.first, node.last, path, drawPosition, drawZoomLevel);
			break;
		}
		return prevNode;
	}

	public void setupPath(Path path, PointF drawPosition, byte drawZoomLevel, Position topLeft, Position bottomRight) {
		path.rewind();

		if (rootBoundingBoxNode == null) return;

		// pixel projection swaps top/bottom (top row starts at 0)
		BoundingBox box = new BoundingBox(
			MercatorProjection.longitudeToPixelX(topLeft.longitude(), (byte) 0),
			MercatorProjection.latitudeToPixelY(bottomRight.latitude(), (byte) 0),
			MercatorProjection.longitudeToPixelX(bottomRight.longitude(), (byte) 0),
			MercatorProjection.latitudeToPixelY(topLeft.latitude(), (byte) 0));

		addToPath(-1, rootBoundingBoxNode, path, drawPosition, drawZoomLevel, box);
	}

	private static class BoundingBox {
		public final double left, top, right, bottom;

		public BoundingBox(double left, double top, double right, double bottom) {
			this.left = left;
			this.top = top;
			this.right = right;
			this.bottom = bottom;
			assert this.left <= this.right;
			assert this.bottom <= this.top;
		}

		// union
		public BoundingBox(BoundingBox child1, BoundingBox child2) {
			this.left = Math.min(child1.left, child2.left);
			this.top = Math.max(child1.top, child2.top);
			this.right = Math.max(child1.right, child2.right);
			this.bottom = Math.min(child1.bottom, child2.bottom);
			assert this.left <= this.right;
			assert this.bottom <= this.top;
		}

		public static enum HitResult { DISJUNCT, INCLUSIVE, PARTIAL };
		/** whether a and b are disjunct, a is in b, or they overlap somehow */
		public static HitResult hit(BoundingBox a, BoundingBox b) {
			if (a.right < b.left || a.left > b.right || a.top < b.bottom || a.bottom > b.top) return HitResult.DISJUNCT;
			if (a.right <= b.right && a.left >= b.left && a.top <= b.top && a.bottom >= b.bottom) return HitResult.INCLUSIVE;
			return HitResult.PARTIAL;
		}

		@Override
		public String toString() {
			return String.format(Locale.US, "BoundingBox(left=%f, top=%f, right=%f, bottom=%f)", left, top, right, bottom);
		}
	}

	private static class BoundingBoxTreeNode {
		/** index range in points this box is covering (first and last may be outside, but have paths into the box) */
		public final int first, last;
		public final BoundingBox box;
		/** sub boxes */
		public final BoundingBoxTreeNode child1, child2;

		public BoundingBoxTreeNode(int first, int last, BoundingBox box) {
			this.first = first;
			this.last = last;
			this.box = box;
			this.child1 = this.child2 = null;
		}

		public BoundingBoxTreeNode(BoundingBoxTreeNode child1, BoundingBoxTreeNode child2) {
			this.first = Math.min(child1.first, child2.first);
			this.last = Math.max(child1.last, child2.last);
			this.box = new BoundingBox(child1.box, child2.box);
			this.child1 = child1;
			this.child2 = child2;
		}
	}
}
