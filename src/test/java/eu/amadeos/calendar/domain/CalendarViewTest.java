package eu.amadeos.calendar.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class CalendarViewTest {
    private static final LocalDate WED = LocalDate.of(2026, 10, 14);

    @Test
    void weekStartsOnMondayInGermanyAndSundayInTheUs() {
        assertEquals(LocalDate.of(2026, 10, 12), CalendarView.WEEK.periodStart(WED, Locale.GERMANY));
        assertEquals(LocalDate.of(2026, 10, 18), CalendarView.WEEK.periodEnd(WED, Locale.GERMANY));
        assertEquals(LocalDate.of(2026, 10, 11), CalendarView.WEEK.periodStart(WED, Locale.US));
    }

    @Test
    void periodsAndSteps() {
        assertEquals(LocalDate.of(2026, 10, 1), CalendarView.MONTH.periodStart(WED, Locale.US));
        assertEquals(LocalDate.of(2026, 10, 31), CalendarView.MONTH.periodEnd(WED, Locale.US));
        assertEquals(LocalDate.of(2026, 12, 31), CalendarView.YEAR.periodEnd(WED, Locale.US));
        assertEquals(WED, CalendarView.DAY.periodEnd(WED, Locale.US));
        assertEquals(WED.plusMonths(1), CalendarView.MONTH.step(WED, 1));
        assertEquals(WED.minusYears(1), CalendarView.YEAR.step(WED, -1));
        assertEquals(WED.plusWeeks(1), CalendarView.WEEK.step(WED, 1));
    }

    @Test
    void preferencesTakeTheLocaleConventions() {
        CalendarPreferences de = CalendarPreferences.defaultsFor(Locale.GERMANY);
        assertEquals(DateOrder.DAY_MONTH_YEAR, de.dateOrder());
        assertEquals('.', de.dateSeparator());
        assertTrue(de.twentyFourHour());
        CalendarPreferences us = CalendarPreferences.defaultsFor(Locale.US);
        assertEquals(DateOrder.MONTH_DAY_YEAR, us.dateOrder());
        assertEquals('/', us.dateSeparator());
        assertFalse(us.twentyFourHour());
        assertEquals(Privacy.TIME_ONLY, us.defaultPrivacy());
        assertEquals(CalendarView.MONTH, us.defaultView());
    }

    @Test
    void preferencesKeepTheHoursConsistent() {
        CalendarPreferences p = new CalendarPreferences(60, null, null, null, null, 20, 10, true, null, null, ' ');
        assertEquals(20, p.dayBegin());
        assertEquals(21, p.dayEnd());
        assertEquals('/', p.dateSeparator());
        assertEquals(DateOrder.DAY_MONTH_YEAR, p.dateOrder());
    }
}
