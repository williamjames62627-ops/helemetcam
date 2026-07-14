package dev.recordable.theme;

public final class ThemeColors {
	public final int panelBackground;
	public final int panelBackgroundAlt;
	public final int panelBorder;
	public final int accent;
	public final int accentHover;
	public final int accentDim;
	public final int textPrimary;
	public final int textSecondary;
	public final int textMuted;
	public final int textError;
	public final int textSuccess;
	public final int headerText;
	public final int headerUnderline;
	public final int sectionBackground;
	public final int sectionHover;
	public final int buttonBackground;
	public final int buttonBackgroundHover;
	public final int buttonBorder;
	public final int buttonText;
	public final int scrollTrack;
	public final int scrollThumb;
	public final int scanlineColor;
	public final int grainColor;
	public final int glitchColor;
	public final int vignetteColor;

	public ThemeColors(ThemeColors.Builder builder) {
		this.panelBackground = builder.panelBackground;
		this.panelBackgroundAlt = builder.panelBackgroundAlt;
		this.panelBorder = builder.panelBorder;
		this.accent = builder.accent;
		this.accentHover = builder.accentHover;
		this.accentDim = builder.accentDim;
		this.textPrimary = builder.textPrimary;
		this.textSecondary = builder.textSecondary;
		this.textMuted = builder.textMuted;
		this.textError = builder.textError;
		this.textSuccess = builder.textSuccess;
		this.headerText = builder.headerText;
		this.headerUnderline = builder.headerUnderline;
		this.sectionBackground = builder.sectionBackground;
		this.sectionHover = builder.sectionHover;
		this.buttonBackground = builder.buttonBackground;
		this.buttonBackgroundHover = builder.buttonBackgroundHover;
		this.buttonBorder = builder.buttonBorder;
		this.buttonText = builder.buttonText;
		this.scrollTrack = builder.scrollTrack;
		this.scrollThumb = builder.scrollThumb;
		this.scanlineColor = builder.scanlineColor;
		this.grainColor = builder.grainColor;
		this.glitchColor = builder.glitchColor;
		this.vignetteColor = builder.vignetteColor;
	}

	public static ThemeColors classic() {
		return new ThemeColors.Builder()
			.panelBackground(-804253680)
			.panelBackgroundAlt(-803727336)
			.panelBorder(-12434878)
			.accent(-48060)
			.accentHover(-39322)
			.accentDim(-6741470)
			.textPrimary(-1)
			.textSecondary(-2039584)
			.textMuted(-4671304)
			.textError(-34953)
			.textSuccess(-8913033)
			.headerText(-1)
			.headerUnderline(-48060)
			.sectionBackground(-15066598)
			.sectionHover(-14342875)
			.buttonBackground(-14013910)
			.buttonBackgroundHover(-12961222)
			.buttonBorder(-11184811)
			.buttonText(-2039584)
			.scrollTrack(-14013910)
			.scrollThumb(-48060)
			.scanlineColor(0)
			.grainColor(0)
			.glitchColor(0)
			.vignetteColor(0)
			.build();
	}

	public static ThemeColors vhs() {
		return new ThemeColors.Builder()
			.panelBackground(-536212972)
			.panelBackgroundAlt(-535949796)
			.panelBorder(-15060139)
			.accent(-3400162)
			.accentHover(-1166541)
			.accentDim(-7858923)
			.textPrimary(-2829108)
			.textSecondary(-5592423)
			.textMuted(-8947866)
			.textError(-43691)
			.textSuccess(-11141240)
			.headerText(-1118499)
			.headerUnderline(-3400162)
			.sectionBackground(-15856102)
			.sectionHover(-15329752)
			.buttonBackground(-15461334)
			.buttonBackgroundHover(-14803398)
			.buttonBorder(-14013867)
			.buttonText(-3355461)
			.scrollTrack(-15461334)
			.scrollThumb(-3400162)
			.scanlineColor(402653184)
			.grainColor(218103807)
			.glitchColor(369033216)
			.vignetteColor(1073741824)
			.build();
	}

	public static ThemeColors cinema() {
		return new ThemeColors.Builder()
			.panelBackground(-535162872)
			.panelBackgroundAlt(-534636528)
			.panelBorder(-11913952)
			.accent(-2840506)
			.accentHover(-1130400)
			.accentDim(-7704528)
			.textPrimary(-661812)
			.textSecondary(-3359840)
			.textMuted(-6715290)
			.textError(-34987)
			.textSuccess(-7807642)
			.headerText(-661812)
			.headerUnderline(-2840506)
			.sectionBackground(-14806008)
			.sectionHover(-14017008)
			.buttonBackground(-14542320)
			.buttonBackgroundHover(-13424616)
			.buttonBorder(-11189214)
			.buttonText(-2241366)
			.scrollTrack(-14542320)
			.scrollThumb(-2840506)
			.scanlineColor(0)
			.grainColor(184540586)
			.glitchColor(0)
			.vignetteColor(1342177280)
			.build();
	}

