package dev.recordable;

import dev.recordable.FFmpegEncoder.FfmpegStatus;
import dev.recordable.RecordableConfig.CaptureDimensions;
import dev.recordable.RecordingManager.StopReason;
import java.lang.ref.WeakReference;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.class_1297;
import net.minecraft.class_1309;
import net.minecraft.class_1657;
import net.minecraft.class_1676;
import net.minecraft.class_1937;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_310;
import net.minecraft.class_5321;

public final class AutoClipManager {
	private static final int TICKS_PER_SECOND = 20;
	private volatile int autoClipStopCountdown = -1;
	private volatile String autoClipReason = null;
	private volatile boolean autoClipOwnsRecording = false;
	private volatile long lastAutoClipTriggerMs = 0L;
	private static final long AUTO_CLIP_COOLDOWN_MS = 5000L;
	private volatile class_5321<class_1937> lastDimension = null;
	private volatile boolean wasPlayerAlive = true;
	private volatile int lastAdvancementCount = -1;
	private static final int KILL_TRACK_WINDOW_TICKS = 200;
	private WeakReference<class_1309> killTrackTarget = null;
	private boolean killTrackTargetIsPlayer = false;
	private int killTrackDeadlineTicks = -1;
	private WeakReference<class_1309> killLastProcessed = null;
	private int ticksSincePlayerSwing = 99999;
	private static final int SWING_GRACE_TICKS = 20;
	private static final double FALLBACK_REACH = 10.0;

	public void initialize() {
		RecordableMod.LOGGER.info("AutoClipManager initialized.");
	}

	private static String nameOf(class_1309 e) {
		try {
			return e.method_5477() != null ? e.method_5477().getString() : String.valueOf(e.method_5864());
		} catch (Throwable var2) {
			return "entity";
		}
	}

	private void beginTracking(class_1309 living, String how) {
		this.killTrackTarget = new WeakReference(living);
		this.killTrackTargetIsPlayer = living instanceof class_1657;
		this.killTrackDeadlineTicks = 200;
		RecordableMod.LOGGER.debug("[AutoClip] Tracking entity for kill detection ({}): {}", how, nameOf(living));
		this.armKillMontageIfEnabled();
	}

	private void updatePlayerSwingTracker(class_310 client) {
		if (client.field_1724 != null && isPlayerCombatActive(client)) {
			this.ticksSincePlayerSwing = 0;
		} else if (this.ticksSincePlayerSwing < 99999) {
			this.ticksSincePlayerSwing++;
		}
	}

	private static boolean isPlayerCombatActive(class_310 client) {
		if (client.field_1724 != null && client.field_1724.field_6252) {
			return true;
		} else {
			try {
				if (client.field_1690 != null) {
					if (client.field_1690.field_1886 != null && client.field_1690.field_1886.method_1434()) {
						return true;
					}

					if (client.field_1690.field_1904 != null && client.field_1690.field_1904.method_1434()) {
						return true;
					}
				}
			} catch (Throwable var2) {
			}

			return false;
		}
	}

	private class_1309 findAimedDamagedEntity(class_310 client, class_1309 processed) {
		class_1657 player = client.field_1724;
		class_243 eye = player.method_33571();
		class_243 look = player.method_5828(1.0F);
		class_243 end = eye.method_1019(look.method_1021(10.0));
		class_238 searchBox = player.method_5829().method_18804(look.method_1021(10.0)).method_1014(1.0);
		class_1309 best = null;
		double bestDist = Double.MAX_VALUE;

		for (class_1297 e : client.field_1687.method_8335(player, searchBox)) {
			if (e instanceof class_1309) {
				class_1309 living = (class_1309)e;
				if (living != processed && !living.method_31481() && (living.field_6235 > 0 || !(living.method_6032() > 0.0F))) {
					Optional<class_243> hit = living.method_5829().method_1014(0.3).method_992(eye, end);
					if (!hit.isEmpty()) {
						double d = eye.method_1025((class_243)hit.get());
						if (d < bestDist) {
							bestDist = d;
							best = living;
						}
					}
				}
			}
		}

		return best;
	}

	public void onPlayerAttackEntity(class_1297 target) {
		if (target instanceof class_1309 living) {
			if (living != client() && !living.method_31481()) {
				class_1309 processed = this.killLastProcessed != null ? (class_1309)this.killLastProcessed.get() : null;
				if (living != processed) {
					this.beginTracking(living, "direct melee attack");
				}
			}
		}
	}

