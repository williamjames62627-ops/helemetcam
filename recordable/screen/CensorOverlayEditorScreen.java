package dev.recordable.screen;

import dev.recordable.CensorRegion;
import dev.recordable.RecordableConfig;
import dev.recordable.CensorRegion.Style;
import dev.recordable.compat.RenderHelper;
import dev.recordable.theme.ThemedButton;
import java.util.List;
import java.util.Locale;
import net.minecraft.class_2561;
import net.minecraft.class_327;
import net.minecraft.class_332;
import net.minecraft.class_437;

public final class CensorOverlayEditorScreen extends class_437 {
	private static final int WIDGET_HEIGHT = 20;
	private static final double MIN_SIZE = 0.03;
	private static final int HANDLE = 7;
	private final class_437 parent;
	private int selected = -1;
	private int dragMode = 0;
	private double pressFx;
	private double pressFy;
	private double origX;
	private double origY;

	public CensorOverlayEditorScreen(class_437 parent) {
		super(class_2561.method_43470("Censor Editor"));
		this.parent = parent;
	}

	private List<CensorRegion> regions() {
		return RecordableConfig.get().censorRegions;
	}

	protected void method_25426() {
		super.method_25426();
		int bw = 96;
		int gap = 6;
		int total = bw * 4 + gap * 3;
		int x = (this.field_22789 - total) / 2;
		int y = this.field_22790 - 28;
		this.method_37063(ThemedButton.create(x, y, bw, 20, class_2561.method_43470("Add Bar"), b -> this.addRegion()));
		x += bw + gap;
		this.method_37063(ThemedButton.create(x, y, bw, 20, class_2561.method_43470("Remove"), b -> this.removeSelected()));
		x += bw + gap;
		this.method_37063(ThemedButton.create(x, y, bw, 20, class_2561.method_43470("Clear All"), b -> {
			this.regions().clear();
			this.selected = -1;
			RecordableConfig.get().save();
		}));
		x += bw + gap;
		this.method_37063(ThemedButton.create(x, y, bw, 20, class_2561.method_43470("Done"), b -> this.method_25419()));
	}

	private void addRegion() {
		RecordableConfig config = RecordableConfig.get();
		CensorRegion r = new CensorRegion(0.4, 0.45, 0.2, 0.08, parseStyle(config.streamerDefaultCensorStyle), "Censor");
		this.regions().add(r);
		this.selected = this.regions().size() - 1;
		config.save();
	}

	private void removeSelected() {
		if (this.selected >= 0 && this.selected < this.regions().size()) {
			this.regions().remove(this.selected);
			this.selected = -1;
			RecordableConfig.get().save();
		}
	}

