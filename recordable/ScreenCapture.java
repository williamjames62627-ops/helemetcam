package dev.recordable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import net.minecraft.class_310;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

public final class ScreenCapture implements AutoCloseable {
	private static final int BYTES_PER_PIXEL = 3;
	private final int outputWidth;
	private final int outputHeight;
	private final int outputByteSize;
	private final int[] pboIds = new int[2];
	private int sourceWidth;
	private int sourceHeight;
	private int sourceByteSize;
	private int pboWriteIndex;
	private boolean hasPendingPboFrame;
	private boolean pboSupported = !PlatformUtils.isAndroid();
	private static final int MAX_PBO_MAP_FAILURES = 5;
	private int consecutivePboMapFailures = 0;
	private ByteBuffer fallbackReadBuffer;
	private byte[] rawConvertScratch;
	private final boolean glesRgbaReadback = PlatformUtils.isAndroid();
	private final int glReadFormat = this.glesRgbaReadback ? 6408 : '胠';
	private final int sourceChannels = this.glesRgbaReadback ? 4 : 3;
	private boolean readbackDiagnosticsLogged = false;
	private static final int READ_SOURCE_AUTO = 0;
	private static final int READ_SOURCE_MAIN_FBO = 1;
	private static final int READ_SOURCE_BACK_BUFFER = 2;
	private static final int READ_SOURCE_MAIN_TEXTURE = 3;
	private static final int READ_SOURCE_COUNT = 4;
	private static final int BLACK_RECOVERY_THRESHOLD = 15;
	private int readSourceMode = PlatformUtils.isAndroid() ? 3 : 0;
	private boolean textureReadbackDiagnosticsLogged = false;
	private int lastSubmittedReadFbo;
	private long totalFramesProduced;
	private long totalBlackFrames;
	private int consecutiveBlackFrames;
	private int blackRecoverySwitches;
	private int lastWindowWidth;
	private int lastWindowHeight;
	private int renderTargetWidth = -1;
	private int renderTargetHeight = -1;
	private boolean sizeMismatchLogged;

	public ScreenCapture(int outputWidth, int outputHeight) {
		this.outputWidth = Math.max(2, outputWidth);
		this.outputHeight = Math.max(2, outputHeight);
		this.outputByteSize = this.outputWidth * this.outputHeight * 3;
		if (!this.pboSupported) {
			RecordableMod.LOGGER
				.info(
					"Android platform detected: PBO screen capture disabled, using synchronous glReadPixels readback (glMapBuffer is unreliable on GLES translation layers)."
				);
		}
	}

	public synchronized ScreenCapture.CapturedFrame captureFrame() {
		class_310 client = class_310.method_1551();
		if (client != null && client.method_22683() != null) {
			int nativeWidth = client.method_22683().method_4489();
			int nativeHeight = client.method_22683().method_4506();
			if (nativeWidth > 0 && nativeHeight > 0) {
				this.recordSizeInfo(nativeWidth, nativeHeight);

				try {
					GL11.glGetError();
				} catch (Throwable var7) {
					RecordableMod.LOGGER.debug("GL context not ready for screen capture; skipping frame.", var7);
					return null;
				}

				try {
					if (!this.pboSupported) {
						return this.captureSynchronously(nativeWidth, nativeHeight);
					} else {
						ScreenCapture.CapturedFrame readyFrame = this.hasPendingPboFrame ? this.mapPendingPboFrame() : null;
						if (this.hasPendingPboFrame && readyFrame == null) {
							this.consecutivePboMapFailures++;
							if (this.consecutivePboMapFailures >= 5) {
								RecordableMod.LOGGER
									.warn(
										"glMapBuffer returned null {} times consecutively; disabling PBOs and switching to synchronous glReadPixels readback for the rest of this session.",
										this.consecutivePboMapFailures
									);
								this.pboSupported = false;
								this.deletePbosSafely();
								return this.captureSynchronously(nativeWidth, nativeHeight);
							}
						} else if (readyFrame != null) {
							this.consecutivePboMapFailures = 0;
						}

						if (this.pboIds[0] == 0 || nativeWidth != this.sourceWidth || nativeHeight != this.sourceHeight) {
							this.initializePbos(nativeWidth, nativeHeight);
						}

						this.submitAsyncRead();
						return readyFrame;
					}
				} catch (Throwable var8) {
					RecordableMod.LOGGER.warn("PBO screen capture failed; falling back to synchronous readback.", var8);
					this.pboSupported = false;
					this.deletePbosSafely();

					try {
						return this.captureSynchronously(nativeWidth, nativeHeight);
					} catch (Throwable var6) {
						RecordableMod.LOGGER.warn("Synchronous screen capture also failed; returning null.", var6);
						return null;
					}
				}
			} else {
				return null;
			}
		} else {
			return null;
		}
	}

