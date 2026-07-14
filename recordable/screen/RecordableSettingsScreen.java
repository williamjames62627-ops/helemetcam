package dev.recordable.screen;

import dev.recordable.AudioCapture;
import dev.recordable.DiskSpaceGuardian;
import dev.recordable.FFmpegEncoder;
import dev.recordable.FfmpegBundleManager;
import dev.recordable.PlatformUtils;
import dev.recordable.RecordableConfig;
import dev.recordable.RecordableMod;
import dev.recordable.RecordingManager;
import dev.recordable.AudioCapture.AudioDeviceStatus;
import dev.recordable.AudioCapture.MicTestResult;
import dev.recordable.FFmpegEncoder.FfmpegStatus;
import dev.recordable.FfmpegBundleManager.DownloadProgress;
import dev.recordable.RecordableConfig.AudioDelayPreset;
import dev.recordable.RecordableConfig.AudioEncoder;
import dev.recordable.RecordableConfig.OverlayStyleHud;
import dev.recordable.RecordableConfig.VideoEncoder;
import dev.recordable.compat.RenderHelper;
import dev.recordable.theme.CycleButton;
import dev.recordable.theme.ThemeColors;
import dev.recordable.theme.ThemeEngine;
import dev.recordable.theme.ThemePreset;
import dev.recordable.theme.ThemedPanel;
import dev.recordable.theme.TypewriterText;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import net.minecraft.class_156;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_339;
import net.minecraft.class_342;
import net.minecraft.class_357;
import net.minecraft.class_4185;
import net.minecraft.class_437;
import net.minecraft.class_5676;
import net.minecraft.class_7919;

public final class RecordableSettingsScreen extends class_437 {
	private static final int PANEL_COLOR = -804253680;
	private static final int PANEL_BORDER_COLOR = -12434878;
	private static final int HEADER_COLOR = -1;
	private static final int MUTED_TEXT_COLOR = -4671304;
	private static final int ERROR_TEXT_COLOR = -34953;
	private static final int WIDGET_HEIGHT = 20;
	private static final int ROW_SPACING = 22;
	private static final class_2561 WINDOWS_AUDIO_WARNING_TEXT = class_2561.method_43470(
		"ℹ Audio capture uses DirectShow (Stereo Mix) to record game audio - same approach as OBS Studio. Enable Stereo Mix in Sound settings if not detected."
	);
	private static final class_2561 LINUX_AUDIO_INFO_TEXT = class_2561.method_43470(
		"ℹ Audio capture uses PulseAudio monitor source to record system audio directly."
	);
	private static final class_2561 MACOS_AUDIO_INFO_TEXT = class_2561.method_43470(
		"ℹ Audio capture uses AVFoundation. Install BlackHole for system audio capture."
	);
	private static final class_2561 ANDROID_AUDIO_INFO_TEXT = class_2561.method_43470(
		"ℹ Audio is captured directly from Minecraft's sound engine via OpenAL loopback. Full-volume game audio with zero noise."
	);
	private static final class_2561 STEREO_MIX_HELP_TEXT = class_2561.method_43470(
		"Audio is captured via system loopback (Stereo Mix/PulseAudio). If no audio, enable Stereo Mix in Windows Sound settings."
	);
	private final class_437 parent;
	private final List<RecordableSettingsScreen.LayoutWidget> layoutWidgets = new ArrayList();
	private class_2561 statusMessage;
	private class_2561 ffmpegStatus;
	private boolean ffmpegStatusIsError;
	private boolean statusIsError;
	private int panelLeft;
	private int panelTop;
	private int panelWidth;
	private int panelBottom;
	private int panelBodyTop;
	private int panelBodyBottom;
	private int footerY;
	private int contentHeight;
	private int fullContentHeight;
	private int scrollOffset;
	private boolean draggingScrollbar = false;
	private class_342 searchBox;
	private String searchQuery = "";
	private int searchMatchRows;
	private String selectedDevicePreset = "mid_end_pc";
	private int videoHeaderY;
	private int audioHeaderY;
	private int generalHeaderY;
	private int autoRecordHeaderY;
	private int appearanceHeaderY;
	private int positionsHeaderY;
	private int performanceHeaderY;
	private int advancedHeaderY;
	private int bitrateLabelY;
	private int performanceHintY;
	private int outputLabelY;
	private int outputPathY;
	private int ffmpegStatusY;
	private int diskSpaceInfoY;
	private int autoClipHeaderY;
	private int androidHeaderY;
	private int v07HeaderY;
	private int chatNotifyHeaderY;
	private int compatHeaderY;
	private int replayMemoryWarningY;
	private int windowsAudioWarningY;
	private int windowsAudioWarningHeight;

	public RecordableSettingsScreen(class_437 parent) {
		super(class_2561.method_43471("screen.recordable.settings.title"));
		this.parent = parent;
	}

