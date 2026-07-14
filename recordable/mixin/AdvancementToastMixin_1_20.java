package dev.recordable.mixin;

import dev.recordable.AutoClipManager;
import dev.recordable.RecordableMod;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.class_310;
import net.minecraft.class_367;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({class_367.class})
public abstract class AdvancementToastMixin_1_20 {
	@Inject(
		method = {"<init>"},
		at = {@At("RETURN")}
	)
	private void recordable$onAdvancementToast(CallbackInfo ci) {
		try {
			AutoClipManager manager = RecordableMod.getAutoClipManager();
			if (manager == null) {
				return;
			}

			String title = "Advancement";

			try {
				Object toastInstance = this;
				Field[] fields = this.getClass().getDeclaredFields();

				for (Field field : fields) {
					field.setAccessible(true);
					Object value = field.get(toastInstance);
					if (value != null) {
						try {
							Method getDisplay = value.getClass().getMethod("getDisplay");
							Object display = getDisplay.invoke(value);
							if (display != null) {
								Method getTitle = display.getClass().getMethod("getTitle");
								Object titleText = getTitle.invoke(display);
								if (titleText != null) {
									title = titleText.toString();
								}
							}
						} catch (NoSuchMethodException var15) {
						}
						break;
					}
				}
			} catch (Throwable var16) {
			}

			class_310 client = class_310.method_1551();
			manager.onAdvancementEarned(client, title);
		} catch (Throwable var17) {
			RecordableMod.LOGGER.debug("Failed to process advancement for auto-clip (1.20.x).", var17);
		}
	}
}
