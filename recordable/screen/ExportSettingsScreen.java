package dev.recordable.screen;

import dev.recordable.RecordableConfig;
import dev.recordable.theme.CycleButton;
import dev.recordable.theme.ThemedButton;
import dev.recordable.theme.ThemedSlider;
import net.minecraft.class_2561;
import net.minecraft.class_332;
import net.minecraft.class_4185;
import net.minecraft.class_437;

public final class ExportSettingsScreen extends class_437 {
	private static final int WIDGET_HEIGHT = 20;
	private static final int ROW_SPACING = 24;
	private static final int PANEL_W = 400;
	private static final String[] EXPORT_FORMATS = new String[]{"mp4", "mkv", "mov", "avi", "webm"};
	private static final String[] VIDEO_CODECS = new String[]{"h264", "h265", "vp9"};
	private static final String[] AUDIO_CODECS = new String[]{"aac", "mp3", "opus"};
	private static final String[] EXPORT_RESOLUTIONS = new String[]{"native", "1080p", "720p", "480p"};
	private static final int[] EXPORT_FPS_VALUES = new int[]{0, 24, 30, 60, 120};
	private final class_437 parent;
	private int panelX;
	private int panelY;
	private int panelBottom;

	public ExportSettingsScreen(class_437 parent) {
		super(class_2561.method_43470("Export Settings"));
		this.parent = parent;
	}

	protected void method_25426() {
		super.method_25426();
		RecordableConfig config = RecordableConfig.get();
		if (config == null) {
			this.method_25419();
		} else {
			this.panelX = (this.field_22789 - 400) / 2;
			this.panelY = 30;
			this.panelBottom = this.field_22790 - 20;
			int innerW = 376;
			int gap = 8;
			int halfW = (innerW - gap) / 2;
			int colL = this.panelX + 12;
			int colR = colL + halfW + gap;
			int y = this.panelY + 34;
			CycleButton formatButton = CycleButton.create(colL, y, innerW, 20, class_2561.method_43470("Format: " + this.getFormatDisplay(config)), b -> {
				config.exportFormat = this.nextFormat(config.exportFormat);
				config.save();
				b.method_25355(class_2561.method_43470("Format: " + this.getFormatDisplay(config)));
			}, b -> {
				config.exportFormat = this.prevFormat(config.exportFormat);
				config.save();
				b.method_25355(class_2561.method_43470("Format: " + this.getFormatDisplay(config)));
			});
			this.method_37063(formatButton);
			y += 24;
			CycleButton videoCodecButton = CycleButton.create(colL, y, halfW, 20, class_2561.method_43470("Codec: " + this.getVideoCodecDisplay(config)), b -> {
				config.exportVideoCodec = this.nextVideoCodec(config.exportVideoCodec);
				config.save();
				b.method_25355(class_2561.method_43470("Codec: " + this.getVideoCodecDisplay(config)));
			}, b -> {
				config.exportVideoCodec = this.prevVideoCodec(config.exportVideoCodec);
				config.save();
				b.method_25355(class_2561.method_43470("Codec: " + this.getVideoCodecDisplay(config)));
			});
			this.method_37063(videoCodecButton);
			ThemedSlider videoBitrateSlider = new ThemedSlider(colR, y, halfW, 20, "Bitrate: %d Mbps", 0.0, 50.0, (double)config.exportVideoBitrateMbps, v -> {
				config.exportVideoBitrateMbps = v.intValue();
				config.save();
			});
			this.method_37063(videoBitrateSlider);
			y += 24;
			CycleButton audioCodecButton = CycleButton.create(colL, y, halfW, 20, class_2561.method_43470("Audio: " + this.getAudioCodecDisplay(config)), b -> {
				config.exportAudioCodec = this.nextAudioCodec(config.exportAudioCodec);
				config.save();
				b.method_25355(class_2561.method_43470("Audio: " + this.getAudioCodecDisplay(config)));
			}, b -> {
				config.exportAudioCodec = this.prevAudioCodec(config.exportAudioCodec);
				config.save();
				b.method_25355(class_2561.method_43470("Audio: " + this.getAudioCodecDisplay(config)));
			});
			this.method_37063(audioCodecButton);
			ThemedSlider audioBitrateSlider = new ThemedSlider(colR, y, halfW, 20, "ABR: %d kbps", 0.0, 320.0, (double)config.exportAudioBitrateKbps, v -> {
				config.exportAudioBitrateKbps = v.intValue();
				config.save();
			});
			this.method_37063(audioBitrateSlider);
			y += 24;
			CycleButton resolutionButton = CycleButton.create(colL, y, halfW, 20, class_2561.method_43470("Res: " + this.getResolutionDisplay(config)), b -> {
				config.exportResolution = this.nextResolution(config.exportResolution);
				config.save();
				b.method_25355(class_2561.method_43470("Res: " + this.getResolutionDisplay(config)));
			}, b -> {
				config.exportResolution = this.prevResolution(config.exportResolution);
				config.save();
				b.method_25355(class_2561.method_43470("Res: " + this.getResolutionDisplay(config)));
			});
			this.method_37063(resolutionButton);
			CycleButton fpsButton = CycleButton.create(colR, y, halfW, 20, class_2561.method_43470("FPS: " + this.getFpsDisplay(config)), b -> {
				config.exportFps = this.nextFps(config.exportFps);
				config.save();
				b.method_25355(class_2561.method_43470("FPS: " + this.getFpsDisplay(config)));
			}, b -> {
				config.exportFps = this.prevFps(config.exportFps);
				config.save();
				b.method_25355(class_2561.method_43470("FPS: " + this.getFpsDisplay(config)));
			});
			this.method_37063(fpsButton);
			y += 34;
			class_4185 resetButton = ThemedButton.create(colL, y, innerW, 20, class_2561.method_43470("Reset to Defaults"), b -> {
				config.exportFormat = "";
				config.exportVideoCodec = "";
				config.exportVideoBitrateMbps = 0;
				config.exportAudioCodec = "";
				config.exportAudioBitrateKbps = 0;
				config.exportResolution = "";
				config.exportFps = 0;
				config.save();
				if (this.field_22787 != null) {
					this.field_22787.method_1507(new ExportSettingsScreen(this.parent));
				}
			});
			this.method_37063(resetButton);
			y += 24;
			class_4185 closeButton = ThemedButton.create(colL, this.field_22790 - 40, innerW, 20, class_2561.method_43470("Done"), b -> this.method_25419());
			this.method_37063(closeButton);
		}
	}