	protected void method_25426() {
		super.method_25426();
		this.method_37067();
		this.layoutWidgets.clear();
		this.statusMessage = null;
		this.statusIsError = false;

		try {
			RecordableConfig config = RecordableConfig.get();
			if (config == null) {
				this.statusMessage = class_2561.method_43470("Record-able config is unavailable.");
				this.statusIsError = true;
				this.addFallbackCloseButton();
				return;
			}

			config.sanitize();
			FfmpegStatus status = FFmpegEncoder.detectFfmpeg();
			String platformLabel = PlatformUtils.detectPlatform().displayName();
			this.ffmpegStatus = class_2561.method_43470("Platform: " + platformLabel + " | FFmpeg: " + status.displayText());
			this.ffmpegStatusIsError = !status.found();
			this.panelWidth = Math.max(340, Math.min((int)((double)this.field_22789 * 0.78), 760));
			this.panelLeft = (this.field_22789 - this.panelWidth) / 2;
			this.panelTop = Math.max(8, (int)((double)this.field_22790 * 0.04));
			this.panelBottom = Math.min(this.field_22790 - 8, this.panelTop + Math.max(300, (int)((double)this.field_22790 * 0.9)));
			this.panelBodyTop = this.panelTop + 46;
			this.footerY = this.panelBottom - 28;
			this.panelBodyBottom = this.footerY - 8;
			int widgetWidth = Math.max(180, this.panelWidth - 28);
			int widgetLeft = this.panelLeft + 14;
			int halfWidgetWidth = Math.max(88, (widgetWidth - 6) / 2);
			int rightWidgetLeft = widgetLeft + halfWidgetWidth + 6;
			this.searchBox = new class_342(
				this.field_22793, widgetLeft, this.panelTop + 24, widgetWidth, 18, class_2561.method_43471("screen.recordable.settings.search")
			);
			this.searchBox.method_1880(64);
			this.searchBox.method_47404(class_2561.method_43471("screen.recordable.settings.search"));
			this.searchBox.method_1852(this.searchQuery);
			this.searchBox.method_1863(value -> {
				this.searchQuery = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
				this.scrollOffset = 0;
				this.updateWidgetLayout();
			});
			this.method_37063(this.searchBox);
			int y = 0;
			this.addLayoutWidget(class_4185.method_46430(class_2561.method_43471("screen.recordable.settings.open_video_collection"), button -> {
				if (this.field_22787 != null) {
					this.field_22787.method_1507(new VideoCollectionScreen(this));
				}
			}).method_46434(widgetLeft, 0, widgetWidth, 20).method_46431(), y);
			y += 22;
			CycleButton templateButton = CycleButton.create(
				widgetLeft, 0, widgetWidth, 20, class_2561.method_43470(RecordableConfig.getTemplateDisplayName(config.activeTemplate)), button -> {
					String next = nextValue(RecordableConfig.TEMPLATES, config.activeTemplate);
					config.applyTemplate(next);
					saveConfigSafely(config);
					button.method_25355(class_2561.method_43470(RecordableConfig.getTemplateDisplayName(next)));
					if (this.field_22787 != null) {
						this.field_22787.method_1507(new RecordableSettingsScreen(this.parent));
					}
				}, button -> {
					String prev = prevValue(RecordableConfig.TEMPLATES, config.activeTemplate);
					config.applyTemplate(prev);
					saveConfigSafely(config);
					button.method_25355(class_2561.method_43470(RecordableConfig.getTemplateDisplayName(prev)));
					if (this.field_22787 != null) {
						this.field_22787.method_1507(new RecordableSettingsScreen(this.parent));
					}
				}
			);
			templateButton.method_47400(
				class_7919.method_47407(
					class_2561.method_43470(
						"Quick recording templates:\n\nCinematic: "
							+ RecordableConfig.getTemplateDescription("cinematic")
							+ "\nBalanced: "
							+ RecordableConfig.getTemplateDescription("balanced")
							+ "\nPvP Clip: "
							+ RecordableConfig.getTemplateDescription("pvp_clip")
							+ "\nCustom: "
							+ RecordableConfig.getTemplateDescription("custom")
					)
				)
			);
			this.addLayoutWidget(templateButton, y);
			y += 22;
			this.videoHeaderY = y;
			y += 12;
			CycleButton formatButton = CycleButton.create(widgetLeft, 0, halfWidgetWidth, 20, class_2561.method_43470("Output: ." + config.getFormat()), button -> {
				String next = nextValue(RecordableConfig.FORMATS, config.getFormat());
				config.format = next;
				saveConfigSafely(config);
				button.method_25355(class_2561.method_43470("Output: ." + config.getFormat()));
			}, button -> {
				String prev = prevValue(RecordableConfig.FORMATS, config.getFormat());
				config.format = prev;
				saveConfigSafely(config);
				button.method_25355(class_2561.method_43470("Output: ." + config.getFormat()));
			});
			formatButton.method_47400(
				class_7919.method_47407(
					class_2561.method_43470(
						"MP4/MKV/MOV use H.264 (fast, hardware-accelerated, best on Android). WebM uses VP9: smaller files but CPU-heavy software encoding, recommended for desktop only."
					)
				)
			);
			this.addLayoutWidget(formatButton, y);
			this.addLayoutWidget(
				this.addCycleButton(
					rightWidgetLeft, 0, halfWidgetWidth, "screen.recordable.settings.resolution", RecordableConfig.RESOLUTIONS, () -> config.resolution, value -> {
						config.resolution = value;
						saveConfigSafely(config);
					}
				),
				y
			);
			y += 22;
			this.addLayoutWidget(
				this.addCycleButton(widgetLeft, 0, halfWidgetWidth, "screen.recordable.settings.quality", RecordableConfig.QUALITIES, () -> config.quality, value -> {
					config.quality = value;
					saveConfigSafely(config);
				}), y
			);
			this.addLayoutWidget(new RecordableSettingsScreen.FpsSlider(rightWidgetLeft, 0, halfWidgetWidth, 20, config.getFps(), value -> {
				config.fps = value;
				saveConfigSafely(config);
			}), y);
			y += 22;
			List<VideoEncoder> availableEncoders = FFmpegEncoder.detectAvailableEncoders();
			if (!availableEncoders.contains(config.encoder)) {
				config.encoder = VideoEncoder.SOFTWARE;
				saveConfigSafely(config);
			}

			CycleButton encoderButton = CycleButton.create(widgetLeft, 0, widgetWidth, 20, cycleEncoderMessage(config.encoder), button -> {
				VideoEncoder next = nextEncoder(availableEncoders, config.encoder);
				config.encoder = next;
				saveConfigSafely(config);
				button.method_25355(cycleEncoderMessage(next));
			}, button -> {
				VideoEncoder prev = prevEncoder(availableEncoders, config.encoder);
				config.encoder = prev;
				saveConfigSafely(config);
				button.method_25355(cycleEncoderMessage(prev));
			});
			encoderButton.method_47400(class_7919.method_47407(class_2561.method_43470("Select video encoder backend")));
			this.addLayoutWidget(encoderButton, y);
			y += 22;
			String encoderLabel = "Encoder: FFmpeg";
			if (!status.found()) {
				encoderLabel = encoderLabel + " (NOT FOUND)";
			}

			if (!status.found()) {
				this.addLayoutWidget(
					class_4185.method_46430(class_2561.method_43470(encoderLabel), button -> {
							FfmpegStatus refreshed = FFmpegEncoder.detectFfmpeg();
							String label = "Encoder: FFmpeg" + (refreshed.found() ? " ✓" : " (NOT FOUND)");
							button.method_25355(class_2561.method_43470(label));
							if (!refreshed.found()) {
								this.statusMessage = class_2561.method_43470("§cFFmpeg is required. " + PlatformUtils.getFfmpegInstallHint());
								this.statusIsError = true;
							} else {
								this.statusMessage = class_2561.method_43470("✓ FFmpeg detected: " + refreshed.version());
								this.statusIsError = false;
							}
						})
						.method_46434(widgetLeft, 0, halfWidgetWidth, 20)
						.method_46436(
							class_7919.method_47407(
								class_2561.method_43470(
									"FFmpeg is the sole encoder. Click to re-detect after installing/downloading.\n\nFFmpeg status: "
										+ status.displayText()
										+ "\nInstall: "
										+ PlatformUtils.getFfmpegInstallHint()
								)
							)
						)
						.method_46431(),
					y
				);
				boolean autoOk = FfmpegBundleManager.isAutoDownloadSupported();
				String dlLabel;
				if (FfmpegBundleManager.isDownloading()) {
					DownloadProgress dp = FfmpegBundleManager.getLastProgress();
					dlLabel = "Downloading… " + dp.displayPercent();
				} else if (autoOk) {
					dlLabel = "Download FFmpeg (" + FfmpegBundleManager.getEstimatedDownloadSize() + ")";
				} else {
					dlLabel = "FFmpeg Setup…";
				}

				String dlTip = autoOk
					? "Open the FFmpeg setup screen to download from "
						+ FfmpegBundleManager.getDownloadSourceDescription()
						+ ".\nFFmpeg is NOT bundled in this mod - first run downloads ~"
						+ FfmpegBundleManager.getEstimatedDownloadSize()
						+ " from a trusted upstream and verifies the hash."
					: FfmpegBundleManager.getManualInstallInstructions();
				this.addLayoutWidget(class_4185.method_46430(class_2561.method_43470(dlLabel), button -> {
					if (this.field_22787 != null) {
						this.field_22787.method_1507(new FfmpegDownloadScreen(this));
					}
				}).method_46434(rightWidgetLeft, 0, halfWidgetWidth, 20).method_46436(class_7919.method_47407(class_2561.method_43470(dlTip))).method_46431(), y);
				y += 22;
			} else {
				this.addLayoutWidget(
					class_4185.method_46430(class_2561.method_43470(encoderLabel + " ✓"), button -> {
							FfmpegStatus refreshed = FFmpegEncoder.detectFfmpeg();
							String label = "Encoder: FFmpeg" + (refreshed.found() ? " ✓" : " (NOT FOUND)");
							button.method_25355(class_2561.method_43470(label));
							if (!refreshed.found()) {
								this.statusMessage = class_2561.method_43470("§cFFmpeg is required. " + PlatformUtils.getFfmpegInstallHint());
								this.statusIsError = true;
							} else {
								this.statusMessage = class_2561.method_43470("✓ FFmpeg detected: " + refreshed.version());
								this.statusIsError = false;
							}
						})
						.method_46434(widgetLeft, 0, widgetWidth, 20)
						.method_46436(
							class_7919.method_47407(
								class_2561.method_43470(
									"FFmpeg is the sole encoder. Click to re-detect.\n\nFFmpeg status: "
										+ status.displayText()
										+ "\nAudio: System loopback (DirectShow/PulseAudio/AVFoundation)\n\nInstall FFmpeg: "
										+ PlatformUtils.getFfmpegInstallHint()
								)
							)
						)
						.method_46431(),
					y
				);
				y += 22;
			}

			class_342 bitrateField = new class_342(this.field_22793, widgetLeft, 0, widgetWidth, 20, class_2561.method_43471("screen.recordable.settings.bitrate"));
			bitrateField.method_1880(16);
			bitrateField.method_1852(config.bitrate == null ? "auto" : config.bitrate);
			bitrateField.method_1863(value -> {
				config.bitrate = value != null && !value.isBlank() ? value.trim() : "auto";
				saveConfigSafely(config);
			});
			this.addLayoutWidget(bitrateField, y);
			y += 22;
			this.bitrateLabelY = y;
			y += 12;
			this.performanceHintY = y;
			y += 22;
			this.audioHeaderY = y;
			y += 12;
			this.addLayoutWidget(this.addBooleanToggle(widgetLeft, 0, halfWidgetWidth, "screen.recordable.settings.capture_audio", config.captureAudio, value -> {
				config.captureAudio = value;
				saveConfigSafely(config);
			}), y);
			class_342 audioDeviceField = new class_342(this.field_22793, widgetLeft, 0, widgetWidth, 20, class_2561.method_43470("Audio Device"));
			audioDeviceField.method_1880(128);
			audioDeviceField.method_1852(config.audioDevice == null ? "auto" : config.audioDevice);
			audioDeviceField.method_47404(class_2561.method_43470("auto"));
			audioDeviceField.method_1863(value -> {
				config.audioDevice = value != null && !value.isBlank() ? value.trim() : "auto";
				saveConfigSafely(config);
				AudioCapture.clearCache();
			});
			FfmpegStatus ffStatus = FFmpegEncoder.detectFfmpeg();
			String ffExe = ffStatus.found() ? ffStatus.executable() : "ffmpeg";
			AudioDeviceStatus initialAudioStatus = AudioCapture.detectAudioDevice(ffExe, config.audioDevice);
			boolean stereoMixDetected = initialAudioStatus.available();
			class_2561 audioLabelText = audioStatusText(stereoMixDetected);
			String audioTooltip = stereoMixDetected
				? initialAudioStatus.message() + "\nClick to re-scan audio devices."
				: initialAudioStatus.message() + "\n" + STEREO_MIX_HELP_TEXT.getString();
			this.addLayoutWidget(class_4185.method_46430(audioLabelText, button -> {
				AudioCapture.clearCache();
				AudioDeviceStatus refreshed = AudioCapture.detectAudioDevice(ffExe, "auto");
				if (refreshed.available()) {
					config.audioDevice = refreshed.deviceName();
					saveConfigSafely(config);
					audioDeviceField.method_1852(refreshed.deviceName());
					button.method_25355(audioStatusText(true));
					button.method_47400(class_7919.method_47407(class_2561.method_43470(refreshed.message() + "\nClick to re-scan audio devices.")));
					this.statusMessage = class_2561.method_43470("✅ Stereo Mix detected. Audio recording is enabled.");
					this.statusIsError = false;
				} else {
					button.method_25355(audioStatusText(false));
					button.method_47400(class_7919.method_47407(class_2561.method_43470(refreshed.message() + "\n" + STEREO_MIX_HELP_TEXT.getString())));
					this.statusMessage = class_2561.method_43470("⚠ Stereo Mix not detected. Video-only recording still works perfectly.");
					this.statusIsError = false;
					if (this.field_22787 != null && this.field_22787.field_1724 != null) {
						this.field_22787.field_1724.method_7353(class_2561.method_43470("⚠ Stereo Mix not found. Recording will continue in video-only mode."), false);
					}
				}
			}).method_46436(class_7919.method_47407(class_2561.method_43470(audioTooltip))).method_46434(rightWidgetLeft, 0, halfWidgetWidth, 20).method_46431(), y);
			y += 22;
			this.addLayoutWidget(audioDeviceField, y);
			y += 22;
			String audioContainer = config.getContainerFromFormat();
			List<AudioEncoder> detectedAudioEncoders = FFmpegEncoder.detectAvailableAudioEncoders();
			List<AudioEncoder> compatibleAudioEncoders = detectedAudioEncoders.stream().filter(encoder -> encoder.supportsContainer(audioContainer)).toList();
			if (compatibleAudioEncoders.isEmpty()) {
				RecordableMod.LOGGER.warn("No detected audio encoders support container {}. Falling back to config enum values.", audioContainer);
				compatibleAudioEncoders = Arrays.stream(AudioEncoder.values()).filter(encoder -> encoder.supportsContainer(audioContainer)).toList();
			}

			if (compatibleAudioEncoders.isEmpty()) {
				compatibleAudioEncoders = List.of(AudioEncoder.AAC);
			}

			RecordableMod.LOGGER
				.info("Audio encoder options for container {}: {}", audioContainer, compatibleAudioEncoders.stream().map(encoder -> encoder.displayName).toList());
			if (!compatibleAudioEncoders.contains(config.audioEncoder)) {
				config.audioEncoder = (AudioEncoder)compatibleAudioEncoders.get(0);
				saveConfigSafely(config);
			}

			this.addLayoutWidget(new RecordableSettingsScreen.AudioVolumeSlider(widgetLeft, 0, widgetWidth, 20, config.audioVolume, value -> {
				config.audioVolume = value;
				saveConfigSafely(config);
			}), y);
			y += 22;
			this.addLayoutWidget(
				this.addBooleanToggle(widgetLeft, 0, halfWidgetWidth, "screen.recordable.settings.capture_microphone", config.captureMicrophone, value -> {
					config.captureMicrophone = value;
					saveConfigSafely(config);
				}), y
			);
			String currentMic = config.microphoneDevice != null && !config.microphoneDevice.isBlank() ? config.microphoneDevice.trim() : "auto";
			List<String> initialMicOptions = buildMicDeviceOptions(ffExe, config.microphoneDevice);
			class_4185 micDeviceButton = class_4185.method_46430(micDeviceButtonLabel(currentMic), button -> {
					List<String> opts = buildMicDeviceOptions(ffExe, config.microphoneDevice);
					String cur = config.microphoneDevice != null && !config.microphoneDevice.isBlank() ? config.microphoneDevice.trim() : "auto";
					int idx = opts.indexOf(cur);
					if (idx < 0) {
						idx = 0;
					}

					String chosen = (String)opts.get((idx + 1) % opts.size());
					config.microphoneDevice = chosen;
					saveConfigSafely(config);
					AudioCapture.clearCache();
					button.method_25355(micDeviceButtonLabel(chosen));
					button.method_47400(class_7919.method_47407(micDeviceTooltip(opts, chosen)));
				})
				.method_46434(rightWidgetLeft, 0, halfWidgetWidth, 20)
				.method_46436(class_7919.method_47407(micDeviceTooltip(initialMicOptions, currentMic)))
				.method_46431();
			this.addLayoutWidget(micDeviceButton, y);
			y += 22;
			this.addLayoutWidget(new RecordableSettingsScreen.MixVolumeSlider(widgetLeft, 0, halfWidgetWidth, 20, "Game Volume", config.gameAudioVolume, value -> {
				config.gameAudioVolume = value;
				saveConfigSafely(config);
			}), y);
			this.addLayoutWidget(new RecordableSettingsScreen.MixVolumeSlider(rightWidgetLeft, 0, halfWidgetWidth, 20, "Mic Volume", config.microphoneVolume, value -> {
				config.microphoneVolume = value;
				saveConfigSafely(config);
			}), y);
			y += 22;
			class_339 pttToggle = this.addBooleanToggle(
				widgetLeft, 0, halfWidgetWidth, "screen.recordable.settings.push_to_talk", config.microphonePushToTalk, value -> {
					config.microphonePushToTalk = value;
					saveConfigSafely(config);
				}
			);
			pttToggle.method_47400(class_7919.method_47407(class_2561.method_43471("screen.recordable.settings.push_to_talk_hint")));
			this.addLayoutWidget(pttToggle, y);
			class_339 noiseToggle = this.addBooleanToggle(
				rightWidgetLeft, 0, halfWidgetWidth, "screen.recordable.settings.noise_suppression", config.noiseSuppression, value -> {
					config.noiseSuppression = value;
					saveConfigSafely(config);
				}
			);
			noiseToggle.method_47400(class_7919.method_47407(class_2561.method_43471("screen.recordable.settings.noise_suppression_hint")));
			this.addLayoutWidget(noiseToggle, y);
			y += 22;
			class_4185 testMicButton = class_4185.method_46430(class_2561.method_43471("screen.recordable.settings.test_mic"), button -> this.runMicTest(button, ffExe))
				.method_46434(widgetLeft, 0, widgetWidth, 20)
				.method_46436(class_7919.method_47407(class_2561.method_43471("screen.recordable.settings.test_mic_hint")))
				.method_46431();
			this.addLayoutWidget(testMicButton, y);
			y += 22;
			CycleButton audioDelayButton = CycleButton.create(
				widgetLeft, 0, widgetWidth, 20, audioDelayPresetMessage(config.audioDelayPreset, config.getEffectiveAudioDelay()), button -> {
					config.audioDelayPreset = config.audioDelayPreset.next();
					saveConfigSafely(config);
					if (this.field_22787 != null) {
						this.field_22787.method_1507(new RecordableSettingsScreen(this.parent));
					}
				}, button -> {
					config.audioDelayPreset = config.audioDelayPreset.previous();
					saveConfigSafely(config);
					if (this.field_22787 != null) {
						this.field_22787.method_1507(new RecordableSettingsScreen(this.parent));
					}
				}
			);
			audioDelayButton.method_47400(
				class_7919.method_47407(
					class_2561.method_43470(
						"Fine-tune audio sync if needed. Usually not required.\n\n• Auto: 0ms (recommended, sync is handled automatically)\n• None: 0ms (same as Auto, explicit zero)\n• Desktop: 46ms (legacy, for unusual audio drivers)\n• Android: 60ms (legacy, for mobile latency)\n• Custom: Set your own value with the slider below\n\nIf audio is ahead of video, increase the delay.\nIf audio is behind video, decrease the delay."
					)
				)
			);
			this.addLayoutWidget(audioDelayButton, y);
			y += 22;
			RecordableSettingsScreen.AudioDelaySlider audioDelaySlider = new RecordableSettingsScreen.AudioDelaySlider(
				widgetLeft, 0, widgetWidth, 20, config.audioSyncOffsetMs, value -> {
					config.audioSyncOffsetMs = value;
					saveConfigSafely(config);
				}
			);
			audioDelaySlider.field_22763 = config.audioDelayPreset == AudioDelayPreset.CUSTOM;
			this.addLayoutWidget(audioDelaySlider, y);
			y += 26;
			this.windowsAudioWarningY = y;
			int warningWrapWidth = Math.max(160, this.panelWidth - 28);
			class_2561 platformWarningText = getPlatformAudioWarningText();
			if (platformWarningText != null) {
				int warningLineCount = Math.max(1, this.field_22793.method_1728(platformWarningText, warningWrapWidth).size());
				int lineHeight = 9 + 1;
				this.windowsAudioWarningHeight = warningLineCount * lineHeight + 4;
			} else {
				this.windowsAudioWarningHeight = 0;
			}

			y += this.windowsAudioWarningHeight + 6;
			this.generalHeaderY = y;
			y += 12;
			this.addLayoutWidget(this.addBooleanToggle(widgetLeft, 0, halfWidgetWidth, "screen.recordable.settings.enabled", config.enabled, value -> {
				config.enabled = value;
				saveConfigSafely(config);
			}), y);
			class_339 showOverlayToggle = this.addBooleanToggle(
				rightWidgetLeft, 0, halfWidgetWidth, "screen.recordable.settings.overlay", config.showOverlay, value -> {
					config.showOverlay = value;
					saveConfigSafely(config);
				}
			);
			showOverlayToggle.method_47400(
				class_7919.method_47407(
					class_2561.method_43470(
						"Shows or hides the recording info overlay (REC timer, FPS, file size) on YOUR screen while recording. To control whether the overlay is saved into the video file, use the \"Bake in Overlay\" option in Streamer Mode."
					)
				)
			);
			this.addLayoutWidget(showOverlayToggle, y);
			y += 22;
			class_339 stopOnDisconnectToggle = this.addBooleanToggle(
				widgetLeft, 0, halfWidgetWidth, "screen.recordable.settings.stop_on_disconnect", config.stopOnDisconnect, value -> {
					config.stopOnDisconnect = value;
					saveConfigSafely(config);
				}
			);
			stopOnDisconnectToggle.method_47400(class_7919.method_47407(class_2561.method_43471("screen.recordable.settings.stop_on_disconnect.tooltip")));
			this.addLayoutWidget(stopOnDisconnectToggle, y);
			this.addLayoutWidget(
				this.addBooleanToggle(rightWidgetLeft, 0, halfWidgetWidth, "screen.recordable.settings.show_home_button", config.showHomeButton, value -> {
					config.showHomeButton = value;
					saveConfigSafely(config);
				}), y
			);
			y += 22;
			CycleButton overlayPositionButton = CycleButton.create(
				widgetLeft, 0, halfWidgetWidth, 20, class_2561.method_43470("Overlay Position: " + config.overlayPosition.displayName), button -> {
					config.overlayPosition = config.overlayPosition.next();
					saveConfigSafely(config);
					button.method_25355(class_2561.method_43470("Overlay Position: " + config.overlayPosition.displayName));
				}, button -> {
					config.overlayPosition = config.overlayPosition.previous();
					saveConfigSafely(config);
					button.method_25355(class_2561.method_43470("Overlay Position: " + config.overlayPosition.displayName));
				}
			);
			overlayPositionButton.method_47400(
				class_7919.method_47407(
					class_2561.method_43470(
						"Where to place the recording overlay on screen.\n\n- Top-Left: Classic position\n- Top-Right: Right side\n- Bottom-Left: Lower left\n- Bottom-Right: Lower right\n- Center-Top: Centered, below boss bars"
					)
				)
			);
			this.addLayoutWidget(overlayPositionButton, y);
			this.addLayoutWidget(new RecordableSettingsScreen.OverlayScaleSlider(rightWidgetLeft, 0, halfWidgetWidth, 20, config.overlayScale, value -> {
				config.overlayScale = value;
				saveConfigSafely(config);
			}), y);
			y += 22;
			class_342 outputDirField = new class_342(this.field_22793, widgetLeft, 0, widgetWidth, 20, class_2561.method_43471("screen.recordable.settings.output_dir"));
			outputDirField.method_1880(256);
			outputDirField.method_1852(config.outputDir == null ? "recordings" : config.outputDir);
			outputDirField.method_1863(value -> {
				config.outputDir = value != null && !value.isBlank() ? value.trim() : "recordings";
				saveConfigSafely(config);
			});
			this.addLayoutWidget(outputDirField, y);
			y += 22;
			this.outputLabelY = y;
			y += 10;
			this.outputPathY = y;
			y += 12;
			this.addLayoutWidget(new RecordableSettingsScreen.MaxFileSizeSlider(widgetLeft, 0, widgetWidth, 20, config.maxFileSizeMB, value -> {
				config.maxFileSizeMB = value;
				saveConfigSafely(config);
			}), y);
			y += 24;
			if (PlatformUtils.isAndroid()) {
				this.androidHeaderY = y;
				y += 12;
				class_339 galleryToggle = this.addBooleanToggle(
					widgetLeft, 0, halfWidgetWidth, "screen.recordable.settings.save_to_gallery", config.saveToGalleryOnAndroid, value -> {
						config.saveToGalleryOnAndroid = value;
						saveConfigSafely(config);
					}
				);
				galleryToggle.method_47400(class_7919.method_47407(class_2561.method_43471("screen.recordable.settings.save_to_gallery.tooltip")));
				class_339 compressToggle = this.addBooleanToggle(
					rightWidgetLeft, 0, halfWidgetWidth, "screen.recordable.settings.auto_compress", config.autoCompressOnAndroid, value -> {
						config.autoCompressOnAndroid = value;
						saveConfigSafely(config);
					}
				);
				compressToggle.method_47400(class_7919.method_47407(class_2561.method_43471("screen.recordable.settings.auto_compress.tooltip")));
				this.addLayoutWidget(galleryToggle, y);
				this.addLayoutWidget(compressToggle, y);
				y += 24;
			}

			this.autoRecordHeaderY = y;
			y += 12;
			class_339 autoRecordToggle = this.addBooleanToggle(
				widgetLeft, 0, halfWidgetWidth, "screen.recordable.settings.auto_record.enabled", config.autoRecord, value -> {
					config.autoRecord = value;
					saveConfigSafely(config);
				}
			);
			autoRecordToggle.method_47400(
				class_7919.method_47407(
					class_2561.method_43470(
						"Master switch for automatic recording.\n§7Starts and stops based on the Start/Stop triggers below\n§7(e.g. record on World Join, stop on World Leave).\n§7Set Start Trigger to Manual for hotkey-only recording."
					)
				)
			);
			this.addLayoutWidget(autoRecordToggle, y);
			this.addLayoutWidget(
				this.addCycleButton(
					rightWidgetLeft,
					0,
					halfWidgetWidth,
					"screen.recordable.settings.auto_record.start_trigger",
					RecordableConfig.AUTO_RECORD_TRIGGERS,
					() -> config.autoRecordTrigger,
					value -> {
						config.autoRecordTrigger = value;
						saveConfigSafely(config);
					}
				),
				y
			);
			y += 22;
			this.addLayoutWidget(
				this.addCycleButton(
					widgetLeft,
					0,
					widgetWidth,
					"screen.recordable.settings.auto_record.stop_trigger",
					RecordableConfig.AUTO_STOP_TRIGGERS,
					() -> config.autoStopTrigger,
					value -> {
						config.autoStopTrigger = value;
						saveConfigSafely(config);
					}
				),
				y
			);
			y += 24;
			this.appearanceHeaderY = y;
			y += 20;
			this.addLayoutWidget(
				new ColorPickerWidget(
					this.field_22793, widgetLeft, 0, widgetWidth, 20, class_2561.method_43471("screen.recordable.settings.overlay_color"), config.overlayColor, value -> {
						config.overlayColor = value;
						saveConfigSafely(config);
					}
				),
				y
			);
			y += 24;
			this.addLayoutWidget(
				new ColorPickerWidget(
					this.field_22793,
					widgetLeft,
					0,
					widgetWidth,
					20,
					class_2561.method_43471("screen.recordable.settings.menu_accent_color"),
					config.menuAccentColor,
					value -> {
						config.menuAccentColor = value;
						saveConfigSafely(config);
					}
				),
				y
			);
			y += 24;
			y += 6;
			CycleButton overlayStyleButton = CycleButton.create(
				widgetLeft, 0, widgetWidth, 20, class_2561.method_43470("Overlay Style: " + config.overlayStyleHud.displayName), button -> {
					config.overlayStyleHud = config.overlayStyleHud.next();
					saveConfigSafely(config);
					int savedScroll = this.scrollOffset;
					this.method_25426();
					this.scrollOffset = savedScroll;
				}, button -> {
					config.overlayStyleHud = config.overlayStyleHud.previous();
					saveConfigSafely(config);
					int savedScroll = this.scrollOffset;
					this.method_25426();
					this.scrollOffset = savedScroll;
				}
			);
			overlayStyleButton.method_47400(
				class_7919.method_47407(
					class_2561.method_43470("On-screen overlay visible while recording.\nSpeed-Runner's Classic = info panel. VHS = camcorder look. None = hidden.")
				)
			);
			this.addLayoutWidget(overlayStyleButton, y);
			y += 22;
			this.addLayoutWidget(
				class_4185.method_46430(class_2561.method_43470("Overlay Skin: " + (config.overlaySkinEnabled ? config.uiTheme.displayName : "Off")), button -> {
						config.overlaySkinEnabled = !config.overlaySkinEnabled;
						saveConfigSafely(config);
						button.method_25355(class_2561.method_43470("Overlay Skin: " + (config.overlaySkinEnabled ? config.uiTheme.displayName : "Off")));
					})
					.method_46434(widgetLeft, 0, widgetWidth, 20)
					.method_46436(
						class_7919.method_47407(
							class_2561.method_43470(
								"Skins the on-screen overlay with the colors of your selected UI Theme.\nOn = overlay follows the UI Theme. Off = overlay uses its own default colors."
							)
						)
					)
					.method_46431(),
				y
			);
			y += 22;
			OverlayStyleHud style = config.overlayStyleHud;
			boolean isVhs = style == OverlayStyleHud.VHS;
			if (isVhs || isVhs) {
				if (isVhs) {
					this.addLayoutWidget(this.addBooleanToggle(widgetLeft, 0, halfWidgetWidth, "screen.recordable.settings.vhs_brackets", config.vhsShowBrackets, v -> {
						config.vhsShowBrackets = v;
						saveConfigSafely(config);
					}), y);
				}

				if (isVhs) {
					this.addLayoutWidget(this.addBooleanToggle(rightWidgetLeft, 0, halfWidgetWidth, "screen.recordable.settings.vhs_play", config.vhsShowPlay, v -> {
						config.vhsShowPlay = v;
						saveConfigSafely(config);
					}), y);
				}

				y += 22;
			}

			if (isVhs || isVhs) {
				if (isVhs) {
					this.addLayoutWidget(this.addBooleanToggle(widgetLeft, 0, halfWidgetWidth, "screen.recordable.settings.vhs_date", config.vhsShowDate, v -> {
						config.vhsShowDate = v;
						saveConfigSafely(config);
					}), y);
				}

				if (isVhs) {
					this.addLayoutWidget(this.addBooleanToggle(rightWidgetLeft, 0, halfWidgetWidth, "screen.recordable.settings.vhs_sp", config.vhsShowSp, v -> {
						config.vhsShowSp = v;
						saveConfigSafely(config);
					}), y);
				}

				y += 22;
			}

			if (isVhs || isVhs) {
				if (isVhs) {
					this.addLayoutWidget(this.addBooleanToggle(widgetLeft, 0, halfWidgetWidth, "screen.recordable.settings.vhs_battery", config.vhsShowBattery, v -> {
						config.vhsShowBattery = v;
						saveConfigSafely(config);
					}), y);
				}

				if (isVhs) {
					this.addLayoutWidget(
						this.addBooleanToggle(rightWidgetLeft, 0, halfWidgetWidth, "screen.recordable.settings.vhs_audio_meter", config.vhsShowAudioMeter, v -> {
							config.vhsShowAudioMeter = v;
							saveConfigSafely(config);
						}), y
					);
				}

				y += 22;
			}

			if (isVhs) {
				this.addLayoutWidget(this.addBooleanToggle(widgetLeft, 0, halfWidgetWidth, "screen.recordable.settings.vhs_tape_counter", config.vhsShowTapeCounter, v -> {
					config.vhsShowTapeCounter = v;
					saveConfigSafely(config);
				}), y);
				y += 22;
			}

			y += 4;
			this.addLayoutWidget(
				class_4185.method_46430(class_2561.method_43470("\ud83c\udfa8 UI Theme: " + config.uiTheme.displayName), button -> {
						if (this.field_22787 != null) {
							this.field_22787.method_1507(new ThemeSettingsScreen(this));
						}
					})
					.method_46434(widgetLeft, 0, widgetWidth, 20)
					.method_46436(
						class_7919.method_47407(
							class_2561.method_43470(
								"Customize the mod's visual theme.\nChoose between VHS retro, Cinema film, Neon synthwave, and more.\nToggle scanlines, film grain, glitch effects and animations."
							)
						)
					)
					.method_46431(),
				y
			);
			y += 26;
			this.positionsHeaderY = y;
			y += 14;
			this.addLayoutWidget(
				class_4185.method_46430(class_2561.method_43470("✎ Position & Colors Editor"), button -> {
						if (this.field_22787 != null) {
							this.field_22787.method_1507(new OverlayPositionScreen(this));
						}
					})
					.method_46434(widgetLeft, 0, widgetWidth, 20)
					.method_46436(
						class_7919.method_47407(
							class_2561.method_43470(
								"Open the visual editor to reposition overlay elements and customize colors.\n\n• Drag elements to move them\n• Drag corner handles on Brackets to resize\n• Right-click to reset an element\n• Color panel on the right for per-element colors\n• ESC to cancel all changes"
							)
						)
					)
					.method_46431(),
				y
			);
			y += 22;
			this.addLayoutWidget(class_4185.method_46430(class_2561.method_43471("screen.recordable.settings.open_watermarks"), button -> {
				if (this.field_22787 != null) {
					this.field_22787.method_1507(new WatermarkScreen(this));
				}
			}).method_46434(widgetLeft, 0, widgetWidth, 20).method_46431(), y);
			y += 22;
			this.addLayoutWidget(
				class_4185.method_46430(class_2561.method_43470("◉ Streamer Mode"), button -> {
						if (this.field_22787 != null) {
							this.field_22787.method_1507(new StreamerModeScreen(this));
						}
					})
					.method_46434(widgetLeft, 0, widgetWidth, 20)
					.method_46436(class_7919.method_47407(class_2561.method_43470("Hide sensitive areas with censor boxes and enable smooth-motion recording.")))
					.method_46431(),
				y
			);
			y += 22;
			this.performanceHeaderY = y;
			y += 14;
			this.addLayoutWidget(
				class_4185.method_46430(class_2561.method_43470("⚡ Performance"), button -> {
						if (this.field_22787 != null) {
							this.field_22787.method_1507(new PerformanceScreen(this));
						}
					})
					.method_46434(widgetLeft, 0, widgetWidth, 20)
					.method_46436(
						class_7919.method_47407(
							class_2561.method_43470("Device presets, performance optimizer, smooth motion, frame pooling, FPS targets and performance stats - all in one place.")
						)
					)
					.method_46431(),
				y,
				"performance optimizer device preset smooth motion frame pooling fps stats"
			);
			y += 22;
			this.addLayoutWidget(
				class_4185.method_46430(class_2561.method_43470("\ud83d\udd0d Capture Test"), button -> {
						if (this.field_22787 != null) {
							this.field_22787.method_1507(new CaptureDiagnosticsScreen(this));
						}
					})
					.method_46434(widgetLeft, 0, widgetWidth, 20)
					.method_46436(
						class_7919.method_47407(
							class_2561.method_43470(
								"Runs a capture self-test and checks for the problems that cause black or blank recordings: framebuffer size mismatches, empty frames and a stuck capture source."
							)
						)
					)
					.method_46431(),
				y,
				"capture test diagnostics black screen blank frame size detector self test health"
			);
			y += 26;
			this.advancedHeaderY = y;
			y += 12;
			this.addLayoutWidget(
				this.addBooleanToggle(widgetLeft, 0, halfWidgetWidth, "screen.recordable.settings.show_toast", config.showPostRecordingToast, value -> {
					config.showPostRecordingToast = value;
					saveConfigSafely(config);
				}), y
			);
			this.addLayoutWidget(
				this.addBooleanToggle(rightWidgetLeft, 0, halfWidgetWidth, "screen.recordable.settings.show_timer", config.showRecordingTimer, value -> {
					config.showRecordingTimer = value;
					saveConfigSafely(config);
				}), y
			);
			y += 22;
			this.addLayoutWidget(
				this.addBooleanToggle(widgetLeft, 0, halfWidgetWidth, "screen.recordable.settings.show_est_size", config.showEstimatedFileSize, value -> {
					config.showEstimatedFileSize = value;
					saveConfigSafely(config);
				}), y
			);
			this.addLayoutWidget(this.addBooleanToggle(rightWidgetLeft, 0, halfWidgetWidth, "screen.recordable.settings.bookmarks", config.bookmarksEnabled, value -> {
				config.bookmarksEnabled = value;
				saveConfigSafely(config);
			}), y);
			y += 22;
			this.autoClipHeaderY = y;
			y += 12;
			class_339 autoClipMasterToggle = this.addBooleanToggle(
				widgetLeft, 0, widgetWidth, "screen.recordable.settings.autoclip_enabled", config.autoClipEnabled, value -> {
					config.autoClipEnabled = value;
					if (value) {
						config.autoClipOnAchievement = true;
						config.autoClipOnDeath = true;
						config.autoClipOnDimensionChange = true;
						config.autoClipOnBossKill = true;
						config.autoClipOnKill = true;
						config.autoClipOnPlayerKill = true;
					} else {
						config.autoClipOnAchievement = false;
						config.autoClipOnDeath = false;
						config.autoClipOnDimensionChange = false;
						config.autoClipOnBossKill = false;
						config.autoClipOnKill = false;
						config.autoClipOnPlayerKill = false;
					}

					saveConfigSafely(config);
					this.method_25426();
				}
			);
			autoClipMasterToggle.method_47400(
				class_7919.method_47407(class_2561.method_43470("Automatically record short clips when specific events occur (achievements, deaths, boss kills, etc.)"))
			);
			this.addLayoutWidget(autoClipMasterToggle, y);
			y += 22;
			class_339 achievementToggle = this.addBooleanToggle(
				widgetLeft, 0, halfWidgetWidth, "screen.recordable.settings.autoclip_on_achievement", config.autoClipOnAchievement, value -> {
					config.autoClipOnAchievement = value;
					saveConfigSafely(config);
				}
			);
			achievementToggle.field_22763 = config.autoClipEnabled;
			this.addLayoutWidget(achievementToggle, y);
			class_339 deathToggle = this.addBooleanToggle(
				rightWidgetLeft, 0, halfWidgetWidth, "screen.recordable.settings.autoclip_on_death", config.autoClipOnDeath, value -> {
					config.autoClipOnDeath = value;
					saveConfigSafely(config);
				}
			);
			deathToggle.field_22763 = config.autoClipEnabled;
			this.addLayoutWidget(deathToggle, y);
			y += 22;
			class_339 dimensionToggle = this.addBooleanToggle(
				widgetLeft, 0, halfWidgetWidth, "screen.recordable.settings.autoclip_on_dimension", config.autoClipOnDimensionChange, value -> {
					config.autoClipOnDimensionChange = value;
					saveConfigSafely(config);
				}
			);
			dimensionToggle.field_22763 = config.autoClipEnabled;
			this.addLayoutWidget(dimensionToggle, y);
			class_339 bossToggle = this.addBooleanToggle(
				rightWidgetLeft, 0, halfWidgetWidth, "screen.recordable.settings.autoclip_on_boss", config.autoClipOnBossKill, value -> {
					config.autoClipOnBossKill = value;
					saveConfigSafely(config);
				}
			);
			bossToggle.field_22763 = config.autoClipEnabled;
			this.addLayoutWidget(bossToggle, y);
			y += 22;
			class_339 killToggle = this.addBooleanToggle(
				widgetLeft, 0, halfWidgetWidth, "screen.recordable.settings.autoclip_on_kill", config.autoClipOnKill, value -> {
					config.autoClipOnKill = value;
					saveConfigSafely(config);
				}
			);
			killToggle.field_22763 = config.autoClipEnabled;
			this.addLayoutWidget(killToggle, y);
			class_339 playerKillToggle = this.addBooleanToggle(
				rightWidgetLeft, 0, halfWidgetWidth, "screen.recordable.settings.autoclip_on_player_kill", config.autoClipOnPlayerKill, value -> {
					config.autoClipOnPlayerKill = value;
					saveConfigSafely(config);
				}
			);
			playerKillToggle.field_22763 = config.autoClipEnabled;
			this.addLayoutWidget(playerKillToggle, y);
			y += 22;
			this.addLayoutWidget(new RecordableSettingsScreen.AutoClipDurationSlider(widgetLeft, 0, widgetWidth, 20, config.autoClipDuration, value -> {
				config.autoClipDuration = value;
				saveConfigSafely(config);
			}, config), y);
			y += 22;
			class_339 autoClipAudioToggle = this.addBooleanToggle(
				widgetLeft, 0, halfWidgetWidth, "screen.recordable.settings.autoclip_audio", config.autoClipAudio, value -> {
					config.autoClipAudio = value;
					saveConfigSafely(config);
				}
			);
			autoClipAudioToggle.field_22763 = config.autoClipEnabled;
			autoClipAudioToggle.method_47400(class_7919.method_47407(class_2561.method_43471("screen.recordable.settings.autoclip_audio.tooltip")));
			this.addLayoutWidget(autoClipAudioToggle, y);
			y += 22;
			class_339 autoClipFpsSlider = new RecordableSettingsScreen.AutoClipFpsSlider(widgetLeft, 0, widgetWidth, 20, config.autoClipFps, value -> {
				config.autoClipFps = value;
				saveConfigSafely(config);
			});
			autoClipFpsSlider.field_22763 = config.autoClipEnabled;
			this.addLayoutWidget(autoClipFpsSlider, y);
			y += 22;
			this.v07HeaderY = y;
			y += 12;
			this.addLayoutWidget(this.addBooleanToggle(widgetLeft, 0, halfWidgetWidth, "screen.recordable.settings.markers_enabled", config.markersEnabled, value -> {
				config.markersEnabled = value;
				saveConfigSafely(config);
			}), y);
			this.addLayoutWidget(
				this.addBooleanToggle(rightWidgetLeft, 0, halfWidgetWidth, "screen.recordable.settings.export_chapters", config.exportChapterFile, value -> {
					config.exportChapterFile = value;
					saveConfigSafely(config);
				}), y
			);
			y += 22;
			this.addLayoutWidget(
				this.addBooleanToggle(widgetLeft, 0, halfWidgetWidth, "screen.recordable.settings.embed_chapters", config.embedChaptersInVideo, value -> {
					config.embedChaptersInVideo = value;
					saveConfigSafely(config);
				}), y
			);
			this.addLayoutWidget(
				this.addBooleanToggle(rightWidgetLeft, 0, halfWidgetWidth, "screen.recordable.settings.auto_marker_start", config.autoMarkerOnStart, value -> {
					config.autoMarkerOnStart = value;
					saveConfigSafely(config);
				}), y
			);
			y += 22;
			this.addLayoutWidget(
				this.addBooleanToggle(widgetLeft, 0, halfWidgetWidth, "screen.recordable.settings.replay_buffer_enabled", config.replayBufferEnabled, value -> {
					config.replayBufferEnabled = value;
					saveConfigSafely(config);
				}), y
			);
			this.addLayoutWidget(
				this.addBooleanToggle(rightWidgetLeft, 0, halfWidgetWidth, "screen.recordable.settings.replay_notify", config.replayBufferNotify, value -> {
					config.replayBufferNotify = value;
					saveConfigSafely(config);
				}), y
			);
			y += 22;
			this.addLayoutWidget(new RecordableSettingsScreen.ReplayBufferSlider(widgetLeft, 0, halfWidgetWidth, 20, config.replayBufferDurationSeconds, value -> {
				config.replayBufferDurationSeconds = value;
				saveConfigSafely(config);
			}), y);
			this.addLayoutWidget(
				this.addCycleButton(
					rightWidgetLeft,
					0,
					halfWidgetWidth,
					"screen.recordable.settings.replay_quality",
					RecordableConfig.REPLAY_QUALITIES,
					() -> config.replayBufferQuality,
					value -> {
						config.replayBufferQuality = value;
						saveConfigSafely(config);
					}
				),
				y
			);
			y += 22;
			this.addLayoutWidget(
				this.addBooleanToggle(widgetLeft, 0, halfWidgetWidth, "screen.recordable.settings.separate_audio", config.separateAudioTracks, value -> {
					config.separateAudioTracks = value;
					saveConfigSafely(config);
				}), y
			);
			this.addLayoutWidget(
				this.addBooleanToggle(rightWidgetLeft, 0, halfWidgetWidth, "screen.recordable.settings.watermarks_enabled", config.watermarksEnabled, value -> {
					config.watermarksEnabled = value;
					saveConfigSafely(config);
				}), y
			);
			y += 22;
			this.addLayoutWidget(class_4185.method_46430(class_2561.method_43471("screen.recordable.settings.open_storage"), button -> {
				if (this.field_22787 != null) {
					this.field_22787.method_1507(new StorageManagerScreen(this));
				}
			}).method_46434(widgetLeft, 0, halfWidgetWidth, 20).method_46431(), y);
			this.addLayoutWidget(
				this.addBooleanToggle(rightWidgetLeft, 0, halfWidgetWidth, "screen.recordable.settings.auto_cleanup", config.autoCleanupEnabled, value -> {
					config.autoCleanupEnabled = value;
					saveConfigSafely(config);
				}), y
			);
			y += 22;
			this.chatNotifyHeaderY = y;
			y += 12;
			this.addLayoutWidget(this.addBooleanToggle(widgetLeft, 0, halfWidgetWidth, "screen.recordable.settings.notify_recording", config.notifyRecording, value -> {
				config.notifyRecording = value;
				saveConfigSafely(config);
			}), y);
			this.addLayoutWidget(this.addBooleanToggle(rightWidgetLeft, 0, halfWidgetWidth, "screen.recordable.settings.notify_clips", config.notifyClips, value -> {
				config.notifyClips = value;
				saveConfigSafely(config);
			}), y);
			y += 22;
			this.addLayoutWidget(this.addBooleanToggle(widgetLeft, 0, halfWidgetWidth, "screen.recordable.settings.notify_replay", config.notifyReplayBuffer, value -> {
				config.notifyReplayBuffer = value;
				saveConfigSafely(config);
			}), y);
			this.addLayoutWidget(
				this.addBooleanToggle(rightWidgetLeft, 0, halfWidgetWidth, "screen.recordable.settings.notify_autorecord", config.notifyAutoRecord, value -> {
					config.notifyAutoRecord = value;
					saveConfigSafely(config);
				}), y
			);
			y += 22;
			this.addLayoutWidget(this.addBooleanToggle(widgetLeft, 0, halfWidgetWidth, "screen.recordable.settings.notify_bookmarks", config.notifyBookmarks, value -> {
				config.notifyBookmarks = value;
				saveConfigSafely(config);
			}), y);
			this.addLayoutWidget(
				this.addBooleanToggle(rightWidgetLeft, 0, halfWidgetWidth, "screen.recordable.settings.notify_warnings", config.notifyWarnings, value -> {
					config.notifyWarnings = value;
					saveConfigSafely(config);
				}), y
			);
			y += 22;
			this.compatHeaderY = y;
			y += 12;
			this.addLayoutWidget(this.addBooleanToggle(widgetLeft, 0, halfWidgetWidth, "screen.recordable.settings.compat_bridge", config.replayCompatBridge, value -> {
				config.replayCompatBridge = value;
				saveConfigSafely(config);
			}), y);
			class_339 compatAutoRecordToggle = this.addBooleanToggle(
				rightWidgetLeft, 0, halfWidgetWidth, "screen.recordable.settings.compat_autorecord", config.replayAutoRecordPlayback, value -> {
					config.replayAutoRecordPlayback = value;
					saveConfigSafely(config);
				}
			);
			compatAutoRecordToggle.method_47400(class_7919.method_47407(class_2561.method_43471("screen.recordable.settings.compat_autorecord.tooltip")));
			this.addLayoutWidget(compatAutoRecordToggle, y);
			y += 22;
			class_339 compatYieldAudioToggle = this.addBooleanToggle(
				widgetLeft, 0, halfWidgetWidth, "screen.recordable.settings.compat_yield_audio", config.replayYieldAudioDevice, value -> {
					config.replayYieldAudioDevice = value;
					saveConfigSafely(config);
				}
			);
			compatYieldAudioToggle.method_47400(class_7919.method_47407(class_2561.method_43471("screen.recordable.settings.compat_yield_audio.tooltip")));
			this.addLayoutWidget(compatYieldAudioToggle, y);
			y += 22;
			this.diskSpaceInfoY = y;
			y += 14;
			this.ffmpegStatusY = y;
			y += 18;
			this.contentHeight = y;
			this.fullContentHeight = y;
			int halfButtonWidth = Math.max(84, (widgetWidth - 6) / 2);
			this.method_37063(
				class_4185.method_46430(class_2561.method_43471("screen.recordable.settings.open_folder"), button -> this.openRecordingsFolder())
					.method_46434(widgetLeft, this.footerY, halfButtonWidth, 20)
					.method_46431()
			);
			this.method_37063(
				class_4185.method_46430(class_2561.method_43471("screen.recordable.settings.done"), button -> this.saveAndClose())
					.method_46434(widgetLeft + halfButtonWidth + 6, this.footerY, halfButtonWidth, 20)
					.method_46431()
			);
			this.updateWidgetLayout();
		} catch (Throwable var62) {
			RecordableMod.LOGGER.error("Failed to initialize Record-able settings screen.", var62);
			this.statusMessage = class_2561.method_43470("Failed to open settings. Check logs for details.");
			this.statusIsError = true;
			this.addFallbackCloseButton();
		}
	}

