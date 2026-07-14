package dev.recordable;

import dev.recordable.AudioCapture.AudioDeviceStatus;
import dev.recordable.FfmpegBundleManager.ExecMethod;
import dev.recordable.RecordableConfig.AudioEncoder;
import dev.recordable.RecordableConfig.VideoEncoder;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.lang.ProcessBuilder.Redirect;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.minecraft.class_2561;
import net.minecraft.class_310;

public final class FFmpegEncoder {
	private static final int DEFAULT_QUEUE_CAPACITY = 120;
	private static final int WATCHDOG_MIN_FRAMES = 1;
	private static final long WATCHDOG_TIMEOUT_MS = 6000L;
	private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static volatile FFmpegEncoder.FfmpegStatus cachedStatus;
	private static volatile List<String> lastDiagnostics = new ArrayList();
	private static volatile long cachedStatusAtMs;
	private final RecordableConfig config;
	private final int width;
	private final int height;
	private final int fps;
	private final int frameByteSize;
	private final int queueCapacity;
	private final ArrayBlockingQueue<FFmpegEncoder.FramePacket> queue;
	private final AtomicBoolean acceptingFrames = new AtomicBoolean(false);
	private final AtomicLong droppedFrames = new AtomicLong();
	private final AtomicLong writtenFrames = new AtomicLong();
	private final String filePrefix;
	private final AtomicLong writerStartedAtNanos = new AtomicLong();
	private volatile boolean perfWarningLogged = false;
	private Process process;
	private OutputStream ffmpegStdin;
	private Thread writerThread;
	private Thread stderrThread;
	private Thread watchdogThread;
	private Path outputFile;
	private String commandLine = "";
	private volatile String lastError = "";
	private volatile boolean audioEnabled = false;
	private volatile String audioDeviceInfo = "";
	private volatile long recordingStartNanos = 0L;
	private volatile long parsedSizeBytes = 0L;
	private volatile long parsedFrameCount = 0L;
	private volatile double parsedFps = 0.0;
	private volatile double parsedBitrate = 0.0;
	private volatile String parsedSpeed = "";
	private volatile long audioRecordingStartNanos = 0L;
	private volatile boolean deferredLoopbackConnection = false;
	private String ffmpegExecutable = "ffmpeg";
	private OpenALAudioCapture openAlCapture;
	private Thread audioWriterThread;
	private Path tempAudioFile;
	private volatile boolean audioWriterRunning;
	private Process ffmpegAudioProcess;
	private Thread ffmpegAudioStderrThread;
	private BufferedOutputStream loopbackAudioStream;
	private FileOutputStream loopbackFileStream;
	private volatile AudioDeviceStatus detectedAudioStatus;
	private Process micCaptureProcess;
	private Thread micCaptureStderrThread;
	private Path tempMicFile;
	private volatile boolean micEnabled = false;
	private volatile long micRecordingStartNanos = 0L;
	private static final int NOISE_SUPPRESSION_NR_DB = 18;
	private static final int NOISE_SUPPRESSION_FLOOR_DB = -28;
	private String selectedVideoCodec = null;
	private static volatile String cachedSoftwareCodec = null;
	private volatile boolean encoderStuck = false;
	private static final Pattern SIZE_PATTERN = Pattern.compile("size=\\s*(\\d+)\\s*([kKmMgG]i?[bB]?)");
	private static final Pattern FRAME_PATTERN = Pattern.compile("frame=\\s*(\\d+)");
	private static final Pattern FPS_PATTERN = Pattern.compile("fps=\\s*([\\d.]+)");
	private static final Pattern BITRATE_PATTERN = Pattern.compile("bitrate=\\s*([\\d.]+)\\s*([kKmMgG]?)bits/s");
	private static final Pattern SPEED_PATTERN = Pattern.compile("speed=\\s*([\\d.]+x)");

	public FFmpegEncoder(RecordableConfig config, int width, int height, int fps) {
		this(config, width, height, fps, 120, null);
	}

	public FFmpegEncoder(RecordableConfig config, int width, int height, int fps, int queueCapacity) {
		this(config, width, height, fps, queueCapacity, null);
	}

	public FFmpegEncoder(RecordableConfig config, int width, int height, int fps, int queueCapacity, String filePrefix) {
		this.config = config;
		this.width = width;
		this.height = height;
		this.fps = fps;
		this.frameByteSize = Math.max(1, width * height * 3);
		this.queueCapacity = Math.max(30, queueCapacity);
		this.queue = new ArrayBlockingQueue(this.queueCapacity);
		this.filePrefix = filePrefix != null && !filePrefix.isEmpty() ? filePrefix + "_" : "";
	}

	public Path start() throws IOException {
		if (!PlatformUtils.isRecordingSupported()) {
			throw new IOException("Recording is not supported on " + PlatformUtils.detectPlatform().displayName() + ". " + PlatformUtils.getFfmpegInstallHint());
		} else {
			FFmpegEncoder.FfmpegStatus status = detectFfmpeg();
			if (!status.found()) {
				String hint = PlatformUtils.getFfmpegInstallHint();
				RecordableMod.LOGGER.error("FFmpeg not found on {}. {}", PlatformUtils.detectPlatform().displayName(), hint);
				throw new IOException(status.error().isBlank() ? "FFmpeg was not found. " + hint : status.error() + " - " + hint);
			} else {
				this.ffmpegExecutable = status.executable();
				Path outputDirectory = this.config.getOutputDirectory();
				Files.createDirectories(outputDirectory);
				String finalPrefix = this.filePrefix;
				if (this.filePrefix != null && this.filePrefix.startsWith("on-")) {
					Path autoClipsDir = outputDirectory.resolve("recording_auto_clips");
					Path triggerDir = autoClipsDir.resolve(this.filePrefix.replace("-", "_"));
					Files.createDirectories(triggerDir);
					this.outputFile = triggerDir.resolve("recordable-" + FILE_TIMESTAMP.format(LocalDateTime.now()) + "." + this.config.getFormat());
				} else {
					this.outputFile = outputDirectory.resolve("recordable-" + this.filePrefix + FILE_TIMESTAMP.format(LocalDateTime.now()) + "." + this.config.getFormat());
				}

				this.audioEnabled = false;
				this.audioDeviceInfo = "";
				this.tempAudioFile = null;
				this.detectedAudioStatus = null;
				this.micEnabled = false;
				this.tempMicFile = null;
				this.micRecordingStartNanos = 0L;
				this.startFfmpegAudioCapture(outputDirectory);
				List<String> command = this.buildCommand(this.ffmpegExecutable, this.outputFile);
				this.commandLine = joinCommand(command);
				RecordableMod.LOGGER.debug("=== FFmpeg Encoder Configuration ===");
				RecordableMod.LOGGER
					.debug(
						"Resolution: {}x{} | FPS: {} | Format: {} | Frame size: {} bytes",
						new Object[]{this.width, this.height, this.fps, this.config.getFormat(), this.frameByteSize}
					);
				RecordableMod.LOGGER.debug("Queue capacity: {} | Output: {}", this.queueCapacity, this.outputFile);
				RecordableMod.LOGGER.debug("Complete FFmpeg command:");
				RecordableMod.LOGGER.debug("{}", this.commandLine);
				RecordableMod.LOGGER.debug("FFmpeg arguments breakdown:");

				for (int i = 0; i < command.size(); i++) {
					RecordableMod.LOGGER.debug("  [{}] = {}", i, command.get(i));
				}

				ProcessBuilder processBuilder = ffmpegProcess(command);
				processBuilder.directory(outputDirectory.toFile());
				processBuilder.redirectOutput(Redirect.DISCARD);

				try {
					class_310 client = class_310.method_1551();
					if (!RecordingManager.isInGameState(client)) {
						throw new IOException("Blocked FFmpeg start because client is not in-game.");
					}

					this.process = processBuilder.start();
					int pipeBuffer = Math.max(this.frameByteSize + 65536, 8388608);
					this.ffmpegStdin = new BufferedOutputStream(this.process.getOutputStream(), pipeBuffer);
					Thread.sleep(150L);
					if (!this.process.isAlive()) {
						int exitCode = this.process.exitValue();
						this.stopAudioCapture();
						throw new IOException(
							"FFmpeg exited immediately with code "
								+ exitCode
								+ ". Command: "
								+ this.commandLine
								+ (this.lastError.isEmpty() ? "" : ". Last error: " + this.lastError)
						);
					}

					this.acceptingFrames.set(true);
					this.recordingStartNanos = System.nanoTime();
					this.writerStartedAtNanos.set(this.recordingStartNanos);
					this.connectDeferredLoopbackAudio();
					this.startMicrophoneCapture(outputDirectory);
					this.writerThread = new Thread(this::writerLoop, "Record-able FFmpeg Writer");
					this.writerThread.setDaemon(true);
					this.writerThread.setPriority(Math.min(9, 6));
					this.writerThread.start();
					this.stderrThread = new Thread(() -> this.stderrLoop(this.process.getErrorStream()), "Record-able FFmpeg Log");
					this.stderrThread.setDaemon(true);
					this.stderrThread.start();
					this.watchdogThread = new Thread(this::watchdogLoop, "Record-able FFmpeg Watchdog");
					this.watchdogThread.setDaemon(true);
					this.watchdogThread.start();
					RecordableMod.LOGGER.info("FFmpeg process started successfully (pid={}). Waiting for frames...", this.process.pid());
				} catch (InterruptedException var9) {
					Thread.currentThread().interrupt();
					this.stopAudioCapture();
					throw new IOException("Interrupted while waiting for FFmpeg to initialize.", var9);
				} catch (IOException var10) {
					this.stopAudioCapture();
					throw var10;
				}

				return this.outputFile;
			}
		}
	}

	public FFmpegEncoder.EnqueueResult writeFrame(ByteBuffer frameData) {
		if (frameData == null) {
			return FFmpegEncoder.EnqueueResult.REJECTED;
		} else {
			ByteBuffer duplicate = frameData.slice();
			byte[] copy = new byte[duplicate.remaining()];
			duplicate.get(copy);
			return this.writeFrame(copy, -1L);
		}
	}

	public FFmpegEncoder.EnqueueResult writeFrame(byte[] frameData) {
		return this.writeFrame(frameData, -1L);
	}

	public FFmpegEncoder.EnqueueResult writeFrame(byte[] frameData, long frameTimestampMs) {
		if (this.acceptingFrames.get() && frameData != null && frameData.length == this.frameByteSize) {
			long ts = frameTimestampMs;
			if (frameTimestampMs < 0L && this.recordingStartNanos > 0L) {
				ts = (System.nanoTime() - this.recordingStartNanos) / 1000000L;
			}

			FFmpegEncoder.FramePacket packet = new FFmpegEncoder.FramePacket(frameData, ts);
			if (!this.queue.offer(packet)) {
				this.droppedFrames.incrementAndGet();
				return FFmpegEncoder.EnqueueResult.REJECTED;
			} else {
				long totalWritten = this.writtenFrames.get();
				if (totalWritten == 0L && this.queue.size() == 1) {
					RecordableMod.LOGGER
						.info(
							"First frame enqueued! Size: {} bytes, expected: {} bytes. FFmpeg alive: {}",
							new Object[]{frameData.length, this.frameByteSize, this.process != null && this.process.isAlive()}
						);
				}

				return FFmpegEncoder.EnqueueResult.QUEUED;
			}
		} else {
			if (frameData != null && frameData.length != this.frameByteSize) {
				RecordableMod.LOGGER.warn("Frame size mismatch: expected {} bytes, got {} bytes. Frame rejected.", this.frameByteSize, frameData.length);
			}

			return FFmpegEncoder.EnqueueResult.REJECTED;
		}
	}