	private static class_1309 client() {
		class_310 mc = class_310.method_1551();
		return mc != null ? mc.field_1724 : null;
	}

	private void armKillMontageIfEnabled() {
		try {
			RecordableConfig config = RecordableConfig.get();
			if (!config.autoClipEnabled) {
				return;
			}

			if (!config.autoClipOnKill && !config.autoClipOnPlayerKill) {
				return;
			}

			if (!config.autoClipKillMontage) {
				return;
			}

			int[] dims = resolveMontageDimensions();
			if (dims == null) {
				return;
			}

			KillClipBuffer.getInstance().arm(class_310.method_1551(), dims[0], dims[1], dims[2], config.autoClipKillPreSeconds, config.autoClipKillPostSeconds);
		} catch (Throwable var3) {
			RecordableMod.LOGGER.debug("Kill-montage arm skipped.", var3);
		}
	}

	private static int[] resolveMontageDimensions() {
		try {
			class_310 client = class_310.method_1551();
			if (client != null && client.method_22683() != null) {
				int nativeWidth = client.method_22683().method_4489();
				int nativeHeight = client.method_22683().method_4506();
				if (nativeWidth > 0 && nativeHeight > 0) {
					RecordableConfig config = RecordableConfig.get();
					CaptureDimensions d = config.resolveCaptureDimensions(nativeWidth, nativeHeight);
					return new int[]{Math.max(2, d.width()), Math.max(2, d.height()), config.autoClipFps};
				} else {
					return null;
				}
			} else {
				return null;
			}
		} catch (Throwable var5) {
			return null;
		}
	}

	private void fireKill(class_310 client, RecordableConfig config, String reason) {
		if (config.autoClipKillMontage) {
			long now = System.currentTimeMillis();
			if (now - this.lastAutoClipTriggerMs < 5000L) {
				RecordableMod.LOGGER.debug("Kill montage skipped (cooldown): {}", reason);
				return;
			}

			this.lastAutoClipTriggerMs = now;
			int[] dims = resolveMontageDimensions();
			if (dims == null) {
				this.triggerAutoClip(client, config, reason);
				return;
			}

			String prefix = sanitizeForFilename(reason);
			KillClipBuffer.getInstance().triggerKill(client, reason, prefix, dims[0], dims[1], dims[2], config.autoClipKillPreSeconds, config.autoClipKillPostSeconds);
		} else {
			this.triggerAutoClip(client, config, reason);
		}
	}

	public void onClientTick(class_310 client) {
		if (client != null && client.field_1724 != null && client.field_1687 != null) {
			this.updatePlayerSwingTracker(client);

			RecordableConfig config;
			try {
				config = RecordableConfig.get();
			} catch (Throwable var4) {
				return;
			}

			if (this.autoClipStopCountdown >= 0) {
				this.autoClipStopCountdown--;
				if (this.autoClipStopCountdown < 0) {
					this.stopAutoClip(client);
				}
			} else if (!config.autoClipEnabled) {
				this.updateTrackedState(client);
			} else {
				if (config.autoClipOnDeath) {
					this.checkDeathTrigger(client, config);
				}

				if (config.autoClipOnDimensionChange) {
					this.checkDimensionChangeTrigger(client, config);
				}

				if (config.autoClipOnAchievement) {
					this.checkAchievementTrigger(client, config);
				}

				if (config.autoClipOnBossKill) {
				}

				if (config.autoClipOnKill || config.autoClipOnPlayerKill) {
					this.scanForPlayerProjectileHits(client);
					this.scanForRecentlyDamagedEntities(client);
					this.checkKillTrigger(client, config);
				}

				this.updateTrackedState(client);
			}
		} else {
			this.lastDimension = null;
			this.wasPlayerAlive = true;
			this.lastAdvancementCount = -1;
			this.killTrackTarget = null;
			this.killTrackDeadlineTicks = -1;
			this.ticksSincePlayerSwing = 99999;
		}
	}

