package eu.amadeos.apps.calendar.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * How an appointment repeats: the subset of the iCalendar {@code RRULE} the CDE "Occurs" and "For"
 * menus can express (see {@link RepeatPreset}).
 *
 * @param frequency        {@link Frequency#NONE} for a one-time appointment
 * @param interval         every n days, weeks, months or years (1 = every one)
 * @param byDay            weekly: the weekdays; empty means the weekday of the first occurrence
 * @param byWeekdayOfMonth monthly: the same weekday in the same week of the month (second Tuesday)
 *                         instead of the same day of the month
 * @param count            number of occurrences, 0 = forever (or until {@code until})
 * @param until            last possible date, or null
 */
public record Repeat(Frequency frequency, int interval, Set<DayOfWeek> byDay, boolean byWeekdayOfMonth, int count,
                     LocalDate until) {

    public enum Frequency { NONE, DAILY, WEEKLY, MONTHLY, YEARLY }

    public static final Repeat ONCE = new Repeat(Frequency.NONE, 1, Set.of(), false, 0, null);

    /** Safety limit so that a rule never expands into an endless loop. */
    private static final int MAX_STEPS = 100_000;

    public Repeat {
        Objects.requireNonNull(frequency, "frequency");
        interval = Math.max(1, interval);
        byDay = byDay == null || byDay.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(byDay));
        count = Math.max(0, count);
    }

    public static Repeat of(Frequency frequency, int interval, int count) {
        return new Repeat(frequency, interval, Set.of(), false, count, null);
    }

    public static Repeat weekly(Set<DayOfWeek> days, int count) {
        return new Repeat(Frequency.WEEKLY, 1, days, false, count, null);
    }

    public boolean repeats() {
        return frequency != Frequency.NONE;
    }

    public Repeat withCount(int newCount) {
        return new Repeat(frequency, interval, byDay, byWeekdayOfMonth, newCount, until);
    }

    public Repeat withUntil(LocalDate newUntil) {
        return new Repeat(frequency, interval, byDay, byWeekdayOfMonth, count, newUntil);
    }

    /**
     * The dates on which an appointment first held on {@code first} takes place within {@code from}
     * to {@code to} (both inclusive), in order.
     */
    public List<LocalDate> occurrences(LocalDate first, LocalDate from, LocalDate to) {
        List<LocalDate> result = new ArrayList<>();
        if (to.isBefore(from)) return result;
        if (frequency == Frequency.NONE) {
            if (!first.isBefore(from) && !first.isAfter(to)) result.add(first);
            return result;
        }
        LocalDate lastDate = until != null && until.isBefore(to) ? until : to;
        int produced = 0;
        int steps = 0;
        if (frequency == Frequency.WEEKLY && !byDay.isEmpty()) {
            // Walk the weeks (interval weeks apart), the chosen weekdays inside each week.
            DayOfWeek weekStart = DayOfWeek.MONDAY;
            LocalDate week = first.with(java.time.temporal.TemporalAdjusters.previousOrSame(weekStart));
            while (!week.isAfter(lastDate) && steps++ < MAX_STEPS) {
                for (int d = 0; d < 7; d++) {
                    LocalDate date = week.plusDays(d);
                    if (!byDay.contains(date.getDayOfWeek()) || date.isBefore(first)) continue;
                    if (date.isAfter(lastDate)) return result;
                    produced++;
                    if (!date.isBefore(from)) result.add(date);
                    if (count > 0 && produced >= count) return result;
                }
                week = week.plusWeeks(interval);
            }
            return result;
        }
        for (int n = 0; steps++ < MAX_STEPS; n++) {
            LocalDate date = nth(first, n);
            if (date == null) {
                // A month or year without this day (31st, 29 February): skipped, as dtcm did.
                if (nthBase(first, n).isAfter(lastDate)) return result;
                continue;
            }
            if (date.isAfter(lastDate)) return result;
            produced++;
            if (!date.isBefore(from)) result.add(date);
            if (count > 0 && produced >= count) return result;
        }
        return result;
    }

    /** The first day of the period the nth repetition falls into (for the stop test of skipped dates). */
    private LocalDate nthBase(LocalDate first, int n) {
        return switch (frequency) {
            case DAILY -> first.plusDays((long) n * interval);
            case WEEKLY -> first.plusWeeks((long) n * interval);
            case MONTHLY -> first.withDayOfMonth(1).plusMonths((long) n * interval);
            case YEARLY -> first.withDayOfYear(1).plusYears((long) n * interval);
            case NONE -> first;
        };
    }

    /** The nth repetition (0 = the first date itself), null if that period has no such day. */
    private LocalDate nth(LocalDate first, int n) {
        switch (frequency) {
            case DAILY:
                return first.plusDays((long) n * interval);
            case WEEKLY:
                return first.plusWeeks((long) n * interval);
            case MONTHLY: {
                LocalDate month = first.withDayOfMonth(1).plusMonths((long) n * interval);
                if (byWeekdayOfMonth) {
                    int ordinal = (first.getDayOfMonth() - 1) / 7 + 1;
                    LocalDate date = month.with(java.time.temporal.TemporalAdjusters
                            .dayOfWeekInMonth(ordinal, first.getDayOfWeek()));
                    return date.getMonth() == month.getMonth() ? date : null;
                }
                return first.getDayOfMonth() <= month.lengthOfMonth() ? month.withDayOfMonth(first.getDayOfMonth()) : null;
            }
            case YEARLY: {
                LocalDate year = first.withDayOfYear(1).plusYears((long) n * interval);
                if (first.getMonthValue() == 2 && first.getDayOfMonth() == 29 && !year.isLeapYear()) return null;
                return year.withMonth(first.getMonthValue()).withDayOfMonth(first.getDayOfMonth());
            }
            default:
                return first;
        }
    }

    /** True if an appointment first held on {@code first} takes place on {@code date}. */
    public boolean occursOn(LocalDate first, LocalDate date) {
        return !occurrences(first, date, date).isEmpty();
    }

    /** Days between the first occurrence and the last one, Long.MAX_VALUE if there is none. */
    public long spanDays(LocalDate first) {
        if (!repeats()) return 0;
        if (until != null) return ChronoUnit.DAYS.between(first, until);
        if (count == 0) return Long.MAX_VALUE;
        List<LocalDate> all = occurrences(first, first, LocalDate.MAX.minusYears(1));
        return all.isEmpty() ? 0 : ChronoUnit.DAYS.between(first, all.getLast());
    }
}
