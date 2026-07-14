package dev.recordable;

import dev.recordable.FFmpegEncoder.FfmpegStatus;
import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import net.minecraft.class_310;

public final class ReplayBuffer {
	private static final ReplayBuffer INSTANCE = new ReplayBuffer();
	private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final long CHUNK_MAX_BYTES = 33554432L;
	private final Object lock = new Object();
	private final Deque<ReplayBuffer.FrameRef> index = new ArrayDeque();
	private final Map<Integer, ReplayBuffer.Chunk> chunks = new HashMap();
	private final AtomicBoolean active = new AtomicBoolean(false);
	private final AtomicBoolean saving = new AtomicBoolean(false);
	private final AtomicLong currentDiskBytes = new AtomicLong(0L);
	private Path bufferDir;
	private ReplayBuffer.Chunk currentChunk;
	private int nextChunkId;
	private boolean pinnedForSaving = false;
	private final Deque<Integer> pendingChunkDeletions = new ArrayDeque();
	private volatile int targetFps = 60;
	private volatile int frameWidth;
	private volatile int frameHeight;
	private volatile int storedFrameWidth;
	private volatile int storedFrameHeight;
	private volatile long bufferDurationMs;
	private volatile long diskBudgetBytes;
	private volatile int maxFrameCount;
	private volatile int downscaleFactor = 1;
	private volatile int fpsCap = 60;
	private volatile long frameIntervalMs = 0L;
	private volatile long lastAcceptedFrameMs = 0L;

	private ReplayBuffer() {
	}

	public static ReplayBuffer getInstance() {
		return INSTANCE;
	}

	public void start(int width, int height, int fps, int durationSeconds) {
		this.start(width, height, fps, durationSeconds, "source");
	}

	public void start(int width, int height, int fps, int durationSeconds, String quality) {
		RecordableConfig config = RecordableConfig.get();
		long budgetMB = PlatformUtils.getReplayBufferDiskBudgetMB();
		long freeBytes = PlatformUtils.getFreeDiskSpaceBytes(config.getOutputDirectory());
		if (freeBytes > 0L) {
			long freeBudgetMB = freeBytes / 1048576L * 80L / 100L;
			budgetMB = Math.min(budgetMB, Math.max(64L, freeBudgetMB));
		}

		this.diskBudgetBytes = budgetMB * 1024L * 1024L;
		int[] preset = RecordableConfig.resolveReplayPreset(quality, height, fps);
		int targetHeight = preset[0];
		int effectiveFps = preset[1] > 0 ? Math.min(fps > 0 ? fps : preset[1], preset[1]) : (fps > 0 ? fps : 60);
		int platformFactor = Math.max(1, PlatformUtils.getReplayBufferDownscaleFactor());
		int qualityFactor = 1;
		if (targetHeight > 0 && height > targetHeight) {
			qualityFactor = Math.max(1, (int)Math.round((double)height / (double)targetHeight));
		}

		this.downscaleFactor = Math.max(platformFactor, qualityFactor);
		this.fpsCap = Math.max(1, effectiveFps);
		this.frameIntervalMs = 1000L / (long)this.fpsCap;
		this.lastAcceptedFrameMs = 0L;
		this.frameWidth = width;
		this.frameHeight = height;
		this.storedFrameWidth = Math.max(1, width / this.downscaleFactor);
		this.storedFrameHeight = Math.max(1, height / this.downscaleFactor);
		this.targetFps = this.fpsCap;
		this.bufferDurationMs = (long)durationSeconds * 1000L;
		long perFrameBytes = (long)this.storedFrameWidth * (long)this.storedFrameHeight * 3L;
		int totalFrames = this.fpsCap * durationSeconds;
		long totalNeeded = perFrameBytes * (long)totalFrames;
		if (perFrameBytes > 0L) {
			int maxByBudget = (int)(this.diskBudgetBytes / perFrameBytes);
			this.maxFrameCount = Math.min(totalFrames, Math.max(1, maxByBudget));
		} else {
			this.maxFrameCount = totalFrames;
		}

		synchronized (this.lock) {
			this.resetStorageLocked();

			try {
				this.bufferDir = config.getOutputDirectory().resolve(".shadow_buffer");
				deleteDirectoryContents(this.bufferDir);
				Files.createDirectories(this.bufferDir);
				this.openNewChunkLocked();
			} catch (Exception var24) {
				RecordableMod.LOGGER.warn("Failed to prepare disk replay buffer directory.", var24);
			}

			this.active.set(true);
		}

		RecordableMod.LOGGER
			.info(
				"Replay buffer started (disk-backed): {}x{} (stored {}x{}, downscale={}x) @{} FPS, {}s, diskBudget={}MB, maxFrames={}, perFrame={}KB, totalNeeded={}MB, dir={}",
				new Object[]{
					width,
					height,
					this.storedFrameWidth,
					this.storedFrameHeight,
					this.downscaleFactor,
					fps,
					durationSeconds,
					budgetMB,
					this.maxFrameCount,
					perFrameBytes / 1024L,
					totalNeeded / 1048576L,
					this.bufferDir
				}
			);
	}