	public Path stop() {
		this.acceptingFrames.set(false);
		if (this.writerThread != null) {
			try {
				this.writerThread.join(15000L);
			} catch (InterruptedException var19) {
				Thread.currentThread().interrupt();
			}
		}

		this.closeStdinQuietly();
		if (this.process != null) {
			try {
				if (!this.process.waitFor(10L, TimeUnit.SECONDS)) {
					RecordableMod.LOGGER.warn("FFmpeg did not exit after stdin closed; destroying process.");
					this.process.destroy();
					if (!this.process.waitFor(3L, TimeUnit.SECONDS)) {
						this.process.destroyForcibly();
					}
				}

				int exitCode = this.process.exitValue();
				if (exitCode != 0) {
					RecordableMod.LOGGER.warn("FFmpeg exited with code {}. Last message: {}", exitCode, this.lastError);
				}
			} catch (InterruptedException var17) {
				Thread.currentThread().interrupt();
				this.process.destroyForcibly();
			} finally {
				this.closeProcessStreamsQuietly();
			}
		}

		if (this.stderrThread != null) {
			try {
				this.stderrThread.join(1000L);
			} catch (InterruptedException var16) {
				Thread.currentThread().interrupt();
			}
		}

		this.stopAudioCapture();
		if (this.outputFile == null) {
			this.deleteTempAudioFileQuietly();
			return null;
		} else {
			try {
				if (!Files.exists(this.outputFile, new LinkOption[0]) || Files.size(this.outputFile) <= 0L) {
					RecordableMod.LOGGER.warn("FFmpeg output file was not finalized correctly: {}", this.outputFile);
				}
			} catch (IOException var20) {
				RecordableMod.LOGGER.warn("Failed to verify FFmpeg output file: {}", this.outputFile, var20);
			}

			this.tryMuxCapturedAudio();
			this.deleteTempAudioFileQuietly();

			try {
				long fileSize = Files.exists(this.outputFile, new LinkOption[0]) ? Files.size(this.outputFile) : 0L;
				long totalFrames = this.writtenFrames.get();
				long dropped = this.droppedFrames.get();
				long durationMs = this.recordingStartNanos > 0L ? (System.nanoTime() - this.recordingStartNanos) / 1000000L : 0L;
				RecordableMod.LOGGER.info("=== Recording Complete ===");
				RecordableMod.LOGGER
					.info("Output: {} ({} bytes, {} MB)", new Object[]{this.outputFile.getFileName(), fileSize, String.format("%.1f", (double)fileSize / 1048576.0)});
				RecordableMod.LOGGER
					.info(
						"Frames: {} written, {} dropped | Duration: {}s | Audio: {}", new Object[]{totalFrames, dropped, durationMs / 1000L, this.audioEnabled ? "yes" : "no"}
					);
			} catch (Exception var15) {
				RecordableMod.LOGGER.debug("Could not log recording summary", var15);
			}

			if (PlatformUtils.isAndroid() && RecordableConfig.get().saveToGalleryOnAndroid) {
				PlatformUtils.copyToAndroidGallery(this.outputFile);
			}

			if (PlatformUtils.isAndroid() && RecordableConfig.get().autoCompressOnAndroid) {
				Path toCompress = this.outputFile;
				Thread compressThread = new Thread(() -> {
					Path compressed = PlatformUtils.compressVideoForMobile(toCompress);
					if (compressed != null && RecordableConfig.get().saveToGalleryOnAndroid) {
						PlatformUtils.copyToAndroidGallery(compressed);
					}
				}, "recordable-compress");
				compressThread.setDaemon(true);
				compressThread.start();
			}

			return this.outputFile;
		}
	}

	public int getQueueSize() {
		return this.queue.size();
	}

	public int getQueueCapacity() {
		return this.queueCapacity;
	}

	public double getQueueUsageRatio() {
		return this.queueCapacity <= 0 ? 0.0 : (double)this.queue.size() / (double)this.queueCapacity;
	}

	public boolean isBacklogged() {
		return this.getQueueUsageRatio() >= 0.9;
	}

	public long getDroppedFrames() {
		return this.droppedFrames.get();
	}

	public long getWrittenFrames() {
		return this.writtenFrames.get();
	}

	public double getEstimatedEncoderFps() {
		long started = this.writerStartedAtNanos.get();
		if (started <= 0L) {
			return 0.0;
		} else {
			double elapsedSeconds = Math.max(0.001, (double)(System.nanoTime() - started) / 1.0E9);
			return (double)this.writtenFrames.get() / elapsedSeconds;
		}
	}

	public Path getOutputFile() {
		return this.outputFile;
	}

	public String getCommandLine() {
		return this.commandLine;
	}

	public long getOutputFileSizeBytes() {
		long parsed = this.parsedSizeBytes;
		if (parsed > 0L) {
			return parsed;
		} else if (this.outputFile == null) {
			return 0L;
		} else {
			try {
				return Files.exists(this.outputFile, new LinkOption[0]) ? Files.size(this.outputFile) : 0L;
			} catch (IOException var4) {
				return 0L;
			}
		}
	}

	public double getParsedFps() {
		return this.parsedFps;
	}

	public double getParsedBitrate() {
		return this.parsedBitrate;
	}

	public String getParsedSpeed() {
		return this.parsedSpeed;
	}

	private static ProcessBuilder ffmpegProcess(List<String> command) {
		List<String> wrapped = FfmpegBundleManager.wrapCommandForExec(command);
		ProcessBuilder pb = new ProcessBuilder(wrapped);
		if (command != null && !command.isEmpty()) {
			FfmpegBundleManager.applyExecEnv(pb, (String)command.get(0));
		}

		return pb;
	}

	private static ProcessBuilder ffmpegProcess(String... command) {
		return ffmpegProcess(new ArrayList(Arrays.asList(command)));
	}

	private List<String> buildCommand(String executable, Path output) {
		ArrayList<String> args = new ArrayList();
		args.add(executable);
		args.add("-hide_banner");
		args.add("-loglevel");
		args.add("info");
		args.add("-stats");
		args.add("-y");
		args.add("-f");
		args.add("rawvideo");
		args.add("-pix_fmt");
		args.add("rgb24");
		args.add("-video_size");
		args.add(this.width + "x" + this.height);
		args.add("-framerate");
		args.add(Integer.toString(this.fps));
		args.add("-i");
		args.add("pipe:0");
		args.add("-fps_mode");
		args.add("cfr");
		args.add("-r");
		args.add(Integer.toString(this.fps));
		String smoothFilter = SmoothMotion.buildFilter(this.config, this.fps);
		if (smoothFilter != null) {
			args.add("-vf");
			args.add(smoothFilter);
			RecordableMod.LOGGER.info("Smooth motion enabled: {}", smoothFilter);
		}

		VideoEncoder activeEncoder = this.resolveVideoEncoder();
		this.addVideoCodecArgs(args, activeEncoder);
		args.add("-an");
		if (this.config.maxFileSizeMB > 0) {
			args.add("-fs");
			args.add(Long.toString((long)this.config.maxFileSizeMB * 1024L * 1024L));
		}

		args.add(output.toAbsolutePath().toString());
		return args;
	}

	private void startFfmpegAudioCapture(Path outputDirectory) {
		if (!this.config.captureAudio) {
			this.audioEnabled = false;
			this.audioDeviceInfo = "";
		} else {
			try {
				if (this.startLoopbackAudioCapture(outputDirectory)) {
					return;
				}

				RecordableMod.LOGGER.info("OpenAL loopback unavailable; trying system audio capture...");
				if (this.startFfmpegSystemAudioCapture(outputDirectory)) {
					return;
				}

				RecordableMod.LOGGER.info("System audio unavailable; trying OpenAL capture as last resort...");
				if (this.startOpenALAudioCapture(outputDirectory)) {
					return;
				}

				RecordableMod.LOGGER.warn("No audio capture method available. Recording video only.");
				this.audioEnabled = false;
				if (PlatformUtils.isAndroid()) {
					this.audioDeviceInfo = "No audio device found. Grant the microphone permission to your launcher and check the device microphone settings.";
				} else if (PlatformUtils.isWindows()) {
					this.audioDeviceInfo = "No audio device found. Enable Stereo Mix or install a virtual audio device.";
				} else {
					this.audioDeviceInfo = "No audio device found. Enable a system audio loopback/monitor source (e.g. PulseAudio/PipeWire monitor).";
				}
			} catch (Throwable var3) {
				RecordableMod.LOGGER.warn("Audio initialization failed unexpectedly; continuing in video-only mode.", var3);
				this.stopAudioCapture();
				this.audioEnabled = false;
				this.audioDeviceInfo = "Audio init failed; recording video only.";
			}
		}
	}

	private boolean startLoopbackAudioCapture(Path outputDirectory) {
		try {
			OpenALLoopbackCapture loopback = OpenALLoopbackCapture.getInstance();
			if (!loopback.isActive()) {
				RecordableMod.LOGGER.info("OpenAL loopback not active, skipping loopback capture.");
				return false;
			} else {
				this.tempAudioFile = outputDirectory.resolve("recordable-" + this.filePrefix + "audio-" + FILE_TIMESTAMP.format(LocalDateTime.now()) + ".wav");
				FileOutputStream fos = new FileOutputStream(this.tempAudioFile.toFile());
				BufferedOutputStream bos = new BufferedOutputStream(fos, 131072);
				writeWavHeader(bos, 48000, 2, 16, 0L);
				this.loopbackAudioStream = bos;
				this.loopbackFileStream = fos;
				this.deferredLoopbackConnection = true;
				this.audioEnabled = true;
				this.audioDeviceInfo = "OpenAL Loopback (48000Hz Stereo)";
				RecordableMod.LOGGER.info("OpenAL loopback audio capture PREPARED (deferred connection): {} -> {}", this.audioDeviceInfo, this.tempAudioFile);
				return true;
			}
		} catch (Throwable var5) {
			RecordableMod.LOGGER.warn("Failed to start loopback audio capture.", var5);
			return false;
		}
	}

	private boolean startOpenALAudioCapture(Path outputDirectory) {
		try {
			int sampleRate = this.config.audioSampleRate > 0 ? this.config.audioSampleRate : '뮀';
			int channels = this.config.audioChannelCount == 1 ? 1 : 2;
			this.openAlCapture = new OpenALAudioCapture(sampleRate, channels);
			if (!this.openAlCapture.start()) {
				RecordableMod.LOGGER.warn("OpenAL capture device could not be opened.");
				this.openAlCapture = null;
				return false;
			} else {
				this.tempAudioFile = outputDirectory.resolve("recordable-" + this.filePrefix + "audio-" + FILE_TIMESTAMP.format(LocalDateTime.now()) + ".wav");
				this.audioWriterRunning = true;
				this.audioWriterThread = new Thread(() -> this.openAlAudioWriterLoop(sampleRate, channels), "Record-able OpenAL Audio Writer");
				this.audioWriterThread.setDaemon(true);
				this.audioWriterThread.start();
				this.audioEnabled = true;
				this.audioDeviceInfo = "OpenAL Game Audio (" + sampleRate + "Hz " + (channels == 2 ? "Stereo" : "Mono") + ")";
				RecordableMod.LOGGER.info("OpenAL audio capture started: {} -> {}", this.audioDeviceInfo, this.tempAudioFile);
				return true;
			}
		} catch (Throwable var5) {
			RecordableMod.LOGGER.warn("Failed to start OpenAL audio capture.", var5);
			if (this.openAlCapture != null) {
				try {
					this.openAlCapture.stop();
				} catch (Throwable var4) {
				}

				this.openAlCapture = null;
			}

			return false;
		}
	}

