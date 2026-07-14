package dev.recordable.screen;

import dev.recordable.FFmpegEncoder;
import dev.recordable.PlatformUtils;
import dev.recordable.RecordableMod;
import dev.recordable.FFmpegEncoder.FfmpegStatus;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.class_2561;
import net.minecraft.class_332;
import net.minecraft.class_4185;
import net.minecraft.class_437;
import net.minecraft.class_7919;

public final class VideoPlayerScreen extends class_437 {
	private static final int BUTTON_WIDTH = 200;
	private static final int BUTTON_HEIGHT = 20;
	private final File videoFile;
	private final class_437 parent;
	private String videoInfo = "Loading...";
	private String errorMessage = null;

	public VideoPlayerScreen(Path videoPath, class_437 parent) {
		super(class_2561.method_43470("Video Player"));
		this.videoFile = videoPath.toFile();
		this.parent = parent;
		RecordableMod.LOGGER.info("VideoPlayerScreen opened for: {}", this.videoFile.getName());
		this.loadVideoInfo();
	}

	private void loadVideoInfo() {
		FfmpegStatus status = FFmpegEncoder.detectFfmpeg();
		if (!status.found()) {
			this.videoInfo = "FFmpeg not found - video info unavailable";
		} else {
			String infoFromProbe = this.tryFfprobeInfo(status);
			if (infoFromProbe != null) {
				this.videoInfo = infoFromProbe;
			} else {
				String infoFromFfmpeg = this.tryFfmpegInfo(status);
				if (infoFromFfmpeg != null) {
					this.videoInfo = infoFromFfmpeg;
				} else {
					long sizeBytes = this.videoFile.length();
					String sizeStr = String.format(Locale.ROOT, "%.2f MB", (double)sizeBytes / 1048576.0);
					this.videoInfo = "Size: " + sizeStr + " (video details unavailable)";
				}
			}
		}
	}

	private String tryFfprobeInfo(FfmpegStatus ffmpegStatus) {
		String ffprobeExecutable = resolveSiblingExecutable(ffmpegStatus.executable(), "ffprobe");
		List<String> command = new ArrayList();
		command.add(ffprobeExecutable);
		command.add("-v");
		command.add("error");
		command.add("-select_streams");
		command.add("v:0");
		command.add("-show_entries");
		command.add("stream=width,height,r_frame_rate:format=duration");
		command.add("-of");
		command.add("csv=p=0");
		command.add(this.videoFile.getAbsolutePath());

		try {
			ProcessBuilder pb = new ProcessBuilder(command);
			pb.redirectErrorStream(true);
			Process process = pb.start();
			BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

			String line;
			try {
				line = reader.readLine();
			} catch (Throwable var18) {
				try {
					reader.close();
				} catch (Throwable var17) {
					var18.addSuppressed(var17);
				}

				throw var18;
			}

			reader.close();
			boolean exited = process.waitFor(10L, TimeUnit.SECONDS);
			if (!exited) {
				process.destroyForcibly();
				return null;
			} else if (process.exitValue() == 0 && line != null && !line.isBlank()) {
				String[] parts = line.split(",");
				if (parts.length < 2) {
					return null;
				} else {
					String resolution = parts[0] + "x" + parts[1];
					String fps = "?";
					String duration = "Unknown";
					if (parts.length >= 3 && !parts[2].isBlank()) {
						String[] fpsParts = parts[2].split("/");
						if (fpsParts.length == 2) {
							double numerator = Double.parseDouble(fpsParts[0]);
							double denominator = Double.parseDouble(fpsParts[1]);
							if (denominator > 0.0) {
								fps = String.format(Locale.ROOT, "%.0f", numerator / denominator);
							}
						} else {
							fps = String.format(Locale.ROOT, "%.0f", Double.parseDouble(parts[2]));
						}
					}

					if (parts.length >= 4 && !parts[3].isBlank()) {
						double dur = Double.parseDouble(parts[3]);
						int hours = (int)(dur / 3600.0);
						int mins = (int)(dur % 3600.0 / 60.0);
						int secs = (int)(dur % 60.0);
						if (hours > 0) {
							duration = String.format(Locale.ROOT, "%d:%02d:%02d", hours, mins, secs);
						} else {
							duration = String.format(Locale.ROOT, "%d:%02d", mins, secs);
						}
					}

					return resolution + " @ " + fps + " fps  |  Duration: " + duration;
				}
			} else {
				return null;
			}
		} catch (Exception var19) {
			RecordableMod.LOGGER.debug("ffprobe info failed for {}: {}", this.videoFile.getName(), var19.getMessage());
			return null;
		}
	}