	public void stop() {
		synchronized (this.lock) {
			this.active.set(false);
			if (this.saving.get()) {
				this.pinnedForSaving = true;
				return;
			}

			this.resetStorageLocked();
		}

		RecordableMod.LOGGER.info("Replay buffer stopped. Disk buffer released.");
	}

	private void resetStorageLocked() {
		for (ReplayBuffer.Chunk c : this.chunks.values()) {
			closeChunkOutput(c);

			try {
				Files.deleteIfExists(c.path);
			} catch (Exception var5) {
			}
		}

		this.chunks.clear();
		this.index.clear();
		this.pendingChunkDeletions.clear();
		this.currentChunk = null;
		this.nextChunkId = 0;
		this.currentDiskBytes.set(0L);
		if (this.bufferDir != null) {
			try {
				Files.deleteIfExists(this.bufferDir);
			} catch (Exception var4) {
			}
		}
	}

	private void openNewChunkLocked() throws Exception {
		int id = this.nextChunkId++;
		Path path = this.bufferDir.resolve("chunk-" + id + ".raw");
		ReplayBuffer.Chunk chunk = new ReplayBuffer.Chunk(id, path);
		chunk.out = new BufferedOutputStream(Files.newOutputStream(path), 65536);
		this.chunks.put(id, chunk);
		this.currentChunk = chunk;
	}

	private static void closeChunkOutput(ReplayBuffer.Chunk c) {
		if (c != null && c.out != null) {
			try {
				c.out.flush();
			} catch (Exception var3) {
			}

			try {
				c.out.close();
			} catch (Exception var2) {
			}

			c.out = null;
		}
	}

	public void addFrame(byte[] rgbPixels) {
		if (this.active.get() && rgbPixels != null) {
			long now = System.currentTimeMillis();
			if (this.frameIntervalMs <= 0L || this.lastAcceptedFrameMs == 0L || now - this.lastAcceptedFrameMs >= this.frameIntervalMs - 1L) {
				this.lastAcceptedFrameMs = now;
				byte[] storedPixels;
				if (this.downscaleFactor > 1) {
					storedPixels = downscaleRgb(rgbPixels, this.frameWidth, this.frameHeight, this.downscaleFactor);
				} else {
					storedPixels = rgbPixels;
				}

				synchronized (this.lock) {
					if (this.active.get() && this.currentChunk != null && this.currentChunk.out != null) {
						try {
							long offset = this.currentChunk.bytesWritten;
							this.currentChunk.out.write(storedPixels, 0, storedPixels.length);
							this.currentChunk.bytesWritten += (long)storedPixels.length;
							this.currentChunk.refCount++;
							this.index.addLast(new ReplayBuffer.FrameRef(this.currentChunk.id, offset, storedPixels.length, this.storedFrameWidth, this.storedFrameHeight, now));
							this.currentDiskBytes.addAndGet((long)storedPixels.length);
							if (this.currentChunk.bytesWritten >= 33554432L) {
								closeChunkOutput(this.currentChunk);
								this.openNewChunkLocked();
							}

							long cutoff = now - this.bufferDurationMs;

							while (!this.index.isEmpty()) {
								ReplayBuffer.FrameRef oldest = (ReplayBuffer.FrameRef)this.index.peekFirst();
								if (oldest == null || oldest.timestampMs >= cutoff) {
									break;
								}

								this.evictOldestLocked();
							}

							while (this.currentDiskBytes.get() > this.diskBudgetBytes && !this.index.isEmpty()) {
								this.evictOldestLocked();
							}

							while (this.index.size() > this.maxFrameCount && !this.index.isEmpty()) {
								this.evictOldestLocked();
							}
						} catch (Exception var12) {
							RecordableMod.LOGGER.warn("Failed to append replay frame to disk; stopping buffer.", var12);
							this.active.set(false);
						}
					}
				}
			}
		}
	}

