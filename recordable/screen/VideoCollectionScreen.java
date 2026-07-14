package dev.recordable.screen;

import dev.recordable.PlatformUtils;
import dev.recordable.RecordableConfig;
import dev.recordable.RecordableMod;
import dev.recordable.StorageManager;
import dev.recordable.VersionHelper;
import dev.recordable.VideoMetadata;
import dev.recordable.compat.RenderHelper;
import dev.recordable.theme.CycleButton;
import dev.recordable.theme.ThemeColors;
import dev.recordable.theme.ThemeEngine;
import dev.recordable.theme.ThemePreset;
import dev.recordable.theme.ThemedPanel;
import dev.recordable.theme.TypewriterText;
import dev.recordable.theme.VhsEffectsRenderer;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import net.minecraft.class_1011;
import net.minecraft.class_1043;
import net.minecraft.class_156;
import net.minecraft.class_2561;
import net.minecraft.class_2960;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_342;
import net.minecraft.class_4185;
import net.minecraft.class_437;
import net.minecraft.class_7919;
import net.minecraft.class_4185.class_4241;

public final class VideoCollectionScreen extends class_437 {
	private static final int PANEL_COLOR = -804253680;
	private static final int PANEL_BORDER_COLOR = -12434878;
	private static final int TEXT_COLOR = -1710619;
	private static final int MUTED_TEXT_COLOR = -4671304;
	private static final int ERROR_TEXT_COLOR = -34953;
	private static final int ENTRY_HEIGHT = 62;
	private static final int BUTTON_WIDTH = 54;
	private static final int BUTTON_HEIGHT = 14;
	private static final int DELETE_CONFIRM_MS = 6000;
	private final class_437 parent;
	private final boolean clipsMode;
	private final List<VideoMetadata> allVideos = new ArrayList();
	private final List<VideoMetadata> filteredVideos = new ArrayList();
	private final List<VideoCollectionScreen.ActionZone> actionZones = new ArrayList();
	private final Map<Path, VideoCollectionScreen.ThumbnailTexture> thumbnailCache = new HashMap();
	private class_342 searchField;
	private class_2561 statusMessage;
	private boolean statusIsError;
	private int scrollOffset;
	private boolean draggingScrollbar;
	private int contentLeft;
	private int contentWidth;
	private int listLeft;
	private int listRight;
	private int listTop;
	private int listBottom;
	private int headerTop;
	private long totalSizeBytes;
	private Path deleteConfirmPath;
	private long deleteConfirmUntil;
	private volatile ExecutorService durationProber;
	private final AtomicBoolean durationProbeRunning = new AtomicBoolean(false);

	public VideoCollectionScreen(class_437 parent) {
		this(parent, false);
	}

	public VideoCollectionScreen(class_437 parent, boolean clipsMode) {
		super(class_2561.method_43471(clipsMode ? "screen.recordable.video_collection.clips_title" : "screen.recordable.video_collection.title"));
		this.parent = parent;
		this.clipsMode = clipsMode;
	}

