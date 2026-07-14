package dev.recordable.filter;

public enum FilterType {
	NONE("None"),
	VHS("VHS"),
	LCD_MOIRE("LCD Moire"),
	CRT("CRT");

	public final String displayName;

	private FilterType(String displayName) {
		this.displayName = displayName;
	}

	public FilterType next() {
		FilterType[] values = values();
		return values[(this.ordinal() + 1) % values.length];
	}

	public FilterType previous() {
		FilterType[] values = values();
		return values[(this.ordinal() - 1 + values.length) % values.length];
	}

	public static FilterType fromName(String name) {
		if (name == null) {
			return NONE;
		} else {
			for (FilterType type : values()) {
				if (type.name().equalsIgnoreCase(name)) {
					return type;
				}
			}

			return NONE;
		}
	}
}
