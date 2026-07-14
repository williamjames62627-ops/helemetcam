package dev.recordable.screen;

import dev.recordable.CaptureDiagnostics;
import dev.recordable.RecordableConfig;
import dev.recordable.RecordingManager;
import dev.recordable.ScreenCapture;
import dev.recordable.CaptureDiagnostics.Check;
import dev.recordable.CaptureDiagnostics.Inputs;
import dev.recordable.CaptureDiagnostics.Status;
import dev.recordable.compat.RenderHelper;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_4185;
import net.minecraft.class_437;

public final class CaptureDiagnosticsScreen extends class_437 {
	private static final int PANEL_COLOR = -804253680;
	private static final int PANEL_BORDER_COLOR = -12434878;
	private static final int TEXT_COLOR = -3092272;
	private static final int OK_COLOR = -7811960;
	private static final int WARN_COLOR = -13244;
	private static final int FAIL_COLOR = -40864;
	private static final int INFO_COLOR = -4668208;
	private final class_437 parent;
	private int panelLeft;
	private int panelTop;
	private int panelWidth;
	private int panelBottom;
	private int scrollOffset;
	private int contentHeight;
	private int bodyTop;
	private int bodyBottom;
	private final List<CaptureDiagnosticsScreen.DiagLine> lines = new ArrayList();

	public CaptureDiagnosticsScreen(class_437 parent) {
		super(class_2561.method_43470("Capture Diagnostics"));
		this.parent = parent;
	}

	protected void method_25426() {
		super.method_25426();
		this.scrollOffset = 0;
		this.panelWidth = Math.max(340, Math.min((int)((double)this.field_22789 * 0.8), 600));
		this.panelLeft = (this.field_22789 - this.panelWidth) / 2;
		this.panelTop = Math.max(8, (int)((double)this.field_22790 * 0.05));
		this.panelBottom = Math.min(this.field_22790 - 8, this.panelTop + Math.max(300, (int)((double)this.field_22790 * 0.88)));
		this.bodyTop = this.panelTop + 38;
		this.bodyBottom = this.panelBottom - 34;
		RecordingManager.getInstance().requestCaptureSelfTest();
		int buttonWidth = 150;
		int gap = 10;
		int totalWidth = buttonWidth + gap + 110;
		int startX = (this.field_22789 - totalWidth) / 2;
		int buttonY = this.panelBottom - 28;
		this.method_37063(
			class_4185.method_46430(class_2561.method_43470("Run Test Again"), button -> RecordingManager.getInstance().requestCaptureSelfTest())
				.method_46434(startX, buttonY, buttonWidth, 20)
				.method_46431()
		);
		this.method_37063(
			class_4185.method_46430(class_2561.method_43470("Back"), button -> this.method_25419())
				.method_46434(startX + buttonWidth + gap, buttonY, 110, 20)
				.method_46431()
		);
	}

	private void rebuildLines() {
		this.lines.clear();
		class_310 client = class_310.method_1551();
		int winW = 0;
		int winH = 0;
		int guiScale = 0;
		if (client != null && client.method_22683() != null) {
			winW = client.method_22683().method_4489();
			winH = client.method_22683().method_4506();

			try {
				guiScale = (int)client.method_22683().method_4495();
			} catch (Throwable var18) {
				guiScale = 0;
			}
		}

		int[] rt = ScreenCapture.currentRenderTargetSize();
		int rtW = rt != null ? rt[0] : -1;
		int rtH = rt != null ? rt[1] : -1;
		RecordingManager manager = RecordingManager.getInstance();
		Inputs inputs = new Inputs(winW, winH, rtW, rtH, guiScale, manager.isActiveOrStopping(), manager.getLiveCaptureStats(), manager.getCaptureSelfTestResult());
		List<Check> checks = CaptureDiagnostics.buildReport(inputs);
		Status verdict = CaptureDiagnostics.overallVerdict(checks);
		this.addLine(CaptureDiagnostics.verdictSummary(verdict), colorFor(verdict), true);
		this.addBlank();
		int detailWidth = this.panelWidth - 40;

		for (Check check : checks) {
			int color = colorFor(check.status());
			this.addLine(statusTag(check.status()) + " " + check.label(), color, true);

			for (String wrapped : this.wrap(check.detail(), detailWidth)) {
				this.addLine("   " + wrapped, -3092272, false);
			}

			this.addBlank();
		}

		this.contentHeight = this.lines.size() * 12 + 10;
	}

	private void addLine(String text, int color, boolean bold) {
		this.lines.add(new CaptureDiagnosticsScreen.DiagLine(text, color, bold));
	}

	private void addBlank() {
		this.lines.add(new CaptureDiagnosticsScreen.DiagLine("", -3092272, false));
	}

	private static String statusTag(Status status) {
		return switch (status) {
			case OK -> "[OK]";
			case WARN -> "[!]";
			case FAIL -> "[X]";
			default -> "[i]";
		};
	}

	private static int colorFor(Status status) {
		return switch (status) {
			case OK -> -7811960;
			case WARN -> -13244;
			case FAIL -> -40864;
			default -> -4668208;
		};
	}

	private List<String> wrap(String text, int maxWidth) {
		List<String> out = new ArrayList();
		if (text != null && !text.isEmpty()) {
			StringBuilder current = new StringBuilder();

			for (String word : text.split(" ")) {
				String candidate = current.length() == 0 ? word : current + " " + word;
				if (this.field_22793.method_1727(candidate) > maxWidth && current.length() > 0) {
					out.add(current.toString());
					current = new StringBuilder(word);
				} else {
					current = new StringBuilder(candidate);
				}
			}

			if (current.length() > 0) {
				out.add(current.toString());
			}

			return out;
		} else {
			return out;
		}
	}

	public boolean method_25401(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		int maxScroll = Math.max(0, this.contentHeight - (this.bodyBottom - this.bodyTop));
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

	public void method_25394(class_332 context, int mouseX, int mouseY, float delta) {
		this.method_25420(context, mouseX, mouseY, delta);
		this.rebuildLines();
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
		int clipTop = this.bodyTop;
		int clipBottom = this.bodyBottom;
		context.method_44379(left + 1, clipTop, right - 1, clipBottom);

		for (int i = 0; i < this.lines.size(); i++) {
			int y = this.bodyTop + i * 12 - this.scrollOffset;
			if (y >= this.bodyTop - 12 && y <= this.bodyBottom + 2) {
				CaptureDiagnosticsScreen.DiagLine line = (CaptureDiagnosticsScreen.DiagLine)this.lines.get(i);
				if (!line.text.isEmpty()) {
					String text = line.bold ? "§l" + line.text : line.text;
					RenderHelper.drawText(context, this.field_22793, class_2561.method_43470(text), textLeft, y, line.color);
				}
			}
		}

		context.method_44380();
		super.method_25394(context, mouseX, mouseY, delta);
	}

	public void method_25419() {
		if (this.field_22787 != null) {
			this.field_22787.method_1507(this.parent);
		}
	}

	private static record DiagLine(String text, int color, boolean bold) {
	}
}
