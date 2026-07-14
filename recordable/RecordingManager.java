package dev.recordable;

import dev.recordable.CaptureDiagnostics.LiveStats;
import dev.recordable.CaptureDiagnostics.SelfTestResult;
import dev.recordable.FFmpegEncoder.EnqueueResult;
import dev.recordable.FFmpegEncoder.FfmpegStatus;
import dev.recordable.RecordableConfig.CaptureDimensions;
import dev.recordable.ScreenCapture.CapturedFrame;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.class_310;

public final class RecordingManager {
	private static final RecordingManager INSTANCE = new RecordingManager();
	private static final int MAX_QUEUE_SIZE = 240;
	private static final long BACKLOG_LOG_INTERVAL_NANOS = 2000000000L;
	private static final long PERFORMANCE_SAMPLE_INTERVAL_NANOS = 1000000000L;
	private final Object lock = new Object();
	private final AtomicLong capturedFrames = new AtomicLong();
	private final AtomicLong skippedFrames = new AtomicLong();
	private final AtomicLong failedCaptures = new AtomicLong();
	private final AtomicLong adaptiveDroppedFrames = new AtomicLong();
	private final AtomicLong finalEncoderDroppedFrames = new AtomicLong();
	private volatile RecordingManager.State state = RecordingManager.State.IDLE;
	private ScreenCapture screenCapture;
	private FFmpegEncoder ffmpegEncoder;
	private FFmpegEncoder pendingStartFfmpegEncoder;
	private Path currentOutputFile;
	private Path lastOutputFile;
	private long startedAtNanos;
	private long lastFrameCaptureNanos;
	private long frameIntervalNanos;
	private int recordingFps = 60;
	private int recordingWidth;
	private int recordingHeight;
	private volatile String pendingJoinNotification;
	private long lastBacklogLogAtNanos;
	private long lastPerformanceSampleAtNanos;
	private long lastCapturedSample;
	private long lastEncoderWrittenSample;
	private volatile double captureFpsEstimate;
	private volatile double encoderFpsEstimate;
	private volatile boolean performanceSuggestionSent;
	private volatile int onFrameNullEncoderLogCount;
	private long baseFrameIntervalNanos;
	private long lastGovernorCheckNanos;
	private int governorCurrentFps;
	private volatile boolean selfTestRequested;
	private volatile SelfTestResult selfTestResult;
	private long totalPausedNanos;
	private long pauseStartNanos;
	private final List<RecordingBookmark> bookmarks = Collections.synchronizedList(new ArrayList());
	private int bookmarkCounter;
	private volatile String pendingToastMessage;
	private volatile Path pendingToastFilePath;
	private volatile long pendingToastExpiresAtMs;

	public long getEffectiveRecordingMillis() {
		if (this.isActiveOrStopping() && this.startedAtNanos != 0L) {
			long now = System.nanoTime();
			long totalElapsed = now - this.startedAtNanos;
			long paused = this.totalPausedNanos;
			if (this.state == RecordingManager.State.PAUSED && this.pauseStartNanos > 0L) {
				paused += now - this.pauseStartNanos;
			}

			return Math.max(0L, (totalElapsed - paused) / 1000000L);
		} else {
			return 0L;
		}
	}

	private RecordingManager() {
	}

	public static RecordingManager getInstance() {
		return INSTANCE;
	}

	public void initialize() {
		FFmpegEncoder.detectFfmpeg();
	}

	public static boolean isInGameState(class_310 client) {
		return client == null ? false : client.field_1687 != null && client.field_1724 != null;
	}

	public void toggleRecording(class_310 client) {
		RecordingManager.State snapshot = this.state;
		if (snapshot == RecordingManager.State.IDLE) {
			class_310 mc = resolveClient(client);
			if (!isInGameState(mc)) {
				return;
			}

			this.startRecording(client);
		} else if (snapshot == RecordingManager.State.RECORDING || snapshot == RecordingManager.State.PAUSED) {
			this.stopRecording(client);
		} else if (snapshot == RecordingManager.State.STARTING) {
			RecordableMod.sendClientMessage(ChatCategory.RECORDING, client, "Record-able is still initializing...", true);
		} else {
			RecordableMod.sendClientMessage(ChatCategory.RECORDING, client, "Record-able is already stopping...", true);
		}
	}

	public void startRecording(class_310 client) {
		this.startRecording(client, null);
	}