	protected void method_25426() {
		super.method_25426();
		this.method_37067();
		this.actionZones.clear();
		this.contentWidth = Math.max(320, Math.min((int)((double)this.field_22789 * 0.92), 980));
		this.contentLeft = (this.field_22789 - this.contentWidth) / 2;
		this.headerTop = Math.max(6, (int)((double)this.field_22790 * 0.025));
		int topBarY = this.headerTop + 18;
		int rowLeft = this.contentLeft + 8;
		int rowRight = this.contentLeft + this.contentWidth - 8;
		int btnGap = 4;
		int btnH = 18;
		int[] btnWidths = new int[]{74, 84, 130, 78, 110, 72};
		class_2561[] btnLabels = new class_2561[]{
			class_2561.method_43471("screen.recordable.video_collection.back"),
			class_2561.method_43471("screen.recordable.video_collection.settings"),
			class_2561.method_43471("screen.recordable.video_collection.open_recordings_folder"),
			class_2561.method_43471("screen.recordable.video_collection.refresh"),
			class_2561.method_43471("screen.recordable.video_collection.sort")
				.method_27661()
				.method_10852(class_2561.method_43470(": " + sortModeLabel(RecordableConfig.get().gallerySortMode))),
			class_2561.method_43471(this.clipsMode ? "screen.recordable.video_collection.recordings" : "screen.recordable.video_collection.clips")
		};
		class_4241[] btnActions = new class_4241[]{button -> this.method_25419(), button -> {
			if (this.field_22787 != null) {
				this.field_22787.method_1507(new RecordableSettingsScreen(this));
			}
		}, button -> this.openRecordingsFolder(), button -> this.refreshVideos(), button -> this.cycleSortMode(), button -> this.toggleClipsView()};
		int totalBtnWidth = 0;

		for (int w : btnWidths) {
			totalBtnWidth += w;
		}

		totalBtnWidth += btnGap * (btnWidths.length - 1);
		class_7919 androidFolderTip = class_7919.method_47407(
			class_2561.method_43470(
				"Not available on Android. Recordings are auto-saved to your gallery (Movies/Record-able) - open them from your Gallery or Files app."
			)
		);
		int availWidth = rowRight - rowLeft;
		int lastRowY = topBarY;
		if (totalBtnWidth <= availWidth) {
			int x = rowRight;

			for (int i = btnWidths.length - 1; i >= 0; i--) {
				x -= btnWidths[i];
				class_4185 btn = (class_4185)(i == 4
					? CycleButton.create(x, topBarY, btnWidths[i], btnH, btnLabels[i], b -> this.cycleSortMode(true), b -> this.cycleSortMode(false))
					: class_4185.method_46430(btnLabels[i], btnActions[i]).method_46434(x, topBarY, btnWidths[i], btnH).method_46431());
				if (i == 2 && PlatformUtils.isAndroid()) {
					btn.field_22763 = false;
					btn.method_47400(androidFolderTip);
				}

				this.method_37063(btn);
				x -= btnGap;
			}
		} else {
			int x = rowLeft;
			int y = topBarY;

			for (int i = 0; i < btnWidths.length; i++) {
				if (x > rowLeft && x + btnWidths[i] > rowRight) {
					x = rowLeft;
					y += btnH + btnGap;
				}

				class_4185 btn = (class_4185)(i == 4
					? CycleButton.create(x, y, btnWidths[i], btnH, btnLabels[i], b -> this.cycleSortMode(true), b -> this.cycleSortMode(false))
					: class_4185.method_46430(btnLabels[i], btnActions[i]).method_46434(x, y, btnWidths[i], btnH).method_46431());
				if (i == 2 && PlatformUtils.isAndroid()) {
					btn.field_22763 = false;
					btn.method_47400(androidFolderTip);
				}

				this.method_37063(btn);
				x += btnWidths[i] + btnGap;
			}

			lastRowY = y;
		}

		this.listLeft = this.contentLeft + 8;
		this.listRight = this.contentLeft + this.contentWidth - 8;
		this.listTop = lastRowY + 28;
		this.listBottom = this.field_22790 - Math.max(24, (int)((double)this.field_22790 * 0.04));
		this.refreshVideos();
	}

	private void sortAllVideos() {
		String mode = RecordableConfig.get().gallerySortMode;

		this.allVideos.sort(switch (mode) {
			case "oldest" -> Comparator.comparingLong(m -> m.modifiedMillis);
			case "name_az" -> Comparator.comparing(m -> m.filename, String.CASE_INSENSITIVE_ORDER);
			case "name_za" -> Comparator.comparing(m -> m.filename, String.CASE_INSENSITIVE_ORDER).reversed();
			case "largest" -> Comparator.comparingLong(m -> m.sizeBytes).reversed();
			case "smallest" -> Comparator.comparingLong(m -> m.sizeBytes);
			case "longest" -> Comparator.comparingDouble(m -> m.durationSeconds).reversed();
			case "shortest" -> Comparator.comparingDouble(m -> m.durationSeconds);
			default -> Comparator.comparingLong(m -> m.modifiedMillis).reversed();
		});
	}

	private static String sortModeLabel(String mode) {
		return switch (mode) {
			case "oldest" -> "Oldest";
			case "name_az" -> "A-Z";
			case "name_za" -> "Z-A";
			case "largest" -> "Largest";
			case "smallest" -> "Smallest";
			case "longest" -> "Longest";
			case "shortest" -> "Shortest";
			default -> "Newest";
		};
	}

	private void cycleSortMode() {
		this.cycleSortMode(true);
	}

	private void cycleSortMode(boolean forward) {
		RecordableConfig config = RecordableConfig.get();
		String[] modes = RecordableConfig.GALLERY_SORT_MODES;
		int idx = 0;

		for (int i = 0; i < modes.length; i++) {
			if (modes[i].equals(config.gallerySortMode)) {
				idx = i;
				break;
			}
		}

		int step = forward ? 1 : -1;
		config.gallerySortMode = modes[(idx + step + modes.length) % modes.length];
		config.save();
		this.sortAllVideos();
		this.applyFilter();
		this.method_25426();
	}

	private int addTopButton(int rightEdge, int y, int width, class_2561 text, class_4241 action) {
		int x = rightEdge - width;
		this.method_37063(class_4185.method_46430(text, action).method_46434(x, y, width, 18).method_46431());
		return x - 4;
	}

	public void method_25419() {
		this.cancelDurationProbe();
		this.clearThumbnails();
		if (this.field_22787 != null) {
			this.field_22787.method_1507(this.parent);
		}
	}

