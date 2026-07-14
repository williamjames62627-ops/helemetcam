package dev.recordable;

import java.util.concurrent.atomic.AtomicLong;

public final class PerformanceMetrics {
	private static final PerformanceMetrics INSTANCE = new PerformanceMetrics();
	private final AtomicLong framesCaptured = new AtomicLong(0L);
	private final AtomicLong framesDropped = new AtomicLong(0L);
	private final AtomicLong framesEncoded = new AtomicLong(0L);
	private final AtomicLong adaptiveDrops = new AtomicLong(0L);
	private final AtomicLong queueSize = new AtomicLong(0L);
	private final AtomicLong queueCapacity = new AtomicLong(240L);
	private final AtomicLong currentFps = new AtomicLong(0L);
	private final AtomicLong encoderFps = new AtomicLong(0L);
	private final AtomicLong memoryUsedMiB = new AtomicLong(0L);
	private final AtomicLong fileSizeBytes = new AtomicLong(0L);
	private final AtomicLong captureLatencySumNanos = new AtomicLong(0L);
	private final AtomicLong captureLatencyCount = new AtomicLong(0L);
	private final AtomicLong encodeLatencySumNanos = new AtomicLong(0L);
	private final AtomicLong encodeLatencyCount = new AtomicLong(0L);
	private volatile double avgCaptureLatencyMs = 0.0;
	private volatile double avgEncodeLatencyMs = 0.0;
	private volatile double bufferHealthPercent = 100.0;

	private PerformanceMetrics() {
	}

	public static PerformanceMetrics getInstance() {
		return INSTANCE;
	}

	public void recordFrameCapture(long durationNanos) {
		this.framesCaptured.incrementAndGet();
		this.captureLatencySumNanos.addAndGet(durationNanos);
		long count = this.captureLatencyCount.incrementAndGet();
		if (count > 0L) {
			this.avgCaptureLatencyMs = (double)this.captureLatencySumNanos.get() / (double)count / 1000000.0;
		}
	}

	public void recordFrameEncode(long durationNanos) {
		this.framesEncoded.incrementAndGet();
		this.encodeLatencySumNanos.addAndGet(durationNanos);
		long count = this.encodeLatencyCount.incrementAndGet();
		if (count > 0L) {
			this.avgEncodeLatencyMs = (double)this.encodeLatencySumNanos.get() / (double)count / 1000000.0;
		}
	}

	public void recordFrameDrop() {
		this.framesDropped.incrementAndGet();
	}

	public void recordAdaptiveDrop() {
		this.adaptiveDrops.incrementAndGet();
	}

	public void updateQueueStats(int size, int capacity) {
		this.queueSize.set((long)size);
		this.queueCapacity.set((long)capacity);
		this.bufferHealthPercent = capacity > 0 ? Math.max(0.0, 100.0 - (double)size * 100.0 / (double)capacity) : 100.0;
	}

	public void updateFps(long captureFps, long encodeFps) {
		this.currentFps.set(captureFps);
		this.encoderFps.set(encodeFps);
	}

	public void updateMemory(long usedMiB) {
		this.memoryUsedMiB.set(usedMiB);
	}

	public void updateFileSize(long bytes) {
		this.fileSizeBytes.set(bytes);
	}

	public void reset() {
		this.framesCaptured.set(0L);
		this.framesDropped.set(0L);
		this.framesEncoded.set(0L);
		this.adaptiveDrops.set(0L);
		this.captureLatencySumNanos.set(0L);
		this.captureLatencyCount.set(0L);
		this.encodeLatencySumNanos.set(0L);
		this.encodeLatencyCount.set(0L);
		this.avgCaptureLatencyMs = 0.0;
		this.avgEncodeLatencyMs = 0.0;
		this.bufferHealthPercent = 100.0;
		this.queueSize.set(0L);
		this.fileSizeBytes.set(0L);
	}

	public double getAvgCaptureLatencyMs() {
		return this.avgCaptureLatencyMs;
	}

	public double getAvgEncodeLatencyMs() {
		return this.avgEncodeLatencyMs;
	}

	public double getBufferHealthPercent() {
		return this.bufferHealthPercent;
	}

	public long getTotalCaptured() {
		return this.framesCaptured.get();
	}

	public long getTotalDropped() {
		return this.framesDropped.get();
	}

	public long getTotalEncoded() {
		return this.framesEncoded.get();
	}

	public long getCaptureFps() {
		return this.currentFps.get();
	}

	public long getEncoderFps() {
		return this.encoderFps.get();
	}

	public long getMemoryUsedMiB() {
		return this.memoryUsedMiB.get();
	}

	public long getQueueSize() {
		return this.queueSize.get();
	}

	public long getQueueCapacity() {
		return this.queueCapacity.get();
	}

	public String getCompactSummary() {
		return String.format("Cap %.1fms | Enc %.1fms | Buf %.0f%%", this.avgCaptureLatencyMs, this.avgEncodeLatencyMs, this.bufferHealthPercent);
	}
}
