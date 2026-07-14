package dev.recordable;

import java.util.ArrayList;
import java.util.List;

public final class MicrophoneState {
	private static final Object LOCK = new Object();
	private static volatile boolean micCapturing = false;
	private static volatile boolean pushToTalkMode = false;
	private static volatile boolean pushToTalkHeld = false;
	private static volatile long micStartNanos = 0L;
	private static final List<long[]> heldIntervals = new ArrayList();

	private MicrophoneState() {
	}

	public static void beginRecording(boolean capturing, boolean pttMode, long startNanos) {
		synchronized (LOCK) {
			micCapturing = capturing;
			pushToTalkMode = pttMode;
			micStartNanos = startNanos;
			pushToTalkHeld = false;
			heldIntervals.clear();
		}
	}

	public static void endRecording() {
		synchronized (LOCK) {
			closeOpenIntervalLocked(System.nanoTime());
			micCapturing = false;
			pushToTalkHeld = false;
		}
	}

	public static void setPushToTalkHeld(boolean held) {
		synchronized (LOCK) {
			if (micCapturing && pushToTalkMode) {
				long now = System.nanoTime();
				if (held && !pushToTalkHeld) {
					heldIntervals.add(new long[]{now, 0L});
				} else if (!held && pushToTalkHeld) {
					closeOpenIntervalLocked(now);
				}
			}

			pushToTalkHeld = held;
		}
	}

	private static void closeOpenIntervalLocked(long endNanos) {
		if (!heldIntervals.isEmpty()) {
			long[] last = (long[])heldIntervals.get(heldIntervals.size() - 1);
			if (last[1] == 0L) {
				last[1] = endNanos;
			}
		}
	}

	public static boolean isPushToTalkMode() {
		return pushToTalkMode;
	}

	public static boolean isPushToTalkHeld() {
		return pushToTalkHeld;
	}

	public static boolean isMicActiveForDisplay() {
		return !micCapturing ? false : !pushToTalkMode || pushToTalkHeld;
	}

	public static boolean isMicCapturing() {
		return micCapturing;
	}

	public static List<double[]> getPushToTalkIntervalsSeconds(long micStreamStartNanos) {
		if (!pushToTalkMode) {
			return null;
		} else {
			long ref = micStreamStartNanos > 0L ? micStreamStartNanos : micStartNanos;
			List<double[]> out = new ArrayList();
			synchronized (LOCK) {
				long now = System.nanoTime();

				for (long[] iv : heldIntervals) {
					long s = iv[0];
					long e = iv[1] == 0L ? now : iv[1];
					double ss = (double)(s - ref) / 1.0E9;
					double ee = (double)(e - ref) / 1.0E9;
					if (!(ee <= 0.0)) {
						if (ss < 0.0) {
							ss = 0.0;
						}

						if (ee > ss) {
							out.add(new double[]{ss, ee});
						}
					}
				}

				return out;
			}
		}
	}
}
