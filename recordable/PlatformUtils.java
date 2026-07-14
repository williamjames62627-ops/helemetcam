package dev.recordable;

import dev.recordable.FFmpegEncoder.FfmpegStatus;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class PlatformUtils {
	private static volatile PlatformUtils.Platform cachedPlatform;

	private PlatformUtils() {
	}

	public static PlatformUtils.Platform detectPlatform() {
		PlatformUtils.Platform cached = cachedPlatform;
		if (cached != null) {
			return cached;
		} else {
			PlatformUtils.Platform detected = probePlatform();
			cachedPlatform = detected;
			RecordableMod.LOGGER
				.info(
					"Detected platform: {} (os.name={}, os.arch={})",
					new Object[]{detected.displayName(), System.getProperty("os.name", "unknown"), System.getProperty("os.arch", "unknown")}
				);
			return detected;
		}
	}

	private static PlatformUtils.Platform probePlatform() {
		String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		String osArch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
		if (isAndroidEnvironment(osName, osArch)) {
			return PlatformUtils.Platform.ANDROID;
		} else if (osName.contains("win")) {
			return PlatformUtils.Platform.WINDOWS;
		} else if (osName.contains("mac") || osName.contains("darwin")) {
			return PlatformUtils.Platform.MACOS;
		} else {
			return !osName.contains("linux") && !osName.contains("nix") && !osName.contains("nux") ? PlatformUtils.Platform.UNKNOWN : PlatformUtils.Platform.LINUX;
		}
	}

	private static boolean isAndroidEnvironment(String osName, String osArch) {
		RecordableMod.LOGGER.info("[AndroidDetect] === Android Detection Start ===");
		RecordableMod.LOGGER.info("[AndroidDetect] os.name={}", System.getProperty("os.name", "?"));
		RecordableMod.LOGGER.info("[AndroidDetect] os.arch={}", System.getProperty("os.arch", "?"));
		RecordableMod.LOGGER.info("[AndroidDetect] java.vm.name={}", System.getProperty("java.vm.name", "?"));
		RecordableMod.LOGGER.info("[AndroidDetect] java.vm.vendor={}", System.getProperty("java.vm.vendor", "?"));
		RecordableMod.LOGGER.info("[AndroidDetect] java.vendor={}", System.getProperty("java.vendor", "?"));
		RecordableMod.LOGGER.info("[AndroidDetect] java.home={}", System.getProperty("java.home", "?"));
		RecordableMod.LOGGER.info("[AndroidDetect] user.dir={}", System.getProperty("user.dir", "?"));
		RecordableMod.LOGGER.info("[AndroidDetect] user.home={}", System.getProperty("user.home", "?"));
		RecordableMod.LOGGER.info("[AndroidDetect] java.runtime.name={}", System.getProperty("java.runtime.name", "?"));
		RecordableMod.LOGGER.info("[AndroidDetect] ANDROID_DATA={}", System.getenv("ANDROID_DATA"));
		RecordableMod.LOGGER.info("[AndroidDetect] ANDROID_ROOT={}", System.getenv("ANDROID_ROOT"));
		String vmName = System.getProperty("java.vm.name", "").toLowerCase(Locale.ROOT);
		if (!vmName.contains("dalvik") && !vmName.contains("art")) {
			String vendor = System.getProperty("java.vendor", "").toLowerCase(Locale.ROOT);
			String vmVendor = System.getProperty("java.vm.vendor", "").toLowerCase(Locale.ROOT);
			if (!vendor.contains("android") && !vmVendor.contains("android")) {
				String androidData = System.getenv("ANDROID_DATA");
				String androidRoot = System.getenv("ANDROID_ROOT");
				if ((androidData == null || androidData.isEmpty()) && (androidRoot == null || androidRoot.isEmpty())) {
					if (Files.exists(Path.of("/system/build.prop"), new LinkOption[0])) {
						RecordableMod.LOGGER.info("[AndroidDetect] ✓ Detected via /system/build.prop");
						return true;
					} else {
						String[] launcherPaths = new String[]{
							"/data/data/net.kdt.pojavlaunch",
							"/data/user/0/net.kdt.pojavlaunch",
							"/data/data/com.movtery.pojavzh",
							"/data/user/0/com.movtery.pojavzh",
							"/data/data/com.movtery.zalern",
							"/data/user/0/com.movtery.zalern",
							"/data/data/com.movtery.zalith",
							"/data/user/0/com.movtery.zalith",
							"/data/data/com.movtery.zalithlauncher",
							"/data/user/0/com.movtery.zalithlauncher",
							"/data/data/com.movtery.zalithlauncher.v2",
							"/data/user/0/com.movtery.zalithlauncher.v2",
							"/data/data/com.tungsten.fcl",
							"/data/user/0/com.tungsten.fcl"
						};

						for (String launcherPath : launcherPaths) {
							if (Files.exists(Path.of(launcherPath), new LinkOption[0])) {
								RecordableMod.LOGGER.info("[AndroidDetect] ✓ Detected via launcher path: {}", launcherPath);
								return true;
							}
						}

						boolean isLinux = osName.contains("linux");
						boolean isArm = osArch.contains("aarch64") || osArch.contains("arm");
						if (!isLinux
							|| !isArm
							|| !Files.exists(Path.of("/data/data"), new LinkOption[0])
								&& !Files.exists(Path.of("/sdcard"), new LinkOption[0])
								&& !Files.exists(Path.of("/storage/emulated"), new LinkOption[0])) {
							String userDir = System.getProperty("user.dir", "");
							String javaHome = System.getProperty("java.home", "");
							if (!userDir.startsWith("/data/data/")
								&& !userDir.startsWith("/data/user/")
								&& !javaHome.startsWith("/data/data/")
								&& !javaHome.startsWith("/data/user/")) {
								try {
									Class.forName("android.os.Build");
									RecordableMod.LOGGER.info("[AndroidDetect] ✓ Detected via android.os.Build class");
									return true;
								} catch (ClassNotFoundException var13) {
									RecordableMod.LOGGER.info("[AndroidDetect] ✗ Not detected as Android");
									RecordableMod.LOGGER.info("[AndroidDetect] === Android Detection End ===");
									return false;
								}
							} else {
								RecordableMod.LOGGER.info("[AndroidDetect] ✓ Detected via working directory inside /data/: userDir={}, javaHome={}", userDir, javaHome);
								return true;
							}
						} else {
							RecordableMod.LOGGER.info("[AndroidDetect] ✓ Detected via Linux+ARM + Android paths");
							return true;
						}
					}
				} else {
					RecordableMod.LOGGER.info("[AndroidDetect] ✓ Detected via env: ANDROID_DATA={}, ANDROID_ROOT={}", androidData, androidRoot);
					return true;
				}
			} else {
				RecordableMod.LOGGER.info("[AndroidDetect] ✓ Detected via vendor: java.vendor={}, java.vm.vendor={}", vendor, vmVendor);
				return true;
			}
		} else {
			RecordableMod.LOGGER.info("[AndroidDetect] ✓ Detected via java.vm.name: {}", vmName);
			return true;
		}
	}

	public static boolean isWindows() {
		return detectPlatform() == PlatformUtils.Platform.WINDOWS;
	}

	public static boolean isLinux() {
		return detectPlatform() == PlatformUtils.Platform.LINUX;
	}

	public static boolean isMacOS() {
		return detectPlatform() == PlatformUtils.Platform.MACOS;
	}

	public static boolean isAndroid() {
		return detectPlatform() == PlatformUtils.Platform.ANDROID;
	}

	public static boolean copyToAndroidGallery(Path videoPath) {
		if (isAndroid() && videoPath != null && Files.isRegularFile(videoPath, new LinkOption[0])) {
			Path[] candidateDirs = new Path[]{Paths.get("/storage/emulated/0/Movies/Record-able"), Paths.get("/storage/emulated/0/DCIM/Record-able")};

			for (Path galleryDir : candidateDirs) {
				try {
					if (!Files.exists(galleryDir, new LinkOption[0])) {
						try {
							Files.createDirectories(galleryDir);
							RecordableMod.LOGGER.info("[AndroidGallery] Created directory: {}", galleryDir);
						} catch (IOException var9) {
							RecordableMod.LOGGER.warn("[AndroidGallery] Could not create {} ({}). Trying next location.", galleryDir, var9.getMessage());
							continue;
						}
					}

					Path destination = galleryDir.resolve(videoPath.getFileName());
					Files.copy(videoPath, destination, StandardCopyOption.REPLACE_EXISTING);
					long sizeMB = Files.size(destination) / 1048576L;
					RecordableMod.LOGGER.info("[AndroidGallery] ✓ Copied to gallery: {} ({} MB)", destination, sizeMB);
					triggerMediaScan(destination);
					return true;
				} catch (IOException var10) {
					RecordableMod.LOGGER
						.warn(
							"[AndroidGallery] Failed to copy to {} ({}: {}). Trying next location.", new Object[]{galleryDir, var10.getClass().getSimpleName(), var10.getMessage()}
						);
				} catch (Throwable var11) {
					RecordableMod.LOGGER.warn("[AndroidGallery] Unexpected error copying to {} ({}). Trying next location.", galleryDir, var11.toString());
				}
			}

			RecordableMod.LOGGER.warn("[AndroidGallery] Could not copy recording to any public gallery directory. Original recording remains in the mod folder.");
			return false;
		} else {
			return false;
		}
	}

	private static void triggerMediaScan(Path filePath) {
		if (isAndroid() && filePath != null) {
			String absPath = filePath.toAbsolutePath().toString();
			String fileName = filePath.getFileName().toString();
			String mimeType = guessVideoMimeType(fileName);
			if (mediaStoreInsert(absPath, fileName, mimeType)) {
				RecordableMod.LOGGER.info("[AndroidGallery] Indexed via MediaStore insert: {}", fileName);
			} else if (legacyMediaScanBroadcast(absPath)) {
				RecordableMod.LOGGER.info("[AndroidGallery] Indexed via legacy MEDIA_SCANNER broadcast: {}", fileName);
			} else if (touchParentDirectory(filePath)) {
				RecordableMod.LOGGER.info("[AndroidGallery] Parent directory touched to prompt rescan: {}", filePath.getParent());
			} else {
				RecordableMod.LOGGER
					.info(
						"[AndroidGallery] No immediate media-scan method succeeded for {}. File is saved and will appear after the next automatic device scan or gallery refresh.",
						fileName
					);
			}
		}
	}

	private static boolean mediaStoreInsert(String absPath, String fileName, String mimeType) {
		try {
			List<String> cmd = new ArrayList();
			cmd.add("content");
			cmd.add("insert");
			cmd.add("--uri");
			cmd.add("content://media/external/video/media");
			cmd.add("--bind");
			cmd.add("_data:s:" + absPath);
			cmd.add("--bind");
			cmd.add("mime_type:s:" + mimeType);
			cmd.add("--bind");
			cmd.add("_display_name:s:" + fileName);
			cmd.add("--bind");
			cmd.add("title:s:" + stripExtension(fileName));
			ProcessBuilder pb = new ProcessBuilder(cmd);
			pb.redirectErrorStream(true);
			Process proc = pb.start();
			boolean finished = proc.waitFor(5L, TimeUnit.SECONDS);
			if (!finished) {
				proc.destroyForcibly();
				RecordableMod.LOGGER.debug("[AndroidGallery] MediaStore insert timed out for {}", fileName);
				return false;
			} else {
				int exit = proc.exitValue();
				if (exit == 0) {
					return true;
				} else {
					RecordableMod.LOGGER.debug("[AndroidGallery] MediaStore insert failed (exit={}) for {}", exit, fileName);
					return false;
				}
			}
		} catch (Exception var8) {
			RecordableMod.LOGGER.debug("[AndroidGallery] MediaStore insert unavailable ({}) for {}", var8.getMessage(), fileName);
			return false;
		}
	}

	private static boolean legacyMediaScanBroadcast(String absPath) {
		try {
			String fileUri = "file://" + absPath;
			ProcessBuilder pb = new ProcessBuilder(new String[]{"am", "broadcast", "-a", "android.intent.action.MEDIA_SCANNER_SCAN_FILE", "-d", fileUri});
			pb.redirectErrorStream(true);
			Process proc = pb.start();
			boolean finished = proc.waitFor(3L, TimeUnit.SECONDS);
			if (!finished) {
				proc.destroyForcibly();
				return false;
			} else {
				return proc.exitValue() == 0;
			}
		} catch (Exception var5) {
			RecordableMod.LOGGER.debug("[AndroidGallery] Legacy media-scan broadcast unavailable ({})", var5.getMessage());
			return false;
		}
	}

	private static boolean touchParentDirectory(Path filePath) {
		try {
			Path parent = filePath.getParent();
			if (parent != null && Files.isDirectory(parent, new LinkOption[0])) {
				Files.setLastModifiedTime(parent, FileTime.fromMillis(System.currentTimeMillis()));
				return true;
			} else {
				return false;
			}
		} catch (Exception var2) {
			RecordableMod.LOGGER.debug("[AndroidGallery] Could not touch parent directory ({})", var2.getMessage());
			return false;
		}
	}

	private static String guessVideoMimeType(String fileName) {
		String lower = fileName.toLowerCase(Locale.ROOT);
		if (lower.endsWith(".mkv")) {
			return "video/x-matroska";
		} else if (lower.endsWith(".webm")) {
			return "video/webm";
		} else if (lower.endsWith(".avi")) {
			return "video/x-msvideo";
		} else {
			return lower.endsWith(".mov") ? "video/quicktime" : "video/mp4";
		}
	}

	private static String stripExtension(String fileName) {
		int dot = fileName.lastIndexOf(46);
		return dot > 0 ? fileName.substring(0, dot) : fileName;
	}

	public static boolean isPojavLauncher() {
		return isAndroid() && AndroidPlatform.isPojavLauncher();
	}

	public static boolean isZalithLauncher() {
		return isAndroid() && AndroidPlatform.isZalithLauncher();
	}

	public static boolean isAndroidArm() {
		return AndroidPlatform.isAndroidArm();
	}

	public static boolean isStandardLinuxArm() {
		return AndroidPlatform.isStandardLinuxArm();
	}

	public static boolean isRecordingSupported() {
		PlatformUtils.Platform platform = detectPlatform();
		return platform != PlatformUtils.Platform.UNKNOWN;
	}

	public static String getPlatformId() {
		return switch (detectPlatform()) {
			case WINDOWS -> "windows";
			case LINUX -> "linux";
			case MACOS -> "macos";
			case ANDROID -> "android";
			case UNKNOWN -> "unknown";
		};
	}

	public static boolean isFfmpegAvailable() {
		try {
			return isAndroid() ? FfmpegBundleManager.isBundledFfmpegAvailable() : FFmpegEncoder.detectFfmpeg().found();
		} catch (Exception var1) {
			RecordableMod.LOGGER.debug("FFmpeg detection failed", var1);
			return false;
		}
	}

	public static String getFfmpegInstallHint() {
		return switch (detectPlatform()) {
			case WINDOWS -> "Click 'Download FFmpeg' in Record-able settings (auto-downloads from gyan.dev), or install manually from https://www.gyan.dev/ffmpeg/builds/ and add it to PATH.";
			case LINUX -> "Click 'Download FFmpeg' in Record-able settings (auto-downloads from johnvansickle.com), or install via your package manager: sudo apt install ffmpeg / sudo dnf install ffmpeg / sudo pacman -S ffmpeg.";
			case MACOS -> "Click 'Download FFmpeg' in Record-able settings (auto-downloads from evermeet.cx), or install via Homebrew: brew install ffmpeg.";
			case ANDROID -> AndroidPlatform.isArm64()
			? "Click 'Download FFmpeg' in Record-able settings (auto-downloads an arm64-v8a build), or install Termux and run 'pkg install ffmpeg' then set ffmpegPath to /data/data/com.termux/files/usr/bin/ffmpeg."
			: "Auto-download is only available on arm64-v8a devices. Install Termux and run 'pkg install ffmpeg', then set ffmpegPath to /data/data/com.termux/files/usr/bin/ffmpeg.";
			case UNKNOWN -> "Please install FFmpeg and ensure it is available in your system PATH.";
		};
	}

	public static String getAudioMethodDescription() {
		return switch (detectPlatform()) {
			case WINDOWS -> "DirectShow (Stereo Mix)";
			case LINUX -> "PulseAudio";
			case MACOS -> "AVFoundation";
			case ANDROID -> "OpenAL Loopback (game audio)";
			case UNKNOWN -> "Unknown";
		};
	}

	public static boolean isMobileDevice() {
		return detectPlatform() == PlatformUtils.Platform.ANDROID;
	}

	public static long getReplayBufferMemoryBudgetMB() {
		long maxHeapMB = Runtime.getRuntime().maxMemory() / 1048576L;
		return isMobileDevice() ? Math.min(200L, maxHeapMB * 15L / 100L) : Math.min(1500L, maxHeapMB * 40L / 100L);
	}

	public static long estimateReplayFrameBytes(int width, int height) {
		return isMobileDevice() ? (long)(width / 2) * (long)(height / 2) * 3L : (long)width * (long)height * 3L;
	}

	public static int getReplayBufferDownscaleFactor() {
		return isMobileDevice() ? 2 : 1;
	}

	public static long getReplayBufferDiskBudgetMB() {
		return isMobileDevice() ? 2048L : 8192L;
	}

	public static long getFreeDiskSpaceBytes(Path dir) {
		try {
			Path probe = dir;

			while (probe != null && !Files.exists(probe, new LinkOption[0])) {
				probe = probe.getParent();
			}

			return probe == null ? -1L : Files.getFileStore(probe).getUsableSpace();
		} catch (Exception var2) {
			return -1L;
		}
	}

	public static String getPlatformStatusText() {
		PlatformUtils.Platform platform = detectPlatform();
		boolean ffmpegFound = isFfmpegAvailable();
		if (platform == PlatformUtils.Platform.ANDROID) {
			boolean bundled = FfmpegBundleManager.isBundledFfmpegAvailable();
			String arch = AndroidPlatform.detectArchitecture().abi();
			String launcher = AndroidPlatform.detectLauncher().displayName();
			return bundled
				? "✓ Android / " + launcher + " (" + arch + ") - FFmpeg ready"
				: "⚠ Android / " + launcher + " (" + arch + ") - FFmpeg not installed (use Termux: pkg install ffmpeg)";
		} else {
			return !ffmpegFound ? "✗ FFmpeg not found - " + getFfmpegInstallHint() : "✓ " + platform.displayName() + " - Audio: " + getAudioMethodDescription();
		}
	}

	public static Path compressVideoForMobile(Path videoPath) {
		if (!Files.isRegularFile(videoPath, new LinkOption[0])) {
			return null;
		} else {
			try {
				String fileName = videoPath.getFileName().toString();
				String nameWithoutExt = stripExtension(fileName);
				Path compressedPath = videoPath.getParent().resolve(nameWithoutExt + "_compressed.mp4");
				FfmpegStatus ffmpegStatus = FFmpegEncoder.detectFfmpeg();
				if (ffmpegStatus.found() && ffmpegStatus.executable() != null) {
					List<String> cmd = new ArrayList();
					cmd.add(ffmpegStatus.executable());
					cmd.add("-i");
					cmd.add(videoPath.toAbsolutePath().toString());
					cmd.add("-vcodec");
					cmd.add("libx264");
					cmd.add("-crf");
					cmd.add("28");
					cmd.add("-preset");
					cmd.add("fast");
					cmd.add("-vf");
					cmd.add("scale=-2:min(720\\,ih)");
					cmd.add("-acodec");
					cmd.add("aac");
					cmd.add("-b:a");
					cmd.add("96k");
					cmd.add("-movflags");
					cmd.add("+faststart");
					cmd.add("-y");
					cmd.add(compressedPath.toAbsolutePath().toString());
					RecordableMod.LOGGER.info("[VideoCompression] Starting compression: {} -> {}", fileName, compressedPath.getFileName());
					ProcessBuilder pb = FfmpegBundleManager.ffmpegProcess(cmd);
					pb.redirectErrorStream(true);
					pb.redirectOutput(Redirect.DISCARD);
					Process proc = pb.start();
					boolean finished = proc.waitFor(5L, TimeUnit.MINUTES);
					if (!finished) {
						proc.destroyForcibly();
						RecordableMod.LOGGER.warn("[VideoCompression] Compression timed out for {}", fileName);
						return null;
					} else if (proc.exitValue() == 0 && Files.exists(compressedPath, new LinkOption[0])) {
						long originalBytes = Files.size(videoPath);
						long compressedBytes = Files.size(compressedPath);
						long originalMB = originalBytes / 1048576L;
						long compressedMB = compressedBytes / 1048576L;
						int savingsPercent = originalBytes > 0L ? (int)(100L - compressedBytes * 100L / originalBytes) : 0;
						RecordableMod.LOGGER.info("[VideoCompression] Compressed {} MB -> {} MB ({}% smaller)", new Object[]{originalMB, compressedMB, savingsPercent});
						return compressedPath;
					} else {
						RecordableMod.LOGGER.warn("[VideoCompression] Compression failed for {}", fileName);
						return null;
					}
				} else {
					RecordableMod.LOGGER.warn("[VideoCompression] FFmpeg not available; skipping compression of {}", fileName);
					return null;
				}
			} catch (Exception var18) {
				RecordableMod.LOGGER.warn("[VideoCompression] Error compressing video: {}", var18.toString());
				return null;
			}
		}
	}

	public static enum Platform {
		WINDOWS("Windows"),
		LINUX("Linux"),
		MACOS("macOS"),
		ANDROID("Android"),
		UNKNOWN("Unknown");

		private final String displayName;

		private Platform(String displayName) {
			this.displayName = displayName;
		}

		public String displayName() {
			return this.displayName;
		}
	}
}
