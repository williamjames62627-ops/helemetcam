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
public abstract class GameRendererMixin {
	@Inject(
		method = {"render(Lnet/minecraft/client/render/RenderTickCounter;Z)V"},
		at = {@At(
			value = "INVOKE",
			target = "Lnet/minecraft/class_329;render(Lnet/minecraft/class_332;Lnet/minecraft/class_317;)V",
			shift = Shift.AFTER
		)},
		require = 0
	)
	private void recordable$captureAfterHud(CallbackInfo ci) {
		try {
			RecordingManager.getInstance().onFrame();
		} catch (Throwable var3) {
			RecordableMod.LOGGER.warn("Record-able capture hook failed; skipping this frame.", var3);
		}
	}
}
