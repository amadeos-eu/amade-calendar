package eu.amadeos.calendar.domain;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.time.temporal.WeekFields;

/** The four views of the main window. */
public enum CalendarView {
    DAY, WEEK, MONTH, YEAR;

    /** The first day of the period that contains {@code date} in this view. */
    public LocalDate periodStart(LocalDate date, Locale locale) {
        return switch (this) {
            case DAY -> date;
            case WEEK -> date.with(TemporalAdjusters.previousOrSame(WeekFields.of(locale).getFirstDayOfWeek()));
            case MONTH -> date.withDayOfMonth(1);
            case YEAR -> date.withDayOfYear(1);
        };
    }

    /** The last day of the period that contains {@code date} in this view. */
    public LocalDate periodEnd(LocalDate date, Locale locale) {
        return switch (this) {
            case DAY -> date;
            case WEEK -> periodStart(date, locale).plusDays(6);
            case MONTH -> date.with(TemporalAdjusters.lastDayOfMonth());
            case YEAR -> date.with(TemporalAdjusters.lastDayOfYear());
        };
    }

    /** The same point {@code steps} periods later (negative: earlier). */
    public LocalDate step(LocalDate date, int steps) {
        return switch (this) {
            case DAY -> date.plusDays(steps);
            case WEEK -> date.plusWeeks(steps);
            case MONTH -> date.plusMonths(steps);
            case YEAR -> date.plusYears(steps);
        };
    }
}
