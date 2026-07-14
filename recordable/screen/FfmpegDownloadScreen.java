package dev.recordable.screen;

import dev.recordable.FFmpegEncoder;
import dev.recordable.FfmpegBundleManager;
import dev.recordable.PlatformUtils;
import dev.recordable.RecordableConfig;
import dev.recordable.RecordableMod;
import dev.recordable.FfmpegBundleManager.DownloadProgress;
import dev.recordable.FfmpegBundleManager.ProgressListener;
import dev.recordable.FfmpegBundleManager.Status;
import dev.recordable.PlatformUtils.Platform;
import dev.recordable.compat.RenderHelper;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.class_156;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_4185;
import net.minecraft.class_437;
import net.minecraft.class_7919;

public final class FfmpegDownloadScreen extends class_437 implements ProgressListener {
	private static final int PANEL_COLOR = -804253680;
	private static final int PANEL_BORDER_COLOR = -12434878;
	private static final int HEADER_COLOR = -1;
	private static final int TEXT_COLOR = -3092272;
	private static final int HIGHLIGHT_COLOR = -7811960;
	private static final int WARNING_COLOR = -13244;
	private static final int ERROR_COLOR = -34953;
	private static final int PROGRESS_BG = -14671840;
	private static final int PROGRESS_BORDER = -10461088;
	private final class_437 parent;
	private int panelLeft;
	private int panelTop;
	private int panelWidth;
	private int panelBottom;
	private class_4185 downloadButton;
	private class_4185 cancelButton;
	private class_4185 openFolderButton;
	private final List<FfmpegDownloadScreen.Line> lines = new ArrayList();
	private DownloadProgress currentProgress = DownloadProgress.IDLE;
	private boolean failureShown = false;
	private String testReport = null;
	private static final int LINE_HEIGHT = 12;
	private static final int SCROLLBAR_WIDTH = 6;
	private int panelBodyTop;
	private int panelBodyBottom;
	private int contentHeight;
	private int scrollOffset;
	private int mainRowY;
	private int toolbarRowY;
	private int progressBarTop;
	private boolean draggingScrollbar = false;
	private boolean draggingBody = false;
	private int textWrapWidth = 400;

	public FfmpegDownloadScreen(class_437 parent) {
		super(class_2561.method_43470("Record-able: FFmpeg Setup"));
		this.parent = parent;
	}

