package dev.recordable;

import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALC11;

public final class OpenALAudioCapture {
	private static final int DEFAULT_QUEUE_CAPACITY = 120;
	private final int sampleRate;
	private final int channelCount;
	private final int bitsPerSample;
	private final int samplesPerRead;
	private final int openAlFormat;
	private final BlockingQueue<byte[]> audioQueue;
	private long captureDevice;
	private Thread captureThread;
	private volatile boolean capturing;

	public OpenALAudioCapture(int sampleRate, int channelCount) {
		this(sampleRate, channelCount, 16, Math.max(256, sampleRate / 10), 120);
	}

	public OpenALAudioCapture(int sampleRate, int channelCount, int bitsPerSample, int samplesPerRead, int queueCapacity) {
		this.sampleRate = sampleRate <= 0 ? '뮀' : sampleRate;
		this.channelCount = channelCount == 1 ? 1 : 2;
		this.bitsPerSample = bitsPerSample <= 0 ? 16 : bitsPerSample;
		this.samplesPerRead = Math.max(128, samplesPerRead);
		this.openAlFormat = this.channelCount == 1 ? 4353 : 4355;
		this.audioQueue = new LinkedBlockingQueue(Math.max(16, queueCapacity));
	}

	public boolean start() {
		if (this.capturing) {
			return true;
		} else {
			try {
				RecordableMod.LOGGER
					.info(
						"Starting OpenAL audio capture: sampleRate={}Hz channels={} bits={} samplesPerRead={}",
						new Object[]{this.sampleRate, this.channelCount, this.bitsPerSample, this.samplesPerRead}
					);
				this.captureDevice = ALC11.alcCaptureOpenDevice((ByteBuffer)null, this.sampleRate, this.openAlFormat, this.sampleRate * 2);
				if (this.captureDevice == 0L) {
					RecordableMod.LOGGER.error("OpenAL capture device could not be opened.");
					return false;
				} else {
					ALC11.alcCaptureStart(this.captureDevice);
					this.capturing = true;
					this.captureThread = new Thread(this::captureLoop, "Record-able OpenAL Capture");
					this.captureThread.setDaemon(true);
					this.captureThread.start();
					return true;
				}
			} catch (Throwable var2) {
				RecordableMod.LOGGER.error("Failed to start OpenAL audio capture", var2);
				this.stop();
				return false;
			}
		}
	}

	private void captureLoop() {
		ShortBuffer sampleBuffer = BufferUtils.createShortBuffer(this.samplesPerRead * this.channelCount);

		while (this.capturing) {
			try {
				int available = ALC10.alcGetInteger(this.captureDevice, 786);
				if (available < this.samplesPerRead) {
					Thread.sleep(5L);
				} else {
					sampleBuffer.clear();
					ALC11.alcCaptureSamples(this.captureDevice, sampleBuffer, this.samplesPerRead);
					byte[] audioData = new byte[this.samplesPerRead * this.channelCount * 2];
					sampleBuffer.flip();

					for (int byteIndex = 0; byteIndex < audioData.length; byteIndex += 2) {
						short sample = sampleBuffer.get();
						audioData[byteIndex] = (byte)(sample & 255);
						audioData[byteIndex + 1] = (byte)(sample >>> 8 & 0xFF);
					}

					if (!this.audioQueue.offer(audioData)) {
						this.audioQueue.poll();
						this.audioQueue.offer(audioData);
					}
				}
			} catch (InterruptedException var6) {
				Thread.currentThread().interrupt();
				break;
			} catch (Throwable var7) {
				RecordableMod.LOGGER.warn("OpenAL capture loop error", var7);
				break;
			}
		}

		RecordableMod.LOGGER.info("OpenAL capture loop stopped.");
	}

	public byte[] getNextAudioFrame() {
		return (byte[])this.audioQueue.poll();
	}

	public byte[] getNextAudioFrame(long timeoutMs) throws InterruptedException {
		return timeoutMs <= 0L ? (byte[])this.audioQueue.poll() : (byte[])this.audioQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
	}

	public int getQueueSize() {
		return this.audioQueue.size();
	}

	public boolean isCapturing() {
		return this.capturing;
	}

	public void stop() {
		this.capturing = false;
		if (this.captureThread != null) {
			try {
				this.captureThread.join(2000L);
			} catch (InterruptedException var4) {
				Thread.currentThread().interrupt();
			}

			this.captureThread = null;
		}

		if (this.captureDevice != 0L) {
			try {
				ALC11.alcCaptureStop(this.captureDevice);
			} catch (Throwable var3) {
			}

			try {
				ALC11.alcCaptureCloseDevice(this.captureDevice);
			} catch (Throwable var2) {
			}

			this.captureDevice = 0L;
		}
	}

	public int getSampleRate() {
		return this.sampleRate;
	}

	public int getChannels() {
		return this.channelCount;
	}

	public int getBitsPerSample() {
		return this.bitsPerSample;
	}
}