	public void startRecording(class_310 client, String filePrefix) {
		class_310 activeClient = resolveClient(client);
		if (activeClient != null && !activeClient.method_18854()) {
			executeOnClientThread(activeClient, () -> this.startRecording(activeClient, filePrefix), "start recording");
		} else if (isInGameState(activeClient)) {
			RecordableConfig config;
			int targetWidth;
			int targetHeight;
			int targetFps;
			synchronized (this.lock) {
				if (this.state != RecordingManager.State.IDLE) {
					RecordableMod.sendClientMessage(ChatCategory.RECORDING, client, "Record-able is already active.", true);
					return;
				}

				if (!isInGameState(activeClient)) {
					return;
				}

				config = RecordableConfig.get();
				config.sanitize();
				if (!config.enabled) {
					RecordableMod.sendClientMessage(ChatCategory.RECORDING, client, "Record-able is disabled in config.", false);
					return;
				}

				FfmpegStatus ffStatus = FFmpegEncoder.detectFfmpeg();
				if (!ffStatus.found()) {
					String hint = PlatformUtils.getFfmpegInstallHint();
					RecordableMod.sendClientMessage(ChatCategory.RECORDING, client, "§cFFmpeg is required but not found. " + hint, false);
					RecordableMod.sendClientMessage(ChatCategory.RECORDING, client, "§eOpen Record-able settings and click 'Download FFmpeg' for an automatic install.", false);
					return;
				}

				if (activeClient == null || activeClient.method_22683() == null) {
					RecordableMod.sendClientMessage(ChatCategory.RECORDING, client, "Record-able could not access the Minecraft window.", false);
					return;
				}

				int nativeWidth = activeClient.method_22683().method_4489();
				int nativeHeight = activeClient.method_22683().method_4506();
				if (nativeWidth <= 0 || nativeHeight <= 0) {
					RecordableMod.sendClientMessage(ChatCategory.RECORDING, client, "Record-able could not determine the framebuffer size.", false);
					return;
				}

				CaptureDimensions dimensions = config.resolveCaptureDimensions(nativeWidth, nativeHeight);
				targetWidth = makeEven(Math.max(2, dimensions.width()));
				targetHeight = makeEven(Math.max(2, dimensions.height()));
				targetFps = config.getFps();
				if (targetWidth != dimensions.width() || targetHeight != dimensions.height()) {
					RecordableMod.LOGGER
						.info(
							"Adjusted recording resolution to even dimensions: {}x{} -> {}x{}", new Object[]{dimensions.width(), dimensions.height(), targetWidth, targetHeight}
						);
				}

				label71: {
					try {
						Path outputDir = config.getOutputDirectory();
						Files.createDirectories(outputDir);
						long freeBytes = Files.getFileStore(outputDir).getUsableSpace();
						long freeMB = freeBytes / 1048576L;
						if (freeMB < (long)config.diskSpaceMinFreeMB) {
							RecordableMod.sendClientMessage(
								ChatCategory.RECORDING,
								client,
								"§c⚠ Low disk space! Only " + freeMB + " MB free (minimum: " + config.diskSpaceMinFreeMB + " MB). Free up space or change the output directory.",
								false
							);
						}

						long freePercent = 100L - freeBytes * 100L / Math.max(1L, Files.getFileStore(outputDir).getTotalSpace());
						if (freePercent < (long)config.diskSpaceBlockPercent) {
							break label71;
						}

						RecordableMod.sendClientMessage(
							ChatCategory.RECORDING,
							client,
							"§c⛔ Disk usage is " + freePercent + "% (block threshold: " + config.diskSpaceBlockPercent + "%). Recording blocked. Free up space first.",
							false
						);
					} catch (Exception var21) {
						RecordableMod.LOGGER.warn("Could not check disk space before recording: {}", var21.getMessage());
						break label71;
					}

					return;
				}

				this.pendingStartFfmpegEncoder = new FFmpegEncoder(config, targetWidth, targetHeight, targetFps, 240, filePrefix);
				this.state = RecordingManager.State.STARTING;
			}

			RecordableMod.LOGGER.info("Starting recording with FFmpeg encoder ({}x{} @ {} FPS).", new Object[]{targetWidth, targetHeight, targetFps});
			RecordableMod.sendClientMessage(ChatCategory.RECORDING, activeClient, "Record-able initializing (FFmpeg)...", true);
			CompletableFuture.runAsync(
				() -> {
					Path output = null;
					Throwable startFailure = null;

					try {
						class_310 clientSnapshot = resolveClient(activeClient);
						if (!isInGameState(clientSnapshot)) {
							synchronized (this.lock) {
								if (this.state == RecordingManager.State.STARTING) {
									this.state = RecordingManager.State.IDLE;
									this.pendingStartFfmpegEncoder = null;
								}

								return;
							}
						}

						output = this.pendingStartFfmpegEncoder.start();
					} catch (Throwable var12x) {
						startFailure = var12x;
					}

					Path finalizedOutput = output;
					Throwable finalizedFailure = startFailure;
					executeOnClientThread(
						activeClient,
						() -> this.completeStartOnClientThread(activeClient, config, targetWidth, targetHeight, targetFps, finalizedOutput, finalizedFailure),
						"complete recording start"
					);
				}
			);
		}
	}

