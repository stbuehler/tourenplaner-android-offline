package de.unistuttgart.informatik.OfflineToureNPlaner.Graph;

import java.io.IOException;

final class FileCache {
	public final int PAGE_SIZE;
	public final int PAGE_MASK;

	private long offset;
	private int localOffset;
	private boolean loaded = false;
	private RandomInputStream file;
	private final long fileLength;
	private int[] intBuffer;

	public FileCache(RandomInputStream file) throws IOException {
		this.file = file;
		fileLength = file.length();
		PAGE_SIZE = file.recommendedPageSize();
		PAGE_MASK = PAGE_SIZE-1;
		intBuffer = new int[PAGE_SIZE/4];
	}

	private void map() throws IOException, InterruptedException {
		if (Thread.interrupted()) throw new InterruptedException("interrupted while doing disk io");

		final int pageSize = (int) Math.min(fileLength - offset, PAGE_SIZE);
		assert pageSize > 0;

		file.seek(offset);
		file.readIntArray(intBuffer, 0, pageSize / 4);
		loaded = true;
	}

	public void seek(long offset) throws IOException, InterruptedException {
		if (this.offset == (offset & ~PAGE_MASK)) {
			// offset isn't changing, no need to reload. just store the new localOffset
			localOffset = (int) (offset & PAGE_MASK);
		} else {
			localOffset = (int) (offset & PAGE_MASK);
			this.offset = offset & ~PAGE_MASK;
			loaded = false;
		}
	}

	public int readInt() throws IOException, InterruptedException {
		if (!loaded) map();
		assert(0 == (localOffset & 3));
		int res = intBuffer[localOffset/4];
		localOffset += 4;
		if (localOffset >= PAGE_SIZE) {
			assert(localOffset == PAGE_SIZE);
			offset = offset + PAGE_SIZE;
			localOffset = 0;
			loaded = false;
		}
		return res;
	}

	public long getOffset() {
		return offset + localOffset;
	}
}