	public synchronized ScreenCapture.CapturedFrame captureFrameSynchronous() {
		class_310 client = class_310.method_1551();
		if (client != null && client.method_22683() != null) {
			int nativeWidth = client.method_22683().method_4489();
			int nativeHeight = client.method_22683().method_4506();
			if (nativeWidth > 0 && nativeHeight > 0) {
				this.recordSizeInfo(nativeWidth, nativeHeight);

				try {
					GL11.glGetError();
				} catch (Throwable var5) {
					return null;
				}

				return this.captureSynchronously(nativeWidth, nativeHeight);
			} else {
				return null;
			}
		} else {
			return null;
		}
	}

	public int getOutputWidth() {
		return this.outputWidth;
	}

	public int getOutputHeight() {
		return this.outputHeight;
	}

	private ScreenCapture.CapturedFrame captureSynchronously(int nativeWidth, int nativeHeight) {
		if (this.readSourceMode == 3) {
			ScreenCapture.CapturedFrame textureFrame = this.captureViaColorTexture(nativeWidth, nativeHeight);
			if (textureFrame != null) {
				return textureFrame;
			}
		}

		try {
			this.sourceWidth = nativeWidth;
			this.sourceHeight = nativeHeight;
			this.sourceByteSize = checkedByteSize(this.sourceWidth, this.sourceHeight, this.sourceChannels);
			this.ensureFallbackReadBuffer();
			int previousReadFramebuffer = GL11.glGetInteger(36010);
			int previousPackAlignment = GL11.glGetInteger(3333);

			try {
				int activeFbo = GL11.glGetInteger(36006);
				int readFbo = this.resolveReadFbo(activeFbo);
				GL30.glBindFramebuffer(36008, readFbo);
				GL11.glReadBuffer(readFbo == 0 ? 1029 : '賠');
				GL11.glPixelStorei(3333, 1);
				this.fallbackReadBuffer.clear();
				GL11.glReadPixels(0, 0, this.sourceWidth, this.sourceHeight, this.glReadFormat, 5121, this.fallbackReadBuffer);
				this.logReadbackDiagnosticsOnce();
			} finally {
				GL11.glPixelStorei(3333, previousPackAlignment);
				GL30.glBindFramebuffer(36008, previousReadFramebuffer);
			}

			byte[] var13 = FrameBufferPool.getInstance().acquire(this.outputByteSize);
			this.copyFlipScaleToRgb(
				this.fallbackReadBuffer, this.sourceWidth, this.sourceHeight, var13, this.outputWidth, this.outputHeight, this.sourceChannels, this.glesRgbaReadback
			);
			this.evaluateFrameForBlackScreen(var13);
			return new ScreenCapture.CapturedFrame(var13, this.outputWidth, this.outputHeight, System.nanoTime());
		} catch (Throwable var11) {
			RecordableMod.LOGGER.warn("Synchronous screen capture failed.", var11);
			return null;
		}
	}

	private void initializePbos(int newSourceWidth, int newSourceHeight) {
		this.deletePbosSafely();
		this.sourceWidth = newSourceWidth;
		this.sourceHeight = newSourceHeight;
		this.sourceByteSize = checkedByteSize(this.sourceWidth, this.sourceHeight, this.sourceChannels);
		this.pboWriteIndex = 0;
		this.hasPendingPboFrame = false;
		int previousPbo = GL11.glGetInteger(35053);

		try {
			for (int index = 0; index < this.pboIds.length; index++) {
				this.pboIds[index] = GL15.glGenBuffers();
				GL15.glBindBuffer(35051, this.pboIds[index]);
				GL15.glBufferData(35051, (long)this.sourceByteSize, 35041);
			}
		} finally {
			GL15.glBindBuffer(35051, previousPbo);
		}
	}

