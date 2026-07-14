package dev.recordable;

import dev.recordable.PlatformUtils.Platform;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import net.fabricmc.loader.api.FabricLoader;

public final class FfmpegBundleManager {
	private static final String BUNDLE_DIR = "recordable/ffmpeg";
	private static final String WIN_URL = "https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip";
	private static final String WIN_SHA_URL = "https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip.sha256";
	private static final String LINUX_URL = "https://johnvansickle.com/ffmpeg/releases/ffmpeg-release-amd64-static.tar.xz";
	private static final String LINUX_MD5_URL = "https://johnvansickle.com/ffmpeg/releases/ffmpeg-release-amd64-static.tar.xz.md5";
	private static final String MACOS_URL = "https://evermeet.cx/ffmpeg/get/zip";
	private static final String ANDROID_FFMPEG_VERSION = "8.0.1";
	private static final String ANDROID_ARM64_FFMPEG_URL = "https://raw.githubusercontent.com/hzw1199/Android-FFmpeg-Prebuilt/main/ffmpeg-8.0.1/bin/ffmpeg";
	private static final String ANDROID_ARM64_FFPROBE_URL = "https://raw.githubusercontent.com/hzw1199/Android-FFmpeg-Prebuilt/main/ffmpeg-8.0.1/bin/ffprobe";
	private static final String ANDROID_ARM64_FFMPEG_SHA256 = "206e84cd597408bbfaf51e27c14b17085590e639e40d568c28735077a6b708b3";
	private static final String ANDROID_ARM64_FFPROBE_SHA256 = "42d18430d4a8b5e6efaf135faa4122a13087ac4a2d20933b81a2b781da07d9d0";
	private static final int CONNECT_TIMEOUT_MS = 15000;
	private static final int READ_TIMEOUT_MS = 30000;
	private static final int BUFFER_SIZE = 65536;
	private static final String LINKER64 = "/system/bin/linker64";
	private static final String LINKER32 = "/system/bin/linker";
	private static volatile FfmpegBundleManager.ExecMethod cachedExecMethod = FfmpegBundleManager.ExecMethod.DIRECT;
	private static volatile String cachedPath;
	private static volatile boolean checkedOnce;
	private static final AtomicReference<FfmpegBundleManager.Status> status = new AtomicReference(FfmpegBundleManager.Status.NOT_FOUND);
	private static final AtomicBoolean downloading = new AtomicBoolean(false);
	private static volatile String lastError;
	private static volatile FfmpegBundleManager.DownloadProgress lastProgress = FfmpegBundleManager.DownloadProgress.IDLE;
	private static final List<FfmpegBundleManager.ProgressListener> listeners = new ArrayList();

	private FfmpegBundleManager() {
	}

	public static String getBundledFfmpegPath() {
		if (checkedOnce) {
			return cachedPath;
		} else {
			synchronized (FfmpegBundleManager.class) {
				if (checkedOnce) {
					return cachedPath;
				} else {
					cachedPath = resolveLocal();
					checkedOnce = true;
					if (cachedPath != null) {
						status.set(FfmpegBundleManager.Status.AVAILABLE);
					}

					return cachedPath;
				}
			}
		}
	}

	public static boolean isBundledFfmpegAvailable() {
		return getBundledFfmpegPath() != null;
	}

	public static void invalidateCache() {
		synchronized (FfmpegBundleManager.class) {
			cachedPath = null;
			checkedOnce = false;
			cachedExecMethod = FfmpegBundleManager.ExecMethod.DIRECT;
		}
	}

	public static Path getBundleDirectory() {
		return FabricLoader.getInstance().getGameDir().resolve("recordable/ffmpeg").resolve("bin");
	}

	public static FfmpegBundleManager.Status getStatus() {
		if (status.get() != FfmpegBundleManager.Status.DOWNLOADING && isBundledFfmpegAvailable()) {
			status.set(FfmpegBundleManager.Status.AVAILABLE);
		}

		return (FfmpegBundleManager.Status)status.get();
	}

	public static String getLastError() {
		return lastError;
	}

	public static FfmpegBundleManager.DownloadProgress getLastProgress() {
		return lastProgress;
	}

	public static boolean isDownloading() {
		return downloading.get();
	}

	public static void addProgressListener(FfmpegBundleManager.ProgressListener listener) {
		if (listener != null) {
			synchronized (listeners) {
				if (!listeners.contains(listener)) {
					listeners.add(listener);
				}
			}
		}
	}

	public static void removeProgressListener(FfmpegBundleManager.ProgressListener listener) {
		synchronized (listeners) {
			listeners.remove(listener);
		}
	}

	private static void fireProgress(FfmpegBundleManager.DownloadProgress p) {
		lastProgress = p;
		List<FfmpegBundleManager.ProgressListener> snapshot;
		synchronized (listeners) {
			snapshot = new ArrayList(listeners);
		}

		for (FfmpegBundleManager.ProgressListener l : snapshot) {
			try {
				l.onProgress(p);
			} catch (Throwable var5) {
				RecordableMod.LOGGER.warn("[FfmpegBundle] Listener threw: {}", var5.getMessage());
			}
		}
	}

