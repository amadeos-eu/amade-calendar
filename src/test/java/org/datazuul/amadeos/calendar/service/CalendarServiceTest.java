package org.datazuul.amadeos.calendar.service;

import org.datazuul.amadeos.calendar.domain.Appointment;
import org.datazuul.amadeos.calendar.domain.CalendarData;
import org.datazuul.amadeos.calendar.domain.Occurrence;
import org.datazuul.amadeos.calendar.domain.Privacy;
import org.datazuul.amadeos.calendar.domain.Reminder;
import org.datazuul.amadeos.calendar.domain.ReminderType;
import org.datazuul.amadeos.calendar.domain.Repeat;
import org.datazuul.amadeos.calendar.domain.RepeatPreset;
import org.datazuul.amadeos.calendar.domain.ToDoItem;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CalendarServiceTest {
    private static final LocalDate DAY = LocalDate.of(2026, 10, 13);
    private final CalendarService service = new CalendarService();

    private static Appointment at(String uid, LocalTime start, String what, Repeat repeat) {
        return new Appointment(uid, DAY, start, start == null ? null : start.plusHours(1), what,
                List.of(new Reminder(ReminderType.BEEP, Duration.ofMinutes(5))), repeat, Privacy.TIME_AND_TEXT);
    }

    @Test
    void occurrencesAreOrderedAndExpanded() {
        service.put(at("b", LocalTime.of(14, 0), "Testing", Repeat.ONCE));
        service.put(at("a", LocalTime.of(9, 0), "Project meeting", RepeatPreset.DAILY.toRepeat(0, 1, RepeatPreset.Unit.DAYS)));
        service.put(at("c", null, "Halloween", Repeat.ONCE));
        List<Occurrence> today = service.occurrencesOn(DAY);
        assertEquals(List.of("Halloween", "Project meeting", "Testing"),
                today.stream().map(o -> o.appointment().summary()).toList());
        assertEquals(List.of("Project meeting"),
                service.occurrencesOn(DAY.plusDays(5)).stream().map(o -> o.appointment().summary()).toList());
        assertTrue(service.hasAppointments(DAY.plusDays(100)));
        assertFalse(service.hasAppointments(DAY.minusDays(1)));
        assertEquals(3 + 2, service.occurrencesBetween(DAY, DAY.plusDays(2)).size());
    }

    @Test
    void findIsCaseInsensitiveAndPerOccurrence() {
        service.put(at("a", LocalTime.of(9, 0), "Project meeting", RepeatPreset.WEEKLY.toRepeat(3, 1, RepeatPreset.Unit.DAYS)));
        service.put(at("b", LocalTime.of(14, 0), "Testing", Repeat.ONCE));
        assertEquals(3, service.find("MEETING", DAY.minusMonths(6), DAY.plusMonths(6)).size());
        assertEquals(1, service.find("test", DAY.minusMonths(6), DAY.plusMonths(6)).size());
        assertTrue(service.find(" ", DAY, DAY).isEmpty());
    }

    @Test
    void toDosSortedAndFiltered() {
        service.put(new ToDoItem("t2", DAY.plusDays(1), "Later", false));
        service.put(new ToDoItem("t1", DAY, "Now", true));
        assertEquals(List.of("Now", "Later"), service.toDos().stream().map(ToDoItem::summary).toList());
        assertEquals(1, service.toDosBetween(DAY, DAY).size());
        assertTrue(service.removeToDo("t1"));
        assertFalse(service.removeToDo("t1"));
        assertEquals(1, service.toDoCount());
    }

    @Test
    void dataRoundTripKeepsExtras() {
        CalendarData data = new CalendarData(List.of(at("a", LocalTime.of(9, 0), "x", Repeat.ONCE)),
                List.of(new ToDoItem("t", DAY, "y", false)), List.of("BEGIN:VJOURNAL", "END:VJOURNAL"));
        service.load(data);
        assertEquals(data, service.data());
    }

    @Test
    void remindersFireOnceAndNotForThePast() {
        service.put(at("a", LocalTime.of(9, 0), "Project meeting", RepeatPreset.DAILY.toRepeat(0, 1, RepeatPreset.Unit.DAYS)));
        ReminderCheck check = new ReminderCheck(service, DAY.atTime(8, 50));
        assertTrue(check.check(DAY.atTime(8, 54)).isEmpty());
        List<ReminderCheck.Due> due = check.check(DAY.atTime(8, 56));
        assertEquals(1, due.size());
        assertEquals(ReminderType.BEEP, due.getFirst().reminder().type());
        assertEquals(DAY, due.getFirst().occurrence().date());
        assertTrue(check.check(DAY.atTime(9, 30)).isEmpty(), "fires once");
        assertEquals(1, check.check(DAY.plusDays(1).atTime(9, 0)).size(), "the next day's occurrence");
        assertTrue(new ReminderCheck(service, LocalDateTime.of(2026, 10, 20, 12, 0)).check(LocalDateTime.of(2026, 10, 20, 12, 1))
                .isEmpty(), "reminders before the start are not caught up on");
    }
}