	private void submitAsyncRead() {
		int previousReadFramebuffer = GL11.glGetInteger(36010);
		int previousPbo = GL11.glGetInteger(35053);
		int previousPackAlignment = GL11.glGetInteger(3333);

		try {
			int activeFbo = GL11.glGetInteger(36006);
			int readFbo = this.resolveReadFbo(activeFbo);
			this.lastSubmittedReadFbo = readFbo;
			GL30.glBindFramebuffer(36008, readFbo);
			GL11.glReadBuffer(readFbo == 0 ? 1029 : '賠');
			GL11.glPixelStorei(3333, 1);
			GL15.glBindBuffer(35051, this.pboIds[this.pboWriteIndex]);
			GL15.glBufferData(35051, (long)this.sourceByteSize, 35041);
			GL11.glReadPixels(0, 0, this.sourceWidth, this.sourceHeight, this.glReadFormat, 5121, 0L);
			this.logReadbackDiagnosticsOnce();
			this.pboWriteIndex = (this.pboWriteIndex + 1) % this.pboIds.length;
			this.hasPendingPboFrame = true;
		} finally {
			GL11.glPixelStorei(3333, previousPackAlignment);
			GL15.glBindBuffer(35051, previousPbo);
			GL30.glBindFramebuffer(36008, previousReadFramebuffer);
		}
	}

	private ScreenCapture.CapturedFrame mapPendingPboFrame() {
		int readIndex = (this.pboWriteIndex + 1) % this.pboIds.length;
		int previousPbo = GL11.glGetInteger(35053);
		ByteBuffer mapped = null;

		Object rgb;
		try {
			GL15.glBindBuffer(35051, this.pboIds[readIndex]);
			mapped = GL15.glMapBuffer(35051, 35000, (long)this.sourceByteSize, null);
			if (mapped != null) {
				byte[] rgbx = FrameBufferPool.getInstance().acquire(this.outputByteSize);
				this.copyFlipScaleToRgb(mapped, this.sourceWidth, this.sourceHeight, rgbx, this.outputWidth, this.outputHeight, this.sourceChannels, this.glesRgbaReadback);
				this.evaluateFrameForBlackScreen(rgbx);
				return new ScreenCapture.CapturedFrame(rgbx, this.outputWidth, this.outputHeight, System.nanoTime());
			}

			rgb = null;
		} finally {
			if (mapped != null) {
				GL15.glUnmapBuffer(35051);
			}

			GL15.glBindBuffer(35051, previousPbo);
		}

		return (ScreenCapture.CapturedFrame)rgb;
	}

	private void ensureFallbackReadBuffer() {
		if (this.fallbackReadBuffer == null || this.fallbackReadBuffer.capacity() < this.sourceByteSize) {
			if (this.fallbackReadBuffer != null) {
				MemoryUtil.memFree(this.fallbackReadBuffer);
			}

			this.fallbackReadBuffer = MemoryUtil.memAlloc(this.sourceByteSize);
		}
	}

	private static int checkedByteSize(int width, int height, int channels) {
		long bytes = (long)width * (long)height * (long)channels;
		if (bytes > 2147483647L) {
			throw new IllegalArgumentException("Capture buffer is too large: " + width + "x" + height);
		} else {
			return (int)bytes;
		}
	}

	private void copyFlipScaleToRgb(
		ByteBuffer sourceBottomUp,
		int sourceWidth,
		int sourceHeight,
		byte[] targetRgbTopDown,
		int targetWidth,
		int targetHeight,
		int srcChannels,
		boolean sourceIsRgba
	) {
		int srcRowStride = sourceWidth * srcChannels;
		int srcTotal = srcRowStride * sourceHeight;
		byte[] src = this.rawConvertScratch;
		if (src == null || src.length < srcTotal) {
			src = new byte[srcTotal];
			this.rawConvertScratch = src;
		}

		int savedPos = sourceBottomUp.position();
		int savedLimit = sourceBottomUp.limit();
		sourceBottomUp.position(0);
		int readable = Math.min(srcTotal, sourceBottomUp.remaining());
		sourceBottomUp.get(src, 0, readable);
		sourceBottomUp.position(savedPos);
		sourceBottomUp.limit(savedLimit);
		int FP_SHIFT = 16;
		int scaleX = (sourceWidth << 16) / targetWidth;
		int scaleY = (sourceHeight << 16) / targetHeight;
		int rOff = sourceIsRgba ? 0 : 2;
		int gOff = 1;
		int bOff = sourceIsRgba ? 2 : 0;

		for (int targetY = 0; targetY < targetHeight; targetY++) {
			int sourceYTopDown = Math.min(sourceHeight - 1, targetY * scaleY >> 16);
			int sourceYBottomUp = sourceHeight - 1 - sourceYTopDown;
			int srcRowBase = sourceYBottomUp * srcRowStride;
			int tgtRowBase = targetY * targetWidth * 3;

			for (int targetX = 0; targetX < targetWidth; targetX++) {
				int sourceX = Math.min(sourceWidth - 1, targetX * scaleX >> 16);
				int sourceIndex = srcRowBase + sourceX * srcChannels;
				int targetIndex = tgtRowBase + targetX * 3;
				targetRgbTopDown[targetIndex] = src[sourceIndex + rOff];
				targetRgbTopDown[targetIndex + 1] = src[sourceIndex + 1];
				targetRgbTopDown[targetIndex + 2] = src[sourceIndex + bOff];
			}
		}
	}

