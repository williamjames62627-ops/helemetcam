package dev.recordable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RecordableMod {
	public static final String MOD_ID = "recordable";
	public static final Logger LOGGER = LoggerFactory.getLogger("recordable");
	private static volatile Object autoClipManagerInstance;
	private static volatile RecordableMod.ClientMessageSender messageSender;

	public static <T> T getAutoClipManager() {
		return (T)autoClipManagerInstance;
	}

	public static void setAutoClipManager(Object manager) {
		autoClipManagerInstance = manager;
	}

	public static void setMessageSender(RecordableMod.ClientMessageSender sender) {
		messageSender = sender;
	}

	public static void sendClientMessage(Object client, String message, boolean actionBar) {
		sendClientMessageInternal(client, message, actionBar);
	}

	public static void sendClientMessage(ChatCategory category, Object client, String message, boolean actionBar) {
		if (shouldNotify(category)) {
			sendClientMessageInternal(client, message, actionBar);
		}
	}

	public static boolean shouldNotify(ChatCategory category) {
		if (category == null) {
			return true;
		} else {
			try {
				RecordableConfig config = RecordableConfig.get();
				if (config == null) {
					return true;
				} else {
					switch (category) {
						case RECORDING:
							return config.notifyRecording;
						case CLIPS:
							return config.notifyClips;
						case REPLAY_BUFFER:
							return config.notifyReplayBuffer;
						case AUTO_RECORD:
							return config.notifyAutoRecord;
						case BOOKMARKS:
							return config.notifyBookmarks;
						case WARNINGS:
							return config.notifyWarnings;
						case GENERAL:
						default:
							return true;
					}
				}
			} catch (Throwable var2) {
				return true;
			}
		}
	}

	private static void sendClientMessageInternal(Object client, String message, boolean actionBar) {
		RecordableMod.ClientMessageSender sender = messageSender;
		if (sender != null) {
			try {
				sender.send(client, message, actionBar);
			} catch (Throwable var5) {
				LOGGER.warn("Failed to send client message: {}", message, var5);
			}
		} else {
			LOGGER.info("[chat] {}", message);
		}
	}

	@FunctionalInterface
	public interface ClientMessageSender {
		void send(Object object, String string, boolean boolean3);
	}
}