	private static Style parseStyle(String s) {
		if (s != null) {
			try {
				return Style.valueOf(s.trim().toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException var2) {
			}
		}

		return Style.SOLID;
	}

	private static double clamp(double v, double lo, double hi) {
		return v < lo ? lo : (v > hi ? hi : v);
	}

	public boolean method_25402(double mouseX, double mouseY, int button) {
		return super.method_25402(mouseX, mouseY, button) ? true : this.canvasPress((int)mouseX, (int)mouseY, button);
	}

	public boolean method_25403(double mouseX, double mouseY, int button, double dx, double dy) {
		if (this.dragMode != 0) {
			this.canvasDrag((int)mouseX, (int)mouseY);
			return true;
		} else {
			return super.method_25403(mouseX, mouseY, button, dx, dy);
		}
	}

	public boolean method_25406(double mouseX, double mouseY, int button) {
		if (this.dragMode != 0) {
			this.canvasRelease();
			return true;
		} else {
			return super.method_25406(mouseX, mouseY, button);
		}
	}

	private boolean canvasPress(int mx, int my, int button) {
		if (button != 0) {
			return false;
		} else {
			double fx = (double)mx / (double)this.field_22789;
			double fy = (double)my / (double)this.field_22790;
			List<CensorRegion> rs = this.regions();
			int prevSelected = this.selected;
			if (this.selected >= 0 && this.selected < rs.size()) {
				CensorRegion r = (CensorRegion)rs.get(this.selected);
				int hx = (int)((r.x + r.width) * (double)this.field_22789);
				int hy = (int)((r.y + r.height) * (double)this.field_22790);
				if (Math.abs(mx - hx) <= 7 && Math.abs(my - hy) <= 7) {
					this.dragMode = 2;
					this.pressFx = fx;
					this.pressFy = fy;
					this.origX = r.x;
					this.origY = r.y;
					return true;
				}
			}

			for (int i = rs.size() - 1; i >= 0; i--) {
				CensorRegion r = (CensorRegion)rs.get(i);
				if (fx >= r.x && fx <= r.x + r.width && fy >= r.y && fy <= r.y + r.height) {
					this.selected = i;
					this.dragMode = 1;
					this.pressFx = fx;
					this.pressFy = fy;
					this.origX = r.x;
					this.origY = r.y;
					return true;
				}
			}

			RecordableConfig config = RecordableConfig.get();
			CensorRegion nr = new CensorRegion(clamp(fx, 0.0, 0.97), clamp(fy, 0.0, 0.97), 0.03, 0.03, parseStyle(config.streamerDefaultCensorStyle), "Censor");
			rs.add(nr);
			this.selected = rs.size() - 1;
			this.dragMode = 3;
			this.pressFx = nr.x;
			this.pressFy = nr.y;
			return true;
		}
	}

	private void canvasDrag(int mx, int my) {
		if (this.selected >= 0 && this.selected < this.regions().size()) {
			CensorRegion r = (CensorRegion)this.regions().get(this.selected);
			double fx = clamp((double)mx / (double)this.field_22789, 0.0, 1.0);
			double fy = clamp((double)my / (double)this.field_22790, 0.0, 1.0);
			if (this.dragMode == 1) {
				double dx = fx - this.pressFx;
				double dy = fy - this.pressFy;
				r.x = clamp(this.origX + dx, 0.0, 1.0 - r.width);
				r.y = clamp(this.origY + dy, 0.0, 1.0 - r.height);
			} else if (this.dragMode == 2) {
				r.width = clamp(fx - r.x, 0.03, 1.0 - r.x);
				r.height = clamp(fy - r.y, 0.03, 1.0 - r.y);
			} else if (this.dragMode == 3) {
				double x0 = Math.min(this.pressFx, fx);
				double y0 = Math.min(this.pressFy, fy);
				double x1 = Math.max(this.pressFx, fx);
				double y1 = Math.max(this.pressFy, fy);
				r.x = x0;
				r.y = y0;
				r.width = Math.max(0.03, x1 - x0);
				r.height = Math.max(0.03, y1 - y0);
			}
		}
	}

	private void canvasRelease() {
		if (this.selected >= 0 && this.selected < this.regions().size()) {
			((CensorRegion)this.regions().get(this.selected)).sanitize();
		}

		this.dragMode = 0;
		RecordableConfig.get().save();
	}

	public boolean method_25404(int keyCode, int scanCode, int modifiers) {
		if (keyCode != 261 && keyCode != 259) {
			return super.method_25404(keyCode, scanCode, modifiers);
		} else {
			this.removeSelected();
			return true;
		}
	}

	public void method_25394(class_332 context, int mouseX, int mouseY, float delta) {
		class_327 tr = this.field_22793;
		RecordableConfig config = RecordableConfig.get();
		if (config != null) {
			List<CensorRegion> rs = this.regions();

			for (int i = 0; i < rs.size(); i++) {
				CensorRegion r = (CensorRegion)rs.get(i);
				int x0 = (int)(r.x * (double)this.field_22789);
				int y0 = (int)(r.y * (double)this.field_22790);
				int x1 = (int)((r.x + r.width) * (double)this.field_22789);
				int y1 = (int)((r.y + r.height) * (double)this.field_22790);
				if (r.style == Style.GRADIENT) {
					context.method_25296(x0, y0, x1, y1, -872415232 | r.color & 16777215, -872415232 | r.colorEnd & 16777215);
				} else {
					context.method_25294(x0, y0, x1, y1, -872415232 | r.color & 16777215);
				}

				int bc = i == this.selected ? -12255420 : -1;
				context.method_25294(x0, y0, x1, y0 + 1, bc);
				context.method_25294(x0, y1 - 1, x1, y1, bc);
				context.method_25294(x0, y0, x0 + 1, y1, bc);
				context.method_25294(x1 - 1, y0, x1, y1, bc);
				String tag = r.showLabel && r.label != null && !r.label.isBlank() ? r.label : r.style.name();
				RenderHelper.drawText(context, tr, class_2561.method_43470("● " + tag), x0 + 3, y0 + 3, -1);
				if (i == this.selected) {
					context.method_25294(x1 - 7, y1 - 7, x1, y1, -30652);
				}
			}

			String info = "Drag to move  -  drag the orange corner to stretch  -  click empty space to add  -  Delete removes selected";
			context.method_27534(tr, class_2561.method_43470(info), this.field_22789 / 2, 8, -1);
			String count = rs.isEmpty() ? "No censor bars yet" : "Bars: " + rs.size();
			context.method_27534(tr, class_2561.method_43470(count), this.field_22789 / 2, 20, -5197648);
			if (!config.streamerModeEnabled) {
				context.method_27534(tr, class_2561.method_43470("Streamer Mode is OFF - bars are saved but not shown in-game"), this.field_22789 / 2, 32, -21931);
			}
		}

		super.method_25394(context, mouseX, mouseY, delta);
	}

	public void method_25420(class_332 context, int mouseX, int mouseY, float delta) {
		if (this.field_22787 == null || this.field_22787.field_1687 == null) {
			super.method_25420(context, mouseX, mouseY, delta);
		}
	}

	public boolean method_25421() {
		return false;
	}

	public void method_25419() {
		if (this.field_22787 != null) {
			this.field_22787.method_1507(this.parent);
		}
	}
}
