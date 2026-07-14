package dev.recordable.theme;

import java.util.function.Consumer;
import net.minecraft.class_2561;
import net.minecraft.class_332;
import net.minecraft.class_4185;

public final class ThemedToggle {
	private ThemedToggle() {
	}

	public static class_4185 create(int x, int y, int w, int h, String label, boolean initial, Consumer<Boolean> onChange) {
		boolean[] state = new boolean[]{initial};
		class_2561 msg = class_2561.method_43470(label + ": " + (initial ? "ON" : "OFF"));
		return class_4185.method_46430(msg, b -> {
			state[0] = !state[0];
			b.method_25355(class_2561.method_43470(label + ": " + (state[0] ? "ON" : "OFF")));
			onChange.accept(state[0]);
		}).method_46434(x, y, w, h).method_46431();
	}

	public static void drawToggleTrack(class_332 context, int x, int y, int trackW, int trackH, float animProgress) {
		ThemeColors colors = ThemeEngine.get().colors();
		int trackColor = ThemeEngine.lerpColor(colors.textMuted & 1090519039, colors.accent & -2130706433, animProgress);
		context.method_25294(x, y, x + trackW, y + trackH, trackColor);
		int thumbW = 8;
		int thumbX = x + 2 + (int)((float)(trackW - thumbW - 4) * animProgress);
		int thumbColor = ThemeEngine.lerpColor(colors.textMuted, colors.accent, animProgress);
		context.method_25294(thumbX, y + 1, thumbX + thumbW, y + trackH - 1, thumbColor);
	}
}