	private void completeStartOnClientThread(
		class_310 client, RecordableConfig config, int targetWidth, int targetHeight, int targetFps, Path output, Throwable startFailure
	) {
		boolean cancelled;
		synchronized (this.lock) {
			cancelled = this.state != RecordingManager.State.STARTING;
		}

		if (cancelled) {
			RecordableMod.LOGGER.info("Background start completed after cancellation; stopping encoder instance.");
			this.stopPendingEncoder();
		} else if (startFailure == null && output != null) {
			ScreenCapture newCapture = null;

			try {
				newCapture = new ScreenCapture(targetWidth, targetHeight);
				synchronized (this.lock) {
					if (this.state != RecordingManager.State.STARTING) {
						throw new IOException("Recording start was cancelled before capture initialization completed.");
					}

					this.ffmpegEncoder = this.pendingStartFfmpegEncoder;
					RecordableMod.LOGGER.info("Assigned active FFmpeg encoder: {}", this.ffmpegEncoder != null ? "OK" : "NULL!");
					this.pendingStartFfmpegEncoder = null;
					this.screenCapture = newCapture;
					this.currentOutputFile = output;
					this.lastOutputFile = output;
					this.recordingWidth = targetWidth;
					this.recordingHeight = targetHeight;
					this.recordingFps = targetFps;
					this.capturedFrames.set(0L);
					this.skippedFrames.set(0L);
					this.failedCaptures.set(0L);
					this.adaptiveDroppedFrames.set(0L);
					this.finalEncoderDroppedFrames.set(0L);
					this.startedAtNanos = System.nanoTime();
					this.lastFrameCaptureNanos = 0L;
					this.totalPausedNanos = 0L;
					this.pauseStartNanos = 0L;
					this.frameIntervalNanos = Math.max(1L, 1000000000L / (long)Math.max(1, this.recordingFps));
					this.baseFrameIntervalNanos = this.frameIntervalNanos;
					this.governorCurrentFps = this.recordingFps;
					this.lastGovernorCheckNanos = this.startedAtNanos;
					this.lastBacklogLogAtNanos = 0L;
					this.lastPerformanceSampleAtNanos = this.startedAtNanos;
					this.lastCapturedSample = 0L;
					this.lastEncoderWrittenSample = 0L;
					this.captureFpsEstimate = 0.0;
					this.encoderFpsEstimate = 0.0;
					this.performanceSuggestionSent = false;
					this.onFrameNullEncoderLogCount = 0;
					FrameBufferPool.getInstance().setEnabled(config.frameBufferPoolingEnabled);
					this.state = RecordingManager.State.RECORDING;
					this.bookmarks.clear();
					this.bookmarkCounter = 0;
				}

				if (config.replayBufferEnabled) {
					String diskSafetyWarning = ReplayBuffer.checkMemorySafety(this.recordingWidth, this.recordingHeight, this.recordingFps, config.replayBufferDurationSeconds);
					if (diskSafetyWarning != null) {
						RecordableMod.LOGGER.warn("Replay buffer disabled due to low disk space: {}", diskSafetyWarning);
						RecordableMod.sendClientMessage(ChatCategory.RECORDING, client, "§eReplay buffer skipped: insufficient disk space. " + diskSafetyWarning, true);
					} else {
						ReplayBuffer.getInstance().start(this.recordingWidth, this.recordingHeight, this.recordingFps, config.replayBufferDurationSeconds);
					}
				}

				boolean audioActive = this.ffmpegEncoder != null && this.ffmpegEncoder.isAudioEnabled();
				String audioDevStr = this.ffmpegEncoder != null ? this.ffmpegEncoder.getAudioDeviceInfo() : "";
				String audioInfo;
				if (audioActive) {
					audioInfo = " + audio [" + audioDevStr + "]";
				} else if (config.captureAudio) {
					audioInfo = " (audio unavailable - recording video only)";
					RecordableMod.sendClientMessage(ChatCategory.RECORDING, client, "§eAudio unavailable. Recording video only.", true);
				} else {
					audioInfo = "";
				}

				RecordableMod.sendClientMessage(
					ChatCategory.RECORDING,
					client,
					"Record-able recording started: "
						+ output.getFileName()
						+ " ("
						+ this.recordingWidth
						+ "x"
						+ this.recordingHeight
						+ " @ "
						+ this.recordingFps
						+ " FPS"
						+ audioInfo
						+ ")",
					false
				);
				RecordableMod.LOGGER
					.info(
						"Recording started: file={} resolution={}x{} fps={} audioEnabled={} audioDevice={}",
						new Object[]{output, this.recordingWidth, this.recordingHeight, this.recordingFps, audioActive, audioDevStr}
					);
			} catch (Throwable var19) {
				synchronized (this.lock) {
					this.state = RecordingManager.State.IDLE;
					this.pendingStartFfmpegEncoder = null;
					this.ffmpegEncoder = null;
					this.screenCapture = null;
				}

				if (newCapture != null) {
					try {
						newCapture.close();
					} catch (Throwable var14) {
						RecordableMod.LOGGER.debug("Failed to close capture after start failure.", var14);
					}
				}

				this.stopPendingEncoder();
				RecordableMod.LOGGER.warn("Failed to finalize Record-able start on client thread.", var19);
				RecordableMod.sendClientMessage(ChatCategory.RECORDING, client, "Record-able could not start: " + var19.getMessage(), false);
			}
		} else {
			synchronized (this.lock) {
				this.state = RecordingManager.State.IDLE;
				this.pendingStartFfmpegEncoder = null;
			}

			this.stopPendingEncoder();
			String reason = startFailure == null ? "Unknown startup failure" : startFailure.getMessage();
			RecordableMod.LOGGER
				.warn("Failed to start Record-able recording (resolution={}x{}, fps={}).", new Object[]{targetWidth, targetHeight, targetFps, startFailure});
			RecordableMod.sendClientMessage(ChatCategory.RECORDING, client, "Record-able could not start: " + reason, false);
		}
	}

	private void stopPendingEncoder() {
		try {
			if (this.pendingStartFfmpegEncoder != null) {
				this.pendingStartFfmpegEncoder.stop();
			}
		} catch (Throwable var2) {
			RecordableMod.LOGGER.debug("Encoder cleanup after failed/cancelled start.", var2);
		}
	}

	public void pauseRecording(class_310 client) {
		class_310 activeClient = resolveClient(client);
		if (activeClient != null && !activeClient.method_18854()) {
			executeOnClientThread(activeClient, () -> this.pauseRecording(activeClient), "pause recording");
		} else {
			synchronized (this.lock) {
				if (this.state != RecordingManager.State.RECORDING) {
					return;
				}

				this.state = RecordingManager.State.PAUSED;
				this.pauseStartNanos = System.nanoTime();
				this.lastFrameCaptureNanos = 0L;
			}

			RecordableMod.LOGGER.info("Recording paused. Effective duration so far: {}", formatDuration(this.getEffectiveRecordingMillis()));
			RecordableMod.sendClientMessage(ChatCategory.RECORDING, activeClient, "§e⏸ Record-able paused.", true);
		}
	}

	public void resumeRecording(class_310 client) {
		class_310 activeClient = resolveClient(client);
		if (activeClient != null && !activeClient.method_18854()) {
			executeOnClientThread(activeClient, () -> this.resumeRecording(activeClient), "resume recording");
		} else {
			synchronized (this.lock) {
				if (this.state != RecordingManager.State.PAUSED) {
					return;
				}

				if (this.pauseStartNanos > 0L) {
					this.totalPausedNanos = this.totalPausedNanos + (System.nanoTime() - this.pauseStartNanos);
					this.pauseStartNanos = 0L;
				}

				this.state = RecordingManager.State.RECORDING;
				this.lastFrameCaptureNanos = 0L;
			}

			RecordableMod.LOGGER.info("Recording resumed. Total paused time: {}ms", this.totalPausedNanos / 1000000L);
			RecordableMod.sendClientMessage(ChatCategory.RECORDING, activeClient, "§a▶ Record-able resumed.", true);
		}
	}

	public void togglePause(class_310 client) {
		RecordingManager.State snapshot = this.state;
		if (snapshot == RecordingManager.State.RECORDING) {
			this.pauseRecording(client);
		} else if (snapshot == RecordingManager.State.PAUSED) {
			this.resumeRecording(client);
		}
	}

	public void stopRecording(class_310 client) {
		this.stopRecording(client, RecordingManager.StopReason.MANUAL);
	}

	public void stopRecordingForDisconnect(class_310 client) {
		this.stopRecording(client, RecordingManager.StopReason.DISCONNECT);
	}

