package eu.amadeos.calendar.domain;

import java.time.DayOfWeek;
import java.util.Set;

import static eu.amadeos.calendar.domain.Repeat.Frequency.*;

/**
 * The entries of the "Occurs" menu in the CDE Appointment Editor and the {@link Repeat} each stands for.
 * {@link #REPEAT_EVERY} is the custom entry: every n days, weeks or months.
 */
public enum RepeatPreset {
    ONE_TIME,
    DAILY,
    WEEKLY,
    EVERY_TWO_WEEKS,
    MONTHLY_BY_DATE,
    MONTHLY_BY_WEEKDAY,
    YEARLY,
    MONDAY_TO_FRIDAY,
    MON_WED_FRI,
    TUE_THU,
    REPEAT_EVERY;

    /** The units "Repeat Every" can be counted in. */
    public enum Unit { DAYS, WEEKS, MONTHS }

    /** The rule for this preset; for {@link #REPEAT_EVERY} the custom interval and unit are used. */
    public Repeat toRepeat(int count, int every, Unit unit) {
        return switch (this) {
            case ONE_TIME -> Repeat.ONCE;
            case DAILY -> Repeat.of(Repeat.Frequency.DAILY, 1, count);
            case WEEKLY -> Repeat.of(Repeat.Frequency.WEEKLY, 1, count);
            case EVERY_TWO_WEEKS -> Repeat.of(Repeat.Frequency.WEEKLY, 2, count);
            case MONTHLY_BY_DATE -> Repeat.of(MONTHLY, 1, count);
            case MONTHLY_BY_WEEKDAY -> new Repeat(MONTHLY, 1, Set.of(), true, count, null);
            case YEARLY -> Repeat.of(Repeat.Frequency.YEARLY, 1, count);
            case MONDAY_TO_FRIDAY -> Repeat.weekly(Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), count);
            case MON_WED_FRI -> Repeat.weekly(Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), count);
            case TUE_THU -> Repeat.weekly(Set.of(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY), count);
            case REPEAT_EVERY -> Repeat.of(switch (unit) {
                case DAYS -> Repeat.Frequency.DAILY;
                case WEEKS -> Repeat.Frequency.WEEKLY;
                case MONTHS -> MONTHLY;
            }, Math.max(1, every), count);
        };
    }

    /** The preset a rule shows as in the menu; rules the menu has no entry for show as "Repeat Every". */
    public static RepeatPreset of(Repeat r) {
        if (!r.repeats()) return ONE_TIME;
        if (r.until() != null) return REPEAT_EVERY;
        switch (r.frequency()) {
            case DAILY:
                return r.interval() == 1 ? DAILY : REPEAT_EVERY;
            case WEEKLY:
                if (!r.byDay().isEmpty()) {
                    if (r.interval() != 1) return REPEAT_EVERY;
                    if (r.byDay().equals(Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY))) return MONDAY_TO_FRIDAY;
                    if (r.byDay().equals(Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))) return MON_WED_FRI;
                    if (r.byDay().equals(Set.of(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY))) return TUE_THU;
                    return REPEAT_EVERY;
                }
                return r.interval() == 1 ? WEEKLY : r.interval() == 2 ? EVERY_TWO_WEEKS : REPEAT_EVERY;
            case MONTHLY:
                if (r.interval() != 1) return REPEAT_EVERY;
                return r.byWeekdayOfMonth() ? MONTHLY_BY_WEEKDAY : MONTHLY_BY_DATE;
            case YEARLY:
                return r.interval() == 1 ? YEARLY : REPEAT_EVERY;
            default:
                return ONE_TIME;
        }
    }

    /** The unit of a custom rule, for the "Repeat Every" fields. */
    public static Unit unitOf(Repeat r) {
        return switch (r.frequency()) {
            case WEEKLY -> Unit.WEEKS;
            case MONTHLY, YEARLY -> Unit.MONTHS;
            default -> Unit.DAYS;
        };
    }
}
