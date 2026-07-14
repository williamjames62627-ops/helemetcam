package dev.recordable;

public final class FrameValidator {
	public static final int BLACK_PIXEL_THRESHOLD = 8;
	public static final double BLACK_FRAME_RATIO = 0.995;
	private static final int SAMPLES_PER_AXIS = 32;

	private FrameValidator() {
	}

	public static double averageBrightness(byte[] rgb, int width, int height) {
		if (rgb != null && width > 0 && height > 0) {
			long required = (long)width * (long)height * 3L;
			if ((long)rgb.length < required) {
				return -1.0;
			} else {
				long sum = 0L;
				int count = 0;
				int stepX = Math.max(1, width / 32);
				int stepY = Math.max(1, height / 32);

				for (int y = 0; y < height; y += stepY) {
					int rowBase = y * width * 3;

					for (int x = 0; x < width; x += stepX) {
						int idx = rowBase + x * 3;
						int r = rgb[idx] & 255;
						int g = rgb[idx + 1] & 255;
						int b = rgb[idx + 2] & 255;
						sum += (long)(r * 77 + g * 150 + b * 29 >> 8);
						count++;
					}
				}

				return count == 0 ? -1.0 : (double)sum / (double)count;
			}
		} else {
			return -1.0;
		}
	}

	public static boolean isBlackFrame(byte[] rgb, int width, int height) {
		if (rgb != null && width > 0 && height > 0) {
			long required = (long)width * (long)height * 3L;
			if ((long)rgb.length < required) {
				return false;
			} else {
				int total = 0;
				int black = 0;
				int stepX = Math.max(1, width / 32);
				int stepY = Math.max(1, height / 32);

				for (int y = 0; y < height; y += stepY) {
					int rowBase = y * width * 3;

					for (int x = 0; x < width; x += stepX) {
						int idx = rowBase + x * 3;
						int r = rgb[idx] & 255;
						int g = rgb[idx + 1] & 255;
						int b = rgb[idx + 2] & 255;
						total++;
						if (r <= 8 && g <= 8 && b <= 8) {
							black++;
						}
					}
				}

				return total == 0 ? false : (double)black / (double)total >= 0.995;
			}
		} else {
			return false;
		}
	}
}