	protected void method_25426() {
		super.method_25426();
		this.method_37067();
		this.panelWidth = Math.min(this.field_22789 - 16, Math.max(320, Math.min((int)((double)this.field_22789 * 0.9), 640)));
		this.panelLeft = (this.field_22789 - this.panelWidth) / 2;
		this.panelTop = Math.max(8, (int)((double)this.field_22790 * 0.08));
		this.panelBottom = Math.min(this.field_22790 - 8, this.panelTop + Math.max(280, (int)((double)this.field_22790 * 0.84)));
		this.textWrapWidth = Math.max(80, this.panelWidth - 28);
		this.rebuildLines();
		this.panelBodyTop = this.panelTop + 22;
		this.mainRowY = this.panelBottom - 28;
		this.toolbarRowY = this.mainRowY - 26;
		this.progressBarTop = this.toolbarRowY - 26;
		this.panelBodyBottom = this.progressBarTop - 14;
		this.contentHeight = this.lines.size() * 12;
		int maxScroll = Math.max(0, this.contentHeight - (this.panelBodyBottom - this.panelBodyTop));
		this.scrollOffset = Math.max(0, Math.min(maxScroll, this.scrollOffset));
		int btnW = 160;
		int btnH = 20;
		int btnY = this.mainRowY;
		int centerX = this.panelLeft + this.panelWidth / 2;
		boolean autoSupported = FfmpegBundleManager.isAutoDownloadSupported();
		boolean isDownloading = FfmpegBundleManager.isDownloading();
		boolean alreadyAvailable = FfmpegBundleManager.isBundledFfmpegAvailable();
		Platform platform = PlatformUtils.detectPlatform();
		if (alreadyAvailable) {
			this.cancelButton = class_4185.method_46430(class_2561.method_43470("Close"), btn -> this.method_25419())
				.method_46434(centerX - btnW / 2, btnY, btnW, btnH)
				.method_46431();
			this.method_37063(this.cancelButton);
		} else if (autoSupported) {
			String dlLabel = isDownloading ? "Downloading…" : "Download FFmpeg (" + FfmpegBundleManager.getEstimatedDownloadSize() + ")";
			this.downloadButton = class_4185.method_46430(class_2561.method_43470(dlLabel), btn -> this.startDownload())
				.method_46434(centerX - btnW - 4, btnY, btnW, btnH)
				.method_46436(
					class_7919.method_47407(
						class_2561.method_43470(
							"Downloads FFmpeg from "
								+ FfmpegBundleManager.getDownloadSourceDescription()
								+ ".\nThe binary will be stored at "
								+ FfmpegBundleManager.getBundleDirectory()
								+ ".\nThe download is HTTPS-authenticated and (where available) verified against the upstream hash."
						)
					)
				)
				.method_46431();
			this.downloadButton.field_22763 = !isDownloading;
			this.method_37063(this.downloadButton);
			this.cancelButton = class_4185.method_46430(class_2561.method_43470(isDownloading ? "Hide" : "Cancel"), btn -> this.method_25419())
				.method_46434(centerX + 4, btnY, btnW, btnH)
				.method_46431();
			this.method_37063(this.cancelButton);
		} else {
			this.openFolderButton = class_4185.method_46430(class_2561.method_43470("Open FFmpeg Folder"), btn -> this.openBundleFolder())
				.method_46434(centerX - btnW - 4, btnY, btnW, btnH)
				.method_46431();
			this.method_37063(this.openFolderButton);
			this.cancelButton = class_4185.method_46430(class_2561.method_43470("Close"), btn -> this.method_25419())
				.method_46434(centerX + 4, btnY, btnW, btnH)
				.method_46431();
			this.method_37063(this.cancelButton);
		}

		if (!isDownloading) {
			int row2Y = this.toolbarRowY;
			int gap = 6;
			int totalW = this.panelWidth - 24;
			int third = (totalW - gap * 2) / 3;
			int bx = this.panelLeft + 12;
			this.method_37063(
				class_4185.method_46430(class_2561.method_43470("Test FFmpeg"), b -> this.runTest())
					.method_46434(bx, row2Y, third, btnH)
					.method_46436(class_7919.method_47407(class_2561.method_43470("Re-probe every FFmpeg location and show exactly what was tried and why each failed.")))
					.method_46431()
			);
			this.method_37063(
				class_4185.method_46430(class_2561.method_43470("Paste FFmpeg Path"), b -> this.pastePathFromClipboard())
					.method_46434(bx + third + gap, row2Y, third, btnH)
					.method_46436(
						class_7919.method_47407(
							class_2561.method_43470("Set the FFmpeg path from your clipboard, e.g.\n/data/data/com.termux/files/usr/bin/ffmpeg\nThen it is tested immediately.")
						)
					)
					.method_46431()
			);
			this.method_37063(
				class_4185.method_46430(class_2561.method_43470("Copy Termux Cmd"), b -> this.copyTermuxCommand())
					.method_46434(bx + (third + gap) * 2, row2Y, totalW - (third + gap) * 2, btnH)
					.method_46436(class_7919.method_47407(class_2561.method_43470("Copy 'pkg install ffmpeg' to the clipboard to paste into Termux.")))
					.method_46431()
			);
		}

		FfmpegBundleManager.addProgressListener(this);
		this.currentProgress = FfmpegBundleManager.getLastProgress();
	}

	private void rebuildAndReinit() {
		this.method_37067();
		this.method_25426();
	}

	private void setClipboardText(String s) {
		if (this.field_22787 != null) {
			this.field_22787.field_1774.method_1455(s);
		}
	}

	private String getClipboardText() {
		return this.field_22787 == null ? "" : this.field_22787.field_1774.method_1460();
	}

