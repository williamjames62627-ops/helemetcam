package dev.recordable.theme;

import net.minecraft.class_2561;
import net.minecraft.class_332;
import net.minecraft.class_4185;
import net.minecraft.class_4185.class_4241;

public final class ThemedButton {
	private ThemedButton() {
	}

	public static class_4185 create(int x, int y, int w, int h, class_2561 msg, class_4241 onPress) {
		return class_4185.method_46430(msg, onPress).method_46434(x, y, w, h).method_46431();
	}

	public static void drawThemedButtonRect(class_332 context, int x, int y, int w, int h, boolean hovered, float hoverProgress) {
		ThemeColors colors = ThemeEngine.get().colors();
		int bgColor = ThemeEngine.lerpColor(colors.buttonBackground, colors.buttonBackgroundHover, hoverProgress);
		int borderColor = ThemeEngine.lerpColor(colors.buttonBorder, colors.accent, hoverProgress);
		context.method_25294(x, y, x + w, y + h, bgColor);
		context.method_25294(x, y, x + w, y + 1, borderColor);
		context.method_25294(x, y + h - 1, x + w, y + h, borderColor);
		context.method_25294(x, y, x + 1, y + h, borderColor);
		context.method_25294(x + w - 1, y, x + w, y + h, borderColor);
		if (hoverProgress > 0.1F && ThemeEngine.get().scanlineEnabled()) {
			long tick = System.currentTimeMillis();
			int scanY = y + (int)(tick / 30L % (long)h);
			int scanAlpha = (int)(20.0F * hoverProgress);
			context.method_25294(x + 1, scanY, x + w - 1, Math.min(scanY + 2, y + h - 1), scanAlpha << 24 | 16777215);
		}

		if (hoverProgress > 0.05F) {
			int barAlpha = (int)(255.0F * hoverProgress);
			int barColor = barAlpha << 24 | colors.accent & 16777215;
			context.method_25294(x, y + 1, x + 2, y + h - 1, barColor);
		}
	}
}