	public void stopRecording(class_310 client, RecordingManager.StopReason stopReason) {
		RecordingManager.StopReason reason = stopReason == null ? RecordingManager.StopReason.MANUAL : stopReason;
		class_310 activeClient = resolveClient(client);
		if (reason != RecordingManager.StopReason.DISCONNECT
			&& reason != RecordingManager.StopReason.SHUTDOWN
			&& activeClient != null
			&& !activeClient.method_18854()) {
			executeOnClientThread(activeClient, () -> this.stopRecording(activeClient, reason), "stop recording");
		} else {
			FFmpegEncoder ffmpegToStop = null;
			boolean cancelledDuringStart = false;
			ScreenCapture captureToClose;
			Path output;
			synchronized (this.lock) {
				if (this.state == RecordingManager.State.IDLE) {
					if (reason != RecordingManager.StopReason.DISCONNECT && reason != RecordingManager.StopReason.SHUTDOWN) {
						RecordableMod.sendClientMessage(ChatCategory.RECORDING, activeClient, "Record-able is not recording.", true);
					}

					return;
				}

				if (this.state == RecordingManager.State.STOPPING) {
					if (reason != RecordingManager.StopReason.DISCONNECT && reason != RecordingManager.StopReason.SHUTDOWN) {
						RecordableMod.sendClientMessage(ChatCategory.RECORDING, activeClient, "Record-able is already stopping...", true);
					}

					return;
				}

				if (this.state == RecordingManager.State.STARTING) {
					cancelledDuringStart = true;
					this.state = RecordingManager.State.IDLE;
					ffmpegToStop = this.pendingStartFfmpegEncoder;
					this.pendingStartFfmpegEncoder = null;
					captureToClose = null;
					output = this.currentOutputFile;
					this.lastFrameCaptureNanos = 0L;
					this.frameIntervalNanos = 0L;
				} else {
					this.state = RecordingManager.State.STOPPING;
					ffmpegToStop = this.ffmpegEncoder;
					captureToClose = this.screenCapture;
					output = this.currentOutputFile;
					this.ffmpegEncoder = null;
					this.screenCapture = null;
					this.lastFrameCaptureNanos = 0L;
					this.frameIntervalNanos = 0L;
				}
			}

			if (cancelledDuringStart) {
				RecordableMod.LOGGER.info("Recording start cancelled before activation.");
				if (ReplayBuffer.getInstance().isActive()) {
					ReplayBuffer.getInstance().stop();
				}

				FFmpegEncoder ffmpegCancel = ffmpegToStop;
				Thread cancelThread = new Thread(() -> {
					try {
						if (ffmpegCancel != null) {
							ffmpegCancel.stop();
						}
					} catch (Throwable var2) {
						RecordableMod.LOGGER.warn("Failed to stop encoder after start cancellation.", var2);
					}
				}, "Record-able Start Cancel Stopper");
				cancelThread.setDaemon(true);
				cancelThread.start();
				if (reason != RecordingManager.StopReason.DISCONNECT && reason != RecordingManager.StopReason.SHUTDOWN) {
					RecordableMod.sendClientMessage(ChatCategory.RECORDING, activeClient, "Record-able initialization cancelled.", true);
				}
			} else {
				RecordableMod.LOGGER
					.info(
						"Recording stop requested: reason={} output={} capturedFrames={} droppedFrames={} failedCaptures={}",
						new Object[]{reason, output, this.capturedFrames.get(), this.skippedFrames.get(), this.failedCaptures.get()}
					);
				this.closeCaptureSafely(activeClient, captureToClose, reason);
				FFmpegEncoder ffmpegFinal = ffmpegToStop;
				Thread stopper = new Thread(() -> this.finishStop(activeClient, ffmpegFinal, output, reason), "Record-able Stopper");
				stopper.setDaemon(true);
				stopper.start();
				if (reason != RecordingManager.StopReason.DISCONNECT && reason != RecordingManager.StopReason.SHUTDOWN) {
					RecordableMod.sendClientMessage(ChatCategory.RECORDING, activeClient, "Record-able stopping and finalizing video...", true);
				}
			}
		}
	}

	public void onFrame() {
		try {
			KillClipBuffer.getInstance().onRenderFrame();
		} catch (Throwable var19) {
		}

		if (this.selfTestRequested) {
			this.selfTestRequested = false;
			this.selfTestResult = this.runCaptureSelfTest();
		}

		if (this.state == RecordingManager.State.RECORDING) {
			FFmpegEncoder activeFfmpeg;
			ScreenCapture activeCapture;
			synchronized (this.lock) {
				if (this.state != RecordingManager.State.RECORDING || this.screenCapture == null) {
					return;
				}

				if (this.ffmpegEncoder == null) {
					long captured = this.capturedFrames.get();
					if (captured == 0L && this.onFrameNullEncoderLogCount < 3) {
						this.onFrameNullEncoderLogCount++;
						RecordableMod.LOGGER.error("onFrame: encoder is null while RECORDING! state={} screenCapture={}", this.state, this.screenCapture != null);
					}

					return;
				}

				activeFfmpeg = this.ffmpegEncoder;
				activeCapture = this.screenCapture;
			}

			long now = System.nanoTime();
			if (this.shouldCaptureFrame(now)) {
				try {
					CapturedFrame frame = activeCapture.captureFrame();
					if (frame == null) {
						return;
					}

					long nowNs = System.nanoTime();
					long frameTimestampMs = this.startedAtNanos > 0L ? (nowNs - this.startedAtNanos - this.totalPausedNanos) / 1000000L : -1L;
					byte[] frameData = frame.rgbPixels();
					RecordableConfig cfg = RecordableConfig.get();
					if (StreamerModeManager.getInstance().isActive() && cfg != null && cfg.bakeInOverlay) {
						StreamerModeManager.getInstance().applyCensoring(frameData, frame.width(), frame.height());
					}

					boolean queued = activeFfmpeg.writeFrame(frameData, frameTimestampMs) == EnqueueResult.QUEUED;
					if (ReplayBuffer.getInstance().isActive()) {
						ReplayBuffer.getInstance().addFrame(frameData);
					}

					if (queued) {
						long totalCaptured = this.capturedFrames.incrementAndGet();
						long logInterval = Math.max(1L, (long)this.recordingFps * 10L);
						if (totalCaptured % logInterval == 0L) {
							RecordableMod.LOGGER
								.info(
									"Frames captured: {} (encoderWritten={} encoderDropped={} queue={}/{} | adaptiveDropped={})",
									new Object[]{
										totalCaptured,
										activeFfmpeg.getWrittenFrames(),
										activeFfmpeg.getDroppedFrames(),
										activeFfmpeg.getQueueSize(),
										activeFfmpeg.getQueueCapacity(),
										this.adaptiveDroppedFrames.get()
									}
								);
						}
					} else {
						this.skippedFrames.incrementAndGet();
						this.logBacklogIfNeeded(now);
					}
				} catch (Throwable var18) {
					this.failedCaptures.incrementAndGet();
					RecordableMod.LOGGER.warn("Record-able failed to capture a frame.", var18);
				}
			}
		}
	}