	private static String resolveLocal() {
		Platform platform = PlatformUtils.detectPlatform();
		RecordableMod.LOGGER.info("[FfmpegBundle] Resolving local FFmpeg (lite mode)");
		RecordableMod.LOGGER.info("[FfmpegBundle]   Platform: {}", platform.displayName());
		RecordableMod.LOGGER.info("[FfmpegBundle]   Bundle dir: {}", getBundleDirectory());
		if (platform == Platform.ANDROID) {
			Path termux = AndroidPlatform.getTermuxFfmpegPath();
			if (termux != null) {
				RecordableMod.LOGGER.info("[FfmpegBundle] [Android] Found Termux ffmpeg at {}", termux);
				if (!Files.isExecutable(termux)) {
					setExecutablePermission(termux);
				}

				if (verifyBinaryExecution(termux)) {
					RecordableMod.LOGGER.info("[FfmpegBundle] [Android] ✓ Using Termux ffmpeg: {}", termux);
					return termux.toAbsolutePath().toString();
				}

				RecordableMod.LOGGER.warn("[FfmpegBundle] [Android] Termux ffmpeg present but not runnable (is Termux's storage accessible to this launcher?): {}", termux);
			}
		}

		Path candidate = getBundleDirectory().resolve(getExecutableName());
		if (Files.isRegularFile(candidate, new LinkOption[0]) && Files.isReadable(candidate)) {
			if (!PlatformUtils.isWindows() && !Files.isExecutable(candidate)) {
				setExecutablePermission(candidate);
			}

			if (verifyBinaryExecution(candidate)) {
				RecordableMod.LOGGER.info("[FfmpegBundle] ✓ Found downloaded FFmpeg: {}", candidate);
				return candidate.toAbsolutePath().toString();
			}

			RecordableMod.LOGGER.warn("[FfmpegBundle] Found {} but cannot execute it (noexec/permission?)", candidate);
		} else {
			RecordableMod.LOGGER.info("[FfmpegBundle] No downloaded FFmpeg at {}", candidate);
		}

		if (platform == Platform.ANDROID) {
			Path execPath = findOnAndroidExecCapablePaths();
			if (execPath != null) {
				return execPath.toAbsolutePath().toString();
			}
		}

		return null;
	}

	private static boolean verifyBinaryExecution(Path binary) {
		String path = binary.toAbsolutePath().toString();
		if (runVersionProbe(new String[]{path, "-version"}, binary, FfmpegBundleManager.ExecMethod.DIRECT)) {
			cachedExecMethod = FfmpegBundleManager.ExecMethod.DIRECT;
			return true;
		} else {
			if (PlatformUtils.isAndroid()) {
				if (Files.isRegularFile(Path.of("/system/bin/linker64"), new LinkOption[0])
					&& runVersionProbe(new String[]{"/system/bin/linker64", path, "-version"}, binary, FfmpegBundleManager.ExecMethod.LINKER64)) {
					cachedExecMethod = FfmpegBundleManager.ExecMethod.LINKER64;
					RecordableMod.LOGGER.info("[FfmpegBundle] [Android] {} runs via linker64 (direct exec blocked)", binary);
					return true;
				}

				if (Files.isRegularFile(Path.of("/system/bin/linker"), new LinkOption[0])
					&& runVersionProbe(new String[]{"/system/bin/linker", path, "-version"}, binary, FfmpegBundleManager.ExecMethod.LINKER32)) {
					cachedExecMethod = FfmpegBundleManager.ExecMethod.LINKER32;
					RecordableMod.LOGGER.info("[FfmpegBundle] [Android] {} runs via linker (direct exec blocked)", binary);
					return true;
				}

				String quoted = "'" + path.replace("'", "'\\''") + "'";
				if (runVersionProbe(new String[]{"/system/bin/sh", "-c", quoted + " -version"}, binary, FfmpegBundleManager.ExecMethod.SHELL)) {
					cachedExecMethod = FfmpegBundleManager.ExecMethod.SHELL;
					RecordableMod.LOGGER.info("[FfmpegBundle] [Android] {} runs via shell wrapper (direct exec blocked)", binary);
					return true;
				}
			}

			return false;
		}
	}

	private static boolean runVersionProbe(String[] command, Path binary, FfmpegBundleManager.ExecMethod method) {
		try {
			ProcessBuilder pb = new ProcessBuilder(command).redirectErrorStream(true);
			applyExecEnv(pb, binary.toAbsolutePath().toString(), method);
			Process proc = pb.start();
			boolean exited = proc.waitFor(5L, TimeUnit.SECONDS);
			if (!exited) {
				proc.destroyForcibly();
				return false;
			} else {
				String output = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
				return proc.exitValue() == 0 && output.toLowerCase(Locale.ROOT).contains("ffmpeg");
			}
		} catch (Exception var7) {
			RecordableMod.LOGGER
				.debug("[FfmpegBundle] Execution check failed for {} via {}: {}", new Object[]{binary, command.length > 0 ? command[0] : "?", var7.getMessage()});
			return false;
		}
	}

	public static FfmpegBundleManager.ExecMethod getExecMethod() {
		return cachedExecMethod;
	}

