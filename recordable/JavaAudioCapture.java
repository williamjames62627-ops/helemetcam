package dev.recordable;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import javax.sound.sampled.AudioFormat.Encoding;
import javax.sound.sampled.Mixer.Info;

public final class JavaAudioCapture {
	private static final int DEFAULT_SAMPLE_RATE = 44100;
	private static final int DEFAULT_SAMPLE_SIZE_BITS = 16;
	private static final int DEFAULT_CHANNELS = 2;
	private static final int BUFFER_SIZE_FRAMES = 4096;
	private final int sampleRate;
	private final int sampleSizeBits;
	private final int channels;
	private final Path outputWavFile;
	private final String preferredDevice;
	private final AtomicBoolean capturing = new AtomicBoolean(false);
	private volatile Thread captureThread;
	private volatile TargetDataLine dataLine;
	private volatile JavaAudioCapture.AudioDeviceInfo activeDevice;
	private volatile String lastError = "";
	private volatile long capturedBytes;

	public JavaAudioCapture(int sampleRate, int sampleSizeBits, int channels, Path outputWavFile, String preferredDevice) {
		this.sampleRate = sampleRate > 0 ? sampleRate : '걄';
		this.sampleSizeBits = sampleSizeBits > 0 ? sampleSizeBits : 16;
		this.channels = channels > 0 ? channels : 2;
		this.outputWavFile = outputWavFile;
		this.preferredDevice = preferredDevice != null && !preferredDevice.isBlank() ? preferredDevice.trim() : "auto";
	}

	public JavaAudioCapture(Path outputWavFile) {
		this(44100, 16, 2, outputWavFile, "auto");
	}

	public boolean start() {
		if (this.capturing.get()) {
			RecordableMod.LOGGER.warn("JavaAudioCapture: already capturing.");
			return true;
		} else {
			AudioFormat[] formatsToTry = this.buildFormatCandidates();
			RecordableMod.LOGGER.info("JavaAudioCapture: will try {} audio format candidates.", formatsToTry.length);
			TargetDataLine line = null;
			AudioFormat successFormat = null;

			for (AudioFormat format : formatsToTry) {
				RecordableMod.LOGGER.debug("JavaAudioCapture: trying format: {}", format);

				try {
					line = this.openAudioDevice(format);
					if (line != null) {
						successFormat = format;
						RecordableMod.LOGGER.info("JavaAudioCapture: successfully opened device with format: {}", format);
						break;
					}
				} catch (Exception var9) {
					RecordableMod.LOGGER.debug("JavaAudioCapture: format {} failed: {}", format, var9.getMessage());
				}
			}

			if (line != null && successFormat != null) {
				this.dataLine = line;
				this.capturing.set(true);
				this.capturedBytes = 0L;
				TargetDataLine captureLine = line;
				AudioFormat captureFormat = successFormat;
				this.captureThread = new Thread(() -> this.captureLoop(captureLine, captureFormat), "Record-able Java Audio Capture");
				this.captureThread.setDaemon(true);
				this.captureThread.setPriority(6);
				this.captureThread.start();
				RecordableMod.LOGGER
					.info(
						"JavaAudioCapture started: device='{}' format={} output={}",
						new Object[]{this.activeDevice != null ? this.activeDevice.name() : "unknown", captureFormat, this.outputWavFile}
					);
				return true;
			} else {
				this.lastError = "No suitable audio capture device found for any supported format.";
				RecordableMod.LOGGER.warn("JavaAudioCapture: {}. Tried {} formats. Falling back to video-only.", this.lastError, formatsToTry.length);
				return false;
			}
		}
	}

	private AudioFormat[] buildFormatCandidates() {
		int[][] candidates = new int[][]{{this.sampleRate, this.channels}, {48000, 2}, {44100, 2}, {48000, 1}, {44100, 1}, {22050, 1}};
		LinkedHashSet<String> seen = new LinkedHashSet();
		List<AudioFormat> formats = new ArrayList();

		for (int[] c : candidates) {
			String key = c[0] + "-" + c[1];
			if (seen.add(key)) {
				int bits = this.sampleSizeBits > 0 ? this.sampleSizeBits : 16;
				formats.add(new AudioFormat(Encoding.PCM_SIGNED, (float)c[0], bits, c[1], bits / 8 * c[1], (float)c[0], false));
			}
		}

		return (AudioFormat[])formats.toArray(new AudioFormat[0]);
	}

