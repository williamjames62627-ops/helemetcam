package dev.recordable.screen;

import dev.recordable.FilterPreviewRenderer;
import dev.recordable.RecordableConfig;
import dev.recordable.WatermarkSlot;
import dev.recordable.RecordableConfig.OverlayStyleHud;
import dev.recordable.WatermarkSlot.Kind;
import dev.recordable.WatermarkSlot.Position;
import dev.recordable.compat.RenderHelper;
import dev.recordable.filter.FilterType;
import dev.recordable.theme.ThemeColors;
import dev.recordable.theme.ThemeEngine;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.class_2561;
import net.minecraft.class_327;
import net.minecraft.class_332;
import net.minecraft.class_4185;
import net.minecraft.class_437;

public final class OverlayPositionScreen extends class_437 {
	private static final int IDLE_BORDER = -1996488705;
	private static final int HOVER_BORDER = -855638272;
	private static final int SELECTED_BORDER = -12255420;
	private static final int SELECTED_FILL = 574947140;
	private static final int RESIZE_HANDLE_COLOR = -30652;
	private static final int LABEL_BG = -872415232;
	private static final int COORD_COLOR = -5570646;
	private static final int GUIDE_COLOR = 872415231;
	private static final int HEADER_COLOR = -1;
	private static final int HINT_COLOR = -5197648;
	private static final int PANEL_BG = -1156706794;
	private static final int PANEL_BORDER = -12961222;
	private static final int SECTION_BG = -15198172;
	private static final int SECTION_HOVER = -14540234;
	private static final int ACCENT = -10057524;
	private static final int RESIZE_HANDLE_SIZE = 6;
	private static final int PANEL_W = 154;
	private static final int ROW_H = 16;
	private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM dd yyyy", Locale.ENGLISH);
	private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);
	private final class_437 parent;
	private int origPlayRecX;
	private int origPlayRecY;
	private int origTimestampOffsetX;
	private int origTimestampY;
	private int origSpX;
	private int origSpOffsetY;
	private int origPerfOffsetX;
	private int origPerfOffsetY;
	private int origDetailsOffsetX;
	private int origDetailsOffsetY;
	private int origCornersX;
	private int origCornersY;
	private int origCornersW;
	private int origCornersH;
	private int origPlayRecW;
	private int origPlayRecH;
	private int origTimestampW;
	private int origTimestampH;
	private int origSpW;
	private int origSpH;
	private int origPerfW;
	private int origPerfH;
	private int origDetailsW;
	private int origDetailsH;
	private String origVhsPlayColor;
	private String origVhsRecTextColor;
	private String origVhsRecDotColor;
	private String origVhsBracketColor;
	private String origVhsTimestampColor;
	private String origVhsDateColor;
	private String origVhsSpColor;
	private int origPlayRecOpacity;
	private int origTimestampOpacity;
	private int origCornersOpacity;
	private int origSpOpacity;
	private int origDetailsOpacity;
	private int origPerfOpacity;
	private String origLayerOrder;
	private boolean origPlayRecVisible;
	private boolean origTimestampVisible;
	private boolean origCornersVisible;
	private boolean origSpVisible;
	private boolean origDetailsVisible;
	private boolean origPerfVisible;
	private int origClassicX;
	private int origClassicY;
	private int origSynthX;
	private int origSynthY;
	private boolean origClassicVisible;
	private boolean origSynthVisible;
	private final List<OverlayPositionScreen.DraggableElement> elements = new ArrayList();
	private OverlayPositionScreen.DraggableElement hoveredElement;
	private OverlayPositionScreen.DraggableElement draggedElement;
	private int dragOffsetX;
	private int dragOffsetY;
	private boolean cancelled;
	private OverlayPositionScreen.ResizeEdge activeResize = OverlayPositionScreen.ResizeEdge.NONE;
	private OverlayPositionScreen.ResizeEdge hoveredResize = OverlayPositionScreen.ResizeEdge.NONE;
	private OverlayPositionScreen.DraggableElement resizeElement;
	private int resizeOrigX;
	private int resizeOrigY;
	private int resizeOrigW;
	private int resizeOrigH;
	private boolean panelOpen = true;
	private int panelScroll = 0;
	private boolean sectionLayersOpen = true;
	private boolean sectionOpacityOpen = false;
	private boolean sectionWatermarksOpen = true;
	private final List<OverlayPositionScreen.OpacityEntry> opacityEntries = new ArrayList();
	private final List<String> layerOrder = new ArrayList();
	private OverlayPositionScreen.OpacityEntry draggingOpacity;
	private int vw;
	private int vh;
	private float overlayScale = 1.0F;
	private double dragMouseX;
	private double dragMouseY;
	private static final int DEF_PLAY_REC_X = 80;
	private static final int DEF_PLAY_REC_Y = 14;
	private static final int DEF_TS_OFFSET_X = 14;
	private static final int DEF_TS_Y = 14;
	private static final int DEF_SP_X = 80;
	private static final int DEF_SP_OFFSET_Y = 24;
	private static final int DEF_PERF_OFFSET_X = 8;
	private static final int DEF_PERF_OFFSET_Y = 80;
	private static final int DEF_DETAILS_OFFSET_X = 14;
	private static final int DEF_DETAILS_OFFSET_Y = 14;
	private static final int DEF_CORNERS_X = 68;
	private static final int DEF_CORNERS_Y = 4;
	private static final int DEF_CORNERS_W = 100;
	private static final int DEF_CORNERS_H = 48;
	private static final String DEF_LAYER_ORDER = RecordableConfig.defaultLayerOrder();
	private static final String[] ALL_LAYERS = (String[])RecordableConfig.allLayerIds().toArray(new String[0]);

	private int tPanelBg() {
		return ThemeEngine.get().colors().panelBackground;
	}

	private int tPanelBorder() {
		return ThemeEngine.get().colors().panelBorder;
	}

	private int tSectionBg() {
		return ThemeEngine.get().colors().sectionBackground;
	}

	private int tSectionHov() {
		return ThemeEngine.get().colors().sectionHover;
	}

	private int tAccent() {
		return ThemeEngine.get().colors().accent;
	}

	public OverlayPositionScreen(class_437 parent) {
		super(class_2561.method_43471("screen.recordable.position_editor.title"));
		this.parent = parent;
	}

	protected void method_25426() {
		super.method_25426();
		RecordableConfig config = RecordableConfig.get();
		this.overlayScale = Math.max(0.5F, Math.min(2.0F, (float)config.overlayScale / 100.0F));
		this.snapshotAll(config);
		this.buildOpacityEntries(config);
		this.initLayerOrder(config);
		int btnW = 60;
		int btnH = 20;
		int gap = 4;
		int totalW = btnW * 4 + gap * 3;
		int startX = (this.field_22789 - totalW) / 2;
		int btnY = this.field_22790 - btnH - 6;
		this.method_37063(
			class_4185.method_46430(class_2561.method_43471("screen.recordable.position_editor.done"), b -> this.saveAndClose())
				.method_46434(startX, btnY, btnW, btnH)
				.method_46431()
		);
		this.method_37063(
			class_4185.method_46430(class_2561.method_43471("screen.recordable.position_editor.reset_all"), b -> this.resetAllDefaults())
				.method_46434(startX + btnW + gap, btnY, btnW, btnH)
				.method_46431()
		);
		this.method_37063(class_4185.method_46430(class_2561.method_43470(this.panelOpen ? "▶ Panel" : "◀ Panel"), b -> {
			this.panelOpen = !this.panelOpen;
			b.method_25355(class_2561.method_43470(this.panelOpen ? "▶ Panel" : "◀ Panel"));
		}).method_46434(startX + (btnW + gap) * 2, btnY, btnW, btnH).method_46431());
		this.method_37063(
			class_4185.method_46430(class_2561.method_43471("screen.recordable.position_editor.cancel"), b -> this.cancelAndClose())
				.method_46434(startX + (btnW + gap) * 3, btnY, btnW, btnH)
				.method_46431()
		);
	}

	private void snapshotAll(RecordableConfig c) {
		this.origPlayRecX = c.hudPlayRecX;
		this.origPlayRecY = c.hudPlayRecY;
		this.origTimestampOffsetX = c.hudTimestampOffsetX;
		this.origTimestampY = c.hudTimestampY;
		this.origSpX = c.hudSpX;
		this.origSpOffsetY = c.hudSpOffsetY;
		this.origPerfOffsetX = c.hudPerfOffsetX;
		this.origPerfOffsetY = c.hudPerfOffsetY;
		this.origDetailsOffsetX = c.hudDetailsOffsetX;
		this.origDetailsOffsetY = c.hudDetailsOffsetY;
		this.origCornersX = c.hudCornersX;
		this.origCornersY = c.hudCornersY;
		this.origCornersW = c.hudCornersWidth;
		this.origCornersH = c.hudCornersHeight;
		this.origPlayRecW = c.hudPlayRecW;
		this.origPlayRecH = c.hudPlayRecH;
		this.origTimestampW = c.hudTimestampW;
		this.origTimestampH = c.hudTimestampH;
		this.origSpW = c.hudSpW;
		this.origSpH = c.hudSpH;
		this.origPerfW = c.hudPerfW;
		this.origPerfH = c.hudPerfH;
		this.origDetailsW = c.hudDetailsW;
		this.origDetailsH = c.hudDetailsH;
		this.origVhsPlayColor = c.vhsPlayColor;
		this.origVhsRecTextColor = c.vhsRecTextColor;
		this.origVhsRecDotColor = c.vhsRecDotColor;
		this.origVhsBracketColor = c.vhsBracketColor;
		this.origVhsTimestampColor = c.vhsTimestampColor;
		this.origVhsDateColor = c.vhsDateColor;
		this.origVhsSpColor = c.vhsSpColor;
		this.origPlayRecOpacity = c.hudPlayRecOpacity;
		this.origTimestampOpacity = c.hudTimestampOpacity;
		this.origCornersOpacity = c.hudCornersOpacity;
		this.origSpOpacity = c.hudSpOpacity;
		this.origDetailsOpacity = c.hudDetailsOpacity;
		this.origPerfOpacity = c.hudPerfOpacity;
		this.origLayerOrder = c.hudLayerOrder;
		this.origPlayRecVisible = c.hudPlayRecVisible;
		this.origTimestampVisible = c.hudTimestampVisible;
		this.origCornersVisible = c.hudCornersVisible;
		this.origSpVisible = c.hudSpVisible;
		this.origDetailsVisible = c.hudDetailsVisible;
		this.origPerfVisible = c.hudPerfVisible;
		this.origClassicX = c.hudClassicX;
		this.origClassicY = c.hudClassicY;
		this.origSynthX = c.hudSynthX;
		this.origSynthY = c.hudSynthY;
		this.origClassicVisible = c.hudClassicVisible;
		this.origSynthVisible = c.hudSynthVisible;
	}

	private void restoreAll(RecordableConfig c) {
		c.hudPlayRecX = this.origPlayRecX;
		c.hudPlayRecY = this.origPlayRecY;
		c.hudTimestampOffsetX = this.origTimestampOffsetX;
		c.hudTimestampY = this.origTimestampY;
		c.hudSpX = this.origSpX;
		c.hudSpOffsetY = this.origSpOffsetY;
		c.hudPerfOffsetX = this.origPerfOffsetX;
		c.hudPerfOffsetY = this.origPerfOffsetY;
		c.hudDetailsOffsetX = this.origDetailsOffsetX;
		c.hudDetailsOffsetY = this.origDetailsOffsetY;
		c.hudCornersX = this.origCornersX;
		c.hudCornersY = this.origCornersY;
		c.hudCornersWidth = this.origCornersW;
		c.hudCornersHeight = this.origCornersH;
		c.hudPlayRecW = this.origPlayRecW;
		c.hudPlayRecH = this.origPlayRecH;
		c.hudTimestampW = this.origTimestampW;
		c.hudTimestampH = this.origTimestampH;
		c.hudSpW = this.origSpW;
		c.hudSpH = this.origSpH;
		c.hudPerfW = this.origPerfW;
		c.hudPerfH = this.origPerfH;
		c.hudDetailsW = this.origDetailsW;
		c.hudDetailsH = this.origDetailsH;
		c.vhsPlayColor = this.origVhsPlayColor;
		c.vhsRecTextColor = this.origVhsRecTextColor;
		c.vhsRecDotColor = this.origVhsRecDotColor;
		c.vhsBracketColor = this.origVhsBracketColor;
		c.vhsTimestampColor = this.origVhsTimestampColor;
		c.vhsDateColor = this.origVhsDateColor;
		c.vhsSpColor = this.origVhsSpColor;
		c.hudPlayRecOpacity = this.origPlayRecOpacity;
		c.hudTimestampOpacity = this.origTimestampOpacity;
		c.hudCornersOpacity = this.origCornersOpacity;
		c.hudSpOpacity = this.origSpOpacity;
		c.hudDetailsOpacity = this.origDetailsOpacity;
		c.hudPerfOpacity = this.origPerfOpacity;
		c.hudLayerOrder = this.origLayerOrder;
		c.hudPlayRecVisible = this.origPlayRecVisible;
		c.hudTimestampVisible = this.origTimestampVisible;
		c.hudCornersVisible = this.origCornersVisible;
		c.hudSpVisible = this.origSpVisible;
		c.hudDetailsVisible = this.origDetailsVisible;
		c.hudPerfVisible = this.origPerfVisible;
		c.hudClassicX = this.origClassicX;
		c.hudClassicY = this.origClassicY;
		c.hudSynthX = this.origSynthX;
		c.hudSynthY = this.origSynthY;
		c.hudClassicVisible = this.origClassicVisible;
		c.hudSynthVisible = this.origSynthVisible;
		c.save();
	}

	private static String elementIcon(String id) {
		return switch (id) {
			case "PLAY/REC" -> "●";
			case "Timestamp" -> "⏱";
			case "Details" -> "ℹ";
			case "SP" -> "▶";
			case "Perf" -> "≡";
			case "Corners" -> "⬜";
			case "Mic" -> "\ud83c\udfa4";
			case "Classic" -> "▤";
			case "Synthwave" -> "▤";
			case "Filter:VHS", "Filter:LCD_MOIRE", "Filter:CRT" -> "▣";
			default -> "•";
		};
	}

	private static String layerDisplayName(String id) {
		return switch (id) {
			case "Filter:VHS" -> "VHS Filter";
			case "Filter:LCD_MOIRE" -> "LCD Moire Filter";
			case "Filter:CRT" -> "CRT Filter";
			default -> id;
		};
	}

	private void buildOpacityEntries(RecordableConfig config) {
		this.opacityEntries.clear();
		OverlayStyleHud opStyle = config.overlayStyleHud != null ? config.overlayStyleHud : OverlayStyleHud.CLASSIC;
		if (opStyle == OverlayStyleHud.VHS) {
			this.opacityEntries.add(new OverlayPositionScreen.OpacityEntry("PLAY/REC", "REC", () -> config.hudPlayRecOpacity, v -> config.hudPlayRecOpacity = v));
			this.opacityEntries.add(new OverlayPositionScreen.OpacityEntry("Timestamp", "Time", () -> config.hudTimestampOpacity, v -> config.hudTimestampOpacity = v));
			this.opacityEntries.add(new OverlayPositionScreen.OpacityEntry("Corners", "Corners", () -> config.hudCornersOpacity, v -> config.hudCornersOpacity = v));
			this.opacityEntries.add(new OverlayPositionScreen.OpacityEntry("SP", "SP", () -> config.hudSpOpacity, v -> config.hudSpOpacity = v));
			this.opacityEntries.add(new OverlayPositionScreen.OpacityEntry("Details", "Details", () -> config.hudDetailsOpacity, v -> config.hudDetailsOpacity = v));
			this.opacityEntries.add(new OverlayPositionScreen.OpacityEntry("Perf", "Perf", () -> config.hudPerfOpacity, v -> config.hudPerfOpacity = v));
		}

		this.opacityEntries.add(new OverlayPositionScreen.OpacityEntry("Filter:VHS", "VHS", () -> config.filterVhsIntensity, v -> config.filterVhsIntensity = v));
		this.opacityEntries
			.add(new OverlayPositionScreen.OpacityEntry("Filter:LCD_MOIRE", "LCD Moire", () -> config.filterLcdMoireIntensity, v -> config.filterLcdMoireIntensity = v));
		this.opacityEntries.add(new OverlayPositionScreen.OpacityEntry("Filter:CRT", "CRT", () -> config.filterCrtIntensity, v -> config.filterCrtIntensity = v));
	}

	private void initLayerOrder(RecordableConfig config) {
		this.layerOrder.clear();
		Set<String> validSet = Set.of(ALL_LAYERS);
		if (config.hudLayerOrder != null && !config.hudLayerOrder.isBlank()) {
			for (String part : config.hudLayerOrder.split(",")) {
				String id = part.trim();
				if (!id.isEmpty() && validSet.contains(id) && !this.layerOrder.contains(id)) {
					this.layerOrder.add(id);
				}
			}
		}

		for (String id : ALL_LAYERS) {
			if (!this.layerOrder.contains(id)) {
				this.layerOrder.add(id);
			}
		}
	}

	private void saveLayerOrder() {
		RecordableConfig config = RecordableConfig.get();
		config.hudLayerOrder = String.join(",", this.layerOrder);
		config.save();
	}

	private List<String> shownLayers() {
		RecordableConfig config = RecordableConfig.get();
		OverlayStyleHud style = config.overlayStyleHud != null ? config.overlayStyleHud : OverlayStyleHud.CLASSIC;
		Set<String> allowed = new HashSet(RecordableConfig.layerIdsForStyle(style));
		List<String> out = new ArrayList();

		for (String id : this.layerOrder) {
			if (allowed.contains(id)) {
				out.add(id);
			}
		}

		return out;
	}

	private void moveShownLayer(List<String> shown, int i, int dir) {
		int j = i + dir;
		if (i >= 0 && j >= 0 && i < shown.size() && j < shown.size()) {
			int ia = this.layerOrder.indexOf(shown.get(i));
			int ib = this.layerOrder.indexOf(shown.get(j));
			if (ia >= 0 && ib >= 0) {
				String tmp = (String)this.layerOrder.get(ia);
				this.layerOrder.set(ia, (String)this.layerOrder.get(ib));
				this.layerOrder.set(ib, tmp);
				this.saveLayerOrder();
			}
		}
	}

	public void method_25394(class_332 context, int mouseX, int mouseY, float delta) {
		this.drawPreviewBackground(context);
		RecordableConfig config = RecordableConfig.get();
		if (config != null && this.field_22787 != null) {
			class_327 tr = this.field_22793;
			this.overlayScale = Math.max(0.5F, Math.min(2.0F, (float)config.overlayScale / 100.0F));
			this.vw = (int)((float)this.field_22789 / this.overlayScale);
			this.vh = (int)((float)this.field_22790 / this.overlayScale);
			context.method_51448().method_22903();
			context.method_51448().method_22905(this.overlayScale, this.overlayScale, 1.0F);
			int smx = (int)((float)mouseX / this.overlayScale);
			int smy = (int)((float)mouseY / this.overlayScale);
			this.drawGridGuides(context);
			this.rebuildElements(config, tr);
			this.hoveredElement = null;
			this.hoveredResize = OverlayPositionScreen.ResizeEdge.NONE;
			boolean overPanel = this.panelOpen && mouseX >= this.field_22789 - 154 - 10;
			if (this.draggedElement == null && this.activeResize == OverlayPositionScreen.ResizeEdge.NONE && !overPanel) {
				List<OverlayPositionScreen.DraggableElement> ordered = new ArrayList();

				for (String layerId : this.shownLayers()) {
					if (config.isElementVisible(layerId)) {
						for (OverlayPositionScreen.DraggableElement el : this.elements) {
							if (el.id.equals(layerId)) {
								ordered.add(el);
								break;
							}
						}
					}
				}

				for (int i = ordered.size() - 1; i >= 0; i--) {
					OverlayPositionScreen.DraggableElement elx = (OverlayPositionScreen.DraggableElement)ordered.get(i);
					if (elx.resizable) {
						OverlayPositionScreen.ResizeEdge edge = this.hitTestResizeHandle(elx, smx, smy);
						if (edge != OverlayPositionScreen.ResizeEdge.NONE) {
							this.hoveredResize = edge;
							this.hoveredElement = elx;
							break;
						}
					}
				}

				label209:
				if (this.hoveredResize == OverlayPositionScreen.ResizeEdge.NONE) {
					int ix = ordered.size() - 1;

					OverlayPositionScreen.DraggableElement elx;
					while (true) {
						if (ix < 0) {
							break label209;
						}

						elx = (OverlayPositionScreen.DraggableElement)ordered.get(ix);
						if (smx >= elx.x && smx <= elx.x + elx.w && smy >= elx.y && smy <= elx.y + elx.h) {
							if (!elx.id.equals("Corners")) {
								break;
							}

							int cz = 40;
							boolean nearTL = smx < elx.x + cz && smy < elx.y + cz;
							boolean nearTR = smx > elx.x + elx.w - cz && smy < elx.y + cz;
							boolean nearBL = smx < elx.x + cz && smy > elx.y + elx.h - cz;
							boolean nearBR = smx > elx.x + elx.w - cz && smy > elx.y + elx.h - cz;
							if (nearTL || nearTR || nearBL || nearBR) {
								break;
							}
						}

						ix--;
					}

					this.hoveredElement = elx;
				}
			}

			if (this.hoveredElement == null && this.draggedElement == null && this.activeResize == OverlayPositionScreen.ResizeEdge.NONE && !overPanel) {
				for (int ix = this.elements.size() - 1; ix >= 0; ix--) {
					OverlayPositionScreen.DraggableElement elx = (OverlayPositionScreen.DraggableElement)this.elements.get(ix);
					if (elx.wmIndex >= 0 && smx >= elx.x && smx <= elx.x + elx.w && smy >= elx.y && smy <= elx.y + elx.h) {
						this.hoveredElement = elx;
						break;
					}
				}
			}

			for (String layerIdx : this.shownLayers()) {
				boolean visible = config.isElementVisible(layerIdx);

				for (OverlayPositionScreen.DraggableElement elx : this.elements) {
					if (elx.id.equals(layerIdx)) {
						this.renderElement(context, tr, elx, smx, smy, visible);
						break;
					}
				}
			}

			for (OverlayPositionScreen.DraggableElement elxx : this.elements) {
				if (elxx.wmIndex >= 0) {
					this.renderWatermarkElement(context, tr, elxx, smx, smy);
				}
			}

			this.renderOverlayPreview(context, config, tr);
			context.method_51448().method_22909();
			if (this.panelOpen) {
				this.renderPanel(context, tr, mouseX, mouseY);
			}

			context.method_27534(tr, this.field_22785, this.field_22789 / 2, 4, -1);
			String hint = this.activeResize != OverlayPositionScreen.ResizeEdge.NONE
				? "Drag to resize · Release to confirm"
				: (
					this.draggedElement != null
						? class_2561.method_43471("screen.recordable.position_editor.hint_dragging").getString()
						: class_2561.method_43471("screen.recordable.position_editor.hint_idle").getString()
				);
			context.method_27534(tr, class_2561.method_43470(hint), this.field_22789 / 2, 15, -5197648);
			if (this.overlayScale != 1.0F) {
				String s = "Scale " + Math.round(this.overlayScale * 100.0F) + "%";
				context.method_27534(tr, class_2561.method_43470(s), this.field_22789 / 2, 26, -8947900);
			}

			if (this.draggedElement != null || this.activeResize != OverlayPositionScreen.ResizeEdge.NONE) {
				OverlayPositionScreen.DraggableElement elxxx = this.draggedElement != null ? this.draggedElement : this.resizeElement;
				if (elxxx != null) {
					String coords = this.activeResize != OverlayPositionScreen.ResizeEdge.NONE
						? elxxx.id + " " + elxxx.w + "×" + elxxx.h
						: elxxx.id + " " + elxxx.getDisplayCoords();
					int cw = tr.method_1727(coords) + 8;
					int cx = mouseX + 14;
					int cy = mouseY - 14;
					if (cx + cw > this.field_22789) {
						cx = mouseX - cw - 4;
					}

					if (cy < 0) {
						cy = mouseY + 18;
					}

					context.method_25294(cx - 2, cy - 2, cx + cw, cy + 12, -872415232);
					RenderHelper.drawText(context, tr, class_2561.method_43470(coords), cx + 2, cy, -5570646);
				}
			}

			super.method_25394(context, mouseX, mouseY, delta);
		} else {
			super.method_25394(context, mouseX, mouseY, delta);
		}
	}

	private void drawPreviewBackground(class_332 context) {
		context.method_25294(0, 0, this.field_22789, this.field_22790, -15066598);
		context.method_25294(0, 0, this.field_22789, this.field_22790, 1711276032);
	}

	private void drawGridGuides(class_332 context) {
		int cx = this.vw / 2;
		int cy = this.vh / 2;
		context.method_25294(cx, 0, cx + 1, this.vh, 1157627903);
		context.method_25294(0, cy, this.vw, cy + 1, 1157627903);
		context.method_25294(this.vw / 3, 0, this.vw / 3 + 1, this.vh, 587202559);
		context.method_25294(this.vw * 2 / 3, 0, this.vw * 2 / 3 + 1, this.vh, 587202559);
		context.method_25294(0, this.vh / 3, this.vw, this.vh / 3 + 1, 587202559);
		context.method_25294(0, this.vh * 2 / 3, this.vw, this.vh * 2 / 3 + 1, 587202559);
	}

	private String watermarkPlayerName() {
		return this.field_22787 != null && this.field_22787.field_1724 != null ? this.field_22787.field_1724.method_5477().getString() : "Player";
	}

	private String watermarkPreviewText(WatermarkSlot slot) {
		if (slot == null) {
			return "Watermark";
		} else {
			String txt = slot.kind == Kind.IMAGE ? "▨ " + (slot.name != null ? slot.name : "Image") : slot.resolveText(this.watermarkPlayerName());
			if (txt == null || txt.isEmpty()) {
				txt = slot.name != null ? slot.name : "Watermark";
			}

			return txt;
		}
	}

	private int[] computeWatermarkPos(WatermarkSlot slot, int w, int h) {
		int pad = slot.padding;
		int x;
		int y;
		switch (slot.position) {
			case CUSTOM:
				return new int[]{slot.customX, slot.customY};
			case TOP_LEFT:
				x = pad;
				y = pad;
				break;
			case TOP_CENTER:
				x = (this.vw - w) / 2;
				y = pad;
				break;
			case TOP_RIGHT:
				x = this.vw - w - pad;
				y = pad;
				break;
			case MIDDLE_LEFT:
				x = pad;
				y = (this.vh - h) / 2;
				break;
			case CENTER:
				x = (this.vw - w) / 2;
				y = (this.vh - h) / 2;
				break;
			case MIDDLE_RIGHT:
				x = this.vw - w - pad;
				y = (this.vh - h) / 2;
				break;
			case BOTTOM_LEFT:
				x = pad;
				y = this.vh - h - pad;
				break;
			case BOTTOM_CENTER:
				x = (this.vw - w) / 2;
				y = this.vh - h - pad;
				break;
			case BOTTOM_RIGHT:
				x = this.vw - w - pad;
				y = this.vh - h - pad;
				break;
			default:
				x = this.vw - w - pad;
				y = this.vh - h - pad;
		}

		return new int[]{x, y};
	}

	private void buildWatermarkElements(RecordableConfig config, class_327 tr) {
		if (config.watermarkSlots != null) {
			for (int i = 0; i < config.watermarkSlots.size(); i++) {
				WatermarkSlot slot = (WatermarkSlot)config.watermarkSlots.get(i);
				if (slot != null && slot.enabled) {
					OverlayPositionScreen.DraggableElement el = new OverlayPositionScreen.DraggableElement(
						"WM:" + i, slot.name != null ? slot.name : "Watermark", OverlayPositionScreen.AnchorMode.TOP_LEFT
					);
					el.wmIndex = i;
					el.resizable = false;
					String txt = this.watermarkPreviewText(slot);
					float f = (float)slot.scale / 100.0F;
					int tw = Math.max(8, Math.round((float)tr.method_1727(txt) * f));
					int th = Math.max(8, Math.round(9.0F * f));
					el.w = tw + 4;
					el.h = th + 2;
					el.autoW = el.w;
					el.autoH = el.h;
					int[] pos = this.computeWatermarkPos(slot, el.w, el.h);
					el.x = Math.max(0, Math.min(Math.max(0, this.vw - el.w), pos[0]));
					el.y = Math.max(0, Math.min(Math.max(0, this.vh - el.h), pos[1]));
					this.elements.add(el);
				}
			}
		}
	}

	private static int parseWatermarkColor(String hex, int opacityPct) {
		int rgb = 16777215;
		int a = 255;

		try {
			String h = hex == null ? "" : hex.trim();
			if (h.startsWith("#")) {
				h = h.substring(1);
			}

			if (h.length() == 8) {
				long v = Long.parseLong(h, 16);
				a = (int)(v >> 24 & 255L);
				rgb = (int)(v & 16777215L);
			} else if (h.length() == 6) {
				rgb = (int)Long.parseLong(h, 16);
			}
		} catch (Exception var7) {
		}

		int op = Math.max(0, Math.min(100, opacityPct));
		int alpha = (int)((double)a * ((double)op / 100.0));
		if (alpha < 40) {
			alpha = 40;
		}

		return alpha << 24 | rgb & 16777215;
	}

	private void renderWatermarkElement(class_332 ctx, class_327 tr, OverlayPositionScreen.DraggableElement el, int mx, int my) {
		RecordableConfig config = RecordableConfig.get();
		if (el.wmIndex >= 0 && config.watermarkSlots != null && el.wmIndex < config.watermarkSlots.size()) {
			WatermarkSlot slot = (WatermarkSlot)config.watermarkSlots.get(el.wmIndex);
			boolean isDragged = el == this.draggedElement;
			boolean isHovered = el == this.hoveredElement;
			int bc = isDragged ? -12255420 : (isHovered ? -855638272 : 1728053247);
			int fc = isDragged ? 574947140 : (isHovered ? 419430144 : 117440511);
			ctx.method_25294(el.x, el.y, el.x + el.w, el.y + el.h, fc);
			ctx.method_25294(el.x, el.y, el.x + el.w, el.y + 1, bc);
			ctx.method_25294(el.x, el.y + el.h - 1, el.x + el.w, el.y + el.h, bc);
			ctx.method_25294(el.x, el.y, el.x + 1, el.y + el.h, bc);
			ctx.method_25294(el.x + el.w - 1, el.y, el.x + el.w, el.y + el.h, bc);
			String txt = tr.method_27523(this.watermarkPreviewText(slot), el.w);
			int color = parseWatermarkColor(slot.textColor, slot.opacity);
			RenderHelper.drawText(ctx, tr, class_2561.method_43470(txt), el.x + 2, el.y + 2, color);
			String tag = "▤ " + el.label;
			int lw = tr.method_1727(tag) + 4;
			int lx = el.x;
			int ly = el.y - 10;
			if (ly < 0) {
				ly = el.y + el.h + 1;
			}

			ctx.method_25294(lx, ly, lx + lw, ly + 9, -872415232);
			RenderHelper.drawText(ctx, tr, class_2561.method_43470(tag), lx + 2, ly + 1, bc);
		}
	}

	private void openWatermarkEditor(int index) {
		if (this.field_22787 != null) {
			this.field_22787.method_1507(new WatermarkScreen(this));
		}
	}

	private void rebuildElements(RecordableConfig config, class_327 tr) {
		this.elements.clear();
		this.buildWatermarkElements(config, tr);
		OverlayStyleHud style = config.overlayStyleHud != null ? config.overlayStyleHud : OverlayStyleHud.CLASSIC;
		boolean isVhs = style == OverlayStyleHud.VHS;
		OverlayPositionScreen.DraggableElement el = new OverlayPositionScreen.DraggableElement("PLAY/REC", "PLAY/REC", OverlayPositionScreen.AnchorMode.TOP_LEFT);
		int textH = isVhs && config.vhsShowPlay ? 24 : 12;
		el.autoW = Math.max(60, tr.method_1727("PLAY ▶") + 12);
		el.autoH = textH;
		el.w = config.hudPlayRecW > 0 ? config.hudPlayRecW : el.autoW;
		el.h = config.hudPlayRecH > 0 ? config.hudPlayRecH : el.autoH;
		el.x = config.hudPlayRecX;
		el.y = config.hudPlayRecY;
		this.elements.add(el);
		el = new OverlayPositionScreen.DraggableElement("Timestamp", "Timestamp", OverlayPositionScreen.AnchorMode.TOP_RIGHT);
		el.autoW = tr.method_1727("00:12:34") + 8;
		el.autoH = 12;
		el.w = config.hudTimestampW > 0 ? config.hudTimestampW : el.autoW;
		el.h = config.hudTimestampH > 0 ? config.hudTimestampH : el.autoH;
		el.x = this.vw - config.hudTimestampOffsetX - el.w;
		el.y = config.hudTimestampY;
		this.elements.add(el);
		el = new OverlayPositionScreen.DraggableElement("Corners", "Corners", OverlayPositionScreen.AnchorMode.RECT);
		el.x = config.hudCornersX;
		el.y = config.hudCornersY;
		el.w = config.hudCornersWidth;
		el.h = config.hudCornersHeight;
		el.autoW = 100;
		el.autoH = 48;
		this.elements.add(el);
		el = new OverlayPositionScreen.DraggableElement("SP", "SP", OverlayPositionScreen.AnchorMode.BOTTOM_LEFT);
		el.autoW = tr.method_1727("SP") + 8;
		el.autoH = 12;
		el.w = config.hudSpW > 0 ? config.hudSpW : el.autoW;
		el.h = config.hudSpH > 0 ? config.hudSpH : el.autoH;
		el.x = config.hudSpX;
		el.y = this.vh - config.hudSpOffsetY;
		this.elements.add(el);
		el = new OverlayPositionScreen.DraggableElement("Details", "Details", OverlayPositionScreen.AnchorMode.BOTTOM_RIGHT);
		textH = 0;
		int cw = 80;
		if (config.vhsShowDate) {
			textH += 22;
		}

		if (config.vhsShowTapeCounter) {
			textH += 11;
		}

		if (config.vhsShowAudioMeter) {
			textH += 12;
		}

		if (config.vhsShowBattery) {
			textH += 13;
		}

		if (textH < 20) {
			textH = 22;
		}

		el.autoW = cw;
		el.autoH = textH;
		el.w = config.hudDetailsW > 0 ? config.hudDetailsW : el.autoW;
		el.h = config.hudDetailsH > 0 ? config.hudDetailsH : el.autoH;
		el.x = this.vw - config.hudDetailsOffsetX - el.w;
		el.y = this.vh - config.hudDetailsOffsetY - el.h;
		this.elements.add(el);
		el = new OverlayPositionScreen.DraggableElement("Perf", "Perf", OverlayPositionScreen.AnchorMode.BOTTOM_RIGHT);
		String[] pL = new String[]{"Cap 60 | Enc 60 FPS", "Mem 1024 MiB | Drop 0", "Queue: 0/240 | 100%"};
		cw = 0;

		for (String l : pL) {
			cw = Math.max(cw, tr.method_1727(l));
		}

		el.autoW = cw + 10;
		el.autoH = 8 + pL.length * 10;
		el.w = config.hudPerfW > 0 ? config.hudPerfW : el.autoW;
		el.h = config.hudPerfH > 0 ? config.hudPerfH : el.autoH;
		el.x = this.vw - config.hudPerfOffsetX - el.w;
		el.y = this.vh - config.hudPerfOffsetY - el.h;
		this.elements.add(el);
		el = new OverlayPositionScreen.DraggableElement("Classic", "Classic", OverlayPositionScreen.AnchorMode.TOP_LEFT);
		String[] lines = new String[]{"REC 00:12:34", "60 FPS  drop 0", "Size: 12.3 MB", "1920x1080  Queue: 0/240 (OK)"};
		cw = 0;

		for (String l : lines) {
			cw = Math.max(cw, tr.method_1727(l));
		}

		el.autoW = cw + 22;
		el.autoH = 12 + lines.length * 11;
		el.w = el.autoW;
		el.h = el.autoH;
		el.resizable = false;
		int[] cp = config.classicPanelPos(this.vw, this.vh, el.w, el.h, 10);
		el.x = cp[0];
		el.y = cp[1];
		this.elements.add(el);
		el = new OverlayPositionScreen.DraggableElement("Synthwave", "Synthwave", OverlayPositionScreen.AnchorMode.TOP_LEFT);
		el.autoW = tr.method_1727("REC 00:12:34") + 22;
		el.autoH = 16;
		el.w = el.autoW;
		el.h = el.autoH;
		el.resizable = false;
		int[] sp = config.synthPanelPos(this.vw, this.vh, el.w, el.h, 0, 0);
		el.x = sp[0];
		el.y = sp[1];
		this.elements.add(el);
		el = new OverlayPositionScreen.DraggableElement("Mic", "Mic", OverlayPositionScreen.AnchorMode.TOP_LEFT);
		String micLabel = "\ud83c\udfa4 MIC (PTT)";
		el.autoW = tr.method_1727(micLabel) + 16;
		el.autoH = 13;
		el.w = el.autoW;
		el.h = el.autoH;
		el.resizable = false;
		el.x = config.hudMicX < 0 ? (this.vw - el.w) / 2 : config.hudMicX;
		el.y = config.hudMicY;
		this.elements.add(el);
	}

	private OverlayPositionScreen.ResizeEdge hitTestResizeHandle(OverlayPositionScreen.DraggableElement el, int mx, int my) {
		if (!el.resizable) {
			return OverlayPositionScreen.ResizeEdge.NONE;
		} else {
			int hs = 6;
			if (mx >= el.x - hs && mx <= el.x + hs && my >= el.y - hs && my <= el.y + hs) {
				return OverlayPositionScreen.ResizeEdge.TOP_LEFT;
			} else if (mx >= el.x + el.w - hs && mx <= el.x + el.w + hs && my >= el.y - hs && my <= el.y + hs) {
				return OverlayPositionScreen.ResizeEdge.TOP_RIGHT;
			} else if (mx >= el.x - hs && mx <= el.x + hs && my >= el.y + el.h - hs && my <= el.y + el.h + hs) {
				return OverlayPositionScreen.ResizeEdge.BOTTOM_LEFT;
			} else {
				return mx >= el.x + el.w - hs && mx <= el.x + el.w + hs && my >= el.y + el.h - hs && my <= el.y + el.h + hs
					? OverlayPositionScreen.ResizeEdge.BOTTOM_RIGHT
					: OverlayPositionScreen.ResizeEdge.NONE;
			}
		}
	}

	private void renderElement(class_332 ctx, class_327 tr, OverlayPositionScreen.DraggableElement el, int mx, int my, boolean visible) {
		boolean isDragged = el == this.draggedElement;
		boolean isHovered = el == this.hoveredElement || el == this.resizeElement;
		boolean isResizing = this.activeResize != OverlayPositionScreen.ResizeEdge.NONE && el == this.resizeElement;
		if (!visible) {
			int ghostBorder = 872367172;
			int ghostFill = 150946884;
			ctx.method_25294(el.x, el.y, el.x + el.w, el.y + el.h, ghostFill);
			ctx.method_25294(el.x, el.y, el.x + el.w, el.y + 1, ghostBorder);
			ctx.method_25294(el.x, el.y + el.h - 1, el.x + el.w, el.y + el.h, ghostBorder);
			ctx.method_25294(el.x, el.y, el.x + 1, el.y + el.h, ghostBorder);
			ctx.method_25294(el.x + el.w - 1, el.y, el.x + el.w, el.y + el.h, ghostBorder);
			String label = "⊘ " + el.id;
			int lw = tr.method_1727(label) + 4;
			int lx = el.x;
			int ly = el.y - 10;
			if (ly < 0) {
				ly = el.y + el.h + 1;
			}

			ctx.method_25294(lx, ly, lx + lw, ly + 9, 1711276032);
			RenderHelper.drawText(ctx, tr, class_2561.method_43470(label), lx + 2, ly + 1, 1442801254);
		} else {
			int bc = !isDragged && !isResizing ? (isHovered ? -855638272 : -1996488705) : -12255420;
			int fc = !isDragged && !isResizing ? (isHovered ? 419430144 : 150994943) : 574947140;
			ctx.method_25294(el.x, el.y, el.x + el.w, el.y + el.h, fc);
			ctx.method_25294(el.x, el.y, el.x + el.w, el.y + 1, bc);
			ctx.method_25294(el.x, el.y + el.h - 1, el.x + el.w, el.y + el.h, bc);
			ctx.method_25294(el.x, el.y, el.x + 1, el.y + el.h, bc);
			ctx.method_25294(el.x + el.w - 1, el.y, el.x + el.w, el.y + el.h, bc);
			if (el.resizable) {
				int hs = 6;
				int hc = -1996488705;
				drawHandle(ctx, el.x - hs / 2, el.y - hs / 2, hs, this.hoveredResize == OverlayPositionScreen.ResizeEdge.TOP_LEFT && isHovered ? -30652 : hc);
				drawHandle(ctx, el.x + el.w - hs / 2, el.y - hs / 2, hs, this.hoveredResize == OverlayPositionScreen.ResizeEdge.TOP_RIGHT && isHovered ? -30652 : hc);
				drawHandle(ctx, el.x - hs / 2, el.y + el.h - hs / 2, hs, this.hoveredResize == OverlayPositionScreen.ResizeEdge.BOTTOM_LEFT && isHovered ? -30652 : hc);
				drawHandle(
					ctx, el.x + el.w - hs / 2, el.y + el.h - hs / 2, hs, this.hoveredResize == OverlayPositionScreen.ResizeEdge.BOTTOM_RIGHT && isHovered ? -30652 : hc
				);
			}

			String icon = elementIcon(el.id);
			String label = icon + " " + el.id;
			int lw = tr.method_1727(label) + 4;
			int lx = el.x;
			int ly = el.y - 10;
			if (ly < 0) {
				ly = el.y + el.h + 1;
			}

			ctx.method_25294(lx, ly, lx + lw, ly + 9, -872415232);
			RenderHelper.drawText(ctx, tr, class_2561.method_43470(label), lx + 2, ly + 1, bc);
		}
	}

	private static void drawHandle(class_332 ctx, int x, int y, int s, int c) {
		ctx.method_25294(x, y, x + s, y + s, c);
		ctx.method_25294(x, y, x + s, y + 1, -1442840576);
		ctx.method_25294(x, y + s - 1, x + s, y + s, -1442840576);
		ctx.method_25294(x, y, x + 1, y + s, -1442840576);
		ctx.method_25294(x + s - 1, y, x + s, y + s, -1442840576);
	}

	private void renderPanel(class_332 ctx, class_327 tr, int mx, int my) {
		RecordableConfig config = RecordableConfig.get();
		int px = this.field_22789 - 154 - 4;
		int py = 36;
		int maxH = this.field_22790 - 68;
		int contentH = 0;
		contentH += 16;
		if (this.sectionLayersOpen) {
			contentH += this.shownLayers().size() * 16;
		}

		contentH += 2;
		contentH += 2;
		contentH += 16;
		if (this.sectionOpacityOpen) {
			contentH += this.opacityEntries.size() * 15;
		}

		contentH += 2;
		contentH += 16;
		if (this.sectionWatermarksOpen) {
			int wmRows = config.watermarkSlots != null && !config.watermarkSlots.isEmpty() ? config.watermarkSlots.size() : 1;
			contentH += wmRows * 16;
			contentH += 16;
		}

		contentH += 4;
		int panelH = Math.min(maxH, contentH + 4);
		ctx.method_25294(px - 1, py - 1, px + 154 + 1, py + panelH + 1, this.tPanelBorder());
		ctx.method_25294(px, py, px + 154, py + panelH, this.tPanelBg());
		int y = py + 2 - this.panelScroll;
		int innerW = 148;
		int left = px + 3;
		y = this.renderSectionHeader(ctx, tr, "▾ Layers", "▸ Layers", this.sectionLayersOpen, left, y, innerW, mx, my, py, py + panelH);
		if (this.sectionLayersOpen) {
			List<String> shown = this.shownLayers();

			for (int i = 0; i < shown.size(); i++) {
				String id = (String)shown.get(i);
				boolean isVisible = config.isElementVisible(id);
				if (y >= py && y + 16 <= py + panelH) {
					boolean isHoveredEl = this.hoveredElement != null && this.hoveredElement.id.equals(id);
					boolean rowHov = mx >= left && mx <= left + innerW && my >= y && my < y + 16;
					if (isHoveredEl) {
						ctx.method_25294(left, y, left + innerW, y + 16 - 1, 587202372);
					} else if (rowHov) {
						ctx.method_25294(left, y, left + innerW, y + 16 - 1, 318767103);
					}

					int eyeX = left + 1;
					boolean eyeHov = mx >= eyeX && mx <= eyeX + 10 && my >= y && my < y + 16;
					String eyeIcon = isVisible ? "◉" : "◎";
					int eyeColor = isVisible ? (eyeHov ? -5570646 : -10044570) : (eyeHov ? -30584 : -7846844);
					RenderHelper.drawText(ctx, tr, class_2561.method_43470(eyeIcon), eyeX, y + 3, eyeColor);
					String icon = elementIcon(id);
					String displayName = layerDisplayName(id);
					int nameColor = isVisible ? (isHoveredEl ? -120 : -3355444) : -10066330;
					String display = icon + " " + tr.method_27523(displayName, innerW - 42);
					RenderHelper.drawText(ctx, tr, class_2561.method_43470(display), left + 13, y + 3, nameColor);
					int ax = left + innerW - 14;
					if (i > 0) {
						boolean hu = mx >= ax && mx <= ax + 10 && my >= y && my <= y + 7;
						RenderHelper.drawText(ctx, tr, class_2561.method_43470("▲"), ax, y + 0, hu ? -188 : -11184811);
					}

					if (i < shown.size() - 1) {
						boolean hd = mx >= ax && mx <= ax + 10 && my >= y + 8 && my <= y + 16;
						RenderHelper.drawText(ctx, tr, class_2561.method_43470("▼"), ax, y + 8, hd ? -188 : -11184811);
					}
				}

				y += 16;
			}
		}

		y = this.renderSectionHeader(ctx, tr, "▾ Opacity", "▸ Opacity", this.sectionOpacityOpen, left, y, innerW, mx, my, py, py + panelH);
		if (this.sectionOpacityOpen) {
			for (OverlayPositionScreen.OpacityEntry entry : this.opacityEntries) {
				if (y >= py && y + 16 - 2 <= py + panelH) {
					this.renderOpacityRow(ctx, tr, entry, left, y, innerW, mx, my);
				}

				y += 15;
			}
		}

		if (y >= py && y + 2 <= py + panelH) {
			ctx.method_25294(left + 4, y, left + innerW - 4, y + 1, 872415231);
		}

		y += 2;
		y = this.renderSectionHeader(ctx, tr, "▾ Watermarks", "▸ Watermarks", this.sectionWatermarksOpen, left, y, innerW, mx, my, py, py + panelH);
		if (this.sectionWatermarksOpen) {
			List<WatermarkSlot> slots = config.watermarkSlots;
			if (slots != null && !slots.isEmpty()) {
				for (int i = 0; i < slots.size(); i++) {
					WatermarkSlot slot = (WatermarkSlot)slots.get(i);
					boolean en = slot != null && slot.enabled;
					if (y >= py && y + 16 <= py + panelH) {
						boolean rowHovx = mx >= left && mx <= left + innerW && my >= y && my < y + 16;
						if (rowHovx) {
							ctx.method_25294(left, y, left + innerW, y + 16 - 1, 318767103);
						}

						int eyeXx = left + 1;
						boolean eyeHovx = mx >= eyeXx && mx <= eyeXx + 12 && my >= y && my < y + 16;
						String eyeIconx = en ? "◉" : "◎";
						int eyeColorx = en ? (eyeHovx ? -5570646 : -10044570) : (eyeHovx ? -30584 : -7846844);
						RenderHelper.drawText(ctx, tr, class_2561.method_43470(eyeIconx), eyeXx, y + 3, eyeColorx);
						String nm = "▤ " + tr.method_27523(slot != null && slot.name != null ? slot.name : "Watermark", innerW - 42);
						RenderHelper.drawText(ctx, tr, class_2561.method_43470(nm), left + 13, y + 3, en ? -3355444 : -8947849);
						int ex = left + innerW - 22;
						boolean ehov = mx >= ex && mx <= ex + 20 && my >= y && my < y + 16;
						RenderHelper.drawText(ctx, tr, class_2561.method_43470("Edit"), ex, y + 3, ehov ? -13244 : -7824965);
					}

					y += 16;
				}
			} else {
				if (y >= py && y + 16 <= py + panelH) {
					RenderHelper.drawText(ctx, tr, class_2561.method_43470("  (none - add below)"), left + 2, y + 3, -8947849);
				}

				y += 16;
			}

			if (y >= py && y + 16 <= py + panelH) {
				boolean ohov = mx >= left && mx <= left + innerW && my >= y && my < y + 16;
				RenderHelper.drawText(ctx, tr, class_2561.method_43470("➕ Open Watermark Editor"), left + 4, y + 3, ohov ? -10040065 : -10053172);
			}

			y += 16;
		}
	}

	private int renderSectionHeader(
		class_332 ctx, class_327 tr, String openLabel, String closedLabel, boolean open, int x, int y, int w, int mx, int my, int clipTop, int clipBot
	) {
		if (y >= clipTop && y + 16 <= clipBot) {
			boolean hov = mx >= x && mx <= x + w && my >= y && my < y + 16;
			ctx.method_25294(x, y, x + w, y + 16 - 1, hov ? this.tSectionHov() : this.tSectionBg());
			ctx.method_25294(x, y + 2, x + 2, y + 16 - 3, this.tAccent());
			RenderHelper.drawText(ctx, tr, class_2561.method_43470(open ? openLabel : closedLabel), x + 5, y + 4, this.tAccent());
		}

		return y + 16;
	}

	private void renderOpacityRow(class_332 ctx, class_327 tr, OverlayPositionScreen.OpacityEntry entry, int x, int y, int w, int mx, int my) {
		int val = (Integer)entry.getter.get();
		String lbl = entry.label + " " + val + "%";
		RenderHelper.drawText(ctx, tr, class_2561.method_43470(lbl), x + 2, y + 1, -5592406);
		int barX = x + 2;
		int barY = y + 10;
		int barW = w - 4;
		int barH = 3;
		ctx.method_25294(barX, barY, barX + barW, barY + barH, -14540254);
		int fillW = (int)((double)(barW * val) / 100.0);
		ctx.method_25294(barX, barY, barX + fillW, barY + barH, this.tAccent());
		if (mx >= barX && mx <= barX + barW && my >= y && my <= y + 16 - 2) {
			ctx.method_25294(barX + fillW - 1, barY - 1, barX + fillW + 2, barY + barH + 1, -1);
		}
	}

	private void renderClassicPreview(class_332 context, RecordableConfig config, class_327 tr) {
		if (config.hudClassicVisible) {
			ThemeColors skin = config.activeOverlaySkinOrNull();
			int accentRgb = skin != null ? skin.accent & 16777215 : config.getOverlayColorRgb();
			int accentArgb = 0xFF000000 | accentRgb;
			int accentSoftArgb = -1442840576 | accentRgb;
			String[] lines = new String[]{"REC 00:12:34", "60 FPS  drop 0", "Size: 12.3 MB", "1920x1080  Queue: 0/240 (OK)"};
			int maxW = 0;

			for (String l : lines) {
				maxW = Math.max(maxW, tr.method_1727(l));
			}

			int panelWidth = maxW + 22;
			int panelHeight = 12 + lines.length * 11;
			int[] cp = config.classicPanelPos(this.vw, this.vh, panelWidth, panelHeight, 10);
			int x = cp[0];
			int y = cp[1];
			int panelBg = skin != null ? skin.panelBackground : -1728053248;
			context.method_25294(x - 3, y - 3, x + panelWidth, y + panelHeight, panelBg);
			context.method_25294(x - 3, y - 3, x + panelWidth, y - 2, accentSoftArgb);
			context.method_25294(x, y + 3, x + 8, y + 11, accentArgb);
			RenderHelper.drawText(context, tr, class_2561.method_43470(lines[0]), x + 13, y, -1);
			int ly = y + 12;
			RenderHelper.drawText(context, tr, class_2561.method_43470(lines[1]), x, ly, skin != null ? skin.textSecondary : -2039584);
			ly += 11;
			RenderHelper.drawText(context, tr, class_2561.method_43470(lines[2]), x, ly, -1);
			ly += 11;
			RenderHelper.drawText(context, tr, class_2561.method_43470(lines[3]), x, ly, -6561137);
		}
	}

	private void renderSynthwavePreview(class_332 context, RecordableConfig config, class_327 tr) {
		if (config.hudSynthVisible) {
			ThemeColors skin = config.activeOverlaySkinOrNull();
			int magenta = skin != null ? skin.accent : -53867;
			int cyan = skin != null ? skin.accentHover : -16718337;
			int panelBg = skin != null ? skin.panelBackground : -434500818;
			int textColor = skin != null ? skin.textPrimary : cyan;
			String line = "REC 00:12:34";
			int pw = tr.method_1727(line) + 22;
			int ph = 16;
			int[] sp = config.synthPanelPos(this.vw, this.vh, pw, ph, 0, 0);
			int x = sp[0];
			int y = sp[1];
			context.method_25294(x, y, x + pw, y + ph, panelBg);
			context.method_25294(x, y, x + pw, y + 1, magenta);
			context.method_25294(x, y + ph - 1, x + pw, y + ph, cyan);
			context.method_25294(x, y, x + 1, y + ph, magenta);
			context.method_25294(x + pw - 1, y, x + pw, y + ph, cyan);
			context.method_25294(x + 6, y + 5, x + 12, y + 11, magenta);
			RenderHelper.drawText(context, tr, class_2561.method_43470(line), x + 16, y + 4, textColor);
		}
	}

	private void renderOverlayPreview(class_332 context, RecordableConfig config, class_327 tr) {
		if (config.showFiltersLive) {
			for (String layerId : RecordableConfig.FILTER_LAYERS) {
				if (config.isElementVisible(layerId)) {
					FilterType ft = RecordableConfig.filterLayerToType(layerId);
					FilterPreviewRenderer.render(context, this.vw, this.vh, ft, config.getFilterIntensity(layerId));
				}
			}
		}

		OverlayStyleHud style = config.overlayStyleHud != null ? config.overlayStyleHud : OverlayStyleHud.CLASSIC;
		boolean isVhs = style == OverlayStyleHud.VHS;
		if (style == OverlayStyleHud.CLASSIC) {
			this.renderClassicPreview(context, config, tr);
		} else if (style == OverlayStyleHud.SYNTHWAVE) {
			this.renderSynthwavePreview(context, config, tr);
		} else if (isVhs) {
			int playColor = RecordableConfig.applyOpacity(RecordableConfig.parseArgbColor(config.vhsPlayColor, -1), config.hudPlayRecOpacity);
			int recDotColor = RecordableConfig.applyOpacity(RecordableConfig.parseArgbColor(config.vhsRecDotColor, -3400162), config.hudPlayRecOpacity);
			int recTextColor = RecordableConfig.applyOpacity(RecordableConfig.parseArgbColor(config.vhsRecTextColor, -1), config.hudPlayRecOpacity);
			int timestampColor = RecordableConfig.applyOpacity(RecordableConfig.parseArgbColor(config.vhsTimestampColor, -1), config.hudTimestampOpacity);
			int dateColor = RecordableConfig.applyOpacity(RecordableConfig.parseArgbColor(config.vhsDateColor, -1), config.hudDetailsOpacity);
			int spColor = RecordableConfig.applyOpacity(RecordableConfig.parseArgbColor(config.vhsSpColor, -1), config.hudSpOpacity);
			int bracketColor = RecordableConfig.applyOpacity(RecordableConfig.parseArgbColor(config.vhsBracketColor, -922746881), config.hudCornersOpacity);
			int leftInset = config.hudPlayRecX;
			int topInset = config.hudPlayRecY;
			if (config.hudPlayRecVisible) {
				if (config.vhsShowPlay) {
					RenderHelper.drawText(context, tr, class_2561.method_43470("PLAY ▶"), leftInset, topInset, playColor);
				}

				int recY = topInset + (config.vhsShowPlay ? 12 : 0);
				context.method_25294(leftInset, recY + 2, leftInset + 7, recY + 9, recDotColor);
				RenderHelper.drawText(context, tr, class_2561.method_43470("REC"), leftInset + 10, recY, recTextColor);
			}

			if (config.hudTimestampVisible) {
				String timer = "00:12:34";
				int timerW = tr.method_1727(timer);
				RenderHelper.drawText(context, tr, class_2561.method_43470(timer), this.vw - config.hudTimestampOffsetX - timerW, config.hudTimestampY, timestampColor);
			}

			if (config.hudCornersVisible && config.vhsShowBrackets) {
				drawCornerBrackets(
					context,
					config.hudCornersX,
					config.hudCornersY,
					config.hudCornersX + config.hudCornersWidth,
					config.hudCornersY + config.hudCornersHeight,
					20,
					2,
					bracketColor
				);
			}

			if (config.hudSpVisible && config.vhsShowSp) {
				RenderHelper.drawText(context, tr, class_2561.method_43470("SP"), config.hudSpX, this.vh - config.hudSpOffsetY, spColor);
			}

			if (config.hudDetailsVisible) {
				int brX = this.vw - config.hudDetailsOffsetX;
				int detY = this.vh - config.hudDetailsOffsetY;
				if (config.vhsShowDate) {
					LocalDateTime now = LocalDateTime.now();
					String dl = DATE_FMT.format(now);
					String tl = TIME_FMT.format(now).toUpperCase(Locale.ROOT);
					detY -= 22;
					RenderHelper.drawText(context, tr, class_2561.method_43470(tl), brX - tr.method_1727(tl), detY, dateColor);
					detY += 11;
					RenderHelper.drawText(context, tr, class_2561.method_43470(dl), brX - tr.method_1727(dl), detY, dateColor);
					detY = this.vh - config.hudDetailsOffsetY - 26;
				} else {
					detY -= 4;
				}

				if (config.vhsShowTapeCounter) {
					String tc = "TC 0143";
					detY -= 11;
					RenderHelper.drawText(
						context, tr, class_2561.method_43470(tc), brX - tr.method_1727(tc), detY, RecordableConfig.applyOpacity(-3355444, config.hudDetailsOpacity)
					);
				}

				if (config.vhsShowAudioMeter) {
					detY -= 12;
					int mX = brX - 60;
					context.method_25294(mX, detY, mX + 55, detY + 3, -13421773);
					context.method_25294(mX, detY + 4, mX + 55, detY + 7, -13421773);
					context.method_25294(mX, detY, mX + 30, detY + 3, -12268476);
					context.method_25294(mX, detY + 4, mX + 28, detY + 7, -12268476);
				}

				if (config.vhsShowBattery) {
					detY -= 13;
					int bx = brX - 50;
					int bw = 24;
					int bh = 10;
					context.method_25294(bx, detY, bx + bw, detY + bh, -5592406);
					context.method_25294(bx + 1, detY + 1, bx + bw - 1, detY + bh - 1, -14540254);
					context.method_25294(bx + bw, detY + 2, bx + bw + 2, detY + bh - 2, -5592406);
					context.method_25294(bx + 2, detY + 2, bx + 18, detY + bh - 2, -12268476);
					RenderHelper.drawText(context, tr, class_2561.method_43470("98%"), bx + bw + 5, detY + 1, -3355444);
				}
			}

			if (config.hudPerfVisible && config.showPerformanceStats) {
				String[] lines = new String[]{"Cap 60 | Enc 60 FPS", "Mem 1024 MiB | Drop 0", "Queue: 0/240 | 100%"};
				int mw = 0;

				for (String l : lines) {
					mw = Math.max(mw, tr.method_1727(l));
				}

				int pw = mw + 10;
				int ph = 8 + lines.length * 10;
				int ppx = this.vw - pw - config.hudPerfOffsetX;
				int ppy = this.vh - ph - config.hudPerfOffsetY;
				int po = config.hudPerfOpacity;
				context.method_25294(ppx - 2, ppy - 2, ppx + pw, ppy + ph, RecordableConfig.applyOpacity(-2013265920, po));
				int ly = ppy;
				int[] cs = new int[]{-3158065, -5197648, -6561137};

				for (int i = 0; i < lines.length; i++) {
					RenderHelper.drawText(context, tr, class_2561.method_43470(lines[i]), ppx + 2, ly, RecordableConfig.applyOpacity(cs[i], po));
					ly += 10;
				}
			}
		}
	}

	private static void drawCornerBrackets(class_332 ctx, int x1, int y1, int x2, int y2, int len, int t, int c) {
		ctx.method_25294(x1, y1, x1 + len, y1 + t, c);
		ctx.method_25294(x1, y1, x1 + t, y1 + len, c);
		ctx.method_25294(x2 - len, y1, x2, y1 + t, c);
		ctx.method_25294(x2 - t, y1, x2, y1 + len, c);
		ctx.method_25294(x1, y2 - t, x1 + len, y2, c);
		ctx.method_25294(x1, y2 - len, x1 + t, y2, c);
		ctx.method_25294(x2 - len, y2 - t, x2, y2, c);
		ctx.method_25294(x2 - t, y2 - len, x2, y2, c);
	}

	public boolean method_25402(double mouseX, double mouseY, int button) {
		if (super.method_25402(mouseX, mouseY, button)) {
			return true;
		} else {
			int mx = (int)mouseX;
			int my = (int)mouseY;
			int btn = button;
			if (this.panelOpen && mx >= this.field_22789 - 154 - 10) {
				return this.handlePanelClick(mx, my, button);
			} else {
				int smx = (int)((float)mx / this.overlayScale);
				int smy = (int)((float)my / this.overlayScale);

				for (OverlayPositionScreen.DraggableElement el : this.elements) {
					if (el.resizable) {
						OverlayPositionScreen.ResizeEdge edge = this.hitTestResizeHandle(el, smx, smy);
						if (edge != OverlayPositionScreen.ResizeEdge.NONE && btn == 0) {
							this.activeResize = edge;
							this.resizeElement = el;
							this.resizeOrigX = el.x;
							this.resizeOrigY = el.y;
							this.resizeOrigW = el.w;
							this.resizeOrigH = el.h;
							this.dragMouseX = (double)smx;
							this.dragMouseY = (double)smy;
							return true;
						}
					}
				}

				RecordableConfig cfgClick = RecordableConfig.get();
				OverlayPositionScreen.DraggableElement clicked = null;
				List<String> shownClick = this.shownLayers();

				for (int i = shownClick.size() - 1; i >= 0; i--) {
					String lid = (String)shownClick.get(i);
					if (cfgClick.isElementVisible(lid)) {
						for (OverlayPositionScreen.DraggableElement elx : this.elements) {
							if (elx.id.equals(lid)) {
								if (smx < elx.x || smx > elx.x + elx.w || smy < elx.y || smy > elx.y + elx.h) {
									break;
								}

								if (elx.id.equals("Corners")) {
									int cz = 40;
									boolean nearTL = smx < elx.x + cz && smy < elx.y + cz;
									boolean nearTR = smx > elx.x + elx.w - cz && smy < elx.y + cz;
									boolean nearBL = smx < elx.x + cz && smy > elx.y + elx.h - cz;
									boolean nearBR = smx > elx.x + elx.w - cz && smy > elx.y + elx.h - cz;
									if (!nearTL && !nearTR && !nearBL && !nearBR) {
										break;
									}
								}

								clicked = elx;
								break;
							}
						}

						if (clicked != null) {
							break;
						}
					}
				}

				if (clicked == null) {
					for (int ix = this.elements.size() - 1; ix >= 0; ix--) {
						OverlayPositionScreen.DraggableElement elxx = (OverlayPositionScreen.DraggableElement)this.elements.get(ix);
						if (elxx.wmIndex >= 0 && smx >= elxx.x && smx <= elxx.x + elxx.w && smy >= elxx.y && smy <= elxx.y + elxx.h) {
							clicked = elxx;
							break;
						}
					}
				}

				if (clicked == null) {
					return false;
				} else if (btn == 1) {
					this.resetElementToDefault(clicked);
					return true;
				} else if (btn == 0) {
					this.draggedElement = clicked;
					this.dragOffsetX = smx - clicked.x;
					this.dragOffsetY = smy - clicked.y;
					this.dragMouseX = (double)smx;
					this.dragMouseY = (double)smy;
					return true;
				} else {
					return false;
				}
			}
		}
	}

	private boolean handlePanelClick(int mx, int my, int btn) {
		RecordableConfig config = RecordableConfig.get();
		int px = this.field_22789 - 154 - 4;
		int py = 36;
		int innerW = 148;
		int left = px + 3;
		int y = py + 2 - this.panelScroll;
		if (my >= y && my < y + 16 && mx >= left && mx <= left + innerW && btn == 0) {
			this.sectionLayersOpen = !this.sectionLayersOpen;
			return true;
		} else {
			y += 16;
			if (this.sectionLayersOpen) {
				List<String> shown = this.shownLayers();

				for (int i = 0; i < shown.size(); i++) {
					String id = (String)shown.get(i);
					if (my >= y && my < y + 16 && btn == 0) {
						int eyeX = left + 1;
						if (mx >= eyeX && mx <= eyeX + 12) {
							config.setElementVisible(id, !config.isElementVisible(id));
							config.save();
							return true;
						}

						int ax = left + innerW - 14;
						if (i > 0 && mx >= ax && mx <= ax + 14 && my < y + 8) {
							this.moveShownLayer(shown, i, -1);
							return true;
						}

						if (i < shown.size() - 1 && mx >= ax && mx <= ax + 14 && my >= y + 8) {
							this.moveShownLayer(shown, i, 1);
							return true;
						}
					}

					y += 16;
				}
			}

			y += 2;
			if (my >= y && my < y + 16 && mx >= left && mx <= left + innerW && btn == 0) {
				this.sectionOpacityOpen = !this.sectionOpacityOpen;
				return true;
			} else {
				y += 16;
				if (this.sectionOpacityOpen) {
					int barX = left + 2;
					int barW = innerW - 4;

					for (OverlayPositionScreen.OpacityEntry entry : this.opacityEntries) {
						if (my >= y && my < y + 16 - 1) {
							if (btn == 0) {
								int newVal = Math.max(0, Math.min(100, (mx - barX) * 100 / barW));
								entry.setter.accept(newVal);
								this.draggingOpacity = entry;
								config.save();
								return true;
							}

							if (btn == 1) {
								entry.setter.accept(100);
								config.save();
								return true;
							}
						}

						y += 15;
					}
				}

				y += 2;
				if (my >= y && my < y + 16 && mx >= left && mx <= left + innerW && btn == 0) {
					this.sectionWatermarksOpen = !this.sectionWatermarksOpen;
					return true;
				} else {
					y += 16;
					if (this.sectionWatermarksOpen) {
						List<WatermarkSlot> slots = config.watermarkSlots;
						if (slots != null && !slots.isEmpty()) {
							for (int i = 0; i < slots.size(); i++) {
								WatermarkSlot slot = (WatermarkSlot)slots.get(i);
								if (my >= y && my < y + 16 && btn == 0) {
									int ex = left + innerW - 22;
									if (mx >= ex && mx <= ex + 20) {
										this.openWatermarkEditor(i);
										return true;
									}

									if (slot != null) {
										slot.enabled = !slot.enabled;
										config.save();
									}

									return true;
								}

								y += 16;
							}
						} else {
							y += 16;
						}

						if (my >= y && my < y + 16 && mx >= left && mx <= left + innerW && btn == 0) {
							this.openWatermarkEditor(-1);
							return true;
						}

						y += 16;
					}

					return true;
				}
			}
		}
	}

	public boolean method_25403(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		double sdx = deltaX / (double)this.overlayScale;
		double sdy = deltaY / (double)this.overlayScale;
		if (this.draggingOpacity != null && button == 0) {
			int px = this.field_22789 - 154 - 4;
			int barX = px + 5;
			int barW = 144;
			int realMx = (int)mouseX;
			int newVal = Math.max(0, Math.min(100, (realMx - barX) * 100 / barW));
			this.draggingOpacity.setter.accept(newVal);
			return true;
		} else if (this.activeResize != OverlayPositionScreen.ResizeEdge.NONE && this.resizeElement != null && button == 0) {
			this.dragMouseX += sdx;
			this.dragMouseY += sdy;
			this.applyResize(this.resizeElement, (int)this.dragMouseX, (int)this.dragMouseY);
			return true;
		} else if (this.draggedElement != null && button == 0) {
			this.dragMouseX += sdx;
			this.dragMouseY += sdy;
			int nx = Math.max(0, Math.min(this.vw - this.draggedElement.w, (int)this.dragMouseX - this.dragOffsetX));
			int ny = Math.max(0, Math.min(this.vh - this.draggedElement.h, (int)this.dragMouseY - this.dragOffsetY));
			this.applyDragPosition(this.draggedElement, nx, ny);
			return true;
		} else {
			return super.method_25403(mouseX, mouseY, button, deltaX, deltaY);
		}
	}

	public boolean method_25406(double mouseX, double mouseY, int button) {
		this.draggingOpacity = null;
		if (this.activeResize != OverlayPositionScreen.ResizeEdge.NONE && button == 0) {
			this.activeResize = OverlayPositionScreen.ResizeEdge.NONE;
			this.resizeElement = null;
			RecordableConfig.get().save();
			return true;
		} else if (this.draggedElement != null && button == 0) {
			this.draggedElement = null;
			RecordableConfig.get().save();
			return true;
		} else {
			return super.method_25406(mouseX, mouseY, button);
		}
	}

	public boolean method_25401(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (this.panelOpen && mouseX >= (double)(this.field_22789 - 154 - 10)) {
			int delta = (int)Math.round(verticalAmount * -8.0);
			this.panelScroll = Math.max(0, this.panelScroll + delta);
			return true;
		} else {
			return super.method_25401(mouseX, mouseY, horizontalAmount, verticalAmount);
		}
	}

	public boolean method_25404(int keyCode, int scanCode, int modifiers) {
		return super.method_25404(keyCode, scanCode, modifiers);
	}

	public boolean method_25400(char chr, int modifiers) {
		return super.method_25400(chr, modifiers);
	}

	private void applyResize(OverlayPositionScreen.DraggableElement el, int mouseX, int mouseY) {
		RecordableConfig config = RecordableConfig.get();
		int minW = 20;
		int minH = 10;
		int nX = this.resizeOrigX;
		int nY = this.resizeOrigY;
		int nW = this.resizeOrigW;
		int nH = this.resizeOrigH;
		switch (this.activeResize) {
			case TOP_LEFT: {
				int dx = mouseX - this.resizeOrigX;
				int dy = mouseY - this.resizeOrigY;
				nX += dx;
				nY += dy;
				nW -= dx;
				nH -= dy;
				break;
			}
			case TOP_RIGHT: {
				int dy = mouseY - this.resizeOrigY;
				nY += dy;
				nW = mouseX - this.resizeOrigX;
				nH -= dy;
				break;
			}
			case BOTTOM_LEFT: {
				int dx = mouseX - this.resizeOrigX;
				nX += dx;
				nW -= dx;
				nH = mouseY - this.resizeOrigY;
				break;
			}
			case BOTTOM_RIGHT:
				nW = mouseX - this.resizeOrigX;
				nH = mouseY - this.resizeOrigY;
				break;
			default:
				return;
		}

		if (nW < minW) {
			nW = minW;
			nX = this.resizeOrigX + this.resizeOrigW - minW;
		}

		if (nH < minH) {
			nH = minH;
			nY = this.resizeOrigY + this.resizeOrigH - minH;
		}

		nX = Math.max(0, nX);
		nY = Math.max(0, nY);
		nW = Math.max(minW, Math.min(this.vw - nX, nW));
		nH = Math.max(minH, Math.min(this.vh - nY, nH));
		String var21 = el.id;
		switch (var21) {
			case "Corners":
				config.hudCornersX = nX;
				config.hudCornersY = nY;
				config.hudCornersWidth = nW;
				config.hudCornersHeight = nH;
				break;
			case "PLAY/REC":
				config.hudPlayRecX = nX;
				config.hudPlayRecY = nY;
				config.hudPlayRecW = nW;
				config.hudPlayRecH = nH;
				break;
			case "Timestamp":
				config.hudTimestampOffsetX = this.vw - nX - nW;
				config.hudTimestampY = nY;
				config.hudTimestampW = nW;
				config.hudTimestampH = nH;
				break;
			case "SP":
				config.hudSpX = nX;
				config.hudSpOffsetY = this.vh - nY;
				config.hudSpW = nW;
				config.hudSpH = nH;
				break;
			case "Details":
				config.hudDetailsOffsetX = this.vw - nX - nW;
				config.hudDetailsOffsetY = this.vh - nY - nH;
				config.hudDetailsW = nW;
				config.hudDetailsH = nH;
				break;
			case "Perf":
				config.hudPerfOffsetX = this.vw - nX - nW;
				config.hudPerfOffsetY = this.vh - nY - nH;
				config.hudPerfW = nW;
				config.hudPerfH = nH;
		}
	}

	private void applyDragPosition(OverlayPositionScreen.DraggableElement el, int sx, int sy) {
		RecordableConfig config = RecordableConfig.get();
		if (el.wmIndex >= 0) {
			if (config.watermarkSlots != null && el.wmIndex < config.watermarkSlots.size()) {
				WatermarkSlot slot = (WatermarkSlot)config.watermarkSlots.get(el.wmIndex);
				slot.position = Position.CUSTOM;
				slot.customX = sx;
				slot.customY = sy;
			}
		} else {
			String slot = el.id;
			switch (slot) {
				case "PLAY/REC":
					config.hudPlayRecX = sx;
					config.hudPlayRecY = sy;
					break;
				case "Timestamp":
					config.hudTimestampOffsetX = this.vw - sx - el.w;
					config.hudTimestampY = sy;
					break;
				case "Corners":
					config.hudCornersX = sx;
					config.hudCornersY = sy;
					break;
				case "SP":
					config.hudSpX = sx;
					config.hudSpOffsetY = this.vh - sy;
					break;
				case "Details":
					config.hudDetailsOffsetX = this.vw - sx - el.w;
					config.hudDetailsOffsetY = this.vh - sy - el.h;
					break;
				case "Perf":
					config.hudPerfOffsetX = this.vw - sx - el.w;
					config.hudPerfOffsetY = this.vh - sy - el.h;
					break;
				case "Mic":
					config.hudMicX = sx;
					config.hudMicY = sy;
					break;
				case "Classic":
					config.hudClassicX = sx;
					config.hudClassicY = sy;
					break;
				case "Synthwave":
					config.hudSynthX = sx;
					config.hudSynthY = sy;
			}
		}
	}

	private void resetElementToDefault(OverlayPositionScreen.DraggableElement el) {
		RecordableConfig c = RecordableConfig.get();
		if (el.wmIndex >= 0) {
			if (c.watermarkSlots != null && el.wmIndex < c.watermarkSlots.size()) {
				((WatermarkSlot)c.watermarkSlots.get(el.wmIndex)).position = Position.BOTTOM_RIGHT;
				c.save();
			}
		} else {
			String var3 = el.id;
			switch (var3) {
				case "PLAY/REC":
					c.hudPlayRecX = 80;
					c.hudPlayRecY = 14;
					c.hudPlayRecW = 0;
					c.hudPlayRecH = 0;
					break;
				case "Timestamp":
					c.hudTimestampOffsetX = 14;
					c.hudTimestampY = 14;
					c.hudTimestampW = 0;
					c.hudTimestampH = 0;
					break;
				case "Corners":
					c.hudCornersX = 68;
					c.hudCornersY = 4;
					c.hudCornersWidth = 100;
					c.hudCornersHeight = 48;
					break;
				case "SP":
					c.hudSpX = 80;
					c.hudSpOffsetY = 24;
					c.hudSpW = 0;
					c.hudSpH = 0;
					break;
				case "Details":
					c.hudDetailsOffsetX = 14;
					c.hudDetailsOffsetY = 14;
					c.hudDetailsW = 0;
					c.hudDetailsH = 0;
					break;
				case "Perf":
					c.hudPerfOffsetX = 8;
					c.hudPerfOffsetY = 80;
					c.hudPerfW = 0;
					c.hudPerfH = 0;
					break;
				case "Mic":
					c.hudMicX = -1;
					c.hudMicY = 4;
					c.hudMicOpacity = 100;
					break;
				case "Classic":
					c.hudClassicX = -1;
					c.hudClassicY = -1;
					break;
				case "Synthwave":
					c.hudSynthX = -1;
					c.hudSynthY = -1;
			}

			c.save();
		}
	}

	private void resetAllDefaults() {
		RecordableConfig c = RecordableConfig.get();
		c.hudPlayRecX = 80;
		c.hudPlayRecY = 14;
		c.hudTimestampOffsetX = 14;
		c.hudTimestampY = 14;
		c.hudSpX = 80;
		c.hudSpOffsetY = 24;
		c.hudPerfOffsetX = 8;
		c.hudPerfOffsetY = 80;
		c.hudDetailsOffsetX = 14;
		c.hudDetailsOffsetY = 14;
		c.hudCornersX = 68;
		c.hudCornersY = 4;
		c.hudCornersWidth = 100;
		c.hudCornersHeight = 48;
		c.hudPlayRecW = 0;
		c.hudPlayRecH = 0;
		c.hudTimestampW = 0;
		c.hudTimestampH = 0;
		c.hudSpW = 0;
		c.hudSpH = 0;
		c.hudPerfW = 0;
		c.hudPerfH = 0;
		c.hudDetailsW = 0;
		c.hudDetailsH = 0;
		c.hudPlayRecOpacity = 100;
		c.hudTimestampOpacity = 100;
		c.hudCornersOpacity = 100;
		c.hudSpOpacity = 100;
		c.hudDetailsOpacity = 100;
		c.hudPerfOpacity = 100;
		c.hudPlayRecVisible = true;
		c.hudTimestampVisible = true;
		c.hudCornersVisible = true;
		c.hudSpVisible = true;
		c.hudDetailsVisible = true;
		c.hudPerfVisible = true;
		c.hudMicX = -1;
		c.hudMicY = 4;
		c.hudMicOpacity = 100;
		c.hudMicVisible = true;
		this.layerOrder.clear();

		for (String s : DEF_LAYER_ORDER.split(",")) {
			this.layerOrder.add(s.trim());
		}

		c.hudLayerOrder = DEF_LAYER_ORDER;
		c.save();
	}

	public void method_25419() {
		this.cancelAndClose();
	}

	private void cancelAndClose() {
		if (!this.cancelled) {
			this.cancelled = true;
			this.restoreAll(RecordableConfig.get());
		}

		if (this.field_22787 != null) {
			this.field_22787.method_1507(this.parent);
		}
	}

	private void saveAndClose() {
		RecordableConfig c = RecordableConfig.get();
		c.hudLayerOrder = String.join(",", this.layerOrder);
		c.sanitize();
		c.save();
		this.cancelled = true;
		if (this.field_22787 != null) {
			this.field_22787.method_1507(this.parent);
		}
	}

	private static enum AnchorMode {
		TOP_LEFT,
		TOP_RIGHT,
		BOTTOM_LEFT,
		BOTTOM_RIGHT,
		RECT;
	}

	private static final class DraggableElement {
		final String id;
		final String label;
		final OverlayPositionScreen.AnchorMode anchor;
		int x;
		int y;
		int w;
		int h;
		int autoW;
		int autoH;
		int wmIndex = -1;
		boolean resizable = true;

		DraggableElement(String id, String label, OverlayPositionScreen.AnchorMode anchor) {
			this.id = id;
			this.label = label;
			this.anchor = anchor;
		}

		String getDisplayCoords() {
			return switch (this.anchor) {
				case TOP_LEFT -> this.x + "," + this.y;
				case TOP_RIGHT -> "←" + this.x + " " + this.y;
				case BOTTOM_LEFT -> this.x + " ↑" + this.y;
				case BOTTOM_RIGHT -> "←" + this.x + " ↑" + this.y;
				case RECT -> this.x + "," + this.y + " " + this.w + "×" + this.h;
			};
		}
	}

	private static final class OpacityEntry {
		final String id;
		final String label;
		final Supplier<Integer> getter;
		final Consumer<Integer> setter;

		OpacityEntry(String id, String label, Supplier<Integer> getter, Consumer<Integer> setter) {
			this.id = id;
			this.label = label;
			this.getter = getter;
			this.setter = setter;
		}
	}

	private static enum ResizeEdge {
		NONE,
		TOP_LEFT,
		TOP_RIGHT,
		BOTTOM_LEFT,
		BOTTOM_RIGHT;
	}
}
