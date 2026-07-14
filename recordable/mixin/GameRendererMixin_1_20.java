package dev.recordable.mixin;

import dev.recordable.RecordableMod;
import dev.recordable.RecordingManager;
import net.minecraft.class_757;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({class_757.class})
public abstract class GameRendererMixin_1_20 {
	@Inject(
		method = {"method_3192(FJZ)V"},
		at = {@At(
			value = "INVOKE",
			target = "Lnet/minecraft/class_332;method_51452()V",
			shift = Shift.AFTER
		)}
	)
	private void recordable$captureAfterHud(float tickDelta, long startTime, boolean tick, CallbackInfo ci) {
		try {
			RecordingManager.getInstance().onFrame();
		} catch (Throwable var7) {
			RecordableMod.LOGGER.warn("Record-able capture hook failed; skipping this frame.", var7);
		}
	}
}