	private void openAlAudioWriterLoop(int sampleRate, int channels) {
		int bitsPerSample = 16;
		long totalDataBytes = 0L;

		try {
			FileOutputStream fos = new FileOutputStream(this.tempAudioFile.toFile());

			try {
				BufferedOutputStream bos = new BufferedOutputStream(fos, 65536);

				try {
					writeWavHeader(bos, sampleRate, channels, bitsPerSample, 0L);

					while (this.audioWriterRunning || this.openAlCapture != null && this.openAlCapture.getQueueSize() > 0) {
						byte[] audioFrame = null;
						if (this.openAlCapture != null) {
							try {
								audioFrame = this.openAlCapture.getNextAudioFrame(50L);
							} catch (InterruptedException var15) {
								Thread.currentThread().interrupt();
								break;
							}
						}

						if (audioFrame != null && audioFrame.length > 0) {
							bos.write(audioFrame);
							totalDataBytes += (long)audioFrame.length;
						}
					}

					bos.flush();
					RandomAccessFile raf = new RandomAccessFile(this.tempAudioFile.toFile(), "rw");

					try {
						updateWavHeaderSize(raf, totalDataBytes);
					} catch (Throwable var14) {
						try {
							raf.close();
						} catch (Throwable var13) {
							var14.addSuppressed(var13);
						}

						throw var14;
					}

					raf.close();
					RecordableMod.LOGGER.info("OpenAL audio writer finished: {} bytes written to {}", totalDataBytes, this.tempAudioFile);
				} catch (Throwable var16) {
					try {
						bos.close();
					} catch (Throwable var12) {
						var16.addSuppressed(var12);
					}

					throw var16;
				}

				bos.close();
			} catch (Throwable var17) {
				try {
					fos.close();
				} catch (Throwable var11) {
					var17.addSuppressed(var11);
				}

				throw var17;
			}

			fos.close();
		} catch (IOException var18) {
			RecordableMod.LOGGER.warn("Error writing OpenAL audio to WAV file.", var18);
		}
	}

	private static void writeWavHeader(OutputStream out, int sampleRate, int channels, int bitsPerSample, long dataSize) throws IOException {
		int byteRate = sampleRate * channels * (bitsPerSample / 8);
		int blockAlign = channels * (bitsPerSample / 8);
		long chunkSize = 36L + dataSize;
		out.write("RIFF".getBytes(StandardCharsets.US_ASCII));
		writeLittleEndianInt(out, (int)chunkSize);
		out.write("WAVE".getBytes(StandardCharsets.US_ASCII));
		out.write("fmt ".getBytes(StandardCharsets.US_ASCII));
		writeLittleEndianInt(out, 16);
		writeLittleEndianShort(out, (short)1);
		writeLittleEndianShort(out, (short)channels);
		writeLittleEndianInt(out, sampleRate);
		writeLittleEndianInt(out, byteRate);
		writeLittleEndianShort(out, (short)blockAlign);
		writeLittleEndianShort(out, (short)bitsPerSample);
		out.write("data".getBytes(StandardCharsets.US_ASCII));
		writeLittleEndianInt(out, (int)dataSize);
	}

	private static void writeLittleEndianInt(OutputStream out, int value) throws IOException {
		out.write(value & 0xFF);
		out.write(value >> 8 & 0xFF);
		out.write(value >> 16 & 0xFF);
		out.write(value >> 24 & 0xFF);
	}

	private static void writeLittleEndianShort(OutputStream out, short value) throws IOException {
		out.write(value & 255);
		out.write(value >> 8 & 0xFF);
	}

	private static void updateWavHeaderSize(RandomAccessFile raf, long dataSize) throws IOException {
		raf.seek(4L);
		int chunkSize = (int)(36L + dataSize);
		raf.write(chunkSize & 0xFF);
		raf.write(chunkSize >> 8 & 0xFF);
		raf.write(chunkSize >> 16 & 0xFF);
		raf.write(chunkSize >> 24 & 0xFF);
		raf.seek(40L);
		int dataSizeInt = (int)dataSize;
		raf.write(dataSizeInt & 0xFF);
		raf.write(dataSizeInt >> 8 & 0xFF);
		raf.write(dataSizeInt >> 16 & 0xFF);
		raf.write(dataSizeInt >> 24 & 0xFF);
	}

	private boolean startFfmpegSystemAudioCapture(Path outputDirectory) {
		try {
			AudioDeviceStatus status = AudioCapture.detectAudioDevice(this.ffmpegExecutable, normalizeConfiguredAudioDevice(this.config.audioDevice));
			this.detectedAudioStatus = status;
			if (!status.available()) {
				RecordableMod.LOGGER.info("No system audio device available: {}.", status.message());
				this.audioDeviceInfo = status.message();
				return false;
			} else {
				this.tempAudioFile = outputDirectory.resolve("recordable-" + this.filePrefix + "audio-" + FILE_TIMESTAMP.format(LocalDateTime.now()) + ".wav");
				List<String> cmd = new ArrayList();
				cmd.add(this.ffmpegExecutable);
				cmd.add("-hide_banner");
				cmd.add("-loglevel");
				cmd.add("warning");
				cmd.add("-y");
				cmd.addAll(status.ffmpegArgs());
				cmd.add("-af");
				cmd.add("highpass=f=80,lowpass=f=18000");
				cmd.add("-c:a");
				cmd.add("pcm_s16le");
				cmd.add("-ar");
				cmd.add(Integer.toString(this.config.audioSampleRate));
				cmd.add("-ac");
				cmd.add(Integer.toString(this.config.audioChannelCount));
				cmd.add(this.tempAudioFile.toAbsolutePath().toString());
				RecordableMod.LOGGER.info("Starting FFmpeg system audio capture (primary): {}", joinCommand(cmd));
				ProcessBuilder pb = ffmpegProcess(cmd);
				pb.directory(outputDirectory.toFile());
				class_310 client = class_310.method_1551();
				if (!RecordingManager.isInGameState(client)) {
					throw new IOException("Blocked FFmpeg audio start because client is not in-game.");
				} else {
					Process audioCaptureProcess = pb.start();
					this.ffmpegAudioProcess = audioCaptureProcess;
					Thread audioStderrThread = new Thread(() -> {
						try {
							BufferedReader reader = new BufferedReader(new InputStreamReader(audioCaptureProcess.getErrorStream(), StandardCharsets.UTF_8));

							String line;
							try {
								while ((line = reader.readLine()) != null) {
									RecordableMod.LOGGER.info("Audio FFmpeg: {}", line);
								}
							} catch (Throwable var5x) {
								try {
									reader.close();
								} catch (Throwable var4x) {
									var5x.addSuppressed(var4x);
								}

								throw var5x;
							}

							reader.close();
						} catch (IOException var6x) {
						}
					}, "Record-able Audio FFmpeg Log");
					audioStderrThread.setDaemon(true);
					audioStderrThread.start();
					this.ffmpegAudioStderrThread = audioStderrThread;
					this.audioEnabled = true;
					this.audioDeviceInfo = status.deviceName() + " (" + status.platform() + ")";
					RecordableMod.LOGGER.info("FFmpeg system audio capture started: device='{}' tempFile={}", status.deviceName(), this.tempAudioFile);
					return true;
				}
			}
		} catch (Exception var8) {
			RecordableMod.LOGGER.warn("Unable to start system audio capture.", var8);
			this.stopAudioCapture();
			this.audioEnabled = false;
			this.audioDeviceInfo = "Failed: " + var8.getMessage();
			return false;
		}
	}

	private void connectDeferredLoopbackAudio() {
		if (this.deferredLoopbackConnection && this.loopbackAudioStream != null) {
			try {
				OpenALLoopbackCapture loopback = OpenALLoopbackCapture.getInstance();
				if (loopback.isActive()) {
					this.audioRecordingStartNanos = System.nanoTime();
					loopback.setRecordingStream(this.loopbackAudioStream);
					this.deferredLoopbackConnection = false;
					long gapMs = (this.audioRecordingStartNanos - this.recordingStartNanos) / 1000000L;
					RecordableMod.LOGGER.info("Loopback audio stream connected ({}ms after video start). Audio and video are now synchronized.", gapMs);
				} else {
					RecordableMod.LOGGER.warn("Loopback became inactive before audio stream could be connected.");
					this.deferredLoopbackConnection = false;
				}
			} catch (Throwable var4) {
				RecordableMod.LOGGER.warn("Failed to connect deferred loopback audio stream.", var4);
				this.deferredLoopbackConnection = false;
			}
		}
	}

	private void stopAudioCapture() {
		try {
			MicrophoneState.endRecording();
		} catch (Throwable var57) {
		}

		try {
			OpenALLoopbackCapture loopback = OpenALLoopbackCapture.getInstance();
			loopback.setRecordingStream(null);
		} catch (Throwable var56) {
		}

		if (this.loopbackAudioStream != null) {
			try {
				this.loopbackAudioStream.flush();
				this.loopbackAudioStream.close();
			} catch (Throwable var55) {
			}

			this.loopbackAudioStream = null;
		}

		if (this.loopbackFileStream != null) {
			try {
				long fileSize = Files.size(this.tempAudioFile);
				long dataSize = fileSize - 44L;
				if (dataSize > 0L) {
					RandomAccessFile raf = new RandomAccessFile(this.tempAudioFile.toFile(), "rw");

					try {
						updateWavHeaderSize(raf, dataSize);
					} catch (Throwable var53) {
						try {
							raf.close();
						} catch (Throwable var52) {
							var53.addSuppressed(var52);
						}

						throw var53;
					}

					raf.close();
				}

				this.loopbackFileStream.close();
			} catch (Throwable var54) {
			}

			this.loopbackFileStream = null;
		}

		this.audioWriterRunning = false;
		if (this.openAlCapture != null) {
			try {
				this.openAlCapture.stop();
			} catch (Throwable var51) {
			}

			this.openAlCapture = null;
		}

		if (this.audioWriterThread != null) {
			try {
				this.audioWriterThread.join(5000L);
			} catch (InterruptedException var50) {
				Thread.currentThread().interrupt();
			}

			this.audioWriterThread = null;
		}

		if (this.ffmpegAudioProcess != null) {
			try {
				OutputStream audioStdin = this.ffmpegAudioProcess.getOutputStream();
				if (audioStdin != null) {
					try {
						audioStdin.write(113);
						audioStdin.flush();
						audioStdin.close();
					} catch (IOException var47) {
					}
				}

				if (!this.ffmpegAudioProcess.waitFor(8L, TimeUnit.SECONDS)) {
					RecordableMod.LOGGER.warn("Audio capture FFmpeg did not exit after 'q'; destroying.");
					this.ffmpegAudioProcess.destroy();
					if (!this.ffmpegAudioProcess.waitFor(3L, TimeUnit.SECONDS)) {
						this.ffmpegAudioProcess.destroyForcibly();
					}
				}

				int exitCode = this.ffmpegAudioProcess.exitValue();
				if (exitCode != 0 && exitCode != 255) {
					RecordableMod.LOGGER.warn("Audio capture FFmpeg exited with code {}", exitCode);
				}
			} catch (InterruptedException var48) {
				Thread.currentThread().interrupt();
				this.ffmpegAudioProcess.destroyForcibly();
			} finally {
				this.ffmpegAudioProcess = null;
			}
		}

		if (this.micCaptureProcess != null) {
			try {
				OutputStream micStdin = this.micCaptureProcess.getOutputStream();
				if (micStdin != null) {
					try {
						micStdin.write(113);
						micStdin.flush();
						micStdin.close();
					} catch (IOException var44) {
					}
				}

				if (!this.micCaptureProcess.waitFor(8L, TimeUnit.SECONDS)) {
					RecordableMod.LOGGER.warn("Microphone FFmpeg did not exit after 'q'; destroying.");
					this.micCaptureProcess.destroy();
					if (!this.micCaptureProcess.waitFor(3L, TimeUnit.SECONDS)) {
						this.micCaptureProcess.destroyForcibly();
					}
				}
			} catch (InterruptedException var45) {
				Thread.currentThread().interrupt();
				this.micCaptureProcess.destroyForcibly();
			} finally {
				this.micCaptureProcess = null;
			}
		}

		if (this.micCaptureStderrThread != null) {
			try {
				this.micCaptureStderrThread.join(2000L);
			} catch (InterruptedException var43) {
				Thread.currentThread().interrupt();
			}

			this.micCaptureStderrThread = null;
		}

		if (this.ffmpegAudioStderrThread != null) {
			try {
				this.ffmpegAudioStderrThread.join(2000L);
			} catch (InterruptedException var42) {
				Thread.currentThread().interrupt();
			}

			this.ffmpegAudioStderrThread = null;
		}
	}

