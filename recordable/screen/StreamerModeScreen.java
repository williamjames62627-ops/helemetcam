package dev.recordable.screen;

import dev.recordable.CensorRegion;
import dev.recordable.RecordableConfig;
import dev.recordable.CensorRegion.GradientDirection;
import dev.recordable.CensorRegion.Style;
import dev.recordable.compat.RenderHelper;
import dev.recordable.theme.CycleButton;
import dev.recordable.theme.ThemedButton;
import dev.recordable.theme.ThemedPanel;
import dev.recordable.theme.ThemedToggle;
import java.util.List;
import java.util.Locale;
import net.minecraft.class_2561;
import net.minecraft.class_327;
import net.minecraft.class_332;
import net.minecraft.class_342;
import net.minecraft.class_4185;
import net.minecraft.class_437;
import net.minecraft.class_7919;

public final class StreamerModeScreen extends class_437 {
	private static final int WIDGET_HEIGHT = 20;
	private static final int ROW_SPACING = 22;
	private static final int PANEL_W = 200;
	private static final double MIN_SIZE = 0.03;
	private final class_437 parent;
	private int canvasX;
	private int canvasY;
	private int canvasW;
	private int canvasH;
	private int selected = -1;
	private int dragMode = 0;
	private double pressFx;
	private double pressFy;
	private double origX;
	private double origY;
	private double origW;
	private double origH;

	public StreamerModeScreen(class_437 parent) {
		super(class_2561.method_43471("screen.recordable.streamer.title"));
		this.parent = parent;
	}

	private List<CensorRegion> regions() {
		return RecordableConfig.get().censorRegions;
	}

	private CensorRegion selectedRegion() {
		List<CensorRegion> rs = this.regions();
		return this.selected >= 0 && this.selected < rs.size() ? (CensorRegion)rs.get(this.selected) : null;
	}

	protected void method_25426() {
		super.method_25426();
		this.computeCanvas();
		this.rebuildWidgets();
	}

	private void computeCanvas() {
		int areaX = 212;
		int areaW = this.field_22789 - areaX - 16;
		int areaY = 40;
		int areaH = this.field_22790 - areaY - 16;
		int w = areaW;
		int h = areaW * 9 / 16;
		if (h > areaH) {
			h = areaH;
			w = areaH * 16 / 9;
		}

		this.canvasW = Math.max(80, w);
		this.canvasH = Math.max(45, h);
		this.canvasX = areaX + (areaW - this.canvasW) / 2;
		this.canvasY = areaY + (areaH - this.canvasH) / 2;
	}

