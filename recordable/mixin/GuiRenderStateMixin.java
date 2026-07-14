package dev.recordable.mixin;

import dev.recordable.RecordableMod;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(
	targets = {"net/minecraft/client/gui/render/state/GuiRenderState"}
)
public abstract class GuiRenderStateMixin {
	private static final int NO_BLUR_LAYER = Integer.MAX_VALUE;
	@Shadow
	@Dynamic
	private int blurLayer;

	@Inject(
		method = {"applyBlur()V"},
		at = {@At("HEAD")},
		cancellable = true
	)
	private void recordable$skipDuplicateBlur(CallbackInfo callbackInfo) {
		if (this.blurLayer != Integer.MAX_VALUE) {
			RecordableMod.LOGGER.debug("Skipped duplicate GUI blur request in the same frame.");
			callbackInfo.cancel();
		}
	}
}
