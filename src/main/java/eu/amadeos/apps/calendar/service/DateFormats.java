package eu.amadeos.apps.calendar.service;

import eu.amadeos.apps.calendar.domain.CalendarPreferences;
import eu.amadeos.apps.calendar.domain.DateOrder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dates and times as the Date Format and Display Settings options want them: a fixed order and
 * separator for dates, 12- or 24-hour times. Parsing is lenient: one or two digits, a two-digit
 * year, {@code 9}, {@code 9:30}, {@code 9.30pm}, {@code 21:30} are all accepted.
 */
public final class DateFormats {
    private static final Pattern DATE_PARTS = Pattern.compile("\\s*(\\d{1,4})\\s*[^\\d\\s]\\s*(\\d{1,2})\\s*[^\\d\\s]\\s*(\\d{1,4})\\s*");
    private static final Pattern TIME_PARTS = Pattern.compile("\\s*(\\d{1,2})(?:[:.hH]\\s*(\\d{2}))?\\s*([aApP])?\\.?\\s*[mM]?\\.?\\s*");

    private final DateOrder order;
    private final char separator;
    private final boolean twentyFourHour;
    private final Locale locale;
    private final DateTimeFormatter date;
    private final DateTimeFormatter time;

    public DateFormats(CalendarPreferences prefs, Locale locale) {
        this(prefs.dateOrder(), prefs.dateSeparator(), prefs.twentyFourHour(), locale);
    }

    public DateFormats(DateOrder order, char separator, boolean twentyFourHour, Locale locale) {
        this.order = order;
        this.separator = separator;
        this.twentyFourHour = twentyFourHour;
        this.locale = locale;
        this.date = DateTimeFormatter.ofPattern(order.pattern(separator), locale);
        this.time = DateTimeFormatter.ofPattern(twentyFourHour ? "HH:mm" : "h:mm a", Locale.ENGLISH);
    }

    public Locale locale() {
        return locale;
    }

    public boolean twentyFourHour() {
        return twentyFourHour;
    }

    /** {@code 17/10/2026}, {@code 10/17/2026} or {@code 2026-10-17}. */
    public String date(LocalDate d) {
        return date.format(d);
    }

    /** {@code 9:30 AM} or {@code 09:30}. */
    public String time(LocalTime t) {
        return time.format(t);
    }

    /** {@code 9:30 AM - 10:30 AM}; only the start if there is no end. */
    public String timeRange(LocalTime start, LocalTime end) {
        if (start == null) return "";
        return end == null ? time(start) : time(start) + " - " + time(end);
    }

    /** The hour as the day grid labels it: {@code 9 AM}, {@code 12 PM} or {@code 09:00}. */
    public String hourLabel(int hour) {
        if (twentyFourHour) return String.format("%02d:00", hour % 24);
        int h = hour % 12 == 0 ? 12 : hour % 12;
        return h + (hour % 24 < 12 ? " AM" : " PM");
    }

    /** {@code Tuesday, 13 October 2026} in the locale. */
    public String longDate(LocalDate d) {
        return DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", locale).format(d);
    }

    /** {@code October 2026}. */
    public String monthAndYear(LocalDate d) {
        return d.getMonth().getDisplayName(TextStyle.FULL_STANDALONE, locale) + " " + d.getYear();
    }

    public String monthName(java.time.Month month) {
        return month.getDisplayName(TextStyle.FULL_STANDALONE, locale);
    }

    public String dayName(java.time.DayOfWeek day, TextStyle style) {
        return day.getDisplayName(style, locale);
    }

    public Optional<LocalDate> parseDate(String text) {
        if (text == null) return Optional.empty();
        Matcher m = DATE_PARTS.matcher(text);
        if (!m.matches()) return Optional.empty();
        int a = Integer.parseInt(m.group(1));
        int b = Integer.parseInt(m.group(2));
        int c = Integer.parseInt(m.group(3));
        int day;
        int month;
        int year;
        switch (order) {
            case DAY_MONTH_YEAR -> { day = a; month = b; year = c; }
            case MONTH_DAY_YEAR -> { month = a; day = b; year = c; }
            default -> { year = a; month = b; day = c; }
        }
        if (year < 100) year += year < 70 ? 2000 : 1900;
        try {
            return Optional.of(LocalDate.of(year, month, day));
        } catch (java.time.DateTimeException e) {
            return Optional.empty();
        }
    }

    public Optional<LocalTime> parseTime(String text) {
        if (text == null) return Optional.empty();
        Matcher m = TIME_PARTS.matcher(text);
        if (!m.matches()) return Optional.empty();
        int hour = Integer.parseInt(m.group(1));
        int minute = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
        String ampm = m.group(3);
        if (ampm != null) {
            if (hour > 12 || hour == 0) return Optional.empty();
            boolean pm = Character.toLowerCase(ampm.charAt(0)) == 'p';
            hour = hour % 12 + (pm ? 12 : 0);
        } else if (hour == 24 && minute == 0) {
            hour = 0;
        }
        try {
            return Optional.of(LocalTime.of(hour, minute));
        } catch (java.time.DateTimeException e) {
            return Optional.empty();
        }
    }

    /** Parse with the locale's own formats as a fallback, e.g. a pasted "2026-10-17". */
    public Optional<LocalDate> parseDateLenient(String text) {
        Optional<LocalDate> own = parseDate(text);
        if (own.isPresent()) return own;
        try {
            return Optional.of(LocalDate.parse(text.strip()));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }
}
