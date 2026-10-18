package org.datazuul.amadeos.calendar.service;

import org.datazuul.amadeos.calendar.domain.Appointment;
import org.datazuul.amadeos.calendar.domain.DateOrder;
import org.datazuul.amadeos.calendar.domain.Privacy;
import org.datazuul.amadeos.calendar.domain.Repeat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class CalendarImporterTest {
    private final CalendarService calendar = new CalendarService();
    private final CalendarImporter importer = new CalendarImporter(calendar);
    private final DateFormats us = new DateFormats(DateOrder.MONTH_DAY_YEAR, '/', false, Locale.US);

    private static final String ICS = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:shared
            DTSTART:20261020T090000
            DTEND:20261020T100000
            SUMMARY:Dentist
            END:VEVENT
            BEGIN:VTODO
            UID:todo1
            DUE;VALUE=DATE:20261021
            SUMMARY:Pay the bill
            END:VTODO
            END:VCALENDAR
            """;

    @Test
    void icalendarIsImportedAndClashingUidsBecomeCopies() {
        calendar.put(new Appointment("shared", LocalDate.of(2026, 1, 1), null, null, "Mine", List.of(), Repeat.ONCE,
                Privacy.TIME_AND_TEXT));
        CalendarImporter.Result result = importer.importText(ICS, us);
        assertEquals(1, result.appointments().size());
        assertEquals(1, result.toDos().size());
        assertNotEquals("shared", result.appointments().getFirst().uid());
        assertEquals("Mine", calendar.appointment("shared").orElseThrow().what(), "the user's entry is untouched");
        assertEquals(2, calendar.appointmentCount());
        assertEquals(LocalDate.of(2026, 10, 20), result.firstDate());
        // Importing again: the To Do UID now clashes too.
        CalendarImporter.Result again = importer.importText(ICS, us);
        assertNotEquals("todo1", again.toDos().getFirst().uid());
        assertEquals(2, calendar.toDoCount());
    }

    @Test
    void dtcmTextAndFiles(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("appointment.txt");
        Files.writeString(file, "\t** Calendar Appointment **\n\n\tDate:\t10/17/2026\n\tStart:\t9:00 am\n\tWhat:\tCall\n");
        CalendarImporter.Result result = importer.importFile(file, us);
        assertEquals("Call", result.appointments().getFirst().summary());
        assertTrue(result.toDos().isEmpty());
        assertTrue(importer.importText("just text", us).isEmpty());
        assertNull(importer.importText("", us).firstDate());
        assertTrue(CalendarImporter.looksLikeCalendar(ICS));
        assertTrue(CalendarImporter.looksLikeCalendar("x ** Calendar Appointment ** y"));
        assertFalse(CalendarImporter.looksLikeCalendar("hello"));
        assertFalse(CalendarImporter.looksLikeCalendar(null));
    }
}