	private void addLayoutWidget(class_339 widget, int baseY) {
		this.addLayoutWidget(widget, baseY, null);
	}

	private void addLayoutWidget(class_339 widget, int baseY, String keywords) {
		this.layoutWidgets.add(new RecordableSettingsScreen.LayoutWidget(widget, baseY, keywords));
		this.method_37063(widget);
	}

	private void addFallbackCloseButton() {
		int buttonWidth = 150;
		int x = (this.field_22789 - buttonWidth) / 2;
		int y = Math.max(24, this.field_22790 - 34);
		this.panelWidth = Math.max(220, buttonWidth + 40);
		this.panelLeft = (this.field_22789 - this.panelWidth) / 2;
		this.panelTop = Math.max(8, y - 48);
		this.panelBottom = Math.min(this.field_22790 - 6, y + 20 + 12);
		this.panelBodyTop = this.panelTop + 24;
		this.panelBodyBottom = y - 6;
		this.footerY = y;
		this.contentHeight = 0;
		this.videoHeaderY = this.panelTop + 24;
		this.audioHeaderY = this.videoHeaderY;
		this.generalHeaderY = this.videoHeaderY;
		this.androidHeaderY = this.videoHeaderY;
		this.autoRecordHeaderY = this.videoHeaderY;
		this.appearanceHeaderY = this.videoHeaderY;
		this.positionsHeaderY = this.videoHeaderY;
		this.performanceHeaderY = this.videoHeaderY;
		this.advancedHeaderY = this.videoHeaderY;
		this.autoClipHeaderY = this.videoHeaderY;
		this.v07HeaderY = this.videoHeaderY;
		this.chatNotifyHeaderY = this.videoHeaderY;
		this.compatHeaderY = this.videoHeaderY;
		this.bitrateLabelY = this.videoHeaderY + 12;
		this.performanceHintY = this.bitrateLabelY + 12;
		this.outputLabelY = this.performanceHintY + 12;
		this.outputPathY = this.outputLabelY + 12;
		this.diskSpaceInfoY = this.outputPathY + 12;
		this.ffmpegStatusY = this.diskSpaceInfoY + 12;
		this.windowsAudioWarningY = this.ffmpegStatusY + 12;
		this.windowsAudioWarningHeight = 0;
		this.method_37063(
			class_4185.method_46430(class_2561.method_43471("screen.recordable.settings.done"), button -> this.saveAndClose())
				.method_46434(x, y, buttonWidth, 20)
				.method_46431()
		);
	}

