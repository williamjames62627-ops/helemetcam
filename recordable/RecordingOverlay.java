package dev.recordable;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.recordable.CensorRegion.Style;
import dev.recordable.RecordableConfig.OverlayStyleHud;
import dev.recordable.RecordingManager.QueueHealth;
import dev.recordable.RecordingManager.State;
import dev.recordable.WatermarkSlot.Kind;
import dev.recordable.compat.RenderHelper;
import dev.recordable.filter.FilterType;
import dev.recordable.theme.ThemeColors;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AfterInit;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AfterRender;
import net.minecraft.class_1011;
import net.minecraft.class_1043;
import net.minecraft.class_2561;
import net.minecraft.class_2960;
import net.minecraft.class_310;
import net.minecraft.class_327;
import net.minecraft.class_332;
import net.minecraft.class_4587;
import org.joml.Quaternionf;

public final class RecordingOverlay {
	private static boolean registered;
	private static int blinkTick;
	private static int tapeCounter;
	private static int lastTapeSecond = -1;
	private static float simulatedAudioLevel;
	private static final Random AUDIO_RNG = new Random();
	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM dd yyyy", Locale.ENGLISH);
	private static final DateTimeFormatter TIME_FORMAT_12H = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);
	private static long watermarkAnchorMs = 0L;
	private static final Map<String, RecordingOverlay.CachedWatermarkTexture> WATERMARK_TEXTURES = new HashMap();

	private RecordingOverlay() {
	}

	public static void register() {
		if (!registered) {
			registered = true;
			HudRenderCallback.EVENT.register(RecordingOverlay::render);
			ScreenEvents.AFTER_INIT
				.register(
					(AfterInit)(client, screen, scaledWidth, scaledHeight) -> ScreenEvents.afterRender(screen)
							.register((AfterRender)(scr, ctx, mouseX, mouseY, tickDelta) -> renderCensorOnScreen(ctx, client))
				);
		}
	}

	private static void renderCensorOnScreen(class_332 context, class_310 client) {
		RecordableConfig config = RecordableConfig.get();
		if (config != null && config.streamerModeEnabled) {
			if (client != null && client.field_1687 != null) {
				if (!config.bakeInOverlay) {
					if (!config.censorOverlayHidden) {
						if (client.field_1772 != null) {
							drawCensorRegions(context, client, config);
						}
					}
				}
			}
		}
	}

	private static void render(class_332 context, float tickDelta) {
		RecordableConfig config = RecordableConfig.get();
		class_310 client = class_310.method_1551();
		if (client != null && client.field_1772 != null) {
			RecordingManager manager = RecordingManager.getInstance();
			renderToastNotification(context, client, manager);
			renderWatermarks(context, client, config, manager);
			renderMicIndicator(context, client);
			renderCensorPreview(context, client, config, manager);
			if (config.showOverlay && manager.isActiveOrStopping()) {
				renderFilterPreviews(context, client, config);
				blinkTick++;
				updateTapeCounter(manager);
				updateAudioLevel();
				OverlayStyleHud style = config.overlayStyleHud != null ? config.overlayStyleHud : OverlayStyleHud.CLASSIC;
				switch (style) {
					case CLASSIC:
						if (config.hudClassicVisible) {
							renderClassicOverlay(context, client, config, manager);
						}
						break;
					case VHS:
						renderVhsLayered(context, client, config, manager, style);
						break;
					case SYNTHWAVE:
						if (config.hudSynthVisible) {
							renderSynthwaveOverlay(context, client, config, manager);
						}
					case NONE:
				}
			}
		}
	}

	private static void renderCensorPreview(class_332 context, class_310 client, RecordableConfig config, RecordingManager manager) {
		if (config != null && config.streamerModeEnabled) {
			if (client != null && client.field_1687 != null) {
				if (manager != null && manager.isActiveOrStopping()) {
					boolean var5 = true;
				} else {
					boolean var10000 = false;
				}

				if (config.bakeInOverlay) {
					if (!config.streamerShowCensorPreview) {
						return;
					}
				} else if (config.censorOverlayHidden) {
					return;
				}

				drawCensorRegions(context, client, config);
			}
		}
	}

	private static void drawCensorRegions(class_332 context, class_310 client, RecordableConfig config) {
		List<CensorRegion> regions = config.censorRegions;
		if (regions != null && !regions.isEmpty()) {
			int w = client.method_22683().method_4486();
			int h = client.method_22683().method_4502();
			class_327 tr = client.field_1772;

			for (CensorRegion r : regions) {
				if (r != null && r.enabled) {
					int x0 = (int)(r.x * (double)w);
					int y0 = (int)(r.y * (double)h);
					int x1 = (int)((r.x + r.width) * (double)w);
					int y1 = (int)((r.y + r.height) * (double)h);
					int rgb = r.color & 16777215;
					if (r.style == Style.GRADIENT) {
						context.method_25296(x0, y0, x1, y1, 0xFF000000 | rgb, 0xFF000000 | r.colorEnd & 16777215);
					} else {
						context.method_25294(x0, y0, x1, y1, 0xFF000000 | rgb);
					}

					String tag = r.showLabel && r.label != null && !r.label.isBlank() ? r.label : r.style.name();
					RenderHelper.drawText(context, tr, class_2561.method_43470("● " + tag), x0 + 2, y0 + 2, -1);
				}
			}
		}
	}

	private static void renderFilterPreviews(class_332 context, class_310 client, RecordableConfig config) {
		if (config.showFiltersLive) {
			int w = client.method_22683().method_4486();
			int h = client.method_22683().method_4502();

			for (String layerId : parseLayerOrder(config.hudLayerOrder)) {
				if (RecordableConfig.isFilterLayer(layerId) && config.isElementVisible(layerId)) {
					FilterType type = RecordableConfig.filterLayerToType(layerId);
					FilterPreviewRenderer.render(context, w, h, type, config.getFilterIntensity(layerId));
				}
			}
		}
	}

	private static void renderMicIndicator(class_332 context, class_310 client) {
		if (MicrophoneState.isMicCapturing()) {
			RecordableConfig config = RecordableConfig.get();
			if (config.hudMicVisible) {
				class_327 tr = client.field_1772;
				boolean live = MicrophoneState.isMicActiveForDisplay();
				boolean ptt = MicrophoneState.isPushToTalkMode();
				String label = live ? "\ud83c\udfa4 MIC" : "\ud83c\udfa4 MIC (PTT)";
				int textColor = live ? -1 : -7829368;
				int dotColor = live ? -53200 : -11184811;
				int textW = tr.method_1727(label);
				int panelW = textW + 16;
				int sw = client.method_22683().method_4486();
				int x = config.hudMicX < 0 ? (sw - panelW) / 2 : config.hudMicX;
				int y = Math.max(config.hudMicY, safeTopInset(client.method_22683().method_4502()));
				int op = config.hudMicOpacity;
				context.method_25294(x - 2, y - 2, x + panelW, y + 11, RecordableConfig.applyOpacity(-1728053248, op));
				context.method_25294(x + 2, y + 2, x + 8, y + 8, RecordableConfig.applyOpacity(dotColor, op));
				RenderHelper.drawText(context, tr, class_2561.method_43470(label), x + 12, y + 1, RecordableConfig.applyOpacity(textColor, op));
				if (ptt && live) {
					context.method_25294(x - 2, y + 10, x + panelW, y + 11, RecordableConfig.applyOpacity(-53200, op));
				}
			}
		}
	}

	private static int safeCornerInset(int scaledW, int scaledH, int desktopDefault) {
		return !PlatformUtils.isAndroid() ? desktopDefault : Math.max(desktopDefault, (int)((float)Math.min(scaledW, scaledH) * 0.05F));
	}

	private static int safeTopInset(int scaledH) {
		return PlatformUtils.isAndroid() ? (int)((float)scaledH * 0.06F) : 0;
	}

	private static int safeSideInset(int scaledW) {
		return PlatformUtils.isAndroid() ? (int)((float)scaledW * 0.04F) : 0;
	}

	private static void renderClassicOverlay(class_332 context, class_310 client, RecordableConfig config, RecordingManager manager) {
		ThemeColors skin = config.activeOverlaySkinOrNull();
		int accentRgb = skin != null ? skin.accent & 16777215 : config.getOverlayColorRgb();
		int accentArgb = 0xFF000000 | accentRgb;
		int accentSoftArgb = -1442840576 | accentRgb;
		class_327 tr = client.field_1772;
		String effectiveTime = RecordingManager.formatDuration(manager.getEffectiveRecordingMillis());
		boolean isPaused = manager.getState() == State.PAUSED;
		boolean showBlink = isPaused && blinkTick / 15 % 2 == 0;
		String firstLine;
		if (manager.getState() == State.STOPPING) {
			firstLine = "REC stopping...";
		} else if (isPaused) {
			firstLine = showBlink ? "⏸ PAUSED " + effectiveTime : "  PAUSED " + effectiveTime;
		} else {
			firstLine = "● REC " + effectiveTime;
		}

		long fileSizeBytes = manager.getCurrentFileSizeBytes();
		String fileSizeStr = fileSizeBytes > 0L ? RecordingManager.formatBytes(fileSizeBytes) : "starting...";
		String secondLine = manager.getRecordingFps() + " FPS  drop " + manager.getDroppedFrames();
		QueueHealth queueHealth = manager.getQueueHealth();

		String queueState = switch (queueHealth) {
			case CRITICAL -> "DROPPING";
			case SLOW -> "SLOW";
			default -> "OK";
		};
		String thirdLine = manager.getRecordingWidth()
			+ "x"
			+ manager.getRecordingHeight()
			+ "  Queue: "
			+ manager.getQueueSize()
			+ "/"
			+ manager.getQueueCapacity()
			+ " ("
			+ queueState
			+ ")";

		int queueColor = switch (queueHealth) {
			case CRITICAL -> -36752;
			case SLOW -> -11930;
			default -> -6561137;
		};
		int fileSizeColor = fileSizeBytes > 0L ? -1 : -5592406;
		int pausedColor = isPaused ? -11930 : -1;
		boolean showTimer = config.showRecordingTimer;
		float scale = Math.max(0.5F, Math.min(2.0F, (float)config.overlayScale / 100.0F));
		context.method_51448().method_22903();
		context.method_51448().method_22905(scale, scale, 1.0F);
		int scaledW = (int)((float)client.method_22683().method_4486() / scale);
		int scaledH = (int)((float)client.method_22683().method_4502() / scale);
		int margin = safeCornerInset(scaledW, scaledH, 10);
		int lineCount = 3;
		if (ReplayBuffer.getInstance().isActive()) {
			lineCount++;
		}

		int maxTextWidth = Math.max(
			tr.method_1727(firstLine), Math.max(tr.method_1727(secondLine), Math.max(tr.method_1727("Size: " + fileSizeStr), tr.method_1727(thirdLine)))
		);
		int panelWidth = maxTextWidth + 22;
		int panelHeight = 12 + lineCount * 11;
		int[] cpos = config.classicPanelPos(scaledW, scaledH, panelWidth, panelHeight, margin);
		int x = cpos[0];
		int y = cpos[1];
		int panelBg = skin != null ? skin.panelBackground : -1728053248;
		context.method_25294(x - 3, y - 3, x + panelWidth, y + panelHeight, panelBg);
		context.method_25294(x - 3, y - 3, x + panelWidth, y - 2, accentSoftArgb);
		if (!isPaused) {
			context.method_25294(x, y + 3, x + 8, y + 11, accentArgb);
		}

		RenderHelper.drawText(context, tr, class_2561.method_43470(firstLine), x + (isPaused ? 0 : 13), y, pausedColor);
		int lineY = y + 12;
		RenderHelper.drawText(context, tr, class_2561.method_43470(secondLine), x, lineY, skin != null ? skin.textSecondary : -2039584);
		lineY += 11;
		RenderHelper.drawText(context, tr, class_2561.method_43470("Size: " + fileSizeStr), x, lineY, fileSizeColor);
		lineY += 11;
		RenderHelper.drawText(context, tr, class_2561.method_43470(thirdLine), x, lineY, queueColor);
		lineY += 11;
		if (ReplayBuffer.getInstance().isActive()) {
			ReplayBuffer rb = ReplayBuffer.getInstance();
			String rbLine = "⟳ Replay: " + rb.getBufferedSeconds() + "s buffered (" + rb.getBufferedFrameCount() + " frames)";
			RenderHelper.drawText(context, tr, class_2561.method_43470(rbLine), x, lineY, -7811841);
		}

		context.method_51448().method_22909();
	}

	private static void renderVhsLayered(class_332 context, class_310 client, RecordableConfig config, RecordingManager manager, OverlayStyleHud style) {
		float scale = Math.max(0.5F, Math.min(2.0F, (float)config.overlayScale / 100.0F));
		context.method_51448().method_22903();
		context.method_51448().method_22905(scale, scale, 1.0F);
		int w = (int)((float)client.method_22683().method_4486() / scale);
		int h = (int)((float)client.method_22683().method_4502() / scale);
		class_327 tr = client.field_1772;
		ThemeColors skin = config.activeOverlaySkinOrNull();

		for (String layerId : parseLayerOrder(config.hudLayerOrder)) {
			if (config.isElementVisible(layerId)) {
				switch (layerId) {
					case "Corners":
						if (config.vhsShowBrackets) {
							int c = skin != null ? skin.accent : RecordableConfig.parseArgbColor(config.vhsBracketColor, -922746881);
							c = RecordableConfig.applyOpacity(c, config.hudCornersOpacity);
							drawCornerBrackets(
								context, config.hudCornersX, config.hudCornersY, config.hudCornersX + config.hudCornersWidth, config.hudCornersY + config.hudCornersHeight, 20, 2, c
							);
						}
						break;
					case "PLAY/REC":
						int opacity = config.hudPlayRecOpacity;
						int leftInset = Math.max(config.hudPlayRecX, safeSideInset(w));
						int topInset = Math.max(config.hudPlayRecY, safeTopInset(h));
						boolean recDotVisible = blinkTick / 15 % 2 == 0;
						if (config.vhsShowPlay) {
							int pc = skin != null ? skin.textPrimary : RecordableConfig.parseArgbColor(config.vhsPlayColor, -1);
							RenderHelper.drawText(context, tr, class_2561.method_43470("PLAY ▶"), leftInset, topInset, RecordableConfig.applyOpacity(pc, opacity));
						}

						int recY = topInset + (config.vhsShowPlay ? 12 : 0);
						if (recDotVisible) {
							int dc = skin != null ? skin.accent : RecordableConfig.parseArgbColor(config.vhsRecDotColor, -3400162);
							dc = RecordableConfig.applyOpacity(dc, opacity);
							context.method_25294(leftInset, recY + 2, leftInset + 7, recY + 9, dc);
						}

						int rc = skin != null ? skin.textPrimary : RecordableConfig.parseArgbColor(config.vhsRecTextColor, -1);
						RenderHelper.drawText(context, tr, class_2561.method_43470("REC"), leftInset + 10, recY, RecordableConfig.applyOpacity(rc, opacity));
						break;
					case "Timestamp":
						int tc = skin != null ? skin.textPrimary : RecordableConfig.parseArgbColor(config.vhsTimestampColor, -1);
						tc = RecordableConfig.applyOpacity(tc, config.hudTimestampOpacity);
						String timer = formatTimer(manager.getEffectiveRecordingMillis());
						int timerW = tr.method_1727(timer);
						int tsRight = Math.max(config.hudTimestampOffsetX, safeSideInset(w));
						int tsTop = Math.max(config.hudTimestampY, safeTopInset(h));
						RenderHelper.drawText(context, tr, class_2561.method_43470(timer), w - tsRight - timerW, tsTop, tc);
						break;
					case "SP":
						if (config.vhsShowSp) {
							int sc = skin != null ? skin.textPrimary : RecordableConfig.parseArgbColor(config.vhsSpColor, -1);
							sc = RecordableConfig.applyOpacity(sc, config.hudSpOpacity);
							RenderHelper.drawText(context, tr, class_2561.method_43470("SP"), config.hudSpX, h - config.hudSpOffsetY, sc);
						}
						break;
					case "Details":
						renderVhsDetails(context, tr, config, manager, w, h);
						break;
					case "Perf":
						if (config.showPerformanceStats) {
							renderPerformanceStatsInner(context, tr, config, manager, w, h);
						}
				}
			}
		}

		renderVhsPerfLine(context, tr, config, manager, w, h);
		context.method_51448().method_22909();
	}

	private static List<String> parseLayerOrder(String order) {
		List<String> all = RecordableConfig.allLayerIds();
		Set<String> validSet = new HashSet(all);
		List<String> result = new ArrayList();
		if (order != null && !order.isBlank()) {
			for (String part : order.split(",")) {
				String id = part.trim();
				if (!id.isEmpty() && validSet.contains(id) && !result.contains(id)) {
					result.add(id);
				}
			}
		}

		for (String id : all) {
			if (!result.contains(id)) {
				result.add(id);
			}
		}

		return result;
	}

	private static void renderVhsPerfLine(class_332 context, class_327 tr, RecordableConfig config, RecordingManager manager, int w, int h) {
		QueueHealth queueHealth = manager.getQueueHealth();

		String queueState = switch (queueHealth) {
			case CRITICAL -> "DROPPING";
			case SLOW -> "SLOW";
			default -> "OK";
		};

		int queueColor = switch (queueHealth) {
			case CRITICAL -> -36752;
			case SLOW -> -11930;
			default -> -6561137;
		};
		String fpsPart = manager.getRecordingFps() + " FPS  drop " + manager.getDroppedFrames() + "  ";
		String queuePart = "Q " + manager.getQueueSize() + "/" + manager.getQueueCapacity() + " " + queueState;
		int fpsW = tr.method_1727(fpsPart);
		int totalW = fpsW + tr.method_1727(queuePart);
		int x = 4;
		int y = h - 11;
		context.method_25294(x - 2, y - 2, x + totalW + 2, y + 9, -2013265920);
		RenderHelper.drawText(context, tr, class_2561.method_43470(fpsPart), x, y, -2039584);
		RenderHelper.drawText(context, tr, class_2561.method_43470(queuePart), x + fpsW, y, queueColor);
	}

	private static void renderVhsDetails(class_332 context, class_327 tr, RecordableConfig config, RecordingManager manager, int w, int h) {
		int opacity = config.hudDetailsOpacity;
		int bottomRightX = w - config.hudDetailsOffsetX;
		int bottomY = h - config.hudDetailsOffsetY;
		int dateColor = RecordableConfig.parseArgbColor(config.vhsDateColor, -1);
		dateColor = RecordableConfig.applyOpacity(dateColor, opacity);
		int var17;
		if (config.vhsShowDate) {
			LocalDateTime now = LocalDateTime.now();
			String dateLine = DATE_FORMAT.format(now);
			String timeLine = TIME_FORMAT_12H.format(now).toUpperCase(Locale.ROOT);
			int dateW = tr.method_1727(dateLine);
			int timeW = tr.method_1727(timeLine);
			var17 = bottomY - 22;
			RenderHelper.drawText(context, tr, class_2561.method_43470(timeLine), bottomRightX - timeW, var17, dateColor);
			var17 += 11;
			RenderHelper.drawText(context, tr, class_2561.method_43470(dateLine), bottomRightX - dateW, var17, dateColor);
			var17 = bottomY - 22 - 4;
		} else {
			var17 = bottomY - 4;
		}

		if (config.vhsShowTapeCounter) {
			String tapeStr = String.format("TC %04d", tapeCounter);
			int tcW = tr.method_1727(tapeStr);
			var17 -= 11;
			RenderHelper.drawText(context, tr, class_2561.method_43470(tapeStr), bottomRightX - tcW, var17, RecordableConfig.applyOpacity(-3355444, opacity));
		}

		if (config.vhsShowAudioMeter) {
			var17 -= 12;
			drawAudioMeter(context, bottomRightX - 60, var17, 55, 8);
		}

		if (config.vhsShowBattery) {
			var17 -= 13;
			drawBatteryIndicator(context, tr, bottomRightX - 50, var17, manager);
		}
	}

	private static void renderSynthwaveOverlay(class_332 context, class_310 client, RecordableConfig config, RecordingManager manager) {
		float scale = Math.max(0.5F, Math.min(2.0F, (float)config.overlayScale / 100.0F));
		context.method_51448().method_22903();
		context.method_51448().method_22905(scale, scale, 1.0F);
		int w = (int)((float)client.method_22683().method_4486() / scale);
		int h = (int)((float)client.method_22683().method_4502() / scale);
		class_327 tr = client.field_1772;
		String timer = formatTimer(manager.getEffectiveRecordingMillis());
		boolean recDotVisible = blinkTick / 15 % 2 == 0;
		ThemeColors skin = config.activeOverlaySkinOrNull();
		int MAGENTA = skin != null ? skin.accent : -53867;
		int CYAN = skin != null ? skin.accentHover : -16718337;
		int panelBg = skin != null ? skin.panelBackground : -434500818;
		int textColor = skin != null ? skin.textPrimary : CYAN;
		String line = "REC " + timer;
		int pw = tr.method_1727(line) + 22;
		int ph = 16;
		int[] spos = config.synthPanelPos(w, h, pw, ph, safeSideInset(w), safeTopInset(h));
		int x = spos[0];
		int y = spos[1];
		context.method_25294(x, y, x + pw, y + ph, panelBg);
		context.method_25294(x, y, x + pw, y + 1, MAGENTA);
		context.method_25294(x, y + ph - 1, x + pw, y + ph, CYAN);
		context.method_25294(x, y, x + 1, y + ph, MAGENTA);
		context.method_25294(x + pw - 1, y, x + pw, y + ph, CYAN);
		if (recDotVisible) {
			context.method_25294(x + 6, y + 5, x + 12, y + 11, MAGENTA);
		}

		RenderHelper.drawText(context, tr, class_2561.method_43470(line), x + 16, y + 4, textColor);
		context.method_51448().method_22909();
	}

	private static void renderPerformanceStatsInner(class_332 context, class_327 tr, RecordableConfig config, RecordingManager manager, int w, int h) {
		int opacity = config.hudPerfOpacity;
		PerformanceMetrics metrics = PerformanceMetrics.getInstance();
		metrics.updateQueueStats(manager.getQueueSize(), manager.getQueueCapacity());
		metrics.updateMemory(manager.getUsedMemoryMiB());
		metrics.updateFps(Math.round(manager.getCaptureFpsEstimate()), Math.round(manager.getEncoderFpsEstimate()));
		metrics.updateFileSize(manager.getCurrentFileSizeBytes());
		String[] lines = new String[]{
			"Cap " + Math.round(manager.getCaptureFpsEstimate()) + " | Enc " + Math.round(manager.getEncoderFpsEstimate()) + " FPS",
			"Mem " + manager.getUsedMemoryMiB() + " MiB | Drop " + manager.getAdaptiveDroppedFrames(),
			"Queue: " + manager.getQueueSize() + "/" + manager.getQueueCapacity() + " | " + String.format("%.0f%%", metrics.getBufferHealthPercent())
		};
		int maxW = 0;

		for (String line : lines) {
			maxW = Math.max(maxW, tr.method_1727(line));
		}

		int panelW = maxW + 10;
		int panelH = 8 + lines.length * 10;
		int px = w - panelW - config.hudPerfOffsetX;
		int py = h - panelH - config.hudPerfOffsetY;
		context.method_25294(px - 2, py - 2, px + panelW, py + panelH, RecordableConfig.applyOpacity(-2013265920, opacity));
		int ly = py;
		int[] colors = new int[]{-3158065, -5197648, -6561137};

		for (int i = 0; i < lines.length; i++) {
			RenderHelper.drawText(context, tr, class_2561.method_43470(lines[i]), px + 2, ly, RecordableConfig.applyOpacity(colors[i % colors.length], opacity));
			ly += 10;
		}
	}

	private static void renderPerformanceStats(class_332 context, class_310 client, RecordableConfig config, RecordingManager manager) {
		float scale = Math.max(0.5F, Math.min(2.0F, (float)config.overlayScale / 100.0F));
		context.method_51448().method_22903();
		context.method_51448().method_22905(scale, scale, 1.0F);
		int w = (int)((float)client.method_22683().method_4486() / scale);
		int h = (int)((float)client.method_22683().method_4502() / scale);
		class_327 tr = client.field_1772;
		renderPerformanceStatsInner(context, tr, config, manager, w, h);
		context.method_51448().method_22909();
	}

	private static void drawCornerBrackets(class_332 ctx, int x1, int y1, int x2, int y2, int len, int thick, int color) {
		ctx.method_25294(x1, y1, x1 + len, y1 + thick, color);
		ctx.method_25294(x1, y1, x1 + thick, y1 + len, color);
		ctx.method_25294(x2 - len, y1, x2, y1 + thick, color);
		ctx.method_25294(x2 - thick, y1, x2, y1 + len, color);
		ctx.method_25294(x1, y2 - thick, x1 + len, y2, color);
		ctx.method_25294(x1, y2 - len, x1 + thick, y2, color);
		ctx.method_25294(x2 - len, y2 - thick, x2, y2, color);
		ctx.method_25294(x2 - thick, y2 - len, x2, y2, color);
	}

	private static void drawAudioMeter(class_332 ctx, int x, int y, int barWidth, int totalHeight) {
		int barH = Math.max(1, totalHeight / 2 - 1);
		ctx.method_25294(x, y, x + barWidth, y + barH, -13421773);
		ctx.method_25294(x, y + barH + 1, x + barWidth, y + barH + 1 + barH, -13421773);
		float level = simulatedAudioLevel;
		int fillW = Math.round(level * (float)barWidth);
		float rLevel = Math.min(1.0F, level + (AUDIO_RNG.nextFloat() * 0.15F - 0.075F));
		int fillWR = Math.round(Math.max(0.0F, rLevel) * (float)barWidth);
		int barColor = level < 0.6F ? -12268476 : (level < 0.85F ? -3355580 : -3390396);
		int barColorR = rLevel < 0.6F ? -12268476 : (rLevel < 0.85F ? -3355580 : -3390396);
		ctx.method_25294(x, y, x + fillW, y + barH, barColor);
		ctx.method_25294(x, y + barH + 1, x + fillWR, y + barH + 1 + barH, barColorR);
	}

	private static void drawBatteryIndicator(class_332 ctx, class_327 tr, int x, int y, RecordingManager manager) {
		int bw = 24;
		int bh = 10;
		ctx.method_25294(x, y, x + bw, y + bh, -5592406);
		ctx.method_25294(x + 1, y + 1, x + bw - 1, y + bh - 1, -14540254);
		ctx.method_25294(x + bw, y + 2, x + bw + 2, y + bh - 2, -5592406);
		long elapsedMs = manager.getEffectiveRecordingMillis();
		float pct = Math.max(0.05F, 1.0F - (float)elapsedMs / 7200000.0F);
		int fillW = Math.round(pct * (float)(bw - 4));
		int fillColor = pct > 0.3F ? -12268476 : (pct > 0.1F ? -3355580 : -3390396);
		ctx.method_25294(x + 2, y + 2, x + 2 + fillW, y + bh - 2, fillColor);
		String pctStr = Math.round(pct * 100.0F) + "%";
		RenderHelper.drawText(ctx, tr, class_2561.method_43470(pctStr), x + bw + 5, y + 1, -3355444);
	}

	private static String formatTimer(long elapsedMs) {
		long totalSeconds = Math.max(0L, elapsedMs / 1000L);
		long hours = totalSeconds / 3600L;
		long mins = totalSeconds % 3600L / 60L;
		long secs = totalSeconds % 60L;
		return String.format("%02d:%02d:%02d", hours, mins, secs);
	}

	private static void updateTapeCounter(RecordingManager manager) {
		int currentSecond = (int)(manager.getEffectiveRecordingMillis() / 1000L);
		if (currentSecond != lastTapeSecond) {
			lastTapeSecond = currentSecond;
			tapeCounter = currentSecond;
		}
	}

	private static void updateAudioLevel() {
		float target = 0.3F + AUDIO_RNG.nextFloat() * 0.5F;
		simulatedAudioLevel = simulatedAudioLevel + (target - simulatedAudioLevel) * 0.15F;
		simulatedAudioLevel = Math.max(0.05F, Math.min(1.0F, simulatedAudioLevel));
	}

	private static void renderToastNotification(class_332 context, class_310 client, RecordingManager manager) {
		String toastMsg = manager.getPendingToastMessage();
		if (toastMsg != null) {
			class_327 tr = client.field_1772;
			int screenWidth = client.method_22683().method_4486();
			int screenHeight = client.method_22683().method_4502();
			int toastWidth = tr.method_1727(toastMsg) + 30;
			int toastHeight = 24;
			int toastX = (screenWidth - toastWidth) / 2;
			int toastY = screenHeight - toastHeight - 40;
			context.method_25294(toastX, toastY, toastX + toastWidth, toastY + toastHeight, -585491942);
			context.method_25294(toastX, toastY, toastX + toastWidth, toastY + 1, -12277180);
			RenderHelper.drawText(context, tr, class_2561.method_43470(toastMsg), toastX + 8, toastY + 7, -1);
		}
	}

	static void renderWatermarks(class_332 context, class_310 client, RecordableConfig config, RecordingManager manager) {
		if (config != null && config.watermarksEnabled) {
			List<WatermarkSlot> slots = config.watermarkSlots;
			if (slots != null && !slots.isEmpty()) {
				boolean recording = manager != null && manager.isActiveOrStopping();
				if (recording || config.showWatermarksLive) {
					class_327 tr = client.field_1772;
					if (tr != null) {
						long nowMs = System.currentTimeMillis();
						double tMs;
						if (recording) {
							watermarkAnchorMs = 0L;
							tMs = (double)manager.getEffectiveRecordingMillis();
						} else {
							if (watermarkAnchorMs == 0L) {
								watermarkAnchorMs = nowMs;
							}

							tMs = (double)(nowMs - watermarkAnchorMs);
						}

						double recSec = tMs / 1000.0;
						int screenW = client.method_22683().method_4486();
						int screenH = client.method_22683().method_4502();
						String username = client.field_1724 != null ? client.field_1724.method_5477().getString() : "Player";

						for (WatermarkSlot slot : slots) {
							if (slot != null && slot.enabled && (!recording || slot.visibleAt(recSec))) {
								try {
									if (slot.kind == Kind.IMAGE) {
										renderImageWatermark(context, client, slot, screenW, screenH, tMs);
									} else {
										renderTextWatermark(context, tr, slot, screenW, screenH, username, tMs);
									}
								} catch (Throwable var19) {
									RecordableMod.LOGGER.debug("[Record-able] watermark render failed: {}", var19.toString());
								}
							}
						}
					}
				}
			}
		}
	}

	private static RecordingOverlay.CachedWatermarkTexture loadWatermarkTexture(class_310 client, String filename) {
		if (filename == null || filename.isBlank()) {
			return null;
		} else if (WATERMARK_TEXTURES.containsKey(filename)) {
			return (RecordingOverlay.CachedWatermarkTexture)WATERMARK_TEXTURES.get(filename);
		} else {
			RecordingOverlay.CachedWatermarkTexture result = null;

			try {
				Path path = WatermarkImageStore.resolve(filename);
				if (path != null && Files.isRegularFile(path, new LinkOption[0])) {
					byte[] pngBytes = WatermarkImageStore.readAsPngBytes(path);
					if (pngBytes == null) {
						WATERMARK_TEXTURES.put(filename, null);
						return null;
					}

					InputStream in = new ByteArrayInputStream(pngBytes);

					class_1011 image;
					try {
						image = class_1011.method_4309(in);
					} catch (Throwable var10) {
						try {
							in.close();
						} catch (Throwable var9) {
							var10.addSuppressed(var9);
						}

						throw var10;
					}

					in.close();
					class_1043 var12 = new class_1043(image);
					String safe = "watermark/" + Integer.toHexString(filename.hashCode() & 2147483647);
					class_2960 id = VersionHelper.id("recordable", safe);
					client.method_1531().method_4616(id, var12);
					result = new RecordingOverlay.CachedWatermarkTexture(id, image.method_4307(), image.method_4323());
				}
			} catch (Throwable var11) {
				RecordableMod.LOGGER.warn("[Record-able] failed to load watermark image '{}': {}", filename, var11.toString());
			}

			WATERMARK_TEXTURES.put(filename, result);
			return result;
		}
	}

	private static void renderImageWatermark(class_332 context, class_310 client, WatermarkSlot slot, int screenW, int screenH, double tMs) {
		RecordingOverlay.CachedWatermarkTexture tex = loadWatermarkTexture(client, slot.imagePath);
		if (tex != null && tex.width > 0 && tex.height > 0) {
			float animAlpha = 1.0F;
			float slideX = 0.0F;
			double durMs = (double)Math.max(1, slot.animationDurationMs);
			switch (slot.animation) {
				case FADE:
					animAlpha = (float)Math.min(1.0, tMs / durMs);
					break;
				case PULSE:
					double phase = tMs % durMs / durMs;
					animAlpha = 0.45F + 0.55F * (float)((1.0 + Math.sin(phase * 2.0 * Math.PI)) / 2.0);
					break;
				case SLIDE:
					double p = Math.min(1.0, tMs / durMs);
					slideX = (float)((1.0 - p) * 60.0);
				case NONE:
			}

			float opacityFactor = Math.max(0.0F, Math.min(1.0F, (float)slot.opacity / 100.0F));
			int finalAlpha = Math.round(255.0F * opacityFactor * animAlpha);
			if (finalAlpha > 2) {
				finalAlpha = Math.min(255, finalAlpha);
				float userScale = Math.max(0.05F, (float)slot.scale / 100.0F);
				float maxBox = Math.min(100.0F, (float)screenW * 0.09F);
				float fit = Math.min(maxBox / (float)tex.width, maxBox / (float)tex.height);
				if (fit > 1.0F) {
					fit = 1.0F;
				}

				float scale = fit * userScale;
				float scaledW = (float)tex.width * scale;
				float scaledH = (float)tex.height * scale;
				float[] pos = computeWatermarkAnchor(slot, screenW, screenH, scaledW, scaledH);
				float x = pos[0] + slideX;
				float y = pos[1];
				class_4587 ms = context.method_51448();
				ms.method_22903();
				ms.method_46416(x, y, 0.0F);
				if (slot.rotation != 0) {
					ms.method_22904((double)scaledW / 2.0, (double)scaledH / 2.0, 0.0);
					ms.method_22907(new Quaternionf().rotateZ((float)Math.toRadians((double)slot.rotation)));
					ms.method_22904((double)(-scaledW) / 2.0, (double)(-scaledH) / 2.0, 0.0);
				}

				ms.method_22905(scale, scale, 1.0F);
				RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, (float)finalAlpha / 255.0F);
				context.method_25290(tex.id, 0, 0, 0.0F, 0.0F, tex.width, tex.height, tex.width, tex.height);
				RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
				ms.method_22909();
			}
		}
	}

	private static void renderTextWatermark(class_332 context, class_327 tr, WatermarkSlot slot, int screenW, int screenH, String username, double tMs) {
		String text = slot.resolveText(username);
		if (text != null && !text.isEmpty()) {
			List<String> colorStops = slot.effectiveColors();
			int baseColor = parseWatermarkColor((String)colorStops.get(0));
			int baseAlpha = baseColor >>> 24 & 0xFF;
			if (baseAlpha == 0) {
				baseAlpha = 255;
			}

			float animAlpha = 1.0F;
			float slideX = 0.0F;
			double durMs = (double)Math.max(1, slot.animationDurationMs);
			switch (slot.animation) {
				case FADE:
					animAlpha = (float)Math.min(1.0, tMs / durMs);
					break;
				case PULSE:
					double phase = tMs % durMs / durMs;
					animAlpha = 0.45F + 0.55F * (float)((1.0 + Math.sin(phase * 2.0 * Math.PI)) / 2.0);
					break;
				case SLIDE:
					double p = Math.min(1.0, tMs / durMs);
					slideX = (float)((1.0 - p) * 60.0);
				case NONE:
			}

			float opacityFactor = Math.max(0.0F, Math.min(1.0F, (float)slot.opacity / 100.0F));
			int finalAlpha = Math.round((float)baseAlpha * opacityFactor * animAlpha);
			if (finalAlpha > 2) {
				finalAlpha = Math.min(255, finalAlpha);
				int color = finalAlpha << 24 | baseColor & 16777215;
				float scale = Math.max(0.1F, (float)slot.scale / 100.0F);
				int textW = tr.method_1727(text);
				int textH = 9;
				float scaledW = (float)textW * scale;
				float scaledH = (float)textH * scale;
				float[] pos = computeWatermarkAnchor(slot, screenW, screenH, scaledW, scaledH);
				float x = pos[0] + slideX;
				float y = pos[1];
				class_4587 ms = context.method_51448();
				ms.method_22903();
				ms.method_46416(x, y, 0.0F);
				if (slot.rotation != 0) {
					ms.method_22904((double)scaledW / 2.0, (double)scaledH / 2.0, 0.0);
					ms.method_22907(new Quaternionf().rotateZ((float)Math.toRadians((double)slot.rotation)));
					ms.method_22904((double)(-scaledW) / 2.0, (double)(-scaledH) / 2.0, 0.0);
				}

				ms.method_22905(scale, scale, 1.0F);
				if (colorStops.size() <= 1) {
					context.method_51433(tr, text, 0, 0, color, slot.textShadow);
				} else {
					drawGradientText(context, tr, text, finalAlpha, colorStops, slot.textShadow);
				}

				ms.method_22909();
			}
		}
	}

	private static float[] computeWatermarkAnchor(WatermarkSlot slot, int screenW, int screenH, float w, float h) {
		int pad = Math.max(0, slot.padding);
		float x;
		float y;
		switch (slot.position) {
			case TOP_LEFT:
				x = (float)pad;
				y = (float)pad;
				break;
			case TOP_CENTER:
				x = ((float)screenW - w) / 2.0F;
				y = (float)pad;
				break;
			case TOP_RIGHT:
				x = (float)screenW - w - (float)pad;
				y = (float)pad;
				break;
			case MIDDLE_LEFT:
				x = (float)pad;
				y = ((float)screenH - h) / 2.0F;
				break;
			case CENTER:
				x = ((float)screenW - w) / 2.0F;
				y = ((float)screenH - h) / 2.0F;
				break;
			case MIDDLE_RIGHT:
				x = (float)screenW - w - (float)pad;
				y = ((float)screenH - h) / 2.0F;
				break;
			case BOTTOM_LEFT:
				x = (float)pad;
				y = (float)screenH - h - (float)pad;
				break;
			case BOTTOM_CENTER:
				x = ((float)screenW - w) / 2.0F;
				y = (float)screenH - h - (float)pad;
				break;
			case BOTTOM_RIGHT:
				x = (float)screenW - w - (float)pad;
				y = (float)screenH - h - (float)pad;
				break;
			case CUSTOM:
				x = (float)slot.customX;
				y = (float)slot.customY;
				break;
			default:
				x = (float)screenW - w - (float)pad;
				y = (float)screenH - h - (float)pad;
		}

		return new float[]{x, y};
	}

	private static void drawGradientText(class_332 context, class_327 tr, String text, int alpha, List<String> colorStops, boolean shadow) {
		int n = colorStops.size();
		int[][] stops = new int[n][];

		for (int i = 0; i < n; i++) {
			int argb = parseWatermarkColor((String)colorStops.get(i));
			stops[i] = new int[]{argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF};
		}

		int totalW = Math.max(1, tr.method_1727(text));
		int penX = 0;
		int len = text.length();

		for (int i = 0; i < len; i++) {
			String ch = String.valueOf(text.charAt(i));
			int chW = tr.method_1727(ch);
			float frac = ((float)penX + (float)chW / 2.0F) / (float)totalW;
			int rgb = sampleGradientRgb(stops, frac);
			int color = alpha << 24 | rgb & 16777215;
			context.method_51433(tr, ch, penX, 0, color, shadow);
			penX += chW;
		}
	}

	private static int sampleGradientRgb(int[][] stops, float t) {
		if (stops.length == 1) {
			return stops[0][0] << 16 | stops[0][1] << 8 | stops[0][2];
		} else {
			if (t < 0.0F) {
				t = 0.0F;
			}

			if (t > 1.0F) {
				t = 1.0F;
			}

			float scaled = t * (float)(stops.length - 1);
			int idx = (int)Math.floor((double)scaled);
			if (idx >= stops.length - 1) {
				idx = stops.length - 2;
			}

			float local = scaled - (float)idx;
			int[] a = stops[idx];
			int[] b = stops[idx + 1];
			int r = Math.round((float)a[0] + (float)(b[0] - a[0]) * local);
			int g = Math.round((float)a[1] + (float)(b[1] - a[1]) * local);
			int bl = Math.round((float)a[2] + (float)(b[2] - a[2]) * local);
			return r << 16 | g << 8 | bl;
		}
	}

	private static int parseWatermarkColor(String hex) {
		if (hex == null) {
			return -1;
		} else {
			String s = hex.trim();
			if (s.startsWith("#")) {
				s = s.substring(1);
			}

			try {
				if (s.length() == 6) {
					return 0xFF000000 | (int)(Long.parseLong(s, 16) & 16777215L);
				}

				if (s.length() == 8) {
					return (int)(Long.parseLong(s, 16) & 4294967295L);
				}
			} catch (NumberFormatException var3) {
			}

			return -1;
		}
	}

	private static final class CachedWatermarkTexture {
		final class_2960 id;
		final int width;
		final int height;

		CachedWatermarkTexture(class_2960 id, int width, int height) {
			this.id = id;
			this.width = width;
			this.height = height;
		}
	}
}
