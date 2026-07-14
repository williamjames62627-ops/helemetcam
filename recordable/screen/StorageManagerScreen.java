package dev.recordable.screen;

import dev.recordable.RecordableConfig;
import dev.recordable.StorageManager;
import dev.recordable.StorageManager.CleanupResult;
import dev.recordable.StorageManager.StorageStats;
import dev.recordable.StorageManager.StoredFile;
import dev.recordable.compat.RenderHelper;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.class_2561;
import net.minecraft.class_332;
import net.minecraft.class_4185;
import net.minecraft.class_437;

public final class StorageManagerScreen extends class_437 {
	private static final int PANEL_COLOR = -804253680;
	private static final int PANEL_BORDER_COLOR = -12434878;
	private static final int TEXT_COLOR = -3092272;
	private static final int HEADER_COLOR = -1;
	private static final int HIGHLIGHT_COLOR = -7811960;
	private static final int WARNING_COLOR = -13244;
	private static final int ROW_HEIGHT = 22;
	private final class_437 parent;
	private int panelLeft;
	private int panelTop;
	private int panelWidth;
	private int panelBottom;
	private int listTop;
	private int listBottom;
	private int scrollOffset;
	private List<StoredFile> files = new ArrayList();
	private StorageStats stats;
	private String statusMessage = "";

	public StorageManagerScreen(class_437 parent) {
		super(class_2561.method_43471("screen.recordable.storage.title"));
		this.parent = parent;
	}

	protected void method_25426() {
		super.method_25426();
		this.panelWidth = Math.max(360, Math.min((int)((double)this.field_22789 * 0.85), 640));
		this.panelLeft = (this.field_22789 - this.panelWidth) / 2;
		this.panelTop = Math.max(8, (int)((double)this.field_22790 * 0.05));
		this.panelBottom = Math.min(this.field_22790 - 8, this.panelTop + Math.max(320, (int)((double)this.field_22790 * 0.88)));
		this.listTop = this.panelTop + 96;
		this.listBottom = this.panelBottom - 40;
		this.scrollOffset = 0;
		this.refreshData();
		this.rebuildWidgets();
	}

	private void refreshData() {
		RecordableConfig config = RecordableConfig.get();
		this.files = StorageManager.listRecordings(config);
		this.stats = StorageManager.computeStats(config);
	}

	private void rebuildWidgets() {
		this.method_37067();
		RecordableConfig config = RecordableConfig.get();
		int btnW = 110;
		int btnH = 20;
		int topBtnY = this.panelTop + 50;
		this.method_37063(class_4185.method_46430(class_2561.method_43471("screen.recordable.storage.clean_now"), button -> {
			CleanupResult result = StorageManager.runCleanup(RecordableConfig.get(), true);
			this.statusMessage = "Removed " + result.filesDeleted() + " file(s), freed " + result.bytesFreedDisplay();
			this.refreshData();
			this.rebuildWidgets();
		}).method_46434(this.panelLeft + 12, topBtnY, btnW, btnH).method_46431());
		this.method_37063(class_4185.method_46430(class_2561.method_43470("Auto-Cleanup: " + (config.autoCleanupEnabled ? "ON" : "OFF")), button -> {
			RecordableConfig c = RecordableConfig.get();
			c.autoCleanupEnabled = !c.autoCleanupEnabled;
			c.save();
			this.rebuildWidgets();
		}).method_46434(this.panelLeft + 12 + btnW + 8, topBtnY, btnW + 20, btnH).method_46431());
		this.method_37063(class_4185.method_46430(class_2561.method_43471("screen.recordable.storage.refresh"), button -> {
			this.refreshData();
			this.rebuildWidgets();
		}).method_46434(this.panelLeft + this.panelWidth - btnW - 12, topBtnY, btnW, btnH).method_46431());
		int rightEdge = this.panelLeft + this.panelWidth - 12;

		for (int i = 0; i < this.files.size(); i++) {
			StoredFile f = (StoredFile)this.files.get(i);
			int rowY = this.listTop + i * 22 - this.scrollOffset;
			if (rowY >= this.listTop - 22 && rowY <= this.listBottom) {
				class_4185 protectBtn = class_4185.method_46430(class_2561.method_43470(f.protectedFlag() ? "Unlock" : "Protect"), button -> {
					StorageManager.toggleProtected(RecordableConfig.get(), f.filename());
					this.refreshData();
					this.rebuildWidgets();
				}).method_46434(rightEdge - 120, rowY, 56, 18).method_46431();
				this.method_37063(protectBtn);
				class_4185 deleteBtn = class_4185.method_46430(class_2561.method_43470("Delete"), button -> {
					if (!f.protectedFlag()) {
						StorageManager.deleteRecording(RecordableConfig.get(), f.path());
						this.statusMessage = "Deleted " + f.filename();
						this.refreshData();
						this.rebuildWidgets();
					} else {
						this.statusMessage = f.filename() + " is protected.";
					}
				}).method_46434(rightEdge - 60, rowY, 56, 18).method_46431();
				deleteBtn.field_22763 = !f.protectedFlag();
				this.method_37063(deleteBtn);
			}
		}

		int backW = 120;
		this.method_37063(
			class_4185.method_46430(class_2561.method_43471("screen.recordable.storage.back"), button -> this.method_25419())
				.method_46434((this.field_22789 - backW) / 2, this.panelBottom - 28, backW, 20)
				.method_46431()
		);
	}

