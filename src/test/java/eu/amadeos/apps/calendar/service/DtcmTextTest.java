package eu.amadeos.apps.calendar.service;

import eu.amadeos.apps.calendar.domain.Appointment;
import eu.amadeos.apps.calendar.domain.DateOrder;
import eu.amadeos.apps.calendar.domain.Privacy;
import eu.amadeos.apps.calendar.domain.Repeat;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class DtcmTextTest {
    private final DateFormats us = new DateFormats(DateOrder.MONTH_DAY_YEAR, '/', false, Locale.US);
    private final DateFormats german = new DateFormats(DateOrder.DAY_MONTH_YEAR, '.', true, Locale.GERMANY);

    @Test
    void formatsAsInTheCdeGuide() {
        Appointment a = new Appointment("x", LocalDate.of(1994, 11, 22), LocalTime.of(10, 0), LocalTime.of(11, 0),
                "Staff meeting\nMonet conference room", List.of(), Repeat.ONCE, Privacy.TIME_AND_TEXT);
        assertEquals("""
                \t** Calendar Appointment **

                \tDate:\t11/22/1994
                \tStart:\t10:00 AM
                \tEnd:\t11:00 AM
                \tWhat:\tStaff meeting
                \t\tMonet conference room
                """, DtcmText.format(a, us));
        assertTrue(DtcmText.contains(DtcmText.format(a, us)));
        assertFalse(DtcmText.contains("Dear all, see you at 10."));
    }

    @Test
    void parsesTheGuideExampleAndTwoDigitYears() {
        String text = """
                Hi,

                \t** Calendar Appointment **

                \tDate:\t11/22/94
                \tStart:\t10:00 am
                \tEnd:\t11:00 am
                \tWhat:\tStaff meeting
                \t\tMonet conference room

                See you there.
                """;
        List<Appointment> parsed = DtcmText.parse(text, us);
        assertEquals(1, parsed.size());
        Appointment a = parsed.getFirst();
        assertEquals(LocalDate.of(1994, 11, 22), a.date());
        assertEquals(LocalTime.of(10, 0), a.start());
        assertEquals(LocalTime.of(11, 0), a.end());
        assertEquals("Staff meeting\nMonet conference room", a.what());
        assertFalse(a.repeat().repeats());
    }

    @Test
    void readsTheUsersFormatAndFallsBackToCdeOrder() {
        List<Appointment> own = DtcmText.parse("** Calendar Appointment **\nDate: 22.11.2026\nStart: 14:30\nWhat: Zahnarzt\n", german);
        assertEquals(LocalDate.of(2026, 11, 22), own.getFirst().date());
        assertEquals(LocalTime.of(14, 30), own.getFirst().start());
        assertNull(own.getFirst().end());
        // A German user receives a US text: 11/22 can only be month/day.
        List<Appointment> foreign = DtcmText.parse("** Calendar Appointment **\nDate: 11/22/2026\nStart: 2:30 pm\nEnd: 3 pm\nWhat: Call\n", german);
        assertEquals(LocalDate.of(2026, 11, 22), foreign.getFirst().date());
        assertEquals(LocalTime.of(15, 0), foreign.getFirst().end());
        List<Appointment> iso = DtcmText.parse("** Calendar Appointment **\nDate: 2026-11-22\nWhat: Call\n", us);
        assertEquals(LocalDate.of(2026, 11, 22), iso.getFirst().date());
        assertNull(iso.getFirst().start());
    }

    @Test
    void severalBlocksAndBrokenOnes() {
        String text = "** Calendar Appointment **\nDate: 1/2/2026\nWhat: One\n\n** Calendar Appointment **\nDate: soon\nWhat: Broken\n\n"
                + "** Calendar Appointment **\nDate: 1/3/2026\nStart: 9\nWhat: Three\n";
        List<Appointment> parsed = DtcmText.parse(text, us);
        assertEquals(List.of("One", "Three"), parsed.stream().map(Appointment::summary).toList());
        assertEquals(LocalTime.of(9, 0), parsed.get(1).start());
        assertTrue(DtcmText.parse("nothing here", us).isEmpty());
        assertTrue(DtcmText.parse(null, us).isEmpty());
    }

    @Test
    void mailtoCarriesSubjectAndBody() {
        Appointment a = new Appointment("x", LocalDate.of(2026, 10, 13), LocalTime.of(9, 0), null, "Call Kent", List.of(),
                Repeat.ONCE, Privacy.TIME_AND_TEXT);
        String uri = DtcmText.mailto("kent@dailyplanet", "Reminder: Call Kent", a, us);
        assertTrue(uri.startsWith("mailto:kent%40dailyplanet?subject=Reminder%3A%20Call%20Kent&body=%09"), uri);
        assertTrue(uri.contains("Calendar%20Appointment"));
        assertFalse(uri.contains("+"));
    }
}
