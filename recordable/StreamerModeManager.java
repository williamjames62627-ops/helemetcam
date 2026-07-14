package dev.recordable;

import dev.recordable.CensorRegion.GradientDirection;
import dev.recordable.CensorRegion.Style;
import java.util.ArrayList;
import java.util.List;

public final class StreamerModeManager {
	private static final StreamerModeManager INSTANCE = new StreamerModeManager();

	private StreamerModeManager() {
	}

	public static StreamerModeManager getInstance() {
		return INSTANCE;
	}

	public boolean isActive() {
		RecordableConfig config = RecordableConfig.get();
		if (config != null && config.streamerModeEnabled) {
			List<CensorRegion> regions = config.censorRegions;
			if (regions != null && !regions.isEmpty()) {
				for (CensorRegion r : regions) {
					if (r != null && r.enabled) {
						return true;
					}
				}

				return false;
			} else {
				return false;
			}
		} else {
			return false;
		}
	}

	public List<CensorRegion> getActiveRegions() {
		List<CensorRegion> out = new ArrayList();
		RecordableConfig config = RecordableConfig.get();
		if (config != null && config.censorRegions != null) {
			for (CensorRegion r : config.censorRegions) {
				if (r != null && r.enabled) {
					out.add(r);
				}
			}

			return out;
		} else {
			return out;
		}
	}

	public void applyCensoring(byte[] rgb, int width, int height) {
		if (rgb != null && width > 0 && height > 0) {
			if ((long)rgb.length >= (long)width * (long)height * 3L) {
				RecordableConfig config = RecordableConfig.get();
				if (config != null && config.streamerModeEnabled && config.censorRegions != null) {
					for (CensorRegion region : config.censorRegions) {
						if (region != null && region.enabled) {
							int px = (int)Math.round(region.x * (double)width);
							int py = (int)Math.round(region.y * (double)height);
							int pw = (int)Math.round(region.width * (double)width);
							int ph = (int)Math.round(region.height * (double)height);
							if (px < 0) {
								pw += px;
								px = 0;
							}

							if (py < 0) {
								ph += py;
								py = 0;
							}

							if (px + pw > width) {
								pw = width - px;
							}

							if (py + ph > height) {
								ph = height - py;
							}

							if (pw > 0 && ph > 0) {
								Style style = region.style != null ? region.style : Style.SOLID;
								if (style == Style.GRADIENT) {
									fillGradient(
										rgb,
										width,
										px,
										py,
										pw,
										ph,
										region.color & 16777215,
										region.colorEnd & 16777215,
										region.gradientDirection != null ? region.gradientDirection : GradientDirection.HORIZONTAL
									);
								} else {
									fillSolid(rgb, width, px, py, pw, ph, region.color & 16777215);
								}

								if (region.showLabel && region.label != null && !region.label.isBlank()) {
									drawLabel(rgb, width, height, px, py, pw, ph, region.label.trim(), region.textColor & 16777215);
								}
							}
						}
					}
				}
			}
		}
	}

	private static void fillSolid(byte[] rgb, int frameWidth, int x, int y, int w, int h, int color) {
		byte cr = (byte)(color >> 16 & 0xFF);
		byte cg = (byte)(color >> 8 & 0xFF);
		byte cb = (byte)(color & 0xFF);
		int rowStride = frameWidth * 3;

		for (int row = y; row < y + h; row++) {
			int base = row * rowStride + x * 3;
			int end = base + w * 3;

			for (int i = base; i < end; i += 3) {
				rgb[i] = cr;
				rgb[i + 1] = cg;
				rgb[i + 2] = cb;
			}
		}
	}

	private static void fillGradient(byte[] rgb, int frameWidth, int x, int y, int w, int h, int colorStart, int colorEnd, GradientDirection direction) {
		int sr = colorStart >> 16 & 0xFF;
		int sg = colorStart >> 8 & 0xFF;
		int sb = colorStart & 0xFF;
		int er = colorEnd >> 16 & 0xFF;
		int eg = colorEnd >> 8 & 0xFF;
		int eb = colorEnd & 0xFF;
		int rowStride = frameWidth * 3;
		int denomX = Math.max(1, w - 1);
		int denomY = Math.max(1, h - 1);
		int denomD = Math.max(1, w - 1 + (h - 1));

		for (int row = 0; row < h; row++) {
			int base = (y + row) * rowStride + x * 3;

			for (int col = 0; col < w; col++) {
				double t = switch (direction) {
					case VERTICAL -> (double)row / (double)denomY;
					case DIAGONAL -> (double)(col + row) / (double)denomD;
					default -> (double)col / (double)denomX;
				};
				if (t < 0.0) {
					t = 0.0;
				} else if (t > 1.0) {
					t = 1.0;
				}

				int idx = base + col * 3;
				rgb[idx] = (byte)((int)Math.round((double)sr + (double)(er - sr) * t));
				rgb[idx + 1] = (byte)((int)Math.round((double)sg + (double)(eg - sg) * t));
				rgb[idx + 2] = (byte)((int)Math.round((double)sb + (double)(eb - sb) * t));
			}
		}
	}

	private static void drawLabel(byte[] rgb, int frameWidth, int frameHeight, int x, int y, int w, int h, String label, int textColor) {
		int baseWidth = CensorFont.textWidth(label);
		if (baseWidth > 0) {
			int maxByWidth = (int)Math.floor((double)w * 0.85 / (double)baseWidth);
			int maxByHeight = (int)Math.floor((double)h * 0.6 / 7.0);
			int scale = Math.min(maxByWidth, maxByHeight);
			if (scale >= 1) {
				int textW = baseWidth * scale;
				int textH = 7 * scale;
				int startX = x + (w - textW) / 2;
				int startY = y + (h - textH) / 2;
				CensorFont.drawText(rgb, frameWidth, frameHeight, startX, startY, label, textColor, scale);
			}
		}
	}
}
