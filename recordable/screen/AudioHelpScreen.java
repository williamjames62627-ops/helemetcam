package dev.recordable.screen;

import dev.recordable.AudioCapture;
import dev.recordable.RecordableConfig;
import dev.recordable.compat.RenderHelper;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.class_2561;
import net.minecraft.class_332;
import net.minecraft.class_4185;
import net.minecraft.class_437;

public final class AudioHelpScreen extends class_437 {
	private static final int PANEL_COLOR = -804253680;
	private static final int PANEL_BORDER_COLOR = -12434878;
	private static final int HEADER_COLOR = -1;
	private static final int TEXT_COLOR = -3092272;
	private static final int HIGHLIGHT_COLOR = -7811960;
	private static final int WARNING_COLOR = -13244;
	private final class_437 parent;
	private int panelLeft;
	private int panelTop;
	private int panelWidth;
	private int panelBottom;
	private int scrollOffset;
	private int contentHeight;
	private int bodyTop;
	private int bodyBottom;
	private final List<AudioHelpScreen.HelpLine> helpLines = new ArrayList();

	public AudioHelpScreen(class_437 parent) {
		super(class_2561.method_43471("screen.recordable.audio_help.title"));
		this.parent = parent;
	}

	protected void method_25426() {
		super.method_25426();
		this.helpLines.clear();
		this.scrollOffset = 0;
		this.panelWidth = Math.max(340, Math.min((int)((double)this.field_22789 * 0.8), 600));
		this.panelLeft = (this.field_22789 - this.panelWidth) / 2;
		this.panelTop = Math.max(8, (int)((double)this.field_22790 * 0.05));
		this.panelBottom = Math.min(this.field_22790 - 8, this.panelTop + Math.max(300, (int)((double)this.field_22790 * 0.88)));
		this.bodyTop = this.panelTop + 24;
		this.bodyBottom = this.panelBottom - 34;
		this.addHeader("Audio Capture - How It Works");
		this.addBlank();
		this.addHighlight("OpenAL Loopback Capture (Default)");
		this.addText("Record-able captures audio DIRECTLY from Minecraft's");
		this.addText("audio engine using OpenAL's ALC_SOFT_loopback extension.");
		this.addBlank();
		this.addText("What this means for you:");
		this.addText("  No Stereo Mix setup needed");
		this.addText("  No virtual cables or BlackHole required");
		this.addText("  No system-wide audio routing tricks");
		this.addText("  Works the same way on every platform");
		this.addBlank();
		this.addText("All game sounds, music, ambient effects, and mod");
		this.addText("sounds are captured at full digital quality:");
		this.addText("  48 kHz, Stereo, 16-bit PCM");
		this.addBlank();
		this.addHighlight("Why This Is Better");
		this.addText("Older recorders relied on Stereo Mix or BlackHole to");
		this.addText("pull audio from the speakers. That approach is fragile,");
		this.addText("noisy, and tied to OS volume sliders.");
		this.addBlank();
		this.addText("Loopback capture taps into the audio mixer BEFORE the");
		this.addText("sound leaves Minecraft. The result: clean audio at");
		this.addText("100% volume, regardless of how loud you set Windows or");
		this.addText("your phone.");
		this.addBlank();
		String platform = AudioCapture.getPlatform();
		switch (platform) {
			case "windows":
				this.buildWindowsHelp();
				break;
			case "linux":
				this.buildLinuxHelp();
				break;
			case "macos":
				this.buildMacOSHelp();
				break;
			case "android":
				this.buildAndroidHelp();
				break;
			default:
				this.addWarning("Unsupported platform for audio: " + platform);
		}

		this.addBlank();
		this.addHeader("General Troubleshooting");
		this.addText("1. Verify 'Capture Audio' is ON in settings");
		this.addText("2. Make sure Minecraft sound is NOT muted in");
		this.addText("   Options > Music & Sounds");
		this.addText("3. Click 'Test Audio' to verify the recorder is");
		this.addText("   receiving samples");
		this.addText("4. Restart Minecraft once if you toggled audio mods");
		this.addText("5. If you hear no audio in the recording, check that");
		this.addText("   the master volume slider in-game is above 0%");
		this.addText("6. Check latest.log for any audio warnings");
		this.addBlank();
		this.addHeader("Volume Slider");
		this.addText("Use the Audio Volume slider (0 to 200%) to adjust the");
		this.addText("recorded audio level:");
		this.addText("  100% = normal volume (default)");
		this.addText("  150% = boost quiet audio by 1.5x");
		this.addText("  50%  = reduce loud audio by half");
		this.addText("  0%   = muted (no audio in recording)");
		this.addBlank();
		this.addText("This slider only changes the RECORDED audio. It does");
		this.addText("not affect what you hear while playing.");
		this.addBlank();
		this.addHeader("Mono vs Stereo");
		this.addText("Stereo (default) is recommended for music and most");
		this.addText("gameplay. Switch to Mono if you only need voice or");
		this.addText("if your output device is mono.");
		this.contentHeight = this.helpLines.size() * 12 + 10;
		int buttonWidth = 120;
		this.method_37063(
			class_4185.method_46430(class_2561.method_43471("screen.recordable.audio_help.back"), button -> this.method_25419())
				.method_46434((this.field_22789 - buttonWidth) / 2, this.panelBottom - 28, buttonWidth, 20)
				.method_46431()
		);
	}