	public void method_25394(class_332 context, int mouseX, int mouseY, float delta) {
		super.method_25394(context, mouseX, mouseY, delta);
		context.method_27534(this.field_22793, this.field_22785, this.field_22789 / 2, this.panelY + 10, 16777215);
	}

	public void method_25419() {
		if (this.field_22787 != null) {
			this.field_22787.method_1507(this.parent);
		}
	}

	private String getFormatDisplay(RecordableConfig cfg) {
		return cfg.exportFormat.isEmpty() ? "Auto" : cfg.exportFormat.toUpperCase();
	}

	private String getVideoCodecDisplay(RecordableConfig cfg) {
		return cfg.exportVideoCodec.isEmpty() ? "Auto" : cfg.exportVideoCodec.toUpperCase();
	}

	private String getAudioCodecDisplay(RecordableConfig cfg) {
		return cfg.exportAudioCodec.isEmpty() ? "Auto" : cfg.exportAudioCodec.toUpperCase();
	}

	private String getResolutionDisplay(RecordableConfig cfg) {
		return cfg.exportResolution.isEmpty() ? "Recording" : cfg.exportResolution;
	}

	private String getFpsDisplay(RecordableConfig cfg) {
		return cfg.exportFps == 0 ? "Recording" : String.valueOf(cfg.exportFps);
	}

	private String nextFormat(String current) {
		if (current.isEmpty()) {
			return EXPORT_FORMATS[0];
		} else {
			for (int i = 0; i < EXPORT_FORMATS.length; i++) {
				if (EXPORT_FORMATS[i].equals(current)) {
					return i == EXPORT_FORMATS.length - 1 ? "" : EXPORT_FORMATS[i + 1];
				}
			}

			return EXPORT_FORMATS[0];
		}
	}

