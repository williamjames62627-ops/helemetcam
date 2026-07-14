package dev.recordable.compat;

import net.minecraft.class_2561;
import net.minecraft.class_327;
import net.minecraft.class_332;

public final class RenderHelper {
	private RenderHelper() {
	}

	public static int drawText(class_332 context, class_327 textRenderer, String text, int x, int y, int color) {
		return context.method_51433(textRenderer, text, x, y, color, true);
	}

	public static int drawText(class_332 context, class_327 textRenderer, class_2561 text, int x, int y, int color) {
		return context.method_51439(textRenderer, text, x, y, color, true);
	}

	public static boolean is1_20_5Plus() {
		return false;
	}
}