	private void cancelDurationProbe() {
		this.durationProbeRunning.set(false);
		ExecutorService exec = this.durationProber;
		if (exec != null) {
			exec.shutdownNow();
			this.durationProber = null;
		}
	}

	private void refreshVideos() {
		this.cancelDurationProbe();
		this.allVideos.clear();
		this.totalSizeBytes = 0L;
		this.statusMessage = null;
		this.statusIsError = false;
		this.clearThumbnails();

		try {
			RecordableConfig config = RecordableConfig.get();
			Path baseDir = config == null ? null : config.getOutputDirectory();
			Path scanDir = baseDir == null ? null : (this.clipsMode ? baseDir.resolve("recording_auto_clips") : baseDir);
			if (scanDir == null || !Files.exists(scanDir, new LinkOption[0]) || !Files.isDirectory(scanDir, new LinkOption[0])) {
				this.statusMessage = class_2561.method_43471(
					this.clipsMode ? "screen.recordable.video_collection.no_clips" : "screen.recordable.video_collection.no_recordings"
				);
				this.applyFilter();
				return;
			}

			Stream<Path> stream = this.clipsMode ? Files.walk(scanDir) : Files.list(scanDir);

			try {
				stream.filter(x$0 -> Files.isRegularFile(x$0, new LinkOption[0])).filter(VideoCollectionScreen::isSupportedVideo).forEach(path -> {
					try {
						VideoMetadata metadata = VideoMetadata.readQuick(path);
						this.allVideos.add(metadata);
						this.totalSizeBytes = this.totalSizeBytes + Math.max(0L, metadata.sizeBytes);
					} catch (Throwable var3x) {
						RecordableMod.LOGGER.warn("Skipping unreadable recording entry {}", path, var3x);
					}
				});
			} catch (Throwable var8) {
				if (stream != null) {
					try {
						stream.close();
					} catch (Throwable var7) {
						var8.addSuppressed(var7);
					}
				}

				throw var8;
			}

			if (stream != null) {
				stream.close();
			}

			this.sortAllVideos();
			if (this.allVideos.isEmpty()) {
				this.statusMessage = class_2561.method_43471(
					this.clipsMode ? "screen.recordable.video_collection.no_clips" : "screen.recordable.video_collection.no_recordings"
				);
			}
		} catch (Throwable var9) {
			RecordableMod.LOGGER.warn("Failed to refresh video collection.", var9);
			this.statusMessage = class_2561.method_43471("screen.recordable.video_collection.refresh_failed");
			this.statusIsError = true;
		}

		this.applyFilter();
		this.startDurationProbe();
	}

