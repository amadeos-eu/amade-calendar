package eu.amadeos.calendar.service;

import eu.amadeos.calendar.domain.Appointment;
import eu.amadeos.calendar.domain.EditScope;
import eu.amadeos.calendar.domain.Occurrence;
import eu.amadeos.calendar.domain.Privacy;
import eu.amadeos.calendar.domain.Repeat;
import eu.amadeos.calendar.domain.RepeatPreset;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RecurrenceEditTest {
    private static final LocalDate MON = LocalDate.of(2026, 10, 12);
    private final CalendarService service = new CalendarService();
    private final Appointment daily = new Appointment("s", MON, LocalTime.of(9, 0), LocalTime.of(10, 0), "Standup", List.of(),
            RepeatPreset.DAILY.toRepeat(0, 1, RepeatPreset.Unit.DAYS), Privacy.TIME_AND_TEXT);

    private List<String> week() {
        return service.occurrencesBetween(MON, MON.plusDays(6)).stream()
                .map(o -> o.date().getDayOfMonth() + ":" + o.appointment().summary() + "@" + o.appointment().start()).toList();
    }

    private Appointment edited(String what, LocalDate date, LocalTime start) {
        return new Appointment("ignored", date, start, start.plusHours(1), what, List.of(), daily.repeat(), Privacy.TIME_AND_TEXT);
    }

    @Test
    void thisOneBecomesAnException() {
        service.put(daily);
        service.change(daily, MON.plusDays(2), edited("Standup moved", MON.plusDays(2), LocalTime.of(11, 0)), EditScope.THIS_ONE);
        assertEquals(List.of("12:Standup@09:00", "13:Standup@09:00", "14:Standup moved@11:00", "15:Standup@09:00",
                "16:Standup@09:00", "17:Standup@09:00", "18:Standup@09:00"), week());
        assertEquals(1, service.exceptionsOf("s").size());
        assertEquals(2, service.data().appointments().size());
        // The exception can move to another day; the original day stays empty.
        Appointment exception = service.exceptionsOf("s").get(MON.plusDays(2));
        service.change(exception, MON.plusDays(2), edited("Standup moved", MON.plusDays(3), LocalTime.of(12, 0)), EditScope.ALL);
        assertEquals(List.of("12:Standup@09:00", "13:Standup@09:00", "15:Standup@09:00", "15:Standup moved@12:00",
                "16:Standup@09:00", "17:Standup@09:00", "18:Standup@09:00"), week());
        // Deleting the exception deletes that occurrence of the series.
        service.delete(service.exceptionsOf("s").get(MON.plusDays(2)), MON.plusDays(3), EditScope.ALL);
        assertEquals(6, week().size());
        assertTrue(service.appointment("s").orElseThrow().excluded().contains(MON.plusDays(2)));
    }

    @Test
    void forwardSplitsTheSeries() {
        service.put(daily);
        service.change(daily, MON.plusDays(3), edited("Standup late", MON.plusDays(3), LocalTime.of(10, 0)), EditScope.FORWARD);
        assertEquals(List.of("12:Standup@09:00", "13:Standup@09:00", "14:Standup@09:00", "15:Standup late@10:00",
                "16:Standup late@10:00", "17:Standup late@10:00", "18:Standup late@10:00"), week());
        assertEquals(MON.plusDays(2), service.appointment("s").orElseThrow().repeat().until());
        assertEquals(2, service.data().appointments().size());
        // Forward on the first day changes the whole series in place.
        Appointment first = service.appointment("s").orElseThrow();
        service.change(first, MON, edited("Early", MON, LocalTime.of(8, 0)).withRepeat(first.repeat()), EditScope.FORWARD);
        assertEquals("Early", service.appointment("s").orElseThrow().summary());
        assertEquals(MON.plusDays(2), service.appointment("s").orElseThrow().repeat().until(), "the edited repeat is kept");
    }

    @Test
    void allMovesTheSeriesWithItsOffset() {
        service.put(daily);
        service.delete(daily, MON.plusDays(1), EditScope.THIS_ONE);
        Appointment series = service.appointment("s").orElseThrow();
        // Edited on Wednesday, moved one day later: the series starts Tuesday.
        service.change(series, MON.plusDays(2), edited("Standup", MON.plusDays(3), LocalTime.of(9, 30))
                .withExcluded(series.excluded()), EditScope.ALL);
        Appointment moved = service.appointment("s").orElseThrow();
        assertEquals(MON.plusDays(1), moved.date());
        assertEquals(LocalTime.of(9, 30), moved.start());
        assertEquals(Set.of(MON.plusDays(1)), moved.excluded(), "the editor passes the exclusions on");
        service.change(moved, MON.plusDays(3), edited("Standup", MON.plusDays(3), LocalTime.of(9, 30)), EditScope.ALL);
        assertEquals(Set.of(), service.appointment("s").orElseThrow().excluded(), "a restored day is back");
        assertEquals(List.of("13:Standup@09:30", "14:Standup@09:30", "15:Standup@09:30", "16:Standup@09:30", "17:Standup@09:30",
                "18:Standup@09:30"), week());
    }

    @Test
    void deleteScopes() {
        service.put(daily);
        service.delete(daily, MON.plusDays(1), EditScope.THIS_ONE);
        assertEquals(6, week().size());
        service.delete(service.appointment("s").orElseThrow(), MON.plusDays(4), EditScope.FORWARD);
        assertEquals(List.of("12:Standup@09:00", "14:Standup@09:00", "15:Standup@09:00"), week());
        service.delete(service.appointment("s").orElseThrow(), MON, EditScope.FORWARD);
        assertTrue(week().isEmpty());
        service.put(daily);
        service.change(daily, MON.plusDays(1), edited("x", MON.plusDays(1), LocalTime.NOON), EditScope.THIS_ONE);
        service.delete(daily, MON, EditScope.ALL);
        assertTrue(service.data().appointments().isEmpty(), "exceptions go with the series");
    }

    @Test
    void multiDayAppointmentsAreOnEveryDay() {
        Appointment holiday = new Appointment("h", MON, null, null, "Holiday", List.of(), Repeat.ONCE, Privacy.TIME_AND_TEXT,
                List.of(), 3, Set.of(), null);
        service.put(holiday);
        assertEquals(List.of("12:Holiday@null", "13:Holiday@null", "14:Holiday@null"), week());
        assertEquals(1, service.occurrencesBetween(MON.plusDays(2), MON.plusDays(10)).size(), "found from inside the span");
        assertTrue(service.hasAppointments(MON.plusDays(2)));
        assertFalse(service.hasAppointments(MON.plusDays(3)));
        Occurrence o = service.occurrencesOn(MON.plusDays(1)).getFirst();
        assertEquals(MON.plusDays(2), o.appointment().lastDay());
    }
}
