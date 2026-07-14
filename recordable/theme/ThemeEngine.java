package dev.recordable.theme;

import dev.recordable.RecordableConfig;
import dev.recordable.RecordableMod;

public final class ThemeEngine {
	private static final ThemeEngine INSTANCE = new ThemeEngine();
	private volatile ThemePreset activePreset = ThemePreset.VHS;
	private volatile ThemeColors colors = ThemeColors.vhs();
	private volatile boolean scanlineEnabled = true;
	private volatile boolean grainEnabled = true;
	private volatile boolean glitchEnabled = true;
	private volatile boolean vignetteEnabled = true;
	private volatile boolean animationsEnabled = true;

	private ThemeEngine() {
	}

	public static ThemeEngine get() {
		return INSTANCE;
	}

	public void loadFromConfig() {
		try {
			RecordableConfig config = RecordableConfig.get();
			if (config == null) {
				return;
			}

			ThemePreset preset = config.uiTheme;
			if (preset == null) {
				preset = ThemePreset.VHS;
			}

			this.applyPreset(preset);
			this.scanlineEnabled = config.uiScanlines;
			this.grainEnabled = config.uiFilmGrain;
			this.glitchEnabled = config.uiGlitchEffects;
			this.vignetteEnabled = config.uiVignette;
			this.animationsEnabled = config.uiAnimations;
		} catch (Exception var3) {
			RecordableMod.LOGGER.debug("Failed to load theme from config", var3);
		}
	}

	public void applyPreset(ThemePreset preset) {
		this.activePreset = preset;
		this.colors = ThemeColors.forPreset(preset);
	}

	public ThemePreset preset() {
		return this.activePreset;
	}

	public ThemeColors colors() {
		return this.colors;
	}

	public boolean scanlineEnabled() {
		return this.scanlineEnabled && (this.colors.scanlineColor & 0xFF000000) != 0;
	}

	public boolean grainEnabled() {
		return this.grainEnabled && (this.colors.grainColor & 0xFF000000) != 0;
	}

	public boolean glitchEnabled() {
		return this.glitchEnabled && (this.colors.glitchColor & 0xFF000000) != 0;
	}

	public boolean vignetteEnabled() {
		return this.vignetteEnabled && (this.colors.vignetteColor & 0xFF000000) != 0;
	}

	public boolean animationsEnabled() {
		return this.animationsEnabled;
	}

	public static int lerpColor(int a, int b, float t) {
		if (t <= 0.0F) {
			return a;
		} else if (t >= 1.0F) {
			return b;
		} else {
			int aA = a >> 24 & 0xFF;
			int aR = a >> 16 & 0xFF;
			int aG = a >> 8 & 0xFF;
			int aB = a & 0xFF;
			int bA = b >> 24 & 0xFF;
			int bR = b >> 16 & 0xFF;
			int bG = b >> 8 & 0xFF;
			int bB = b & 0xFF;
			return (int)((float)aA + (float)(bA - aA) * t) << 24
				| (int)((float)aR + (float)(bR - aR) * t) << 16
				| (int)((float)aG + (float)(bG - aG) * t) << 8
				| (int)((float)aB + (float)(bB - aB) * t);
		}
	}

	public static float pulse(long tickMs, int periodMs) {
		float phase = (float)(tickMs % (long)periodMs) / (float)periodMs;
		return (float)(0.5 + 0.5 * Math.sin((double)phase * Math.PI * 2.0));
	}
}
