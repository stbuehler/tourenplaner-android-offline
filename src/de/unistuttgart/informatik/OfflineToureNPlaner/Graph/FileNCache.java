package de.unistuttgart.informatik.OfflineToureNPlaner.Graph;

import java.io.IOException;

final class FileNCache {
	public final int PAGE_SIZE;
	public final int PAGE_MASK;

	private long offset;
	private int localOffset, slot = -1;
	private final int slots;
	private final RandomInputStream file;
	private final long fileLength;
	private final int[] intBuffer;
	private final long[] offsets;
	private final int[] slotLRUNext, slotLRUPrev; // last entry is the cyclic head link

	public FileNCache(RandomInputStream file, int slots) throws IOException {
		this.slots = slots;
		this.file = file;
		fileLength = file.length();

		PAGE_SIZE = file.recommendedPageSize();
		PAGE_MASK = PAGE_SIZE-1;

		intBuffer = new int[slots*(PAGE_SIZE/4)];
		offsets = new long[slots];
		java.util.Arrays.fill(offsets, -1);

		// init cyclic double linked list with last entry as cyclic head
		slotLRUNext = new int[slots+1];
		slotLRUPrev = new int[slots+1];
		for (int i = 0; i < slots; ++i) {
			slotLRUNext[i] = i+1;
			slotLRUPrev[i+1] = i;
		}
		slotLRUNext[slots] = 0;
		slotLRUPrev[0] = slots;
	}

	private void slotMarkLRU(int s) {
		if (s == slotLRUNext[slots]) return; /* already head */

		// remove s from list
		slotLRUNext[slotLRUPrev[s]] = slotLRUNext[s];
		slotLRUPrev[slotLRUNext[s]] = slotLRUPrev[s];
		// add as head
		slotLRUPrev[s] = slots;
		slotLRUNext[s] = slotLRUNext[slots];
		slotLRUNext[slots] = s;
		slotLRUPrev[slotLRUNext[s]] = s;
	}

	private void slotUnmarkLRU(int s) {
		if (s == slotLRUPrev[slots]) return; /* already tail */

		// remove s from list
		slotLRUNext[slotLRUPrev[s]] = slotLRUNext[s];
		slotLRUPrev[slotLRUNext[s]] = slotLRUPrev[s];
		// add as tail
		slotLRUNext[s] = slots;
		slotLRUPrev[s] = slotLRUPrev[slots];
		slotLRUPrev[slots] = s;
		slotLRUNext[slotLRUPrev[s]] = s;
	}

	private void map() throws IOException, InterruptedException {
		if (Thread.interrupted()) throw new InterruptedException("interrupted while doing disk io");

		for (slot = 0; slot < offsets.length; ++slot) {
			if (offset == offsets[slot]) {
				/* already mapped */
				slotMarkLRU(slot);
				return;
			}
		}

		slot = slotLRUPrev[slots]; // purge last entry
		offsets[slot] = offset;
		slotMarkLRU(slot);

		int pageSize = (int) Math.min(fileLength - offset, PAGE_SIZE);
		assert pageSize > 0;

		file.seek(offset);
		file.readIntArray(intBuffer, slot*(PAGE_SIZE/4), pageSize / 4);
	}

	public void seek(long offset) throws IOException {
		if (this.offset == (offset & ~PAGE_MASK)) {
			// offset isn't changing, no need to reload. just store the new localOffset
			localOffset = (int) (offset & PAGE_MASK);
		} else {
			localOffset = (int) (offset & PAGE_MASK);
			this.offset = offset & ~PAGE_MASK;
			slot = -1;
		}
	}

	public int readInt() throws IOException, InterruptedException {
		if (-1 == slot) map();
		assert(0 == (localOffset & 3));
		int res = intBuffer[(slot * (PAGE_SIZE/4)) + localOffset/4];
		localOffset += 4;
		if (localOffset >= PAGE_SIZE) {
			assert(localOffset == PAGE_SIZE);
			offset = offset + PAGE_SIZE;
			localOffset = 0;
			slotUnmarkLRU(slot); // we reached the end, so we are probably "done" with this slot
			slot = -1;
		}
		return res;
	}

	public long getOffset() {
		return offset + localOffset;
	}
}