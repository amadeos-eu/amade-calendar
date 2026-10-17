package org.datazuul.amadeos.calendar.repository;

import org.datazuul.amadeos.calendar.domain.Appointment;
import org.datazuul.amadeos.calendar.domain.CalendarData;
import org.datazuul.amadeos.calendar.domain.Privacy;
import org.datazuul.amadeos.calendar.domain.Reminder;
import org.datazuul.amadeos.calendar.domain.ReminderType;
import org.datazuul.amadeos.calendar.domain.Repeat;
import org.datazuul.amadeos.calendar.domain.RepeatPreset;
import org.datazuul.amadeos.calendar.domain.ToDoItem;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class IcsCodecTest {
    private static final LocalDate DAY = LocalDate.of(2026, 10, 13);

    private static Appointment meeting() {
        return new Appointment("m1@amade-calendar", DAY, LocalTime.of(14, 30), LocalTime.of(15, 30),
                "Staff meeting\nMonet conference room; bring the list, please",
                List.of(new Reminder(ReminderType.BEEP, Duration.ofMinutes(5)),
                        new Reminder(ReminderType.POPUP, Duration.ofMinutes(15)),
                        new Reminder(ReminderType.FLASH, Duration.ofMinutes(10)),
                        new Reminder(ReminderType.MAIL, Duration.ofHours(1), "kent@dailyplanet")),
                RepeatPreset.WEEKLY.toRepeat(10, 1, RepeatPreset.Unit.DAYS), Privacy.TIME_ONLY);
    }

    @Test
    void appointmentSurvivesTheRoundTrip() {
        Appointment untimed = new Appointment("h@amade-calendar", LocalDate.of(2026, 10, 31), null, null, "Halloween",
                List.of(), Repeat.ONCE, Privacy.TIME_AND_TEXT);
        Appointment startOnly = new Appointment("t@amade-calendar", DAY, LocalTime.of(18, 0), null, "Trick-or-treating",
                List.of(), RepeatPreset.MONTHLY_BY_WEEKDAY.toRepeat(0, 1, RepeatPreset.Unit.DAYS), Privacy.NOTHING);
        CalendarData data = new CalendarData(List.of(meeting(), untimed, startOnly), List.of(), List.of());
        String ics = IcsCodec.write(data);
        assertTrue(ics.startsWith("BEGIN:VCALENDAR\r\nVERSION:2.0\r\n"), ics);
        assertTrue(ics.contains("DTSTART:20261013T143000\r\n"));
        assertTrue(ics.contains("DTSTART;VALUE=DATE:20261031\r\n"));
        assertTrue(ics.contains("RRULE:FREQ=WEEKLY;COUNT=10\r\n"));
        assertTrue(ics.contains("RRULE:FREQ=MONTHLY;BYDAY=2TU\r\n"));
        assertTrue(ics.contains("SUMMARY:Staff meeting\r\n"));
        assertTrue(ics.contains("DESCRIPTION:Staff meeting\\nMonet conference room\\; bring the list\\, please\r\n"));
        assertTrue(ics.contains("CLASS:CONFIDENTIAL\r\n"));
        assertTrue(ics.contains("ATTENDEE:mailto:kent@dailyplanet\r\n"));
        assertTrue(ics.contains("TRIGGER:-PT1H\r\n"));

        CalendarData back = IcsCodec.read(ics);
        assertEquals(data.appointments(), back.appointments());
        assertEquals(data.toDos(), back.toDos());
    }

    @Test
    void toDoSurvivesTheRoundTrip() {
        ToDoItem done = new ToDoItem("d@amade-calendar", DAY, "Buy milk", true);
        ToDoItem pending = new ToDoItem("p@amade-calendar", DAY.plusDays(1), "Write report\nfor Monday", false);
        String ics = IcsCodec.write(new CalendarData(List.of(), List.of(done, pending), List.of()));
        assertTrue(ics.contains("BEGIN:VTODO\r\n"));
        assertTrue(ics.contains("DUE;VALUE=DATE:20261013\r\n"));
        assertTrue(ics.contains("STATUS:COMPLETED\r\n"));
        assertTrue(ics.contains("STATUS:NEEDS-ACTION\r\n"));
        assertEquals(List.of(done, pending), IcsCodec.read(ics).toDos());
    }

    @Test
    void unknownPropertiesAndComponentsAreKept() {
        String ics = """
                BEGIN:VCALENDAR
                VERSION:2.0
                PRODID:-//Other//Calendar//EN
                BEGIN:VTIMEZONE
                TZID:Europe/Berlin
                END:VTIMEZONE
                BEGIN:VEVENT
                UID:x1
                DTSTART;TZID=Europe/Berlin:20261013T090000
                DTEND;TZID=Europe/Berlin:20261013T100000
                SUMMARY:Project meeting
                LOCATION:Room 4
                X-FOO:bar
                BEGIN:VALARM
                ACTION:DISPLAY
                TRIGGER;VALUE=DATE-TIME:20261013T080000Z
                DESCRIPTION:absolute alarm
                END:VALARM
                END:VEVENT
                END:VCALENDAR
                """;
        CalendarData data = IcsCodec.read(ics);
        assertEquals(1, data.appointments().size());
        Appointment a = data.appointments().getFirst();
        assertEquals("x1", a.uid());
        assertEquals("Project meeting", a.what());
        assertTrue(a.extra().contains("LOCATION:Room 4"), a.extra().toString());
        assertTrue(a.extra().contains("X-FOO:bar"));
        assertTrue(a.extra().contains("BEGIN:VALARM"), "an alarm the editor cannot show is kept as is");
        assertEquals(List.of("BEGIN:VTIMEZONE", "TZID:Europe/Berlin", "END:VTIMEZONE"), data.extra());

        String written = IcsCodec.write(data);
        assertTrue(written.contains("LOCATION:Room 4\r\n"));
        assertTrue(written.contains("TRIGGER;VALUE=DATE-TIME:20261013T080000Z\r\n"));
        assertTrue(written.contains("BEGIN:VTIMEZONE\r\nTZID:Europe/Berlin\r\nEND:VTIMEZONE\r\n"));
        assertEquals(data.appointments(), IcsCodec.read(written).appointments());
    }

    @Test
    void longLinesAreFoldedAndUnfolded() {
        String what = "A".repeat(200) + " " + "B".repeat(100);
        Appointment a = new Appointment("l@amade-calendar", DAY, null, null, what, List.of(), Repeat.ONCE, Privacy.TIME_AND_TEXT);
        String ics = IcsCodec.write(new CalendarData(List.of(a), List.of(), List.of()));
        for (String line : ics.split("\r\n")) assertTrue(line.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 75, line);
        assertEquals(what, IcsCodec.read(ics).appointments().getFirst().what());
    }

    @Test
    void rulesFromOtherProgramsAreUnderstood() {
        assertEquals(Repeat.weekly(Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), 0),
                IcsCodec.parseRule("FREQ=WEEKLY;BYDAY=MO,WE,FR"));
        Repeat monthly = IcsCodec.parseRule("FREQ=MONTHLY;INTERVAL=2;BYDAY=-1FR;COUNT=6");
        assertEquals(Repeat.Frequency.MONTHLY, monthly.frequency());
        assertEquals(2, monthly.interval());
        assertTrue(monthly.byWeekdayOfMonth());
        assertEquals(6, monthly.count());
        Repeat until = IcsCodec.parseRule("FREQ=DAILY;UNTIL=20261231T235959Z");
        assertEquals(LocalDate.of(2026, 12, 31), until.until());
        assertEquals(Repeat.ONCE, IcsCodec.parseRule("FREQ=SECONDLY"));
        assertEquals(Repeat.ONCE, IcsCodec.parseRule(null));
    }

    @Test
    void textEscaping() {
        assertEquals("a\\;b\\,c\\\\d\\ne", IcsCodec.escapeText("a;b,c\\d\ne"));
        assertEquals("a;b,c\\d\ne", IcsCodec.unescapeText("a\\;b\\,c\\\\d\\Ne"));
    }

    @Test
    void emptyAndBrokenInputGiveAnEmptyCalendar() {
        assertEquals(CalendarData.EMPTY, IcsCodec.read(""));
        CalendarData data = IcsCodec.read("BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nSUMMARY:no date\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n");
        assertTrue(data.appointments().isEmpty());
    }
}
