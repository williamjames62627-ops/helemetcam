package dev.recordable.theme;

import dev.recordable.compat.RenderHelper;
import java.util.Random;
import net.minecraft.class_327;
import net.minecraft.class_332;

public final class VhsEffectsRenderer {
	private static final Random RNG = new Random();
	private static long lastGlitchTick = 0L;
	private static int glitchY = 0;
	private static int glitchHeight = 0;
	private static boolean glitchActive = false;

	private VhsEffectsRenderer() {
	}

	public static void renderOverPanel(class_332 ctx, int left, int top, int right, int bottom) {
		ThemeEngine engine = ThemeEngine.get();
		ThemeColors colors = engine.colors();
		if (engine.scanlineEnabled()) {
			renderScanlines(ctx, left, top, right, bottom, colors.scanlineColor);
		}

		if (engine.grainEnabled()) {
			renderFilmGrain(ctx, left, top, right, bottom, colors.grainColor);
		}

		if (engine.glitchEnabled()) {
			renderGlitchBars(ctx, left, top, right, bottom, colors.glitchColor);
		}

		if (engine.vignetteEnabled()) {
			renderVignette(ctx, left, top, right, bottom, colors.vignetteColor);
		}
	}

	public static void renderScanlines(class_332 ctx, int left, int top, int right, int bottom, int color) {
		if ((color & 0xFF000000) != 0) {
			for (int y = top; y < bottom; y += 2) {
				ctx.method_25294(left, y, right, y + 1, color);
			}
		}
	}

	public static void renderFilmGrain(class_332 ctx, int left, int top, int right, int bottom, int color) {
		if ((color & 0xFF000000) != 0) {
			int w = right - left;
			int h = bottom - top;
			int count = Math.max(20, w * h / 400);
			int baseAlpha = color >> 24 & 0xFF;

			for (int i = 0; i < count; i++) {
				int x = left + RNG.nextInt(w);
				int y = top + RNG.nextInt(h);
				int alpha = Math.max(1, baseAlpha / 2 + RNG.nextInt(Math.max(1, baseAlpha / 2)));
				int grainColor = alpha << 24 | color & 16777215;
				ctx.method_25294(x, y, x + 1, y + 1, grainColor);
			}
		}
	}

	public static void renderGlitchBars(class_332 ctx, int left, int top, int right, int bottom, int color) {
		if ((color & 0xFF000000) != 0) {
			long now = System.currentTimeMillis();
			if (!glitchActive && now - lastGlitchTick > (long)(3000 + RNG.nextInt(5000))) {
				glitchActive = true;
				lastGlitchTick = now;
				glitchY = top + RNG.nextInt(Math.max(1, bottom - top - 10));
				glitchHeight = 2 + RNG.nextInt(6);
			}

			if (glitchActive) {
				if (now - lastGlitchTick > (long)(100 + RNG.nextInt(200))) {
					glitchActive = false;
				} else {
					int shift = -3 + RNG.nextInt(7);
					ctx.method_25294(left + shift, glitchY, right + shift, Math.min(bottom, glitchY + glitchHeight), color);
					int y2 = glitchY + 8 + RNG.nextInt(20);
					if (y2 < bottom) {
						ctx.method_25294(left - shift, y2, right - shift, Math.min(bottom, y2 + 2), color & -2130706433);
					}
				}
			}
		}
	}

	public static void renderVignette(class_332 ctx, int left, int top, int right, int bottom, int color) {
		if ((color & 0xFF000000) != 0) {
			int w = right - left;
			int h = bottom - top;
			int borderW = Math.max(6, w / 12);
			int borderH = Math.max(6, h / 12);
			int alpha = color >> 24 & 0xFF;

			for (int layer = 0; layer < 4; layer++) {
				int layerAlpha = alpha * (4 - layer) / 6;
				int c = layerAlpha << 24;
				int inset = layer * (borderW / 4);
				ctx.method_25294(left + inset, top + inset, right - inset, top + borderH - layer * (borderH / 4), c);
				ctx.method_25294(left + inset, bottom - borderH + layer * (borderH / 4), right - inset, bottom - inset, c);
				ctx.method_25294(left + inset, top + borderH - layer * (borderH / 4), left + borderW - layer * (borderW / 4), bottom - borderH + layer * (borderH / 4), c);
				ctx.method_25294(right - borderW + layer * (borderW / 4), top + borderH - layer * (borderH / 4), right - inset, bottom - borderH + layer * (borderH / 4), c);
			}
		}
	}

	public static void renderTrackingNoise(class_332 ctx, int left, int top, int right, int lineCount) {
		ThemeColors colors = ThemeEngine.get().colors();
		int alpha = Math.max(10, (colors.scanlineColor >> 24 & 0xFF) / 2);

		for (int i = 0; i < lineCount; i++) {
			int y = top + i * 2;
			int xOff = RNG.nextInt(5) - 2;
			ctx.method_25294(left + xOff, y, right + xOff, y + 1, alpha << 24 | 16777215);
		}
	}

	public static void renderSprocketHoles(class_332 ctx, int x, int top, int bottom, int color) {
		int spacing = 18;
		int holeW = 6;
		int holeH = 4;

		for (int y = top + 6; y < bottom - 6; y += spacing) {
			ctx.method_25294(x, y, x + holeW, y + holeH, color);
			ctx.method_25294(x + 1, y + 1, x + holeW - 1, y + holeH - 1, -16777216);
		}
	}

	public static void renderFilmStripBorders(class_332 ctx, int left, int top, int right, int bottom, int borderWidth) {
		ThemeColors colors = ThemeEngine.get().colors();
		int stripColor = colors.panelBorder;
		ctx.method_25294(left, top, left + borderWidth, bottom, stripColor);
		renderSprocketHoles(ctx, left + 2, top, bottom, colors.accent);
		ctx.method_25294(right - borderWidth, top, right, bottom, stripColor);
		renderSprocketHoles(ctx, right - borderWidth + 2, top, bottom, colors.accent);
	}

	public static void renderTapeLoadingBar(class_332 ctx, int left, int y, int right, float progress) {
		ThemeColors colors = ThemeEngine.get().colors();
		int h = 3;
		ctx.method_25294(left, y, right, y + h, colors.panelBorder);
		int filled = (int)((float)(right - left) * Math.max(0.0F, Math.min(1.0F, progress)));
		ctx.method_25294(left, y, left + filled, y + h, colors.accent);
		if (progress < 1.0F) {
			int shimmerX = left + filled;
			ctx.method_25294(shimmerX, y, Math.min(shimmerX + 4, right), y + h, colors.accentHover);
		}
	}

	public static void renderVcrPlayBadge(class_332 ctx, class_327 textRenderer, int x, int y) {
		ThemeColors colors = ThemeEngine.get().colors();
		float pulse = ThemeEngine.pulse(System.currentTimeMillis(), 2000);
		int alpha = (int)(180.0F + 75.0F * pulse);
		int textColor = alpha << 24 | colors.textPrimary & 16777215;
		RenderHelper.drawText(ctx, textRenderer, "▶ PLAY", x, y, textColor);
	}

	public static void renderRecDot(class_332 ctx, int x, int y, int radius) {
		ThemeColors colors = ThemeEngine.get().colors();
		float pulse = ThemeEngine.pulse(System.currentTimeMillis(), 1000);
		int alpha = (int)(100.0F + 155.0F * pulse);
		int dotColor = alpha << 24 | colors.accent & 16777215;
		ctx.method_25294(x - radius, y - radius, x + radius, y + radius, dotColor);
	}
}
