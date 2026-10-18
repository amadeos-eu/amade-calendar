package eu.amadeos.apps.calendar.domain;

/**
 * What other people may see of an appointment, as in the Privacy menu of the CDE Appointment Editor.
 * Stored as the iCalendar {@code CLASS}.
 */
public enum Privacy {
    /** Others See Time and Text: {@code CLASS:PUBLIC}. */
    TIME_AND_TEXT("PUBLIC"),
    /** Others See Time Only, the CDE default: {@code CLASS:CONFIDENTIAL}. */
    TIME_ONLY("CONFIDENTIAL"),
    /** Others See Nothing: {@code CLASS:PRIVATE}. */
    NOTHING("PRIVATE");

    private final String icsClass;

    Privacy(String icsClass) {
        this.icsClass = icsClass;
    }

    public String icsClass() {
        return icsClass;
    }

    public static Privacy fromIcsClass(String value) {
        for (Privacy p : values()) if (p.icsClass.equalsIgnoreCase(value)) return p;
        return TIME_AND_TEXT;
    }
}
