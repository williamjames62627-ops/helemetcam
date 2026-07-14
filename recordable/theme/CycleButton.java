package dev.recordable.theme;

import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_4185;
import net.minecraft.class_4185.class_4241;

public final class CycleButton extends class_4185 {
	private final class_4241 onSecondary;

	private CycleButton(int x, int y, int w, int h, class_2561 msg, class_4241 onPrimary, class_4241 onSecondary) {
		super(x, y, w, h, msg, onPrimary, field_40754);
		this.onSecondary = onSecondary;
	}

	public static CycleButton create(int x, int y, int w, int h, class_2561 msg, class_4241 onLeft, class_4241 onRight) {
		return new CycleButton(x, y, w, h, msg, onLeft, onRight);
	}

	public boolean method_25402(double mouseX, double mouseY, int button) {
		if (this.field_22763 && this.field_22764 && button == 1 && this.onSecondary != null && this.method_25405(mouseX, mouseY)) {
			class_310 mc = class_310.method_1551();
			if (mc != null) {
				this.method_25354(mc.method_1483());
			}

			this.onSecondary.onPress(this);
			return true;
		} else {
			return super.method_25402(mouseX, mouseY, button);
		}
	}
}
