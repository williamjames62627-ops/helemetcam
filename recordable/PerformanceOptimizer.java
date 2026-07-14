package dev.recordable;

import java.util.ArrayList;
import java.util.List;

public final class PerformanceOptimizer {
	private static final PerformanceOptimizer INSTANCE = new PerformanceOptimizer();
	private static final int LOW_FPS_SAMPLES = 3;
	private static final long COOLDOWN_MS = 8000L;
	private int lowFpsStreak = 0;
	private long lastActionAtMs = 0L;
	private int escalationLevel = 0;
	private final List<PerformanceOptimizer.Action> appliedThisSession = new ArrayList();

	public static PerformanceOptimizer getInstance() {
		return INSTANCE;
	}

	private PerformanceOptimizer() {
	}

	public synchronized void reset() {
		this.lowFpsStreak = 0;
		this.lastActionAtMs = 0L;
		this.escalationLevel = 0;
		this.appliedThisSession.clear();
	}

	public synchronized List<PerformanceOptimizer.Action> getAppliedActions() {
		return List.copyOf(this.appliedThisSession);
	}

	public synchronized PerformanceOptimizer.Recommendation evaluate(int currentFps, RecordableConfig config) {
		if (config != null && config.perfOptimizerEnabled) {
			int target = Math.max(10, config.perfMinFps);
			if (currentFps >= target) {
				this.lowFpsStreak = Math.max(0, this.lowFpsStreak - 1);
				return PerformanceOptimizer.Recommendation.NONE;
			} else {
				this.lowFpsStreak++;
				if (this.lowFpsStreak < 3) {
					return PerformanceOptimizer.Recommendation.NONE;
				} else {
					long now = System.currentTimeMillis();
					if (now - this.lastActionAtMs < 8000L) {
						return PerformanceOptimizer.Recommendation.NONE;
					} else {
						PerformanceOptimizer.Action next = this.nextEnabledAction(config);
						if (next == PerformanceOptimizer.Action.NONE) {
							return PerformanceOptimizer.Recommendation.NONE;
						} else {
							String reason = "FPS " + currentFps + " below target " + target;
							boolean confirm = config.perfWarnBeforeAdjust;
							if (config.perfAutoAdjust && !confirm) {
								this.markApplied(next, now);
							}

							return new PerformanceOptimizer.Recommendation(next, reason, confirm || !config.perfAutoAdjust);
						}
					}
				}
			}
		} else {
			return PerformanceOptimizer.Recommendation.NONE;
		}
	}

	public synchronized void markApplied(PerformanceOptimizer.Action action, long whenMs) {
		if (action != null && action != PerformanceOptimizer.Action.NONE) {
			this.appliedThisSession.add(action);
			this.escalationLevel++;
			this.lastActionAtMs = whenMs;
			this.lowFpsStreak = 0;
		}
	}

	private PerformanceOptimizer.Action nextEnabledAction(RecordableConfig config) {
		PerformanceOptimizer.Action[] order = config.perfModeGamePriority
			? new PerformanceOptimizer.Action[]{
				PerformanceOptimizer.Action.FASTER_PRESET, PerformanceOptimizer.Action.LOWER_FPS, PerformanceOptimizer.Action.LOWER_RESOLUTION
			}
			: new PerformanceOptimizer.Action[]{
				PerformanceOptimizer.Action.FASTER_PRESET, PerformanceOptimizer.Action.LOWER_FPS, PerformanceOptimizer.Action.LOWER_RESOLUTION
			};

		for (PerformanceOptimizer.Action a : order) {
			if (!this.appliedThisSession.contains(a) && this.isEnabled(a, config)) {
				return a;
			}
		}

		return PerformanceOptimizer.Action.NONE;
	}

	private boolean isEnabled(PerformanceOptimizer.Action a, RecordableConfig config) {
		return switch (a) {
			case FASTER_PRESET -> config.perfActionFasterPreset;
			case LOWER_FPS -> config.perfActionLowerFps;
			case LOWER_RESOLUTION -> config.perfActionLowerRes;
			case NONE -> false;
		};
	}

	public static String describe(PerformanceOptimizer.Action a) {
		return switch (a) {
			case FASTER_PRESET -> "Switched to a faster encoder preset";
			case LOWER_FPS -> "Lowered recording frame rate";
			case LOWER_RESOLUTION -> "Lowered recording resolution";
			case NONE -> "No change";
		};
	}

	public static enum Action {
		NONE,
		FASTER_PRESET,
		LOWER_FPS,
		LOWER_RESOLUTION;
	}

	public static record Recommendation(PerformanceOptimizer.Action action, String reason, boolean needsConfirmation) {
		public static final PerformanceOptimizer.Recommendation NONE = new PerformanceOptimizer.Recommendation(PerformanceOptimizer.Action.NONE, "", false);
	}
}
