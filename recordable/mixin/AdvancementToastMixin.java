package dev.recordable.mixin;

import dev.recordable.AutoClipManager;
import dev.recordable.RecordableMod;
import net.minecraft.class_185;
import net.minecraft.class_310;
import net.minecraft.class_367;
import net.minecraft.class_8779;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({class_367.class})
public abstract class AdvancementToastMixin {
	@Shadow
	@Final
	private class_8779 field_2205;

	@Inject(
		method = {"<init>"},
		at = {@At("RETURN")}
	)
	private void recordable$onAdvancementToast(class_8779 advancement, CallbackInfo ci) {
		try {
			AutoClipManager manager = RecordableMod.getAutoClipManager();
			if (manager == null) {
				return;
			}

			String title = "Advancement";
			if (advancement != null && advancement.comp_1920() != null) {
				class_185 display = (class_185)advancement.comp_1920().comp_1913().orElse(null);
				if (display != null && display.method_811() != null) {
					title = display.method_811().getString();
				}
			}

			class_310 client = class_310.method_1551();
			manager.onAdvancementEarned(client, title);
		} catch (Throwable var6) {
			RecordableMod.LOGGER.debug("Failed to process advancement for auto-clip.", var6);
		}
	}
}
