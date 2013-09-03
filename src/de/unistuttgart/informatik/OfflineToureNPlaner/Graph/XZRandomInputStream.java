package de.unistuttgart.informatik.OfflineToureNPlaner.Graph;

import java.io.File;
import java.io.IOException;

import de.unistuttgart.informatik.OfflineToureNPlaner.xz.XZInputStream;

public class XZRandomInputStream implements RandomInputStream {
	private XZInputStream stream;
	private long offset;

	public XZRandomInputStream(File file) throws IOException {
		stream = new XZInputStream(file.getAbsolutePath());
		offset = 0;
	}

	@Override
	public void close() throws IOException {
		stream = null;
	}

	@Override
	public long length() throws IOException {
		return stream.length();
	}

	@Override
	public long position() throws IOException {
		return offset;
	}

	@Override
	public void seek(long position) throws IOException {
		if (position < 0 || position > stream.length()) throw new IOException("seek position " + position + " outside valid range [0-" + stream.length() +"]");
		offset = position;
	}

	@Override
	public void skip(long offset) throws IOException {
		seek(this.offset + offset);
	}

	@Override
	public int readInt() throws IOException {
		int[] buf = new int[1];
		readIntArray(buf, 0, 1);
		return buf[0];
	}

	@Override
	public void readIntArray(int[] buf, int off, int len) throws IOException {
		stream.readInt(offset, buf, off, len);
		offset += 4 * len;
	}

	@Override
	public int recommendedPageSize() {
		// xz seeking is expensive - use "large" buffers
		return 16*1024;
	}
}
