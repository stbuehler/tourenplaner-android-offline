package de.unistuttgart.informatik.OfflineToureNPlaner.Graph;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

import org.tukaani.xz.SeekableFileInputStream;
import org.tukaani.xz.SeekableXZInputStream;

/**
 * The xz java implementation is rather slow - but it would work.
 * Obviously this needs the xz java library, so it is excluded from
 * build right now.
 */
public class XZRandomInputStreamPure implements RandomInputStream {
	private static final int BUF_SIZE = 4096;

	private final byte[] byteBuffer = new byte[BUF_SIZE];
	private final IntBuffer intBuffer;
	private SeekableXZInputStream xzStream;

	public XZRandomInputStreamPure(File file) throws IOException {
		intBuffer = ByteBuffer.wrap(byteBuffer).order(ByteOrder.BIG_ENDIAN).asIntBuffer();
		xzStream = new SeekableXZInputStream(new SeekableFileInputStream(file));
	}

	public XZRandomInputStreamPure(RandomAccessFile file) throws IOException {
		intBuffer = ByteBuffer.wrap(byteBuffer).order(ByteOrder.BIG_ENDIAN).asIntBuffer();
		xzStream = new SeekableXZInputStream(new SeekableFileInputStream(file));
	}

	@Override
	public void close() throws IOException {
		xzStream.close();
	}

	@Override
	public long length() throws IOException {
		return xzStream.length();
	}

	@Override
	public long position() throws IOException {
		return xzStream.position();
	}

	@Override
	public void seek(long position) throws IOException {
		xzStream.seek(position);
	}

	@Override
	public void skip(long offset) throws IOException {
		xzStream.seek(xzStream.position() + offset);
	}

	@Override
	public int readInt() throws IOException {
		if (4 != xzStream.read(byteBuffer, 0, 4)) throw new IOException("Unexpected end of file");
		return intBuffer.get(0);
	}

	@Override
	public void readIntArray(int[] buf, int off, int len) throws IOException {
		while (len > 0) {
			final int want = Math.min(len, BUF_SIZE / 4);
			final int bytes = 4*want;
			if (bytes != xzStream.read(byteBuffer, 0, bytes)) throw new IOException("Unexpected end of file");
			intBuffer.rewind();
			intBuffer.get(buf, off, want);
			off += want;
			len -= want;
		}
	}

	@Override
	public int recommendedPageSize() {
		// xz seeking is expensive - use "large" buffers
		return 16*1024;
	}
}
