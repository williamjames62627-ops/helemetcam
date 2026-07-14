package dev.recordable;

import java.util.HashMap;
import java.util.Map;

public final class CensorFont {
	public static final int GLYPH_W = 5;
	public static final int GLYPH_H = 7;
	public static final int GLYPH_GAP = 1;
	private static final Map<Character, int[]> GLYPHS = new HashMap();

	private CensorFont() {
	}

	private static void put(char c, int r0, int r1, int r2, int r3, int r4, int r5, int r6) {
		GLYPHS.put(c, new int[]{r0, r1, r2, r3, r4, r5, r6});
	}

	public static int textWidth(String text) {
		if (text != null && !text.isEmpty()) {
			int n = text.length();
			return n * 5 + (n - 1) * 1;
		} else {
			return 0;
		}
	}

	public static void drawText(byte[] rgb, int frameWidth, int frameHeight, int startX, int startY, String text, int rgbColor, int scale) {
		if (rgb != null && text != null && !text.isEmpty() && scale >= 1) {
			String upper = text.toUpperCase();
			byte cr = (byte)(rgbColor >> 16 & 0xFF);
			byte cg = (byte)(rgbColor >> 8 & 0xFF);
			byte cb = (byte)(rgbColor & 0xFF);
			int rowStride = frameWidth * 3;
			int penX = startX;

			for (int ci = 0; ci < upper.length(); ci++) {
				int[] glyph = (int[])GLYPHS.get(upper.charAt(ci));
				if (glyph != null) {
					for (int gy = 0; gy < 7; gy++) {
						int bits = glyph[gy];

						for (int gx = 0; gx < 5; gx++) {
							boolean on = (bits >> 4 - gx & 1) != 0;
							if (on) {
								int blockX = penX + gx * scale;
								int blockY = startY + gy * scale;

								for (int sy = 0; sy < scale; sy++) {
									int fy = blockY + sy;
									if (fy >= 0 && fy < frameHeight) {
										int rowBase = fy * rowStride;

										for (int sx = 0; sx < scale; sx++) {
											int fx = blockX + sx;
											if (fx >= 0 && fx < frameWidth) {
												int idx = rowBase + fx * 3;
												rgb[idx] = cr;
												rgb[idx + 1] = cg;
												rgb[idx + 2] = cb;
											}
										}
									}
								}
							}
						}
					}
				}

				penX += 6 * scale;
			}
		}
	}

	static {
		put(' ', 0, 0, 0, 0, 0, 0, 0);
		put('A', 14, 17, 17, 31, 17, 17, 17);
		put('B', 30, 17, 17, 30, 17, 17, 30);
		put('C', 15, 16, 16, 16, 16, 16, 15);
		put('D', 30, 17, 17, 17, 17, 17, 30);
		put('E', 31, 16, 16, 30, 16, 16, 31);
		put('F', 31, 16, 16, 30, 16, 16, 16);
		put('G', 15, 16, 16, 23, 17, 17, 15);
		put('H', 17, 17, 17, 31, 17, 17, 17);
		put('I', 31, 4, 4, 4, 4, 4, 31);
		put('J', 7, 2, 2, 2, 18, 18, 12);
		put('K', 17, 18, 20, 24, 20, 18, 17);
		put('L', 16, 16, 16, 16, 16, 16, 31);
		put('M', 17, 27, 21, 21, 17, 17, 17);
		put('N', 17, 25, 21, 21, 19, 17, 17);
		put('O', 14, 17, 17, 17, 17, 17, 14);
		put('P', 30, 17, 17, 30, 16, 16, 16);
		put('Q', 14, 17, 17, 17, 21, 18, 13);
		put('R', 30, 17, 17, 30, 20, 18, 17);
		put('S', 15, 16, 16, 14, 1, 1, 30);
		put('T', 31, 4, 4, 4, 4, 4, 4);
		put('U', 17, 17, 17, 17, 17, 17, 14);
		put('V', 17, 17, 17, 17, 17, 10, 4);
		put('W', 17, 17, 17, 21, 21, 27, 17);
		put('X', 17, 17, 10, 4, 10, 17, 17);
		put('Y', 17, 17, 10, 4, 4, 4, 4);
		put('Z', 31, 1, 2, 4, 8, 16, 31);
		put('0', 14, 17, 19, 21, 25, 17, 14);
		put('1', 4, 12, 4, 4, 4, 4, 14);
		put('2', 14, 17, 1, 2, 4, 8, 31);
		put('3', 31, 2, 4, 2, 1, 17, 14);
		put('4', 2, 6, 10, 18, 31, 2, 2);
		put('5', 31, 16, 30, 1, 1, 17, 14);
		put('6', 14, 16, 16, 30, 17, 17, 14);
		put('7', 31, 1, 2, 4, 8, 8, 8);
		put('8', 14, 17, 17, 14, 17, 17, 14);
		put('9', 14, 17, 17, 15, 1, 1, 14);
		put('.', 0, 0, 0, 0, 0, 12, 12);
		put(':', 0, 12, 12, 0, 12, 12, 0);
		put('-', 0, 0, 0, 31, 0, 0, 0);
		put('_', 0, 0, 0, 0, 0, 0, 31);
		put('!', 4, 4, 4, 4, 4, 0, 4);
		put('?', 14, 17, 1, 6, 4, 0, 4);
		put('/', 1, 1, 2, 4, 8, 16, 16);
		put('#', 10, 10, 31, 10, 31, 10, 10);
		put('+', 0, 4, 4, 31, 4, 4, 0);
		put(',', 0, 0, 0, 0, 12, 4, 8);
	}
}
