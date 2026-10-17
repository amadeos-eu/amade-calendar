package org.datazuul.amadeos.calendar.domain;

import java.time.Duration;
import java.time.LocalTime;
import java.time.chrono.IsoChronology;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.FormatStyle;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * The settings of the Calendar Options dialog: Editor Defaults, Display Settings and Date Format.
 *
 * @param defaultDurationMinutes length of a new appointment
 * @param defaultStart           start time of a new appointment
 * @param defaultReminders       reminders of a new appointment with their lead times
 * @param defaultMailTo          address of the Mail reminder
 * @param defaultPrivacy         privacy of a new appointment
 * @param dayBegin               first hour shown in Day and Week view (0-23)
 * @param dayEnd                 last hour shown in Day and Week view (1-24, after {@code dayBegin})
 * @param twentyFourHour         24-hour instead of 12-hour times
 * @param defaultView            view shown at start
 * @param dateOrder              order of day, month and year in dates
 * @param dateSeparator          character between them
 */
public record CalendarPreferences(int defaultDurationMinutes, LocalTime defaultStart,
                                  Map<ReminderType, Duration> defaultReminders, String defaultMailTo,
                                  Privacy defaultPrivacy, int dayBegin, int dayEnd, boolean twentyFourHour,
                                  CalendarView defaultView, DateOrder dateOrder, char dateSeparator) {

    public CalendarPreferences {
        defaultDurationMinutes = Math.max(0, defaultDurationMinutes);
        defaultStart = Objects.requireNonNullElse(defaultStart, LocalTime.of(9, 0));
        EnumMap<ReminderType, Duration> copy = new EnumMap<>(ReminderType.class);
        if (defaultReminders != null) defaultReminders.forEach((t, d) -> {
            if (t != null && d != null && !d.isNegative()) copy.put(t, d);
        });
        defaultReminders = java.util.Collections.unmodifiableMap(copy);
        defaultMailTo = defaultMailTo == null ? "" : defaultMailTo.strip();
        defaultPrivacy = Objects.requireNonNullElse(defaultPrivacy, Privacy.TIME_ONLY);
        dayBegin = Math.clamp(dayBegin, 0, 23);
        dayEnd = Math.clamp(dayEnd, dayBegin + 1, 24);
        defaultView = Objects.requireNonNullElse(defaultView, CalendarView.MONTH);
        dateOrder = Objects.requireNonNullElse(dateOrder, DateOrder.DAY_MONTH_YEAR);
        if (Character.isLetterOrDigit(dateSeparator) || Character.isWhitespace(dateSeparator)) dateSeparator = '/';
    }

    /** dtcm's defaults with the date and time conventions of the locale. */
    public static CalendarPreferences defaultsFor(Locale locale) {
        String timePattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern(null, FormatStyle.SHORT,
                IsoChronology.INSTANCE, locale);
        return new CalendarPreferences(60, LocalTime.of(9, 0), Map.of(), "", Privacy.TIME_ONLY, 8, 18,
                !timePattern.contains("a"), CalendarView.MONTH, DateOrder.of(locale), DateOrder.separatorOf(locale));
    }

    public CalendarPreferences withDefaultView(CalendarView view) {
        return new CalendarPreferences(defaultDurationMinutes, defaultStart, defaultReminders, defaultMailTo,
                defaultPrivacy, dayBegin, dayEnd, twentyFourHour, view, dateOrder, dateSeparator);
    }
}