	private void logReadbackDiagnosticsOnce() {
		if (!this.readbackDiagnosticsLogged) {
			this.readbackDiagnosticsLogged = true;

			int err;
			try {
				err = GL11.glGetError();
			} catch (Throwable var3) {
				return;
			}

			RecordableMod.LOGGER
				.info(
					"Screen capture readback: format={} channels={} ({} path), source {}x{}, glReadPixels error=0x{}",
					new Object[]{
						this.glesRgbaReadback ? "GL_RGBA" : "GL_BGR",
						this.sourceChannels,
						this.glesRgbaReadback ? "OpenGL ES/Android" : "desktop GL",
						this.sourceWidth,
						this.sourceHeight,
						Integer.toHexString(err)
					}
				);
			if (err != 0) {
				RecordableMod.LOGGER
					.warn(
						"glReadPixels reported GL error 0x{} on first capture - captured frames may be black. On OpenGL ES only GL_RGBA + GL_UNSIGNED_BYTE is guaranteed for readback.",
						Integer.toHexString(err)
					);
			}
		}
	}

	private void deletePbosSafely() {
		if (!hasGLContext()) {
			RecordableMod.LOGGER.debug("Skipping PBO cleanup - no OpenGL context on current thread ({})", Thread.currentThread().getName());
			this.hasPendingPboFrame = false;
		} else {
			try {
				for (int index = 0; index < this.pboIds.length; index++) {
					if (this.pboIds[index] != 0) {
						GL15.glDeleteBuffers(this.pboIds[index]);
						this.pboIds[index] = 0;
					}
				}
			} catch (Throwable var2) {
				RecordableMod.LOGGER.debug("Failed to delete screen-capture PBOs.", var2);
			}

			this.hasPendingPboFrame = false;
		}
	}

	private int resolveReadFbo(int activeDrawFbo) {
		switch (this.readSourceMode) {
			case 1:
				int mainFbo = mainFramebufferId();
				return mainFbo >= 0 ? mainFbo : activeDrawFbo;
			case 2:
				return 0;
			default:
				return activeDrawFbo;
		}
	}

	private static int mainFramebufferId() {
		try {
			class_310 client = class_310.method_1551();
			if (client == null) {
				return -1;
			}

			Object framebuffer = client.method_1522();
			if (framebuffer == null) {
				return -1;
			}

			for (String fieldName : new String[]{"fbo", "field_1476", "frameBufferId"}) {
				try {
					Field f = framebuffer.getClass().getField(fieldName);
					if (f.get(framebuffer) instanceof Integer i && i > 0) {
						return i;
					}
				} catch (NoSuchFieldException var9) {
				}
			}
		} catch (Throwable var10) {
		}

		return -1;
	}