	public void onClientTick(class_310 client) {
		if (this.state == RecordingManager.State.RECORDING) {
			RecordableConfig config = RecordableConfig.get();
			if (config.maxFileSizeMB > 0) {
				long maxBytes = (long)config.maxFileSizeMB * 1024L * 1024L;
				long currentBytes = this.getCurrentFileSizeBytes();
				if (currentBytes >= maxBytes && currentBytes > 0L) {
					RecordableMod.LOGGER.info("File size limit reached: {} >= {} MB. Stopping recording.", formatBytes(currentBytes), config.maxFileSizeMB);
					RecordableMod.sendClientMessage(
						ChatCategory.RECORDING,
						client,
						"§eRecord-able auto-stopped: file size limit reached (" + config.maxFileSizeMB + " MB). Increase the limit in settings or set to 0 for unlimited.",
						false
					);
					this.stopRecording(client, RecordingManager.StopReason.FILE_SIZE_LIMIT);
					return;
				}
			}

			this.samplePerformanceMetrics();
			this.evaluateAdaptiveCaptureFps(client, config);
			if (!this.performanceSuggestionSent && this.getDroppedFrames() >= 100L) {
				this.performanceSuggestionSent = true;
				RecordableMod.LOGGER
					.warn("Performance issue detected - droppedFrames={} queue={}/{}.", new Object[]{this.getDroppedFrames(), this.getQueueSize(), this.getQueueCapacity()});
				RecordableMod.sendClientMessage(
					ChatCategory.RECORDING, client, "§eRecord-able is dropping frames. Try 720p/30fps or Quality=Performance for smoother recording.", true
				);
			}
		}
	}

	private void evaluateAdaptiveCaptureFps(class_310 client, RecordableConfig config) {
		if (config != null && client != null) {
			if (config.perfOptimizerEnabled && config.perfAutoAdjust && config.perfActionLowerFps) {
				long now = System.nanoTime();
				if (now - this.lastGovernorCheckNanos >= 1000000000L) {
					this.lastGovernorCheckNanos = now;
					int gameFps = client.method_47599();
					if (gameFps > 0) {
						int minFps = Math.max(1, config.perfMinFps);
						int FLOOR_FPS = 15;
						int STEP = 5;
						synchronized (this.lock) {
							if (this.state == RecordingManager.State.RECORDING && this.baseFrameIntervalNanos > 0L) {
								int baseFps = Math.max(1, (int)Math.round(1.0E9 / (double)this.baseFrameIntervalNanos));
								if (this.governorCurrentFps <= 0) {
									this.governorCurrentFps = baseFps;
								}

								if (gameFps < minFps && this.governorCurrentFps > 15) {
									int newFps = Math.max(15, this.governorCurrentFps - 5);
									if (newFps < this.governorCurrentFps) {
										this.governorCurrentFps = newFps;
										this.frameIntervalNanos = Math.max(1L, 1000000000L / (long)newFps);
										RecordableMod.LOGGER
											.info("Adaptive capture-fps governor: game FPS {} < target {}, lowering capture to {} fps.", new Object[]{gameFps, minFps, newFps});
									}
								} else if (gameFps > minFps + 5 && this.governorCurrentFps < baseFps) {
									int newFps = Math.min(baseFps, this.governorCurrentFps + 5);
									if (newFps > this.governorCurrentFps) {
										this.governorCurrentFps = newFps;
										this.frameIntervalNanos = Math.max(1L, 1000000000L / (long)newFps);
										RecordableMod.LOGGER.info("Adaptive capture-fps governor: game FPS {} recovered, raising capture to {} fps.", gameFps, newFps);
									}
								}
							}
						}
					}
				}
			}
		}
	}

	public void shutdown() {
		try {
			class_310 activeClient = resolveClient(null);
			if (activeClient != null && !activeClient.method_18854()) {
				try {
					activeClient.method_19537(this::shutdown);
					return;
				} catch (Throwable var3) {
					RecordableMod.LOGGER.warn("Failed to marshal shutdown onto client thread.", var3);
				}
			}
		} catch (Throwable var4) {
			RecordableMod.LOGGER.warn("Could not resolve client during shutdown.", var4);
		}

		this.doShutdown();
	}

	public void forceShutdown() {
		this.doShutdown();
	}

	private void doShutdown() {
		FFmpegEncoder ffmpegToStop;
		ScreenCapture captureToClose;
		synchronized (this.lock) {
			if (this.state == RecordingManager.State.IDLE) {
				return;
			}

			this.state = RecordingManager.State.STOPPING;
			ffmpegToStop = this.ffmpegEncoder != null ? this.ffmpegEncoder : this.pendingStartFfmpegEncoder;
			captureToClose = this.screenCapture;
			this.ffmpegEncoder = null;
			this.pendingStartFfmpegEncoder = null;
			this.screenCapture = null;
			this.lastFrameCaptureNanos = 0L;
			this.frameIntervalNanos = 0L;
		}

		if (captureToClose != null) {
			try {
				captureToClose.close();
			} catch (Throwable var8) {
				RecordableMod.LOGGER.warn("Failed to close screen capture during shutdown.", var8);
			}
		}

		if (ffmpegToStop != null) {
			try {
				Path finalized = ffmpegToStop.stop();
				if (finalized != null) {
					this.outputFileFromStop(finalized);
				}

				this.finalEncoderDroppedFrames.addAndGet(ffmpegToStop.getDroppedFrames());
			} catch (Throwable var7) {
				RecordableMod.LOGGER.warn("Failed to stop FFmpeg encoder during shutdown.", var7);
			}
		}

		synchronized (this.lock) {
			this.state = RecordingManager.State.IDLE;
			this.startedAtNanos = 0L;
			this.lastFrameCaptureNanos = 0L;
			this.frameIntervalNanos = 0L;
			this.captureFpsEstimate = 0.0;
			this.encoderFpsEstimate = 0.0;
			this.performanceSuggestionSent = false;
		}
	}

