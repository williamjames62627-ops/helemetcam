package dev.recordable.theme;

import dev.recordable.compat.RenderHelper;
import net.minecraft.class_327;
import net.minecraft.class_332;

public final class ThemedPanel {
	private ThemedPanel() {
	}

	public static void drawPanel(class_332 ctx, int left, int top, int right, int bottom) {
		ThemeColors colors = ThemeEngine.get().colors();
		ctx.method_25294(left, top, right, bottom, colors.panelBackground);
		ctx.method_25294(left, top, right, top + 2, colors.accent);
		ctx.method_25294(left, bottom - 1, right, bottom, colors.panelBorder);
		ctx.method_25294(left, top, left + 1, bottom, colors.panelBorder);
		ctx.method_25294(right - 1, top, right, bottom, colors.panelBorder);
		VhsEffectsRenderer.renderOverPanel(ctx, left + 1, top + 2, right - 1, bottom - 1);
	}

	public static void drawFilmPanel(class_332 ctx, int left, int top, int right, int bottom) {
		ThemeColors colors = ThemeEngine.get().colors();
		int stripW = 12;
		ctx.method_25294(left + stripW, top, right - stripW, bottom, colors.panelBackground);
		VhsEffectsRenderer.renderFilmStripBorders(ctx, left, top, right, bottom, stripW);
		VhsEffectsRenderer.renderOverPanel(ctx, left + stripW, top, right - stripW, bottom);
	}

	public static void drawSectionHeader(class_332 ctx, class_327 textRenderer, String text, int x, int y, int maxWidth) {
		ThemeColors colors = ThemeEngine.get().colors();
		ThemePreset preset = ThemeEngine.get().preset();
		RenderHelper.drawText(ctx, textRenderer, text, x, y, colors.headerText);
		int textWidth = textRenderer.method_1727(text);
		int lineY = y + 10;
		ctx.method_25294(x, lineY, x + textWidth, lineY + 1, colors.headerUnderline);
		int extEnd = Math.min(x + maxWidth, x + textWidth + 40);
		if (extEnd > x + textWidth + 4) {
			int fadeColor = colors.headerUnderline & 1090519039;
			ctx.method_25294(x + textWidth + 2, lineY, extEnd, lineY + 1, fadeColor);
		}

		if (preset == ThemePreset.VHS || preset == ThemePreset.NEON) {
			RenderHelper.drawText(ctx, textRenderer, "▌", x - 8, y, colors.accent);
		}

		if (preset == ThemePreset.CINEMA) {
			RenderHelper.drawText(ctx, textRenderer, "★", x - 10, y, colors.accent);
		}
	}

	public static void drawDivider(class_332 ctx, int x, int y, int width) {
		ThemeColors colors = ThemeEngine.get().colors();
		ctx.method_25294(x, y, x + width, y + 1, colors.panelBorder);
	}

	public static void drawScrollbar(class_332 ctx, int barLeft, int barTop, int barBottom, int thumbTop, int thumbHeight) {
		ThemeColors colors = ThemeEngine.get().colors();
		int barRight = barLeft + 3;
		ctx.method_25294(barLeft, barTop, barRight, barBottom, colors.scrollTrack);
		ctx.method_25294(barLeft, thumbTop, barRight, thumbTop + thumbHeight, colors.scrollThumb);
	}

	public static void drawReelLoading(class_332 ctx, int centerX, int centerY, int radius) {
		ThemeColors colors = ThemeEngine.get().colors();
		long tick = System.currentTimeMillis();
		double angle = (double)(tick % 2000L) / 2000.0 * Math.PI * 2.0;

		for (int i = 0; i < 8; i++) {
			double a = angle + (double)i * Math.PI / 4.0;
			int dx = (int)(Math.cos(a) * (double)radius);
			int dy = (int)(Math.sin(a) * (double)radius);
			int alpha = 80 + (int)((double)(175L * (((long)i + tick / 125L % 8L) % 8L)) / 8.0);
			alpha = Math.min(255, alpha);
			int dotColor = alpha << 24 | colors.accent & 16777215;
			ctx.method_25294(centerX + dx - 1, centerY + dy - 1, centerX + dx + 2, centerY + dy + 2, dotColor);
		}
	}

	public static void drawVhsStatusBadge(class_332 ctx, class_327 textRenderer, String text, int x, int y, boolean blink) {
		ThemeColors colors = ThemeEngine.get().colors();
		int bgColor = colors.panelBackground & -872415232 | colors.panelBackground & 16777215;
		int textWidth = textRenderer.method_1727(text);
		ctx.method_25294(x - 2, y - 1, x + textWidth + 2, y + 10, bgColor);
		if (blink) {
			float pulse = ThemeEngine.pulse(System.currentTimeMillis(), 1200);
			int alpha = (int)(130.0F + 125.0F * pulse);
			int textColor = alpha << 24 | colors.textPrimary & 16777215;
			RenderHelper.drawText(ctx, textRenderer, text, x, y, textColor);
		} else {
			RenderHelper.drawText(ctx, textRenderer, text, x, y, colors.textPrimary);
		}
	}

	public static void drawCategoryTab(class_332 ctx, class_327 textRenderer, String label, int x, int y, int width, boolean selected) {
		ThemeColors colors = ThemeEngine.get().colors();
		int bgColor = selected ? colors.sectionHover : colors.sectionBackground;
		ctx.method_25294(x, y, x + width, y + 16, bgColor);
		if (selected) {
			ctx.method_25294(x, y, x + 2, y + 16, colors.accent);
		}

		RenderHelper.drawText(ctx, textRenderer, label, x + 6, y + 4, selected ? colors.textPrimary : colors.textSecondary);
	}
}
