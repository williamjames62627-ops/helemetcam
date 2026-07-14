package dev.recordable;

import dev.recordable.FFmpegEncoder.FfmpegStatus;
import dev.recordable.ScreenCapture.CapturedFrame;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class KillClipBuffer {
	private static final KillClipBuffer INSTANCE = new KillClipBuffer();
	private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final int MAX_CAPTURE_HEIGHT = 1080;
	private static final int MONTAGE_FPS_CAP = 60;
	private final Deque<KillClipBuffer.TimedFrame> frames = new ArrayDeque();
	private final AtomicBoolean saving = new AtomicBoolean(false);
	private ScreenCapture capture;
	private int capWidth;
	private int capHeight;
	private int fpsCap = 60;
	private long frameIntervalMs = 16L;
	private long lastFrameMs = 0L;
	private long preMs = 1000L;
	private long postMs = 1000L;
	private long memoryBudgetBytes = 536870912L;
	private long currentBytes = 0L;
	private volatile boolean armed = false;
	private volatile boolean finishing = false;
	private long armIdleDeadlineMs = 0L;
	private long killTimeMs = 0L;
	private long postDeadlineMs = 0L;
	private String pendingPrefix = "on-kill";
	private String pendingReason = "Kill";
	private Object pendingClient = null;
	private volatile boolean audioEnabled = false;

	public static KillClipBuffer getInstance() {
		return INSTANCE;
	}

	private KillClipBuffer() {
	}

	public synchronized void arm(Object client, int width, int height, int fps, int preSeconds, int postSeconds) {
		if (!this.finishing) {
			int w = Math.max(2, width);
			int h = Math.max(2, height);
			if (h > 1080) {
				double scale = 1080.0 / (double)h;
				w = Math.max(2, (int)Math.round((double)w * scale));
				h = 1080;
			}

			w = w / 2 * 2;
			h = h / 2 * 2;
			this.preMs = Math.max(0L, (long)preSeconds * 1000L);
			this.postMs = Math.max(0L, (long)postSeconds * 1000L);
			this.fpsCap = Math.max(10, Math.min(fps > 0 ? fps : 60, 60));
			this.frameIntervalMs = 1000L / (long)this.fpsCap;
			this.memoryBudgetBytes = Math.min(PlatformUtils.getReplayBufferMemoryBudgetMB(), 768L) * 1024L * 1024L;
			this.pendingClient = client;
			if (this.capture == null || w != this.capWidth || h != this.capHeight) {
				this.closeCapture();
				this.capture = new ScreenCapture(w, h);
				this.capWidth = w;
				this.capHeight = h;
			}

			this.armIdleDeadlineMs = System.currentTimeMillis() + this.preMs + 12000L;
			this.armed = true;
			this.audioEnabled = RecordableConfig.get().autoClipAudio;
			if (this.audioEnabled) {
				try {
					long retentionMs = this.preMs + this.postMs + 3000L;
					OpenALLoopbackCapture.getInstance().enableRollingBuffer(retentionMs);
				} catch (Throwable var11) {
					RecordableMod.LOGGER.debug("Kill-montage audio buffer enable failed.", var11);
					this.audioEnabled = false;
				}
			}
		}
	}

	public synchronized void onRenderFrame() {
		if (this.armed || this.finishing) {
			long now = System.currentTimeMillis();
			if (this.armed && !this.finishing && now > this.armIdleDeadlineMs) {
				this.reset();
			} else {
				boolean dueForFrame = this.frameIntervalMs <= 0L || this.lastFrameMs == 0L || now - this.lastFrameMs >= this.frameIntervalMs - 1L;
				if (dueForFrame && this.capture != null) {
					try {
						CapturedFrame f = this.capture.captureFrame();
						if (f != null && f.rgbPixels() != null) {
							this.frames.addLast(new KillClipBuffer.TimedFrame(f.rgbPixels(), f.width(), f.height(), now));
							this.currentBytes = this.currentBytes + (long)f.rgbPixels().length;
							this.lastFrameMs = now;
							this.trim(now);
						}
					} catch (Throwable var5) {
						RecordableMod.LOGGER.debug("Kill-clip capture frame failed.", var5);
					}
				}

				if (this.finishing && now >= this.postDeadlineMs) {
					this.finalizeClip();
				}
			}
		}
	}

	public synchronized void triggerKill(Object client, String reason, String filePrefix, int width, int height, int fps, int preSeconds, int postSeconds) {
		if (!this.finishing && !this.saving.get()) {
			if (this.capture == null || !this.armed) {
				this.arm(client, width, height, fps, preSeconds, postSeconds);
			}

			long now = System.currentTimeMillis();
			this.killTimeMs = now;
			this.preMs = Math.max(0L, (long)preSeconds * 1000L);
			this.postMs = Math.max(0L, (long)postSeconds * 1000L);
			this.postDeadlineMs = now + this.postMs;
			this.pendingReason = reason != null ? reason : "Kill";
			this.pendingPrefix = filePrefix != null ? filePrefix : "on-kill";
			if (client != null) {
				this.pendingClient = client;
			}

			this.finishing = true;
			this.armed = true;
			this.trimPreRollToKill();
			if (this.postMs == 0L) {
				this.finalizeClip();
			}
		}
	}

	private void trim(long now) {
		if (!this.finishing) {
			long cutoff = now - this.preMs;

			while (!this.frames.isEmpty()) {
				KillClipBuffer.TimedFrame oldest = (KillClipBuffer.TimedFrame)this.frames.peekFirst();
				if (oldest == null || oldest.tsMs() >= cutoff) {
					break;
				}

				KillClipBuffer.TimedFrame removed = (KillClipBuffer.TimedFrame)this.frames.pollFirst();
				if (removed != null) {
					this.currentBytes = this.currentBytes - removed.bytes();
				}
			}
		}

		while (this.currentBytes > this.memoryBudgetBytes && this.frames.size() > 1) {
			KillClipBuffer.TimedFrame removed = (KillClipBuffer.TimedFrame)this.frames.pollFirst();
			if (removed != null) {
				this.currentBytes = this.currentBytes - removed.bytes();
			}
		}
	}

	private void trimPreRollToKill() {
		long cutoff = this.killTimeMs - this.preMs;

		while (!this.frames.isEmpty()) {
			KillClipBuffer.TimedFrame oldest = (KillClipBuffer.TimedFrame)this.frames.peekFirst();
			if (oldest == null || oldest.tsMs() >= cutoff) {
				break;
			}

			KillClipBuffer.TimedFrame removed = (KillClipBuffer.TimedFrame)this.frames.pollFirst();
			if (removed != null) {
				this.currentBytes = this.currentBytes - removed.bytes();
			}
		}
	}

	private void finalizeClip() {
		if (this.frames.size() < 2) {
			this.reset();
		} else {
			KillClipBuffer.TimedFrame[] snapshot = (KillClipBuffer.TimedFrame[])this.frames.toArray(new KillClipBuffer.TimedFrame[0]);
			String reason = this.pendingReason;
			String prefix = this.pendingPrefix;
			Object client = this.pendingClient;
			byte[] audioSnapshot = null;
			if (this.audioEnabled && snapshot.length >= 2) {
				try {
					long startMs = snapshot[0].tsMs();
					long endMs = snapshot[snapshot.length - 1].tsMs();
					audioSnapshot = OpenALLoopbackCapture.getInstance().extractAudio(startMs, endMs);
				} catch (Throwable var11) {
					RecordableMod.LOGGER.debug("Kill-montage audio extract failed.", var11);
				}
			}

			byte[] audio = audioSnapshot;
			this.reset();
			if (!this.saving.getAndSet(true)) {
				Thread t = new Thread(() -> {
					try {
						this.encode(client, snapshot, prefix, reason, audio);
					} catch (Throwable var10x) {
						RecordableMod.LOGGER.warn("Failed to save kill montage clip.", var10x);
					} finally {
						this.saving.set(false);
					}
				}, "Record-able Kill Montage Save");
				t.setDaemon(true);

				try {
					t.setPriority(1);
				} catch (Throwable var10) {
				}

				t.start();
			}
		}
	}

	private void encode(Object client, KillClipBuffer.TimedFrame[] f, String prefix, String reason, byte[] audioPcm) throws Exception {
		if (f.length >= 2) {
			RecordableConfig config = RecordableConfig.get();
			FfmpegStatus ff = FFmpegEncoder.detectFfmpeg();
			if (!ff.found()) {
				RecordableMod.LOGGER.warn("Kill montage skipped (FFmpeg not found): {}", reason);
			} else {
				Path outputDir = config.getOutputDirectory();
				Path triggerDir = outputDir.resolve("recording_auto_clips").resolve(prefix.replace("-", "_"));
				Files.createDirectories(triggerDir);
				String ext = config.getFormat();
				Path outputFile = triggerDir.resolve("recordable-" + FILE_TIMESTAMP.format(LocalDateTime.now()) + "." + ext);
				int outW = f[0].width();
				int outH = f[0].height();
				long durationMs = f[f.length - 1].tsMs() - f[0].tsMs();
				double effFps = durationMs > 0L ? (double)f.length * 1000.0 / (double)durationMs : (double)this.fpsCap;
				int fps = Math.max(1, Math.min(120, (int)Math.round(effFps)));
				Path audioFile = null;
				boolean hasAudio = audioPcm != null && audioPcm.length > 0;
				if (hasAudio) {
					try {
						audioFile = Files.createTempFile("recordable-montage-", ".pcm");
						Files.write(audioFile, audioPcm, new OpenOption[0]);
					} catch (Throwable var43) {
						RecordableMod.LOGGER.debug("Kill-montage audio temp write failed; encoding video-only.", var43);
						hasAudio = false;
						audioFile = null;
					}
				}

				try {
					List<String> cmd = new ArrayList();
					cmd.add(ff.executable());
					cmd.add("-y");
					cmd.add("-hide_banner");
					cmd.add("-loglevel");
					cmd.add("error");
					cmd.add("-nostats");
					cmd.add("-f");
					cmd.add("rawvideo");
					cmd.add("-pixel_format");
					cmd.add("rgb24");
					cmd.add("-video_size");
					cmd.add(outW + "x" + outH);
					cmd.add("-framerate");
					cmd.add(String.valueOf(fps));
					cmd.add("-i");
					cmd.add("pipe:0");
					if (hasAudio) {
						cmd.add("-f");
						cmd.add("s16le");
						cmd.add("-ar");
						cmd.add(String.valueOf(48000));
						cmd.add("-ac");
						cmd.add(String.valueOf(2));
						cmd.add("-i");
						cmd.add(audioFile.toString());
					}

					cmd.add("-c:v");
					cmd.add("libx264");
					cmd.add("-preset");
					cmd.add("ultrafast");
					cmd.add("-threads");
					cmd.add("2");
					cmd.add("-crf");
					cmd.add("23");
					cmd.add("-pix_fmt");
					cmd.add("yuv420p");
					if (hasAudio) {
						cmd.add("-map");
						cmd.add("0:v:0");
						cmd.add("-map");
						cmd.add("1:a:0");
						cmd.add("-c:a");
						cmd.add("aac");
						cmd.add("-b:a");
						cmd.add("160k");
						cmd.add("-shortest");
					}

					cmd.add(outputFile.toString());
					ProcessBuilder pb = new ProcessBuilder(cmd);
					pb.redirectErrorStream(true);
					Process process = pb.start();
					StringBuilder ffmpegLog = new StringBuilder();
					Thread drain = new Thread(() -> {
						try {
							BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream()));

							String line;
							try {
								while ((line = r.readLine()) != null) {
									if (ffmpegLog.length() < 8000) {
										ffmpegLog.append(line).append('\n');
									}
								}
							} catch (Throwable var6x) {
								try {
									r.close();
								} catch (Throwable var5) {
									var6x.addSuppressed(var5);
								}

								throw var6x;
							}

							r.close();
						} catch (Exception var7x) {
						}
					}, "Record-able Kill Montage FFmpeg Log");
					drain.setDaemon(true);
					drain.start();
					OutputStream stdin = process.getOutputStream();

					try {
						for (KillClipBuffer.TimedFrame tf : f) {
							stdin.write(tf.rgb());
						}

						stdin.flush();
					} catch (Throwable var44) {
						if (stdin != null) {
							try {
								stdin.close();
							} catch (Throwable var41) {
								var44.addSuppressed(var41);
							}
						}

						throw var44;
					}

					if (stdin != null) {
						stdin.close();
					}

					int exitCode = process.waitFor();

					try {
						drain.join(2000L);
					} catch (InterruptedException var42) {
						Thread.currentThread().interrupt();
					}

					if (exitCode == 0 && Files.exists(outputFile, new LinkOption[0])) {
						long size = Files.size(outputFile);
						RecordableMod.LOGGER
							.info(
								"Kill montage saved: {} ({} frames, {}, {}s @ {} FPS, audio: {})",
								new Object[]{
									outputFile, f.length, RecordingManager.formatBytes(size), String.format("%.1f", (double)durationMs / 1000.0), fps, hasAudio ? "yes" : "no"
								}
							);
						RecordableMod.sendClientMessage(
							ChatCategory.CLIPS,
							client,
							"§a\ud83c\udfac Kill montage saved: §f" + reason + " §7(" + String.format("%.1fs", (double)durationMs / 1000.0) + (hasAudio ? ", audio" : "") + ")",
							false
						);
					} else {
						RecordableMod.LOGGER
							.warn("Kill montage encode failed (exit code {}): {}\nFFmpeg output:\n{}", new Object[]{exitCode, outputFile, ffmpegLog.toString().trim()});
					}
				} finally {
					if (audioFile != null) {
						try {
							Files.deleteIfExists(audioFile);
						} catch (Throwable var40) {
						}
					}
				}
			}
		}
	}

	private void reset() {
		this.armed = false;
		this.finishing = false;
		this.frames.clear();
		this.currentBytes = 0L;
		this.lastFrameMs = 0L;
		this.pendingClient = null;
		if (this.audioEnabled) {
			try {
				OpenALLoopbackCapture.getInstance().disableRollingBuffer();
			} catch (Throwable var2) {
			}
		}

		this.audioEnabled = false;
	}

	private void closeCapture() {
		if (this.capture != null) {
			try {
				this.capture.close();
			} catch (Throwable var2) {
			}

			this.capture = null;
		}
	}

	public boolean isActive() {
		return this.armed || this.finishing;
	}

	private static record TimedFrame(byte[] rgb, int width, int height, long tsMs) {
		long bytes() {
			return (long)this.rgb.length;
		}
	}
}