	private void startMicrophoneCapture(Path outputDirectory) {
		if (this.config.captureAudio && this.config.captureMicrophone) {
			try {
				List<AudioDeviceStatus> candidates = new ArrayList();
				AudioDeviceStatus primary = AudioCapture.detectMicrophoneDevice(this.ffmpegExecutable, this.config.microphoneDevice);
				if (primary != null && primary.available() && !primary.ffmpegArgs().isEmpty()) {
					candidates.add(primary);
				} else {
					RecordableMod.LOGGER
						.info("Microphone capture: configured device '{}' unavailable: {}", this.config.microphoneDevice, primary == null ? "null" : primary.message());
				}

				boolean configuredIsAuto = this.config.microphoneDevice == null
					|| this.config.microphoneDevice.isBlank()
					|| this.config.microphoneDevice.trim().equalsIgnoreCase("auto");
				if (!configuredIsAuto) {
					AudioDeviceStatus auto = AudioCapture.detectMicrophoneDevice(this.ffmpegExecutable, "auto");
					if (auto != null && auto.available() && !auto.ffmpegArgs().isEmpty() && (primary == null || !auto.deviceName().equals(primary.deviceName()))) {
						candidates.add(auto);
					}
				}

				if (candidates.isEmpty()) {
					RecordableMod.LOGGER.warn("Microphone capture requested but no usable input device was found.");
					this.notifyMicWarning("Microphone: no usable input device found. Recording continues without mic.");
					return;
				}

				this.tempMicFile = outputDirectory.resolve("recordable-mic-" + FILE_TIMESTAMP.format(LocalDateTime.now()) + ".wav");

				for (int attempt = 0; attempt < candidates.size(); attempt++) {
					AudioDeviceStatus mic = (AudioDeviceStatus)candidates.get(attempt);
					List<String> cmd = new ArrayList();
					cmd.add(this.ffmpegExecutable);
					cmd.add("-hide_banner");
					cmd.add("-loglevel");
					cmd.add("info");
					cmd.add("-y");
					cmd.add("-thread_queue_size");
					cmd.add("1024");
					cmd.addAll(mic.ffmpegArgs());
					cmd.add("-af");
					StringBuilder micAf = new StringBuilder("highpass=f=80,lowpass=f=18000");
					if (this.config.noiseSuppression) {
						micAf.append(",afftdn=nr=").append(18).append(":nf=").append(-28).append(":tn=1");
						RecordableMod.LOGGER.info("Microphone noise suppression ENABLED (afftdn nr={}dB nf={}dB).", 18, -28);
					}

					cmd.add(micAf.toString());
					cmd.add("-c:a");
					cmd.add("pcm_s16le");
					cmd.add("-ar");
					cmd.add(Integer.toString(this.config.audioSampleRate));
					cmd.add("-ac");
					cmd.add(Integer.toString(this.config.audioChannelCount));
					cmd.add(this.tempMicFile.toAbsolutePath().toString());
					RecordableMod.LOGGER
						.info("Starting microphone capture (attempt {}/{}, device='{}'): {}", new Object[]{attempt + 1, candidates.size(), mic.deviceName(), joinCommand(cmd)});
					ProcessBuilder pb = ffmpegProcess(cmd);
					pb.directory(outputDirectory.toFile());
					pb.redirectErrorStream(true);
					Process proc = pb.start();
					StringBuilder micLog = new StringBuilder();
					Thread logThread = new Thread(() -> {
						try {
							BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8));

							String line;
							try {
								while ((line = reader.readLine()) != null) {
									synchronized (micLog) {
										if (micLog.length() < 4000) {
											micLog.append(line).append('\n');
										}
									}

									RecordableMod.LOGGER.info("[mic-ffmpeg] {}", line);
								}
							} catch (Throwable var8x) {
								try {
									reader.close();
								} catch (Throwable var6x) {
									var8x.addSuppressed(var6x);
								}

								throw var8x;
							}

							reader.close();
						} catch (IOException var9x) {
						}
					}, "Record-able Mic Log");
					logThread.setDaemon(true);
					logThread.start();

					try {
						Thread.sleep(300L);
					} catch (InterruptedException var20) {
						Thread.currentThread().interrupt();
					}

					if (proc.isAlive()) {
						this.micCaptureProcess = proc;
						this.micCaptureStderrThread = logThread;
						this.micRecordingStartNanos = System.nanoTime();
						this.micEnabled = true;
						MicrophoneState.beginRecording(true, this.config.microphonePushToTalk, this.micRecordingStartNanos);
						this.audioDeviceInfo = this.audioDeviceInfo + " + Mic (" + mic.deviceName() + ")";
						RecordableMod.LOGGER.info("Microphone capture started: {} -> {}", mic.deviceName(), this.tempMicFile);
						return;
					}

					int code = -1;

					try {
						code = proc.exitValue();
					} catch (Throwable var19) {
					}

					String why;
					synchronized (micLog) {
						why = micLog.toString().trim();
					}

					RecordableMod.LOGGER
						.warn("Microphone device '{}' failed to open (ffmpeg exit={}). Output:\n{}", new Object[]{mic.deviceName(), code, why.isEmpty() ? "(no output)" : why});

					try {
						logThread.join(500L);
					} catch (InterruptedException var17) {
						Thread.currentThread().interrupt();
					}
				}

				this.micEnabled = false;
				this.tempMicFile = null;
				this.notifyMicWarning("Microphone failed to start (see log). Recording continues without mic.");
			} catch (Throwable var21) {
				RecordableMod.LOGGER.warn("Failed to start microphone capture; continuing without mic.", var21);
				this.micEnabled = false;
				this.tempMicFile = null;
			}
		}
	}

	private void notifyMicWarning(String message) {
		RecordableMod.LOGGER.warn("[mic] {}", message);
	}

	private void tryMuxCapturedAudio() {
		if (this.outputFile != null) {
			boolean hasGame = false;

			try {
				hasGame = this.audioEnabled && this.tempAudioFile != null && Files.exists(this.tempAudioFile, new LinkOption[0]) && Files.size(this.tempAudioFile) > 44L;
			} catch (IOException var30) {
			}

			boolean hasMic = false;

			try {
				hasMic = this.micEnabled && this.tempMicFile != null && Files.exists(this.tempMicFile, new LinkOption[0]) && Files.size(this.tempMicFile) > 44L;
			} catch (IOException var29) {
			}

			if (this.config.captureAudio && this.config.captureMicrophone && !hasMic) {
				long micBytes = -1L;

				try {
					if (this.tempMicFile != null && Files.exists(this.tempMicFile, new LinkOption[0])) {
						micBytes = Files.size(this.tempMicFile);
					}
				} catch (IOException var25) {
				}

				RecordableMod.LOGGER
					.warn(
						"Microphone capture produced no usable audio (micEnabled={}, tempMicFile={}, bytes={}). Check the selected mic device in settings - 'auto' may have found no real input, or the chosen device could not be opened.",
						new Object[]{this.micEnabled, this.tempMicFile, micBytes}
					);
				this.notifyMicWarning("Microphone recorded no audio. Check the selected mic device in settings.");
			}

			if (hasMic) {
				double micMeanDb = this.probeMicMeanVolume(this.tempMicFile);
				if (!Double.isNaN(micMeanDb)) {
					RecordableMod.LOGGER.info("Microphone WAV level check: mean_volume={} dB (device='{}')", micMeanDb, this.config.microphoneDevice);
					if (micMeanDb <= -75.0) {
						if (PlatformUtils.isAndroid()) {
							RecordableMod.LOGGER
								.warn(
									"Microphone captured ONLY SILENCE (mean={} dB). The device '{}' opened and FFmpeg recorded {} bytes, but every sample is silent - so there is nothing to mix into the recording. This is an Android permission/OS-side block, NOT a mod bug. Fix: (1) Android Settings > Apps > your launcher (e.g. PojavLauncher/Zalith) > Permissions: turn ON the Microphone permission. (2) Make sure the device microphone is not muted and no other app is holding it.",
									new Object[]{micMeanDb, this.config.microphoneDevice, this.micFileBytesQuietly()}
								);
							this.notifyMicWarning(
								"Mic captured only silence. On Android: grant the Microphone permission to your launcher in Settings > Apps > Permissions, and make sure the mic is not muted."
							);
						} else if (PlatformUtils.isWindows()) {
							RecordableMod.LOGGER
								.warn(
									"Microphone captured ONLY SILENCE (mean={} dB). The device '{}' opened and FFmpeg recorded {} bytes, but every sample is silent - so there is nothing to mix into the recording. This is a Windows/OS-side block, NOT a mod bug. Fix: (1) Windows Settings > Privacy & security > Microphone: turn ON 'Microphone access' AND 'Let desktop apps access your microphone' (Java must be allowed). (2) Sound settings > Recording > your mic > Properties > Levels: unmute and raise to ~100%. (3) In Razer Synapse, ensure the mic is not muted and no app holds it in Exclusive Mode.",
									new Object[]{micMeanDb, this.config.microphoneDevice, this.micFileBytesQuietly()}
								);
							this.notifyMicWarning(
								"Mic captured only silence. In Windows: Privacy > Microphone, turn ON 'Let desktop apps access your microphone', then unmute/raise the mic level in Sound settings (and in Razer Synapse)."
							);
						} else {
							RecordableMod.LOGGER
								.warn(
									"Microphone captured ONLY SILENCE (mean={} dB). The device '{}' opened and FFmpeg recorded {} bytes, but every sample is silent - so there is nothing to mix into the recording. This is an OS-side block, NOT a mod bug. Fix: check your sound server (PulseAudio/PipeWire) input settings - unmute the microphone source and raise its level, and make sure no other app holds the device exclusively.",
									new Object[]{micMeanDb, this.config.microphoneDevice, this.micFileBytesQuietly()}
								);
							this.notifyMicWarning("Mic captured only silence. Check your PulseAudio/PipeWire input settings: unmute the microphone source and raise its level.");
						}
					}
				}
			}

			if (!hasGame && !hasMic) {
				RecordableMod.LOGGER.warn("No usable audio captured (gameEnabled={}, micEnabled={}). Keeping video-only output.", this.audioEnabled, this.micEnabled);
			} else {
				try {
					int userOffsetMs = RecordableConfig.get().getEffectiveAudioDelay();
					String outputName = this.outputFile.getFileName().toString();
					int dotIndex = outputName.lastIndexOf(46);
					String baseName = dotIndex > 0 ? outputName.substring(0, dotIndex) : outputName;
					String ext = dotIndex > 0 ? outputName.substring(dotIndex) : ".mp4";
					Path muxedOutput = this.outputFile.resolveSibling(baseName + "-muxed" + ext);
					List<String> muxCommand = new ArrayList();
					muxCommand.add(this.ffmpegExecutable);
					muxCommand.add("-nostdin");
					muxCommand.add("-hide_banner");
					muxCommand.add("-loglevel");
					muxCommand.add("info");
					muxCommand.add("-y");
					muxCommand.add("-i");
					muxCommand.add(this.outputFile.toAbsolutePath().toString());
					int nextInput = 1;
					int gameInputIdx = -1;
					int micInputIdx = -1;
					if (hasGame) {
						long startGapMs = 0L;
						if (this.audioRecordingStartNanos > 0L && this.recordingStartNanos > 0L) {
							startGapMs = (this.audioRecordingStartNanos - this.recordingStartNanos) / 1000000L;
						}

						int gameTotalOffsetMs = userOffsetMs - (int)startGapMs;
						double offsetSec = (double)gameTotalOffsetMs / 1000.0;
						RecordableMod.LOGGER
							.info(
								"Game audio: {} bytes, itsoffset={}ms (user={}ms, startGap={}ms)",
								new Object[]{Files.size(this.tempAudioFile), gameTotalOffsetMs, userOffsetMs, startGapMs}
							);
						muxCommand.add("-itsoffset");
						muxCommand.add(String.format(Locale.ROOT, "%.3f", offsetSec));
						muxCommand.add("-i");
						muxCommand.add(this.tempAudioFile.toAbsolutePath().toString());
						gameInputIdx = nextInput++;
					}

					if (hasMic) {
						long micGapMs = 0L;
						if (this.micRecordingStartNanos > 0L && this.recordingStartNanos > 0L) {
							micGapMs = (this.micRecordingStartNanos - this.recordingStartNanos) / 1000000L;
						}

						double micOffsetSec = (double)((long)userOffsetMs + micGapMs) / 1000.0;
						RecordableMod.LOGGER
							.info(
								"Microphone: {} bytes, itsoffset={}ms (user={}ms, micGap={}ms)",
								new Object[]{Files.size(this.tempMicFile), (int)((long)userOffsetMs + micGapMs), userOffsetMs, micGapMs}
							);
						muxCommand.add("-itsoffset");
						muxCommand.add(String.format(Locale.ROOT, "%.3f", micOffsetSec));
						muxCommand.add("-i");
						muxCommand.add(this.tempMicFile.toAbsolutePath().toString());
						micInputIdx = nextInput++;
					}

					muxCommand.add("-map");
					muxCommand.add("0:v:0");
					muxCommand.add("-c:v");
					muxCommand.add("copy");
					this.addAudioCodecArgs(muxCommand);
					String masterFilter = this.buildMasterVolumeFilter();
					if (hasGame && hasMic) {
						double gameVol = (double)Math.max(0, Math.min(200, this.config.gameAudioVolume)) / 100.0;
						double micVol = (double)Math.max(0, Math.min(200, this.config.microphoneVolume)) / 100.0;
						StringBuilder fc = new StringBuilder();
						fc.append(String.format(Locale.ROOT, "[%d:a]volume=%.2f[g];", gameInputIdx, gameVol));
						fc.append(String.format(Locale.ROOT, "[%d:a]%s[m];", micInputIdx, this.buildMicVolumeFilter(micVol)));
						if (this.config.separateAudioTracks) {
							String fcStr = fc.toString();
							if (fcStr.endsWith(";")) {
								fcStr = fcStr.substring(0, fcStr.length() - 1);
							}

							muxCommand.add("-filter_complex");
							muxCommand.add(fcStr);
							muxCommand.add("-map");
							muxCommand.add("[g]");
							muxCommand.add("-map");
							muxCommand.add("[m]");
							muxCommand.add("-metadata:s:a:0");
							muxCommand.add("title=Game");
							muxCommand.add("-metadata:s:a:1");
							muxCommand.add("title=Microphone");
							muxCommand.add("-disposition:a:0");
							muxCommand.add("default");
							RecordableMod.LOGGER.info("Writing SEPARATE audio tracks: game (track 0) + microphone (track 1)");
						} else {
							fc.append("[g][m]amix=inputs=2:duration=longest:dropout_transition=0:normalize=0");
							if (!masterFilter.isEmpty()) {
								fc.append(",").append(masterFilter);
							}

							fc.append("[aout]");
							muxCommand.add("-filter_complex");
							muxCommand.add(fc.toString());
							muxCommand.add("-map");
							muxCommand.add("[aout]");
							RecordableMod.LOGGER.info("Mixing game audio + microphone into single file: gameVol={} micVol={}", gameVol, micVol);
						}
					} else if (hasGame) {
						muxCommand.add("-map");
						muxCommand.add(gameInputIdx + ":a:0");
						StringBuilder af = new StringBuilder(masterFilter);
						if (!this.deferredLoopbackConnection && this.audioRecordingStartNanos <= 0L) {
							if (af.length() > 0) {
								af.append(",");
							}

							af.append("aresample=async=1000");
						}

						if (af.length() > 0) {
							muxCommand.add("-af");
							muxCommand.add(af.toString());
						}

						RecordableMod.LOGGER.info("Muxing game audio only into single file.");
					} else {
						double micVol = (double)Math.max(0, Math.min(200, this.config.microphoneVolume)) / 100.0;
						StringBuilder fc = new StringBuilder();
						fc.append(String.format(Locale.ROOT, "[%d:a]%s", micInputIdx, this.buildMicVolumeFilter(micVol)));
						if (!masterFilter.isEmpty()) {
							fc.append(",").append(masterFilter);
						}

						fc.append("[aout]");
						muxCommand.add("-filter_complex");
						muxCommand.add(fc.toString());
						muxCommand.add("-map");
						muxCommand.add("[aout]");
						RecordableMod.LOGGER.info("Muxing microphone only into single file (no game audio available): micVol={}", micVol);
					}

					muxCommand.add("-shortest");
					muxCommand.add(muxedOutput.toAbsolutePath().toString());
					RecordableMod.LOGGER.info("Muxing audio: {}", joinCommand(muxCommand));
					Process muxProcess = ffmpegProcess(muxCommand).redirectErrorStream(true).start();
					BufferedReader reader = new BufferedReader(new InputStreamReader(muxProcess.getInputStream(), StandardCharsets.UTF_8));

					String muxOutput;
					try {
						muxOutput = (String)reader.lines().collect(Collectors.joining("\n"));
					} catch (Throwable var24) {
						try {
							reader.close();
						} catch (Throwable var21) {
							var24.addSuppressed(var21);
						}

						throw var24;
					}

					reader.close();
					if (!muxProcess.waitFor(30L, TimeUnit.SECONDS)) {
						muxProcess.destroyForcibly();
						RecordableMod.LOGGER.warn("Audio mux timed out. Keeping original video-only file.");
						this.deleteMuxedQuietly(muxedOutput);
						return;
					}

					if (muxProcess.exitValue() != 0) {
						RecordableMod.LOGGER.warn("Audio mux failed with exit code {}: {}", muxProcess.exitValue(), muxOutput);
						this.deleteMuxedQuietly(muxedOutput);
						return;
					}

					boolean replaced = false;
					IOException lastMoveError = null;

					for (int attempt = 1; attempt <= 6 && !replaced; attempt++) {
						try {
							try {
								Files.move(muxedOutput, this.outputFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
							} catch (IOException var23) {
								Files.move(muxedOutput, this.outputFile, StandardCopyOption.REPLACE_EXISTING);
							}

							replaced = true;
						} catch (IOException var27) {
							lastMoveError = var27;

							try {
								Thread.sleep(300L);
							} catch (InterruptedException var26) {
								Thread.currentThread().interrupt();
								break;
							}
						}
					}

					if (replaced) {
						RecordableMod.LOGGER.info("Audio mux completed successfully: {}", this.outputFile);
					} else if (Files.exists(muxedOutput, new LinkOption[0]) && Files.size(muxedOutput) > 0L) {
						RecordableMod.LOGGER
							.warn(
								"Could not replace original with muxed file after retries (locked by another process). Keeping muxed file as the final recording: {}",
								muxedOutput,
								lastMoveError
							);

						try {
							Files.deleteIfExists(this.outputFile);
						} catch (IOException var22) {
						}

						this.outputFile = muxedOutput;
					} else {
						RecordableMod.LOGGER.warn("Audio mux move failed and muxed file is missing; keeping original video-only file.", lastMoveError);
					}

					this.probeAudioLevel();
				} catch (Exception var28) {
					RecordableMod.LOGGER.warn("Failed to mux audio into output.", var28);
				}
			}
		}
	}

	private String buildMasterVolumeFilter() {
		StringBuilder audioFilter = new StringBuilder();
		if (this.config.audioVolume != 100 && this.config.audioVolume >= 0 && this.config.audioVolume <= 200) {
			double volumeFactor = (double)this.config.audioVolume / 100.0;
			audioFilter.append("volume=").append(String.format(Locale.ROOT, "%.2f", volumeFactor));
			RecordableMod.LOGGER.info("Applying user audio volume: {}x ({}%)", volumeFactor, this.config.audioVolume);
		}

		if (this.config.audioVolumeBoostDb > 0 && this.config.audioVolumeBoostDb <= 24) {
			if (audioFilter.length() > 0) {
				audioFilter.append(",");
			}

			audioFilter.append("volume=").append(this.config.audioVolumeBoostDb).append("dB");
			RecordableMod.LOGGER.info("Applying audio boost: +{} dB", this.config.audioVolumeBoostDb);
		}

		return audioFilter.toString();
	}

	private String buildMicVolumeFilter(double micVol) {
		int userOffsetMs = RecordableConfig.get().getEffectiveAudioDelay();
		List<double[]> pttIntervals = null;

		try {
			pttIntervals = MicrophoneState.getPushToTalkIntervalsSeconds(this.micRecordingStartNanos);
		} catch (Throwable var16) {
		}

		if (pttIntervals == null) {
			return String.format(Locale.ROOT, "volume=%.2f", micVol);
		} else if (pttIntervals.isEmpty()) {
			RecordableMod.LOGGER.info("Push-to-Talk: key never held during recording; microphone muted.");
			this.notifyMicWarning(
				"Push-to-Talk was ON but the PTT key was never held, so the mic is silent. Hold the Push-to-Talk key while talking, or turn Push-to-Talk OFF for always-on mic."
			);
			return "volume=0";
		} else {
			long pttMicGapMs = this.micRecordingStartNanos > 0L && this.recordingStartNanos > 0L
				? (this.micRecordingStartNanos - this.recordingStartNanos) / 1000000L
				: 0L;
			double pttMicOffsetSec = (double)((long)userOffsetMs + pttMicGapMs) / 1000.0;
			StringBuilder gate = new StringBuilder();

			for (int i = 0; i < pttIntervals.size(); i++) {
				double[] iv = (double[])pttIntervals.get(i);
				double s = Math.max(0.0, iv[0] + pttMicOffsetSec);
				double e = Math.max(0.0, iv[1] + pttMicOffsetSec);
				if (i > 0) {
					gate.append("+");
				}

				gate.append(String.format(Locale.ROOT, "between(t,%.3f,%.3f)", s, e));
			}

			RecordableMod.LOGGER.info("Push-to-Talk: gating microphone to {} held interval(s).", pttIntervals.size());
			return String.format(Locale.ROOT, "volume=volume='%.2f*(%s)':eval=frame", micVol, gate);
		}
	}

	private double probeMicMeanVolume(Path micWav) {
		if (micWav == null) {
			return Double.NaN;
		} else {
			try {
				List<String> cmd = List.of(
					this.ffmpegExecutable, "-nostdin", "-hide_banner", "-i", micWav.toAbsolutePath().toString(), "-af", "volumedetect", "-vn", "-f", "null", "-"
				);
				Process probe = ffmpegProcess(cmd).redirectErrorStream(true).start();
				BufferedReader reader = new BufferedReader(new InputStreamReader(probe.getInputStream(), StandardCharsets.UTF_8));

				String output;
				try {
					output = (String)reader.lines().collect(Collectors.joining("\n"));
				} catch (Throwable var13) {
					try {
						reader.close();
					} catch (Throwable var12) {
						var13.addSuppressed(var12);
					}

					throw var13;
				}

				reader.close();
				if (probe.waitFor(10L, TimeUnit.SECONDS)) {
					for (String line : output.split("\n")) {
						if (line.contains("mean_volume")) {
							String[] parts = line.split("mean_volume:\\s*");
							if (parts.length > 1) {
								try {
									return Double.parseDouble(parts[1].trim().split("\\s+")[0]);
								} catch (NumberFormatException var11) {
									return Double.NaN;
								}
							}
						}
					}
				} else {
					probe.destroyForcibly();
				}
			} catch (Exception var14) {
				RecordableMod.LOGGER.debug("Mic level probe failed (non-critical).", var14);
			}

			return Double.NaN;
		}
	}

	private long micFileBytesQuietly() {
		try {
			if (this.tempMicFile != null && Files.exists(this.tempMicFile, new LinkOption[0])) {
				return Files.size(this.tempMicFile);
			}
		} catch (IOException var2) {
		}

		return -1L;
	}

	private void probeAudioLevel() {
		if (this.outputFile != null) {
			try {
				List<String> cmd = List.of(
					this.ffmpegExecutable,
					"-nostdin",
					"-hide_banner",
					"-i",
					this.outputFile.toAbsolutePath().toString(),
					"-map",
					"0:a:0",
					"-af",
					"volumedetect",
					"-vn",
					"-f",
					"null",
					"-"
				);
				Process probe = ffmpegProcess(cmd).redirectErrorStream(true).start();
				BufferedReader reader = new BufferedReader(new InputStreamReader(probe.getInputStream(), StandardCharsets.UTF_8));

				String output;
				try {
					output = (String)reader.lines().collect(Collectors.joining("\n"));
				} catch (Throwable var14) {
					try {
						reader.close();
					} catch (Throwable var12) {
						var14.addSuppressed(var12);
					}

					throw var14;
				}

				reader.close();
				if (probe.waitFor(10L, TimeUnit.SECONDS)) {
					for (String line : output.split("\n")) {
						if (line.contains("mean_volume")) {
							RecordableMod.LOGGER.info("Audio level check: {}", line.trim());

							try {
								String[] parts = line.split("mean_volume:\\s*");
								if (parts.length > 1) {
									double meanDb = Double.parseDouble(parts[1].trim().split("\\s+")[0]);
									if (meanDb < -70.0) {
										String fix;
										if (PlatformUtils.isAndroid()) {
											fix = "Grant the microphone permission to your launcher (Android Settings > Apps > permissions) and make sure the device microphone is not muted or turned down.";
										} else if (PlatformUtils.isWindows()) {
											fix = "Check Stereo Mix volume in Windows Sound Settings > Recording > Properties > Levels. Set Stereo Mix level to 100%.";
										} else {
											fix = "Check that your system audio loopback/monitor source is enabled and unmuted (for example a PulseAudio/PipeWire monitor device) and that its level is turned up.";
										}

										RecordableMod.LOGGER.warn("Audio appears silent or near-silent (mean={} dB). {}", meanDb, fix);
									}
								}
							} catch (NumberFormatException var13) {
							}
						}

						if (line.contains("max_volume")) {
							RecordableMod.LOGGER.info("Audio level check: {}", line.trim());
						}
					}
				} else {
					probe.destroyForcibly();
				}
			} catch (Exception var15) {
				RecordableMod.LOGGER.debug("Audio level probe failed (non-critical).", var15);
			}
		}
	}

	private void deleteTempAudioFileQuietly() {
		if (this.tempAudioFile != null) {
			try {
				Files.deleteIfExists(this.tempAudioFile);
			} catch (IOException var3) {
			}

			this.tempAudioFile = null;
		}

		if (this.tempMicFile != null) {
			try {
				Files.deleteIfExists(this.tempMicFile);
			} catch (IOException var2) {
			}

			this.tempMicFile = null;
		}
	}

	private void deleteMuxedQuietly(Path muxedOutput) {
		if (muxedOutput != null) {
			try {
				if (Files.deleteIfExists(muxedOutput)) {
					RecordableMod.LOGGER.debug("Removed leftover muxed intermediate: {}", muxedOutput);
				}
			} catch (IOException var3) {
			}
		}
	}

	public AudioDeviceStatus getDetectedAudioStatus() {
		return this.detectedAudioStatus;
	}

	private static String normalizeConfiguredAudioDevice(String configuredDevice) {
		if (configuredDevice == null) {
			return "auto";
		} else {
			String normalized = configuredDevice.trim();
			return !normalized.isEmpty() && !"openal".equalsIgnoreCase(normalized) ? normalized : "auto";
		}
	}

	private VideoEncoder resolveVideoEncoder() {
		VideoEncoder requested = this.config.encoder == null ? VideoEncoder.SOFTWARE : this.config.encoder;
		if (requested == VideoEncoder.SOFTWARE) {
			return requested;
		} else {
			List<VideoEncoder> available = detectAvailableEncoders();
			if (available.contains(requested)) {
				return requested;
			} else {
				RecordableMod.LOGGER.warn("Requested encoder {} is not available in this FFmpeg build. Falling back to software x264.", requested.displayName);
				return VideoEncoder.SOFTWARE;
			}
		}
	}

	public static List<VideoEncoder> detectAvailableEncoders() {
		List<VideoEncoder> available = new ArrayList();
		available.add(VideoEncoder.SOFTWARE);
		String softwareCodec = resolveSoftwareVideoCodec();
		RecordableMod.LOGGER.info("SOFTWARE encoder will use ffmpeg codec '{}' on this device.", softwareCodec);
		if (testEncoder("h264_nvenc")) {
			available.add(VideoEncoder.NVIDIA);
		}

		if (testEncoder("h264_amf")) {
			available.add(VideoEncoder.AMD);
		}

		if (testEncoder("h264_qsv")) {
			available.add(VideoEncoder.INTEL);
		}

		return available;
	}

	public String getSelectedVideoCodec() {
		return this.selectedVideoCodec;
	}

	public static String getCachedSoftwareCodec() {
		return cachedSoftwareCodec;
	}

	public boolean isEncoderStuck() {
		return this.encoderStuck;
	}

	private static String resolveSoftwareVideoCodec() {
		String cached = cachedSoftwareCodec;
		if (cached != null) {
			return cached;
		} else {
			List<String> chain = new ArrayList();
			if (PlatformUtils.isAndroid()) {
				ExecMethod exec = FfmpegBundleManager.getExecMethod();
				if (exec == ExecMethod.DIRECT) {
					chain.add("h264_mediacodec");
				} else {
					RecordableMod.LOGGER
						.info(
							"Skipping h264_mediacodec: FFmpeg exec method is {} (MediaCodec needs DIRECT exec for binder access; falling back to a software encoder instead).", exec
						);
				}
			}

			chain.add("libx264");
			chain.add("mpeg4");
			chain.add("libxvid");
			String chosen = null;

			for (String c : chain) {
				if (testEncoder(c)) {
					chosen = c;
					break;
				}
			}

			if (chosen == null) {
				chosen = "mpeg4";
				RecordableMod.LOGGER.warn("No software video encoder could be probed; defaulting to mpeg4.");
			} else {
				RecordableMod.LOGGER.info("Resolved software video codec: {}", chosen);
			}

			cachedSoftwareCodec = chosen;
			return chosen;
		}
	}

	private static boolean codecSupportsPreset(String codec) {
		if (codec == null) {
			return false;
		} else {
			switch (codec) {
				case "libx264":
				case "libx265":
				case "h264_nvenc":
				case "hevc_nvenc":
				case "h264_amf":
				case "h264_qsv":
					return true;
				default:
					return false;
			}
		}
	}

	private static int mpeg4QualityFromCrf(int crf) {
		int q = Math.round((float)crf / 2.0F);
		if (q < 1) {
			q = 1;
		}

		if (q > 15) {
			q = 15;
		}

		return q;
	}

	public static List<AudioEncoder> detectAvailableAudioEncoders() {
		List<AudioEncoder> available = new ArrayList();

		for (AudioEncoder encoder : AudioEncoder.values()) {
			if (testAudioEncoder(encoder.ffmpegCodec)) {
				available.add(encoder);
			}
		}

		if (available.isEmpty()) {
			available.add(AudioEncoder.AAC);
		}

		return available;
	}

	private static boolean testAudioEncoder(String codec) {
		return testEncoder(codec);
	}

	private static boolean testEncoder(String codec) {
		FFmpegEncoder.FfmpegStatus status = detectFfmpeg();
		if (!status.found()) {
			return false;
		} else {
			Set<String> encoders = new HashSet();
			Process process = null;

			boolean var17;
			try {
				process = ffmpegProcess(status.executable(), "-hide_banner", "-encoders").redirectErrorStream(true).start();
				BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

				String line;
				try {
					while ((line = reader.readLine()) != null) {
						String lowered = line.toLowerCase(Locale.ROOT).trim();
						if (!lowered.isEmpty()) {
							String[] parts = lowered.split("\\s+");
							if (parts.length >= 2 && parts[0].length() == 6) {
								encoders.add(parts[1]);
							}
						}
					}
				} catch (Throwable var14) {
					try {
						reader.close();
					} catch (Throwable var13) {
						var14.addSuppressed(var13);
					}

					throw var14;
				}

				reader.close();
				if (process.waitFor(5L, TimeUnit.SECONDS)) {
					return encoders.contains(codec.toLowerCase(Locale.ROOT));
				}

				process.destroyForcibly();
				var17 = false;
			} catch (Exception var15) {
				RecordableMod.LOGGER.debug("Failed to probe encoder {}", codec, var15);
				return false;
			} finally {
				if (process != null) {
					process.destroy();
				}
			}

			return var17;
		}
	}

	private static String multiplyBitrateString(String bitrateStr, int factor) {
		if (bitrateStr != null && !bitrateStr.isBlank()) {
			String s = bitrateStr.trim();

			try {
				if (s.endsWith("M") || s.endsWith("m")) {
					double val = Double.parseDouble(s.substring(0, s.length() - 1));
					return Math.round(val * (double)factor) + "M";
				} else if (!s.endsWith("k") && !s.endsWith("K")) {
					long val = Long.parseLong(s);
					return Long.toString(val * (long)factor);
				} else {
					long val = Long.parseLong(s.substring(0, s.length() - 1));
					return val * (long)factor + "k";
				}
			} catch (NumberFormatException var5) {
				return bitrateStr;
			}
		} else {
			return bitrateStr;
		}
	}

	private static String scaleBitrateString(String bitrateStr, double factor) {
		if (bitrateStr != null && !bitrateStr.isBlank()) {
			String s = bitrateStr.trim();

			try {
				if (s.endsWith("M") || s.endsWith("m")) {
					double val = Double.parseDouble(s.substring(0, s.length() - 1));
					return Math.round(val * factor) + "M";
				} else if (!s.endsWith("k") && !s.endsWith("K")) {
					double val = Double.parseDouble(s);
					return Long.toString(Math.round(val * factor));
				} else {
					double val = Double.parseDouble(s.substring(0, s.length() - 1));
					return Math.round(val * factor) + "k";
				}
			} catch (NumberFormatException var6) {
				return bitrateStr;
			}
		} else {
			return bitrateStr;
		}
	}

	private void addWebmVp9Args(List<String> args, String bitrate) {
		String vp9Codec = "libvpx-vp9";
		this.selectedVideoCodec = vp9Codec;
		RecordableMod.LOGGER
			.info("Video encoder selected: WebM/VP9 (ffmpeg codec '{}', real-time mode, exec method {})", vp9Codec, FfmpegBundleManager.getExecMethod());
		args.add("-c:v");
		args.add(vp9Codec);
		args.add("-deadline");
		args.add("realtime");
		args.add("-cpu-used");
		args.add("8");
		args.add("-row-mt");
		args.add("1");
		args.add("-b:v");
		args.add(bitrate);
		args.add("-g");
		args.add(Integer.toString(Math.max(1, this.fps)));
		args.add("-threads");
		args.add(Integer.toString(Math.max(1, Runtime.getRuntime().availableProcessors() - 2)));
		args.add("-pix_fmt");
		args.add("yuv420p");
	}

	private void addVideoCodecArgs(List<String> args, VideoEncoder selectedEncoder) {
		String format = this.config.getFormat();
		String bitrate = this.config.resolveBitrate(this.width, this.height);
		if (this.config.isWebmFormat()) {
			this.addWebmVp9Args(args, bitrate);
		} else {
			VideoEncoder encoderToUse = selectedEncoder == null ? VideoEncoder.SOFTWARE : selectedEncoder;
			String codec = encoderToUse == VideoEncoder.SOFTWARE ? resolveSoftwareVideoCodec() : encoderToUse.ffmpegCodec;
			this.selectedVideoCodec = codec;
			String encoderDisplayLabel = encoderToUse == VideoEncoder.SOFTWARE ? "Software (" + codec + ")" : encoderToUse.displayName;
			RecordableMod.LOGGER
				.info("Video encoder selected: {} (ffmpeg codec '{}', exec method {})", new Object[]{encoderDisplayLabel, codec, FfmpegBundleManager.getExecMethod()});
			args.add("-c:v");
			args.add(codec);
			switch (encoderToUse) {
				case NVIDIA:
					args.add("-preset");
					args.add("p4");
					args.add("-tune");
					args.add("ull");
					args.add("-profile:v");
					args.add("high");
					args.add("-rc");
					args.add("vbr");
					args.add("-cq");
					args.add(Integer.toString(this.config.getX264Crf()));
					args.add("-b:v");
					args.add(bitrate);
					args.add("-bf");
					args.add("0");
					break;
				case AMD:
					args.add("-quality");
					args.add("speed");
					args.add("-rc");
					args.add("vbr_latency");
					args.add("-qp_i");
					args.add(Integer.toString(this.config.getX264Crf()));
					args.add("-qp_p");
					args.add(Integer.toString(this.config.getX264Crf()));
					args.add("-b:v");
					args.add(bitrate);
					break;
				case INTEL:
					args.add("-preset");
					args.add("veryfast");
					args.add("-global_quality");
					args.add(Integer.toString(this.config.getX264Crf()));
					args.add("-b:v");
					args.add(bitrate);
					break;
				case SOFTWARE:
					if (codec.equals("libx264") || codec.equals("libx265")) {
						args.add("-preset");
						args.add("ultrafast");
						args.add("-tune");
						args.add("zerolatency");
						args.add("-crf");
						args.add(Integer.toString(this.config.getX264Crf()));
						args.add("-b:v");
						args.add(bitrate);
						args.add("-threads");
						args.add(Integer.toString(Math.max(1, Runtime.getRuntime().availableProcessors() - 2)));
						String maxrateBitrate = multiplyBitrateString(bitrate, 2);
						String bufsizeBitrate = multiplyBitrateString(bitrate, 3);
						args.add("-maxrate");
						args.add(maxrateBitrate);
						args.add("-bufsize");
						args.add(bufsizeBitrate);
					} else if (codec.equals("h264_mediacodec")) {
						args.add("-b:v");
						args.add(bitrate);
						args.add("-bf");
						args.add("0");
						args.add("-g");
						args.add(Integer.toString(Math.max(1, this.fps)));
					} else {
						String mpeg4Bitrate = scaleBitrateString(bitrate, 2.5);
						String mpeg4Maxrate = scaleBitrateString(mpeg4Bitrate, 1.5);
						String mpeg4Bufsize = scaleBitrateString(mpeg4Bitrate, 2.0);
						int mpeg4Gop = Math.max(1, this.fps);
						int mpeg4Qmax = mpeg4QualityFromCrf(this.config.getX264Crf());
						args.add("-b:v");
						args.add(mpeg4Bitrate);
						args.add("-maxrate");
						args.add(mpeg4Maxrate);
						args.add("-bufsize");
						args.add(mpeg4Bufsize);
						args.add("-g");
						args.add(Integer.toString(mpeg4Gop));
						args.add("-flags");
						args.add("+aic+mv4");
						args.add("-qmax");
						args.add(Integer.toString(mpeg4Qmax));
						args.add("-threads");
						args.add(Integer.toString(Math.max(1, Runtime.getRuntime().availableProcessors() - 2)));
						RecordableMod.LOGGER
							.info(
								"mpeg4 software encode: {}x{} @ {}fps, target bitrate {} (maxrate {}, bufsize {}), GOP {}, qmax {}",
								new Object[]{this.width, this.height, this.fps, mpeg4Bitrate, mpeg4Maxrate, mpeg4Bufsize, mpeg4Gop, mpeg4Qmax}
							);
					}
			}

			args.add("-pix_fmt");
			args.add("yuv420p");
			String containerFormat = format == null ? "" : format.toLowerCase(Locale.ROOT);
			if (containerFormat.equals("mp4") || containerFormat.equals("mov")) {
				args.add("-movflags");
				args.add("+faststart");
			}
		}
	}

	private void addAudioCodecArgs(List<String> args) {
		this.config.validateAudioEncoderCompatibility();
		AudioEncoder selected = this.config.audioEncoder == null ? AudioEncoder.AAC : this.config.audioEncoder;
		args.add("-c:a");
		args.add(selected.ffmpegCodec);
		args.add("-ar");
		args.add(Integer.toString(this.config.audioSampleRate));
		args.add("-ac");
		args.add(Integer.toString(this.config.audioChannelCount));
		if (!selected.isLossless()) {
			args.add("-b:a");
			args.add(this.config.audioBitrateKbps + "k");
		}

		switch (selected) {
			case OPUS:
				args.add("-compression_level");
				args.add("10");
				break;
			case MP3:
				args.add("-q:a");
				args.add("2");
				break;
			case FLAC:
				args.add("-compression_level");
				args.add("8");
		}
	}

	public boolean isAudioEnabled() {
		return this.audioEnabled;
	}

	public String getAudioDeviceInfo() {
		return this.audioDeviceInfo;
	}

	private void notifyUser(String message) {
		try {
			class_310 client = class_310.method_1551();
			if (client != null) {
				client.execute(() -> {
					if (client.field_1724 != null) {
						client.field_1724.method_7353(class_2561.method_43470("⚠ Record-able: " + message), false);
					}
				});
			}
		} catch (Throwable var3) {
		}
	}

	private void watchdogLoop() {
		try {
			while (this.acceptingFrames.get()) {
				Thread.sleep(500L);
				if (this.encoderStuck) {
					return;
				}

				long started = this.writerStartedAtNanos.get();
				if (started > 0L) {
					long elapsedMs = (System.nanoTime() - started) / 1000000L;
					if (elapsedMs >= 6000L) {
						long written = this.writtenFrames.get();
						long produced = this.parsedFrameCount;
						if (written >= 1L && produced == 0L) {
							this.encoderStuck = true;
							this.lastError = "Encoder '"
								+ this.selectedVideoCodec
								+ "' produced no output after "
								+ written
								+ " input frames in "
								+ elapsedMs
								+ "ms (stuck/deadlocked).";
							RecordableMod.LOGGER
								.error(
									"WATCHDOG: {} FFmpeg exec method={}. Forcibly stopping the stuck encoder so the recording does not hang and the failure is surfaced.",
									this.lastError,
									FfmpegBundleManager.getExecMethod()
								);
							this.notifyUser(
								"Recording failed: the '"
									+ this.selectedVideoCodec
									+ "' video encoder is not working on this device. Open Record-able settings and choose a different encoder, or update FFmpeg."
							);
							this.acceptingFrames.set(false);

							try {
								if (this.process != null) {
									this.process.destroyForcibly();
								}
							} catch (Exception var10) {
							}

							return;
						}
					}
				}
			}
		} catch (InterruptedException var11) {
			Thread.currentThread().interrupt();
		}
	}

	private void writerLoop() {
		int flushCounter = 0;
		long framesWrittenSinceLog = 0L;
		long duplicatedSinceLog = 0L;
		long totalDuplicated = 0L;
		long logWindowStarted = System.nanoTime();
		long frameIntervalUs = Math.max(1L, 1000000L / (long)Math.max(1, this.fps));
		int MAX_DUP_PER_PACKET = 10;

		try {
			while (this.acceptingFrames.get() || !this.queue.isEmpty()) {
				if (this.process != null && !this.process.isAlive()) {
					int exitCode = this.process.exitValue();
					RecordableMod.LOGGER.error("FFmpeg process died unexpectedly (exit code {}). Stopping writer. Last FFmpeg error: {}", exitCode, this.lastError);
					this.acceptingFrames.set(false);
					break;
				}

				FFmpegEncoder.FramePacket packet = (FFmpegEncoder.FramePacket)this.queue.poll(100L, TimeUnit.MILLISECONDS);
				if (packet != null) {
					long currentWritten = this.writtenFrames.get();
					long ts = packet.timestampMs();
					int framesToWrite = 1;
					if (ts > 0L) {
						long tsUs = ts * 1000L;
						long expectedFrameIndex = tsUs / frameIntervalUs;
						long gap = expectedFrameIndex - currentWritten;
						if (gap > 1L) {
							int duplicates = (int)Math.min(gap - 1L, 10L);
							framesToWrite += duplicates;
							totalDuplicated += (long)duplicates;
							duplicatedSinceLog += (long)duplicates;
						}
					}

					for (int i = 0; i < framesToWrite; i++) {
						this.ffmpegStdin.write(packet.data());
						this.writtenFrames.incrementAndGet();
						framesWrittenSinceLog++;
						flushCounter++;
					}

					FrameBufferPool.getInstance().release(packet.data());
					long now = System.nanoTime();
					if (now - logWindowStarted >= 1000000000L) {
						RecordableMod.LOGGER
							.debug(
								"Encoder: {} fps | Queue: {}/{} | Written: {} | Dup: {} (total {})",
								new Object[]{framesWrittenSinceLog, this.queue.size(), this.queueCapacity, this.writtenFrames.get(), duplicatedSinceLog, totalDuplicated}
							);
						long written = this.writtenFrames.get();
						if (!this.perfWarningLogged && written >= (long)this.fps * 5L && (double)totalDuplicated > (double)written * 0.3) {
							this.perfWarningLogged = true;
							long pct = Math.round(100.0 * (double)totalDuplicated / (double)written);
							RecordableMod.LOGGER
								.warn(
									"Recording performance: {}% of frames are duplicates - the game is rendering slower than the {} FPS recording target. For smoother recordings, lower your in-game graphics settings (render distance, graphics quality) or reduce the recording resolution/FPS.",
									pct,
									this.fps
								);
						}

						framesWrittenSinceLog = 0L;
						duplicatedSinceLog = 0L;
						logWindowStarted = now;
					}

					if (flushCounter >= 2) {
						this.ffmpegStdin.flush();
						flushCounter = 0;
					}
				}
			}

			this.ffmpegStdin.flush();
		} catch (InterruptedException var30) {
			Thread.currentThread().interrupt();
		} catch (IOException var31) {
			this.lastError = var31.getMessage() == null ? var31.toString() : var31.getMessage();
			boolean processDead = this.process != null && !this.process.isAlive();
			if (processDead) {
				RecordableMod.LOGGER
					.error("Write to FFmpeg stdin failed because the process exited (code {}). Last FFmpeg message: {}", this.process.exitValue(), this.lastError);
			} else {
				RecordableMod.LOGGER.warn("Failed while writing raw video to FFmpeg.", var31);
			}

			this.acceptingFrames.set(false);
		} finally {
			this.closeStdinQuietly();
			RecordableMod.LOGGER.info("Writer loop finished. Total frames written: {} (duplicated: {})", this.writtenFrames.get(), totalDuplicated);
		}
	}

	private void stderrLoop(InputStream errorStream) {
		try {
			BufferedInputStream inputStream = new BufferedInputStream(errorStream);

			try {
				StringBuilder currentLine = new StringBuilder();

				int value;
				while ((value = inputStream.read()) != -1) {
					if (value != 10 && value != 13) {
						currentLine.append((char)value);
					} else {
						String line = currentLine.toString();
						if (!line.isBlank()) {
							this.parseAndLogFfmpegLine(line);
						}

						currentLine.setLength(0);
					}
				}

				if (!currentLine.isEmpty()) {
					this.parseAndLogFfmpegLine(currentLine.toString());
				}
			} catch (Throwable var7) {
				try {
					inputStream.close();
				} catch (Throwable var6) {
					var7.addSuppressed(var6);
				}

				throw var7;
			}

			inputStream.close();
		} catch (IOException var8) {
			if (this.acceptingFrames.get()) {
				RecordableMod.LOGGER.debug("FFmpeg stderr reader ended.", var8);
			}
		}
	}

	private void parseAndLogFfmpegLine(String line) {
		if (line != null && !line.isBlank()) {
			boolean isProgressLine = line.contains("frame=") && line.contains("size=");
			if (isProgressLine) {
				Matcher sizeMatcher = SIZE_PATTERN.matcher(line);
				if (sizeMatcher.find()) {
					try {
						long sizeValue = Long.parseLong(sizeMatcher.group(1));
						String unit = sizeMatcher.group(2).toLowerCase(Locale.ROOT);
						if (unit.startsWith("k")) {
							this.parsedSizeBytes = sizeValue * 1024L;
						} else if (unit.startsWith("m")) {
							this.parsedSizeBytes = sizeValue * 1024L * 1024L;
						} else if (unit.startsWith("g")) {
							this.parsedSizeBytes = sizeValue * 1024L * 1024L * 1024L;
						} else {
							this.parsedSizeBytes = sizeValue;
						}
					} catch (NumberFormatException var13) {
					}
				}

				Matcher frameMatcher = FRAME_PATTERN.matcher(line);
				if (frameMatcher.find()) {
					try {
						this.parsedFrameCount = Long.parseLong(frameMatcher.group(1));
					} catch (NumberFormatException var12) {
					}
				}

				Matcher fpsMatcher = FPS_PATTERN.matcher(line);
				if (fpsMatcher.find()) {
					try {
						this.parsedFps = Double.parseDouble(fpsMatcher.group(1));
					} catch (NumberFormatException var11) {
					}
				}

				Matcher bitrateMatcher = BITRATE_PATTERN.matcher(line);
				if (bitrateMatcher.find()) {
					try {
						double br = Double.parseDouble(bitrateMatcher.group(1));
						String brUnit = bitrateMatcher.group(2).toLowerCase(Locale.ROOT);
						if (brUnit.startsWith("m")) {
							this.parsedBitrate = br * 1000.0;
						} else {
							this.parsedBitrate = br;
						}
					} catch (NumberFormatException var10) {
					}
				}

				Matcher speedMatcher = SPEED_PATTERN.matcher(line);
				if (speedMatcher.find()) {
					this.parsedSpeed = speedMatcher.group(1);
				}

				if (this.parsedFrameCount > 0L && this.parsedFrameCount % 300L == 0L) {
					RecordableMod.LOGGER
						.debug(
							"FFmpeg progress: frame={} size={} fps={} bitrate={}kbits/s speed={}",
							new Object[]{
								this.parsedFrameCount,
								RecordingManager.formatBytes(this.parsedSizeBytes),
								String.format(Locale.ROOT, "%.1f", this.parsedFps),
								String.format(Locale.ROOT, "%.1f", this.parsedBitrate),
								this.parsedSpeed
							}
						);
				}
			} else {
				this.lastError = line;
				RecordableMod.LOGGER.warn("FFmpeg: {}", line);
			}
		}
	}

	private void closeStdinQuietly() {
		if (this.ffmpegStdin != null) {
			try {
				this.ffmpegStdin.close();
			} catch (IOException var2) {
			}
		}
	}

	private void closeProcessStreamsQuietly() {
		if (this.process != null) {
			try {
				this.process.getInputStream().close();
			} catch (IOException var3) {
			}

			try {
				this.process.getErrorStream().close();
			} catch (IOException var2) {
			}
		}
	}

	public static FFmpegEncoder.FfmpegStatus detectFfmpeg() {
		long now = System.currentTimeMillis();
		FFmpegEncoder.FfmpegStatus cached = cachedStatus;
		if (cached != null && now - cachedStatusAtMs < 5000L) {
			return cached;
		} else {
			List<String> diag = new ArrayList();
			RecordableConfig config = null;

			try {
				config = RecordableConfig.get();
			} catch (Exception var11) {
			}

			String manual = config == null ? null : config.ffmpegPath;
			if (manual != null && !manual.isBlank()) {
				FFmpegEncoder.FfmpegStatus s = probeFfmpeg(manual.trim());
				if (s.found()) {
					diag.add("[OK] Manual path (settings): " + manual.trim() + "  ->  " + s.version());
					lastDiagnostics = diag;
					RecordableMod.LOGGER.info("[FFmpeg] Using manual settings path: {}", s.executable());
					cachedStatus = s;
					cachedStatusAtMs = now;
					return s;
				}

				diag.add("[FAIL] Manual path (settings): " + manual.trim() + "  ->  " + s.error());
			} else {
				diag.add("[--] Manual path (settings): not set");
			}

			String configured = System.getenv("RECORDABLE_FFMPEG_PATH");
			if (configured != null && !configured.isBlank()) {
				FFmpegEncoder.FfmpegStatus userStatus = probeFfmpeg(configured.trim());
				if (userStatus.found()) {
					diag.add("[OK] Env RECORDABLE_FFMPEG_PATH: " + configured.trim() + "  ->  " + userStatus.version());
					lastDiagnostics = diag;
					RecordableMod.LOGGER.info("[FFmpeg] Using env RECORDABLE_FFMPEG_PATH: {}", userStatus.executable());
					cachedStatus = userStatus;
					cachedStatusAtMs = now;
					return userStatus;
				}

				diag.add("[FAIL] Env RECORDABLE_FFMPEG_PATH: " + configured.trim() + "  ->  " + userStatus.error());
			} else {
				diag.add("[--] Env RECORDABLE_FFMPEG_PATH: not set");
			}

			boolean useBundled = config == null || config.useBundledFfmpeg;
			if (useBundled) {
				String bundledPath = FfmpegBundleManager.getBundledFfmpegPath();
				if (bundledPath != null) {
					FFmpegEncoder.FfmpegStatus bundledStatus = probeFfmpeg(bundledPath);
					if (bundledStatus.found()) {
						diag.add("[OK] Bundled/downloaded: " + bundledPath + "  ->  " + bundledStatus.version());
						lastDiagnostics = diag;
						RecordableMod.LOGGER.info("[FFmpeg] Using bundled FFmpeg: {}", bundledStatus.executable());
						cachedStatus = bundledStatus;
						cachedStatusAtMs = now;
						return bundledStatus;
					}

					diag.add("[FAIL] Bundled/downloaded: " + bundledPath + "  ->  " + bundledStatus.error());
				} else {
					diag.add("[--] Bundled/downloaded: none installed");
				}
			} else {
				diag.add("[--] Bundled/downloaded: disabled in settings");
			}

			for (String cand : commonFfmpegCandidates()) {
				FFmpegEncoder.FfmpegStatus s = probeFfmpeg(cand);
				if (s.found()) {
					diag.add("[OK] Known location: " + cand + "  ->  " + s.version());
					lastDiagnostics = diag;
					RecordableMod.LOGGER.info("[FFmpeg] Using known-location FFmpeg: {}", s.executable());
					cachedStatus = s;
					cachedStatusAtMs = now;
					return s;
				}

				diag.add("[FAIL] Known location: " + cand + "  ->  " + s.error());
			}

			FFmpegEncoder.FfmpegStatus systemStatus = probeFfmpeg("ffmpeg");
			if (systemStatus.found()) {
				diag.add("[OK] System PATH (ffmpeg): " + systemStatus.version());
				RecordableMod.LOGGER.info("[FFmpeg] Using system PATH FFmpeg: {}", systemStatus.version());
			} else {
				diag.add("[FAIL] System PATH (ffmpeg): " + systemStatus.error());
				RecordableMod.LOGGER.warn("[FFmpeg] No FFmpeg found via any method (manual, env, bundled, known locations, PATH).");
			}

			lastDiagnostics = diag;
			cachedStatus = systemStatus;
			cachedStatusAtMs = now;
			return systemStatus;
		}
	}

	private static List<String> commonFfmpegCandidates() {
		List<String> list = new ArrayList();
		list.add("/data/data/com.termux/files/usr/bin/ffmpeg");
		list.add("/usr/bin/ffmpeg");
		list.add("/usr/local/bin/ffmpeg");
		list.add("/bin/ffmpeg");
		list.add("/system/bin/ffmpeg");
		list.add("/opt/homebrew/bin/ffmpeg");
		return list;
	}

	public static List<String> getLastDiagnostics() {
		List<String> snapshot = lastDiagnostics;
		return snapshot == null ? new ArrayList() : new ArrayList(snapshot);
	}

	public static void invalidateDetectionCache() {
		cachedStatus = null;
		cachedStatusAtMs = 0L;
	}

	public static String testFfmpegVerbose(String explicitPath) {
		StringBuilder sb = new StringBuilder();
		if (explicitPath != null && !explicitPath.isBlank()) {
			String exe = explicitPath.trim();
			sb.append("Testing: ").append(exe).append('\n');
			FFmpegEncoder.FfmpegStatus s = probeFfmpeg(exe);
			if (s.found()) {
				sb.append("RESULT: OK\n").append(s.version()).append('\n');
			} else {
				sb.append("RESULT: FAILED\n").append(s.error()).append('\n');
			}

			return sb.toString();
		} else {
			invalidateDetectionCache();
			FFmpegEncoder.FfmpegStatus s = detectFfmpeg();
			sb.append(s.found() ? "RESULT: FFmpeg FOUND\n" : "RESULT: FFmpeg NOT FOUND\n");
			if (s.found()) {
				sb.append("Using: ").append(s.executable()).append('\n');
				sb.append(s.version()).append('\n');
			}

			sb.append('\n').append("Paths tried:\n");

			for (String d : getLastDiagnostics()) {
				sb.append("  ").append(d).append('\n');
			}

			return sb.toString();
		}
	}

	private static FFmpegEncoder.FfmpegStatus probeFfmpeg(String executable) {
		try {
			Process process = ffmpegProcess(executable, "-version").redirectErrorStream(true).start();
			boolean exited = process.waitFor(3L, TimeUnit.SECONDS);
			if (!exited) {
				process.destroyForcibly();
				return FFmpegEncoder.FfmpegStatus.notFound(executable, "Timed out while probing FFmpeg.");
			} else {
				String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
				String firstLine = (String)output.lines().findFirst().orElse("ffmpeg version unknown");
				return process.exitValue() == 0
					? new FFmpegEncoder.FfmpegStatus(true, executable, firstLine, "")
					: FFmpegEncoder.FfmpegStatus.notFound(executable, firstLine.isBlank() ? "FFmpeg probe failed." : firstLine);
			}
		} catch (Exception var5) {
			String message = var5.getMessage() == null ? var5.toString() : var5.getMessage();
			return FFmpegEncoder.FfmpegStatus.notFound(executable, "FFmpeg not found: " + message);
		}
	}

	private static String joinCommand(List<String> command) {
		StringJoiner joiner = new StringJoiner(" ");

		for (String part : command) {
			if (part.indexOf(32) >= 0) {
				joiner.add("\"" + part.replace("\"", "\\\"") + "\"");
			} else {
				joiner.add(part);
			}
		}

		return joiner.toString();
	}

	public static enum EnqueueResult {
		QUEUED,
		REJECTED;
	}

	public static record FfmpegStatus(boolean found, String executable, String version, String error) {
		private static FFmpegEncoder.FfmpegStatus notFound(String executable, String error) {
			return new FFmpegEncoder.FfmpegStatus(false, executable, "", error == null ? "" : error);
		}

		public String displayText() {
			return this.found ? "Found: " + this.version : "Not found: " + this.error;
		}
	}

	private static record FramePacket(byte[] data, long timestampMs) {
	}
}
