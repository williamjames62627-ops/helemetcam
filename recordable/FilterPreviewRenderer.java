package dev.recordable;

import dev.recordable.filter.FilterType;
import net.minecraft.class_332;

public final class FilterPreviewRenderer {
	private static int tick;

	private FilterPreviewRenderer() {
	}

	public static void render(class_332 context, int screenW, int screenH, FilterType filterType, int intensity) {
		if (filterType != null && filterType != FilterType.NONE) {
			tick++;
			float t = Math.max(0.0F, Math.min(1.0F, (float)intensity / 100.0F));
			switch (filterType) {
				case VHS:
					renderVhs(context, screenW, screenH, t);
					break;
				case LCD_MOIRE:
					renderLcdMoire(context, screenW, screenH, t);
					break;
				case CRT:
					renderCrt(context, screenW, screenH, t);
			}
		}
	}

	private static void renderVhs(class_332 context, int w, int h, float t) {
		int tintAlpha = (int)(30.0F * t);
		if (tintAlpha > 0) {
			context.method_25294(0, 0, w, h, argb(tintAlpha, 180, 120, 40));
		}

		int scanAlpha = (int)(25.0F * t);
		if (scanAlpha > 0) {
			int scanColor = argb(scanAlpha, 0, 0, 0);
			int offset = tick / 2 % 4;

			for (int y = offset; y < h; y += 4) {
				context.method_25294(0, y, w, y + 1, scanColor);
			}
		}

		int trackingY = tick * 3 / 2 % (h + 60) - 30;
		int trackAlpha = (int)(18.0F * t);
		if (trackAlpha > 0 && trackingY > -10 && trackingY < h) {
			int bandH = 3 + tick % 3;
			context.method_25294(0, trackingY, w, Math.min(h, trackingY + bandH), argb(trackAlpha, 200, 200, 200));
		}

		if (tick % 8 < 2 && t > 0.3F) {
			int noiseY = (tick * 7 + 41) % h;
			int noiseH = 1 + tick % 2;
			int noiseAlpha = (int)(12.0F * t);
			context.method_25294(0, noiseY, w, Math.min(h, noiseY + noiseH), argb(noiseAlpha, 255, 255, 255));
		}

		int fringeAlpha = (int)(15.0F * t);
		if (fringeAlpha > 0) {
			int fringeW = Math.max(1, (int)(3.0F * t));
			context.method_25294(0, 0, fringeW, h, argb(fringeAlpha, 255, 60, 60));
			context.method_25294(w - fringeW, 0, w, h, argb(fringeAlpha, 60, 60, 255));
		}
	}

	private static void renderLcdMoire(class_332 context, int w, int h, float t) {
		int dimAlpha = (int)(15.0F * t);
		if (dimAlpha > 0) {
			context.method_25294(0, 0, w, h, argb(dimAlpha, 0, 0, 0));
		}

		int subAlpha = (int)(12.0F * t);
		if (subAlpha > 0) {
			for (int x = 0; x < w; x += 3) {
				int phase = x % 3;
				int r = phase == 0 ? 255 : 0;
				int g = phase == 1 ? 255 : 0;
				int b = phase == 2 ? 255 : 0;
				context.method_25294(x, 0, x + 1, h, argb(subAlpha, r, g, b));
			}
		}

		int moireAlpha = (int)(10.0F * t);
		if (moireAlpha > 0) {
			float phase = (float)tick * 0.05F;

			for (int y = 0; y < h; y += 6) {
				float sine = (float)Math.sin((double)y * 0.15 + (double)phase);
				int bandAlpha = (int)((float)moireAlpha * Math.abs(sine));
				if (bandAlpha > 0) {
					context.method_25294(0, y, w, y + 3, argb(bandAlpha, 128, 128, 128));
				}
			}
		}
	}

	private static void renderCrt(class_332 context, int w, int h, float t) {
		int scanAlpha = (int)(30.0F * t);
		if (scanAlpha > 0) {
			int scanColor = argb(scanAlpha, 0, 0, 0);

			for (int y = 0; y < h; y += 3) {
				context.method_25294(0, y, w, y + 1, scanColor);
			}
		}

		int glowAlpha = (int)(8.0F * t);
		if (glowAlpha > 0) {
			context.method_25294(0, 0, w, h, argb(glowAlpha, 255, 240, 200));
		}
	}

	private static int argb(int a, int r, int g, int b) {
		return (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | b & 0xFF;
	}
}