	private void evictOldestLocked() {
		ReplayBuffer.FrameRef removed = (ReplayBuffer.FrameRef)this.index.pollFirst();
		if (removed != null) {
			this.currentDiskBytes.addAndGet((long)(-removed.length));
			ReplayBuffer.Chunk chunk = (ReplayBuffer.Chunk)this.chunks.get(removed.chunkId);
			if (chunk != null) {
				chunk.refCount--;
				if (chunk.refCount <= 0 && chunk != this.currentChunk) {
					if (this.pinnedForSaving) {
						this.pendingChunkDeletions.addLast(chunk.id);
					} else {
						this.removeChunkLocked(chunk);
					}
				}
			}
		}
	}

	private void removeChunkLocked(ReplayBuffer.Chunk chunk) {
		closeChunkOutput(chunk);

		try {
			Files.deleteIfExists(chunk.path);
		} catch (Exception var3) {
		}

		this.chunks.remove(chunk.id);
	}

	private static byte[] downscaleRgb(byte[] src, int srcWidth, int srcHeight, int factor) {
		int dstWidth = srcWidth / factor;
		int dstHeight = srcHeight / factor;
		byte[] dst = new byte[dstWidth * dstHeight * 3];

		for (int dy = 0; dy < dstHeight; dy++) {
			for (int dx = 0; dx < dstWidth; dx++) {
				int r = 0;
				int g = 0;
				int b = 0;
				int count = 0;

				for (int fy = 0; fy < factor; fy++) {
					for (int fx = 0; fx < factor; fx++) {
						int sx = dx * factor + fx;
						int sy = dy * factor + fy;
						int srcIdx = (sy * srcWidth + sx) * 3;
						if (srcIdx + 2 < src.length) {
							r += src[srcIdx] & 255;
							g += src[srcIdx + 1] & 255;
							b += src[srcIdx + 2] & 255;
							count++;
						}
					}
				}

				if (count > 0) {
					int dstIdx = (dy * dstWidth + dx) * 3;
					dst[dstIdx] = (byte)(r / count);
					dst[dstIdx + 1] = (byte)(g / count);
					dst[dstIdx + 2] = (byte)(b / count);
				}
			}
		}

		return dst;
	}

	public void saveBuffer(class_310 client) {
		if (!this.active.get()) {
			RecordableMod.sendClientMessage(ChatCategory.REPLAY_BUFFER, client, "§cReplay buffer is not active.", true);
		} else if (this.saving.getAndSet(true)) {
			RecordableMod.sendClientMessage(ChatCategory.REPLAY_BUFFER, client, "§eReplay buffer is already being saved...", true);
		} else {
			ReplayBuffer.FrameRef[] frames;
			long diskBytes;
			synchronized (this.lock) {
				if (this.index.isEmpty()) {
					this.saving.set(false);
					RecordableMod.sendClientMessage(ChatCategory.REPLAY_BUFFER, client, "§eReplay buffer is empty. Play for a few seconds first.", true);
					return;
				}

				if (this.currentChunk != null && this.currentChunk.out != null) {
					try {
						this.currentChunk.out.flush();
					} catch (Exception var8) {
					}
				}

				frames = (ReplayBuffer.FrameRef[])this.index.toArray(new ReplayBuffer.FrameRef[0]);
				diskBytes = this.currentDiskBytes.get();
				this.pinnedForSaving = true;
			}

			if (frames.length < 2) {
				this.finishSaving();
				RecordableMod.sendClientMessage(ChatCategory.REPLAY_BUFFER, client, "§eNot enough frames in replay buffer.", true);
			} else {
				RecordableMod.sendClientMessage(
					ChatCategory.REPLAY_BUFFER, client, "§eSaving replay buffer (" + frames.length + " frames, " + diskBytes / 1048576L + " MB on disk)...", true
				);
				Thread saveThread = new Thread(() -> {
					try {
						this.saveFramesToFile(client, frames);
					} catch (Throwable var7) {
						RecordableMod.LOGGER.warn("Failed to save replay buffer.", var7);
						RecordableMod.sendClientMessage(ChatCategory.REPLAY_BUFFER, client, "§cFailed to save replay buffer: " + var7.getMessage(), false);
					} finally {
						this.finishSaving();
					}
				}, "Record-able Replay Save");
				saveThread.setDaemon(true);
				saveThread.start();
			}
		}
	}

	private void finishSaving() {
		synchronized (this.lock) {
			this.saving.set(false);
			this.pinnedForSaving = false;

			while (!this.pendingChunkDeletions.isEmpty()) {
				Integer id = (Integer)this.pendingChunkDeletions.pollFirst();
				ReplayBuffer.Chunk chunk = id != null ? (ReplayBuffer.Chunk)this.chunks.get(id) : null;
				if (chunk != null && chunk.refCount <= 0 && chunk != this.currentChunk) {
					this.removeChunkLocked(chunk);
				}
			}

			if (!this.active.get()) {
				this.resetStorageLocked();
			}
		}
	}