	private void runTest() {
		this.testReport = FFmpegEncoder.testFfmpegVerbose(null);
		this.rebuildAndReinit();
	}

	private void copyTermuxCommand() {
		this.setClipboardText("pkg install ffmpeg");
		this.testReport = "Copied to clipboard:\n  pkg install ffmpeg\n\n1. Open Termux (from F-Droid) and paste + run it.\n2. Run 'which ffmpeg' and copy the printed path.\n3. Come back and tap 'Paste FFmpeg Path'.";
		this.rebuildAndReinit();
	}

	private void pastePathFromClipboard() {
		String clip = this.getClipboardText();
		if (clip != null && !clip.isBlank()) {
			String p = clip.trim();

			try {
				RecordableConfig c = RecordableConfig.get();
				c.ffmpegPath = p;
				c.save();
			} catch (Exception var4) {
			}

			FFmpegEncoder.invalidateDetectionCache();
			this.testReport = "Set FFmpeg path to:\n  " + p + "\n\n" + FFmpegEncoder.testFfmpegVerbose(p);
			this.rebuildAndReinit();
		} else {
			this.testReport = "Clipboard is empty.\nCopy your ffmpeg path first, e.g.\n/data/data/com.termux/files/usr/bin/ffmpeg";
			this.rebuildAndReinit();
		}
	}

	private void rebuildLines() {
		this.lines.clear();
		boolean alreadyAvailable = FfmpegBundleManager.isBundledFfmpegAvailable();
		boolean autoSupported = FfmpegBundleManager.isAutoDownloadSupported();
		Platform platform = PlatformUtils.detectPlatform();
		this.addHeader("FFmpeg Setup");
		this.addBlank();
		if (alreadyAvailable) {
			this.addHighlight("✓ FFmpeg is installed and ready.");
			this.addBlank();
			this.addText("Location:");
			String path = FfmpegBundleManager.getBundledFfmpegPath();
			this.addText("  " + truncate(path == null ? "(unknown)" : path, 70));
			this.addBlank();
			this.addText("You can close this screen and start recording.");
		} else {
			this.addText("Record-able needs FFmpeg to encode video. To keep this mod");
			this.addText("Modrinth-friendly we do not bundle the FFmpeg binary.");
			this.addText("You download it once, from an official upstream, and the");
			this.addText("mod stores it inside your Minecraft folder.");
			this.addBlank();
			this.addHighlight("Platform: " + platform.displayName());
			if (autoSupported) {
				this.addText("Source:    " + FfmpegBundleManager.getDownloadSourceDescription());
				this.addText("Size:      " + FfmpegBundleManager.getEstimatedDownloadSize());
				this.addText("Saved to:  " + truncate(FfmpegBundleManager.getBundleDirectory().toString(), 60));
				this.addBlank();
				this.addHighlight("Integrity:");
				switch (platform) {
					case WINDOWS:
						this.addText("  • Downloaded over HTTPS from gyan.dev");
						this.addText("  • SHA-256 verified against gyan.dev's published");
						this.addText("    .sha256 sibling file (best-effort).");
						break;
					case LINUX:
						this.addText("  • Downloaded over HTTPS from johnvansickle.com");
						this.addText("  • MD5 verified against the .md5 sibling file");
						this.addText("    (best-effort).");
						break;
					case MACOS:
						this.addText("  • Downloaded over HTTPS from evermeet.cx");
						this.addText("  • HTTPS host authentication; no sibling hash file");
						this.addText("    is published. The archive is signed upstream.");
				}

				this.addBlank();
				this.addText("Click 'Download FFmpeg' to start. The mod will not");
				this.addText("contact the internet until you do.");
			} else if (platform == Platform.ANDROID) {
				this.addWarning("Auto-download is not supported on Android.");
				this.addText("Exec-mounted, writable storage is rare on Pojav/Zalith/FCL,");
				this.addText("so a downloaded binary often cannot be run.");
				this.addBlank();
				this.addHighlight("Recommended (easiest):");
				this.addText("  1. Install Termux from F-Droid");
				this.addText("     ( https://f-droid.org/packages/com.termux/ )");
				this.addText("  2. In Termux, run:  pkg install ffmpeg");
				this.addText("  3. In Record-able's settings, set");
				this.addText("     ffmpegPath to:");
				this.addText("     /data/data/com.termux/files/usr/bin/ffmpeg");
				this.addBlank();
				this.addHighlight("Manual alternative:");
				this.addText("  Place a static arm64 ffmpeg binary at:");
				this.addText("    " + truncate(FfmpegBundleManager.getBundleDirectory().resolve("ffmpeg").toString(), 64));
				this.addText("  Then chmod +x it. Use 'Open FFmpeg Folder' below.");
			} else {
				this.addWarning("Auto-download not available for this platform.");
				this.addText(FfmpegBundleManager.getManualInstallInstructions());
			}

			Status st = FfmpegBundleManager.getStatus();
			if (st == Status.ERROR) {
				this.addBlank();
				this.addError("Last error: " + (FfmpegBundleManager.getLastError() == null ? "unknown" : FfmpegBundleManager.getLastError()));
				this.addText("If the problem persists, see manual install above.");
			}

			this.appendManualOverrideSection();
		}
	}