	private void finishStop(class_310 client, FFmpegEncoder ffmpegToStop, Path output, RecordingManager.StopReason reason) {
		if (ReplayBuffer.getInstance().isActive()) {
			ReplayBuffer.getInstance().stop();
		}

		Path finalizedOutput = output;
		long encoderWritten = 0L;
		if (ffmpegToStop != null) {
			Path encoderOutput = ffmpegToStop.stop();
			if (encoderOutput != null) {
				finalizedOutput = encoderOutput;
			}

			this.finalEncoderDroppedFrames.addAndGet(ffmpegToStop.getDroppedFrames());
			encoderWritten = ffmpegToStop.getWrittenFrames();
		}

		long fileSize = safeFileSize(finalizedOutput);
		long durationMillis;
		synchronized (this.lock) {
			durationMillis = this.startedAtNanos > 0L ? Math.max(0L, (System.nanoTime() - this.startedAtNanos) / 1000000L) : 0L;
			this.startedAtNanos = 0L;
			this.state = RecordingManager.State.IDLE;
			this.pendingStartFfmpegEncoder = null;
			this.currentOutputFile = finalizedOutput;
			this.lastOutputFile = finalizedOutput;
			this.lastFrameCaptureNanos = 0L;
			this.frameIntervalNanos = 0L;
			this.captureFpsEstimate = 0.0;
			this.encoderFpsEstimate = 0.0;
			this.performanceSuggestionSent = false;
		}

		if (RecordableConfig.get().bookmarksEnabled) {
			this.saveBookmarks(finalizedOutput);
		}

		if (reason == RecordingManager.StopReason.DISCONNECT) {
			this.pendingJoinNotification = "Previous recording was saved: " + (finalizedOutput == null ? "recording" : finalizedOutput.getFileName());
			RecordableMod.LOGGER.info("Recording saved (disconnected): {} ({}).", finalizedOutput, formatBytes(fileSize));
		} else {
			if (reason != RecordingManager.StopReason.SHUTDOWN) {
				String savedMsg = "Record-able saved " + (finalizedOutput == null ? "recording" : finalizedOutput.getFileName()) + " (" + formatBytes(fileSize) + ")";
				runOnClient(client, () -> RecordableMod.sendClientMessage(ChatCategory.RECORDING, client, savedMsg, false));
				if (RecordableConfig.get().showPostRecordingToast) {
					this.pendingToastMessage = "§a✓ Recording saved: §f"
						+ (finalizedOutput == null ? "recording" : finalizedOutput.getFileName())
						+ " §7("
						+ formatBytes(fileSize)
						+ ", "
						+ formatDuration(durationMillis)
						+ ")";
					this.pendingToastFilePath = finalizedOutput != null ? finalizedOutput.getParent() : null;
					this.pendingToastExpiresAtMs = System.currentTimeMillis() + 8000L;
				}
			}

			RecordableMod.LOGGER
				.info(
					"Recording stopped: reason={} duration={} framesCaptured={} dropped={} encoderWritten={} file={} ({})",
					new Object[]{
						reason, formatDuration(durationMillis), this.capturedFrames.get(), this.getDroppedFrames(), encoderWritten, finalizedOutput, formatBytes(fileSize)
					}
				);
		}
	}

	private void logBacklogIfNeeded(long nowNanos) {
		if (nowNanos - this.lastBacklogLogAtNanos >= 2000000000L) {
			this.lastBacklogLogAtNanos = nowNanos;
			int qSize = this.getQueueSize();
			int qCapacity = this.getQueueCapacity();
			double queueRatio = qCapacity <= 0 ? 0.0 : (double)qSize / (double)qCapacity;
			RecordableMod.LOGGER
				.warn(
					"Encoder falling behind - dropping frames. Queue: {}/{} ({}%) | dropped={} (adaptive={})",
					new Object[]{qSize, qCapacity, Math.round(queueRatio * 100.0), this.getDroppedFrames(), this.adaptiveDroppedFrames.get()}
				);
		}
	}

	private void samplePerformanceMetrics() {
		FFmpegEncoder ff = this.ffmpegEncoder;
		if (ff != null) {
			long now = System.nanoTime();
			if (this.lastPerformanceSampleAtNanos == 0L) {
				this.lastPerformanceSampleAtNanos = now;
				this.lastCapturedSample = this.capturedFrames.get();
				this.lastEncoderWrittenSample = ff.getWrittenFrames();
			} else {
				long elapsed = now - this.lastPerformanceSampleAtNanos;
				if (elapsed >= 1000000000L) {
					long capturedNow = this.capturedFrames.get();
					long writtenNow = ff.getWrittenFrames();
					double elapsedSeconds = Math.max(0.001, (double)elapsed / 1.0E9);
					this.captureFpsEstimate = Math.max(0.0, (double)(capturedNow - this.lastCapturedSample) / elapsedSeconds);
					this.encoderFpsEstimate = Math.max(0.0, (double)(writtenNow - this.lastEncoderWrittenSample) / elapsedSeconds);
					this.lastCapturedSample = capturedNow;
					this.lastEncoderWrittenSample = writtenNow;
					this.lastPerformanceSampleAtNanos = now;
				}
			}
		}
	}

	private boolean shouldCaptureFrame(long nowNanos) {
		long interval = this.frameIntervalNanos > 0L ? this.frameIntervalNanos : Math.max(1L, 1000000000L / (long)Math.max(1, this.recordingFps));
		if (this.lastFrameCaptureNanos > 0L && nowNanos - this.lastFrameCaptureNanos < interval) {
			return false;
		} else {
			double queueRatio = this.getActiveQueueUsageRatio();
			if (queueRatio >= 0.9) {
				long total = this.capturedFrames.get() + this.skippedFrames.get();
				if (total % 4L != 0L) {
					this.adaptiveDroppedFrames.incrementAndGet();
					return false;
				}
			} else if (queueRatio >= 0.7) {
				long total = this.capturedFrames.get() + this.skippedFrames.get();
				if (total % 2L != 0L) {
					this.adaptiveDroppedFrames.incrementAndGet();
					return false;
				}
			}

			this.lastFrameCaptureNanos = nowNanos;
			return true;
		}
	}