	private String tryFfmpegInfo(FfmpegStatus ffmpegStatus) {
		try {
			Process process = new ProcessBuilder(new String[]{ffmpegStatus.executable(), "-nostdin", "-hide_banner", "-i", this.videoFile.getAbsolutePath()})
				.redirectErrorStream(true)
				.start();
			BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

			String output;
			try {
				StringBuilder sb = new StringBuilder();

				String l;
				while ((l = reader.readLine()) != null) {
					if (sb.length() > 0) {
						sb.append('\n');
					}

					sb.append(l);
				}

				output = sb.toString();
			} catch (Throwable var14) {
				try {
					reader.close();
				} catch (Throwable var13) {
					var14.addSuppressed(var13);
				}

				throw var14;
			}

			reader.close();
			process.waitFor(10L, TimeUnit.SECONDS);
			if (process.isAlive()) {
				process.destroyForcibly();
			}

			String resolution = "?";
			String fps = "?";
			String duration = "Unknown";
			Pattern durPat = Pattern.compile("Duration:\\s*(\\d+):(\\d+):(\\d+\\.\\d+)");
			Matcher durMatch = durPat.matcher(output);
			if (durMatch.find()) {
				int hours = Integer.parseInt(durMatch.group(1));
				int mins = Integer.parseInt(durMatch.group(2));
				double secs = Double.parseDouble(durMatch.group(3));
				if (hours > 0) {
					duration = String.format(Locale.ROOT, "%d:%02d:%02d", hours, mins, (int)secs);
				} else {
					duration = String.format(Locale.ROOT, "%d:%02d", mins, (int)secs);
				}
			}

			Pattern vidPat = Pattern.compile("Stream.*Video:.*?(\\d{2,5})x(\\d{2,5})");
			Matcher vidMatch = vidPat.matcher(output);
			if (vidMatch.find()) {
				resolution = vidMatch.group(1) + "x" + vidMatch.group(2);
			}

			Pattern fpsPat = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s+fps");
			Matcher fpsMatch = fpsPat.matcher(output);
			if (fpsMatch.find()) {
				fps = String.format(Locale.ROOT, "%.0f", Double.parseDouble(fpsMatch.group(1)));
			}

			return resolution + " @ " + fps + " fps  |  Duration: " + duration;
		} catch (Exception var15) {
			RecordableMod.LOGGER.debug("ffmpeg -i info failed for {}: {}", this.videoFile.getName(), var15.getMessage());
			return null;
		}
	}

	protected void method_25426() {
		super.method_25426();
		int centerX = this.field_22789 / 2;
		int y = this.field_22790 / 2 + 20;
		if (PlatformUtils.isAndroid()) {
			class_7919 androidTip = class_7919.method_47407(
				class_2561.method_43470(
					"Not available on Android. Recordings are auto-saved to your gallery (Movies/Record-able) - open them from your Gallery or Files app."
				)
			);
			class_4185 openVideoLabel = class_4185.method_46430(class_2561.method_43470("Open Video (Not available on Android)"), button -> {
			}).method_46434(centerX - 100, y, 200, 20).method_46431();
			openVideoLabel.field_22763 = false;
			openVideoLabel.method_47400(androidTip);
			this.method_37063(openVideoLabel);
			class_4185 openFolderLabel = class_4185.method_46430(class_2561.method_43470("Open Folder (Not available on Android)"), button -> {
			}).method_46434(centerX - 100, y + 30, 200, 20).method_46431();
			openFolderLabel.field_22763 = false;
			openFolderLabel.method_47400(androidTip);
			this.method_37063(openFolderLabel);
		} else {
			this.method_37063(
				class_4185.method_46430(class_2561.method_43470("▶ Open Video"), button -> this.openInDefaultPlayer())
					.method_46434(centerX - 100, y, 200, 20)
					.method_46431()
			);
			this.method_37063(
				class_4185.method_46430(class_2561.method_43470("\ud83d\udcc1 Open Recordings Folder"), button -> this.openRecordingsFolder())
					.method_46434(centerX - 100, y + 30, 200, 20)
					.method_46431()
			);
		}

		this.method_37063(class_4185.method_46430(class_2561.method_43470("Back"), button -> {
			if (this.field_22787 != null) {
				this.field_22787.method_1507(this.parent);
			}
		}).method_46434(centerX - 100, y + 60, 200, 20).method_46431());
	}