	private class_339 addCycleButton(int x, int y, int width, String translationKey, String[] values, Supplier<String> getter, Consumer<String> setter) {
		return CycleButton.create(x, y, width, 20, cycleMessage(translationKey, (String)getter.get()), button -> {
			String next = nextValue(values, (String)getter.get());

			try {
				setter.accept(next);
				button.method_25355(cycleMessage(translationKey, next));
			} catch (Throwable var7) {
				RecordableMod.LOGGER.warn("Failed to update setting {} to {}.", new Object[]{translationKey, next, var7});
			}
		}, button -> {
			String prev = prevValue(values, (String)getter.get());

			try {
				setter.accept(prev);
				button.method_25355(cycleMessage(translationKey, prev));
			} catch (Throwable var7) {
				RecordableMod.LOGGER.warn("Failed to update setting {} to {}.", new Object[]{translationKey, prev, var7});
			}
		});
	}

	private class_339 addBooleanToggle(int x, int y, int width, String translationKey, boolean value, Consumer<Boolean> setter) {
		return class_5676.method_32613(value).method_32617(x, y, width, 20, class_2561.method_43471(translationKey), (button, newValue) -> {
			try {
				setter.accept(newValue);
			} catch (Throwable var5) {
				RecordableMod.LOGGER.warn("Failed to update setting {} to {}.", new Object[]{translationKey, newValue, var5});
			}
		});
	}