	public static List<String> wrapCommandForExec(List<String> command) {
		if (command != null && !command.isEmpty() && cachedExecMethod != FfmpegBundleManager.ExecMethod.DIRECT) {
			switch (cachedExecMethod) {
				case LINKER64: {
					List<String> wrapped = new ArrayList(command.size() + 1);
					wrapped.add("/system/bin/linker64");
					wrapped.addAll(command);
					return wrapped;
				}
				case LINKER32: {
					List<String> wrapped = new ArrayList(command.size() + 1);
					wrapped.add("/system/bin/linker");
					wrapped.addAll(command);
					return wrapped;
				}
				case SHELL: {
					StringBuilder sb = new StringBuilder();

					for (int i = 0; i < command.size(); i++) {
						if (i > 0) {
							sb.append(' ');
						}

						sb.append('\'').append(((String)command.get(i)).replace("'", "'\\''")).append('\'');
					}

					List<String> wrapped = new ArrayList(3);
					wrapped.add("/system/bin/sh");
					wrapped.add("-c");
					wrapped.add(sb.toString());
					return wrapped;
				}
				default:
					return command;
			}
		} else {
			return command;
		}
	}

	public static void applyExecEnv(ProcessBuilder pb, String binaryPath) {
		applyExecEnv(pb, binaryPath, cachedExecMethod);
	}

	public static ProcessBuilder ffmpegProcess(List<String> command) {
		List<String> wrapped = wrapCommandForExec(command);
		ProcessBuilder pb = new ProcessBuilder(wrapped);
		if (command != null && !command.isEmpty()) {
			applyExecEnv(pb, (String)command.get(0));
		}

		return pb;
	}

	public static ProcessBuilder ffmpegProcess(String... command) {
		return ffmpegProcess(new ArrayList(Arrays.asList(command)));
	}

	private static void applyExecEnv(ProcessBuilder pb, String binaryPath, FfmpegBundleManager.ExecMethod method) {
		if (pb != null && binaryPath != null && (method == FfmpegBundleManager.ExecMethod.LINKER64 || method == FfmpegBundleManager.ExecMethod.LINKER32)) {
			try {
				Path parent = Path.of(binaryPath).toAbsolutePath().getParent();
				if (parent == null) {
					return;
				}

				String add = parent.toString();
				String existing = (String)pb.environment().get("LD_LIBRARY_PATH");
				pb.environment().put("LD_LIBRARY_PATH", existing != null && !existing.isBlank() ? add + ":" + existing : add);
			} catch (Exception var6) {
				RecordableMod.LOGGER.debug("[FfmpegBundle] Could not set LD_LIBRARY_PATH for {}: {}", binaryPath, var6.getMessage());
			}
		}
	}

	private static Path findOnAndroidExecCapablePaths() {
		String[] binaryNames = new String[]{"recordable-ffmpeg", "ffmpeg"};

		for (Path dir : AndroidPlatform.getExecCapableDirectories()) {
			for (String binaryName : binaryNames) {
				Path candidate = dir.resolve(binaryName);
				if (Files.isRegularFile(candidate, new LinkOption[0]) && Files.isReadable(candidate)) {
					if (!Files.isExecutable(candidate)) {
						setExecutablePermission(candidate);
					}

					if (verifyBinaryExecution(candidate)) {
						return candidate;
					}
				}
			}
		}

		return null;
	}

	@Deprecated
	static String detectAndroidPackageName() {
		return AndroidPlatform.getPackageName();
	}

	public static boolean isAutoDownloadSupported() {
		Platform p = PlatformUtils.detectPlatform();
		return p == Platform.ANDROID ? AndroidPlatform.isArm64() : p == Platform.WINDOWS || p == Platform.LINUX || p == Platform.MACOS;
	}

	public static String getDownloadSourceDescription() {
		return switch (PlatformUtils.detectPlatform()) {
			case WINDOWS -> "gyan.dev (FFmpeg release essentials, Windows x64)";
			case LINUX -> "johnvansickle.com (FFmpeg release static, Linux x64)";
			case MACOS -> "evermeet.cx (FFmpeg static, macOS x64)";
			case ANDROID -> AndroidPlatform.isArm64()
			? "hzw1199/Android-FFmpeg-Prebuilt (FFmpeg 8.0.1, arm64-v8a)"
			: "Not supported on " + AndroidPlatform.detectArchitecture().abi() + " - see manual instructions";
			case UNKNOWN -> "Unsupported platform";
		};
	}

	public static String getEstimatedDownloadSize() {
		return switch (PlatformUtils.detectPlatform()) {
			case WINDOWS -> "~103 MB";
			case LINUX -> "~80 MB";
			case MACOS -> "~80 MB";
			case ANDROID -> "~30 MB";
			default -> "n/a";
		};
	}