	public void stop() {
		this.capturing.set(false);
		TargetDataLine line = this.dataLine;
		if (line != null) {
			try {
				line.stop();
			} catch (Exception var6) {
				RecordableMod.LOGGER.debug("JavaAudioCapture: error stopping data line.", var6);
			}

			try {
				line.close();
			} catch (Exception var5) {
				RecordableMod.LOGGER.debug("JavaAudioCapture: error closing data line.", var5);
			}

			this.dataLine = null;
		}

		Thread thread = this.captureThread;
		if (thread != null) {
			try {
				thread.join(8000L);
			} catch (InterruptedException var4) {
				Thread.currentThread().interrupt();
			}

			if (thread.isAlive()) {
				RecordableMod.LOGGER.warn("JavaAudioCapture: capture thread still alive after join timeout.");
			}

			this.captureThread = null;
		}

		RecordableMod.LOGGER.info("JavaAudioCapture stopped. Captured {} bytes to {}", this.capturedBytes, this.outputWavFile);
	}

	public boolean isCapturing() {
		return this.capturing.get();
	}

	public JavaAudioCapture.AudioDeviceInfo getActiveDevice() {
		return this.activeDevice;
	}

	public String getLastError() {
		return this.lastError;
	}

	public Path getOutputFile() {
		return this.outputWavFile;
	}

	public long getCapturedBytes() {
		return this.capturedBytes;
	}

	private void captureLoop(TargetDataLine line, AudioFormat format) {
		int bufferSize = 4096 * format.getFrameSize();
		byte[] buffer = new byte[bufferSize];

		try {
			Files.createDirectories(this.outputWavFile.getParent());
		} catch (Exception var11) {
			this.lastError = "Could not create audio output directory: " + var11.getMessage();
			RecordableMod.LOGGER.warn("JavaAudioCapture: {}", this.lastError, var11);
			return;
		}

		try {
			RandomAccessFile raf = new RandomAccessFile(this.outputWavFile.toFile(), "rw");

			try {
				this.writeWavHeader(raf, format, 0L);
				long dataStart = raf.getFilePointer();

				try {
					while (this.capturing.get() && line.isOpen()) {
						int bytesRead = line.read(buffer, 0, buffer.length);
						if (bytesRead > 0) {
							raf.write(buffer, 0, bytesRead);
							this.capturedBytes += (long)bytesRead;
						} else if (bytesRead == -1) {
							break;
						}
					}
				} catch (Exception var12) {
					RecordableMod.LOGGER.debug("JavaAudioCapture: capture read ended (line closed): {}", var12.getMessage());
				}

				long dataSize = raf.getFilePointer() - dataStart;
				this.updateWavHeader(raf, dataSize);
				RecordableMod.LOGGER.info("JavaAudioCapture: WAV header updated. dataSize={} bytes, capturedBytes={}", dataSize, this.capturedBytes);
			} catch (Throwable var13) {
				try {
					raf.close();
				} catch (Throwable var10) {
					var13.addSuppressed(var10);
				}

				throw var13;
			}

			raf.close();
		} catch (Exception var14) {
			this.lastError = "Audio capture error: " + var14.getMessage();
			RecordableMod.LOGGER.warn("JavaAudioCapture: capture loop failed.", var14);
		}
	}

	private void writeWavHeader(RandomAccessFile raf, AudioFormat format, long dataSize) throws IOException {
		int channels = format.getChannels();
		int sampleRate = (int)format.getSampleRate();
		int bitsPerSample = format.getSampleSizeInBits();
		int byteRate = sampleRate * channels * (bitsPerSample / 8);
		short blockAlign = (short)(channels * (bitsPerSample / 8));
		raf.writeBytes("RIFF");
		writeIntLE(raf, (int)(36L + dataSize));
		raf.writeBytes("WAVE");
		raf.writeBytes("fmt ");
		writeIntLE(raf, 16);
		writeShortLE(raf, (short)1);
		writeShortLE(raf, (short)channels);
		writeIntLE(raf, sampleRate);
		writeIntLE(raf, byteRate);
		writeShortLE(raf, blockAlign);
		writeShortLE(raf, (short)bitsPerSample);
		raf.writeBytes("data");
		writeIntLE(raf, (int)dataSize);
	}

