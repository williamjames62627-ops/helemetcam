package dev.recordable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CaptureDiagnostics {
	private CaptureDiagnostics() {
	}

	public static List<CaptureDiagnostics.Check> buildReport(CaptureDiagnostics.Inputs in) {
		List<CaptureDiagnostics.Check> checks = new ArrayList();
		checks.add(new CaptureDiagnostics.Check(CaptureDiagnostics.Status.INFO, "Window framebuffer", in.windowWidth() + " x " + in.windowHeight()));
		boolean rtKnown = in.renderTargetWidth() > 0 && in.renderTargetHeight() > 0;
		if (rtKnown) {
			checks.add(new CaptureDiagnostics.Check(CaptureDiagnostics.Status.INFO, "Game render target", in.renderTargetWidth() + " x " + in.renderTargetHeight()));
			boolean mismatch = in.renderTargetWidth() != in.windowWidth() || in.renderTargetHeight() != in.windowHeight();
			if (mismatch) {
				checks.add(
					new CaptureDiagnostics.Check(
						CaptureDiagnostics.Status.FAIL,
						"Size match",
						"Window ("
							+ in.windowWidth()
							+ "x"
							+ in.windowHeight()
							+ ") and render target ("
							+ in.renderTargetWidth()
							+ "x"
							+ in.renderTargetHeight()
							+ ") differ. Capture may read the wrong region or appear cropped/blank. This usually means the window was resized mid-capture or a shader mod (Iris/OptiFine) is drawing into a different-sized buffer."
					)
				);
			} else {
				checks.add(new CaptureDiagnostics.Check(CaptureDiagnostics.Status.OK, "Size match", "Window and render target sizes agree."));
			}
		} else {
			checks.add(
				new CaptureDiagnostics.Check(CaptureDiagnostics.Status.INFO, "Game render target", "size unavailable on this version (window size used instead).")
			);
		}

		checks.add(new CaptureDiagnostics.Check(CaptureDiagnostics.Status.INFO, "GUI scale", in.guiScale() > 0 ? String.valueOf(in.guiScale()) : "unknown"));
		checks.add(new CaptureDiagnostics.Check(CaptureDiagnostics.Status.INFO, "Recording active", in.recordingActive() ? "yes" : "no"));
		CaptureDiagnostics.LiveStats live = in.liveStats();
		if (live != null) {
			checks.add(new CaptureDiagnostics.Check(CaptureDiagnostics.Status.INFO, "Frames produced", String.valueOf(live.framesProduced())));
			checks.add(new CaptureDiagnostics.Check(CaptureDiagnostics.Status.INFO, "Capture source", live.sourceName()));
			if (live.persistentBlack()) {
				checks.add(
					new CaptureDiagnostics.Check(
						CaptureDiagnostics.Status.FAIL,
						"Black frames",
						"Recording is persistently black after trying every capture source. "
							+ live.blackFrames()
							+ " black frame(s) so far. Your GPU driver may block glReadPixels, or a shader mod is intercepting the framebuffer."
					)
				);
			} else if (live.consecutiveBlack() <= 0 && live.blackFrames() <= 0L) {
				checks.add(new CaptureDiagnostics.Check(CaptureDiagnostics.Status.OK, "Black frames", "none detected."));
			} else {
				checks.add(
					new CaptureDiagnostics.Check(
						CaptureDiagnostics.Status.WARN,
						"Black frames",
						live.blackFrames() + " black frame(s) seen (" + live.consecutiveBlack() + " in a row right now). Auto-recovery is cycling the capture source."
					)
				);
			}

			if (live.sizeMismatch()) {
				checks.add(
					new CaptureDiagnostics.Check(
						CaptureDiagnostics.Status.WARN,
						"Live size check",
						"The window size changed away from the recording size (" + live.sourceWidth() + "x" + live.sourceHeight() + "). Frames are being rescaled to fit."
					)
				);
			}
		}

		CaptureDiagnostics.SelfTestResult st = in.selfTest();
		if (st == null) {
			checks.add(new CaptureDiagnostics.Check(CaptureDiagnostics.Status.INFO, "Self-test", "running... (open this screen from in-game so a frame can be drawn)."));
		} else if (!st.producedFrame()) {
			checks.add(
				new CaptureDiagnostics.Check(
					CaptureDiagnostics.Status.FAIL,
					"Self-test",
					"Could not read a frame from the GPU. " + (st.note() == null ? "" : st.note()) + " glReadPixels returned nothing - capture cannot work in this state."
				)
			);
		} else if (st.black()) {
			checks.add(
				new CaptureDiagnostics.Check(
					CaptureDiagnostics.Status.FAIL,
					"Self-test",
					"The captured test frame is black (avg brightness "
						+ fmt(st.avgBrightness())
						+ "). The pipeline reads an empty framebuffer, so the GUI/world is not reaching the captured surface."
				)
			);
		} else {
			checks.add(
				new CaptureDiagnostics.Check(
					CaptureDiagnostics.Status.OK,
					"Self-test",
					"Captured a normal frame at " + st.width() + "x" + st.height() + " (avg brightness " + fmt(st.avgBrightness()) + ")."
				)
			);
		}

		return checks;
	}

	public static CaptureDiagnostics.Status overallVerdict(List<CaptureDiagnostics.Check> checks) {
		CaptureDiagnostics.Status worst = CaptureDiagnostics.Status.OK;

		for (CaptureDiagnostics.Check c : checks) {
			if (c.status() == CaptureDiagnostics.Status.FAIL) {
				return CaptureDiagnostics.Status.FAIL;
			}

			if (c.status() == CaptureDiagnostics.Status.WARN) {
				worst = CaptureDiagnostics.Status.WARN;
			}
		}

		return worst;
	}

	public static String verdictSummary(CaptureDiagnostics.Status verdict) {
		return switch (verdict) {
			case FAIL -> "Capture problem detected - see the failed checks below.";
			case WARN -> "Capture works but something needs attention.";
			default -> "Capture looks healthy.";
		};
	}

	private static String fmt(double v) {
		return String.format(Locale.ROOT, "%.1f", v);
	}

	public static int[] readRenderTargetSize(Object target) {
		if (target == null) {
			return null;
		} else {
			int w = readIntMember(target, "getWidth", "width", "textureWidth", "viewportWidth");
			int h = readIntMember(target, "getHeight", "height", "textureHeight", "viewportHeight");
			return w > 0 && h > 0 ? new int[]{w, h} : null;
		}
	}

	private static int readIntMember(Object obj, String... names) {
		Class<?> cls = obj.getClass();

		for (String name : names) {
			try {
				Method m = cls.getMethod(name);
				if (m.invoke(obj) instanceof Integer i && i > 0) {
					return i;
				}
			} catch (Throwable var11) {
			}

			try {
				Field f = cls.getField(name);
				if (f.get(obj) instanceof Integer i && i > 0) {
					return i;
				}
			} catch (Throwable var10) {
			}
		}

		return -1;
	}

	public static record Check(CaptureDiagnostics.Status status, String label, String detail) {
	}

	public static record Inputs(
		int windowWidth,
		int windowHeight,
		int renderTargetWidth,
		int renderTargetHeight,
		int guiScale,
		boolean recordingActive,
		CaptureDiagnostics.LiveStats liveStats,
		CaptureDiagnostics.SelfTestResult selfTest
	) {
	}

	public static record LiveStats(
		long framesProduced,
		long blackFrames,
		int consecutiveBlack,
		boolean persistentBlack,
		String sourceName,
		int sourceWidth,
		int sourceHeight,
		int renderTargetWidth,
		int renderTargetHeight,
		boolean sizeMismatch
	) {
	}

	public static record SelfTestResult(boolean producedFrame, boolean black, double avgBrightness, int width, int height, String note) {
	}

	public static enum Status {
		OK,
		WARN,
		FAIL,
		INFO;
	}
}