	public boolean method_25401(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (!(mouseX < (double)this.panelLeft)
			&& !(mouseX > (double)(this.panelLeft + this.panelWidth))
			&& !(mouseY < (double)this.panelBodyTop)
			&& !(mouseY > (double)this.panelBodyBottom)) {
			int maxScroll = Math.max(0, this.contentHeight - (this.panelBodyBottom - this.panelBodyTop));
			if (maxScroll <= 0) {
				return super.method_25401(mouseX, mouseY, horizontalAmount, verticalAmount);
			} else {
				int delta = (int)Math.round(verticalAmount * -20.0);
				if (delta == 0) {
					delta = verticalAmount > 0.0 ? -20 : 20;
				}

				this.scrollOffset = Math.max(0, Math.min(maxScroll, this.scrollOffset + delta));
				this.updateWidgetLayout();
				return true;
			}
		} else {
			return super.method_25401(mouseX, mouseY, horizontalAmount, verticalAmount);
		}
	}

	public boolean method_25402(double mouseX, double mouseY, int button) {
		if (button == 0 && this.isOverScrollbar(mouseX, mouseY)) {
			this.draggingScrollbar = true;
			this.scrollToMouse(mouseY);
			return true;
		} else {
			return super.method_25402(mouseX, mouseY, button);
		}
	}

	public boolean method_25403(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (this.draggingScrollbar && button == 0) {
			this.scrollToMouse(mouseY);
			return true;
		} else {
			return super.method_25403(mouseX, mouseY, button, deltaX, deltaY);
		}
	}

	public boolean method_25406(double mouseX, double mouseY, int button) {
		if (button == 0 && this.draggingScrollbar) {
			this.draggingScrollbar = false;
			return true;
		} else {
			return super.method_25406(mouseX, mouseY, button);
		}
	}

	private boolean isOverScrollbar(double mouseX, double mouseY) {
		int viewportHeight = this.panelBodyBottom - this.panelBodyTop;
		int maxScroll = Math.max(0, this.contentHeight - viewportHeight);
		if (maxScroll <= 0) {
			return false;
		} else {
			int barLeft = this.panelLeft + this.panelWidth - 4;
			return mouseX >= (double)(barLeft - 2) && mouseX <= (double)(barLeft + 5) && mouseY >= (double)this.panelBodyTop && mouseY <= (double)this.panelBodyBottom;
		}
	}

	private void scrollToMouse(double mouseY) {
		int viewportHeight = this.panelBodyBottom - this.panelBodyTop;
		int maxScroll = Math.max(0, this.contentHeight - viewportHeight);
		if (maxScroll > 0) {
			int thumbHeight = Math.max(22, (int)((double)viewportHeight * ((double)viewportHeight / (double)this.contentHeight)));
			int available = viewportHeight - thumbHeight;
			if (available <= 0) {
				this.scrollOffset = 0;
			} else {
				double rel = (mouseY - (double)this.panelBodyTop - (double)thumbHeight / 2.0) / (double)available;
				rel = Math.max(0.0, Math.min(1.0, rel));
				this.scrollOffset = (int)Math.round(rel * (double)maxScroll);
			}

			this.updateWidgetLayout();
		}
	}

