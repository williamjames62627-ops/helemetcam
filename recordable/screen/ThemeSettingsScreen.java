package dev.recordable.screen;

import dev.recordable.FFmpegEncoder;
import dev.recordable.RecordableConfig;
import dev.recordable.RecordableMod;
import dev.recordable.RecordableModInit;
import dev.recordable.RecordableConfig.VideoEncoder;
import dev.recordable.RecordableModInit.Hotkey;
import dev.recordable.compat.RenderHelper;
import dev.recordable.theme.CycleButton;
import dev.recordable.theme.ThemeColors;
import dev.recordable.theme.ThemeEngine;
import dev.recordable.theme.ThemePreset;
import dev.recordable.theme.ThemedButton;
import dev.recordable.theme.ThemedPanel;
import dev.recordable.theme.ThemedToggle;
import dev.recordable.theme.TypewriterText;
import net.minecraft.class_2561;
import net.minecraft.class_332;
import net.minecraft.class_437;

public final class ThemeSettingsScreen extends class_437 {
	private static final int WIDGET_HEIGHT = 20;
	private static final int ROW_SPACING = 24;
	private final class_437 parent;
	private TypewriterText titleAnim;
	private int panelLeft;
	private int panelTop;
	private int panelWidth;
	private int panelBottom;
	private int previewLeft;
	private int previewTop;
	private int previewRight;
	private int previewBottom;
	private int descriptionY;
	private static boolean loggedSwatchDebug = false;

	public ThemeSettingsScreen(class_437 parent) {
		super(class_2561.method_43471("screen.recordable.theme.title"));
		this.parent = parent;
	}

	protected void method_25426() {
		super.method_25426();
		this.method_37067();
		RecordableConfig config = RecordableConfig.get();
		if (config == null) {
			this.method_25419();
		} else {
			this.titleAnim = new TypewriterText("[ THEME CONFIGURATION ]", 30);
			int totalWidth = Math.max(500, Math.min((int)((double)this.field_22789 * 0.88), 800));
			int totalLeft = (this.field_22789 - totalWidth) / 2;
			this.panelWidth = totalWidth / 2 - 8;
			this.panelLeft = totalLeft;
			this.panelTop = Math.max(24, (int)((double)this.field_22790 * 0.08));
			this.panelBottom = this.field_22790 - 36;
			this.previewLeft = totalLeft + this.panelWidth + 16;
			this.previewTop = this.panelTop;
			this.previewRight = totalLeft + totalWidth;
			this.previewBottom = this.panelBottom;
			int wLeft = this.panelLeft + 10;
			int wWidth = this.panelWidth - 20;
			int y = this.panelTop + 22;
			this.method_37063(CycleButton.create(wLeft, y, wWidth, 20, class_2561.method_43470("Theme: " + config.uiTheme.displayName), button -> {
				config.uiTheme = config.uiTheme.next();
				ThemeEngine.get().applyPreset(config.uiTheme);
				config.save();
				button.method_25355(class_2561.method_43470("Theme: " + config.uiTheme.displayName));
			}, button -> {
				config.uiTheme = config.uiTheme.prev();
				ThemeEngine.get().applyPreset(config.uiTheme);
				config.save();
				button.method_25355(class_2561.method_43470("Theme: " + config.uiTheme.displayName));
			}));
			y += 24;
			this.descriptionY = y;
			y += 14;
			this.method_37063(ThemedToggle.create(wLeft, y, wWidth, 20, "Scanlines", config.uiScanlines, val -> {
				config.uiScanlines = val;
				ThemeEngine.get().loadFromConfig();
				config.save();
			}));
			y += 24;
			this.method_37063(ThemedToggle.create(wLeft, y, wWidth, 20, "Film Grain", config.uiFilmGrain, val -> {
				config.uiFilmGrain = val;
				ThemeEngine.get().loadFromConfig();
				config.save();
			}));
			y += 24;
			this.method_37063(ThemedToggle.create(wLeft, y, wWidth, 20, "Glitch Effects", config.uiGlitchEffects, val -> {
				config.uiGlitchEffects = val;
				ThemeEngine.get().loadFromConfig();
				config.save();
			}));
			y += 24;
			this.method_37063(ThemedToggle.create(wLeft, y, wWidth, 20, "Vignette", config.uiVignette, val -> {
				config.uiVignette = val;
				ThemeEngine.get().loadFromConfig();
				config.save();
			}));
			y += 24;
			this.method_37063(ThemedToggle.create(wLeft, y, wWidth, 20, "Animations", config.uiAnimations, val -> {
				config.uiAnimations = val;
				ThemeEngine.get().loadFromConfig();
				config.save();
			}));
			y += 32;
			this.method_37063(ThemedButton.create(wLeft, y, wWidth, 20, class_2561.method_43470("Reset to Defaults"), button -> {
				config.uiTheme = ThemePreset.VHS;
				config.uiScanlines = true;
				config.uiFilmGrain = true;
				config.uiGlitchEffects = true;
				config.uiVignette = true;
				config.uiAnimations = true;
				config.uiCustomAccentColor = "";
				ThemeEngine.get().loadFromConfig();
				config.save();
				if (this.field_22787 != null) {
					this.field_22787.method_1507(new ThemeSettingsScreen(this.parent));
				}
			}));
			this.method_37063(
				ThemedButton.create((this.field_22789 - 120) / 2, this.panelBottom + 6, 120, 20, class_2561.method_43470("Done"), button -> this.method_25419())
			);
		}
	}