	private void saveFramesToFile(class_310 client, ReplayBuffer.FrameRef[] frames) throws Exception {
		RecordableConfig config = RecordableConfig.get();
		Path outputDir = config.getOutputDirectory();
		Files.createDirectories(outputDir);
		String timestamp = LocalDateTime.now().format(FILE_TIMESTAMP);
		String ext = config.getFormat();
		Path outputFile = outputDir.resolve("replay-" + timestamp + "." + ext);
		FfmpegStatus ffStatus = FFmpegEncoder.detectFfmpeg();
		if (!ffStatus.found()) {
			RecordableMod.sendClientMessage(ChatCategory.REPLAY_BUFFER, client, "§cFFmpeg not found. Cannot save replay.", false);
		} else {
			int outWidth = frames[0].width;
			int outHeight = frames[0].height;
			long durationMs = frames[frames.length - 1].timestampMs - frames[0].timestampMs;
			double effectiveFps = durationMs > 0L ? (double)frames.length * 1000.0 / (double)durationMs : (double)this.targetFps;
			int fps = Math.max(1, Math.min(120, (int)Math.round(effectiveFps)));
			List<String> cmd = new ArrayList(
				List.of(
					ffStatus.executable(),
					"-y",
					"-f",
					"rawvideo",
					"-pixel_format",
					"rgb24",
					"-video_size",
					outWidth + "x" + outHeight,
					"-framerate",
					String.valueOf(fps),
					"-i",
					"pipe:0"
				)
			);
			String smoothFilter = SmoothMotion.buildFilter(config, fps);
			if (smoothFilter != null) {
				cmd.add("-vf");
				cmd.add(smoothFilter);
			}

			cmd.add("-c:v");
			cmd.add("libx264");
			cmd.add("-preset");
			cmd.add("fast");
			cmd.add("-crf");
			cmd.add("23");
			cmd.add("-pix_fmt");
			cmd.add("yuv420p");
			cmd.add(outputFile.toString());
			ProcessBuilder pb = new ProcessBuilder(cmd);
			pb.redirectErrorStream(true);
			Process process = pb.start();
			Map<Integer, RandomAccessFile> openChunks = new HashMap();

			try {
				OutputStream stdin = process.getOutputStream();

				try {
					byte[] frameBuf = null;

					for (ReplayBuffer.FrameRef frame : frames) {
						RandomAccessFile raf = (RandomAccessFile)openChunks.get(frame.chunkId);
						if (raf == null) {
							Path chunkPath;
							synchronized (this.lock) {
								ReplayBuffer.Chunk chunk = (ReplayBuffer.Chunk)this.chunks.get(frame.chunkId);
								chunkPath = chunk != null ? chunk.path : null;
							}

							if (chunkPath == null || !Files.exists(chunkPath, new LinkOption[0])) {
								continue;
							}

							raf = new RandomAccessFile(chunkPath.toFile(), "r");
							openChunks.put(frame.chunkId, raf);
						}

						if (frameBuf == null || frameBuf.length != frame.length) {
							frameBuf = new byte[frame.length];
						}

						raf.seek(frame.offset);
						raf.readFully(frameBuf);
						stdin.write(frameBuf);
					}

					stdin.flush();
				} catch (Throwable var45) {
					if (stdin != null) {
						try {
							stdin.close();
						} catch (Throwable var43) {
							var45.addSuppressed(var43);
						}
					}

					throw var45;
				}

				if (stdin != null) {
					stdin.close();
				}
			} finally {
				for (RandomAccessFile rafx : openChunks.values()) {
					try {
						rafx.close();
					} catch (Exception var42) {
					}
				}
			}

			int exitCode = process.waitFor();
			if (exitCode == 0 && Files.exists(outputFile, new LinkOption[0])) {
				long fileSize = Files.size(outputFile);
				String msg = "§a✓ Replay saved: "
					+ outputFile.getFileName()
					+ " ("
					+ RecordingManager.formatBytes(fileSize)
					+ ", "
					+ String.format("%.1fs", (double)durationMs / 1000.0)
					+ ")";
				RecordableMod.sendClientMessage(ChatCategory.REPLAY_BUFFER, client, msg, false);
				RecordableMod.LOGGER
					.info(
						"Replay buffer saved: {} ({} frames, {} at {}x{})", new Object[]{outputFile, frames.length, RecordingManager.formatBytes(fileSize), outWidth, outHeight}
					);
			} else {
				RecordableMod.sendClientMessage(ChatCategory.REPLAY_BUFFER, client, "§cReplay save failed (exit code " + exitCode + ").", false);
			}
		}
	}

