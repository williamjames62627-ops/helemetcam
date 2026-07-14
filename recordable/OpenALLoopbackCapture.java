package dev.recordable;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicReference;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.AudioFormat.Encoding;
import javax.sound.sampled.DataLine.Info;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.SOFTLoopback;

public final class OpenALLoopbackCapture {
	public static final int SAMPLE_RATE = 48000;
	public static final int CHANNELS = 2;
	public static final int BITS_PER_SAMPLE = 16;
	private static final int RENDER_INTERVAL_MS = 10;
	private static final int SAMPLES_PER_RENDER = 480;
	private static final int BYTES_PER_RENDER = 1920;
	private static volatile OpenALLoopbackCapture instance;
	private volatile long loopbackDevice;
	private volatile boolean running;
	private Thread renderThread;
	private SourceDataLine speakerLine;
	private final AtomicReference<OutputStream> recordingStream = new AtomicReference(null);
	private static final double BYTES_PER_MS = 192.0;
	private final Deque<OpenALLoopbackCapture.TimedAudio> rollingAudio = new ArrayDeque();
	private volatile boolean rollingEnabled = false;
	private volatile long rollingRetentionMs = 0L;
	private long rollingBytes = 0L;

	private OpenALLoopbackCapture() {
	}

	public static OpenALLoopbackCapture getInstance() {
		if (instance == null) {
			synchronized (OpenALLoopbackCapture.class) {
				if (instance == null) {
					instance = new OpenALLoopbackCapture();
				}
			}
		}

		return instance;
	}

	public static boolean isLoopbackSupported() {
		try {
			return ALC10.alcIsExtensionPresent(0L, "ALC_SOFT_loopback");
		} catch (Throwable var1) {
			RecordableMod.LOGGER.debug("ALC_SOFT_loopback extension check failed", var1);
			return false;
		}
	}

	public long openLoopbackDevice() {
		try {
			long device = SOFTLoopback.alcLoopbackOpenDeviceSOFT((CharSequence)null);
			if (device == 0L) {
				RecordableMod.LOGGER.error("alcLoopbackOpenDeviceSOFT returned 0 (failed)");
				return 0L;
			} else {
				boolean supported = SOFTLoopback.alcIsRenderFormatSupportedSOFT(device, 48000, 5377, 5122);
				if (!supported) {
					RecordableMod.LOGGER.error("Loopback format not supported (48kHz stereo 16-bit)");
					ALC10.alcCloseDevice(device);
					return 0L;
				} else {
					this.loopbackDevice = device;
					RecordableMod.LOGGER.info("OpenAL loopback device opened successfully: {}", device);
					return device;
				}
			}
		} catch (Throwable var4) {
			RecordableMod.LOGGER.error("Failed to open loopback device", var4);
			return 0L;
		}
	}

	public int[] getContextAttributes() {
		return new int[]{6544, 5377, 6545, 5122, 4103, 48000, 0};
	}

	public void startRenderThread() {
		if (!this.running) {
			if (this.loopbackDevice == 0L) {
				RecordableMod.LOGGER.warn("Cannot start render thread: no loopback device");
			} else {
				this.running = true;
				this.renderThread = new Thread(this::renderLoop, "Record-able Audio Render");
				this.renderThread.setDaemon(true);
				this.renderThread.setPriority(9);
				this.renderThread.start();
				RecordableMod.LOGGER.info("Loopback render thread started");
			}
		}
	}

	public void stopRenderThread() {
		this.running = false;
		if (this.renderThread != null) {
			try {
				this.renderThread.join(2000L);
			} catch (InterruptedException var2) {
				Thread.currentThread().interrupt();
			}

			this.renderThread = null;
		}

		this.closeSpeakerLine();
		RecordableMod.LOGGER.info("Loopback render thread stopped");
	}

	public void setRecordingStream(OutputStream stream) {
		this.recordingStream.set(stream);
	}

	public void enableRollingBuffer(long retentionMs) {
		long clamped = Math.max(1000L, Math.min(30000L, retentionMs));
		synchronized (this.rollingAudio) {
			this.rollingRetentionMs = clamped;
			this.rollingEnabled = true;
		}
	}

	public void disableRollingBuffer() {
		synchronized (this.rollingAudio) {
			this.rollingEnabled = false;
			this.rollingAudio.clear();
			this.rollingBytes = 0L;
		}
	}

	public boolean isRollingBufferEnabled() {
		return this.rollingEnabled;
	}

	private void appendRollingAudio(byte[] audioBytes) {
		long now = System.currentTimeMillis();
		synchronized (this.rollingAudio) {
			if (this.rollingEnabled) {
				byte[] copy = (byte[])audioBytes.clone();
				this.rollingAudio.addLast(new OpenALLoopbackCapture.TimedAudio(copy, now));
				this.rollingBytes += (long)copy.length;
				long cutoff = now - this.rollingRetentionMs;

				while (!this.rollingAudio.isEmpty()) {
					OpenALLoopbackCapture.TimedAudio oldest = (OpenALLoopbackCapture.TimedAudio)this.rollingAudio.peekFirst();
					if (oldest == null || oldest.tsMs() >= cutoff) {
						break;
					}

					OpenALLoopbackCapture.TimedAudio removed = (OpenALLoopbackCapture.TimedAudio)this.rollingAudio.pollFirst();
					if (removed != null) {
						this.rollingBytes = this.rollingBytes - (long)removed.pcm().length;
					}
				}
			}
		}
	}

