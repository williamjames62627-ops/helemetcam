package dev.recordable.screen;

import dev.recordable.FfmpegBundleManager;
import dev.recordable.RecordableConfig;
import dev.recordable.theme.ThemedButton;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.class_2561;
import net.minecraft.class_332;
import net.minecraft.class_437;

public final class FfmpegWelcomeScreen extends class_437 {
	private static final int PANEL_WIDTH = 500;
	private static final int PANEL_COLOR = -535818224;
	private static final int BORDER_COLOR = -12303292;
	private static final int TITLE_COLOR = -1;
	private static final int TEXT_COLOR = -3355444;
	private static final int HIGHLIGHT_COLOR = -7798904;
	private final class_437 parent;
	private int panelX;
	private int panelY;
	private int panelHeight;

	public FfmpegWelcomeScreen(class_437 parent) {
		super(class_2561.method_43470("FFmpeg Setup Required"));
		this.parent = parent;
	}

	protected void method_25426() {
		super.method_25426();
		this.panelX = (this.field_22789 - 500) / 2;
		this.panelHeight = 240;
		this.panelY = (this.field_22790 - this.panelHeight) / 2;
		int buttonWidth = 140;
		int buttonHeight = 20;
		int gap = 10;
		int buttonY = this.panelY + this.panelHeight - buttonHeight - 16;
		boolean autoSupported = FfmpegBundleManager.isAutoDownloadSupported();
		if (autoSupported) {
			int totalWidth = buttonWidth * 2 + gap;
			int startX = (this.field_22789 - totalWidth) / 2;
			this.method_37063(ThemedButton.create(startX, buttonY, buttonWidth, buttonHeight, class_2561.method_43470("Download FFmpeg"), button -> {
				RecordableConfig.get().ffmpegFirstRunShown = true;
				RecordableConfig.get().save();
				if (this.field_22787 != null) {
					this.field_22787.method_1507(new FfmpegDownloadScreen(this.parent));
				}
			}));
			this.method_37063(
				ThemedButton.create(startX + buttonWidth + gap, buttonY, buttonWidth, buttonHeight, class_2561.method_43470("Dismiss"), button -> this.dismiss())
			);
		} else {
			int centerX = (this.field_22789 - buttonWidth) / 2;
			this.method_37063(ThemedButton.create(centerX, buttonY, buttonWidth, buttonHeight, class_2561.method_43470("Dismiss"), button -> this.dismiss()));
		}
	}

	private void dismiss() {
		RecordableConfig.get().ffmpegFirstRunShown = true;
		RecordableConfig.get().save();
		if (this.field_22787 != null) {
			this.field_22787.method_1507(this.parent);
		}
	}

	public void method_25394(class_332 context, int mouseX, int mouseY, float delta) {
		super.method_25394(context, mouseX, mouseY, delta);
		context.method_25294(this.panelX, this.panelY, this.panelX + 500, this.panelY + this.panelHeight, -535818224);
		context.method_25294(this.panelX, this.panelY, this.panelX + 500, this.panelY + 1, -12303292);
		context.method_25294(this.panelX, this.panelY + this.panelHeight - 1, this.panelX + 500, this.panelY + this.panelHeight, -12303292);
		context.method_25294(this.panelX, this.panelY, this.panelX + 1, this.panelY + this.panelHeight, -12303292);
		context.method_25294(this.panelX + 500 - 1, this.panelY, this.panelX + 500, this.panelY + this.panelHeight, -12303292);
		context.method_27534(this.field_22793, class_2561.method_43470("FFmpeg Setup Required"), this.field_22789 / 2, this.panelY + 12, -1);
		int textX = this.panelX + 20;
		int textY = this.panelY + 36;
		int lineHeight = 12;
		int wrapWidth = 460;
		boolean autoSupported = FfmpegBundleManager.isAutoDownloadSupported();
		String[] lines;
		if (autoSupported) {
			lines = new String[]{
				"Record-able requires FFmpeg to encode video and audio.",
				"",
				"FFmpeg was not found on your system. Without it, recordings",
				"will be black and unusable.",
				"",
				"Click \"Download FFmpeg\" to automatically download and install",
				"FFmpeg from a trusted source:",
				FfmpegBundleManager.getDownloadSourceDescription(),
				"",
				"Download size: " + FfmpegBundleManager.getEstimatedDownloadSize(),
				"",
				"Or click \"Dismiss\" to set it up manually later."
			};
		} else {
			lines = new String[]{
				"Record-able requires FFmpeg to encode video and audio.",
				"",
				"FFmpeg was not found on your system. Without it, recordings",
				"will be black and unusable.",
				"",
				"Auto-download is not available for your platform.",
				"",
				"Manual installation instructions:",
				FfmpegBundleManager.getManualInstallInstructions()
			};
		}

		for (String line : lines) {
			if (line.isEmpty()) {
				textY += lineHeight / 2;
			} else {
				for (String wrappedLine : this.wrapText(line, wrapWidth)) {
					int color = !wrappedLine.contains("trusted source") && !wrappedLine.contains("Download size") ? -3355444 : -7798904;
					context.method_51439(this.field_22793, class_2561.method_43470(wrappedLine), textX, textY, color, false);
					textY += lineHeight;
				}
			}
		}
	}

	private List<String> wrapText(String text, int maxWidth) {
		List<String> result = new ArrayList();
		String[] words = text.split(" ");
		StringBuilder line = new StringBuilder();

		for (String word : words) {
			String testLine = line.length() == 0 ? word : line + " " + word;
			if (this.field_22793.method_1727(testLine) <= maxWidth) {
				if (line.length() > 0) {
					line.append(" ");
				}

				line.append(word);
			} else if (line.length() > 0) {
				result.add(line.toString());
				line = new StringBuilder(word);
			} else {
				result.add(word);
			}
		}

		if (line.length() > 0) {
			result.add(line.toString());
		}

		return result;
	}

	public void method_25420(class_332 context, int mouseX, int mouseY, float delta) {
		this.method_52752(context);
	}

	public boolean method_25421() {
		return true;
	}

	public void method_25419() {
		this.dismiss();
	}
}