	public boolean method_25404(int keyCode, int scanCode, int modifiers) {
		if (this.searchBox != null && this.searchBox.method_25370()) {
			return super.method_25404(keyCode, scanCode, modifiers);
		} else if (keyCode == 264) {
			this.scrollOffset += 22;
			this.updateWidgetLayout();
			return true;
		} else if (keyCode == 265) {
			this.scrollOffset -= 22;
			this.updateWidgetLayout();
			return true;
		} else {
			return super.method_25404(keyCode, scanCode, modifiers);
		}
	}

	private void updateWidgetLayout() {
		boolean searching = !this.searchQuery.isEmpty();
		if (searching) {
			this.updateWidgetLayoutSearching();
		} else {
			this.searchMatchRows = -1;
			this.contentHeight = this.fullContentHeight;
			int maxScroll = Math.max(0, this.contentHeight - (this.panelBodyBottom - this.panelBodyTop));
			this.scrollOffset = Math.max(0, Math.min(maxScroll, this.scrollOffset));

			for (RecordableSettingsScreen.LayoutWidget item : this.layoutWidgets) {
				int y = this.panelBodyTop + item.baseY - this.scrollOffset;
				class_339 widget = item.widget;
				if (widget instanceof ColorPickerWidget colorPickerWidget) {
					colorPickerWidget.method_48229(widget.method_46426(), y);
				} else {
					widget.method_46419(y);
				}

				boolean visible = y >= this.panelBodyTop && y + widget.method_25364() <= this.panelBodyBottom;
				widget.field_22764 = visible;
				widget.field_22763 = visible;
			}
		}
	}

	private void updateWidgetLayoutSearching() {
		List<Integer> distinctBaseYs = new ArrayList();

		for (RecordableSettingsScreen.LayoutWidget item : this.layoutWidgets) {
			if (this.matchesSearch(item) && !distinctBaseYs.contains(item.baseY)) {
				distinctBaseYs.add(item.baseY);
			}
		}

		Collections.sort(distinctBaseYs);
		this.searchMatchRows = distinctBaseYs.size();
		this.contentHeight = Math.max(0, distinctBaseYs.size() * 22);
		int maxScroll = Math.max(0, this.contentHeight - (this.panelBodyBottom - this.panelBodyTop));
		this.scrollOffset = Math.max(0, Math.min(maxScroll, this.scrollOffset));

		for (RecordableSettingsScreen.LayoutWidget itemx : this.layoutWidgets) {
			class_339 widget = itemx.widget;
			if (this.matchesSearch(itemx)) {
				int rowIndex = distinctBaseYs.indexOf(itemx.baseY);
				int y = this.panelBodyTop + rowIndex * 22 - this.scrollOffset;
				if (widget instanceof ColorPickerWidget colorPickerWidget) {
					colorPickerWidget.method_48229(widget.method_46426(), y);
				} else {
					widget.method_46419(y);
				}

				boolean visible = y >= this.panelBodyTop && y + widget.method_25364() <= this.panelBodyBottom;
				widget.field_22764 = visible;
				widget.field_22763 = visible;
			} else {
				widget.field_22764 = false;
				widget.field_22763 = false;
			}
		}
	}

	private boolean matchesSearch(RecordableSettingsScreen.LayoutWidget item) {
		if (this.searchQuery.isEmpty()) {
			return true;
		} else {
			StringBuilder sb = new StringBuilder();

			try {
				class_2561 message = item.widget.method_25369();
				if (message != null) {
					sb.append(message.getString().toLowerCase(Locale.ROOT));
				}
			} catch (Throwable var4) {
			}

			if (item.keywords != null) {
				sb.append(' ').append(item.keywords);
			}

			return sb.toString().contains(this.searchQuery);
		}
	}

	public void method_25394(class_332 context, int mouseX, int mouseY, float delta) {
		this.method_25420(context, mouseX, mouseY, delta);
		ThemeColors colors = ThemeEngine.get().colors();
		ThemePreset preset = ThemeEngine.get().preset();
		int accent = colors.accent;
		int left = this.panelLeft - 6;
		int right = this.panelLeft + this.panelWidth + 6;
		if (preset == ThemePreset.CINEMA) {
			ThemedPanel.drawFilmPanel(context, left, this.panelTop - 6, right, this.panelBottom);
		} else {
			ThemedPanel.drawPanel(context, left, this.panelTop - 6, right, this.panelBottom);
		}

		if (preset == ThemePreset.VHS || preset == ThemePreset.NEON) {
			TypewriterText.renderFlickerText(
				context,
				this.field_22793,
				"[ " + this.field_22785.getString() + " ]",
				this.field_22789 / 2 - this.field_22793.method_1727("[ " + this.field_22785.getString() + " ]") / 2,
				this.panelTop,
				colors.headerText
			);
		} else if (preset == ThemePreset.CINEMA) {
			context.method_25300(this.field_22793, "★ " + this.field_22785.getString() + " ★", this.field_22789 / 2, this.panelTop, colors.headerText);
		} else {
			context.method_27534(this.field_22793, this.field_22785, this.field_22789 / 2, this.panelTop, colors.headerText);
		}

		super.method_25394(context, mouseX, mouseY, delta);
		if (this.searchQuery.isEmpty()) {
			this.drawHeader(context, "screen.recordable.settings.video", this.videoHeaderY);
			this.drawHeader(context, "screen.recordable.settings.audio", this.audioHeaderY);
			this.drawHeader(context, "screen.recordable.settings.general", this.generalHeaderY);
			if (PlatformUtils.isAndroid()) {
				this.drawHeader(context, "screen.recordable.settings.android", this.androidHeaderY);
			}

			this.drawHeader(context, "screen.recordable.settings.auto_record.section", this.autoRecordHeaderY);
			this.drawHeader(context, "screen.recordable.settings.appearance", this.appearanceHeaderY);
			this.drawHeader(context, "screen.recordable.settings.positions", this.positionsHeaderY);
			this.drawHeader(context, "screen.recordable.settings.performance_section", this.performanceHeaderY);
			this.drawHeader(context, "screen.recordable.settings.advanced", this.advancedHeaderY);
			this.drawHeader(context, "screen.recordable.settings.autoclip.section", this.autoClipHeaderY);
			this.drawHeader(context, "screen.recordable.settings.v07_section", this.v07HeaderY);
			this.drawHeader(context, "screen.recordable.settings.notify_section", this.chatNotifyHeaderY);
			this.drawHeader(context, "screen.recordable.settings.compat_section", this.compatHeaderY);
			this.drawLabel(context, class_2561.method_43471("screen.recordable.settings.bitrate_hint"), this.bitrateLabelY, colors.textMuted);
			String perfHint = RecordableConfig.get().getPerformanceHint();
			int perfHintColor = !perfHint.contains("drop") && !perfHint.contains("expensive") ? colors.textMuted : -13210;
			this.drawWrappedLabel(context, class_2561.method_43470("Performance: " + perfHint), this.performanceHintY, this.panelWidth - 28, perfHintColor);
			this.drawLabel(context, class_2561.method_43471("screen.recordable.settings.output_dir"), this.outputLabelY, colors.textMuted);
			this.drawWrappedLabel(context, class_2561.method_43470(this.getDisplayOutputPath()), this.outputPathY, this.panelWidth - 28, colors.textMuted);
			if (this.windowsAudioWarningHeight > 0) {
				class_2561 platformWarning = getPlatformAudioWarningText();
				if (platformWarning != null) {
					int warningColor = -13210;
					this.drawWrappedLabel(context, platformWarning, this.windowsAudioWarningY, this.panelWidth - 28, warningColor);
				}
			}

			try {
				String diskInfo = "Disk: " + DiskSpaceGuardian.getFormattedFreeSpace(RecordableConfig.get().getOutputDirectory()) + " free";
				this.drawLabel(context, class_2561.method_43470(diskInfo), this.diskSpaceInfoY, colors.textMuted);
			} catch (Exception var14) {
			}

			if (this.ffmpegStatus != null) {
				this.drawWrappedLabel(context, this.ffmpegStatus, this.ffmpegStatusY, this.panelWidth - 28, this.ffmpegStatusIsError ? colors.textError : colors.textMuted);
			}
		} else if (this.searchMatchRows == 0) {
			context.method_27534(
				this.field_22793, class_2561.method_43471("screen.recordable.settings.no_search_results"), this.field_22789 / 2, this.panelBodyTop + 12, colors.textMuted
			);
		}

		if (this.statusMessage != null) {
			context.method_27534(
				this.field_22793, this.statusMessage, this.field_22789 / 2, this.panelBottom - 12, this.statusIsError ? colors.textError : colors.textMuted
			);
		}

		if (preset == ThemePreset.VHS) {
			boolean recording = RecordingManager.getInstance().isRecording();
			if (recording) {
				ThemedPanel.drawVhsStatusBadge(context, this.field_22793, "● REC", right - 50, this.panelTop - 3, true);
			}
		}

		this.renderScrollbar(context, accent);
	}

	private void drawHeader(class_332 context, String key, int baseY) {
		int y = this.panelBodyTop + baseY - this.scrollOffset;
		if (y >= this.panelBodyTop - 12 && y <= this.panelBodyBottom + 2) {
			String text = class_2561.method_43471(key).getString();
			ThemedPanel.drawSectionHeader(context, this.field_22793, text, this.panelLeft + 14, y, this.panelWidth - 28);
		}
	}

	private void drawLabel(class_332 context, class_2561 text, int baseY, int color) {
		this.drawLabel(context, text, baseY, color, this.panelLeft + 14);
	}

	private void drawLabel(class_332 context, class_2561 text, int baseY, int color, int x) {
		int y = this.panelBodyTop + baseY - this.scrollOffset;
		if (y >= this.panelBodyTop - 12 && y <= this.panelBodyBottom + 2) {
			RenderHelper.drawText(context, this.field_22793, text, x, y, color);
		}
	}

	private void drawWrappedLabel(class_332 context, class_2561 text, int baseY, int wrapWidth, int color) {
		int y = this.panelBodyTop + baseY - this.scrollOffset;
		if (y >= this.panelBodyTop - 18 && y <= this.panelBodyBottom + 2) {
			context.method_51440(this.field_22793, text, this.panelLeft + 14, y, wrapWidth, color);
		}
	}

	private void renderScrollbar(class_332 context, int accent) {
		int viewportHeight = this.panelBodyBottom - this.panelBodyTop;
		int maxScroll = Math.max(0, this.contentHeight - viewportHeight);
		if (maxScroll > 0) {
			int barLeft = this.panelLeft + this.panelWidth - 4;
			int thumbHeight = Math.max(22, (int)((double)viewportHeight * ((double)viewportHeight / (double)this.contentHeight)));
			int available = viewportHeight - thumbHeight;
			int thumbTop = this.panelBodyTop + (int)((double)this.scrollOffset / (double)maxScroll * (double)available);
			ThemedPanel.drawScrollbar(context, barLeft, this.panelBodyTop, this.panelBodyBottom, thumbTop, thumbHeight);
		}
	}

	public void method_25419() {
		this.saveAndClose();
	}

	private static class_2561 cycleEncoderMessage(VideoEncoder encoder) {
		VideoEncoder value = encoder == null ? VideoEncoder.SOFTWARE : encoder;
		return class_2561.method_43470("Video Encoder: " + encoderRuntimeLabel(value));
	}

	private static String encoderRuntimeLabel(VideoEncoder value) {
		String label = value.displayName;
		if (value == VideoEncoder.SOFTWARE) {
			String codec = FFmpegEncoder.getCachedSoftwareCodec();
			if (codec != null) {
				label = label + " [" + codec + "]";
			}
		}

		return label;
	}

	private static VideoEncoder nextEncoder(List<VideoEncoder> values, VideoEncoder current) {
		if (values != null && !values.isEmpty()) {
			int index = values.indexOf(current);
			return index < 0 ? (VideoEncoder)values.get(0) : (VideoEncoder)values.get((index + 1) % values.size());
		} else {
			return VideoEncoder.SOFTWARE;
		}
	}

	private static VideoEncoder prevEncoder(List<VideoEncoder> values, VideoEncoder current) {
		if (values != null && !values.isEmpty()) {
			int index = values.indexOf(current);
			return index < 0 ? (VideoEncoder)values.get(0) : (VideoEncoder)values.get((index - 1 + values.size()) % values.size());
		} else {
			return VideoEncoder.SOFTWARE;
		}
	}