	private void rebuildWidgets() {
		this.method_37067();
		RecordableConfig config = RecordableConfig.get();
		if (config == null) {
			this.method_25419();
		} else {
			int wx = 14;
			int ww = 176;
			int y = 32;
			this.method_37063(ThemedToggle.create(wx, y, ww, 20, "Streamer Mode", config.streamerModeEnabled, v -> {
				config.streamerModeEnabled = v;
				config.save();
			}));
			y += 22;
			this.method_37063(ThemedToggle.create(wx, y, ww, 20, "Show Preview", config.streamerShowCensorPreview, v -> {
				config.streamerShowCensorPreview = v;
				config.save();
			}));
			y += 22;
			class_4185 bakeInOverlayToggle = ThemedToggle.create(wx, y, ww, 20, "Bake in Overlay", config.bakeInOverlay, v -> {
				config.bakeInOverlay = v;
				config.save();
			});
			bakeInOverlayToggle.method_47400(
				class_7919.method_47407(
					class_2561.method_43470(
						"Controls whether your Streamer Mode censor blocks are baked into the recording.\n\nOFF (default): the recording stays clean (no censor in the saved video). Instead, the censor blocks appear as a live on-screen overlay (like a watermark) that obstructs scoreboards, coordinates, GUI elements and inventories. Regions are layered (they stack), and you can show/hide the overlay with the \"Toggle Censor Overlay\" hotkey (set it in Options > Controls).\n\nON: the censor is baked into your recording. It may also appear on your live screen (controlled by Show Preview)."
					)
				)
			);
			this.method_37063(bakeInOverlayToggle);
			y += 22;
			this.method_37063(ThemedButton.create(wx, y, ww, 20, class_2561.method_43470("Add Region"), b -> {
				this.addRegion();
				this.rebuildWidgets();
			}));
			y += 22;
			this.method_37063(
				ThemedButton.create(wx, y, ww, 20, class_2561.method_43470("Edit On Screen (Live)"), b -> this.field_22787.method_1507(new CensorOverlayEditorScreen(this)))
			);
			y += 22;
			CensorRegion sel = this.selectedRegion();
			if (sel == null) {
				this.method_37063(ThemedButton.create(wx, y, ww, 20, class_2561.method_43470("Clear All"), b -> {
					this.regions().clear();
					this.selected = -1;
					config.save();
					this.rebuildWidgets();
				}));
				y += 26;
			} else {
				this.method_37063(CycleButton.create(wx, y, ww, 20, class_2561.method_43470("Style: " + sel.style.name()), b -> {
					sel.style = nextStyle(sel.style);
					config.save();
					this.rebuildWidgets();
				}, b -> {
					sel.style = prevStyle(sel.style);
					config.save();
					this.rebuildWidgets();
				}));
				y += 22;
				this.method_37063(new ColorPickerWidget(this.field_22793, wx, y, ww, 20, class_2561.method_43470("Color"), toHex(sel.color), hex -> {
					sel.color = fromHex(hex);
					config.save();
				}));
				y += 22;
				if (sel.style == Style.GRADIENT) {
					this.method_37063(new ColorPickerWidget(this.field_22793, wx, y, ww, 20, class_2561.method_43470("Color 2"), toHex(sel.colorEnd), hex -> {
						sel.colorEnd = fromHex(hex);
						config.save();
					}));
					y += 22;
					this.method_37063(CycleButton.create(wx, y, ww, 20, class_2561.method_43470("Gradient: " + sel.gradientDirection.name()), b -> {
						sel.gradientDirection = nextDir(sel.gradientDirection);
						config.save();
						b.method_25355(class_2561.method_43470("Gradient: " + sel.gradientDirection.name()));
					}, b -> {
						sel.gradientDirection = prevDir(sel.gradientDirection);
						config.save();
						b.method_25355(class_2561.method_43470("Gradient: " + sel.gradientDirection.name()));
					}));
					y += 22;
				}

				this.method_37063(ThemedToggle.create(wx, y, ww, 20, "Show Text", sel.showLabel, v -> {
					sel.showLabel = v;
					config.save();
					this.rebuildWidgets();
				}));
				y += 22;
				class_342 labelField = new class_342(this.field_22793, wx, y, ww, 18, class_2561.method_43470("Label"));
				labelField.method_1880(40);
				labelField.method_1852(sel.label == null ? "" : sel.label);
				labelField.method_1863(v -> {
					sel.label = v != null && !v.isBlank() ? v : "Censor";
					config.save();
				});
				this.method_37063(labelField);
				y += 22;
				if (sel.showLabel) {
					this.method_37063(new ColorPickerWidget(this.field_22793, wx, y, ww, 20, class_2561.method_43470("Text Color"), toHex(sel.textColor), hex -> {
						sel.textColor = fromHex(hex);
						config.save();
					}));
					y += 22;
				}

				this.method_37063(ThemedButton.create(wx, y, ww, 20, class_2561.method_43470("Remove Selected"), b -> {
					this.removeSelected();
					this.rebuildWidgets();
				}));
				y += 26;
			}

			this.method_37063(ThemedButton.create(wx, this.field_22790 - 28, ww, 20, class_2561.method_43470("Done"), b -> this.method_25419()));
		}
	}

