package eu.amadeos.calendar.domain;

import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.chrono.IsoChronology;
import java.util.Locale;

/** The order of day, month and year in the Date Format options. */
public enum DateOrder {
    DAY_MONTH_YEAR, MONTH_DAY_YEAR, YEAR_MONTH_DAY;

    /** The order the locale's short date uses. */
    public static DateOrder of(Locale locale) {
        String pattern = DateTimeFormatterBuilderHolder.shortPattern(locale).toLowerCase(Locale.ROOT);
        int d = pattern.indexOf('d');
        int m = pattern.indexOf('m');
        int y = pattern.indexOf('y');
        if (y >= 0 && y < d && y < m) return YEAR_MONTH_DAY;
        if (m >= 0 && m < d) return MONTH_DAY_YEAR;
        return DAY_MONTH_YEAR;
    }

    /** The separator the locale's short date uses, '/' if it has none. */
    public static char separatorOf(Locale locale) {
        for (char c : DateTimeFormatterBuilderHolder.shortPattern(locale).toCharArray()) {
            if (!Character.isLetter(c) && !Character.isWhitespace(c) && c != '\'') return c;
        }
        return '/';
    }

    /** Pattern with two-digit day and month and a four-digit year, e.g. {@code dd.MM.yyyy}. */
    public String pattern(char separator) {
        String s = String.valueOf(separator);
        return switch (this) {
            case DAY_MONTH_YEAR -> "dd" + s + "MM" + s + "yyyy";
            case MONTH_DAY_YEAR -> "MM" + s + "dd" + s + "yyyy";
            case YEAR_MONTH_DAY -> "yyyy" + s + "MM" + s + "dd";
        };
    }

    private static final class DateTimeFormatterBuilderHolder {
        static String shortPattern(Locale locale) {
            return java.time.format.DateTimeFormatterBuilder.getLocalizedDateTimePattern(FormatStyle.SHORT, null,
                    IsoChronology.INSTANCE, locale);
        }
    }
}