	private void updateWavHeader(RandomAccessFile raf, long dataSize) throws IOException {
		raf.seek(4L);
		writeIntLE(raf, (int)(36L + dataSize));
		raf.seek(40L);
		writeIntLE(raf, (int)dataSize);
	}

	private static void writeIntLE(RandomAccessFile raf, int value) throws IOException {
		raf.write(value & 0xFF);
		raf.write(value >> 8 & 0xFF);
		raf.write(value >> 16 & 0xFF);
		raf.write(value >> 24 & 0xFF);
	}

	private static void writeShortLE(RandomAccessFile raf, short value) throws IOException {
		raf.write(value & 255);
		raf.write(value >> 8 & 0xFF);
	}

	private TargetDataLine openAudioDevice(AudioFormat format) {
		if (!"auto".equalsIgnoreCase(this.preferredDevice)) {
			TargetDataLine line = this.tryOpenDevice(this.preferredDevice, format);
			if (line != null) {
				return line;
			}

			RecordableMod.LOGGER.warn("JavaAudioCapture: preferred device '{}' not found or failed. Trying auto-detect.", this.preferredDevice);
		}

		List<JavaAudioCapture.AudioDeviceInfo> devices = detectAudioDevices();
		RecordableMod.LOGGER.info("JavaAudioCapture: detected {} audio capture devices.", devices.size());

		for (JavaAudioCapture.AudioDeviceInfo device : devices) {
			if (device.isLoopback()) {
				TargetDataLine line = this.tryOpenMixer(device, format);
				if (line != null) {
					return line;
				}
			}
		}

		for (JavaAudioCapture.AudioDeviceInfo devicex : devices) {
			if (!devicex.isLoopback()) {
				TargetDataLine line = this.tryOpenMixer(devicex, format);
				if (line != null) {
					return line;
				}
			}
		}

		return null;
	}

	private TargetDataLine tryOpenDevice(String deviceName, AudioFormat format) {
		Info[] mixerInfos = AudioSystem.getMixerInfo();

		for (Info info : mixerInfos) {
			if (info.getName().toLowerCase(Locale.ROOT).contains(deviceName.toLowerCase(Locale.ROOT))) {
				try {
					Mixer mixer = AudioSystem.getMixer(info);
					javax.sound.sampled.Line.Info lineInfo = new javax.sound.sampled.DataLine.Info(TargetDataLine.class, format);
					if (mixer.isLineSupported(lineInfo)) {
						TargetDataLine line = (TargetDataLine)mixer.getLine(lineInfo);
						line.open(format, 4096 * format.getFrameSize());
						line.start();
						this.activeDevice = new JavaAudioCapture.AudioDeviceInfo(info.getName(), info, isLoopbackDevice(info), getPlatformId());
						RecordableMod.LOGGER.info("JavaAudioCapture: opened device '{}' with format {}", info.getName(), format);
						return line;
					}
				} catch (LineUnavailableException var11) {
					RecordableMod.LOGGER.debug("JavaAudioCapture: format not supported on device '{}': {}", info.getName(), var11.getMessage());
				} catch (Exception var12) {
					RecordableMod.LOGGER.debug("JavaAudioCapture: failed to open device '{}'", info.getName(), var12);
				}
			}
		}

		return null;
	}

	private TargetDataLine tryOpenMixer(JavaAudioCapture.AudioDeviceInfo device, AudioFormat format) {
		try {
			Mixer mixer = AudioSystem.getMixer(device.mixerInfo());
			javax.sound.sampled.Line.Info lineInfo = new javax.sound.sampled.DataLine.Info(TargetDataLine.class, format);
			if (!mixer.isLineSupported(lineInfo)) {
				return null;
			} else {
				TargetDataLine line = (TargetDataLine)mixer.getLine(lineInfo);
				line.open(format, 4096 * format.getFrameSize());
				line.start();
				this.activeDevice = device;
				RecordableMod.LOGGER.info("JavaAudioCapture: opened device '{}' (loopback={}) with format {}", new Object[]{device.name(), device.isLoopback(), format});
				return line;
			}
		} catch (LineUnavailableException var6) {
			RecordableMod.LOGGER.debug("JavaAudioCapture: format {} not supported on device '{}': {}", new Object[]{format, device.name(), var6.getMessage()});
			return null;
		} catch (Exception var7) {
			RecordableMod.LOGGER.debug("JavaAudioCapture: failed to open device '{}'", device.name(), var7);
			return null;
		}
	}