	private String prevFormat(String current) {
		if (current.isEmpty()) {
			return EXPORT_FORMATS[EXPORT_FORMATS.length - 1];
		} else {
			for (int i = 0; i < EXPORT_FORMATS.length; i++) {
				if (EXPORT_FORMATS[i].equals(current)) {
					return i == 0 ? "" : EXPORT_FORMATS[i - 1];
				}
			}

			return EXPORT_FORMATS[0];
		}
	}

	private String nextVideoCodec(String current) {
		if (current.isEmpty()) {
			return VIDEO_CODECS[0];
		} else {
			for (int i = 0; i < VIDEO_CODECS.length; i++) {
				if (VIDEO_CODECS[i].equals(current)) {
					return i == VIDEO_CODECS.length - 1 ? "" : VIDEO_CODECS[i + 1];
				}
			}

			return VIDEO_CODECS[0];
		}
	}

	private String prevVideoCodec(String current) {
		if (current.isEmpty()) {
			return VIDEO_CODECS[VIDEO_CODECS.length - 1];
		} else {
			for (int i = 0; i < VIDEO_CODECS.length; i++) {
				if (VIDEO_CODECS[i].equals(current)) {
					return i == 0 ? "" : VIDEO_CODECS[i - 1];
				}
			}

			return VIDEO_CODECS[0];
		}
	}

	private String nextAudioCodec(String current) {
		if (current.isEmpty()) {
			return AUDIO_CODECS[0];
		} else {
			for (int i = 0; i < AUDIO_CODECS.length; i++) {
				if (AUDIO_CODECS[i].equals(current)) {
					return i == AUDIO_CODECS.length - 1 ? "" : AUDIO_CODECS[i + 1];
				}
			}

			return AUDIO_CODECS[0];
		}
	}

	private String prevAudioCodec(String current) {
		if (current.isEmpty()) {
			return AUDIO_CODECS[AUDIO_CODECS.length - 1];
		} else {
			for (int i = 0; i < AUDIO_CODECS.length; i++) {
				if (AUDIO_CODECS[i].equals(current)) {
					return i == 0 ? "" : AUDIO_CODECS[i - 1];
				}
			}

			return AUDIO_CODECS[0];
		}
	}

	private String nextResolution(String current) {
		if (current.isEmpty()) {
			return EXPORT_RESOLUTIONS[0];
		} else {
			for (int i = 0; i < EXPORT_RESOLUTIONS.length; i++) {
				if (EXPORT_RESOLUTIONS[i].equals(current)) {
					return i == EXPORT_RESOLUTIONS.length - 1 ? "" : EXPORT_RESOLUTIONS[i + 1];
				}
			}

			return EXPORT_RESOLUTIONS[0];
		}
	}

	private String prevResolution(String current) {
		if (current.isEmpty()) {
			return EXPORT_RESOLUTIONS[EXPORT_RESOLUTIONS.length - 1];
		} else {
			for (int i = 0; i < EXPORT_RESOLUTIONS.length; i++) {
				if (EXPORT_RESOLUTIONS[i].equals(current)) {
					return i == 0 ? "" : EXPORT_RESOLUTIONS[i - 1];
				}
			}

			return EXPORT_RESOLUTIONS[0];
		}
	}

	private int nextFps(int current) {
		for (int i = 0; i < EXPORT_FPS_VALUES.length; i++) {
			if (EXPORT_FPS_VALUES[i] == current) {
				return i == EXPORT_FPS_VALUES.length - 1 ? EXPORT_FPS_VALUES[0] : EXPORT_FPS_VALUES[i + 1];
			}
		}

		return EXPORT_FPS_VALUES[0];
	}

	private int prevFps(int current) {
		for (int i = 0; i < EXPORT_FPS_VALUES.length; i++) {
			if (EXPORT_FPS_VALUES[i] == current) {
				return i == 0 ? EXPORT_FPS_VALUES[EXPORT_FPS_VALUES.length - 1] : EXPORT_FPS_VALUES[i - 1];
			}
		}

		return EXPORT_FPS_VALUES[0];
	}
}
