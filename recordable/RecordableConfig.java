package dev.recordable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.recordable.filter.FilterType;
import dev.recordable.theme.ThemeColors;
import dev.recordable.theme.ThemePreset;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;

public final class RecordableConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String CONFIG_FILE_NAME = "recordable.json";
	private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?$");
	public static final String[] FORMATS = new String[]{"mp4", "mkv", "mov", "webm"};
	public static final int[] FPS_VALUES = new int[]{30, 60, 120};
	public static final int[] AUTO_CLIP_FPS_VALUES = new int[]{15, 20, 24, 30, 45, 60};
	public static final String[] RESOLUTIONS = new String[]{"native", "1080p", "720p", "480p"};
	public static final String[] QUALITIES = new String[]{"high", "balanced", "performance"};
	public static final int ANDROID_MAX_FPS = 30;
	public static final int ANDROID_CAPTURE_SCALE_DIVISOR = 1;
	public static final String[] REPLAY_QUALITIES = new String[]{"source", "balanced", "performance", "high"};
	public static final String[] AUTO_RECORD_TRIGGERS = new String[]{"world_join", "game_start", "manual"};
	public static final String[] AUTO_STOP_TRIGGERS = new String[]{"world_leave", "game_quit", "never"};
	public static final String[] AUDIO_CHANNELS = new String[]{"auto", "mono", "stereo"};
	public static final int[] AUDIO_SAMPLE_RATES = new int[]{44100, 48000};
	private static volatile RecordableConfig instance;
	private static Path configPath;
	public String format = "mp4";
	public int fps = 60;
	public String resolution = "1080p";
	public String quality = "balanced";
	public String bitrate = "auto";
	public RecordableConfig.VideoEncoder encoder = RecordableConfig.VideoEncoder.SOFTWARE;
	public boolean captureAudio = true;
	public String audioSource = "game";
	public String audioDevice = "auto";
	public RecordableConfig.AudioEncoder audioEncoder = RecordableConfig.AudioEncoder.AAC;
	public int audioBitrateKbps = 192;
	public int audioSampleRate = 48000;
	public int audioChannelCount = 2;
	public int audioVolume = 100;
	public int audioVolumeBoostDb = 0;
	public boolean captureMicrophone = false;
	public String microphoneDevice = "auto";
	public int gameAudioVolume = 100;
	public int microphoneVolume = 80;
	public boolean microphonePushToTalk = false;
	public boolean noiseSuppression = false;
	public String audioBitrate = "192k";
	public String audioChannels = "auto";
	public boolean useFFmpegIfAvailable = true;
	public boolean useBundledFfmpeg = true;
	public String bundledFfmpegPath = "";
	public String ffmpegPath = "";
	public boolean enabled = true;
	public String outputDir = "recordings";
	public boolean showOverlay = true;
	public boolean ffmpegFirstRunShown = false;
	public boolean bakeInOverlay = false;
	public boolean showHomeButton = true;
	public boolean stopOnDisconnect = true;
	public boolean showPerformanceStats = false;
	public boolean saveToGalleryOnAndroid = true;
	public boolean autoCompressOnAndroid = false;
	public int maxFileSizeMB = 0;
	public String overlayColor = "#FF0000";
	public String menuAccentColor = "#FF0000";
	public RecordableConfig.OverlayPosition overlayPosition = RecordableConfig.OverlayPosition.TOP_LEFT;
	public int overlayScale = 100;
	public RecordableConfig.AudioDelayPreset audioDelayPreset = RecordableConfig.AudioDelayPreset.AUTO;
	public int audioSyncOffsetMs = 46;
	public boolean autoRecord = true;
	public String autoRecordTrigger = "world_join";
	public String autoStopTrigger = "world_leave";
	public int autoRecordDelay = 2;
	public static final int DEFAULT_HOTKEY_TOGGLE_RECORDING = 45;
	public static final int DEFAULT_HOTKEY_PAUSE_RESUME = 61;
	public static final int DEFAULT_HOTKEY_OPEN_SETTINGS = 298;
	public static final int DEFAULT_HOTKEY_OPEN_VIDEO_COLLECTION = 301;
	public static final int DEFAULT_HOTKEY_PUSH_TO_TALK = 86;
	public int hotkeyToggleRecording = 45;
	public int hotkeyPauseResume = 61;
	public int hotkeyOpenSettings = 298;
	public int hotkeyOpenVideoCollection = 301;
	public int hotkeyPushToTalk = 86;
	public int hotkeyToggleCensorOverlay = -1;
	public int hotkeyOpenCensorEditor = -1;
	public static final String[] TEMPLATES = new String[]{"custom", "cinematic", "balanced", "pvp_clip"};
	public String activeTemplate = "custom";
	public int diskSpaceWarnPercent = 90;
	public int diskSpaceBlockPercent = 95;
	public int diskSpaceMinFreeMB = 500;
	public boolean replayBufferEnabled = false;
	public int replayBufferDurationSeconds = 30;
	public boolean showRecordingTimer = true;
	public boolean showEstimatedFileSize = true;
	public boolean showPostRecordingToast = true;
	public RecordableConfig.OverlayStyleHud overlayStyleHud = RecordableConfig.OverlayStyleHud.CLASSIC;
	public boolean overlaySkinEnabled = false;
	public String vhsPlayColor = "#FFFFFF";
	public String vhsRecTextColor = "#FFFFFF";
	public String vhsRecDotColor = "#CC1E1E";
	public String vhsBracketColor = "#C8FFFFFF";
	public String vhsTimestampColor = "#FFFFFF";
	public String vhsDateColor = "#FFFFFF";
	public String vhsSpColor = "#FFFFFF";
	public boolean vhsShowBrackets = true;
	public boolean vhsShowPlay = true;
	public boolean vhsShowDate = true;
	public boolean vhsShowSp = true;
	public boolean vhsShowBattery = true;
	public boolean vhsShowAudioMeter = true;
	public boolean vhsShowTapeCounter = true;
	public int hudPlayRecX = 80;
	public int hudPlayRecY = 14;
	public int hudTimestampOffsetX = 14;
	public int hudTimestampY = 14;
	public int hudSpX = 80;
	public int hudSpOffsetY = 24;
	public int hudPerfOffsetX = 8;
	public int hudPerfOffsetY = 80;
	public int hudDetailsOffsetX = 14;
	public int hudDetailsOffsetY = 14;
	public int hudCornersX = 68;
	public int hudCornersY = 4;
	public int hudCornersWidth = 100;
	public int hudCornersHeight = 48;
	public int hudPlayRecW = 0;
	public int hudPlayRecH = 0;
	public int hudTimestampW = 0;
	public int hudTimestampH = 0;
	public int hudSpW = 0;
	public int hudSpH = 0;
	public int hudPerfW = 0;
	public int hudPerfH = 0;
	public int hudDetailsW = 0;
	public int hudDetailsH = 0;
	public int hudPlayRecOpacity = 100;
	public int hudTimestampOpacity = 100;
	public int hudCornersOpacity = 100;
	public int hudSpOpacity = 100;
	public int hudDetailsOpacity = 100;
	public int hudPerfOpacity = 100;
	public String hudLayerOrder = defaultLayerOrder();
	public boolean hudPlayRecVisible = true;
	public boolean hudTimestampVisible = true;
	public boolean hudCornersVisible = true;
	public boolean hudSpVisible = true;
	public boolean hudDetailsVisible = true;
	public boolean hudPerfVisible = true;
	public boolean showFiltersLive = true;
	public boolean filterVhsVisible = false;
	public boolean filterLcdMoireVisible = false;
	public boolean filterCrtVisible = false;
	public int filterVhsIntensity = 75;
	public int filterLcdMoireIntensity = 75;
	public int filterCrtIntensity = 75;
	public int hudMicX = -1;
	public int hudMicY = 4;
	public boolean hudMicVisible = true;
	public int hudMicOpacity = 100;
	public int hudClassicX = -1;
	public int hudClassicY = -1;
	public boolean hudClassicVisible = true;
	public int hudSynthX = -1;
	public int hudSynthY = -1;
	public boolean hudSynthVisible = true;
	public boolean bookmarksEnabled = true;
	public boolean autoClipEnabled = false;
	public boolean autoClipOnAchievement = false;
	public boolean autoClipOnDeath = false;
	public boolean autoClipOnDimensionChange = false;
	public boolean autoClipOnBossKill = false;
	public boolean autoClipOnKill = false;
	public boolean autoClipOnPlayerKill = false;
	public int autoClipDuration = 30;
	public boolean autoClipKillMontage = true;
	public int autoClipKillPreSeconds = 1;
	public int autoClipKillPostSeconds = 1;
	public int autoClipFps = 30;
	public boolean autoClipAudio = true;
	public boolean notifyRecording = true;
	public boolean notifyClips = true;
	public boolean notifyReplayBuffer = true;
	public boolean notifyAutoRecord = true;
	public boolean notifyBookmarks = true;
	public boolean notifyWarnings = true;
	public boolean replayCompatBridge = true;
	public boolean replayAutoRecordPlayback = false;
	public boolean replayYieldAudioDevice = false;
	public String replayBufferQuality = "balanced";
	public int hotkeySaveReplayBuffer = -1;
	public boolean replayBufferNotify = true;
	public static final String[] GALLERY_SORT_MODES = new String[]{"newest", "oldest", "name_az", "name_za", "largest", "smallest", "longest", "shortest"};
	public String gallerySortMode = "newest";
	public boolean galleryShowMetadata = true;
	public int galleryColumns = 3;
	public boolean markersEnabled = true;
	public int hotkeyAddBookmark = -1;
	public boolean exportChapterFile = true;
	public boolean embedChaptersInVideo = false;
	public boolean autoMarkerOnStart = true;
	public boolean watermarksEnabled = false;
	public boolean showWatermarksLive = true;
	public List<WatermarkSlot> watermarkSlots = new ArrayList();
	public static final int MAX_WATERMARK_SLOTS = 4;
	public boolean separateAudioTracks = false;
	public boolean trackGameAudio = true;
	public boolean trackMicAudio = true;
	public boolean trackMusicAudio = false;
	public boolean autoCleanupEnabled = false;
	public int autoCleanupOlderThanDays = 30;
	public int autoCleanupMaxTotalMB = 0;
	public List<String> storageProtectedFiles = new ArrayList();
	public int storageCompressionCrf = 28;
	public boolean perfOptimizerEnabled = false;
	public boolean perfAutoAdjust = false;
	public int perfMinFps = 45;
	public boolean perfModeGamePriority = true;
	public boolean perfActionLowerRes = true;
	public boolean perfActionLowerFps = true;
	public boolean perfActionFasterPreset = true;
	public boolean perfWarnBeforeAdjust = false;
	public boolean perfShowStatsOverlay = false;
	public String selectedDevicePreset = "mid_end_pc";
	public boolean frameBufferPoolingEnabled = true;
	public boolean smoothMotionEnabled = false;
	public String smoothMotionMode = "blend";
	public boolean hideChat = false;
	public boolean hideCrosshair = false;
	public boolean hideHotbar = false;
	public boolean hideBossBar = false;
	public boolean hideHand = false;
	public boolean hideScoreboard = false;
	public boolean hideVignette = false;
	public String exportFormat = "";
	public String exportVideoCodec = "";
	public int exportVideoBitrateMbps = 0;
	public String exportAudioCodec = "";
	public int exportAudioBitrateKbps = 0;
	public String exportResolution = "";
	public int exportFps = 0;
	public boolean streamerModeEnabled = false;
	public boolean streamerShowCensorPreview = false;
	public boolean censorOverlayHidden = false;
	public String streamerDefaultCensorStyle = "SOLID";
	public List<CensorRegion> censorRegions = new ArrayList();
	public ThemePreset uiTheme = ThemePreset.VHS;
	public boolean uiScanlines = true;
	public boolean uiFilmGrain = true;
	public boolean uiGlitchEffects = true;
	public boolean uiVignette = true;
	public boolean uiAnimations = true;
	public String uiCustomAccentColor = "";
	public static final String[] FILTER_LAYERS = new String[]{"Filter:VHS", "Filter:LCD_MOIRE", "Filter:CRT"};
	private static final String[] HUD_LAYERS = new String[]{"Corners", "PLAY/REC", "Timestamp", "SP", "Details", "Perf", "Mic"};
	private static final String[] PANEL_LAYERS = new String[]{"Classic", "Synthwave"};
	private static final String[] VHS_HUD_LAYERS = new String[]{"Corners", "PLAY/REC", "Timestamp", "SP", "Details", "Perf"};
	public static final String[] DEVICE_PRESETS = new String[]{"android_phone", "low_end_pc", "mid_end_pc", "high_end_pc", "nasa"};

	public int getEffectiveAudioDelay() {
		if (this.audioDelayPreset == null) {
			this.audioDelayPreset = RecordableConfig.AudioDelayPreset.AUTO;
		}
		return switch (this.audioDelayPreset) {
			case AUTO -> 0;
			case NONE -> 0;
			case DESKTOP -> 46;
			case ANDROID -> 60;
			case CUSTOM -> Math.max(0, Math.min(500, this.audioSyncOffsetMs));
		};
	}

	public ThemeColors overlaySkin() {
		return ThemeColors.forPreset(this.uiTheme != null ? this.uiTheme : ThemePreset.CLASSIC);
	}

	public ThemeColors activeOverlaySkinOrNull() {
		return this.overlaySkinEnabled ? this.overlaySkin() : null;
	}

	private RecordableConfig() {
	}

	public static RecordableConfig get() {
		RecordableConfig inst = instance;
		if (inst == null) {
			synchronized (RecordableConfig.class) {
				inst = instance;
				if (inst == null) {
					load();
					inst = instance;
				}
			}
		}

		return inst;
	}

	public static synchronized RecordableConfig load() {
		configPath = FabricLoader.getInstance().getConfigDir().resolve("recordable.json");
		if (Files.exists(configPath, new LinkOption[0])) {
			try {
				Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8);

				RecordableConfig var2;
				try {
					RecordableConfig loaded = (RecordableConfig)GSON.fromJson(reader, RecordableConfig.class);
					instance = loaded == null ? new RecordableConfig() : loaded;
					instance.migrateOldConfig();
					instance.sanitize();
					instance.save();
					var2 = instance;
				} catch (Throwable var4) {
					if (reader != null) {
						try {
							reader.close();
						} catch (Throwable var3) {
							var4.addSuppressed(var3);
						}
					}

					throw var4;
				}

				if (reader != null) {
					reader.close();
				}

				return var2;
			} catch (Exception var5) {
				RecordableMod.LOGGER.warn("Failed to load Record-able config at {}. Recreating defaults.", configPath, var5);
			}
		}

		instance = new RecordableConfig();
		instance.sanitize();
		instance.save();
		return instance;
	}

	public synchronized void save() {
		this.sanitize();

		try {
			Files.createDirectories(getConfigPath().getParent());
			Writer writer = Files.newBufferedWriter(
				getConfigPath(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE
			);

			try {
				GSON.toJson(this, writer);
			} catch (Throwable var5) {
				if (writer != null) {
					try {
						writer.close();
					} catch (Throwable var4) {
						var5.addSuppressed(var4);
					}
				}

				throw var5;
			}

			if (writer != null) {
				writer.close();
			}
		} catch (IOException var6) {
			RecordableMod.LOGGER.warn("Failed to save Record-able config at {}.", getConfigPath(), var6);
		}
	}

	public static Path getConfigPath() {
		if (configPath == null) {
			configPath = FabricLoader.getInstance().getConfigDir().resolve("recordable.json");
		}

		return configPath;
	}

	public String getFormat() {
		if (this.format != null && !this.format.isBlank()) {
			String normalized = this.format.trim().toLowerCase(Locale.ROOT);

			for (String valid : FORMATS) {
				if (valid.equals(normalized)) {
					return normalized;
				}
			}

			return "mp4";
		} else {
			return "mp4";
		}
	}

	public int getFps() {
		return PlatformUtils.isAndroid() ? Math.min(this.fps, 30) : this.fps;
	}

	public String getResolution() {
		return this.resolution;
	}

	public String getQuality() {
		return this.quality;
	}

	public boolean isAutoBitrate() {
		return this.bitrate == null || this.bitrate.isBlank() || "auto".equalsIgnoreCase(this.bitrate.trim());
	}

	public String resolveBitrate(int width, int height) {
		if (!this.isAutoBitrate()) {
			String userBitrate = this.bitrate.trim();
			long bitrateInKbps = parseBitrateToKbps(userBitrate);
			if (bitrateInKbps > 0L && bitrateInKbps < 500L) {
				RecordableMod.LOGGER
					.warn(
						"WARNING: Configured bitrate '{}' ({} kbps) is very low for {}x{} video. This will produce poor quality. Recommended: at least 2M for 720p, 5M for 1080p. Set to 'auto' for optimal quality.",
						new Object[]{userBitrate, bitrateInKbps, width, height}
					);
			}

			return userBitrate;
		} else {
			String megabits = this.quality;

			double qualityFactor = switch (megabits) {
				case "high" -> 0.12;
				case "performance" -> 0.05;
				default -> 0.08;
			};
			double megabits = (double)width * (double)height * (double)Math.max(1, this.fps) * qualityFactor / 1000000.0;
			int rounded = (int)Math.round(Math.max(2.0, Math.min(80.0, megabits)));
			return rounded + "M";
		}
	}

	private static long parseBitrateToKbps(String bitrateStr) {
		if (bitrateStr != null && !bitrateStr.isBlank()) {
			String lower = bitrateStr.trim().toLowerCase(Locale.ROOT);

			try {
				if (lower.endsWith("m")) {
					return (long)(Double.parseDouble(lower.substring(0, lower.length() - 1)) * 1000.0);
				} else {
					return lower.endsWith("k") ? (long)Double.parseDouble(lower.substring(0, lower.length() - 1)) : Long.parseLong(lower) / 1000L;
				}
			} catch (NumberFormatException var3) {
				return -1L;
			}
		} else {
			return -1L;
		}
	}

	public int getX264Crf() {
		String var1 = this.quality;

		return switch (var1) {
			case "high" -> 18;
			case "performance" -> 28;
			default -> 23;
		};
	}

	public String getX264Preset() {
		String var1 = this.quality;

		return switch (var1) {
			case "high" -> "slow";
			case "performance" -> "ultrafast";
			default -> "medium";
		};
	}

	public int getVp9Crf() {
		String var1 = this.quality;

		return switch (var1) {
			case "high" -> 30;
			case "performance" -> 40;
			default -> 35;
		};
	}

	public int getMjpegQuality() {
		String var1 = this.quality;

		return switch (var1) {
			case "high" -> 2;
			case "performance" -> 8;
			default -> 5;
		};
	}

	public Path getOutputDirectory() {
		try {
			boolean isDefault = this.outputDir == null || this.outputDir.isBlank() || this.outputDir.trim().equals("recordings");
			if (isDefault && PlatformUtils.isAndroid()) {
				return AndroidPlatform.resolveRecordingsOutputDir();
			} else {
				Path configured = Paths.get(isDefault ? "recordings" : this.outputDir.trim());
				return configured.isAbsolute() ? configured.normalize() : FabricLoader.getInstance().getGameDir().resolve(configured).normalize();
			}
		} catch (InvalidPathException var3) {
			return FabricLoader.getInstance().getGameDir().resolve("recordings").normalize();
		}
	}

	public int getOverlayColorRgb() {
		return parseHexColor(this.overlayColor, 16711680);
	}

	public int getMenuAccentColorRgb() {
		return parseHexColor(this.menuAccentColor, 16711680);
	}

	public boolean isElementVisible(String elementId) {
		return switch (elementId) {
			case "PLAY/REC" -> this.hudPlayRecVisible;
			case "Timestamp" -> this.hudTimestampVisible;
			case "Corners" -> this.hudCornersVisible;
			case "SP" -> this.hudSpVisible;
			case "Details" -> this.hudDetailsVisible;
			case "Perf" -> this.hudPerfVisible;
			case "Mic" -> this.hudMicVisible;
			case "Classic" -> this.hudClassicVisible;
			case "Synthwave" -> this.hudSynthVisible;
			case "Filter:VHS" -> this.filterVhsVisible;
			case "Filter:LCD_MOIRE" -> this.filterLcdMoireVisible;
			case "Filter:CRT" -> this.filterCrtVisible;
			default -> true;
		};
	}

	public void setElementVisible(String elementId, boolean visible) {
		switch (elementId) {
			case "PLAY/REC":
				this.hudPlayRecVisible = visible;
				break;
			case "Timestamp":
				this.hudTimestampVisible = visible;
				break;
			case "Corners":
				this.hudCornersVisible = visible;
				break;
			case "SP":
				this.hudSpVisible = visible;
				break;
			case "Details":
				this.hudDetailsVisible = visible;
				break;
			case "Perf":
				this.hudPerfVisible = visible;
				break;
			case "Mic":
				this.hudMicVisible = visible;
				break;
			case "Classic":
				this.hudClassicVisible = visible;
				break;
			case "Synthwave":
				this.hudSynthVisible = visible;
				break;
			case "Filter:VHS":
				this.filterVhsVisible = visible;
				break;
			case "Filter:LCD_MOIRE":
				this.filterLcdMoireVisible = visible;
				break;
			case "Filter:CRT":
				this.filterCrtVisible = visible;
		}
	}

	public static boolean isFilterLayer(String layerId) {
		return layerId != null && layerId.startsWith("Filter:");
	}

	public static FilterType filterLayerToType(String layerId) {
		return !isFilterLayer(layerId) ? FilterType.NONE : FilterType.fromName(layerId.substring("Filter:".length()));
	}

	public int getFilterIntensity(String layerId) {
		return switch (layerId) {
			case "Filter:VHS" -> this.filterVhsIntensity;
			case "Filter:LCD_MOIRE" -> this.filterLcdMoireIntensity;
			case "Filter:CRT" -> this.filterCrtIntensity;
			default -> 0;
		};
	}

	public void setFilterIntensity(String layerId, int value) {
		int v = Math.max(0, Math.min(100, value));
		switch (layerId) {
			case "Filter:VHS":
				this.filterVhsIntensity = v;
				break;
			case "Filter:LCD_MOIRE":
				this.filterLcdMoireIntensity = v;
				break;
			case "Filter:CRT":
				this.filterCrtIntensity = v;
		}
	}

	public static List<String> allLayerIds() {
		List<String> ids = new ArrayList();
		Collections.addAll(ids, FILTER_LAYERS);
		Collections.addAll(ids, HUD_LAYERS);
		Collections.addAll(ids, PANEL_LAYERS);
		return ids;
	}

	public static String defaultLayerOrder() {
		return String.join(",", allLayerIds());
	}

	public static List<String> layerIdsForStyle(RecordableConfig.OverlayStyleHud style) {
		List<String> ids = new ArrayList();
		Collections.addAll(ids, FILTER_LAYERS);
		if (style == RecordableConfig.OverlayStyleHud.VHS) {
			Collections.addAll(ids, VHS_HUD_LAYERS);
		} else if (style == RecordableConfig.OverlayStyleHud.CLASSIC) {
			ids.add("Classic");
		} else if (style == RecordableConfig.OverlayStyleHud.SYNTHWAVE) {
			ids.add("Synthwave");
		}

		ids.add("Mic");
		return ids;
	}

	public int[] classicPanelPos(int areaW, int areaH, int panelW, int panelH, int margin) {
		if (this.hudClassicX >= 0 && this.hudClassicY >= 0) {
			int x = Math.max(0, Math.min(this.hudClassicX, Math.max(0, areaW - panelW)));
			int y = Math.max(0, Math.min(this.hudClassicY, Math.max(0, areaH - panelH)));
			return new int[]{x, y};
		} else {
			RecordableConfig.OverlayPosition position = this.overlayPosition != null ? this.overlayPosition : RecordableConfig.OverlayPosition.TOP_LEFT;
			int x;
			int y;
			switch (position) {
				case TOP_RIGHT:
					x = areaW - panelW - margin - 130;
					y = margin;
					break;
				case BOTTOM_LEFT:
					x = margin;
					y = areaH - panelH - margin - 50;
					break;
				case BOTTOM_RIGHT:
					x = areaW - panelW - margin - 130;
					y = areaH - panelH - margin - 50;
					break;
				case CENTER_TOP:
					x = (areaW - panelW) / 2;
					y = margin + 70;
					break;
				default:
					x = margin;
					y = margin;
			}

			x = Math.max(margin, Math.min(x, areaW - panelW - margin));
			y = Math.max(margin, Math.min(y, areaH - panelH - margin));
			return new int[]{x, y};
		}
	}

	public int[] synthPanelPos(int areaW, int areaH, int panelW, int panelH, int defX, int defY) {
		int x = this.hudSynthX >= 0 ? this.hudSynthX : defX;
		int y = this.hudSynthY >= 0 ? this.hudSynthY : defY;
		x = Math.max(0, Math.min(x, Math.max(0, areaW - panelW)));
		y = Math.max(0, Math.min(y, Math.max(0, areaH - panelH)));
		return new int[]{x, y};
	}

	public static int applyOpacity(int argb, int opacityPercent) {
		if (opacityPercent >= 100) {
			return argb;
		} else if (opacityPercent <= 0) {
			return argb & 16777215;
		} else {
			int alpha = argb >>> 24 & 0xFF;
			alpha = alpha * opacityPercent / 100;
			return alpha << 24 | argb & 16777215;
		}
	}

	public static int parseArgbColor(String hexColor, int fallbackArgb) {
		if (hexColor != null && !hexColor.isBlank()) {
			String h = hexColor.trim();
			if (h.startsWith("#")) {
				h = h.substring(1);
			}

			try {
				if (h.length() == 8) {
					return (int)Long.parseLong(h, 16);
				}

				if (h.length() == 6) {
					return 0xFF000000 | Integer.parseInt(h, 16);
				}
			} catch (NumberFormatException var4) {
			}

			return fallbackArgb;
		} else {
			return fallbackArgb;
		}
	}

	public RecordableConfig.CaptureDimensions resolveCaptureDimensions(int nativeWidth, int nativeHeight) {
		int safeNativeWidth = Math.max(2, nativeWidth);
		int safeNativeHeight = Math.max(2, nativeHeight);
		String targetWidth = this.resolution;

		int maxHeight = switch (targetWidth) {
			case "1080p" -> 1080;
			case "720p" -> 720;
			case "480p" -> 480;
			default -> safeNativeHeight;
		};
		int targetWidth;
		int targetHeight;
		if (!"native".equals(this.resolution) && safeNativeHeight > maxHeight) {
			double scale = (double)maxHeight / (double)safeNativeHeight;
			targetWidth = (int)Math.round((double)safeNativeWidth * scale);
			targetHeight = (int)Math.round((double)safeNativeHeight * scale);
		} else {
			targetWidth = safeNativeWidth;
			targetHeight = safeNativeHeight;
		}

		if (PlatformUtils.isAndroid()) {
			targetWidth /= 1;
			targetHeight /= 1;
		}

		int var11 = makeEven(targetWidth);
		targetHeight = makeEven(targetHeight);
		return new RecordableConfig.CaptureDimensions(Math.max(2, var11), Math.max(2, targetHeight));
	}

	public String getPerformanceHint() {
		boolean heavyResolution = "native".equals(this.resolution) || "1080p".equals(this.resolution);
		if (this.fps >= 120) {
			return "120 FPS recording is expensive. Prefer 60 FPS unless you need slow-motion footage.";
		} else if (heavyResolution && this.fps >= 60 && "high".equals(this.quality)) {
			return "High quality 1080p/native at 60 FPS may drop frames on slower CPUs. Use Performance quality or lower FPS/resolution.";
		} else {
			return heavyResolution && this.fps >= 60
				? "If queue warnings appear, switch to 720p or 30 FPS for smoother recording."
				: "Current settings are expected to record smoothly on most systems.";
		}
	}

	public boolean migrateOldConfig() {
		if (this.audioDevice != null && "openal".equalsIgnoreCase(this.audioDevice.trim())) {
			RecordableMod.LOGGER.info("Migrating legacy audioDevice='openal' to 'auto'.");
			this.audioDevice = "auto";
			return true;
		} else {
			return false;
		}
	}

	public void sanitize() {
		if (this.format != null && (this.format.contains("recordable-") || this.format.contains(".wav") || this.format.length() > 10)) {
			RecordableMod.LOGGER.warn("Config format field was corrupted: '{}'. Resetting to 'mp4'.", this.format);
			this.format = "mp4";
		}

		this.format = sanitizeString(this.format, FORMATS, "mp4");
		this.resolution = sanitizeString(this.resolution, RESOLUTIONS, "native");
		this.quality = sanitizeString(this.quality, QUALITIES, "balanced");
		if (this.encoder == null) {
			this.encoder = RecordableConfig.VideoEncoder.SOFTWARE;
		}

		this.autoRecordTrigger = sanitizeString(this.autoRecordTrigger, AUTO_RECORD_TRIGGERS, "world_join");
		this.autoStopTrigger = sanitizeString(this.autoStopTrigger, AUTO_STOP_TRIGGERS, "world_leave");
		if (Arrays.stream(FPS_VALUES).noneMatch(value -> value == this.fps)) {
			this.fps = 60;
		}

		if (this.bitrate != null && !this.bitrate.isBlank()) {
			String trimmed = this.bitrate.trim();
			this.bitrate = "auto".equalsIgnoreCase(trimmed) ? "auto" : trimmed;
			if (!"auto".equalsIgnoreCase(this.bitrate) && !this.bitrate.matches("(?i)^[1-9][0-9]*(?:[km])?$")) {
				this.bitrate = "auto";
			}
		} else {
			this.bitrate = "auto";
		}

		this.audioSource = "game";
		if (this.audioDevice != null && !this.audioDevice.isBlank()) {
			this.audioDevice = this.audioDevice.trim();
			if ("openal".equalsIgnoreCase(this.audioDevice)) {
				this.audioDevice = "auto";
			}
		} else {
			this.audioDevice = "auto";
		}

		if (this.audioEncoder == null) {
			this.audioEncoder = RecordableConfig.AudioEncoder.AAC;
		}

		if (this.audioBitrate != null && !this.audioBitrate.isBlank()) {
			String trimmedAudio = this.audioBitrate.trim().toLowerCase(Locale.ROOT);
			if (trimmedAudio.matches("^[1-9][0-9]*k$")) {
				try {
					this.audioBitrateKbps = Integer.parseInt(trimmedAudio.substring(0, trimmedAudio.length() - 1));
				} catch (NumberFormatException var11) {
				}
			}
		}

		this.audioBitrateKbps = Math.max(32, Math.min(512, this.audioBitrateKbps));
		this.audioChannelCount = this.audioChannelCount == 1 ? 1 : 2;
		this.audioChannels = sanitizeString(this.audioChannels, AUDIO_CHANNELS, "auto");
		if ("mono".equals(this.audioChannels)) {
			this.audioChannelCount = 1;
		} else if ("stereo".equals(this.audioChannels)) {
			this.audioChannelCount = 2;
		}

		if (Arrays.stream(AUDIO_SAMPLE_RATES).noneMatch(value -> value == this.audioSampleRate)) {
			this.audioSampleRate = 48000;
		}

		this.audioVolume = Math.max(0, Math.min(200, this.audioVolume));
		this.audioVolumeBoostDb = Math.max(0, Math.min(24, this.audioVolumeBoostDb));
		if (this.microphoneDevice != null && !this.microphoneDevice.isBlank()) {
			this.microphoneDevice = this.microphoneDevice.trim();
		} else {
			this.microphoneDevice = "auto";
		}

		this.gameAudioVolume = Math.max(0, Math.min(200, this.gameAudioVolume));
		this.microphoneVolume = Math.max(0, Math.min(200, this.microphoneVolume));
		this.validateAudioEncoderCompatibility();
		this.audioBitrate = this.audioBitrateKbps + "k";
		this.audioChannels = this.audioChannelCount == 1 ? "mono" : "stereo";
		if (this.outputDir != null && !this.outputDir.isBlank()) {
			this.outputDir = this.outputDir.trim();
		} else {
			this.outputDir = "recordings";
		}

		try {
			String detected = FfmpegBundleManager.getBundledFfmpegPath();
			this.bundledFfmpegPath = detected != null ? detected : "";
		} catch (Exception var10) {
			this.bundledFfmpegPath = "";
		}

		this.overlayColor = sanitizeHexColor(this.overlayColor, "#FF0000");
		this.menuAccentColor = sanitizeHexColor(this.menuAccentColor, "#FF0000");
		if (this.overlayPosition == null) {
			this.overlayPosition = RecordableConfig.OverlayPosition.TOP_LEFT;
		}

		this.overlayScale = Math.max(50, Math.min(200, this.overlayScale));
		if (this.overlayStyleHud == null) {
			this.overlayStyleHud = RecordableConfig.OverlayStyleHud.CLASSIC;
		}

		this.vhsPlayColor = sanitizeHexColor(this.vhsPlayColor, "#FFFFFF");
		this.vhsRecTextColor = sanitizeHexColor(this.vhsRecTextColor, "#FFFFFF");
		this.vhsRecDotColor = sanitizeHexColor(this.vhsRecDotColor, "#CC1E1E");
		this.vhsBracketColor = sanitizeHexColor(this.vhsBracketColor, "#C8FFFFFF");
		this.vhsTimestampColor = sanitizeHexColor(this.vhsTimestampColor, "#FFFFFF");
		this.vhsDateColor = sanitizeHexColor(this.vhsDateColor, "#FFFFFF");
		this.vhsSpColor = sanitizeHexColor(this.vhsSpColor, "#FFFFFF");
		this.hudPlayRecX = Math.max(0, Math.min(2000, this.hudPlayRecX));
		this.hudPlayRecY = Math.max(0, Math.min(2000, this.hudPlayRecY));
		this.hudTimestampOffsetX = Math.max(0, Math.min(2000, this.hudTimestampOffsetX));
		this.hudTimestampY = Math.max(0, Math.min(2000, this.hudTimestampY));
		this.hudSpX = Math.max(0, Math.min(2000, this.hudSpX));
		this.hudSpOffsetY = Math.max(0, Math.min(2000, this.hudSpOffsetY));
		this.hudPerfOffsetX = Math.max(0, Math.min(2000, this.hudPerfOffsetX));
		this.hudPerfOffsetY = Math.max(0, Math.min(2000, this.hudPerfOffsetY));
		this.hudDetailsOffsetX = Math.max(0, Math.min(2000, this.hudDetailsOffsetX));
		this.hudDetailsOffsetY = Math.max(0, Math.min(2000, this.hudDetailsOffsetY));
		this.hudCornersX = Math.max(0, Math.min(2000, this.hudCornersX));
		this.hudCornersY = Math.max(0, Math.min(2000, this.hudCornersY));
		this.hudCornersWidth = Math.max(20, Math.min(2000, this.hudCornersWidth));
		this.hudCornersHeight = Math.max(20, Math.min(2000, this.hudCornersHeight));
		this.hudPlayRecW = Math.max(0, Math.min(2000, this.hudPlayRecW));
		this.hudPlayRecH = Math.max(0, Math.min(2000, this.hudPlayRecH));
		this.hudTimestampW = Math.max(0, Math.min(2000, this.hudTimestampW));
		this.hudTimestampH = Math.max(0, Math.min(2000, this.hudTimestampH));
		this.hudSpW = Math.max(0, Math.min(2000, this.hudSpW));
		this.hudSpH = Math.max(0, Math.min(2000, this.hudSpH));
		this.hudPerfW = Math.max(0, Math.min(2000, this.hudPerfW));
		this.hudPerfH = Math.max(0, Math.min(2000, this.hudPerfH));
		this.hudDetailsW = Math.max(0, Math.min(2000, this.hudDetailsW));
		this.hudDetailsH = Math.max(0, Math.min(2000, this.hudDetailsH));
		this.hudPlayRecOpacity = Math.max(0, Math.min(100, this.hudPlayRecOpacity));
		this.hudTimestampOpacity = Math.max(0, Math.min(100, this.hudTimestampOpacity));
		this.hudCornersOpacity = Math.max(0, Math.min(100, this.hudCornersOpacity));
		this.hudSpOpacity = Math.max(0, Math.min(100, this.hudSpOpacity));
		this.hudDetailsOpacity = Math.max(0, Math.min(100, this.hudDetailsOpacity));
		this.hudPerfOpacity = Math.max(0, Math.min(100, this.hudPerfOpacity));
		this.hudMicOpacity = Math.max(0, Math.min(100, this.hudMicOpacity));
		if (this.hudMicX < -1) {
			this.hudMicX = -1;
		}

		if (this.hudMicY < 0) {
			this.hudMicY = 0;
		}

		List<String> allLayers = allLayerIds();
		Set<String> validLayers = new HashSet(allLayers);
		if (this.hudLayerOrder != null && !this.hudLayerOrder.isBlank()) {
			StringBuilder cleaned = new StringBuilder();
			Set<String> seen = new HashSet();

			for (String part : this.hudLayerOrder.split(",")) {
				String id = part.trim();
				if (!id.isEmpty() && validLayers.contains(id) && seen.add(id)) {
					if (cleaned.length() > 0) {
						cleaned.append(',');
					}

					cleaned.append(id);
				}
			}

			for (String id : allLayers) {
				if (seen.add(id)) {
					if (cleaned.length() > 0) {
						cleaned.append(',');
					}

					cleaned.append(id);
				}
			}

			this.hudLayerOrder = cleaned.length() > 0 ? cleaned.toString() : defaultLayerOrder();
		} else {
			this.hudLayerOrder = defaultLayerOrder();
		}

		this.maxFileSizeMB = Math.max(0, this.maxFileSizeMB);
		this.autoRecordDelay = Math.max(0, Math.min(10, this.autoRecordDelay));
		if (this.audioDelayPreset == null) {
			this.audioDelayPreset = RecordableConfig.AudioDelayPreset.AUTO;
		}

		this.audioSyncOffsetMs = Math.max(0, Math.min(500, this.audioSyncOffsetMs));
		this.activeTemplate = sanitizeString(this.activeTemplate, TEMPLATES, "custom");
		this.diskSpaceWarnPercent = Math.max(50, Math.min(99, this.diskSpaceWarnPercent));
		this.diskSpaceBlockPercent = Math.max(this.diskSpaceWarnPercent + 1, Math.min(100, this.diskSpaceBlockPercent));
		this.diskSpaceMinFreeMB = Math.max(100, Math.min(10000, this.diskSpaceMinFreeMB));
		this.replayBufferDurationSeconds = Math.max(10, Math.min(600, this.replayBufferDurationSeconds));
		this.autoClipDuration = Math.max(5, Math.min(300, this.autoClipDuration));
		this.autoClipKillPreSeconds = Math.max(0, Math.min(10, this.autoClipKillPreSeconds));
		this.autoClipKillPostSeconds = Math.max(0, Math.min(10, this.autoClipKillPostSeconds));
		if (Arrays.stream(AUTO_CLIP_FPS_VALUES).noneMatch(value -> value == this.autoClipFps)) {
			int nearest = AUTO_CLIP_FPS_VALUES[0];
			int bestDelta = Integer.MAX_VALUE;

			for (int candidate : AUTO_CLIP_FPS_VALUES) {
				int delta = Math.abs(candidate - this.autoClipFps);
				if (delta < bestDelta) {
					bestDelta = delta;
					nearest = candidate;
				}
			}

			this.autoClipFps = nearest;
		}

		if (this.autoClipKillPreSeconds == 0 && this.autoClipKillPostSeconds == 0) {
			this.autoClipKillPreSeconds = 1;
			this.autoClipKillPostSeconds = 1;
		}

		this.replayBufferQuality = sanitizeString(this.replayBufferQuality, REPLAY_QUALITIES, "balanced");
		this.gallerySortMode = sanitizeString(this.gallerySortMode, GALLERY_SORT_MODES, "newest");
		this.galleryColumns = Math.max(1, Math.min(6, this.galleryColumns));
		if (this.watermarkSlots == null) {
			this.watermarkSlots = new ArrayList();
		}

		while (this.watermarkSlots.size() > 4) {
			this.watermarkSlots.remove(this.watermarkSlots.size() - 1);
		}

		for (int i = this.watermarkSlots.size() - 1; i >= 0; i--) {
			WatermarkSlot slot = (WatermarkSlot)this.watermarkSlots.get(i);
			if (slot == null) {
				this.watermarkSlots.remove(i);
			} else {
				slot.sanitize();
			}
		}

		this.autoCleanupOlderThanDays = Math.max(1, Math.min(3650, this.autoCleanupOlderThanDays));
		this.autoCleanupMaxTotalMB = Math.max(0, Math.min(10000000, this.autoCleanupMaxTotalMB));
		this.storageCompressionCrf = Math.max(0, Math.min(51, this.storageCompressionCrf));
		if (this.storageProtectedFiles == null) {
			this.storageProtectedFiles = new ArrayList();
		}

		this.perfMinFps = Math.max(10, Math.min(240, this.perfMinFps));
		this.smoothMotionMode = SmoothMotion.sanitizeMode(this.smoothMotionMode);
		if (this.streamerDefaultCensorStyle == null || !this.streamerDefaultCensorStyle.equals("SOLID") && !this.streamerDefaultCensorStyle.equals("GRADIENT")) {
			this.streamerDefaultCensorStyle = "SOLID";
		}

		if (this.censorRegions == null) {
			this.censorRegions = new ArrayList();
		} else {
			for (CensorRegion region : this.censorRegions) {
				if (region != null) {
					region.sanitize();
				}
			}
		}

		if (this.uiTheme == null) {
			this.uiTheme = ThemePreset.VHS;
		}

		if (this.uiCustomAccentColor == null) {
			this.uiCustomAccentColor = "";
		} else if (!this.uiCustomAccentColor.isBlank()) {
			this.uiCustomAccentColor = sanitizeHexColor(this.uiCustomAccentColor, "");
		}
	}

	public void applyTemplate(String templateName) {
		switch (templateName) {
			case "cinematic":
				this.resolution = "1080p";
				this.fps = 60;
				this.quality = "high";
				this.bitrate = "auto";
				this.captureAudio = true;
				this.audioEncoder = RecordableConfig.AudioEncoder.AAC;
				this.audioBitrateKbps = 256;
				this.maxFileSizeMB = 0;
				break;
			case "balanced":
				this.resolution = "1080p";
				this.fps = 60;
				this.quality = "balanced";
				this.bitrate = "auto";
				this.captureAudio = true;
				this.audioEncoder = RecordableConfig.AudioEncoder.AAC;
				this.audioBitrateKbps = 192;
				this.maxFileSizeMB = 0;
				break;
			case "pvp_clip":
				this.resolution = "720p";
				this.fps = 60;
				this.quality = "balanced";
				this.bitrate = "auto";
				this.maxFileSizeMB = 200;
				this.captureAudio = true;
				this.audioEncoder = RecordableConfig.AudioEncoder.AAC;
				this.audioBitrateKbps = 128;
		}

		this.activeTemplate = templateName;
		this.sanitize();
	}

	public static String getTemplateDisplayName(String template) {
		return switch (template) {
			case "cinematic" -> "Cinematic";
			case "balanced" -> "Balanced";
			case "pvp_clip" -> "PvP Clip";
			default -> "Custom";
		};
	}

	public static String getTemplateDescription(String template) {
		return switch (template) {
			case "cinematic" -> "1080p60, high quality, unlimited size";
			case "balanced" -> "1080p60, balanced quality, general use";
			case "pvp_clip" -> "720p60, quick clips under 200MB";
			default -> "Your custom settings";
		};
	}

	public void applyLowEndOptimizations() {
		this.resolution = "480p";
		this.fps = 30;
		this.quality = "performance";
		this.bitrate = "auto";
		this.encoder = RecordableConfig.VideoEncoder.SOFTWARE;
		this.captureAudio = true;
		this.audioEncoder = RecordableConfig.AudioEncoder.AAC;
		this.audioBitrateKbps = 96;
		this.audioChannels = "mono";
		this.audioSampleRate = 44100;
		this.perfOptimizerEnabled = true;
		this.perfAutoAdjust = true;
		this.perfMinFps = 30;
		this.perfModeGamePriority = true;
		this.perfActionLowerRes = true;
		this.perfActionLowerFps = true;
		this.perfActionFasterPreset = true;
		this.perfWarnBeforeAdjust = false;
		this.perfShowStatsOverlay = false;
		this.uiScanlines = false;
		this.uiFilmGrain = false;
		this.uiGlitchEffects = false;
		this.uiVignette = false;
		this.uiAnimations = false;
		this.uiTheme = ThemePreset.MINIMAL;
		this.overlayStyleHud = RecordableConfig.OverlayStyleHud.NONE;
		this.replayBufferEnabled = false;
		this.filterVhsVisible = false;
		this.filterLcdMoireVisible = false;
		this.filterCrtVisible = false;
		this.watermarksEnabled = false;
		this.activeTemplate = "custom";
		this.sanitize();
	}

	public static String sanitizeDevicePreset(String preset) {
		if (preset != null) {
			for (String p : DEVICE_PRESETS) {
				if (p.equals(preset)) {
					return preset;
				}
			}
		}

		return "mid_end_pc";
	}

	public static String getDevicePresetDisplayName(String preset) {
		if (preset == null) {
			return "Custom";
		} else {
			switch (preset) {
				case "android_phone":
					return "Android Phones";
				case "low_end_pc":
					return "Low-end PC";
				case "mid_end_pc":
					return "Mid-end PC";
				case "high_end_pc":
					return "High-end PC";
				case "nasa":
					return "N.A.S.A Super-Computer";
				default:
					return "Custom";
			}
		}
	}

	public void applyDevicePreset(String preset) {
		if (preset == null) {
			preset = "mid_end_pc";
		}

		switch (preset) {
			case "android_phone":
				this.resolution = "480p";
				this.fps = 30;
				this.quality = "performance";
				this.bitrate = "auto";
				this.encoder = RecordableConfig.VideoEncoder.SOFTWARE;
				this.captureAudio = true;
				this.audioEncoder = RecordableConfig.AudioEncoder.AAC;
				this.audioBitrateKbps = 96;
				this.audioChannels = "mono";
				this.audioSampleRate = 44100;
				this.perfOptimizerEnabled = true;
				this.perfAutoAdjust = true;
				this.perfMinFps = 30;
				this.perfModeGamePriority = true;
				this.perfActionLowerRes = true;
				this.perfActionLowerFps = true;
				this.perfActionFasterPreset = true;
				this.perfWarnBeforeAdjust = false;
				break;
			case "low_end_pc":
				this.resolution = "720p";
				this.fps = 30;
				this.quality = "performance";
				this.bitrate = "auto";
				this.encoder = RecordableConfig.VideoEncoder.SOFTWARE;
				this.captureAudio = true;
				this.audioEncoder = RecordableConfig.AudioEncoder.AAC;
				this.audioBitrateKbps = 128;
				this.audioChannels = "stereo";
				this.audioSampleRate = 44100;
				this.perfOptimizerEnabled = true;
				this.perfAutoAdjust = true;
				this.perfMinFps = 30;
				this.perfModeGamePriority = true;
				this.perfActionLowerRes = true;
				this.perfActionLowerFps = true;
				this.perfActionFasterPreset = true;
				this.perfWarnBeforeAdjust = false;
				break;
			case "high_end_pc":
				this.resolution = "1080p";
				this.fps = 60;
				this.quality = "high";
				this.bitrate = "auto";
				this.encoder = RecordableConfig.VideoEncoder.NVIDIA;
				this.captureAudio = true;
				this.audioEncoder = RecordableConfig.AudioEncoder.AAC;
				this.audioBitrateKbps = 256;
				this.audioChannels = "stereo";
				this.audioSampleRate = 48000;
				this.perfOptimizerEnabled = false;
				this.perfAutoAdjust = false;
				break;
			case "nasa":
				this.resolution = "native";
				this.fps = 120;
				this.quality = "high";
				this.bitrate = "auto";
				this.encoder = RecordableConfig.VideoEncoder.NVIDIA;
				this.captureAudio = true;
				this.audioEncoder = RecordableConfig.AudioEncoder.AAC;
				this.audioBitrateKbps = 320;
				this.audioChannels = "stereo";
				this.audioSampleRate = 48000;
				this.perfOptimizerEnabled = false;
				this.perfAutoAdjust = false;
				break;
			case "mid_end_pc":
			default:
				this.resolution = "1080p";
				this.fps = 60;
				this.quality = "balanced";
				this.bitrate = "auto";
				this.encoder = RecordableConfig.VideoEncoder.NVIDIA;
				this.captureAudio = true;
				this.audioEncoder = RecordableConfig.AudioEncoder.AAC;
				this.audioBitrateKbps = 192;
				this.audioChannels = "stereo";
				this.audioSampleRate = 48000;
				this.perfOptimizerEnabled = true;
				this.perfAutoAdjust = true;
				this.perfMinFps = 45;
				this.perfModeGamePriority = true;
				this.perfActionLowerRes = false;
				this.perfActionLowerFps = true;
				this.perfActionFasterPreset = true;
				this.perfWarnBeforeAdjust = true;
		}

		this.activeTemplate = "custom";
		this.sanitize();
	}

	public void validateAudioEncoderCompatibility() {
		if (this.audioEncoder == null) {
			this.audioEncoder = RecordableConfig.AudioEncoder.AAC;
		}

		String container = this.getContainerFromFormat();
		if (!this.audioEncoder.supportsContainer(container)) {
			RecordableConfig.AudioEncoder fallback = "webm".equals(container) ? RecordableConfig.AudioEncoder.OPUS : RecordableConfig.AudioEncoder.AAC;
			RecordableMod.LOGGER
				.warn(
					"Audio encoder {} is not supported in {} container. Falling back to {}.", new Object[]{this.audioEncoder.displayName, container, fallback.displayName}
				);
			this.audioEncoder = fallback;
		}

		if (!this.audioEncoder.isLossless() && this.audioBitrateKbps <= 0) {
			this.audioBitrateKbps = Math.max(96, this.audioEncoder.defaultBitrateKbps);
		}
	}

	public String getContainerFromFormat() {
		String var1 = this.getFormat();

		return switch (var1) {
			case "webm" -> "webm";
			case "avi" -> "avi";
			case "mkv" -> "mkv";
			case "mov" -> "mov";
			default -> "mp4";
		};
	}

	public boolean isWebmFormat() {
		return "webm".equals(this.getFormat());
	}

	public static int[] resolveReplayPreset(String quality, int recHeight, int recFps) {
		String q = quality == null ? "source" : quality.trim().toLowerCase(Locale.ROOT);
		int safeFps = recFps > 0 ? recFps : 30;
		switch (q) {
			case "balanced":
				return new int[]{recHeight > 0 ? Math.min(720, recHeight) : 720, Math.min(30, safeFps)};
			case "performance":
				return new int[]{recHeight > 0 ? Math.min(480, recHeight) : 480, Math.min(30, safeFps)};
			case "high":
				return new int[]{recHeight > 0 ? Math.min(1080, recHeight) : 1080, Math.min(60, safeFps)};
			case "source":
			default:
				return new int[]{0, safeFps};
		}
	}

	private static String sanitizeString(String value, String[] allowed, String fallback) {
		if (value != null) {
			String normalized = value.trim().toLowerCase(Locale.ROOT);

			for (String candidate : allowed) {
				if (candidate.equals(normalized)) {
					return normalized;
				}
			}
		}

		return fallback;
	}

	private static String sanitizeHexColor(String value, String fallback) {
		if (value == null) {
			return fallback;
		} else {
			String normalized = value.trim();
			if (!normalized.startsWith("#")) {
				normalized = "#" + normalized;
			}

			return !HEX_COLOR_PATTERN.matcher(normalized).matches() ? fallback : normalized.toUpperCase(Locale.ROOT);
		}
	}

	private static int parseHexColor(String value, int fallback) {
		String normalized = sanitizeHexColor(value, String.format(Locale.ROOT, "#%06X", fallback));

		try {
			return Integer.parseInt(normalized.substring(1), 16);
		} catch (Throwable var4) {
			return fallback;
		}
	}

	public EncoderType resolveEncoderType() {
		return EncoderType.FFMPEG;
	}

	private static int makeEven(int value) {
		return value % 2 == 0 ? value : value - 1;
	}

	public static enum AudioDelayPreset {
		AUTO("Auto", -1),
		NONE("None", 0),
		DESKTOP("Desktop", 46),
		ANDROID("Android", 60),
		CUSTOM("Custom", -2);

		public final String displayName;
		public final int defaultMs;

		private AudioDelayPreset(String displayName, int defaultMs) {
			this.displayName = displayName;
			this.defaultMs = defaultMs;
		}

		public RecordableConfig.AudioDelayPreset next() {
			RecordableConfig.AudioDelayPreset[] values = values();
			return values[(this.ordinal() + 1) % values.length];
		}

		public RecordableConfig.AudioDelayPreset previous() {
			RecordableConfig.AudioDelayPreset[] values = values();
			return values[(this.ordinal() - 1 + values.length) % values.length];
		}
	}

	public static enum AudioEncoder {
		AAC("AAC", "aac", new String[]{"mp4", "mkv", "mov", "avi"}, 192),
		OPUS("Opus", "libopus", new String[]{"webm", "mkv"}, 128),
		MP3("MP3", "libmp3lame", new String[]{"mp4", "mkv", "mov", "avi"}, 192),
		FLAC("FLAC (Lossless)", "flac", new String[]{"mkv", "mov", "avi", "mp4"}, 0);

		public final String displayName;
		public final String ffmpegCodec;
		public final String[] supportedContainers;
		public final int defaultBitrateKbps;

		private AudioEncoder(String displayName, String ffmpegCodec, String[] supportedContainers, int defaultBitrateKbps) {
			this.displayName = displayName;
			this.ffmpegCodec = ffmpegCodec;
			this.supportedContainers = supportedContainers;
			this.defaultBitrateKbps = defaultBitrateKbps;
		}

		public boolean supportsContainer(String container) {
			if (container != null && !container.isBlank()) {
				String normalized = container.trim().toLowerCase(Locale.ROOT);

				for (String supported : this.supportedContainers) {
					if (supported.equalsIgnoreCase(normalized)) {
						return true;
					}
				}

				return false;
			} else {
				return false;
			}
		}

		public boolean isLossless() {
			return this.defaultBitrateKbps <= 0;
		}
	}

	public static record CaptureDimensions(int width, int height) {
	}

	public static enum OverlayPosition {
		TOP_LEFT("Top-Left"),
		TOP_RIGHT("Top-Right"),
		BOTTOM_LEFT("Bottom-Left"),
		BOTTOM_RIGHT("Bottom-Right"),
		CENTER_TOP("Center-Top");

		public final String displayName;

		private OverlayPosition(String displayName) {
			this.displayName = displayName;
		}

		public RecordableConfig.OverlayPosition next() {
			RecordableConfig.OverlayPosition[] values = values();
			return values[(this.ordinal() + 1) % values.length];
		}

		public RecordableConfig.OverlayPosition previous() {
			RecordableConfig.OverlayPosition[] values = values();
			return values[(this.ordinal() - 1 + values.length) % values.length];
		}
	}

	public static enum OverlayStyleHud {
		CLASSIC("Speed-Runner's Classic"),
		VHS("VHS"),
		SYNTHWAVE("Synthwave"),
		NONE("None");

		public final String displayName;

		private OverlayStyleHud(String displayName) {
			this.displayName = displayName;
		}

		public RecordableConfig.OverlayStyleHud next() {
			RecordableConfig.OverlayStyleHud[] values = values();
			return values[(this.ordinal() + 1) % values.length];
		}

		public RecordableConfig.OverlayStyleHud previous() {
			RecordableConfig.OverlayStyleHud[] values = values();
			return values[(this.ordinal() - 1 + values.length) % values.length];
		}
	}

	public static enum VideoEncoder {
		SOFTWARE("Software (x264)", "libx264"),
		NVIDIA("NVIDIA NVENC", "h264_nvenc"),
		AMD("AMD AMF", "h264_amf"),
		INTEL("Intel QuickSync", "h264_qsv");

		public final String displayName;
		public final String ffmpegCodec;

		private VideoEncoder(String displayName, String ffmpegCodec) {
			this.displayName = displayName;
			this.ffmpegCodec = ffmpegCodec;
		}
	}
}
