package dev.recordable;

import dev.recordable.RecordingManager.StopReason;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Disconnect;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Join;
import net.minecraft.class_310;

public final class AutoRecordManager {
	private static final int TICKS_PER_SECOND = 20;
	private static final int DISCONNECT_WATCHDOG_TICKS = 60;
	private final Object lock = new Object();
	private int countdownTicksRemaining = -1;
	private int nextAnnouncementSecond = -1;
	private boolean gameStartHandled;
	private int worldAbsentTicks = 0;
	private boolean sawWorldDuringRecording = false;
	private volatile String deferredJoinNotification;
	private volatile int deferredJoinTickDelay = -1;

	public void initialize() {
		ClientPlayConnectionEvents.JOIN.register((Join)(handler, sender, client) -> {
			try {
				RecordableConfig config = RecordableConfig.get();
				String pendingSaveNotice = RecordingManager.getInstance().consumePendingJoinNotification();
				if (pendingSaveNotice != null && !pendingSaveNotice.isBlank()) {
					this.deferredJoinNotification = pendingSaveNotice;
					this.deferredJoinTickDelay = 40;
				}

				if (config != null && !config.stopOnDisconnect && RecordingManager.getInstance().isPaused()) {
					RecordingManager.getInstance().resumeRecording(client);
				}

				if (config != null && "world_join".equals(config.autoRecordTrigger)) {
					this.scheduleAutoRecording(client, "world join");
				}
			} catch (Throwable var6) {
				RecordableMod.LOGGER.warn("Failed to process auto-record world join event.", var6);
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((Disconnect)(handler, client) -> {
			try {
				this.cancelScheduledAutoRecord();
				this.deferredJoinNotification = null;
				this.deferredJoinTickDelay = -1;
				this.handleDisconnectBehavior(client);
				RecordableConfig config = RecordableConfig.get();
				if (config != null && "world_leave".equals(config.autoStopTrigger) && !config.stopOnDisconnect) {
					stopIfRecording(client, "world leave");
				}
			} catch (Throwable var4) {
				RecordableMod.LOGGER.warn("Failed to process auto-record disconnect event.", var4);
			}
		});
	}

	public void onClientTick(class_310 client) {
		if (client != null) {
			this.checkDisconnectWatchdog(client);
			if (this.deferredJoinTickDelay >= 0) {
				this.deferredJoinTickDelay--;
				if (this.deferredJoinTickDelay < 0) {
					String notification = this.deferredJoinNotification;
					this.deferredJoinNotification = null;
					if (notification != null && !notification.isBlank()) {
						RecordableMod.sendClientMessage(ChatCategory.AUTO_RECORD, client, notification, false);
					}
				}
			}

			this.handleGameStartTrigger(client);
			int countdownSnapshot;
			synchronized (this.lock) {
				countdownSnapshot = this.countdownTicksRemaining;
			}

			if (countdownSnapshot >= 0) {
				synchronized (this.lock) {
					if (this.countdownTicksRemaining < 0) {
						return;
					}

					this.countdownTicksRemaining--;
					int nextValue = this.countdownTicksRemaining;
					if (nextValue >= 0) {
						int secondsRemaining = (int)Math.ceil((double)nextValue / 20.0);
						if (secondsRemaining > 0 && secondsRemaining != this.nextAnnouncementSecond) {
							this.nextAnnouncementSecond = secondsRemaining;
							RecordableMod.sendClientMessage(ChatCategory.AUTO_RECORD, client, "Auto-recording starts in " + secondsRemaining + "...", true);
						}
					}

					if (nextValue >= 0) {
						return;
					}

					this.countdownTicksRemaining = -1;
					this.nextAnnouncementSecond = -1;
				}

				if (!client.method_18854()) {
					executeOnClientThread(client, () -> this.triggerStartNow(client), "auto-record countdown completion");
				} else {
					this.triggerStartNow(client);
				}
			}
		}
	}

	public void onClientStopping(class_310 client) {
		this.cancelScheduledAutoRecord();

		try {
			RecordableConfig config = RecordableConfig.get();
			if (config != null && "game_quit".equals(config.autoStopTrigger)) {
				stopIfRecording(client, "game quit");
			}
		} catch (Throwable var3) {
			RecordableMod.LOGGER.warn("Failed to process auto-record game quit stop trigger.", var3);
		}
	}

	public void scheduleAutoRecording(class_310 client, String reason) {
		RecordableConfig config = RecordableConfig.get();
		if (config != null && config.enabled && config.autoRecord && !"manual".equals(config.autoRecordTrigger)) {
			if (!RecordingManager.getInstance().isActiveOrStopping()) {
				if (ModCompatibilityChecker.detectReplayPlayback(client)) {
					RecordableMod.LOGGER.info("Skipping auto-record-on-join: replay playback active (handled by compatibility bridge).");
				} else {
					int delaySeconds = Math.max(0, config.autoRecordDelay);
					if (delaySeconds <= 0) {
						this.triggerStartNow(client);
					} else {
						synchronized (this.lock) {
							this.countdownTicksRemaining = delaySeconds * 20;
							this.nextAnnouncementSecond = delaySeconds;
						}

						RecordableMod.sendClientMessage(ChatCategory.AUTO_RECORD, client, "Auto-recording starts in " + delaySeconds + "...", false);
						RecordableMod.LOGGER.info("Scheduled auto-record start in {} seconds ({})", delaySeconds, reason);
					}
				}
			}
		}
	}

	public void cancelScheduledAutoRecord() {
		synchronized (this.lock) {
			this.countdownTicksRemaining = -1;
			this.nextAnnouncementSecond = -1;
		}
	}

	private void handleGameStartTrigger(class_310 client) {
		if (!this.gameStartHandled) {
			RecordableConfig config;
			try {
				config = RecordableConfig.get();
			} catch (Throwable var4) {
				return;
			}

			if (config != null && config.autoRecord && "game_start".equals(config.autoRecordTrigger)) {
				this.gameStartHandled = true;
				this.scheduleAutoRecording(client, "game start");
			}
		}
	}

	private void checkDisconnectWatchdog(class_310 client) {
		try {
			RecordingManager recordingManager = RecordingManager.getInstance();
			boolean active = recordingManager.isRecording() || recordingManager.isPaused();
			if (!active) {
				this.worldAbsentTicks = 0;
				this.sawWorldDuringRecording = false;
				return;
			}

			if (ModCompatibilityChecker.detectReplayPlayback(client)) {
				this.worldAbsentTicks = 0;
				return;
			}

			if (client.field_1687 != null) {
				this.sawWorldDuringRecording = true;
				this.worldAbsentTicks = 0;
				return;
			}

			if (!this.sawWorldDuringRecording) {
				return;
			}

			this.worldAbsentTicks++;
			if (this.worldAbsentTicks < 60) {
				return;
			}

			this.worldAbsentTicks = 0;
			this.sawWorldDuringRecording = false;
			RecordableMod.LOGGER
				.info(
					"Disconnect watchdog: player left the world while recording was still active (the DISCONNECT event may have been intercepted by another mod); applying disconnect behavior."
				);
			this.cancelScheduledAutoRecord();
			this.handleDisconnectBehavior(client);
			RecordableConfig config = RecordableConfig.get();
			if (config != null && "world_leave".equals(config.autoStopTrigger) && !config.stopOnDisconnect) {
				stopIfRecording(client, "world leave (watchdog)");
			}
		} catch (Throwable var5) {
			RecordableMod.LOGGER.warn("Disconnect watchdog failed.", var5);
		}
	}

	private void handleDisconnectBehavior(class_310 client) {
		RecordableConfig config = RecordableConfig.get();
		if (config != null) {
			RecordingManager recordingManager = RecordingManager.getInstance();
			if (!recordingManager.isStopping()) {
				if (recordingManager.isRecording()) {
					if (config.stopOnDisconnect) {
						RecordableMod.LOGGER.info("Auto-stopping recording due to disconnect");
						recordingManager.stopRecordingForDisconnect(client);
					} else {
						recordingManager.pauseRecording(client);
						RecordableMod.LOGGER.info("Auto-pausing recording due to disconnect (stopOnDisconnect=false)");
					}
				}
			}
		}
	}

	private void triggerStartNow(class_310 client) {
		class_310 activeClient = resolveClient(client);
		if (activeClient != null && !activeClient.method_18854()) {
			executeOnClientThread(activeClient, () -> this.triggerStartNow(activeClient), "auto-record start");
		} else {
			RecordableConfig config = RecordableConfig.get();
			if (config != null && config.enabled && config.autoRecord) {
				if (ModCompatibilityChecker.detectReplayPlayback(activeClient)) {
					RecordableMod.LOGGER.info("Skipping auto-record: replay playback active (handled by compatibility bridge).");
				} else {
					if (!RecordingManager.getInstance().isActiveOrStopping()) {
						RecordingManager.getInstance().startRecording(activeClient);
						if (RecordingManager.getInstance().isRecording()) {
							RecordableMod.sendClientMessage(ChatCategory.AUTO_RECORD, activeClient, "Auto-recording started", false);
						}
					}
				}
			}
		}
	}

	private static void stopIfRecording(class_310 client, String reason) {
		class_310 activeClient = resolveClient(client);
		if (activeClient != null && !activeClient.method_18854()) {
			executeOnClientThread(activeClient, () -> stopIfRecording(activeClient, reason), "auto-record stop");
		} else {
			RecordingManager recordingManager = RecordingManager.getInstance();
			if (!recordingManager.isStopping()) {
				if (recordingManager.isRecording() || recordingManager.isPaused()) {
					RecordableMod.sendClientMessage(ChatCategory.AUTO_RECORD, activeClient, "Auto-recording stopped (" + reason + ")", true);
					recordingManager.stopRecording(activeClient, StopReason.AUTO);
				}
			}
		}
	}

	private static class_310 resolveClient(class_310 client) {
		return client == null ? class_310.method_1551() : client;
	}

	private static void executeOnClientThread(class_310 client, Runnable runnable, String actionDescription) {
		if (client != null && runnable != null) {
			try {
				client.execute(runnable);
			} catch (Throwable var4) {
				RecordableMod.LOGGER.warn("Failed to schedule Record-able {} on the client thread.", actionDescription, var4);
			}
		}
	}
}