	public void method_25420(class_332 context, int mouseX, int mouseY, float delta) {
		super.method_25420(context, mouseX, mouseY, delta);
		context.method_51452();
		ThemeColors colors = ThemeEngine.get().colors();
		ThemedPanel.drawPanel(context, this.panelLeft - 4, this.panelTop - 4, this.panelLeft + this.panelWidth + 4, this.panelBottom);
		if (this.titleAnim != null) {
			this.titleAnim.render(context, this.field_22793, this.panelLeft + 10, this.panelTop + 8, colors.headerText);
		}

		ThemePreset preset = ThemeEngine.get().preset();
		RenderHelper.drawText(context, this.field_22793, preset.description, this.panelLeft + 10, this.descriptionY, colors.textMuted);
		this.renderPreview(context);
	}

	public void method_25394(class_332 context, int mouseX, int mouseY, float delta) {
		super.method_25394(context, mouseX, mouseY, delta);
	}

	private static String previewEncoderName() {
		try {
			RecordableConfig c = RecordableConfig.get();
			if (c != null && c.encoder != null) {
				if (c.encoder == VideoEncoder.SOFTWARE) {
					String codec = FFmpegEncoder.getCachedSoftwareCodec();
					if (codec != null) {
						return "Software (" + prettyCodecName(codec) + ")";
					}
				}

				return c.encoder.displayName;
			}
		} catch (Throwable var2) {
		}

		return VideoEncoder.SOFTWARE.displayName;
	}

	private static String prettyCodecName(String codec) {
		if (codec == null) {
			return "?";
		} else {
			switch (codec) {
				case "libx264":
					return "x264";
				case "libx265":
					return "x265";
				case "mpeg4":
					return "MPEG-4";
				case "libxvid":
					return "Xvid";
				case "h264_mediacodec":
					return "HW H.264";
				default:
					return codec;
			}
		}
	}

	private static String previewResolution() {
		try {
			RecordableConfig c = RecordableConfig.get();
			if (c != null && c.resolution != null && !c.resolution.isBlank()) {
				return c.resolution;
			}
		} catch (Throwable var1) {
		}

		return "1080p";
	}

	private static int previewFps() {
		try {
			RecordableConfig c = RecordableConfig.get();
			if (c != null && c.fps > 0) {
				return c.fps;
			}
		} catch (Throwable var1) {
		}

		return 60;
	}