	public static CompletableFuture<Boolean> downloadAsync(Consumer<Boolean> onComplete) {
		if (!isAutoDownloadSupported()) {
			String err = "Auto-download is not supported on " + PlatformUtils.detectPlatform().displayName() + ". See manual installation instructions.";
			lastError = err;
			status.set(FfmpegBundleManager.Status.ERROR);
			RecordableMod.LOGGER.warn("[FfmpegBundle] {}", err);
			if (onComplete != null) {
				onComplete.accept(false);
			}

			return CompletableFuture.completedFuture(false);
		} else if (!downloading.compareAndSet(false, true)) {
			RecordableMod.LOGGER.info("[FfmpegBundle] Download already in progress, ignoring duplicate request");
			CompletableFuture<Boolean> existing = new CompletableFuture();
			existing.complete(false);
			return existing;
		} else {
			status.set(FfmpegBundleManager.Status.DOWNLOADING);
			lastError = null;
			fireProgress(new FfmpegBundleManager.DownloadProgress("starting", 0L, 0L, 0.0));
			CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
				Boolean ok;
				try {
					doDownloadAndInstall();
					invalidateCache();
					String resolved = getBundledFfmpegPath();
					boolean okx = resolved != null;
					if (okx) {
						status.set(FfmpegBundleManager.Status.AVAILABLE);
						fireProgress(new FfmpegBundleManager.DownloadProgress("done", 1L, 1L, 1.0));
						RecordableMod.LOGGER.info("[FfmpegBundle] ✓ FFmpeg ready at {}", resolved);
					} else {
						status.set(FfmpegBundleManager.Status.ERROR);
						lastError = "Download finished but FFmpeg binary could not be located after extraction.";
						fireProgress(new FfmpegBundleManager.DownloadProgress("error", 0L, 0L, 0.0));
					}

					return okx;
				} catch (Exception var6) {
					lastError = var6.getMessage() == null ? var6.getClass().getSimpleName() : var6.getMessage();
					status.set(FfmpegBundleManager.Status.ERROR);
					fireProgress(new FfmpegBundleManager.DownloadProgress("error", 0L, 0L, 0.0));
					RecordableMod.LOGGER.warn("[FfmpegBundle] Download failed: {}", lastError, var6);
					ok = false;
				} finally {
					downloading.set(false);
				}

				return ok;
			});
			if (onComplete != null) {
				future.whenComplete((ok, ex) -> {
					try {
						onComplete.accept(ok != null && ok);
					} catch (Throwable var4) {
						RecordableMod.LOGGER.warn("[FfmpegBundle] onComplete threw: {}", var4.getMessage());
					}
				});
			}