	private ScreenCapture.CapturedFrame captureViaColorTexture(int nativeWidth, int nativeHeight) {
		int textureId = mainColorAttachmentTexture();
		if (textureId <= 0) {
			return null;
		} else {
			int texWidth = nativeWidth;
			int texHeight = nativeHeight;
			int[] texSize = mainFramebufferTextureSize();
			if (texSize != null && texSize[0] > 0 && texSize[1] > 0) {
				texWidth = texSize[0];
				texHeight = texSize[1];
			}

			this.sourceWidth = texWidth;
			this.sourceHeight = texHeight;
			this.sourceByteSize = checkedByteSize(this.sourceWidth, this.sourceHeight, this.sourceChannels);
			this.ensureFallbackReadBuffer();
			int previousTexture = GL11.glGetInteger(32873);
			int previousPackAlignment = GL11.glGetInteger(3333);

			label46: {
				Object var10;
				try {
					GL11.glBindTexture(3553, textureId);
					GL11.glPixelStorei(3333, 1);
					this.fallbackReadBuffer.clear();
					GL11.glGetTexImage(3553, 0, this.glReadFormat, 5121, this.fallbackReadBuffer);
					this.logTextureReadbackDiagnosticsOnce();
					break label46;
				} catch (Throwable var14) {
					RecordableMod.LOGGER.debug("Texture-based screen capture failed; falling back to glReadPixels.", var14);
					var10 = null;
				} finally {
					GL11.glPixelStorei(3333, previousPackAlignment);
					GL11.glBindTexture(3553, previousTexture);
				}

				return (ScreenCapture.CapturedFrame)var10;
			}

			byte[] rgb = FrameBufferPool.getInstance().acquire(this.outputByteSize);
			this.copyFlipScaleToRgb(
				this.fallbackReadBuffer, this.sourceWidth, this.sourceHeight, rgb, this.outputWidth, this.outputHeight, this.sourceChannels, this.glesRgbaReadback
			);
			this.evaluateFrameForBlackScreen(rgb);
			return new ScreenCapture.CapturedFrame(rgb, this.outputWidth, this.outputHeight, System.nanoTime());
		}
	}

	private static int mainColorAttachmentTexture() {
		try {
			class_310 client = class_310.method_1551();
			if (client == null) {
				return -1;
			}

			Object framebuffer = client.method_1522();
			if (framebuffer == null) {
				return -1;
			}

			for (String methodName : new String[]{"getColorAttachment", "method_30277"}) {
				try {
					Method m = framebuffer.getClass().getMethod(methodName);
					if (m.invoke(framebuffer) instanceof Integer i && i > 0) {
						return i;
					}
				} catch (NoSuchMethodException var10) {
				}
			}

			for (String fieldName : new String[]{"colorAttachment", "field_1476", "field_1480"}) {
				try {
					Field f = framebuffer.getClass().getField(fieldName);
					if (f.get(framebuffer) instanceof Integer i && i > 0) {
						return i;
					}
				} catch (NoSuchFieldException var9) {
				}
			}
		} catch (Throwable var11) {
		}

		return -1;
	}

	private static int[] mainFramebufferTextureSize() {
		try {
			class_310 client = class_310.method_1551();
			if (client == null) {
				return null;
			}

			Object framebuffer = client.method_1522();
			if (framebuffer == null) {
				return null;
			}

			Integer w = readIntField(framebuffer, new String[]{"textureWidth", "field_1480"});
			Integer h = readIntField(framebuffer, new String[]{"textureHeight", "field_1477"});
			if (w != null && h != null && w > 0 && h > 0) {
				return new int[]{w, h};
			}
		} catch (Throwable var4) {
		}

		return null;
	}

	private static Integer readIntField(Object target, String[] candidateNames) {
		for (String name : candidateNames) {
			try {
				Field f = target.getClass().getField(name);
				Object v = f.get(target);
				if (v instanceof Integer) {
					return (Integer)v;
				}
			} catch (NoSuchFieldException var9) {
			} catch (Throwable var10) {
				return null;
			}
		}

		return null;
	}

	private void logTextureReadbackDiagnosticsOnce() {
		if (!this.textureReadbackDiagnosticsLogged) {
			this.textureReadbackDiagnosticsLogged = true;

			int err;
			try {
				err = GL11.glGetError();
			} catch (Throwable var3) {
				return;
			}

			RecordableMod.LOGGER
				.info(
					"Screen capture readback: TEXTURE path (vanilla-screenshot style glGetTexImage), format={} channels={}, source {}x{}, glGetTexImage error=0x{}",
					new Object[]{this.glesRgbaReadback ? "GL_RGBA" : "GL_BGR", this.sourceChannels, this.sourceWidth, this.sourceHeight, Integer.toHexString(err)}
				);
			if (err != 0) {
				RecordableMod.LOGGER
					.warn(
						"glGetTexImage reported GL error 0x{} on first texture capture - frames may be black; auto-recovery will try glReadPixels sources next.",
						Integer.toHexString(err)
					);
			}
		}
	}

