package org.datazuul.amadeos.calendar.domain;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RepeatTest {
    private static final LocalDate FIRST = LocalDate.of(2026, 10, 13); // a Tuesday

    @Test
    void oneTimeOccursOnlyOnItsDay() {
        assertEquals(List.of(FIRST), Repeat.ONCE.occurrences(FIRST, FIRST.minusDays(3), FIRST.plusDays(3)));
        assertTrue(Repeat.ONCE.occurrences(FIRST, FIRST.plusDays(1), FIRST.plusDays(9)).isEmpty());
        assertFalse(Repeat.ONCE.repeats());
    }

    @Test
    void dailyWithCountStopsAfterCountOccurrences() {
        Repeat daily = RepeatPreset.DAILY.toRepeat(3, 1, RepeatPreset.Unit.DAYS);
        assertEquals(List.of(FIRST, FIRST.plusDays(1), FIRST.plusDays(2)),
                daily.occurrences(FIRST, FIRST.minusYears(1), FIRST.plusYears(1)));
        assertEquals(List.of(FIRST.plusDays(2)), daily.occurrences(FIRST, FIRST.plusDays(2), FIRST.plusDays(30)));
    }

    @Test
    void everyTwoWeeksAndRepeatEvery() {
        Repeat r = RepeatPreset.EVERY_TWO_WEEKS.toRepeat(0, 1, RepeatPreset.Unit.DAYS);
        assertEquals(List.of(FIRST, FIRST.plusWeeks(2), FIRST.plusWeeks(4)),
                r.occurrences(FIRST, FIRST, FIRST.plusWeeks(5)));
        Repeat everyThreeDays = RepeatPreset.REPEAT_EVERY.toRepeat(0, 3, RepeatPreset.Unit.DAYS);
        assertEquals(List.of(FIRST.plusDays(3), FIRST.plusDays(6)),
                everyThreeDays.occurrences(FIRST, FIRST.plusDays(1), FIRST.plusDays(7)));
        assertEquals(RepeatPreset.REPEAT_EVERY, RepeatPreset.of(everyThreeDays));
        assertEquals(RepeatPreset.EVERY_TWO_WEEKS, RepeatPreset.of(r));
    }

    @Test
    void weekdaysOnlyFallOnMondayToFriday() {
        Repeat r = RepeatPreset.MONDAY_TO_FRIDAY.toRepeat(0, 1, RepeatPreset.Unit.DAYS);
        List<LocalDate> dates = r.occurrences(FIRST, FIRST, FIRST.plusDays(13));
        assertEquals(10, dates.size());
        assertEquals(FIRST, dates.getFirst());
        for (LocalDate d : dates) assertNotEquals(DayOfWeek.SATURDAY, d.getDayOfWeek());
        assertEquals(RepeatPreset.MONDAY_TO_FRIDAY, RepeatPreset.of(r));
        Repeat tueThu = RepeatPreset.TUE_THU.toRepeat(3, 1, RepeatPreset.Unit.DAYS);
        assertEquals(List.of(FIRST, FIRST.plusDays(2), FIRST.plusDays(7)),
                tueThu.occurrences(FIRST, FIRST.minusDays(10), FIRST.plusDays(60)));
    }

    @Test
    void monthlyByDateSkipsMonthsWithoutTheDay() {
        LocalDate jan31 = LocalDate.of(2026, 1, 31);
        Repeat r = RepeatPreset.MONTHLY_BY_DATE.toRepeat(0, 1, RepeatPreset.Unit.DAYS);
        assertEquals(List.of(jan31, LocalDate.of(2026, 3, 31), LocalDate.of(2026, 5, 31)),
                r.occurrences(jan31, jan31, LocalDate.of(2026, 6, 15)));
    }

    @Test
    void monthlyByWeekdayKeepsTheSecondTuesday() {
        Repeat r = RepeatPreset.MONTHLY_BY_WEEKDAY.toRepeat(0, 1, RepeatPreset.Unit.DAYS);
        assertEquals(List.of(FIRST, LocalDate.of(2026, 11, 10), LocalDate.of(2026, 12, 8)),
                r.occurrences(FIRST, FIRST, LocalDate.of(2026, 12, 31)));
        assertEquals(RepeatPreset.MONTHLY_BY_WEEKDAY, RepeatPreset.of(r));
    }

    @Test
    void yearlyOnLeapDayOnlyInLeapYears() {
        LocalDate leap = LocalDate.of(2024, 2, 29);
        Repeat r = RepeatPreset.YEARLY.toRepeat(0, 1, RepeatPreset.Unit.DAYS);
        assertEquals(List.of(leap, LocalDate.of(2028, 2, 29)), r.occurrences(leap, leap, LocalDate.of(2029, 1, 1)));
    }

    @Test
    void untilLimitsTheRule() {
        Repeat r = Repeat.of(Repeat.Frequency.DAILY, 1, 0).withUntil(FIRST.plusDays(2));
        assertEquals(3, r.occurrences(FIRST, FIRST, FIRST.plusDays(30)).size());
        assertTrue(r.occursOn(FIRST, FIRST.plusDays(2)));
        assertFalse(r.occursOn(FIRST, FIRST.plusDays(3)));
        assertEquals(RepeatPreset.REPEAT_EVERY, RepeatPreset.of(r));
    }

    @Test
    void weeklyWithDaysBeforeTheFirstDateAreNotProduced() {
        Repeat r = Repeat.weekly(Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), 2);
        // Tuesday 13 Oct is the first; the Monday before it does not count.
        assertEquals(List.of(FIRST, FIRST.plusDays(6)), r.occurrences(FIRST, FIRST.minusDays(7), FIRST.plusDays(30)));
    }
}