			return future;
		}
	}

	private static void doDownloadAndInstall() throws IOException {
		Platform platform = PlatformUtils.detectPlatform();
		Path bundleDir = getBundleDirectory();
		Files.createDirectories(bundleDir);
		Path tempDir = Files.createTempDirectory("recordable-ffmpeg-");

		try {
			switch (platform) {
				case WINDOWS:
					downloadAndInstallWindows(tempDir, bundleDir);
					break;
				case LINUX:
					downloadAndInstallLinux(tempDir, bundleDir);
					break;
				case MACOS:
					downloadAndInstallMacOS(tempDir, bundleDir);
					break;
				case ANDROID:
					downloadAndInstallAndroid(tempDir);
					break;
				default:
					throw new IOException("Platform not supported for auto-download: " + platform.displayName());
			}
		} finally {
			deleteRecursive(tempDir);
		}
	}

	private static void downloadAndInstallWindows(Path tempDir, Path bundleDir) throws IOException {
		Path zipFile = tempDir.resolve("ffmpeg.zip");
		downloadFile("https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip", zipFile, "Downloading FFmpeg (Windows)");
		String expected = tryFetchExpectedHash("https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip.sha256", "SHA-256");
		if (expected != null) {
			String actual = computeSha256(zipFile);
			if (!expected.equalsIgnoreCase(actual)) {
				throw new IOException("SHA-256 mismatch for FFmpeg download. expected=" + expected + ", actual=" + actual);
			}

			RecordableMod.LOGGER.info("[FfmpegBundle] ✓ SHA-256 verified against gyan.dev");
		} else {
			RecordableMod.LOGGER.warn("[FfmpegBundle] Could not fetch upstream SHA-256, relying on HTTPS authentication only.");
		}

		fireProgress(new FfmpegBundleManager.DownloadProgress("extracting", 0L, 1L, 0.0));
		extractZipFindExecutable(zipFile, bundleDir, "ffmpeg.exe");
	}

	private static void downloadAndInstallLinux(Path tempDir, Path bundleDir) throws IOException {
		Path archive = tempDir.resolve("ffmpeg.tar.xz");
		downloadFile("https://johnvansickle.com/ffmpeg/releases/ffmpeg-release-amd64-static.tar.xz", archive, "Downloading FFmpeg (Linux)");
		String expectedMd5 = tryFetchExpectedHash("https://johnvansickle.com/ffmpeg/releases/ffmpeg-release-amd64-static.tar.xz.md5", "MD5");
		if (expectedMd5 != null) {
			String actual = computeMd5(archive);
			if (!expectedMd5.equalsIgnoreCase(actual)) {
				throw new IOException("MD5 mismatch for FFmpeg download. expected=" + expectedMd5 + ", actual=" + actual);
			}

			RecordableMod.LOGGER.info("[FfmpegBundle] ✓ MD5 verified against johnvansickle.com");
		} else {
			RecordableMod.LOGGER.warn("[FfmpegBundle] Could not fetch upstream MD5, relying on HTTPS authentication only.");
		}

		fireProgress(new FfmpegBundleManager.DownloadProgress("extracting", 0L, 1L, 0.0));
		Path extractDir = tempDir.resolve("extracted");
		Files.createDirectories(extractDir);
		runProcess(new String[]{"tar", "-xJf", archive.toAbsolutePath().toString(), "-C", extractDir.toAbsolutePath().toString()});
		Path ffmpegBin = findFileRecursive(extractDir, "ffmpeg");
		if (ffmpegBin == null) {
			throw new IOException("Could not locate 'ffmpeg' binary in extracted archive at " + extractDir);
		} else {
			Path target = bundleDir.resolve("ffmpeg");
			Files.copy(ffmpegBin, target, StandardCopyOption.REPLACE_EXISTING);
			setExecutablePermission(target);
			Path ffprobeBin = findFileRecursive(extractDir, "ffprobe");
			if (ffprobeBin != null) {
				Path probeTarget = bundleDir.resolve("ffprobe");
				Files.copy(ffprobeBin, probeTarget, StandardCopyOption.REPLACE_EXISTING);
				setExecutablePermission(probeTarget);
			}
		}
	}

	private static void downloadAndInstallMacOS(Path tempDir, Path bundleDir) throws IOException {
		Path zipFile = tempDir.resolve("ffmpeg.zip");
		downloadFile("https://evermeet.cx/ffmpeg/get/zip", zipFile, "Downloading FFmpeg (macOS)");
		RecordableMod.LOGGER.info("[FfmpegBundle] HTTPS-authenticated download from evermeet.cx (no sibling hash file)");
		fireProgress(new FfmpegBundleManager.DownloadProgress("extracting", 0L, 1L, 0.0));
		extractZipFindExecutable(zipFile, bundleDir, "ffmpeg");
	}

	private static void downloadAndInstallAndroid(Path tempDir) throws IOException {
		if (AndroidPlatform.hasTermuxFfmpeg()) {
			Path termux = AndroidPlatform.getTermuxFfmpegPath();
			if (termux != null && verifyBinaryExecution(termux)) {
				RecordableMod.LOGGER.info("[FfmpegBundle] [Android] Termux ffmpeg already usable at {}, skipping download", termux);
				return;
			}
		}

		if (!AndroidPlatform.isArm64()) {
			throw new IOException(
				"Android auto-download only supports arm64-v8a devices (detected " + AndroidPlatform.detectArchitecture().abi() + "). " + getAndroidManualInstructions()
			);
		} else {
			RecordableMod.LOGGER.info("[FfmpegBundle] [Android] Installing FFmpeg {} (arm64-v8a) from {}", "8.0.1", "hzw1199/Android-FFmpeg-Prebuilt");
			AndroidPlatform.logDiagnostics();
			Path tmpFfmpeg = tempDir.resolve("ffmpeg");
			downloadFile(
				"https://raw.githubusercontent.com/hzw1199/Android-FFmpeg-Prebuilt/main/ffmpeg-8.0.1/bin/ffmpeg", tmpFfmpeg, "Downloading FFmpeg (Android arm64)"
			);
			verifyHardcodedSha256(tmpFfmpeg, "206e84cd597408bbfaf51e27c14b17085590e639e40d568c28735077a6b708b3", "ffmpeg");
			Path tmpFfprobe = tempDir.resolve("ffprobe");
			boolean haveProbe = false;

			try {
				downloadFile(
					"https://raw.githubusercontent.com/hzw1199/Android-FFmpeg-Prebuilt/main/ffmpeg-8.0.1/bin/ffprobe", tmpFfprobe, "Downloading ffprobe (Android arm64)"
				);
				verifyHardcodedSha256(tmpFfprobe, "42d18430d4a8b5e6efaf135faa4122a13087ac4a2d20933b81a2b781da07d9d0", "ffprobe");
				haveProbe = true;
			} catch (IOException var13) {
				RecordableMod.LOGGER.warn("[FfmpegBundle] [Android] ffprobe unavailable, continuing with ffmpeg only: {}", var13.getMessage());
			}

			fireProgress(new FfmpegBundleManager.DownloadProgress("installing", 0L, 1L, 0.0));
			List<Path> execDirs = AndroidPlatform.getExecCapableDirectories();
			if (execDirs.isEmpty()) {
				throw new IOException("No candidate exec-capable directories on this device. " + getAndroidManualInstructions());
			} else {
				RecordableMod.LOGGER.info("[FfmpegBundle] [Android] Will try {} exec-candidate dir(s) in order:", execDirs.size());

				for (int i = 0; i < execDirs.size(); i++) {
					RecordableMod.LOGGER.info("[FfmpegBundle] [Android]   {}. {}", i + 1, execDirs.get(i));
				}

				IOException lastFailure = null;

				for (Path dir : execDirs) {
					try {
						Files.createDirectories(dir);
						Path target = dir.resolve("recordable-ffmpeg");
						Files.copy(tmpFfmpeg, target, StandardCopyOption.REPLACE_EXISTING);
						applyAndroidExecPermissions(target);
						if (verifyBinaryExecution(target)) {
							RecordableMod.LOGGER.info("[FfmpegBundle] [Android] ✓ FFmpeg installed and verified executable at {}", target);
							if (haveProbe) {
								try {
									Path probeTarget = dir.resolve("recordable-ffprobe");
									Files.copy(tmpFfprobe, probeTarget, StandardCopyOption.REPLACE_EXISTING);
									applyAndroidExecPermissions(probeTarget);
									RecordableMod.LOGGER.info("[FfmpegBundle] [Android] ✓ ffprobe installed at {}", probeTarget);
								} catch (IOException var10) {
									RecordableMod.LOGGER.warn("[FfmpegBundle] [Android] Could not install ffprobe at {}: {}", dir, var10.getMessage());
								}
							}

							return;
						}

						lastFailure = new IOException("Copied to " + dir + " but could not execute it (noexec mount or SELinux denial).");
						RecordableMod.LOGGER.warn("[FfmpegBundle] [Android] {}", lastFailure.getMessage());

						try {
							Files.deleteIfExists(target);
						} catch (IOException var11) {
						}
					} catch (IOException var12) {
						lastFailure = var12;
						RecordableMod.LOGGER.warn("[FfmpegBundle] [Android] Install into {} failed: {}", dir, var12.getMessage());
					}
				}

				throw new IOException(
					"Downloaded FFmpeg but no Android directory permitted execution (all candidates were noexec/SELinux-blocked). "
						+ (lastFailure != null ? "Last error: " + lastFailure.getMessage() + ". " : "")
						+ getAndroidManualInstructions()
				);
			}
		}
	}

	private static void verifyHardcodedSha256(Path file, String expected, String label) throws IOException {
		String actual = computeSha256(file);
		if (!expected.equalsIgnoreCase(actual)) {
			throw new IOException("SHA-256 mismatch for Android " + label + " - refusing to install. expected=" + expected + ", actual=" + actual);
		} else {
			RecordableMod.LOGGER.info("[FfmpegBundle] [Android] ✓ SHA-256 verified for {} ({})", label, expected);
		}
	}

	private static void applyAndroidExecPermissions(Path path) {
		try {
			File f = path.toFile();
			f.setReadable(true, false);
			f.setExecutable(true, false);
		} catch (Exception var2) {
		}

		boolean ok = setExecutablePermission(path);
		RecordableMod.LOGGER.info("[FfmpegBundle] [Android] chmod +x {} → {} (executable={})", new Object[]{path, ok ? "ok" : "fell back", Files.isExecutable(path)});
	}

	private static void extractZipFindExecutable(Path zipFile, Path bundleDir, String exeName) throws IOException {
		boolean found = false;
		ZipInputStream zin = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zipFile)));

		ZipEntry entry;
		try {
			while ((entry = zin.getNextEntry()) != null) {
				if (!entry.isDirectory()) {
					String name = entry.getName();
					String basename = name.substring(name.lastIndexOf(47) + 1);
					if (basename.equalsIgnoreCase(exeName)) {
						Path target = bundleDir.resolve(exeName);
						Files.copy(zin, target, new CopyOption[]{StandardCopyOption.REPLACE_EXISTING});
						setExecutablePermission(target);
						RecordableMod.LOGGER.info("[FfmpegBundle] Extracted {} → {}", name, target);
						found = true;
					} else if (basename.equalsIgnoreCase("ffprobe.exe") || basename.equalsIgnoreCase("ffprobe")) {
						String probeName = PlatformUtils.isWindows() ? "ffprobe.exe" : "ffprobe";
						Path target = bundleDir.resolve(probeName);
						Files.copy(zin, target, new CopyOption[]{StandardCopyOption.REPLACE_EXISTING});
						setExecutablePermission(target);
						RecordableMod.LOGGER.info("[FfmpegBundle] Extracted {} → {}", name, target);
					}
				}
			}
		} catch (Throwable var11) {
			try {
				zin.close();
			} catch (Throwable var10) {
				var11.addSuppressed(var10);
			}

			throw var11;
		}

		zin.close();
		if (!found) {
			throw new IOException("Could not find '" + exeName + "' inside downloaded archive.");
		}
	}

	private static Path findFileRecursive(Path root, String filename) throws IOException {
		Stream<Path> stream = Files.walk(root);

		Path var3;
		try {
			var3 = (Path)stream.filter(x$0 -> Files.isRegularFile(x$0, new LinkOption[0]))
				.filter(p -> p.getFileName().toString().equals(filename))
				.findFirst()
				.orElse(null);
		} catch (Throwable var6) {
			if (stream != null) {
				try {
					stream.close();
				} catch (Throwable var5) {
					var6.addSuppressed(var5);
				}
			}

			throw var6;
		}

		if (stream != null) {
			stream.close();
		}

		return var3;
	}

	private static void runProcess(String[] cmd) throws IOException {
		try {
			Process proc = new ProcessBuilder(cmd).redirectErrorStream(true).start();
			boolean done = proc.waitFor(2L, TimeUnit.MINUTES);
			if (!done) {
				proc.destroyForcibly();
				throw new IOException("Process timed out: " + String.join(" ", cmd));
			} else if (proc.exitValue() != 0) {
				String out = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
				throw new IOException("Process failed (" + proc.exitValue() + "): " + String.join(" ", cmd) + "\n" + out);
			}
		} catch (InterruptedException var4) {
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted while running " + String.join(" ", cmd), var4);
		}
	}

	private static void downloadFile(String urlStr, Path target, String phase) throws IOException {
		RecordableMod.LOGGER.info("[FfmpegBundle] Downloading: {}", urlStr);
		HttpURLConnection conn = openConnectionFollowingRedirects(urlStr, 5);
		long contentLength = conn.getContentLengthLong();
		long downloaded = 0L;
		long lastReport = 0L;

		try {
			InputStream in = new BufferedInputStream(conn.getInputStream());

			try {
				OutputStream out = Files.newOutputStream(target);

				try {
					byte[] buf = new byte[65536];

					int n;
					while ((n = in.read(buf)) > 0) {
						out.write(buf, 0, n);
						downloaded += (long)n;
						long now = System.currentTimeMillis();
						if (now - lastReport > 200L || contentLength > 0L && downloaded >= contentLength) {
							double frac = contentLength > 0L ? (double)downloaded / (double)contentLength : 0.0;
							fireProgress(new FfmpegBundleManager.DownloadProgress(phase, downloaded, contentLength, frac));
							lastReport = now;
						}
					}
				} catch (Throwable var26) {
					if (out != null) {
						try {
							out.close();
						} catch (Throwable var25) {
							var26.addSuppressed(var25);
						}
					}

					throw var26;
				}

				if (out != null) {
					out.close();
				}
			} catch (Throwable var27) {
				try {
					in.close();
				} catch (Throwable var24) {
					var27.addSuppressed(var24);
				}

				throw var27;
			}

			in.close();
		} finally {
			conn.disconnect();
		}

		RecordableMod.LOGGER.info("[FfmpegBundle] Downloaded {} bytes to {}", downloaded, target);
	}

	private static HttpURLConnection openConnectionFollowingRedirects(String urlStr, int maxHops) throws IOException {
		String current = urlStr;

		for (int i = 0; i < maxHops; i++) {
			URL url;
			try {
				url = URI.create(current).toURL();
			} catch (IllegalArgumentException var8) {
				throw new IOException("Invalid URL: " + current, var8);
			}

			if (!"https".equalsIgnoreCase(url.getProtocol())) {
				throw new IOException("Refusing non-HTTPS URL: " + current);
			}

			HttpURLConnection conn = (HttpURLConnection)url.openConnection();
			conn.setConnectTimeout(15000);
			conn.setReadTimeout(30000);
			conn.setRequestProperty("User-Agent", "Record-able/" + getModVersion() + " (+https://modrinth.com/mod/record-able)");
			conn.setInstanceFollowRedirects(false);
			int code = conn.getResponseCode();
			if (code >= 200 && code < 300) {
				return conn;
			}

			if (code < 300 || code >= 400) {
				conn.disconnect();
				throw new IOException("HTTP " + code + " from " + current);
			}

			String loc = conn.getHeaderField("Location");
			conn.disconnect();
			if (loc == null) {
				throw new IOException("HTTP " + code + " redirect with no Location header from " + current);
			}

			if (loc.startsWith("/")) {
				loc = url.getProtocol() + "://" + url.getHost() + loc;
			}

			current = loc;
		}

		throw new IOException("Too many redirects starting at " + urlStr);
	}

	private static String getModVersion() {
		try {
			return (String)FabricLoader.getInstance().getModContainer("recordable").map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
		} catch (Exception var1) {
			return "unknown";
		}
	}

	private static String tryFetchExpectedHash(String url, String algoLabel) {
		try {
			HttpURLConnection conn = openConnectionFollowingRedirects(url, 5);

			String var17;
			try {
				BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));

				label108: {
					String token;
					label109: {
						try {
							String line = reader.readLine();
							if (line == null) {
								token = null;
								break label109;
							}

							token = line.trim().split("\\s+")[0];
							if (token.matches("[0-9a-fA-F]+")) {
								RecordableMod.LOGGER.info("[FfmpegBundle] Fetched expected {}: {}", algoLabel, token);
								var17 = token;
								break label108;
							}

							var17 = null;
						} catch (Throwable var13) {
							try {
								reader.close();
							} catch (Throwable var12) {
								var13.addSuppressed(var12);
							}

							throw var13;
						}

						reader.close();
						return var17;
					}

					reader.close();
					return token;
				}

				reader.close();
			} finally {
				conn.disconnect();
			}

			return var17;
		} catch (Exception var15) {
			RecordableMod.LOGGER.debug("[FfmpegBundle] Could not fetch {} from {}: {}", new Object[]{algoLabel, url, var15.getMessage()});
			return null;
		}
	}

	private static String computeSha256(Path file) throws IOException {
		return computeHash(file, "SHA-256");
	}

	private static String computeMd5(Path file) throws IOException {
		return computeHash(file, "MD5");
	}

	private static String computeHash(Path file, String algorithm) throws IOException {
		MessageDigest md;
		try {
			md = MessageDigest.getInstance(algorithm);
		} catch (NoSuchAlgorithmException var7) {
			throw new IOException("Hash algorithm not available: " + algorithm, var7);
		}

		InputStream in = new BufferedInputStream(Files.newInputStream(file));

		try {
			byte[] buf = new byte[65536];

			int n;
			while ((n = in.read(buf)) > 0) {
				md.update(buf, 0, n);
			}
		} catch (Throwable var8) {
			try {
				in.close();
			} catch (Throwable var6) {
				var8.addSuppressed(var6);
			}

			throw var8;
		}

		in.close();
		return HexFormat.of().formatHex(md.digest());
	}

	private static void deleteRecursive(Path root) {
		if (root != null && Files.exists(root, new LinkOption[0])) {
			try {
				Stream<Path> stream = Files.walk(root);

				try {
					stream.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> {
						try {
							Files.deleteIfExists(p);
						} catch (IOException var2) {
						}
					});
				} catch (Throwable var5) {
					if (stream != null) {
						try {
							stream.close();
						} catch (Throwable var4) {
							var5.addSuppressed(var4);
						}
					}

					throw var5;
				}

				if (stream != null) {
					stream.close();
				}
			} catch (IOException var6) {
			}
		}
	}

	public static String getExecutableName() {
		return PlatformUtils.isWindows() ? "ffmpeg.exe" : "ffmpeg";
	}

	private static boolean setExecutablePermission(Path path) {
		try {
			if (path.toFile().setExecutable(true, false)) {
				return true;
			}
		} catch (Exception var3) {
		}

		try {
			Process p = Runtime.getRuntime().exec(new String[]{"chmod", "+x", path.toAbsolutePath().toString()});
			return p.waitFor() == 0;
		} catch (Exception var2) {
			RecordableMod.LOGGER.debug("[FfmpegBundle] chmod failed: {}", var2.getMessage());
			return false;
		}
	}

	static String detectArmArchitecture() {
		return switch (AndroidPlatform.detectArchitecture()) {
			case ARM64 -> "arm64";
			case ARM32 -> "arm32";
			default -> "unknown";
		};
	}

	public static String getStatusDescription() {
		String path = getBundledFfmpegPath();
		if (path != null) {
			return "FFmpeg ready: " + path;
		} else {
			return switch ((FfmpegBundleManager.Status)status.get()) {
				case DOWNLOADING -> "Downloading FFmpeg from " + getDownloadSourceDescription();
				case ERROR -> "FFmpeg download failed: " + (lastError == null ? "unknown error" : lastError);
				case AVAILABLE -> "FFmpeg ready";
				case NOT_FOUND -> isAutoDownloadSupported()
				? "FFmpeg not installed - click 'Download FFmpeg' to fetch it (" + getEstimatedDownloadSize() + " from " + getDownloadSourceDescription() + ")."
				: "FFmpeg not installed. " + getManualInstallInstructions();
			};
		}
	}

	public static String getManualInstallInstructions() {
		return switch (PlatformUtils.detectPlatform()) {
			case WINDOWS -> "Manual install: download ffmpeg-release-essentials.zip from https://www.gyan.dev/ffmpeg/builds/ and extract ffmpeg.exe to "
			+ getBundleDirectory();
			case LINUX -> "Manual install: install via 'sudo apt install ffmpeg' (Debian/Ubuntu), 'sudo dnf install ffmpeg' (Fedora), 'sudo pacman -S ffmpeg' (Arch), or download from https://johnvansickle.com/ffmpeg/ and place 'ffmpeg' at "
			+ getBundleDirectory();
			case MACOS -> "Manual install: 'brew install ffmpeg' or download from https://evermeet.cx/ffmpeg/ and place 'ffmpeg' at " + getBundleDirectory();
			case ANDROID -> getAndroidManualInstructions();
			case UNKNOWN -> "Please install FFmpeg from https://ffmpeg.org/ and add it to PATH.";
		};
	}

	public static String getAndroidManualInstructions() {
		String autoNote = AndroidPlatform.isArm64()
			? "On arm64-v8a devices, try the in-game 'Download FFmpeg' button first. If that fails (noexec/SELinux), use one of these manual options:\n"
			: "Auto-download requires an arm64-v8a device. Manual options:\n";
		return autoNote
			+ "  1. Install Termux from F-Droid and run: pkg install ffmpeg\n     Then set 'ffmpegPath' in the config to /data/data/com.termux/files/usr/bin/ffmpeg.\n  2. Download a static arm64 build (e.g. from hzw1199/Android-FFmpeg-Prebuilt) and place\n     it at "
			+ getBundleDirectory().resolve("ffmpeg")
			+ " then chmod +x it.";
	}

	public static void runDiagnostics() {
		RecordableMod.LOGGER.info("[FfmpegBundle] ── Diagnostics (lite) ──");
		AndroidPlatform.logDiagnostics();
		RecordableMod.LOGGER.info("[FfmpegBundle]   Platform: {}", PlatformUtils.detectPlatform().displayName());
		RecordableMod.LOGGER.info("[FfmpegBundle]   os.arch:  {}", System.getProperty("os.arch", "?"));
		RecordableMod.LOGGER.info("[FfmpegBundle]   Game dir: {}", FabricLoader.getInstance().getGameDir());
		RecordableMod.LOGGER.info("[FfmpegBundle]   Bundle dir: {} (exists={})", getBundleDirectory(), Files.exists(getBundleDirectory(), new LinkOption[0]));
		RecordableMod.LOGGER.info("[FfmpegBundle]   Auto-download supported: {}", isAutoDownloadSupported());
		RecordableMod.LOGGER.info("[FfmpegBundle]   Download source: {}", getDownloadSourceDescription());
		RecordableMod.LOGGER.info("[FfmpegBundle]   Current status:  {}", getStatusDescription());
		Path candidate = getBundleDirectory().resolve(getExecutableName());
		RecordableMod.LOGGER
			.info(
				"[FfmpegBundle]   Candidate file: {} (exists={}, executable={})",
				new Object[]{candidate, Files.exists(candidate, new LinkOption[0]), Files.exists(candidate, new LinkOption[0]) && Files.isExecutable(candidate)}
			);
	}

	public static record DownloadProgress(String phase, long bytesDownloaded, long totalBytes, double fraction) {
		public static final FfmpegBundleManager.DownloadProgress IDLE = new FfmpegBundleManager.DownloadProgress("idle", 0L, 0L, 0.0);

		public String displayPercent() {
			if (this.totalBytes <= 0L) {
				return this.bytesDownloaded > 0L ? humanBytes(this.bytesDownloaded) : "0%";
			} else {
				return String.format(Locale.ROOT, "%.1f%%", this.fraction * 100.0);
			}
		}

		public String displayBytes() {
			return this.totalBytes <= 0L ? humanBytes(this.bytesDownloaded) : humanBytes(this.bytesDownloaded) + " / " + humanBytes(this.totalBytes);
		}

		private static String humanBytes(long bytes) {
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
	}

	public static enum ExecMethod {
		DIRECT,
		LINKER64,
		LINKER32,
		SHELL;
	}

	@FunctionalInterface
	public interface ProgressListener {
		void onProgress(FfmpegBundleManager.DownloadProgress downloadProgress);
	}

	public static enum Status {
		NOT_FOUND,
		DOWNLOADING,
		ERROR,
		AVAILABLE;
	}
}