	public static List<JavaAudioCapture.AudioDeviceInfo> detectAudioDevices() {
		List<JavaAudioCapture.AudioDeviceInfo> devices = new ArrayList();
		String platform = getPlatformId();
		AudioFormat[] testFormats = new AudioFormat[]{
			new AudioFormat(Encoding.PCM_SIGNED, 48000.0F, 16, 2, 4, 48000.0F, false),
			new AudioFormat(Encoding.PCM_SIGNED, 44100.0F, 16, 2, 4, 44100.0F, false),
			new AudioFormat(Encoding.PCM_SIGNED, 48000.0F, 16, 1, 2, 48000.0F, false),
			new AudioFormat(Encoding.PCM_SIGNED, 44100.0F, 16, 1, 2, 44100.0F, false),
			new AudioFormat(Encoding.PCM_SIGNED, 22050.0F, 16, 1, 2, 22050.0F, false)
		};
		Info[] mixerInfos = AudioSystem.getMixerInfo();
		Set<String> seenMixers = new HashSet();

		for (Info info : mixerInfos) {
			if (!seenMixers.contains(info.getName())) {
				try {
					Mixer mixer = AudioSystem.getMixer(info);

					for (AudioFormat fmt : testFormats) {
						javax.sound.sampled.DataLine.Info targetLineInfo = new javax.sound.sampled.DataLine.Info(TargetDataLine.class, fmt);
						if (mixer.isLineSupported(targetLineInfo)) {
							boolean loopback = isLoopbackDevice(info);
							devices.add(new JavaAudioCapture.AudioDeviceInfo(info.getName(), info, loopback, platform));
							seenMixers.add(info.getName());
							break;
						}
					}
				} catch (Exception var16) {
				}
			}
		}

		devices.sort((a, b) -> Boolean.compare(b.isLoopback(), a.isLoopback()));
		return devices;
	}

	public static List<String> getAvailableDeviceNames() {
		List<String> names = new ArrayList();

		for (JavaAudioCapture.AudioDeviceInfo device : detectAudioDevices()) {
			names.add(device.displayName());
		}

		return names;
	}

	public static boolean isLoopbackAvailable() {
		return detectAudioDevices().stream().anyMatch(JavaAudioCapture.AudioDeviceInfo::isLoopback);
	}

	public static boolean isAnyCaptureDeviceAvailable() {
		return !detectAudioDevices().isEmpty();
	}

	private static boolean isLoopbackDevice(Info info) {
		if (info == null) {
			return false;
		} else {
			String name = info.getName().toLowerCase(Locale.ROOT);
			String desc = (info.getDescription() != null ? info.getDescription() : "").toLowerCase(Locale.ROOT);
			String platform = getPlatformId();

			return switch (platform) {
				case "windows" -> isWindowsLoopback(name, desc);
				case "linux" -> isLinuxLoopback(name, desc);
				case "macos" -> isMacLoopback(name, desc);
				default -> false;
			};
		}
	}

	private static boolean isWindowsLoopback(String name, String desc) {
		return name.contains("stereo mix")
			|| name.contains("wave out mix")
			|| name.contains("what u hear")
			|| name.contains("what you hear")
			|| name.contains("loopback")
			|| name.contains("cable output")
			|| name.contains("voicemeeter")
			|| name.contains("vb-audio")
			|| desc.contains("stereo mix")
			|| desc.contains("loopback");
	}

	private static boolean isLinuxLoopback(String name, String desc) {
		return name.contains("monitor") || name.contains("loopback") || desc.contains("monitor of");
	}

	private static boolean isMacLoopback(String name, String desc) {
		return name.contains("blackhole") || name.contains("soundflower") || name.contains("loopback") || name.contains("multi-output") || desc.contains("loopback");
	}

	private static String getPlatformId() {
		try {
			return PlatformUtils.getPlatformId();
		} catch (Exception var2) {
			String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
			if (os.contains("win")) {
				return "windows";
			} else if (os.contains("mac") || os.contains("darwin")) {
				return "macos";
			} else {
				return !os.contains("nux") && !os.contains("nix") ? "unknown" : "linux";
			}
		}
	}

	public static record AudioDeviceInfo(String name, Info mixerInfo, boolean isLoopback, String platform) {
		public String displayName() {
			return this.name + (this.isLoopback ? " (Loopback)" : "");
		}
	}
}