	public byte[] extractAudio(long startMs, long endMs) {
		synchronized (this.rollingAudio) {
			if (!this.rollingAudio.isEmpty() && endMs > startMs) {
				ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(1024, (int)((double)(endMs - startMs) * 192.0)));

				for (OpenALLoopbackCapture.TimedAudio chunk : this.rollingAudio) {
					if (chunk.tsMs() >= startMs && chunk.tsMs() <= endMs) {
						out.write(chunk.pcm(), 0, chunk.pcm().length);
					}
				}

				byte[] result = out.toByteArray();
				return result.length > 0 ? result : null;
			} else {
				return null;
			}
		}
	}

	public boolean isActive() {
		return this.running && this.loopbackDevice != 0L;
	}

	public boolean hasLoopbackDevice() {
		return this.loopbackDevice != 0L;
	}

	private void renderLoop() {
		RecordableMod.LOGGER.info("Render loop starting: {}Hz {}ch {}bit, {} samples/render, {} bytes/render", new Object[]{48000, 2, 16, 480, 1920});
		ByteBuffer renderBuffer = ByteBuffer.allocateDirect(1920);
		renderBuffer.order(ByteOrder.nativeOrder());
		byte[] audioBytes = new byte[1920];
		if (PlatformUtils.isAndroid()) {
			RecordableMod.LOGGER.info("Android: skipping Java Sound speaker monitor (unsupported on this platform); recording audio is unaffected");
		} else if (!this.openSpeakerLine()) {
			RecordableMod.LOGGER.error("Failed to open speaker line, audio will be silent");
		}

		long startNanos = System.nanoTime();
		long samplesRendered = 0L;

		while (this.running) {
			try {
				long device = this.loopbackDevice;
				if (device == 0L) {
					Thread.sleep(50L);
					startNanos = System.nanoTime();
					samplesRendered = 0L;
				} else {
					long expectedNanos = startNanos + samplesRendered * 1000000000L / 48000L;
					long nowNanos = System.nanoTime();
					long waitNanos = expectedNanos - nowNanos;
					if (waitNanos > 1000000L) {
						Thread.sleep(waitNanos / 1000000L, (int)(waitNanos % 1000000L));
					} else if (waitNanos < -500000000L) {
						startNanos = System.nanoTime();
						samplesRendered = 0L;
					}

					renderBuffer.clear();
					SOFTLoopback.alcRenderSamplesSOFT(device, renderBuffer, 480);
					samplesRendered += 480L;
					renderBuffer.rewind();
					renderBuffer.get(audioBytes);
					if (this.rollingEnabled) {
						this.appendRollingAudio(audioBytes);
					}

					OutputStream stream = (OutputStream)this.recordingStream.get();
					if (stream != null) {
						try {
							stream.write(audioBytes);
						} catch (Throwable var17) {
							RecordableMod.LOGGER.debug("Recording stream write failed", var17);
						}
					}

					if (this.speakerLine != null && this.speakerLine.isOpen()) {
						this.speakerLine.write(audioBytes, 0, audioBytes.length);
					}
				}
			} catch (InterruptedException var19) {
				Thread.currentThread().interrupt();
				break;
			} catch (Throwable var20) {
				RecordableMod.LOGGER.warn("Render loop error", var20);

				try {
					Thread.sleep(50L);
				} catch (InterruptedException var18) {
					Thread.currentThread().interrupt();
					break;
				}
			}
		}

		RecordableMod.LOGGER.info("Render loop exited");
	}

	private boolean openSpeakerLine() {
		try {
			AudioFormat format = new AudioFormat(Encoding.PCM_SIGNED, 48000.0F, 16, 2, 4, 48000.0F, false);
			Info info = new Info(SourceDataLine.class, format);
			if (!AudioSystem.isLineSupported(info)) {
				RecordableMod.LOGGER.warn("Java Sound does not support format: {}", format);
				return false;
			} else {
				this.speakerLine = (SourceDataLine)AudioSystem.getLine(info);
				int bufferSize = 15360;
				this.speakerLine.open(format, bufferSize);
				this.speakerLine.start();
				RecordableMod.LOGGER.info("Java Sound speaker line opened: {}", format);
				return true;
			}
		} catch (Throwable var4) {
			RecordableMod.LOGGER.error("Failed to open Java Sound speaker line", var4);
			this.speakerLine = null;
			return false;
		}
	}

	private void closeSpeakerLine() {
		if (this.speakerLine != null) {
			try {
				this.speakerLine.stop();
				this.speakerLine.close();
			} catch (Throwable var2) {
			}

			this.speakerLine = null;
		}
	}

	public void shutdown() {
		this.stopRenderThread();
		this.disableRollingBuffer();
		this.recordingStream.set(null);
		this.loopbackDevice = 0L;
		instance = null;
	}

	private static record TimedAudio(byte[] pcm, long tsMs) {
	}
}