	private void appendManualOverrideSection() {
		this.addBlank();
		this.addHeader("Manual override / diagnostics");
		String configured = "";

		try {
			configured = RecordableConfig.get().ffmpegPath;
		} catch (Exception var10) {
		}

		if (configured != null && !configured.isBlank()) {
			this.addHighlight("Current FFmpeg path (settings):");
			this.addText("  " + truncate(configured.trim(), 64));
		} else {
			this.addText("No manual FFmpeg path set.");
		}

		this.addText("• 'Paste FFmpeg Path' - set it from the clipboard.");
		this.addText("• 'Test FFmpeg' - re-probe and show what was tried.");
		this.addText("• 'Copy Termux Cmd' - copies 'pkg install ffmpeg'.");
		if (this.testReport != null && !this.testReport.isBlank()) {
			this.addBlank();
			this.addHeader("Last test result");

			for (String raw : this.testReport.split("\n", -1)) {
				String line = raw.replace("\t", "    ");
				int color = line.contains("[OK]") || line.startsWith("RESULT: FFmpeg FOUND") || line.startsWith("RESULT: OK")
					? -7811960
					: (!line.contains("[FAIL]") && !line.contains("NOT FOUND") && !line.startsWith("RESULT: FAILED") ? -3092272 : -34953);
				if (line.isEmpty()) {
					this.lines.add(new FfmpegDownloadScreen.Line("", color, false));
				} else {
					for (String wrapped : this.wrapToWidth(line, this.textWrapWidth)) {
						this.lines.add(new FfmpegDownloadScreen.Line(wrapped, color, false));
					}
				}
			}
		}
	}

	private void startDownload() {
		if (!FfmpegBundleManager.isDownloading()) {
			if (this.downloadButton != null) {
				this.downloadButton.method_25355(class_2561.method_43470("Downloading…"));
				this.downloadButton.field_22763 = false;
			}

			FfmpegBundleManager.downloadAsync(success -> {
				class_310 mc = class_310.method_1551();
				if (mc != null) {
					mc.execute(() -> {
						this.failureShown = !success;
						this.method_37067();
						this.method_25426();
					});
				}
			});
		}
	}

	private void openBundleFolder() {
		try {
			Files.createDirectories(FfmpegBundleManager.getBundleDirectory());
			class_156.method_668().method_672(FfmpegBundleManager.getBundleDirectory().toFile());
		} catch (Exception var2) {
			RecordableMod.LOGGER.warn("[FfmpegDownloadScreen] Could not open folder: {}", var2.getMessage());
		}
	}

	@Override
	public void onProgress(DownloadProgress progress) {
		this.currentProgress = progress;
	}