	public static ThemeColors neon() {
		return new ThemeColors.Builder()
			.panelBackground(-536215528)
			.panelBackgroundAlt(-535822304)
			.panelBorder(-14020512)
			.accent(-65281)
			.accentHover(-47873)
			.accentDim(-5635926)
			.textPrimary(-1118465)
			.textSecondary(-4473891)
			.textMuted(-7829334)
			.textError(-48026)
			.textSuccess(-12255318)
			.headerText(-16711681)
			.headerUnderline(-65281)
			.sectionBackground(-15859680)
			.sectionHover(-15204304)
			.buttonBackground(-15466448)
			.buttonBackgroundHover(-14679996)
			.buttonBorder(-12320598)
			.buttonText(-2236929)
			.scrollTrack(-15466448)
			.scrollThumb(-65281)
			.scanlineColor(285147391)
			.grainColor(150994943)
			.glitchColor(352387071)
			.vignetteColor(805306419)
			.build();
	}

	public static ThemeColors minimal() {
		return new ThemeColors.Builder()
			.panelBackground(-535555052)
			.panelBackgroundAlt(-535291880)
			.panelBorder(-13421773)
			.accent(-2236963)
			.accentHover(-1)
			.accentDim(-6710887)
			.textPrimary(-1118482)
			.textSecondary(-4473925)
			.textMuted(-7829368)
			.textError(-39322)
			.textSuccess(-10027162)
			.headerText(-1)
			.headerUnderline(-11184811)
			.sectionBackground(-15066598)
			.sectionHover(-14540254)
			.buttonBackground(-14342875)
			.buttonBackgroundHover(-13421773)
			.buttonBorder(-12303292)
			.buttonText(-2236963)
			.scrollTrack(-14540254)
			.scrollThumb(-7829368)
			.scanlineColor(0)
			.grainColor(0)
			.glitchColor(0)
			.vignetteColor(0)
			.build();
	}

	public static ThemeColors forPreset(ThemePreset preset) {
		return switch (preset) {
			case VHS -> vhs();
			case CINEMA -> cinema();
			case NEON -> neon();
			case MINIMAL -> minimal();
			default -> classic();
		};
	}

	public static final class Builder {
		private int panelBackground = -804253680;
		private int panelBackgroundAlt = -803727336;
		private int panelBorder = -12434878;
		private int accent = -48060;
		private int accentHover = -39322;
		private int accentDim = -6741470;
		private int textPrimary = -1;
		private int textSecondary = -2039584;
		private int textMuted = -4671304;
		private int textError = -34953;
		private int textSuccess = -8913033;
		private int headerText = -1;
		private int headerUnderline = -48060;
		private int sectionBackground = -15066598;
		private int sectionHover = -14342875;
		private int buttonBackground = -14013910;
		private int buttonBackgroundHover = -12961222;
		private int buttonBorder = -11184811;
		private int buttonText = -2039584;
		private int scrollTrack = -14013910;
		private int scrollThumb = -48060;
		private int scanlineColor = 0;
		private int grainColor = 0;
		private int glitchColor = 0;
		private int vignetteColor = 0;

		public ThemeColors.Builder panelBackground(int v) {
			this.panelBackground = v;
			return this;
		}

		public ThemeColors.Builder panelBackgroundAlt(int v) {
			this.panelBackgroundAlt = v;
			return this;
		}

		public ThemeColors.Builder panelBorder(int v) {
			this.panelBorder = v;
			return this;
		}

		public ThemeColors.Builder accent(int v) {
			this.accent = v;
			return this;
		}

		public ThemeColors.Builder accentHover(int v) {
			this.accentHover = v;
			return this;
		}

		public ThemeColors.Builder accentDim(int v) {
			this.accentDim = v;
			return this;
		}

		public ThemeColors.Builder textPrimary(int v) {
			this.textPrimary = v;
			return this;
		}

		public ThemeColors.Builder textSecondary(int v) {
			this.textSecondary = v;
			return this;
		}

		public ThemeColors.Builder textMuted(int v) {
			this.textMuted = v;
			return this;
		}

		public ThemeColors.Builder textError(int v) {
			this.textError = v;
			return this;
		}

		public ThemeColors.Builder textSuccess(int v) {
			this.textSuccess = v;
			return this;
		}

		public ThemeColors.Builder headerText(int v) {
			this.headerText = v;
			return this;
		}

		public ThemeColors.Builder headerUnderline(int v) {
			this.headerUnderline = v;
			return this;
		}

		public ThemeColors.Builder sectionBackground(int v) {
			this.sectionBackground = v;
			return this;
		}

		public ThemeColors.Builder sectionHover(int v) {
			this.sectionHover = v;
			return this;
		}

		public ThemeColors.Builder buttonBackground(int v) {
			this.buttonBackground = v;
			return this;
		}

		public ThemeColors.Builder buttonBackgroundHover(int v) {
			this.buttonBackgroundHover = v;
			return this;
		}

		public ThemeColors.Builder buttonBorder(int v) {
			this.buttonBorder = v;
			return this;
		}

		public ThemeColors.Builder buttonText(int v) {
			this.buttonText = v;
			return this;
		}

		public ThemeColors.Builder scrollTrack(int v) {
			this.scrollTrack = v;
			return this;
		}

		public ThemeColors.Builder scrollThumb(int v) {
			this.scrollThumb = v;
			return this;
		}

		public ThemeColors.Builder scanlineColor(int v) {
			this.scanlineColor = v;
			return this;
		}

		public ThemeColors.Builder grainColor(int v) {
			this.grainColor = v;
			return this;
		}

		public ThemeColors.Builder glitchColor(int v) {
			this.glitchColor = v;
			return this;
		}

		public ThemeColors.Builder vignetteColor(int v) {
			this.vignetteColor = v;
			return this;
		}

		public ThemeColors build() {
			return new ThemeColors(this);
		}
	}
}