	private void checkKillTrigger(class_310 client, RecordableConfig config) {
		class_1309 tracked = this.killTrackTarget != null ? (class_1309)this.killTrackTarget.get() : null;
		if (tracked != null) {
			float health = tracked.method_6032();
			boolean died = health <= 0.0F || tracked.method_29504();
			boolean removedWhileAlive = tracked.method_31481() && health > 0.0F;
			if (died) {
				boolean isPlayer = this.killTrackTargetIsPlayer;
				this.killLastProcessed = new WeakReference(tracked);
				this.killTrackTarget = null;
				this.killTrackDeadlineTicks = -1;
				String name = nameOf(tracked);
				RecordableMod.LOGGER.info("[AutoClip] Confirmed local-player kill: {} (health={}, pvp={})", new Object[]{name, health, isPlayer});
				if (isPlayer) {
					if (config.autoClipOnPlayerKill) {
						this.fireKill(client, config, "Player Kill");
					}
				} else if (config.autoClipOnKill) {
					this.fireKill(client, config, "Kill: " + name);
				}
			} else if (removedWhileAlive) {
				RecordableMod.LOGGER.debug("[AutoClip] Tracked entity removed while alive; not crediting a kill: {}", nameOf(tracked));
				this.killTrackTarget = null;
				this.killTrackDeadlineTicks = -1;
			} else if (--this.killTrackDeadlineTicks < 0) {
				RecordableMod.LOGGER.debug("[AutoClip] Kill-tracking window expired without a kill: {}", nameOf(tracked));
				this.killTrackTarget = null;
			}
		}
	}

	private void scanForRecentlyDamagedEntities(class_310 client) {
		try {
			if (client.field_1724 == null || client.field_1687 == null) {
				return;
			}

			class_1309 current = this.killTrackTarget != null ? (class_1309)this.killTrackTarget.get() : null;
			if (current != null && current.method_5805()) {
				return;
			}

			if (this.ticksSincePlayerSwing > 20) {
				return;
			}

			class_1309 processed = this.killLastProcessed != null ? (class_1309)this.killLastProcessed.get() : null;
			class_1309 living = this.findAimedDamagedEntity(client, processed);
			if (living == null) {
				return;
			}

			this.beginTracking(living, "melee/spear fallback (recent swing + aim)");
		} catch (Throwable var5) {
			RecordableMod.LOGGER.debug("Recent damage scan failed.", var5);
		}
	}

	private void scanForPlayerProjectileHits(class_310 client) {
		try {
			if (client.field_1724 == null || client.field_1687 == null) {
				return;
			}

			class_1309 current = this.killTrackTarget != null ? (class_1309)this.killTrackTarget.get() : null;
			if (current != null && current.method_5805()) {
				return;
			}

			class_1309 processed = this.killLastProcessed != null ? (class_1309)this.killLastProcessed.get() : null;

			for (class_1297 e : client.field_1687.method_18112()) {
				if (e instanceof class_1676 proj && proj.method_24921() == client.field_1724) {
					class_238 hitBox = proj.method_5829().method_1014(1.0);

					for (class_1297 victim : client.field_1687.method_8335(client.field_1724, hitBox)) {
						if (victim instanceof class_1309) {
							class_1309 living = (class_1309)victim;
							if (living != processed && !living.method_31481()) {
								boolean hurtOrDying = living.field_6235 > 0 || living.method_6032() <= 0.0F;
								if (hurtOrDying) {
									this.beginTracking(living, "player-owned projectile hit");
									return;
								}
							}
						}
					}
				}
			}
		} catch (Throwable var12) {
			RecordableMod.LOGGER.debug("Projectile kill scan failed.", var12);
		}
	}

	private void updateTrackedState(class_310 client) {
		if (client.field_1724 != null) {
			this.wasPlayerAlive = client.field_1724.method_5805();
			this.lastDimension = client.field_1687 != null ? client.field_1687.method_27983() : null;
		}
	}

	private void checkDeathTrigger(class_310 client, RecordableConfig config) {
		if (client.field_1724 != null) {
			boolean isAlive = client.field_1724.method_5805();
			if (this.wasPlayerAlive && !isAlive) {
				this.triggerAutoClip(client, config, "Player Death");
			}

			this.wasPlayerAlive = isAlive;
		}
	}

	private void checkDimensionChangeTrigger(class_310 client, RecordableConfig config) {
		if (client.field_1687 != null) {
			class_5321<class_1937> currentDimension = client.field_1687.method_27983();
			if (this.lastDimension != null && !this.lastDimension.equals(currentDimension)) {
				String dimName = getDimensionDisplayName(currentDimension);
				this.triggerAutoClip(client, config, "Entered " + dimName);
			}

			this.lastDimension = currentDimension;
		}
	}

	private void checkAchievementTrigger(class_310 client, RecordableConfig config) {
		try {
			if (client.field_1724 != null && client.field_1724.field_3944 != null) {
			}
		} catch (Throwable var4) {
		}
	}