	private void buildWindowsHelp() {
		this.addHeader("Windows");
		this.addBlank();
		this.addHighlight("Nothing to set up");
		this.addText("OpenAL loopback works out of the box on Windows.");
		this.addText("You do NOT need to enable Stereo Mix, install");
		this.addText("VB-Cable, or change any audio routing.");
		this.addBlank();
		this.addText("If audio still does not record:");
		this.addText("1. Make sure 'Capture Audio' is ON in settings");
		this.addText("2. Confirm Minecraft is producing sound");
		this.addText("   (check the Music & Sounds menu)");
		this.addText("3. Click 'Test Audio' in the settings screen");
	}

	private void buildLinuxHelp() {
		this.addHeader("Linux");
		this.addBlank();
		this.addHighlight("Nothing to set up");
		this.addText("OpenAL loopback works on PulseAudio, PipeWire, and");
		this.addText("ALSA without any extra configuration.");
		this.addBlank();
		this.addText("If audio still does not record:");
		this.addText("1. Make sure 'Capture Audio' is ON in settings");
		this.addText("2. Confirm Minecraft is producing sound");
		this.addText("3. Click 'Test Audio' in the settings screen");
		this.addText("4. If using PipeWire, make sure the OpenAL backend");
		this.addText("   is not forced to a specific device");
	}

	private void buildMacOSHelp() {
		this.addHeader("macOS");
		this.addBlank();
		this.addHighlight("Nothing to set up");
		this.addText("OpenAL loopback works out of the box on macOS.");
		this.addText("BlackHole and Multi-Output Devices are NOT required.");
		this.addBlank();
		this.addText("If audio still does not record:");
		this.addText("1. Make sure 'Capture Audio' is ON in settings");
		this.addText("2. Confirm Minecraft is producing sound");
		this.addText("3. Click 'Test Audio' in the settings screen");
	}

	private void buildAndroidHelp() {
		this.addHeader("Android (PojavLauncher / Zalith / FCL)");
		this.addBlank();
		this.addHighlight("Nothing to set up");
		this.addText("OpenAL loopback is automatic on Android launchers.");
		this.addText("You do NOT need root, special permissions, or any");
		this.addText("extra audio plugin. Just keep 'Capture Audio' ON.");
		this.addBlank();
		this.addText("If audio still does not record:");
		this.addText("1. Make sure 'Capture Audio' is ON in settings");
		this.addText("2. Confirm Minecraft is producing sound");
		this.addText("   (turn up the master volume slider)");
		this.addText("3. Click 'Test Audio' to verify the recorder");
		this.addText("   is receiving samples");
		this.addBlank();
		this.addWarning("Tip: Some launchers need OpenAL Soft enabled in");
		this.addWarning("their launcher settings. If audio is silent, look");
		this.addWarning("for an OpenAL or Sound option in your launcher.");
	}

	private void addHeader(String text) {
		this.helpLines.add(new AudioHelpScreen.HelpLine(text, -1, true));
	}

	private void addText(String text) {
		this.helpLines.add(new AudioHelpScreen.HelpLine(text, -3092272, false));
	}

	private void addHighlight(String text) {
		this.helpLines.add(new AudioHelpScreen.HelpLine(text, -7811960, false));
	}

	private void addWarning(String text) {
		this.helpLines.add(new AudioHelpScreen.HelpLine(text, -13244, false));
	}

	private void addBlank() {
		this.helpLines.add(new AudioHelpScreen.HelpLine("", -3092272, false));
	}

	public boolean method_25401(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		int maxScroll = Math.max(0, this.contentHeight - (this.bodyBottom - this.bodyTop));
		if (maxScroll <= 0) {
			return super.method_25401(mouseX, mouseY, horizontalAmount, verticalAmount);
		} else {
			int delta = (int)Math.round(verticalAmount * -20.0);
			if (delta == 0) {
				delta = verticalAmount > 0.0 ? -20 : 20;
			}

			this.scrollOffset = Math.max(0, Math.min(maxScroll, this.scrollOffset + delta));
			return true;
		}
	}

	public void method_25394(class_332 context, int mouseX, int mouseY, float delta) {
		this.method_25420(context, mouseX, mouseY, delta);
		int accent = 0xFF000000 | RecordableConfig.get().getMenuAccentColorRgb();
		int left = this.panelLeft - 6;
		int right = this.panelLeft + this.panelWidth + 6;
		context.method_25294(left, this.panelTop - 6, right, this.panelBottom, -804253680);
		context.method_25294(left, this.panelTop - 6, right, this.panelTop - 5, accent);
		context.method_25294(left, this.panelBottom - 1, right, this.panelBottom, -12434878);
		context.method_25294(left, this.panelTop - 6, left + 1, this.panelBottom, -12434878);
		context.method_25294(right - 1, this.panelTop - 6, right, this.panelBottom, -12434878);
		context.method_27534(this.field_22793, this.field_22785, this.field_22789 / 2, this.panelTop, -1);
		int textLeft = this.panelLeft + 14;

		for (int i = 0; i < this.helpLines.size(); i++) {
			int y = this.bodyTop + i * 12 - this.scrollOffset;
			if (y >= this.bodyTop - 12 && y <= this.bodyBottom + 2) {
				AudioHelpScreen.HelpLine line = (AudioHelpScreen.HelpLine)this.helpLines.get(i);
				if (!line.text.isEmpty()) {
					if (line.bold) {
						RenderHelper.drawText(context, this.field_22793, class_2561.method_43470("§l" + line.text), textLeft, y, line.color);
					} else {
						RenderHelper.drawText(context, this.field_22793, class_2561.method_43470(line.text), textLeft, y, line.color);
					}
				}
			}
		}

		super.method_25394(context, mouseX, mouseY, delta);
	}

	public void method_25419() {
		if (this.field_22787 != null) {
			this.field_22787.method_1507(this.parent);
		}
	}

	private static record HelpLine(String text, int color, boolean bold) {
	}
}