	private static class_310 resolveClient(class_310 client) {
		return client == null ? class_310.method_1551() : client;
	}

	private static void executeOnClientThread(class_310 client, Runnable runnable, String actionDescription) {
		if (client != null && runnable != null) {
			try {
				client.execute(runnable);
			} catch (Throwable var4) {
				RecordableMod.LOGGER.warn("Failed to schedule Record-able {} on client thread.", actionDescription, var4);
			}
		}
	}

	private static void runOnClient(class_310 client, Runnable runnable) {
		if (runnable != null) {
			try {
				class_310 activeClient = resolveClient(client);
				if (activeClient != null && !activeClient.method_18854()) {
					activeClient.execute(runnable);
				} else {
					runnable.run();
				}
			} catch (Throwable var3) {
				runnable.run();
			}
		}
	}

	private void closeCaptureSafely(class_310 client, ScreenCapture capture, RecordingManager.StopReason reason) {
		if (capture != null) {
			Runnable closeTask = () -> {
				try {
					capture.close();
				} catch (Throwable var3) {
					RecordableMod.LOGGER.warn("Failed to close screen capture (reason={}).", reason, var3);
				}
			};
			class_310 activeClient = resolveClient(client);
			if (activeClient != null && !activeClient.method_18854()) {
				try {
					activeClient.execute(closeTask);
					return;
				} catch (Throwable var7) {
					RecordableMod.LOGGER.warn("Failed to schedule screen-capture close; closing on current thread.", var7);
				}
			}

			closeTask.run();
		}
	}

	public RecordingManager.State getState() {
		return this.state;
	}

	public boolean isRecording() {
		return this.state == RecordingManager.State.RECORDING;
	}

	public boolean isStopping() {
		return this.state == RecordingManager.State.STOPPING || this.state == RecordingManager.State.STARTING;
	}

	public boolean isPaused() {
		return this.state == RecordingManager.State.PAUSED;
	}

	public boolean isActiveOrStopping() {
		return this.state == RecordingManager.State.STARTING
			|| this.state == RecordingManager.State.RECORDING
			|| this.state == RecordingManager.State.PAUSED
			|| this.state == RecordingManager.State.STOPPING;
	}

	public void requestCaptureSelfTest() {
		this.selfTestResult = null;
		this.selfTestRequested = true;
	}

	public SelfTestResult getCaptureSelfTestResult() {
		return this.selfTestResult;
	}

	public LiveStats getLiveCaptureStats() {
		synchronized (this.lock) {
			ScreenCapture cap = this.screenCapture;
			return cap == null
				? null
				: new LiveStats(
					cap.getTotalFramesProduced(),
					cap.getTotalBlackFrames(),
					cap.getConsecutiveBlackFrames(),
					cap.isPersistentlyBlack(),
					cap.getReadSourceName(),
					cap.getSourceWidth(),
					cap.getSourceHeight(),
					cap.getRenderTargetWidth(),
					cap.getRenderTargetHeight(),
					cap.hasSizeMismatch()
				);
		}
	}

	private SelfTestResult runCaptureSelfTest() {
		ScreenCapture probe = null;

		SelfTestResult var13;
		try {
			class_310 client = class_310.method_1551();
			if (client == null || client.method_22683() == null) {
				return new SelfTestResult(false, false, 0.0, 0, 0, "No game window available.");
			}

			int winW = client.method_22683().method_4489();
			int winH = client.method_22683().method_4506();
			int[] size = scaledProbeSize(winW, winH);
			probe = new ScreenCapture(size[0], size[1]);
			CapturedFrame frame = probe.captureFrameSynchronous();
			if (frame == null || frame.rgbPixels() == null) {
				return new SelfTestResult(false, false, 0.0, size[0], size[1], "Synchronous capture returned no frame.");
			}

			byte[] rgb = frame.rgbPixels();
			int w = frame.width();
			int h = frame.height();
			double brightness = FrameValidator.averageBrightness(rgb, w, h);
			boolean black = FrameValidator.isBlackFrame(rgb, w, h);
			var13 = new SelfTestResult(true, black, brightness, w, h, null);
		} catch (Throwable var25) {
			RecordableMod.LOGGER.warn("Capture self-test failed.", var25);
			return new SelfTestResult(false, false, 0.0, 0, 0, "Self-test threw: " + var25.getClass().getSimpleName());
		} finally {
			if (probe != null) {
				try {
					probe.close();
				} catch (Throwable var24) {
				}
			}
		}

		return var13;
	}

	private static int[] scaledProbeSize(int winW, int winH) {
		int maxW = 480;
		if (winW <= 0 || winH <= 0) {
			return new int[]{320, 180};
		} else if (winW <= maxW) {
			return new int[]{Math.max(2, winW), Math.max(2, winH)};
		} else {
			double scale = (double)maxW / (double)winW;
			return new int[]{maxW, Math.max(2, (int)Math.round((double)winH * scale))};
		}
	}

	public String consumePendingJoinNotification() {
		String notification = this.pendingJoinNotification;
		this.pendingJoinNotification = null;
		return notification;
	}

	public long getElapsedMillis() {
		return this.isActiveOrStopping() && this.startedAtNanos != 0L ? Math.max(0L, (System.nanoTime() - this.startedAtNanos) / 1000000L) : 0L;
	}

	public long getCapturedFrames() {
		return this.capturedFrames.get();
	}

	public long getDroppedFrames() {
		FFmpegEncoder ff = this.ffmpegEncoder;
		long encoderDrops = ff != null ? ff.getDroppedFrames() : this.finalEncoderDroppedFrames.get();
		return this.skippedFrames.get() + this.failedCaptures.get() + encoderDrops;
	}

	public long getAdaptiveDroppedFrames() {
		return this.adaptiveDroppedFrames.get();
	}

	public RecordingManager.QueueHealth getQueueHealth() {
		double ratio = this.getActiveQueueUsageRatio();
		if (ratio >= 0.9) {
			return RecordingManager.QueueHealth.CRITICAL;
		} else {
			return ratio >= 0.5 ? RecordingManager.QueueHealth.SLOW : RecordingManager.QueueHealth.OK;
		}
	}

	public double getCaptureFpsEstimate() {
		return this.captureFpsEstimate;
	}