	private static AudioEncoder nextAudioEncoder(List<AudioEncoder> values, AudioEncoder current) {
		if (values != null && !values.isEmpty()) {
			int index = values.indexOf(current);
			return index < 0 ? (AudioEncoder)values.get(0) : (AudioEncoder)values.get((index + 1) % values.size());
		} else {
			return AudioEncoder.AAC;
		}
	}

	private static class_2561 cycleMessage(String translationKey, String value) {
		String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
		if (translationKey.startsWith("screen.recordable.settings.auto_record.")) {
			class_2561 translatedValue = class_2561.method_43471(translationKey + ".value." + normalized);
			return class_2561.method_43469(translationKey, new Object[]{translatedValue});
		} else {
			return translationKey.equals("screen.recordable.settings.replay_quality")
				? class_2561.method_43469(translationKey, new Object[]{class_2561.method_43470(replayQualityLabel(normalized))})
				: class_2561.method_43469(translationKey, new Object[]{class_2561.method_43470(displayValue(value))});
		}
	}

	private static String replayQualityLabel(String value) {
		String var1 = value == null ? "" : value;
		switch (var1) {
			case "source":
				return "Source";
			case "balanced":
				return "720p30";
			case "performance":
				return "480p30";
			case "high":
				return "1080p60";
			default:
				return displayValue(value);
		}
	}

	private static String displayValue(String value) {
		if (value != null && !value.isBlank()) {
			return value.length() <= 4 ? value.toUpperCase(Locale.ROOT) : Character.toUpperCase(value.charAt(0)) + value.substring(1);
		} else {
			return "?";
		}
	}

	private static String nextValue(String[] values, String current) {
		if (values != null && values.length != 0) {
			for (int index = 0; index < values.length; index++) {
				if (values[index].equalsIgnoreCase(current == null ? "" : current)) {
					return values[(index + 1) % values.length];
				}
			}

			return values[0];
		} else {
			return current;
		}
	}

	private static String prevValue(String[] values, String current) {
		if (values != null && values.length != 0) {
			for (int index = 0; index < values.length; index++) {
				if (values[index].equalsIgnoreCase(current == null ? "" : current)) {
					return values[(index - 1 + values.length) % values.length];
				}
			}

			return values[0];
		} else {
			return current;
		}
	}

	private static String truncateDeviceName(String name, int maxLen) {
		if (name == null) {
			return "?";
		} else {
			return name.length() <= maxLen ? name : name.substring(0, maxLen - 3) + "...";
		}
	}

	private static class_2561 audioDelayPresetMessage(AudioDelayPreset preset, int effectiveMs) {
		if (preset == null) {
			preset = AudioDelayPreset.AUTO;
		}

		return class_2561.method_43470("Audio Delay: " + preset.displayName + " (" + effectiveMs + "ms)");
	}

	private void runMicTest(class_4185 button, String ffExe) {
		RecordableConfig cfg = RecordableConfig.get();
		String device = cfg.microphoneDevice;
		button.field_22763 = false;
		button.method_25355(class_2561.method_43471("screen.recordable.settings.test_mic_running"));
		Thread t = new Thread(
			() -> {
				MicTestResult result;
				try {
					result = AudioCapture.testMicrophoneLevel(ffExe, device, 3);
				} catch (Throwable var6) {
					result = new MicTestResult(false, device == null ? "auto" : device, Double.NaN, Double.NaN, "Mic test crashed: " + var6.getMessage());
				}

				MicTestResult r = result;
				class_310 mc = class_310.method_1551();
				mc.execute(
					() -> {
						button.field_22763 = true;
						button.method_25355(class_2561.method_43471("screen.recordable.settings.test_mic"));
						String prefix = r.hasSignal() ? "✔ Mic test: " : "⚠ Mic test: ";
						if (mc.field_1724 != null) {
							mc.field_1724.method_7353(class_2561.method_43470(prefix + r.message()), false);
							if (r.deviceName() != null && !r.deviceName().isBlank()) {
								mc.field_1724.method_7353(class_2561.method_43470("   Device: " + r.deviceName()), false);
							}
						} else {
							button.method_47400(class_7919.method_47407(class_2561.method_43470(r.message())));
						}

						RecordableMod.LOGGER
							.info(
								"Mic test result: success={} signal={} device='{}' mean={} max={} msg='{}'",
								new Object[]{r.success(), r.hasSignal(), r.deviceName(), r.meanDb(), r.maxDb(), r.message()}
							);
					}
				);
			},
			"recordable-mic-test"
		);
		t.setDaemon(true);
		t.start();
	}

	private static List<String> buildMicDeviceOptions(String ffExe, String configured) {
		List<String> opts = new ArrayList();
		opts.add("auto");

		try {
			for (String d : AudioCapture.listMicrophoneDevices(ffExe)) {
				if (d != null && !d.isBlank() && !opts.contains(d)) {
					opts.add(d);
				}
			}
		} catch (Throwable var5) {
		}

		String cur = configured != null && !configured.isBlank() ? configured.trim() : "auto";
		if (!opts.contains(cur)) {
			opts.add(cur);
		}

		return opts;
	}

	private static class_2561 micDeviceButtonLabel(String device) {
		String name = device != null && !device.isBlank() && !device.equalsIgnoreCase("auto") ? device : "Auto (default mic)";
		String shown = name.length() > 26 ? name.substring(0, 25) + "…" : name;
		return class_2561.method_43470("Mic: " + shown);
	}

	private static class_2561 micDeviceTooltip(List<String> opts, String current) {
		String cur = current != null && !current.isBlank() ? current.trim() : "auto";
		StringBuilder sb = new StringBuilder("Click to cycle the microphone input device.\n\nAvailable inputs:");

		for (String o : opts) {
			String label = o.equalsIgnoreCase("auto") ? "auto (system default microphone)" : o;
			sb.append("\n").append(o.equals(cur) ? "✔ " : "   ").append(label);
		}

		if (opts.size() <= 1) {
			sb.append("\n\nNo microphones detected. Plug in/enable a mic, then click to re-scan.");
		} else {
			sb.append("\n\nThe list is re-scanned on each click, so newly plugged mics appear.");
		}

		return class_2561.method_43470(sb.toString());
	}

	private static class_2561 audioStatusText(boolean detected) {
		if (detected) {
			String method = PlatformUtils.getAudioMethodDescription();
			return class_2561.method_43470("Audio: " + method + " Detected ✓").method_27694(style -> style.method_36139(6750054));
		} else {
			return class_2561.method_43470("Audio: Not Available - Video Only").method_27694(style -> style.method_36139(16764006));
		}
	}

	private static class_2561 getPlatformAudioWarningText() {
		if (PlatformUtils.isAndroid()) {
			return ANDROID_AUDIO_INFO_TEXT;
		} else if (PlatformUtils.isWindows()) {
			return WINDOWS_AUDIO_WARNING_TEXT;
		} else if (PlatformUtils.isLinux()) {
			return LINUX_AUDIO_INFO_TEXT;
		} else {
			return PlatformUtils.isMacOS() ? MACOS_AUDIO_INFO_TEXT : null;
		}
	}

	private static void saveConfigSafely(RecordableConfig config) {
		if (config != null) {
			try {
				config.save();
			} catch (Throwable var2) {
				RecordableMod.LOGGER.warn("Failed to save Record-able config from settings screen.", var2);
			}
		}
	}

	private void testAudioCapture(class_4185 button) {
		if (button != null) {
			button.field_22763 = false;
			button.method_25355(class_2561.method_43470("Testing..."));
		}

		Thread testThread = new Thread(() -> {
			try {
				RecordableConfig config = RecordableConfig.get();
				if (config == null) {
					this.setTestResult("Config unavailable.", true, button);
					return;
				}

				FfmpegStatus ffStatus = FFmpegEncoder.detectFfmpeg();
				if (!ffStatus.found()) {
					this.setTestResult("FFmpeg not found. Cannot test audio.", true, button);
					return;
				}

				AudioCapture.clearCache();
				AudioDeviceStatus audioStatus = AudioCapture.detectAudioDevice(ffStatus.executable(), config.audioDevice);
				if (!audioStatus.available()) {
					this.setTestResult("No audio device detected: " + audioStatus.message(), true, button);
					return;
				}

				boolean testResult = AudioCapture.testAudioDevice(ffStatus.executable(), audioStatus);
				if (testResult) {
					this.setTestResult("✅ Audio test passed! Device: " + audioStatus.deviceName(), false, button);
				} else {
					this.onTestAudioFailed();
					this.setTestResult("⚠ Audio device found but probe failed: " + audioStatus.deviceName() + ". Check 'How to Fix Audio'.", true, button);
				}
			} catch (Exception var6) {
				RecordableMod.LOGGER.warn("Audio test exception", var6);
				this.setTestResult("Audio test error: " + var6.getMessage(), true, button);
			}
		}, "Record-able Audio Test");
		testThread.setDaemon(true);
		testThread.start();
	}

	private void setTestResult(String message, boolean isError, class_4185 button) {
		if (this.field_22787 != null) {
			this.field_22787.execute(() -> {
				this.statusMessage = class_2561.method_43470(message);
				this.statusIsError = isError;
				if (button != null) {
					button.method_25355(class_2561.method_43471("screen.recordable.settings.test_audio"));
					button.field_22763 = true;
				}
			});
		}
	}

	private void onTestAudioFailed() {
		if (this.field_22787 != null && this.field_22787.field_1724 != null) {
			this.field_22787
				.execute(
					() -> {
						this.field_22787.field_1724.method_7353(class_2561.method_43470("⚠ Audio test failed. Try these fixes:"), false);
						this.field_22787
							.field_1724
							.method_7353(class_2561.method_43470("1) Enable Stereo Mix: Sound settings → Recording → Show Disabled Devices → Enable"), false);
						this.field_22787.field_1724.method_7353(class_2561.method_43470("2) Click Re-scan Audio, then run Test Audio again"), false);
						this.field_22787.field_1724.method_7353(class_2561.method_43470("3) If audio is still unavailable, keep recording in video-only mode"), false);
						this.field_22787.field_1724.method_7353(class_2561.method_43470("Tip: Use the Volume slider to boost quiet audio (up to 200%)."), false);
					}
				);
		}
	}

	private void saveAndClose() {
		saveConfigSafely(RecordableConfig.get());
		if (this.field_22787 != null) {
			this.field_22787.method_1507(this.parent);
		}
	}

	private String getDisplayOutputPath() {
		try {
			return RecordingManager.getInstance().getCurrentOutputDirectory().toString();
		} catch (Throwable var2) {
			return "(output path unavailable)";
		}
	}

	private void openRecordingsFolder() {
		try {
			RecordableConfig.get().save();
			Path folder = RecordingManager.getInstance().getCurrentOutputDirectory();
			Files.createDirectories(folder);
			class_156.method_668().method_673(folder.toUri());
			this.statusMessage = class_2561.method_43471("screen.recordable.settings.opened_folder");
			this.statusIsError = false;
		} catch (Throwable var3) {
			RecordableMod.LOGGER.warn("Failed to open Record-able recordings folder.", var3);
			this.statusMessage = class_2561.method_43471("screen.recordable.settings.open_folder_failed");
			this.statusIsError = true;
		}
	}

	private static final class AudioBoostSlider extends class_357 {
		private static final int MAX_BOOST_DB = 24;
		private final IntConsumer setter;

		private AudioBoostSlider(int x, int y, int width, int height, int currentDb, IntConsumer setter) {
			super(x, y, width, height, class_2561.method_43473(), normalize(currentDb));
			this.setter = setter;
			this.method_25346();
		}

		protected void method_25346() {
			int db = this.getCurrentDb();
			String label = db == 0 ? "Off" : "+" + db + " dB";
			this.method_25355(class_2561.method_43470("Audio Boost: " + label));
		}

		protected void method_25344() {
			this.setter.accept(this.getCurrentDb());
			this.method_25346();
		}

		private int getCurrentDb() {
			return Math.max(0, Math.min(24, (int)Math.round(this.field_22753 * 24.0)));
		}

		private static double normalize(int value) {
			return (double)Math.max(0, Math.min(24, value)) / 24.0;
		}
	}

	private static final class AudioDelaySlider extends class_357 {
		private static final int MAX_MS = 500;
		private final IntConsumer setter;

		private AudioDelaySlider(int x, int y, int width, int height, int currentValue, IntConsumer setter) {
			super(x, y, width, height, class_2561.method_43473(), normalize(currentValue));
			this.setter = setter;
			this.method_25346();
		}

		protected void method_25346() {
			int ms = this.getCurrentMs();
			this.method_25355(class_2561.method_43470("Custom Delay: " + ms + " ms"));
		}

		protected void method_25344() {
			this.setter.accept(this.getCurrentMs());
			this.method_25346();
		}