	public boolean isActive() {
		return this.active.get();
	}

	public boolean isSaving() {
		return this.saving.get();
	}

	public int getBufferedFrameCount() {
		synchronized (this.lock) {
			return this.index.size();
		}
	}

	public long getCurrentMemoryMB() {
		return this.currentDiskBytes.get() / 1048576L;
	}

	public long getMemoryBudgetMB() {
		return this.diskBudgetBytes / 1048576L;
	}

	public long getCurrentDiskMB() {
		return this.currentDiskBytes.get() / 1048576L;
	}

	public long getDiskBudgetMB() {
		return this.diskBudgetBytes / 1048576L;
	}

	public int getMaxFrameCount() {
		return this.maxFrameCount;
	}

	public int getDownscaleFactor() {
		return this.downscaleFactor;
	}

	public int getBufferedSeconds() {
		synchronized (this.lock) {
			if (this.index.isEmpty()) {
				return 0;
			} else {
				ReplayBuffer.FrameRef first = (ReplayBuffer.FrameRef)this.index.peekFirst();
				ReplayBuffer.FrameRef last = (ReplayBuffer.FrameRef)this.index.peekLast();
				return first != null && last != null ? (int)((last.timestampMs - first.timestampMs) / 1000L) : 0;
			}
		}
	}

	public static String checkMemorySafety(int width, int height, int fps, int durationSeconds) {
		int downscale = PlatformUtils.getReplayBufferDownscaleFactor();
		int sw = width / downscale;
		int sh = height / downscale;
		long perFrame = (long)sw * (long)sh * 3L;
		long totalFrames = (long)fps * (long)durationSeconds;
		long totalNeeded = perFrame * totalFrames;
		long budgetBytes = PlatformUtils.getReplayBufferDiskBudgetMB() * 1024L * 1024L;
		long needed = Math.min(totalNeeded, budgetBytes);
		long freeBytes = PlatformUtils.getFreeDiskSpaceBytes(RecordableConfig.get().getOutputDirectory());
		if (freeBytes < 0L) {
			return null;
		} else {
			long minHeadroom = 268435456L;
			if (freeBytes < needed + minHeadroom) {
				long freeMB = freeBytes / 1048576L;
				long neededMB = needed / 1048576L;
				return String.format("§cLow disk space! Only %d MB free, need ~%d MB for buffer + %d MB headroom.", freeMB, neededMB, minHeadroom / 1048576L);
			} else {
				return null;
			}
		}
	}

	public static String getMemoryEstimate(int width, int height, int fps, int durationSeconds) {
		int downscale = PlatformUtils.getReplayBufferDownscaleFactor();
		int sw = width / downscale;
		int sh = height / downscale;
		long perFrame = (long)sw * (long)sh * 3L;
		long totalFrames = (long)fps * (long)durationSeconds;
		long totalMB = perFrame * totalFrames / 1048576L;
		long budgetMB = PlatformUtils.getReplayBufferDiskBudgetMB();
		long effectiveMB = Math.min(totalMB, budgetMB);
		String suffix = downscale > 1 ? " (mobile: " + sw + "x" + sh + ")" : "";
		return String.format("~%d MB disk / %d MB budget%s", effectiveMB, budgetMB, suffix);
	}

	private static void deleteDirectoryContents(Path dir) {
		if (dir != null && Files.exists(dir, new LinkOption[0])) {
			try {
				Stream<Path> walk = Files.walk(dir);

				try {
					walk.sorted(Comparator.reverseOrder()).forEach(p -> {
						try {
							Files.deleteIfExists(p);
						} catch (Exception var2) {
						}
					});
				} catch (Throwable var5) {
					if (walk != null) {
						try {
							walk.close();
						} catch (Throwable var4) {
							var5.addSuppressed(var4);
						}
					}

					throw var5;
				}

				if (walk != null) {
					walk.close();
				}
			} catch (Exception var6) {
			}
		}
	}

	private static final class Chunk {
		final int id;
		final Path path;
		BufferedOutputStream out;
		long bytesWritten;
		int refCount;

		Chunk(int id, Path path) {
			this.id = id;
			this.path = path;
		}
	}

	private static record FrameRef(int chunkId, long offset, int length, int width, int height, long timestampMs) {
	}
}