	private void startDurationProbe() {
		List<Path> needsProbe = new ArrayList();

		for (VideoMetadata m : this.allVideos) {
			if (m.durationSeconds <= 0.0 && m.file != null) {
				needsProbe.add(m.file);
			}
		}

		if (!needsProbe.isEmpty()) {
			this.durationProbeRunning.set(true);
			ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
				Thread t = new Thread(r, "recordable-duration-prober");
				t.setDaemon(true);
				return t;
			});
			this.durationProber = exec;
			exec.submit(() -> {
				for (Path path : needsProbe) {
					if (!this.durationProbeRunning.get()) {
						break;
					}

					try {
						VideoMetadata probed = VideoMetadata.probeDurationFor(path);
						if (probed != null && this.durationProbeRunning.get()) {
							class_310 mc = class_310.method_1551();
							if (mc != null) {
								mc.execute(() -> this.replaceProbedEntry(path, probed));
							}
						}
					} catch (Throwable var6) {
						RecordableMod.LOGGER.debug("Background duration probe error for {}", path, var6);
					}
				}

				this.durationProbeRunning.set(false);
			});
		}
	}

	private void replaceProbedEntry(Path file, VideoMetadata probed) {
		replaceInList(this.allVideos, file, probed);
		replaceInList(this.filteredVideos, file, probed);
	}

	private static void replaceInList(List<VideoMetadata> list, Path file, VideoMetadata replacement) {
		for (int i = 0; i < list.size(); i++) {
			VideoMetadata existing = (VideoMetadata)list.get(i);
			if (existing != null && existing.file != null && existing.file.equals(replacement.file)) {
				list.set(i, replacement);
				break;
			}
		}
	}

	private void applyFilter() {
		String query = this.searchField == null ? "" : this.searchField.method_1882();
		String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
		this.filteredVideos.clear();
		if (normalized.isBlank()) {
			this.filteredVideos.addAll(this.allVideos);
		} else {
			for (VideoMetadata metadata : this.allVideos) {
				if (metadata != null) {
					String haystack = (metadata.filename + " " + metadata.recordedAtDisplay).toLowerCase(Locale.ROOT);
					if (haystack.contains(normalized)) {
						this.filteredVideos.add(metadata);
					}
				}
			}
		}

		this.clampScroll();
	}

	public boolean method_25401(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		int viewHeight = Math.max(0, this.listBottom - this.listTop);
		int maxScroll = Math.max(0, this.filteredVideos.size() * 62 - viewHeight);
		if (maxScroll <= 0) {
			return super.method_25401(mouseX, mouseY, horizontalAmount, verticalAmount);
		} else {
			int delta = (int)Math.round(verticalAmount * -20.0);
			if (delta == 0) {
				delta = verticalAmount > 0.0 ? -20 : 20;
			}

			this.scrollOffset = Math.max(0, Math.min(maxScroll, this.scrollOffset + delta));
			return true;
		}
	}

	public boolean method_25402(double mouseX, double mouseY, int button) {
		if (super.method_25402(mouseX, mouseY, button)) {
			return true;
		} else if (button == 0 && this.isOverScrollbar(mouseX, mouseY)) {
			this.draggingScrollbar = true;
			this.scrollToMouse(mouseY);
			return true;
		} else {
			for (VideoCollectionScreen.ActionZone zone : this.actionZones) {
				if (zone.contains(mouseX, mouseY)) {
					zone.action.run();
					return true;
				}
			}

			return false;
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
		int viewHeight = Math.max(0, this.listBottom - this.listTop);
		int contentHeight = this.filteredVideos.size() * 62;
		if (contentHeight <= viewHeight) {
			return false;
		} else {
			int scrollbarLeft = this.listRight - 6;
			int scrollbarRight = this.listRight;
			return mouseX >= (double)scrollbarLeft && mouseX <= (double)scrollbarRight && mouseY >= (double)this.listTop && mouseY <= (double)this.listBottom;
		}
	}

	private void scrollToMouse(double mouseY) {
		int viewHeight = Math.max(1, this.listBottom - this.listTop);
		int contentHeight = this.filteredVideos.size() * 62;
		int maxScroll = Math.max(0, contentHeight - viewHeight);
		if (maxScroll > 0) {
			int thumbHeight = Math.max(24, (int)((double)viewHeight * ((double)viewHeight / (double)contentHeight)));
			int available = Math.max(1, viewHeight - thumbHeight);
			double ratio = (mouseY - (double)this.listTop - (double)thumbHeight / 2.0) / (double)available;
			ratio = Math.max(0.0, Math.min(1.0, ratio));
			this.scrollOffset = (int)Math.round(ratio * (double)maxScroll);
			this.clampScroll();
		}
	}

	public boolean method_25404(int keyCode, int scanCode, int modifiers) {
		if (this.searchField != null && this.searchField.method_25370()) {
			return super.method_25404(keyCode, scanCode, modifiers);
		} else if (keyCode == 264) {
			this.scrollOffset += 62;
			this.clampScroll();
			return true;
		} else if (keyCode == 265) {
			this.scrollOffset -= 62;
			this.clampScroll();
			return true;
		} else {
			return super.method_25404(keyCode, scanCode, modifiers);
		}
	}

	public void method_25394(class_332 context, int mouseX, int mouseY, float delta) {
		this.method_25420(context, mouseX, mouseY, delta);
		ThemeColors colors = ThemeEngine.get().colors();
		ThemePreset preset = ThemeEngine.get().preset();
		int panelLeft = this.listLeft - 8;
		int panelRight = this.listRight + 8;
		int panelTop = this.headerTop - 4;
		int panelBottom = this.field_22790 - 8;
		if (preset == ThemePreset.CINEMA) {
			ThemedPanel.drawFilmPanel(context, panelLeft, panelTop, panelRight, panelBottom);
		} else {
			ThemedPanel.drawPanel(context, panelLeft, panelTop, panelRight, panelBottom);
		}

		if (preset == ThemePreset.VHS) {
			TypewriterText.renderFlickerText(
				context,
				this.field_22793,
				"▶ " + this.field_22785.getString(),
				this.field_22789 / 2 - this.field_22793.method_1727("▶ " + this.field_22785.getString()) / 2,
				panelTop + 6,
				colors.headerText
			);
		} else if (preset == ThemePreset.CINEMA) {
			context.method_25300(this.field_22793, "\ud83c\udfac " + this.field_22785.getString(), this.field_22789 / 2, panelTop + 6, colors.headerText);
		} else {
			context.method_27534(this.field_22793, this.field_22785, this.field_22789 / 2, panelTop + 6, colors.headerText);
		}

		String summary = class_2561.method_43469(
				"screen.recordable.video_collection.summary", new Object[]{Integer.toString(this.allVideos.size()), formatSizeMb(this.totalSizeBytes)}
			)
			.getString();
		RenderHelper.drawText(context, this.field_22793, class_2561.method_43470(summary), this.listLeft, panelTop + 6, colors.textMuted);
		super.method_25394(context, mouseX, mouseY, delta);
		context.method_25294(this.listLeft, this.listTop, this.listRight, this.listBottom, colors.sectionBackground);
		context.method_25294(this.listLeft, this.listTop, this.listRight, this.listTop + 1, colors.accent);
		context.method_25294(this.listLeft, this.listBottom - 1, this.listRight, this.listBottom, colors.panelBorder);
		if (preset == ThemePreset.CINEMA) {
			VhsEffectsRenderer.renderSprocketHoles(context, this.listLeft - 6, this.listTop, this.listBottom, colors.accent);
			VhsEffectsRenderer.renderSprocketHoles(context, this.listRight + 1, this.listTop, this.listBottom, colors.accent);
		}

		this.actionZones.clear();
		if (this.filteredVideos.isEmpty()) {
			class_2561 noItemsText = class_2561.method_43471("screen.recordable.video_collection.no_recordings");
			context.method_27534(
				this.field_22793, noItemsText, this.field_22789 / 2, this.listTop + Math.max(8, (this.listBottom - this.listTop) / 2 - 6), colors.textMuted
			);
			if (preset == ThemePreset.VHS || preset == ThemePreset.CINEMA) {
				ThemedPanel.drawReelLoading(context, this.field_22789 / 2, this.listTop + (this.listBottom - this.listTop) / 2 + 16, 12);
			}
		} else {
			this.renderVideoEntries(context, mouseX, mouseY);
		}

		if (this.statusMessage != null) {
			RenderHelper.drawText(
				context, this.field_22793, this.statusMessage, this.listLeft, this.field_22790 - 18, this.statusIsError ? colors.textError : colors.textMuted
			);
		}
	}

	private void renderVideoEntries(class_332 context, int mouseX, int mouseY) {
		int viewHeight = Math.max(1, this.listBottom - this.listTop);
		int firstIndex = Math.max(0, this.scrollOffset / 62);
		int lastIndexExclusive = Math.min(this.filteredVideos.size(), firstIndex + viewHeight / 62 + 3);
		int y = this.listTop - this.scrollOffset % 62;

		for (int index = firstIndex; index < lastIndexExclusive; index++) {
			VideoMetadata metadata = (VideoMetadata)this.filteredVideos.get(index);
			int entryTop = y + (index - firstIndex) * 62;
			int entryBottom = entryTop + 62 - 2;
			if (entryBottom >= this.listTop && entryTop <= this.listBottom) {
				this.renderEntry(context, metadata, entryTop, entryBottom, mouseX, mouseY);
			}
		}

		this.renderScrollBar(context, viewHeight);
	}

	private void renderEntry(class_332 context, VideoMetadata metadata, int top, int bottom, int mouseX, int mouseY) {
		if (metadata != null) {
			ThemeColors tc = ThemeEngine.get().colors();
			int accent = tc.accent;
			boolean hovered = mouseY >= top && mouseY <= bottom && mouseX >= this.listLeft && mouseX <= this.listRight;
			int background = hovered ? tc.panelBackground : ThemeEngine.lerpColor(tc.panelBackground, -16777216, 0.3F);
			context.method_25294(this.listLeft + 2, top, this.listRight - 2, bottom, background);
			if (hovered) {
				context.method_25294(this.listLeft + 2, top, this.listLeft + 4, bottom, accent);
			}

			int thumbLeft = this.listLeft + 6;
			int thumbTop = top + 5;
			int thumbWidth = 74;
			int thumbHeight = 40;
			context.method_25294(thumbLeft, thumbTop, thumbLeft + thumbWidth, thumbTop + thumbHeight, tc.panelBackground);
			context.method_25294(thumbLeft, thumbTop, thumbLeft + thumbWidth, thumbTop + 1, tc.panelBorder);
			context.method_25294(thumbLeft, thumbTop + thumbHeight - 1, thumbLeft + thumbWidth, thumbTop + thumbHeight, tc.panelBorder);
			boolean renderedThumb = this.drawThumbnail(context, metadata, thumbLeft + 1, thumbTop + 1, thumbWidth - 2, thumbHeight - 2);
			if (!renderedThumb) {
				context.method_25294(thumbLeft + 8, thumbTop + 7, thumbLeft + 66, thumbTop + 33, tc.sectionHover);
				context.method_27534(this.field_22793, class_2561.method_43470("VIDEO"), thumbLeft + thumbWidth / 2, thumbTop + 15, accent);
			}

			boolean isProtected = StorageManager.isProtected(RecordableConfig.get(), metadata.filename);
			int textX = thumbLeft + thumbWidth + 8;
			String displayName = (isProtected ? "\ud83d\udd12 " : "") + metadata.filename;
			RenderHelper.drawText(context, this.field_22793, class_2561.method_43470(displayName), textX, top + 4, isProtected ? tc.accent : tc.textPrimary);
			RenderHelper.drawText(
				context,
				this.field_22793,
				class_2561.method_43469("screen.recordable.video_collection.meta.size_duration", new Object[]{metadata.sizeDisplay, metadata.durationDisplay}),
				textX,
				top + 17,
				tc.textSecondary
			);
			RenderHelper.drawText(
				context,
				this.field_22793,
				class_2561.method_43469("screen.recordable.video_collection.meta.recorded_at", new Object[]{metadata.recordedAtDisplay}),
				textX,
				top + 29,
				tc.textSecondary
			);
			int buttonsRight = this.listRight - 6;
			int col3 = buttonsRight - 54;
			int col2 = col3 - 5 - 54;
			int col1 = col2 - 5 - 54;
			int row1 = top + 5;
			int row2 = top + 24;
			int row3 = top + 43;
			this.drawActionButton(
				context, mouseX, mouseY, col1, row1, 54, 14, class_2561.method_43471("screen.recordable.video_collection.play"), () -> this.playInGame(metadata.file)
			);
			if (PlatformUtils.isAndroid()) {
				this.drawDisabledActionButton(context, col2, row1, 54, 14, class_2561.method_43471("screen.recordable.video_collection.open_folder"));
			} else {
				this.drawActionButton(
					context,
					mouseX,
					mouseY,
					col2,
					row1,
					54,
					14,
					class_2561.method_43471("screen.recordable.video_collection.open_folder"),
					() -> this.openContainingFolder(metadata.file)
				);
			}

			this.drawActionButton(
				context,
				mouseX,
				mouseY,
				col1,
				row2,
				54,
				14,
				class_2561.method_43471(isProtected ? "screen.recordable.video_collection.unprotect" : "screen.recordable.video_collection.protect"),
				() -> this.toggleProtect(metadata)
			);
			this.drawActionButton(
				context, mouseX, mouseY, col2, row2, 54, 14, class_2561.method_43471("screen.recordable.video_collection.delete"), () -> this.confirmDelete(metadata.file)
			);
			this.drawActionButton(
				context, mouseX, mouseY, col1, row3, 54, 14, class_2561.method_43471("screen.recordable.video_collection.copy_path"), () -> this.copyPath(metadata.file)
			);
		}
	}

	private void toggleProtect(VideoMetadata metadata) {
		if (metadata != null && metadata.filename != null) {
			try {
				StorageManager.toggleProtected(RecordableConfig.get(), metadata.filename);
				boolean nowProtected = StorageManager.isProtected(RecordableConfig.get(), metadata.filename);
				if (nowProtected && metadata.file != null && metadata.file.equals(this.deleteConfirmPath)) {
					this.deleteConfirmPath = null;
					this.deleteConfirmUntil = 0L;
				}

				this.setStatus(
					class_2561.method_43469(
						nowProtected ? "screen.recordable.video_collection.protected" : "screen.recordable.video_collection.unprotected", new Object[]{metadata.filename}
					),
					false
				);
			} catch (Throwable var3) {
				RecordableMod.LOGGER.warn("Failed to toggle protection for {}", metadata.filename, var3);
			}
		}
	}

	private boolean drawThumbnail(class_332 context, VideoMetadata metadata, int x, int y, int width, int height) {
		if (metadata.thumbnailPath != null && Files.exists(metadata.thumbnailPath, new LinkOption[0])) {
			VideoCollectionScreen.ThumbnailTexture texture = (VideoCollectionScreen.ThumbnailTexture)this.thumbnailCache.get(metadata.thumbnailPath);
			if (texture == null) {
				texture = this.loadThumbnailTexture(metadata.thumbnailPath);
				if (texture != null) {
					this.thumbnailCache.put(metadata.thumbnailPath, texture);
				}
			}

			if (texture != null && texture.identifier != null) {
				try {
					context.method_25290(texture.identifier, x, y, 0.0F, 0.0F, width, height, width, height);
					return true;
				} catch (Throwable var9) {
					RecordableMod.LOGGER.debug("Failed to draw thumbnail texture for {}", metadata.thumbnailPath, var9);
					return false;
				}
			} else {
				return false;
			}
		} else {
			return false;
		}
	}

	private VideoCollectionScreen.ThumbnailTexture loadThumbnailTexture(Path thumbnailPath) {
		class_310 client = this.field_22787 == null ? class_310.method_1551() : this.field_22787;
		if (client != null && client.method_1531() != null) {
			try {
				if (Files.exists(thumbnailPath, new LinkOption[0]) && Files.isReadable(thumbnailPath)) {
					InputStream stream = Files.newInputStream(thumbnailPath);

					class_1011 image;
					try {
						image = class_1011.method_4309(stream);
					} catch (Throwable var8) {
						if (stream != null) {
							try {
								stream.close();
							} catch (Throwable var7) {
								var8.addSuppressed(var7);
							}
						}

						throw var8;
					}

					if (stream != null) {
						stream.close();
					}

					class_1043 nativeTexture = new class_1043(image);
					String idSuffix = Integer.toHexString(thumbnailPath.toAbsolutePath().toString().hashCode());
					class_2960 id = VersionHelper.id("recordable", "thumb/" + idSuffix);
					client.method_1531().method_4616(id, nativeTexture);
					return new VideoCollectionScreen.ThumbnailTexture(id, nativeTexture);
				} else {
					RecordableMod.LOGGER.debug("Thumbnail path is missing/unreadable: {}", thumbnailPath);
					return null;
				}
			} catch (Throwable var9) {
				RecordableMod.LOGGER.debug("Failed to load thumbnail texture from {}", thumbnailPath, var9);
				return null;
			}
		} else {
			return null;
		}
	}

	private void clearThumbnails() {
		class_310 client = this.field_22787 == null ? class_310.method_1551() : this.field_22787;

		for (VideoCollectionScreen.ThumbnailTexture texture : this.thumbnailCache.values()) {
			if (texture != null) {
				try {
					if (client != null && client.method_1531() != null && texture.identifier != null) {
						client.method_1531().method_4615(texture.identifier);
					}

					if (texture.texture != null) {
						texture.texture.close();
					}
				} catch (Throwable var5) {
				}
			}
		}

		this.thumbnailCache.clear();
	}

	private void drawActionButton(class_332 context, int mouseX, int mouseY, int x, int y, int width, int height, class_2561 label, Runnable action) {
		ThemeColors tc = ThemeEngine.get().colors();
		int accent = tc.accent;
		boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
		int fill = hovered ? tc.sectionHover : tc.sectionBackground;
		context.method_25294(x, y, x + width, y + height, fill);
		context.method_25294(x, y, x + width, y + 1, hovered ? accent : tc.panelBorder);
		context.method_25294(x, y + height - 1, x + width, y + height, tc.panelBorder);
		context.method_25294(x, y, x + 1, y + height, tc.panelBorder);
		context.method_25294(x + width - 1, y, x + width, y + height, tc.panelBorder);
		context.method_27534(this.field_22793, label, x + width / 2, y + 3, hovered ? tc.textPrimary : tc.textSecondary);
		this.actionZones.add(new VideoCollectionScreen.ActionZone(x, y, x + width, y + height, action));
	}

	private void drawDisabledActionButton(class_332 context, int x, int y, int width, int height, class_2561 label) {
		ThemeColors tc = ThemeEngine.get().colors();
		context.method_25294(x, y, x + width, y + height, tc.sectionBackground);
		context.method_25294(x, y, x + width, y + 1, tc.panelBorder);
		context.method_25294(x, y + height - 1, x + width, y + height, tc.panelBorder);
		context.method_25294(x, y, x + 1, y + height, tc.panelBorder);
		context.method_25294(x + width - 1, y, x + width, y + height, tc.panelBorder);
		context.method_27534(this.field_22793, label, x + width / 2, y + 3, tc.textMuted);
	}

	private void renderScrollBar(class_332 context, int viewHeight) {
		int contentHeight = this.filteredVideos.size() * 62;
		if (contentHeight > viewHeight) {
			ThemeColors tc = ThemeEngine.get().colors();
			int scrollbarLeft = this.listRight - 6;
			int scrollbarRight = this.listRight - 2;
			context.method_25294(scrollbarLeft, this.listTop, scrollbarRight, this.listBottom, tc.sectionBackground);
			int thumbHeight = Math.max(24, (int)((double)viewHeight * ((double)viewHeight / (double)contentHeight)));
			int maxScroll = contentHeight - viewHeight;
			int available = viewHeight - thumbHeight;
			int thumbTop = this.listTop + (int)((double)this.scrollOffset / (double)maxScroll * (double)available);
			boolean thumbHovered = this.draggingScrollbar;
			context.method_25294(scrollbarLeft, thumbTop, scrollbarRight, thumbTop + thumbHeight, thumbHovered ? tc.textPrimary : tc.accent);
		}
	}

	private void toggleClipsView() {
		if (this.field_22787 != null) {
			if (this.clipsMode) {
				this.method_25419();
			} else {
				this.field_22787.method_1507(new VideoCollectionScreen(this, true));
			}
		}
	}

	private void openRecordingsFolder() {
		try {
			Path dir = RecordableConfig.get().getOutputDirectory();
			if (this.clipsMode) {
				dir = dir.resolve("recording_auto_clips");
			}

			Files.createDirectories(dir);
			class_156.method_668().method_673(dir.toUri());
			this.setStatus(class_2561.method_43471("screen.recordable.video_collection.opened_folder"), false);
		} catch (Throwable var2) {
			RecordableMod.LOGGER.warn("Failed to open recordings folder from collection screen.", var2);
			this.setStatus(class_2561.method_43471("screen.recordable.video_collection.open_folder_failed"), true);
		}
	}

	private void openContainingFolder(Path file) {
		if (file != null) {
			try {
				Path parent = file.getParent();
				if (parent == null) {
					throw new IOException("Missing parent directory");
				}

				Files.createDirectories(parent);
				class_156.method_668().method_673(parent.toUri());
				this.setStatus(class_2561.method_43471("screen.recordable.video_collection.opened_folder"), false);
			} catch (Throwable var3) {
				RecordableMod.LOGGER.warn("Failed to open folder for {}", file, var3);
				this.setStatus(class_2561.method_43471("screen.recordable.video_collection.open_folder_failed"), true);
			}
		}
	}

	private void playInGame(Path file) {
		if (file != null && this.field_22787 != null) {
			try {
				this.clearThumbnails();
				this.field_22787.method_1507(new VideoPlayerScreen(file, this));
			} catch (Throwable var3) {
				RecordableMod.LOGGER.warn("Failed to open in-game video player for {}", file, var3);
				this.setStatus(class_2561.method_43471("screen.recordable.video_collection.play_failed"), true);
			}
		}
	}

	private void openFile(Path file) {
		if (file != null) {
			try {
				class_156.method_668().method_673(file.toUri());
				this.setStatus(class_2561.method_43469("screen.recordable.video_collection.playing", new Object[]{file.getFileName()}), false);
			} catch (Throwable var3) {
				RecordableMod.LOGGER.warn("Failed to open video file {}", file, var3);
				this.setStatus(class_2561.method_43471("screen.recordable.video_collection.play_failed"), true);
			}
		}
	}

	private void copyPath(Path file) {
		if (file != null && this.field_22787 != null && this.field_22787.field_1774 != null) {
			try {
				this.field_22787.field_1774.method_1455(file.toAbsolutePath().toString());
				this.setStatus(class_2561.method_43471("screen.recordable.video_collection.copied_path"), false);
			} catch (Throwable var3) {
				RecordableMod.LOGGER.warn("Failed to copy file path for {}", file, var3);
				this.setStatus(class_2561.method_43471("screen.recordable.video_collection.copy_failed"), true);
			}
		}
	}

	private void confirmDelete(Path file) {
		if (file != null) {
			if (StorageManager.isProtected(RecordableConfig.get(), file.getFileName().toString())) {
				this.setStatus(class_2561.method_43469("screen.recordable.video_collection.delete_protected", new Object[]{file.getFileName()}), true);
			} else {
				long now = System.currentTimeMillis();
				if (file.equals(this.deleteConfirmPath) && now <= this.deleteConfirmUntil) {
					this.deleteConfirmPath = null;
					this.deleteConfirmUntil = 0L;

					try {
						Files.deleteIfExists(file);
						this.setStatus(class_2561.method_43469("screen.recordable.video_collection.deleted", new Object[]{file.getFileName()}), false);
						this.refreshVideos();
					} catch (Throwable var5) {
						RecordableMod.LOGGER.warn("Failed to delete recording {}", file, var5);
						this.setStatus(class_2561.method_43469("screen.recordable.video_collection.delete_failed", new Object[]{file.getFileName()}), true);
					}
				} else {
					this.deleteConfirmPath = file;
					this.deleteConfirmUntil = now + 6000L;
					this.setStatus(class_2561.method_43469("screen.recordable.video_collection.delete_confirm", new Object[]{file.getFileName()}), true);
				}
			}
		}
	}

	private void clampScroll() {
		int viewHeight = Math.max(0, this.listBottom - this.listTop);
		int maxScroll = Math.max(0, this.filteredVideos.size() * 62 - viewHeight);
		this.scrollOffset = Math.max(0, Math.min(maxScroll, this.scrollOffset));
	}

	private void setStatus(class_2561 text, boolean isError) {
		this.statusMessage = text;
		this.statusIsError = isError;
	}

	private static String formatSizeMb(long bytes) {
		return String.format(Locale.ROOT, "%.2f MB", (double)Math.max(0L, bytes) / 1048576.0);
	}

	private static boolean isSupportedVideo(Path path) {
		if (path != null && path.getFileName() != null) {
			String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
			return name.endsWith(".mp4") || name.endsWith(".mkv");
		} else {
			return false;
		}
	}

	private static record ActionZone(int x1, int y1, int x2, int y2, Runnable action) {
		private boolean contains(double x, double y) {
			return x >= (double)this.x1 && x < (double)this.x2 && y >= (double)this.y1 && y < (double)this.y2;
		}
	}

	private static record ThumbnailTexture(class_2960 identifier, class_1043 texture) {
	}
}