	public void method_25394(class_332 context, int mouseX, int mouseY, float delta) {
		this.method_25420(context, mouseX, mouseY, delta);
		int accent = 0xFF000000 | RecordableConfig.get().getMenuAccentColorRgb();
		int left = this.panelLeft - 6;
		int right = this.panelLeft + this.panelWidth + 6;
		context.method_25294(left, this.panelTop - 6, right, this.panelBottom, -804253680);
		context.method_25294(left, this.panelTop - 6, right, this.panelTop - 5, accent);
		context.method_25294(left, this.panelBottom - 1, right, this.panelBottom, -12434878);
		context.method_25294(left, this.panelTop - 6, left + 1, this.panelBottom, -12434878);
		context.method_25294(right - 1, this.panelTop - 6, right, this.panelBottom, -12434878);
		context.method_27534(this.field_22793, this.field_22785, this.field_22789 / 2, this.panelTop, -1);
		int textLeft = this.panelLeft + 14;

		for (int i = 0; i < this.lines.size(); i++) {
			int y = this.panelBodyTop + i * 12 - this.scrollOffset;
			if (y >= this.panelBodyTop - 12 && y <= this.panelBodyBottom - 2) {
				FfmpegDownloadScreen.Line line = (FfmpegDownloadScreen.Line)this.lines.get(i);
				if (!line.text.isEmpty()) {
					String prefix = line.bold ? "§l" : "";
					RenderHelper.drawText(context, this.field_22793, class_2561.method_43470(prefix + line.text), textLeft, y, line.color);
				}
			}
		}

		this.renderScrollbar(context, accent);
		if (FfmpegBundleManager.isDownloading()) {
			int barWidth = this.panelWidth - 36;
			int barHeight = 14;
			int barLeft = this.panelLeft + 18;
			int barTop = this.progressBarTop;
			context.method_25294(barLeft, barTop, barLeft + barWidth, barTop + barHeight, -14671840);
			context.method_25294(barLeft, barTop, barLeft + barWidth, barTop + 1, -10461088);
			context.method_25294(barLeft, barTop + barHeight - 1, barLeft + barWidth, barTop + barHeight, -10461088);
			context.method_25294(barLeft, barTop, barLeft + 1, barTop + barHeight, -10461088);
			context.method_25294(barLeft + barWidth - 1, barTop, barLeft + barWidth, barTop + barHeight, -10461088);
			double frac = this.currentProgress.fraction();
			if (frac > 0.0) {
				int fillWidth = (int)Math.round((double)(barWidth - 2) * Math.min(1.0, Math.max(0.0, frac)));
				context.method_25294(barLeft + 1, barTop + 1, barLeft + 1 + fillWidth, barTop + barHeight - 1, accent);
			}

			String label = (this.currentProgress.phase() == null ? "" : capitalize(this.currentProgress.phase()) + " · ")
				+ this.currentProgress.displayBytes()
				+ (this.currentProgress.totalBytes() > 0L ? " (" + this.currentProgress.displayPercent() + ")" : "");
			RenderHelper.drawText(context, this.field_22793, class_2561.method_43470(label), barLeft, barTop - 11, -3092272);
		}

		super.method_25394(context, mouseX, mouseY, delta);
	}

	private void renderScrollbar(class_332 context, int accent) {
		int viewportHeight = this.panelBodyBottom - this.panelBodyTop;
		int maxScroll = Math.max(0, this.contentHeight - viewportHeight);
		if (maxScroll > 0) {
			int barLeft = this.panelLeft + this.panelWidth - 6 - 2;
			int barRight = barLeft + 6;
			context.method_25294(barLeft, this.panelBodyTop, barRight, this.panelBodyBottom, 1073741824);
			int thumbHeight = Math.max(28, (int)((double)viewportHeight * ((double)viewportHeight / (double)this.contentHeight)));
			int available = viewportHeight - thumbHeight;
			int thumbTop = this.panelBodyTop + (available <= 0 ? 0 : (int)((double)this.scrollOffset / (double)maxScroll * (double)available));
			context.method_25294(barLeft, thumbTop, barRight, thumbTop + thumbHeight, accent);
		}
	}

