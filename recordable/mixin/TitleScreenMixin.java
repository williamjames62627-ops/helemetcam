package dev.recordable.mixin;

import dev.recordable.RecordableConfig;
import dev.recordable.screen.HomeButtonWidget;
import net.minecraft.class_2561;
import net.minecraft.class_437;
import net.minecraft.class_442;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({class_442.class})
public abstract class TitleScreenMixin extends class_437 {
	protected TitleScreenMixin(class_2561 title) {
		super(title);
	}

	@Inject(
		method = {"method_25426"},
		at = {@At("TAIL")}
	)
	private void recordable$addHomeButton(CallbackInfo ci) {
		RecordableConfig config;
		try {
			config = RecordableConfig.get();
		} catch (Throwable var5) {
			return;
		}

		if (config != null && config.showHomeButton) {
			try {
				this.method_37063(HomeButtonWidget.create((class_442)this));
			} catch (Throwable var4) {
			}
		}
	}
}
