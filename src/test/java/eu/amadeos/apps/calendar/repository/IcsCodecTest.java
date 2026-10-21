package eu.amadeos.apps.calendar.repository;

import eu.amadeos.apps.calendar.domain.Appointment;
import eu.amadeos.apps.calendar.domain.CalendarData;
import eu.amadeos.apps.calendar.domain.Privacy;
import eu.amadeos.apps.calendar.domain.Reminder;
import eu.amadeos.apps.calendar.domain.ReminderType;
import eu.amadeos.apps.calendar.domain.Repeat;
import eu.amadeos.apps.calendar.domain.RepeatPreset;
import eu.amadeos.apps.calendar.domain.ToDoItem;
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

    // --8<-- [start:round-trip]
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
    // --8<-- [end:round-trip]

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
    void exceptionsExclusionsAndMultiDayEvents() {
        String ics = """
                BEGIN:VCALENDAR
                BEGIN:VEVENT
                UID:series
                DTSTART:20261012T090000
                DTEND:20261012T100000
                RRULE:FREQ=DAILY;UNTIL=20261018T215959Z
                EXDATE:20261013T090000,20261015T090000
                SUMMARY:Standup
                END:VEVENT
                BEGIN:VEVENT
                UID:series
                RECURRENCE-ID:20261014T090000
                DTSTART:20261014T110000
                DTEND:20261014T120000
                SUMMARY:Standup moved
                END:VEVENT
                BEGIN:VEVENT
                UID:holiday
                DTSTART;VALUE=DATE:20261020
                DTEND;VALUE=DATE:20261023
                SUMMARY:Holiday
                END:VEVENT
                END:VCALENDAR
                """;
        CalendarData data = IcsCodec.read(ics);
        assertEquals(3, data.appointments().size());
        Appointment series = data.appointments().get(0);
        Appointment exception = data.appointments().get(1);
        Appointment holiday = data.appointments().get(2);
        assertEquals(Set.of(LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 15)), series.excluded());
        assertEquals(LocalDate.of(2026, 10, 18), series.repeat().until());
        assertFalse(series.isException());
        assertTrue(exception.isException());
        assertEquals("series", exception.uid());
        assertEquals(LocalDate.of(2026, 10, 14), exception.recurrenceId());
        assertEquals(LocalTime.of(11, 0), exception.start());
        assertEquals("series#2026-10-14", exception.key());
        assertEquals(3, holiday.days());
        assertEquals(LocalDate.of(2026, 10, 22), holiday.lastDay());

        String written = IcsCodec.write(data);
        assertTrue(written.contains("EXDATE:20261013T090000,20261015T090000\r\n"), written);
        assertTrue(written.contains("RECURRENCE-ID:20261014T090000\r\n"));
        assertTrue(written.contains("DTEND;VALUE=DATE:20261023\r\n"));
        assertTrue(written.contains("UNTIL=20261018"));
        assertEquals(data.appointments(), IcsCodec.read(written).appointments());
    }

    @Test
    void timesWithAZoneOrInUtcBecomeLocalTimes() {
        String ics = """
                BEGIN:VCALENDAR
                BEGIN:VEVENT
                UID:z
                DTSTART;TZID=Europe/Berlin:20261012T090000
                DTEND;TZID=Europe/Berlin:20261012T100000
                EXDATE;TZID=Europe/Berlin:20261013T090000
                RRULE:FREQ=DAILY;COUNT=3
                SUMMARY:Zoned
                END:VEVENT
                BEGIN:VEVENT
                UID:u
                DTSTART:20261012T070000Z
                SUMMARY:Utc
                END:VEVENT
                END:VCALENDAR
                """;
        java.time.ZoneId here = java.time.ZoneId.systemDefault();
        java.time.LocalDateTime berlin = java.time.ZonedDateTime.of(2026, 10, 12, 9, 0, 0, 0, java.time.ZoneId.of("Europe/Berlin"))
                .withZoneSameInstant(here).toLocalDateTime();
        java.time.LocalDateTime utc = java.time.ZonedDateTime.of(2026, 10, 12, 7, 0, 0, 0, java.time.ZoneOffset.UTC)
                .withZoneSameInstant(here).toLocalDateTime();
        List<Appointment> read = IcsCodec.read(ics).appointments();
        assertEquals(berlin.toLocalDate(), read.get(0).date());
        assertEquals(berlin.toLocalTime(), read.get(0).start());
        assertEquals(berlin.toLocalTime().plusHours(1), read.get(0).end());
        assertEquals(Set.of(berlin.toLocalDate().plusDays(1)), read.get(0).excluded());
        assertEquals(utc.toLocalDate(), read.get(1).date());
        assertEquals(utc.toLocalTime(), read.get(1).start());
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
