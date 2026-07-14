package dev.recordable;

public final class SmoothMotion {
	public static final String MODE_BLEND = "blend";
	public static final String MODE_MOTION = "motion";
	public static final String[] MODES = new String[]{"blend", "motion"};

	private SmoothMotion() {
	}

	public static String buildFilter(RecordableConfig config, int outputFps) {
		if (config != null && config.smoothMotionEnabled) {
			int fps = Math.max(1, outputFps);
			String mode = sanitizeMode(config.smoothMotionMode);
			return "motion".equals(mode) ? "minterpolate=fps=" + fps + ":mi_mode=mci:mc_mode=aobmc:me_mode=bidir:vsbmc=1" : "minterpolate=fps=" + fps + ":mi_mode=blend";
		} else {
			return null;
		}
	}

	public static String describe(String mode) {
		String var1 = sanitizeMode(mode);
		byte var2 = -1;
		switch (var1.hashCode()) {
			case -1068318794:
				if (var1.equals("motion")) {
					var2 = 0;
				}
			default:
				return switch (var2) {
					case 0 -> "Motion (smoothest, heavier CPU)";
					default -> "Blend (light, balanced)";
				};
		}
	}

	public static String sanitizeMode(String mode) {
		if (mode == null) {
			return "blend";
		} else {
			String m = mode.trim().toLowerCase();

			for (String valid : MODES) {
				if (valid.equals(m)) {
					return valid;
				}
			}

			return "blend";
		}
	}
}