	private void addRegion() {
		RecordableConfig config = RecordableConfig.get();
		CensorRegion r = new CensorRegion(0.375, 0.45, 0.25, 0.1, parseStyle(config.streamerDefaultCensorStyle), "Censor");
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

	private static Style nextStyle(Style s) {
		Style[] all = Style.values();
		return all[(s.ordinal() + 1) % all.length];
	}

	private static GradientDirection nextDir(GradientDirection d) {
		GradientDirection[] all = GradientDirection.values();
		return all[(d.ordinal() + 1) % all.length];
	}

	private static Style prevStyle(Style s) {
		Style[] all = Style.values();
		return all[(s.ordinal() - 1 + all.length) % all.length];
	}

	private static GradientDirection prevDir(GradientDirection d) {
		GradientDirection[] all = GradientDirection.values();
		return all[(d.ordinal() - 1 + all.length) % all.length];
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

	private static String toHex(int rgb) {
		return String.format(Locale.ROOT, "#%06X", rgb & 16777215);
	}

	private static int fromHex(String s) {
		try {
			String t = s != null && s.startsWith("#") ? s.substring(1) : s;
			return Integer.parseInt(t, 16) & 16777215;
		} catch (Throwable var2) {
			return 0;
		}
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
		} else if (mx >= this.canvasX && mx <= this.canvasX + this.canvasW && my >= this.canvasY && my <= this.canvasY + this.canvasH) {
			double fx = (double)(mx - this.canvasX) / (double)this.canvasW;
			double fy = (double)(my - this.canvasY) / (double)this.canvasH;
			List<CensorRegion> rs = this.regions();
			int prevSelected = this.selected;
			if (this.selected >= 0 && this.selected < rs.size()) {
				CensorRegion r = (CensorRegion)rs.get(this.selected);
				int hx = this.canvasX + (int)((r.x + r.width) * (double)this.canvasW);
				int hy = this.canvasY + (int)((r.y + r.height) * (double)this.canvasH);
				if (Math.abs(mx - hx) <= 6 && Math.abs(my - hy) <= 6) {
					this.dragMode = 2;
					this.pressFx = fx;
					this.pressFy = fy;
					this.origX = r.x;
					this.origY = r.y;
					this.origW = r.width;
					this.origH = r.height;
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
					this.origW = r.width;
					this.origH = r.height;
					if (this.selected != prevSelected) {
						this.rebuildWidgets();
					}

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
			this.origX = nr.x;
			this.origY = nr.y;
			this.origW = 0.03;
			this.origH = 0.03;
			this.rebuildWidgets();
			return true;
		} else {
			return false;
		}
	}

	private void canvasDrag(int mx, int my) {
		if (this.selected >= 0 && this.selected < this.regions().size()) {
			CensorRegion r = (CensorRegion)this.regions().get(this.selected);
			double fx = clamp((double)(mx - this.canvasX) / (double)this.canvasW, 0.0, 1.0);
			double fy = clamp((double)(my - this.canvasY) / (double)this.canvasH, 0.0, 1.0);
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

	public void method_25394(class_332 context, int mouseX, int mouseY, float delta) {
		super.method_25394(context, mouseX, mouseY, delta);
	}

	public void method_25420(class_332 context, int mouseX, int mouseY, float delta) {
		super.method_25420(context, mouseX, mouseY, delta);
		context.method_51452();
		class_327 tr = this.field_22793;
		RecordableConfig config = RecordableConfig.get();
		if (config != null) {
			ThemedPanel.drawPanel(context, 6, 6, 200, this.field_22790 - 6);
			context.method_27534(tr, this.field_22785, 100, 14, -1);
			context.method_25294(this.canvasX - 2, this.canvasY - 2, this.canvasX + this.canvasW + 2, this.canvasY + this.canvasH + 2, -14013910);
			context.method_25294(this.canvasX, this.canvasY, this.canvasX + this.canvasW, this.canvasY + this.canvasH, -15724524);
			context.method_25294(this.canvasX + this.canvasW / 3, this.canvasY, this.canvasX + this.canvasW / 3 + 1, this.canvasY + this.canvasH, 587202559);
			context.method_25294(this.canvasX + this.canvasW * 2 / 3, this.canvasY, this.canvasX + this.canvasW * 2 / 3 + 1, this.canvasY + this.canvasH, 587202559);
			context.method_25294(this.canvasX, this.canvasY + this.canvasH / 3, this.canvasX + this.canvasW, this.canvasY + this.canvasH / 3 + 1, 587202559);
			context.method_25294(this.canvasX, this.canvasY + this.canvasH * 2 / 3, this.canvasX + this.canvasW, this.canvasY + this.canvasH * 2 / 3 + 1, 587202559);
			List<CensorRegion> rs = this.regions();

			for (int i = 0; i < rs.size(); i++) {
				CensorRegion r = (CensorRegion)rs.get(i);
				int x0 = this.canvasX + (int)(r.x * (double)this.canvasW);
				int y0 = this.canvasY + (int)(r.y * (double)this.canvasH);
				int x1 = this.canvasX + (int)((r.x + r.width) * (double)this.canvasW);
				int y1 = this.canvasY + (int)((r.y + r.height) * (double)this.canvasH);
				if (r.style == Style.GRADIENT) {
					context.method_25296(x0, y0, x1, y1, 0xFF000000 | r.color & 16777215, 0xFF000000 | r.colorEnd & 16777215);
				} else {
					context.method_25294(x0, y0, x1, y1, 0xFF000000 | r.color & 16777215);
				}

				int bc = i == this.selected ? -12255420 : -1;
				context.method_25294(x0, y0, x1, y0 + 1, bc);
				context.method_25294(x0, y1 - 1, x1, y1, bc);
				context.method_25294(x0, y0, x0 + 1, y1, bc);
				context.method_25294(x1 - 1, y0, x1, y1, bc);
				if (r.showLabel && r.label != null && !r.label.isBlank()) {
					RenderHelper.drawText(context, tr, class_2561.method_43470(r.label), x0 + 3, y0 + 3, 0xFF000000 | r.textColor & 16777215);
				} else {
					RenderHelper.drawText(context, tr, class_2561.method_43470(r.style.name()), x0 + 3, y0 + 3, -1);
				}

				if (i == this.selected) {
					context.method_25294(x1 - 5, y1 - 5, x1, y1, -30652);
				}
			}

			String info = rs.isEmpty()
				? "Drag on the canvas to add a censor box"
				: "Regions: " + rs.size() + (this.selected >= 0 ? "  (selected #" + (this.selected + 1) + ")" : "");
			RenderHelper.drawText(context, tr, class_2561.method_43470(info), this.canvasX, this.canvasY + this.canvasH + 4, -5197648);
			if (!config.streamerModeEnabled) {
				RenderHelper.drawText(
					context, tr, class_2561.method_43470("Streamer Mode is OFF - regions are saved but not applied"), this.canvasX, this.canvasY - 12, -21931
				);
			}
		}
	}

	public void method_25419() {
		if (this.field_22787 != null) {
			this.field_22787.method_1507(this.parent);
		}
	}
}
