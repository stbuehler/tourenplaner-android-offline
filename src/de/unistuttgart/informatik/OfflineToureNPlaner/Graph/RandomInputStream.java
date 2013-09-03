package de.unistuttgart.informatik.OfflineToureNPlaner.Graph;

import java.io.Closeable;
import java.io.IOException;

public interface RandomInputStream extends Closeable {
	long length() throws IOException;
	long position() throws IOException;
	void seek(long position) throws IOException;
	void skip(long offset) throws IOException;

	int readInt() throws IOException;
	void readIntArray(int[] buf, int off, int len) throws IOException;

	int recommendedPageSize();

	public static class RandAccFile implements RandomInputStream {
		private java.io.RandomAccessFile file;

		RandAccFile(java.io.RandomAccessFile file) {
			this.file = file;
		}

		@Override
		public void close() throws IOException {
			file.close();
		}

		@Override
		public long length() throws IOException {
			return file.length();
		}

		@Override
		public long position() throws IOException {
			return file.getFilePointer();
		}

		@Override
		public void seek(long position) throws IOException {
			file.seek(position);
		}

		@Override
		public void skip(long offset) throws IOException {
			file.seek(file.getFilePointer() + offset);
		}

		@Override
		public int readInt() throws IOException {
			return file.readInt();
		}

		@Override
		public void readIntArray(int[] buf, int off, int len) throws IOException {
			long offset = file.getFilePointer();
			final int BUFSIZE = 16*1024; // 64kb

			final java.nio.channels.FileChannel channel = file.getChannel();
			while (len > 0) {
				int want = len > BUFSIZE ? BUFSIZE : len;
				java.nio.MappedByteBuffer buffer = channel.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, offset, want * 4);
				buffer.order(java.nio.ByteOrder.BIG_ENDIAN);
				buffer.asIntBuffer().get(buf, off, want);
				off += want;
				len -= want;
				offset += want * 4;
			}
			file.seek(offset);
		}

		@Override
		public int recommendedPageSize() {
			return 4096;
		}
	}
}
