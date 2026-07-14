package dev.recordable;

public final class CensorRegion {
	public double x = 0.0;
	public double y = 0.0;
	public double width = 0.25;
	public double height = 0.1;
	public CensorRegion.Style style = CensorRegion.Style.SOLID;
	public int color = 0;
	public int colorEnd = 4473924;
	public CensorRegion.GradientDirection gradientDirection = CensorRegion.GradientDirection.HORIZONTAL;
	public String label = "Censor";
	public boolean showLabel = false;
	public int textColor = 16777215;
	public boolean enabled = true;

	public CensorRegion() {
	}

	public CensorRegion(double x, double y, double width, double height, CensorRegion.Style style, String label) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		this.style = style;
		this.label = label;
		this.enabled = true;
	}

	public CensorRegion copy() {
		CensorRegion c = new CensorRegion(this.x, this.y, this.width, this.height, this.style, this.label);
		c.color = this.color;
		c.colorEnd = this.colorEnd;
		c.gradientDirection = this.gradientDirection;
		c.showLabel = this.showLabel;
		c.textColor = this.textColor;
		c.enabled = this.enabled;
		return c;
	}

	public void sanitize() {
		this.x = clamp01(this.x);
		this.y = clamp01(this.y);
		this.width = clamp01(this.width);
		this.height = clamp01(this.height);
		if (this.x + this.width > 1.0) {
			this.width = 1.0 - this.x;
		}

		if (this.y + this.height > 1.0) {
			this.height = 1.0 - this.y;
		}

		if (this.width <= 0.0) {
			this.width = 0.02;
		}

		if (this.height <= 0.0) {
			this.height = 0.02;
		}

		if (this.style == null) {
			this.style = CensorRegion.Style.SOLID;
		}

		if (this.gradientDirection == null) {
			this.gradientDirection = CensorRegion.GradientDirection.HORIZONTAL;
		}

		if (this.label == null || this.label.isBlank()) {
			this.label = "Censor";
		}

		this.color &= 16777215;
		this.colorEnd &= 16777215;
		this.textColor &= 16777215;
	}

	private static double clamp01(double v) {
		return !Double.isNaN(v) && !(v < 0.0) ? Math.min(v, 1.0) : 0.0;
	}

	public static enum GradientDirection {
		HORIZONTAL,
		VERTICAL,
		DIAGONAL;
	}

	public static enum Style {
		SOLID,
		GRADIENT;
	}
}
