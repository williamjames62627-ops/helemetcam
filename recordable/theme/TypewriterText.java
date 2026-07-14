package dev.recordable.theme;

import dev.recordable.compat.RenderHelper;
import net.minecraft.class_327;
import net.minecraft.class_332;

public final class TypewriterText {
	private final String fullText;
	private final long startTimeMs;
	private final int charsPerSecond;
	private boolean completed = false;

	public TypewriterText(String text, int charsPerSecond) {
		this.fullText = text;
		this.startTimeMs = System.currentTimeMillis();
		this.charsPerSecond = charsPerSecond;
	}

	public int render(class_332 ctx, class_327 textRenderer, int x, int y, int color) {
		long elapsed = System.currentTimeMillis() - this.startTimeMs;
		int visibleChars = (int)((double)(elapsed * (long)this.charsPerSecond) / 1000.0);
		visibleChars = Math.min(visibleChars, this.fullText.length());
		if (visibleChars >= this.fullText.length()) {
			this.completed = true;
		}

		String visible = this.fullText.substring(0, visibleChars);
		RenderHelper.drawText(ctx, textRenderer, visible, x, y, color);
		if (!this.completed || System.currentTimeMillis() / 500L % 2L == 0L) {
			int cursorX = x + textRenderer.method_1727(visible);
			ThemeColors colors = ThemeEngine.get().colors();
			ctx.method_25294(cursorX, y, cursorX + 1, y + 9, colors.accent);
		}

		if (ThemeEngine.get().preset() == ThemePreset.VHS && !this.completed && elapsed % 400L < 30L) {
			int flickerAlpha = 48;
			ctx.method_25294(x, y - 1, x + textRenderer.method_1727(visible) + 2, y + 10, flickerAlpha << 24);
		}

		return visibleChars;
	}

	public boolean isCompleted() {
		return this.completed;
	}

	public String getFullText() {
		return this.fullText;
	}

	public void complete() {
		this.completed = true;
	}

	public static void renderFlickerText(class_332 ctx, class_327 textRenderer, String text, int x, int y, int baseColor) {
		float pulse = ThemeEngine.pulse(System.currentTimeMillis(), 3000);
		int alpha = (int)(200.0F + 55.0F * pulse);
		int color = alpha << 24 | baseColor & 16777215;
		RenderHelper.drawText(ctx, textRenderer, text, x, y, color);
	}
}