	public void onBossKilled(class_310 client, String bossName) {
		RecordableConfig config;
		try {
			config = RecordableConfig.get();
		} catch (Throwable var5) {
			return;
		}

		if (config.autoClipEnabled) {
			if (config.autoClipOnBossKill) {
				this.triggerAutoClip(client, config, "Boss Killed: " + bossName);
			}
		}
	}

	public void onAdvancementEarned(class_310 client, String advancementTitle) {
		RecordableConfig config;
		try {
			config = RecordableConfig.get();
		} catch (Throwable var5) {
			return;
		}

		if (config.autoClipEnabled) {
			if (config.autoClipOnAchievement) {
				this.triggerAutoClip(client, config, "Achievement: " + advancementTitle);
			}
		}
	}

	private void triggerAutoClip(class_310 client, RecordableConfig config, String reason) {
		long now = System.currentTimeMillis();
		if (now - this.lastAutoClipTriggerMs < 5000L) {
			RecordableMod.LOGGER.debug("Auto-clip skipped (cooldown): {}", reason);
		} else {
			this.lastAutoClipTriggerMs = now;
			FfmpegStatus ffStatus = FFmpegEncoder.detectFfmpeg();
			if (!ffStatus.found()) {
				RecordableMod.LOGGER.warn("Auto-clip skipped (FFmpeg not found): {}", reason);
			} else if (!RecordingManager.getInstance().isRecording() && !RecordingManager.getInstance().isPaused()) {
				this.autoClipReason = reason;
				this.autoClipStopCountdown = config.autoClipDuration * 20;
				this.autoClipOwnsRecording = true;
				RecordableMod.LOGGER.info("Auto-clip triggered: {} (duration: {}s)", reason, config.autoClipDuration);
				RecordableMod.sendClientMessage(
					ChatCategory.CLIPS, client, "§a\ud83c\udfac Auto-clip started: §f" + reason + " §7(" + config.autoClipDuration + "s)", false
				);
				String filePrefix = sanitizeForFilename(reason);
				RecordingManager.getInstance().startRecording(client, filePrefix);
			} else {
				RecordableMod.LOGGER.info("Auto-clip event '{}' detected, but a recording is already in progress; leaving it running.", reason);
				RecordableMod.sendClientMessage(ChatCategory.CLIPS, client, "§a\ud83c\udfac Clip-worthy moment: §f" + reason + " §7(already recording)", false);
			}
		}
	}

	private static String sanitizeForFilename(String reason) {
		if (reason != null && !reason.isEmpty()) {
			String lower = reason.toLowerCase(Locale.ROOT);
			if (lower.contains("death")) {
				return "on-death";
			} else if (lower.contains("achievement")) {
				return "on-achievement";
			} else if (lower.contains("nether") || lower.contains("end") || lower.contains("overworld") || lower.contains("dimension")) {
				return "on-dimension";
			} else if (lower.contains("boss")) {
				return "on-boss";
			} else if (lower.contains("player") && lower.contains("kill")) {
				return "on-player-kill";
			} else {
				return lower.contains("kill") ? "on-kill" : "auto-clip";
			}
		} else {
			return "auto-clip";
		}
	}

	private void stopAutoClip(class_310 client) {
		class_310 activeClient = client != null ? client : class_310.method_1551();
		String reason = this.autoClipReason != null ? this.autoClipReason : "Auto-clip";
		this.autoClipReason = null;
		boolean owned = this.autoClipOwnsRecording;
		this.autoClipOwnsRecording = false;
		if (owned && (RecordingManager.getInstance().isRecording() || RecordingManager.getInstance().isPaused())) {
			RecordableMod.LOGGER.info("Auto-clip stopping: {}", reason);
			RecordableMod.sendClientMessage(ChatCategory.CLIPS, activeClient, "§e\ud83c\udfac Auto-clip saved: §f" + reason, false);
			RecordingManager.getInstance().stopRecording(activeClient, StopReason.AUTO);
		}
	}

	public boolean isAutoClipActive() {
		return this.autoClipStopCountdown >= 0;
	}

	public int getAutoClipRemainingSeconds() {
		int countdown = this.autoClipStopCountdown;
		return countdown >= 0 ? countdown / 20 : -1;
	}

	private static String getDimensionDisplayName(class_5321<class_1937> dimension) {
		if (dimension == null) {
			return "Unknown";
		} else if (class_1937.field_25179.equals(dimension)) {
			return "Overworld";
		} else if (class_1937.field_25180.equals(dimension)) {
			return "The Nether";
		} else {
			return class_1937.field_25181.equals(dimension) ? "The End" : dimension.method_29177().method_12832();
		}
	}
}
