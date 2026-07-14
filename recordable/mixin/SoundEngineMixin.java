package dev.recordable.mixin;

import dev.recordable.OpenALLoopbackCapture;
import dev.recordable.RecordableMod;
import dev.recordable.ReplayCompatBridge;
import java.nio.IntBuffer;
import net.minecraft.class_4225;
import org.lwjgl.openal.ALC10;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({class_4225.class})
public abstract class SoundEngineMixin {
	@Unique
	private static boolean recordable$usingLoopback = false;

	@Redirect(
		method = {"method_19661"},
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/class_4225;method_38501(Ljava/lang/String;)J"
		)
	)
	private static long recordable$redirectOpenDevice(String deviceSpecifier) {
		recordable$usingLoopback = false;
		if (ReplayCompatBridge.shouldYieldAudioDevice()) {
			RecordableMod.LOGGER
				.info("[Recordable] Yielding OpenAL loopback to {} (compatibility bridge); using normal audio device.", ReplayCompatBridge.getPresentReplayModName());
			return recordable$openNormalDevice(deviceSpecifier);
		} else {
			try {
				if (!OpenALLoopbackCapture.isLoopbackSupported()) {
					RecordableMod.LOGGER.info("ALC_SOFT_loopback not supported, using normal audio");
					return recordable$openNormalDevice(deviceSpecifier);
				} else {
					OpenALLoopbackCapture loopback = OpenALLoopbackCapture.getInstance();
					long device = loopback.openLoopbackDevice();
					if (device == 0L) {
						RecordableMod.LOGGER.warn("Loopback device failed, using normal audio");
						return recordable$openNormalDevice(deviceSpecifier);
					} else {
						recordable$usingLoopback = true;
						RecordableMod.LOGGER.info("SoundEngine using loopback device: {}", device);
						return device;
					}
				}
			} catch (Throwable var4) {
				RecordableMod.LOGGER.error("Loopback setup error, using normal audio", var4);
				return recordable$openNormalDevice(deviceSpecifier);
			}
		}
	}

	@Redirect(
		method = {"method_19661"},
		at = @At(
			value = "INVOKE",
			target = "Lorg/lwjgl/openal/ALC10;alcCreateContext(JLjava/nio/IntBuffer;)J"
		)
	)
	private long recordable$redirectCreateContext(long device, IntBuffer originalAttrs) {
		if (!recordable$usingLoopback) {
			return ALC10.alcCreateContext(device, originalAttrs);
		} else {
			OpenALLoopbackCapture loopback = OpenALLoopbackCapture.getInstance();
			int[] loopbackAttrs = loopback.getContextAttributes();
			RecordableMod.LOGGER.info("Creating context with loopback format: {}Hz stereo 16-bit", 48000);
			return ALC10.alcCreateContext(device, loopbackAttrs);
		}
	}

	@Inject(
		method = {"method_19661"},
		at = {@At("HEAD")}
	)
	private void recordable$beforeInit(String deviceSpecifier, boolean enableHrtf, CallbackInfo ci) {
		RecordableMod.LOGGER.info("[Recordable] SoundEngineMixin active - attempting OpenAL loopback for game-audio capture");
	}

	@Inject(
		method = {"method_19661"},
		at = {@At("TAIL")}
	)
	private void recordable$afterInit(String deviceSpecifier, boolean enableHrtf, CallbackInfo ci) {
		if (recordable$usingLoopback) {
			OpenALLoopbackCapture.getInstance().startRenderThread();
			RecordableMod.LOGGER.info("[Recordable] Loopback render thread started - game audio captured directly from OpenAL");
		} else {
			RecordableMod.LOGGER
				.warn(
					"[Recordable] Game-audio loopback is NOT active after SoundEngine init. Recordings will fall back to system audio capture, which is often silent or only background noise. Most common cause: another recording mod (Flashback, ReplayMod, etc.) also redirects OpenAL and took the device first. Try disabling other recording mods and keep only Record-able for game audio."
				);
		}
	}

	@Inject(
		method = {"method_19664"},
		at = {@At("HEAD")}
	)
	private void recordable$beforeClose(CallbackInfo ci) {
		if (recordable$usingLoopback) {
			try {
				OpenALLoopbackCapture.getInstance().shutdown();
			} catch (Throwable var3) {
			}

			recordable$usingLoopback = false;
		}
	}

	@Unique
	private static long recordable$openNormalDevice(String deviceSpecifier) {
		long device = 0L;
		if (deviceSpecifier != null) {
			device = ALC10.alcOpenDevice(deviceSpecifier);
		}

		if (device == 0L) {
			String available = class_4225.method_38500();
			if (available != null) {
				device = ALC10.alcOpenDevice(available);
			}
		}

		if (device == 0L) {
			device = ALC10.alcOpenDevice((CharSequence)null);
		}

		if (device == 0L) {
			throw new IllegalStateException("Failed to open OpenAL device");
		} else {
			return device;
		}
	}
}
