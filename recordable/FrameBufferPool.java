package dev.recordable;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public final class FrameBufferPool {
	private static final FrameBufferPool INSTANCE = new FrameBufferPool();
	private static final int MAX_POOLED = 6;
	private final ConcurrentLinkedQueue<byte[]> pool = new ConcurrentLinkedQueue();
	private final AtomicInteger pooledCount = new AtomicInteger(0);
	private volatile int currentSize = -1;
	private volatile boolean enabled = true;

	private FrameBufferPool() {
	}

	public static FrameBufferPool getInstance() {
		return INSTANCE;
	}

	public void setEnabled(boolean value) {
		this.enabled = value;
		if (!value) {
			this.clear();
		}
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	public byte[] acquire(int size) {
		if (this.enabled && size > 0) {
			if (size != this.currentSize) {
				this.clear();
				this.currentSize = size;
			}

			byte[] buf = (byte[])this.pool.poll();
			if (buf != null) {
				this.pooledCount.decrementAndGet();
				if (buf.length == size) {
					return buf;
				}
			}

			return new byte[size];
		} else {
			return new byte[Math.max(0, size)];
		}
	}

	public void release(byte[] buffer) {
		if (this.enabled && buffer != null && buffer.length == this.currentSize) {
			if (this.pooledCount.get() < 6) {
				this.pool.offer(buffer);
				this.pooledCount.incrementAndGet();
			}
		}
	}

	public void clear() {
		this.pool.clear();
		this.pooledCount.set(0);
	}

	public int getPooledCount() {
		return this.pooledCount.get();
	}
}