		private int getCurrentMs() {
			int raw = (int)Math.round(this.field_22753 * 500.0);
			int snapped = Math.round((float)raw / 5.0F) * 5;
			return Math.max(0, Math.min(500, snapped));
		}

		private static double normalize(int value) {
			int clamped = Math.max(0, Math.min(500, value));
			return (double)clamped / 500.0;
		}
	}

	private static final class AudioVolumeSlider extends class_357 {
		private static final int MAX_VOLUME = 200;
		private final IntConsumer setter;

		private AudioVolumeSlider(int x, int y, int width, int height, int currentValue, IntConsumer setter) {
			super(x, y, width, height, class_2561.method_43473(), normalize(currentValue));
			this.setter = setter;
			this.method_25346();
		}

		protected void method_25346() {
			int vol = this.getCurrentVolume();
			String label = vol == 0 ? "Muted" : vol + "%";
			this.method_25355(class_2561.method_43470("Audio Volume: " + label));
		}

		protected void method_25344() {
			this.setter.accept(this.getCurrentVolume());
			this.method_25346();
		}

		private int getCurrentVolume() {
			int raw = (int)Math.round(this.field_22753 * 200.0);
			int snapped = Math.round((float)raw / 5.0F) * 5;
			return Math.max(0, Math.min(200, snapped));
		}

		private static double normalize(int value) {
			int clamped = Math.max(0, Math.min(200, value));
			return (double)clamped / 200.0;
		}
	}

	private static final class AutoClipDurationSlider extends class_357 {
		private static final int MIN_SECONDS = 5;
		private static final int MAX_SECONDS = 300;
		private final IntConsumer setter;
		private final RecordableConfig config;
		private boolean firstInteraction = true;

		private AutoClipDurationSlider(int x, int y, int width, int height, int currentValue, IntConsumer setter, RecordableConfig config) {
			super(x, y, width, height, class_2561.method_43473(), normalize(currentValue));
			this.setter = setter;
			this.config = config;
			this.method_25346();
		}

		protected void method_25346() {
			int seconds = this.getCurrentSeconds();
			String label = seconds >= 60 ? String.format("Auto-Clip Duration: %dm %ds", seconds / 60, seconds % 60) : "Auto-Clip Duration: " + seconds + "s";
			this.method_25355(class_2561.method_43470(label));
		}

		protected void method_25344() {
			this.setter.accept(this.getCurrentSeconds());
			if (this.firstInteraction && this.config != null) {
				this.firstInteraction = false;
				if (!this.config.autoClipEnabled) {
					this.config.autoClipEnabled = true;
					this.config.autoClipOnAchievement = true;
					this.config.autoClipOnDeath = true;
					this.config.autoClipOnDimensionChange = true;
					this.config.autoClipOnBossKill = true;
					this.config.autoClipOnKill = true;
					this.config.autoClipOnPlayerKill = true;
					RecordableMod.LOGGER.info("Auto-enabled auto-clipping when adjusting auto-clip duration");
				}
			}

			this.method_25346();
		}

		private int getCurrentSeconds() {
			int raw = (int)Math.round(this.field_22753 * 295.0) + 5;
			int snapped = Math.round((float)raw / 5.0F) * 5;
			return Math.max(5, Math.min(300, snapped));
		}

		private static double normalize(int value) {
			int clamped = Math.max(5, Math.min(300, value));
			return (double)(clamped - 5) / 295.0;
		}
	}

	private static final class AutoClipFpsSlider extends class_357 {
		private final IntConsumer setter;

		private AutoClipFpsSlider(int x, int y, int width, int height, int currentValue, IntConsumer setter) {
			super(x, y, width, height, class_2561.method_43473(), normalizeFps(currentValue));
			this.setter = setter;
			this.method_25346();
		}

		protected void method_25346() {
			this.method_25355(class_2561.method_43469("screen.recordable.settings.autoclip_fps", new Object[]{this.getCurrentFps()}));
		}

		protected void method_25344() {
			this.setter.accept(this.getCurrentFps());
			this.method_25346();
		}

		private int getCurrentFps() {
			int index = (int)Math.round(this.field_22753 * (double)(RecordableConfig.AUTO_CLIP_FPS_VALUES.length - 1));
			index = Math.max(0, Math.min(RecordableConfig.AUTO_CLIP_FPS_VALUES.length - 1, index));
			return RecordableConfig.AUTO_CLIP_FPS_VALUES[index];
		}

		private static double normalizeFps(int fps) {
			for (int index = 0; index < RecordableConfig.AUTO_CLIP_FPS_VALUES.length; index++) {
				if (RecordableConfig.AUTO_CLIP_FPS_VALUES[index] == fps) {
					return (double)index / (double)(RecordableConfig.AUTO_CLIP_FPS_VALUES.length - 1);
				}
			}

			return 0.6;
		}
	}

	private static final class AutoDelaySlider extends class_357 {
		private final IntConsumer setter;

		private AutoDelaySlider(int x, int y, int width, int height, int currentValue, IntConsumer setter) {
			super(x, y, width, height, class_2561.method_43473(), normalize(currentValue));
			this.setter = setter;
			this.method_25346();
		}

		protected void method_25346() {
			this.method_25355(class_2561.method_43469("screen.recordable.settings.auto_record.delay", new Object[]{this.getCurrentDelay()}));
		}

		protected void method_25344() {
			this.setter.accept(this.getCurrentDelay());
			this.method_25346();
		}

		private int getCurrentDelay() {
			return Math.max(0, Math.min(10, (int)Math.round(this.field_22753 * 10.0)));
		}

		private static double normalize(int value) {
			int clamped = Math.max(0, Math.min(10, value));
			return (double)clamped / 10.0;
		}
	}

	private static final class FpsSlider extends class_357 {
		private final IntConsumer setter;

		private FpsSlider(int x, int y, int width, int height, int currentValue, IntConsumer setter) {
			super(x, y, width, height, class_2561.method_43473(), normalizeFps(currentValue));
			this.setter = setter;
			this.method_25346();
		}

		protected void method_25346() {
			this.method_25355(class_2561.method_43469("screen.recordable.settings.fps", new Object[]{this.getCurrentFps()}));
		}

		protected void method_25344() {
			this.setter.accept(this.getCurrentFps());
			this.method_25346();
		}

		private int getCurrentFps() {
			int index = (int)Math.round(this.field_22753 * (double)(RecordableConfig.FPS_VALUES.length - 1));
			index = Math.max(0, Math.min(RecordableConfig.FPS_VALUES.length - 1, index));
			return RecordableConfig.FPS_VALUES[index];
		}

		private static double normalizeFps(int fps) {
			for (int index = 0; index < RecordableConfig.FPS_VALUES.length; index++) {
				if (RecordableConfig.FPS_VALUES[index] == fps) {
					return (double)index / (double)(RecordableConfig.FPS_VALUES.length - 1);
				}
			}

			return 0.5;
		}
	}

	private static final class LayoutWidget {
		final class_339 widget;
		final int baseY;
		final String keywords;

		LayoutWidget(class_339 widget, int baseY, String keywords) {
			this.widget = widget;
			this.baseY = baseY;
			this.keywords = keywords;
		}
	}

	private static final class MaxFileSizeSlider extends class_357 {
		private static final int MAX_MB = 10240;
		private final IntConsumer setter;

		private MaxFileSizeSlider(int x, int y, int width, int height, int currentValue, IntConsumer setter) {
			super(x, y, width, height, class_2561.method_43473(), normalize(currentValue));
			this.setter = setter;
			this.method_25346();
		}

		protected void method_25346() {
			int value = this.getCurrentValue();
			this.method_25355(
				value <= 0
					? class_2561.method_43471("screen.recordable.settings.max_file_size.unlimited")
					: class_2561.method_43469("screen.recordable.settings.max_file_size", new Object[]{value})
			);
		}

		protected void method_25344() {
			this.setter.accept(this.getCurrentValue());
			this.method_25346();
		}

		private int getCurrentValue() {
			int rounded = (int)Math.round(this.field_22753 * 102.4) * 100;
			return Math.max(0, Math.min(10240, rounded));
		}

		private static double normalize(int value) {
			int clamped = Math.max(0, Math.min(10240, value));
			return (double)clamped / 10240.0;
		}
	}

	private static final class MixVolumeSlider extends class_357 {
		private static final int MAX_VOLUME = 200;
		private final String label;
		private final IntConsumer setter;

		private MixVolumeSlider(int x, int y, int width, int height, String label, int currentValue, IntConsumer setter) {
			super(x, y, width, height, class_2561.method_43473(), normalize(currentValue));
			this.label = label;
			this.setter = setter;
			this.method_25346();
		}

		protected void method_25346() {
			int vol = this.getCurrentVolume();
			this.method_25355(class_2561.method_43470(this.label + ": " + (vol == 0 ? "Muted" : vol + "%")));
		}

		protected void method_25344() {
			this.setter.accept(this.getCurrentVolume());
			this.method_25346();
		}

		private int getCurrentVolume() {
			int raw = (int)Math.round(this.field_22753 * 200.0);
			int snapped = Math.round((float)raw / 5.0F) * 5;
			return Math.max(0, Math.min(200, snapped));
		}

		private static double normalize(int value) {
			int clamped = Math.max(0, Math.min(200, value));
			return (double)clamped / 200.0;
		}
	}

	private static final class OverlayScaleSlider extends class_357 {
		private static final int MIN_SCALE = 50;
		private static final int MAX_SCALE = 200;
		private final IntConsumer setter;

		private OverlayScaleSlider(int x, int y, int width, int height, int currentValue, IntConsumer setter) {
			super(x, y, width, height, class_2561.method_43473(), normalize(currentValue));
			this.setter = setter;
			this.method_25346();
		}

		protected void method_25346() {
			int pct = this.getCurrentScale();
			String label;
			if (pct == 100) {
				label = "100% (Default)";
			} else if (pct < 100) {
				label = pct + "% (Smaller)";
			} else {
				label = pct + "% (Larger)";
			}

			this.method_25355(class_2561.method_43470("Overlay Scale: " + label));
		}

		protected void method_25344() {
			this.setter.accept(this.getCurrentScale());
			this.method_25346();
		}

		private int getCurrentScale() {
			int raw = (int)Math.round(this.field_22753 * 150.0) + 50;
			int snapped = Math.round((float)raw / 10.0F) * 10;
			return Math.max(50, Math.min(200, snapped));
		}

		private static double normalize(int value) {
			int clamped = Math.max(50, Math.min(200, value));
			return (double)(clamped - 50) / 150.0;
		}
	}

	private static final class PerfMinFpsSlider extends class_357 {
		private static final int MIN_FPS = 10;
		private static final int MAX_FPS = 240;
		private final IntConsumer setter;

		private PerfMinFpsSlider(int x, int y, int width, int height, int currentValue, IntConsumer setter) {
			super(x, y, width, height, class_2561.method_43473(), normalize(currentValue));
			this.setter = setter;
			this.method_25346();
		}

		protected void method_25346() {
			this.method_25355(class_2561.method_43470("Min FPS Target: " + this.getCurrentFps()));
		}

		protected void method_25344() {
			this.setter.accept(this.getCurrentFps());
			this.method_25346();
		}

		private int getCurrentFps() {
			int raw = (int)Math.round(this.field_22753 * 230.0) + 10;
			int snapped = Math.round((float)raw / 5.0F) * 5;
			return Math.max(10, Math.min(240, snapped));
		}

		private static double normalize(int value) {
			int clamped = Math.max(10, Math.min(240, value));
			return (double)(clamped - 10) / 230.0;
		}
	}

	private static final class ReplayBufferSlider extends class_357 {
		private static final int MIN_SECONDS = 10;
		private static final int MAX_SECONDS = 300;
		private final IntConsumer setter;

		private ReplayBufferSlider(int x, int y, int width, int height, int currentValue, IntConsumer setter) {
			super(x, y, width, height, class_2561.method_43473(), normalize(currentValue));
			this.setter = setter;
			this.method_25346();
		}

		protected void method_25346() {
			int seconds = this.getCurrentSeconds();
			String label = seconds >= 60 ? String.format("Replay Buffer: %dm %ds", seconds / 60, seconds % 60) : "Replay Buffer: " + seconds + "s";
			this.method_25355(class_2561.method_43470(label));
		}

		protected void method_25344() {
			this.setter.accept(this.getCurrentSeconds());
			this.method_25346();
		}

		private int getCurrentSeconds() {
			int raw = (int)Math.round(this.field_22753 * 290.0) + 10;
			int snapped = Math.round((float)raw / 5.0F) * 5;
			return Math.max(10, Math.min(300, snapped));
		}

		private static double normalize(int value) {
			int clamped = Math.max(10, Math.min(300, value));
			return (double)(clamped - 10) / 290.0;
		}
	}
}