	private void evaluateFrameForBlackScreen(byte[] rgb) {
		this.totalFramesProduced++;
		boolean black = FrameValidator.isBlackFrame(rgb, this.outputWidth, this.outputHeight);
		if (!black) {
			this.consecutiveBlackFrames = 0;
		} else {
			this.totalBlackFrames++;
			this.consecutiveBlackFrames++;
			if (this.consecutiveBlackFrames == 15 && this.blackRecoverySwitches < 4) {
				int previousMode = this.readSourceMode;
				this.readSourceMode = (this.readSourceMode + 1) % 4;
				this.blackRecoverySwitches++;
				this.consecutiveBlackFrames = 0;
				this.readbackDiagnosticsLogged = false;
				this.textureReadbackDiagnosticsLogged = false;
				RecordableMod.LOGGER
					.warn(
						"Black-screen capture detected ({} consecutive black frames). Switching capture source {} -> {} (recovery attempt {}/{}). If recordings stay black, your GPU driver may block glReadPixels or a shader mod (Iris/OptiFine) is intercepting the framebuffer.",
						new Object[]{15, readSourceName(previousMode), readSourceName(this.readSourceMode), this.blackRecoverySwitches, 4}
					);
				this.deletePbosSafely();
			}
		}
	}

	private static String readSourceName(int mode) {
		return switch (mode) {
			case 1 -> "MAIN_FBO";
			case 2 -> "BACK_BUFFER";
			case 3 -> "MAIN_TEXTURE";
			default -> "AUTO";
		};
	}

	public synchronized long getTotalFramesProduced() {
		return this.totalFramesProduced;
	}

	public synchronized long getTotalBlackFrames() {
		return this.totalBlackFrames;
	}

	public synchronized int getConsecutiveBlackFrames() {
		return this.consecutiveBlackFrames;
	}

	public synchronized String getReadSourceName() {
		return readSourceName(this.readSourceMode);
	}

	public synchronized boolean isPersistentlyBlack() {
		return this.blackRecoverySwitches >= 4 && this.consecutiveBlackFrames >= 15;
	}

	private void recordSizeInfo(int windowWidth, int windowHeight) {
		this.lastWindowWidth = windowWidth;
		this.lastWindowHeight = windowHeight;
		int[] rt = currentRenderTargetSize();
		if (rt != null) {
			this.renderTargetWidth = rt[0];
			this.renderTargetHeight = rt[1];
			if (!this.sizeMismatchLogged && (this.renderTargetWidth != windowWidth || this.renderTargetHeight != windowHeight)) {
				this.sizeMismatchLogged = true;
				RecordableMod.LOGGER
					.warn(
						"Capture size mismatch: window framebuffer is {}x{} but the game render target is {}x{}. Frames may be cropped, misaligned, or blank. This often means the window was resized mid-capture or a shader mod is drawing into a different-sized buffer.",
						new Object[]{windowWidth, windowHeight, this.renderTargetWidth, this.renderTargetHeight}
					);
			}
		}
	}

	public static int[] currentRenderTargetSize() {
		try {
			class_310 client = class_310.method_1551();
			return client == null ? null : CaptureDiagnostics.readRenderTargetSize(client.method_1522());
		} catch (Throwable var1) {
			return null;
		}
	}

	public synchronized int getSourceWidth() {
		return this.lastWindowWidth;
	}

	public synchronized int getSourceHeight() {
		return this.lastWindowHeight;
	}

	public synchronized int getRenderTargetWidth() {
		return this.renderTargetWidth;
	}

	public synchronized int getRenderTargetHeight() {
		return this.renderTargetHeight;
	}

	public synchronized boolean hasSizeMismatch() {
		return this.renderTargetWidth > 0
			&& this.renderTargetHeight > 0
			&& (this.renderTargetWidth != this.lastWindowWidth || this.renderTargetHeight != this.lastWindowHeight);
	}

	private static boolean hasGLContext() {
		try {
			GL.getCapabilities();
			return true;
		} catch (IllegalStateException var1) {
			return false;
		}
	}

	public synchronized void close() {
		this.deletePbosSafely();
		if (this.fallbackReadBuffer != null) {
			MemoryUtil.memFree(this.fallbackReadBuffer);
			this.fallbackReadBuffer = null;
		}

		this.rawConvertScratch = null;
	}

	public static record CapturedFrame(byte[] rgbPixels, int width, int height, long capturedAtNanos) {
	}
}