	public boolean method_25401(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		int maxScroll = Math.max(0, this.contentHeight - (this.panelBodyBottom - this.panelBodyTop));
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

	public boolean method_25404(int keyCode, int scanCode, int modifiers) {
		int viewportHeight = this.panelBodyBottom - this.panelBodyTop;
		int maxScroll = Math.max(0, this.contentHeight - viewportHeight);
		if (maxScroll > 0) {
			switch (keyCode) {
				case 264:
					this.scrollOffset = Math.min(maxScroll, this.scrollOffset + 12);
					return true;
				case 265:
					this.scrollOffset = Math.max(0, this.scrollOffset - 12);
					return true;
				case 266:
					this.scrollOffset = Math.max(0, this.scrollOffset - viewportHeight);
					return true;
				case 267:
					this.scrollOffset = Math.min(maxScroll, this.scrollOffset + viewportHeight);
					return true;
			}
		}

		return super.method_25404(keyCode, scanCode, modifiers);
	}

	public boolean method_25402(double mouseX, double mouseY, int button) {
		if (button == 0 && this.isOverScrollbar(mouseX, mouseY)) {
			this.draggingScrollbar = true;
			this.scrollToMouse(mouseY);
			return true;
		} else {
			if (button == 0 && this.isContentScrollable() && this.isOverBody(mouseX, mouseY)) {
				this.draggingBody = true;
			}

			return super.method_25402(mouseX, mouseY, button);
		}
	}

	public boolean method_25403(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (this.draggingScrollbar && button == 0) {
			this.scrollToMouse(mouseY);
			return true;
		} else if (this.draggingBody && button == 0) {
			this.panBy(deltaY);
			return true;
		} else {
			return super.method_25403(mouseX, mouseY, button, deltaX, deltaY);
		}
	}

	public boolean method_25406(double mouseX, double mouseY, int button) {
		boolean wasScrollbar = this.draggingScrollbar;
		if (button == 0) {
			this.draggingScrollbar = false;
			this.draggingBody = false;
		}

		return wasScrollbar ? true : super.method_25406(mouseX, mouseY, button);
	}

	private boolean isOverScrollbar(double mouseX, double mouseY) {
		int viewportHeight = this.panelBodyBottom - this.panelBodyTop;
		int maxScroll = Math.max(0, this.contentHeight - viewportHeight);
		if (maxScroll <= 0) {
			return false;
		} else {
			int barLeft = this.panelLeft + this.panelWidth - 6 - 2;
			int barRight = barLeft + 6;
			return mouseX >= (double)(barLeft - 8) && mouseX <= (double)(barRight + 6) && mouseY >= (double)this.panelBodyTop && mouseY <= (double)this.panelBodyBottom;
		}
	}

	private boolean isContentScrollable() {
		return this.contentHeight > this.panelBodyBottom - this.panelBodyTop;
	}

	private boolean isOverBody(double mouseX, double mouseY) {
		return mouseX >= (double)this.panelLeft
			&& mouseX <= (double)(this.panelLeft + this.panelWidth)
			&& mouseY >= (double)this.panelBodyTop
			&& mouseY <= (double)this.panelBodyBottom;
	}

	private void panBy(double deltaY) {
		int viewportHeight = this.panelBodyBottom - this.panelBodyTop;
		int maxScroll = Math.max(0, this.contentHeight - viewportHeight);
		if (maxScroll > 0) {
			this.scrollOffset = Math.max(0, Math.min(maxScroll, this.scrollOffset - (int)Math.round(deltaY)));
		}
	}

	private void scrollToMouse(double mouseY) {
		int viewportHeight = this.panelBodyBottom - this.panelBodyTop;
		int maxScroll = Math.max(0, this.contentHeight - viewportHeight);
		if (maxScroll > 0) {
			int thumbHeight = Math.max(28, (int)((double)viewportHeight * ((double)viewportHeight / (double)this.contentHeight)));
			int available = viewportHeight - thumbHeight;
			if (available <= 0) {
				this.scrollOffset = 0;
			} else {
				double rel = (mouseY - (double)this.panelBodyTop - (double)thumbHeight / 2.0) / (double)available;
				rel = Math.max(0.0, Math.min(1.0, rel));
				this.scrollOffset = (int)Math.round(rel * (double)maxScroll);
			}
		}
	}

	public void method_25432() {
		FfmpegBundleManager.removeProgressListener(this);
		super.method_25432();
	}

	public void method_25419() {
		if (this.field_22787 != null) {
			this.field_22787.method_1507(this.parent);
		}
	}

	public boolean method_25421() {
		return false;
	}

	private void addHeader(String text) {
		this.addWrapped(text, -1, true);
	}

	private void addText(String text) {
		this.addWrapped(text, -3092272, false);
	}

	private void addHighlight(String text) {
		this.addWrapped(text, -7811960, false);
	}

	private void addWarning(String text) {
		this.addWrapped(text, -13244, false);
	}

	private void addError(String text) {
		this.addWrapped(text, -34953, false);
	}

	private void addBlank() {
		this.lines.add(new FfmpegDownloadScreen.Line("", -3092272, false));
	}

	private void addWrapped(String text, int color, boolean bold) {
		if (text != null && !text.isEmpty()) {
			for (String paragraph : text.split("\n", -1)) {
				if (paragraph.isEmpty()) {
					this.lines.add(new FfmpegDownloadScreen.Line("", color, bold));
				} else {
					for (String wrapped : this.wrapToWidth(paragraph, this.textWrapWidth)) {
						this.lines.add(new FfmpegDownloadScreen.Line(wrapped, color, bold));
					}
				}
			}
		} else {
			this.lines.add(new FfmpegDownloadScreen.Line("", color, bold));
		}
	}

	private List<String> wrapToWidth(String text, int maxWidth) {
		List<String> out = new ArrayList();
		if (text != null && !text.isEmpty()) {
			if (maxWidth > 8 && this.field_22793.method_1727(text) > maxWidth) {
				int indentCount = 0;

				while (indentCount < text.length() && text.charAt(indentCount) == ' ') {
					indentCount++;
				}

				String indent = text.substring(0, indentCount);
				String contIndent = indent + "  ";
				String[] words = text.substring(indentCount).split(" ");
				String curIndent = indent;
				StringBuilder line = new StringBuilder(indent);
				boolean hasWord = false;

				for (String w : words) {
					String word = w;
					if (!w.isEmpty()) {
						while (this.field_22793.method_1727(curIndent + word) > maxWidth && word.length() > 1) {
							if (hasWord) {
								out.add(line.toString());
								curIndent = contIndent;
								line.setLength(0);
								line.append(contIndent);
								hasWord = false;
							}

							int fit = 1;

							while (fit < word.length() && this.field_22793.method_1727(curIndent + word.substring(0, fit + 1)) <= maxWidth) {
								fit++;
							}

							out.add(curIndent + word.substring(0, fit));
							word = word.substring(fit);
							curIndent = contIndent;
							line.setLength(0);
							line.append(contIndent);
							hasWord = false;
						}

						if (!hasWord) {
							line.setLength(0);
							line.append(curIndent).append(word);
							hasWord = true;
						} else if (this.field_22793.method_1727(line + " " + word) <= maxWidth) {
							line.append(" ").append(word);
						} else {
							out.add(line.toString());
							curIndent = contIndent;
							line.setLength(0);
							line.append(contIndent).append(word);
							hasWord = true;
						}
					}
				}

				if (hasWord) {
					out.add(line.toString());
				}

				if (out.isEmpty()) {
					out.add(text);
				}

				return out;
			} else {
				out.add(text);
				return out;
			}
		} else {
			out.add("");
			return out;
		}
	}

	private static String truncate(String s, int maxLen) {
		if (s == null) {
			return "";
		} else {
			return s.length() <= maxLen ? s : "…" + s.substring(s.length() - maxLen + 1);
		}
	}

	private static String capitalize(String s) {
		return s != null && !s.isEmpty() ? Character.toUpperCase(s.charAt(0)) + s.substring(1) : "";
	}

	private static record Line(String text, int color, boolean bold) {
	}
}
