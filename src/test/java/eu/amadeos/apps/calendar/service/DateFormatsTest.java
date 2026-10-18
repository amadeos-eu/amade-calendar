package eu.amadeos.apps.calendar.service;

import eu.amadeos.apps.calendar.domain.DateOrder;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DateFormatsTest {
    private final DateFormats german = new DateFormats(DateOrder.DAY_MONTH_YEAR, '.', true, Locale.GERMANY);
    private final DateFormats us = new DateFormats(DateOrder.MONTH_DAY_YEAR, '/', false, Locale.US);
    private final DateFormats iso = new DateFormats(DateOrder.YEAR_MONTH_DAY, '-', true, Locale.ENGLISH);

    @Test
    void datesFollowOrderAndSeparator() {
        LocalDate d = LocalDate.of(2026, 10, 3);
        assertEquals("03.10.2026", german.date(d));
        assertEquals("10/03/2026", us.date(d));
        assertEquals("2026-10-03", iso.date(d));
        assertEquals(Optional.of(d), german.parseDate("3.10.2026"));
        assertEquals(Optional.of(d), german.parseDate("03/10/26"));
        assertEquals(Optional.of(d), us.parseDate("10/3/2026"));
        assertEquals(Optional.of(d), iso.parseDate("2026-10-3"));
        assertEquals(Optional.of(d), us.parseDateLenient("2026-10-03"));
        assertEquals(Optional.empty(), german.parseDate("31.02.2026"));
        assertEquals(Optional.empty(), german.parseDate("October"));
    }

    @Test
    void timesIn12And24Hours() {
        assertEquals("14:05", german.time(LocalTime.of(14, 5)));
        assertEquals("2:05 PM", us.time(LocalTime.of(14, 5)));
        assertEquals("12:00 AM", us.time(LocalTime.MIDNIGHT));
        assertEquals("9 AM", us.hourLabel(9));
        assertEquals("12 PM", us.hourLabel(12));
        assertEquals("09:00", german.hourLabel(9));
        assertEquals("9:30 AM - 10:00 AM", us.timeRange(LocalTime.of(9, 30), LocalTime.of(10, 0)));
        assertEquals("09:30", german.timeRange(LocalTime.of(9, 30), null));
        assertEquals("", german.timeRange(null, null));
    }

    @Test
    void lenientTimeParsing() {
        assertEquals(Optional.of(LocalTime.of(9, 30)), us.parseTime("9:30"));
        assertEquals(Optional.of(LocalTime.of(21, 30)), us.parseTime("9:30 pm"));
        assertEquals(Optional.of(LocalTime.of(21, 30)), us.parseTime("9.30PM"));
        assertEquals(Optional.of(LocalTime.of(21, 0)), us.parseTime("9pm"));
        assertEquals(Optional.of(LocalTime.of(0, 15)), us.parseTime("12:15 am"));
        assertEquals(Optional.of(LocalTime.of(12, 15)), us.parseTime("12:15 p.m."));
        assertEquals(Optional.of(LocalTime.of(14, 0)), german.parseTime("14"));
        assertEquals(Optional.of(LocalTime.of(14, 0)), german.parseTime("14h00"));
        assertEquals(Optional.of(LocalTime.MIDNIGHT), german.parseTime("24:00"));
        assertEquals(Optional.empty(), german.parseTime("25:00"));
        assertEquals(Optional.empty(), german.parseTime("13 pm"));
        assertEquals(Optional.empty(), german.parseTime("soon"));
        assertEquals(Optional.empty(), german.parseTime(""));
    }
}