	private void renderPreview(class_332 context) {
		ThemeColors colors = ThemeEngine.get().colors();
		ThemePreset preset = ThemeEngine.get().preset();
		if (preset == ThemePreset.CINEMA) {
			ThemedPanel.drawFilmPanel(context, this.previewLeft, this.previewTop, this.previewRight, this.previewBottom);
		} else {
			ThemedPanel.drawPanel(context, this.previewLeft, this.previewTop, this.previewRight, this.previewBottom);
		}

		int px = this.previewLeft + 14;
		int pw = this.previewRight - this.previewLeft - 28;
		int py = this.previewTop + 12;
		int panelH = this.previewBottom - this.previewTop;
		ThemedPanel.drawSectionHeader(context, this.field_22793, "Live Preview", px, py, pw);
		py += 22;
		ThemedPanel.drawSectionHeader(context, this.field_22793, "Recording Settings", px, py, pw);
		py += 18;
		RenderHelper.drawText(context, this.field_22793, "Resolution: " + previewResolution(), px + 8, py, colors.textPrimary);
		py += 12;
		RenderHelper.drawText(context, this.field_22793, "FPS: " + previewFps(), px + 8, py, colors.textPrimary);
		py += 12;
		RenderHelper.drawText(context, this.field_22793, "Encoder: " + previewEncoderName(), px + 8, py, colors.textSecondary);
		py += 18;
		ThemedPanel.drawDivider(context, px, py, pw);
		py += 14;
		ThemedPanel.drawVhsStatusBadge(context, this.field_22793, "▶ PLAY", px + 6, py, false);
		String recText = "● REC";
		int recTextWidth = this.field_22793.method_1727(recText);
		int recX = Math.min(px + pw - recTextWidth - 8, px + pw / 2 + 20);
		TypewriterText.renderFlickerText(context, this.field_22793, recText, recX, py, colors.accent);
		py += 22;
		ThemedPanel.drawDivider(context, px, py, pw);
		py += 14;
		RenderHelper.drawText(context, this.field_22793, "Quick Keys:", px + 6, py, colors.textMuted);
		py += 14;
		String[] keyLabels = new String[]{
			RecordableModInit.getBoundKeyDisplay(Hotkey.TOGGLE_RECORDING),
			RecordableModInit.getBoundKeyDisplay(Hotkey.PAUSE_RESUME),
			RecordableModInit.getBoundKeyDisplay(Hotkey.ADD_BOOKMARK)
		};
		String[] keyDescs = new String[]{"Record", "Pause", "Bookmark"};
		int badgeH = 12;
		int maxLabelW = 0;

		for (String label : keyLabels) {
			maxLabelW = Math.max(maxLabelW, this.field_22793.method_1727(label));
		}

		int badgeW = maxLabelW + 8;
		int bx = px + 10;

		for (int k = 0; k < keyLabels.length; k++) {
			context.method_25294(bx, py, bx + badgeW, py + badgeH, colors.buttonBackground);
			context.method_25294(bx, py, bx + badgeW, py + 1, colors.buttonBorder);
			context.method_25294(bx, py + badgeH - 1, bx + badgeW, py + badgeH, colors.buttonBorder);
			context.method_25294(bx, py, bx + 1, py + badgeH, colors.buttonBorder);
			context.method_25294(bx + badgeW - 1, py, bx + badgeW, py + badgeH, colors.buttonBorder);
			int keyTextW = this.field_22793.method_1727(keyLabels[k]);
			int keyColor = "Not Bound".equals(keyLabels[k]) ? colors.textMuted : colors.accent;
			RenderHelper.drawText(context, this.field_22793, keyLabels[k], bx + (badgeW - keyTextW) / 2, py + 2, keyColor);
			RenderHelper.drawText(context, this.field_22793, keyDescs[k], bx + badgeW + 6, py + 2, colors.textPrimary);
			py += badgeH + 4;
		}

		py += 6;
		ThemedPanel.drawDivider(context, px, py, pw);
		py += 14;
		RenderHelper.drawText(context, this.field_22793, "Color Palette:", px + 6, py, colors.textMuted);
		py += 14;
		int[] swatches = new int[]{
			colors.accent,
			colors.accentHover,
			colors.accentDim,
			colors.textPrimary,
			colors.textSecondary,
			colors.textMuted,
			colors.panelBackground,
			colors.panelBorder,
			colors.headerUnderline
		};
		int swatchCount = swatches.length;
		int availableW = pw - 16;
		int swatchGap = 3;
		int swatchSize = Math.min(14, (availableW - (swatchCount - 1) * swatchGap) / swatchCount);
		swatchSize = Math.max(6, swatchSize);
		int swatchY = py;
		if (py + swatchSize > this.previewBottom - 4) {
			swatchY = this.previewBottom - swatchSize - 4;
		}

		if (!loggedSwatchDebug) {
			loggedSwatchDebug = true;
			RecordableMod.LOGGER
				.info(
					"[Record-able] Theme swatch render: count={} size={} swatchY={} previewBottom={} px={} pw={} firstColor=0x{}",
					new Object[]{swatchCount, swatchSize, swatchY, this.previewBottom, px, pw, Integer.toHexString(swatches[0])}
				);
		}

		for (int i = 0; i < swatchCount; i++) {
			int sx = px + 8 + i * (swatchSize + swatchGap);
			context.method_25294(sx, swatchY, sx + swatchSize, swatchY + swatchSize, swatches[i]);
			context.method_25294(sx, swatchY, sx + swatchSize, swatchY + 1, -11184811);
			context.method_25294(sx, swatchY + swatchSize - 1, sx + swatchSize, swatchY + swatchSize, -11184811);
			context.method_25294(sx, swatchY, sx + 1, swatchY + swatchSize, -11184811);
			context.method_25294(sx + swatchSize - 1, swatchY, sx + swatchSize, swatchY + swatchSize, -11184811);
		}
	}

	public void method_25419() {
		if (this.field_22787 != null) {
			this.field_22787.method_1507(this.parent);
		}
	}
}