	public double getEncoderFpsEstimate() {
		FFmpegEncoder ff = this.ffmpegEncoder;
		double realtime = ff != null ? ff.getEstimatedEncoderFps() : 0.0;
		return Math.max(this.encoderFpsEstimate, realtime);
	}

	public long getUsedMemoryMiB() {
		Runtime runtime = Runtime.getRuntime();
		long usedBytes = Math.max(0L, runtime.totalMemory() - runtime.freeMemory());
		return usedBytes / 1048576L;
	}

	public int getRecordingFps() {
		return this.recordingFps;
	}

	public int getRecordingWidth() {
		return this.recordingWidth;
	}

	public int getRecordingHeight() {
		return this.recordingHeight;
	}

	public int getQueueSize() {
		FFmpegEncoder ff = this.ffmpegEncoder;
		return ff != null ? ff.getQueueSize() : 0;
	}

	public int getQueueCapacity() {
		FFmpegEncoder ff = this.ffmpegEncoder;
		return ff != null ? ff.getQueueCapacity() : 240;
	}

	public long getCurrentFileSizeBytes() {
		FFmpegEncoder ff = this.ffmpegEncoder;
		long size = ff != null ? ff.getOutputFileSizeBytes() : 0L;
		return size > 0L ? size : safeFileSize(this.currentOutputFile == null ? this.lastOutputFile : this.currentOutputFile);
	}

	public EncoderType getActiveEncoderType() {
		return EncoderType.FFMPEG;
	}

	public Path getCurrentOutputFile() {
		return this.currentOutputFile == null ? this.lastOutputFile : this.currentOutputFile;
	}

	public Path getCurrentOutputDirectory() {
		return RecordableConfig.get().getOutputDirectory();
	}

	public String getPendingToastMessage() {
		if (this.pendingToastMessage != null && System.currentTimeMillis() > this.pendingToastExpiresAtMs) {
			this.pendingToastMessage = null;
			this.pendingToastFilePath = null;
		}

		return this.pendingToastMessage;
	}

	public Path getPendingToastFilePath() {
		return this.pendingToastFilePath;
	}

	public void dismissToast() {
		this.pendingToastMessage = null;
		this.pendingToastFilePath = null;
	}

	public String getEstimatedFileSize() {
		long effectiveMs = this.getEffectiveRecordingMillis();
		long currentSize = this.getCurrentFileSizeBytes();
		return currentSize > 0L && effectiveMs > 2000L ? formatBytes(currentSize) : "calculating...";
	}

	private double getActiveQueueUsageRatio() {
		FFmpegEncoder ff = this.ffmpegEncoder;
		return ff != null ? ff.getQueueUsageRatio() : 0.0;
	}

	public static String formatDuration(long elapsedMillis) {
		long seconds = elapsedMillis / 1000L;
		long hours = seconds / 3600L;
		long minutes = seconds % 3600L / 60L;
		long remainingSeconds = seconds % 60L;
		return hours > 0L ? String.format("%d:%02d:%02d", hours, minutes, remainingSeconds) : String.format("%02d:%02d", minutes, remainingSeconds);
	}

	public static String formatBytes(long bytes) {
		if (bytes < 1024L) {
			return bytes + " B";
		} else {
			double kib = (double)bytes / 1024.0;
			if (kib < 1024.0) {
				return String.format("%.1f KiB", kib);
			} else {
				double mib = kib / 1024.0;
				return mib < 1024.0 ? String.format("%.1f MiB", mib) : String.format("%.2f GiB", mib / 1024.0);
			}
		}
	}

	private void outputFileFromStop(Path finalized) {
		synchronized (this.lock) {
			this.currentOutputFile = finalized;
			this.lastOutputFile = finalized;
		}
	}

	public String addBookmark() {
		if (this.state != RecordingManager.State.RECORDING) {
			return null;
		} else {
			long timestampMs = this.getEffectiveRecordingMillis();
			this.bookmarkCounter++;
			String desc = "Bookmark " + this.bookmarkCounter;
			RecordingBookmark bookmark = new RecordingBookmark(timestampMs, desc);
			this.bookmarks.add(bookmark);
			RecordableMod.LOGGER.info("Bookmark added: {} at {}", desc, formatDuration(timestampMs));
			return desc;
		}
	}

	private void saveBookmarks(Path videoFile) {
		if (!this.bookmarks.isEmpty() && videoFile != null) {
			try {
				String videoName = videoFile.getFileName().toString();
				int dotIndex = videoName.lastIndexOf(46);
				String baseName = dotIndex > 0 ? videoName.substring(0, dotIndex) : videoName;
				Path bookmarkFile = videoFile.getParent().resolve(baseName + "_bookmarks.txt");
				List<String> lines = new ArrayList();
				lines.add("Recording Bookmarks for: " + videoName);
				lines.add("Generated by Record-able");
				lines.add("");
				synchronized (this.bookmarks) {
					for (RecordingBookmark bm : this.bookmarks) {
						lines.add(bm.toFileLine());
					}
				}

				Files.write(bookmarkFile, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
				RecordableMod.LOGGER.info("Saved {} bookmarks to {}", this.bookmarks.size(), bookmarkFile);
			} catch (Exception var12) {
				RecordableMod.LOGGER.warn("Failed to save bookmarks file.", var12);
			}
		}
	}

	public List<RecordingBookmark> getBookmarks() {
		return List.copyOf(this.bookmarks);
	}

	public int getBookmarkCount() {
		return this.bookmarks.size();
	}

	private static int makeEven(int value) {
		return value % 2 == 0 ? value : value - 1;
	}

	private static long safeFileSize(Path path) {
		if (path == null) {
			return 0L;
		} else {
			try {
				return Files.exists(path, new LinkOption[0]) ? Files.size(path) : 0L;
			} catch (IOException var2) {
				return 0L;
			}
		}
	}

	public static enum QueueHealth {
		OK,
		SLOW,
		CRITICAL;
	}

	public static enum State {
		IDLE,
		STARTING,
		RECORDING,
		PAUSED,
		STOPPING;
	}

	public static enum StopReason {
		MANUAL,
		DISCONNECT,
		SHUTDOWN,
		AUTO,
		FILE_SIZE_LIMIT;
	}
}