	public boolean method_25401(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		int contentHeight = this.files.size() * 22;
		int viewHeight = this.listBottom - this.listTop;
		int maxScroll = Math.max(0, contentHeight - viewHeight);
		if (maxScroll <= 0) {
			return super.method_25401(mouseX, mouseY, horizontalAmount, verticalAmount);
		} else {
			int delta = (int)Math.round(verticalAmount * -22.0);
			if (delta == 0) {
				delta = verticalAmount > 0.0 ? -22 : 22;
			}

			this.scrollOffset = Math.max(0, Math.min(maxScroll, this.scrollOffset + delta));
			this.rebuildWidgets();
			return true;
		}
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
		if (this.stats != null) {
			String diskLine = "Disk: "
				+ this.stats.diskFreeDisplay()
				+ " free / "
				+ this.stats.diskTotalDisplay()
				+ " total  ("
				+ this.stats.diskUsedPercent()
				+ "% used)";
			int diskColor = this.stats.diskUsedPercent() >= 90 ? -13244 : -7811960;
			RenderHelper.drawText(context, this.field_22793, class_2561.method_43470(diskLine), textLeft, this.panelTop + 18, diskColor);
			String recLine = "Recordings: " + this.stats.recordingCount() + " file(s), " + this.stats.recordingsDisplay();
			RenderHelper.drawText(context, this.field_22793, class_2561.method_43470(recLine), textLeft, this.panelTop + 32, -3092272);
		}

		RenderHelper.drawText(context, this.field_22793, class_2561.method_43470("§lRecordings"), textLeft, this.listTop - 14, -1);
		context.method_44379(this.panelLeft, this.listTop, this.panelLeft + this.panelWidth, this.listBottom);

		for (int i = 0; i < this.files.size(); i++) {
			StoredFile f = (StoredFile)this.files.get(i);
			int rowY = this.listTop + i * 22 - this.scrollOffset;
			if (rowY >= this.listTop - 22 && rowY <= this.listBottom) {
				if (i % 2 == 0) {
					context.method_25294(this.panelLeft + 8, rowY - 2, this.panelLeft + this.panelWidth - 8, rowY + 22 - 4, 822083583);
				}

				String name = f.filename();
				int maxChars = Math.max(10, (this.panelWidth - 280) / 6);
				if (name.length() > maxChars) {
					name = name.substring(0, maxChars - 1) + "…";
				}

				int nameColor = f.protectedFlag() ? -7811960 : -3092272;
				String prefix = f.protectedFlag() ? "\ud83d\udd12 " : "";
				RenderHelper.drawText(context, this.field_22793, class_2561.method_43470(prefix + name), textLeft, rowY + 3, nameColor);
				RenderHelper.drawText(context, this.field_22793, class_2561.method_43470(f.sizeDisplay()), this.panelLeft + this.panelWidth - 200, rowY + 3, -3092272);
			}
		}

		context.method_44380();
		if (this.files.isEmpty()) {
			context.method_27534(this.field_22793, class_2561.method_43470("No recordings found."), this.field_22789 / 2, this.listTop + 10, -3092272);
		}

		if (!this.statusMessage.isEmpty()) {
			context.method_27534(this.field_22793, class_2561.method_43470(this.statusMessage), this.field_22789 / 2, this.panelBottom - 42, -7811960);
		}

		super.method_25394(context, mouseX, mouseY, delta);
	}

	public void method_25419() {
		if (this.field_22787 != null) {
			this.field_22787.method_1507(this.parent);
		}
	}
}