	private void openInDefaultPlayer() {
		try {
			String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
			List<String> command = new ArrayList();
			RecordableMod.LOGGER.info("Opening video in default player (OS: {})", os);
			if (os.contains("win")) {
				command.add("cmd");
				command.add("/c");
				command.add("start");
				command.add("");
				command.add(this.videoFile.getAbsolutePath());
			} else if (os.contains("mac")) {
				command.add("open");
				command.add(this.videoFile.getAbsolutePath());
			} else {
				command.add("xdg-open");
				command.add(this.videoFile.getAbsolutePath());
			}

			new ProcessBuilder(command).start();
			RecordableMod.LOGGER.info("Video opened successfully in system player");
			this.errorMessage = null;
		} catch (IOException var3) {
			RecordableMod.LOGGER.error("Failed to open video in default player", var3);
			this.errorMessage = "Failed to open video: " + var3.getMessage();
		}
	}

	private void openRecordingsFolder() {
		try {
			File folder = this.videoFile.getParentFile();
			String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
			RecordableMod.LOGGER.info("Opening recordings folder: {}", folder.getAbsolutePath());
			if (os.contains("win")) {
				new ProcessBuilder(new String[]{"explorer", folder.getAbsolutePath()}).start();
			} else if (os.contains("mac")) {
				new ProcessBuilder(new String[]{"open", folder.getAbsolutePath()}).start();
			} else {
				new ProcessBuilder(new String[]{"xdg-open", folder.getAbsolutePath()}).start();
			}
		} catch (IOException var3) {
			RecordableMod.LOGGER.error("Failed to open recordings folder", var3);
			this.errorMessage = "Failed to open folder: " + var3.getMessage();
		}
	}

	public void method_25394(class_332 context, int mouseX, int mouseY, float delta) {
		this.method_25420(context, mouseX, mouseY, delta);
		context.method_27534(this.field_22793, class_2561.method_43470("▶ Video Player"), this.field_22789 / 2, 40, 16777215);
		int infoY = this.field_22790 / 2 - 80;
		context.method_27534(this.field_22793, class_2561.method_43470(this.videoFile.getName()), this.field_22789 / 2, infoY, 16777215);
		if (this.errorMessage != null) {
			context.method_27534(this.field_22793, class_2561.method_43470(this.errorMessage), this.field_22789 / 2, this.field_22790 - 60, 16733525);
		}

		super.method_25394(context, mouseX, mouseY, delta);
	}

	public void method_25419() {
		if (this.field_22787 != null) {
			this.field_22787.method_1507(this.parent);
		}
	}

	public boolean method_25421() {
		return false;
	}

	private static String formatFileSize(long bytes) {
		if (bytes < 1024L) {
			return bytes + " B";
		} else if (bytes < 1048576L) {
			return String.format(Locale.ROOT, "%.1f KB", (double)bytes / 1024.0);
		} else {
			return bytes < 1073741824L
				? String.format(Locale.ROOT, "%.1f MB", (double)bytes / 1048576.0)
				: String.format(Locale.ROOT, "%.2f GB", (double)bytes / 1.0737418E9F);
		}
	}

	private static String resolveSiblingExecutable(String ffmpegPath, String targetBaseName) {
		if (ffmpegPath != null && !ffmpegPath.isBlank()) {
			String lower = ffmpegPath.toLowerCase(Locale.ROOT);
			if (lower.endsWith("ffmpeg.exe")) {
				return ffmpegPath.substring(0, ffmpegPath.length() - "ffmpeg.exe".length()) + targetBaseName + ".exe";
			} else {
				return lower.endsWith("ffmpeg") ? ffmpegPath.substring(0, ffmpegPath.length() - "ffmpeg".length()) + targetBaseName : targetBaseName;
			}
		} else {
			return targetBaseName;
		}
	}
}
