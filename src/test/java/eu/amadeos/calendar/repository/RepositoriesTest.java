package eu.amadeos.calendar.repository;

import eu.amadeos.calendar.domain.Appointment;
import eu.amadeos.calendar.domain.CalendarData;
import eu.amadeos.calendar.domain.CalendarPreferences;
import eu.amadeos.calendar.domain.CalendarView;
import eu.amadeos.calendar.domain.DateOrder;
import eu.amadeos.calendar.domain.Privacy;
import eu.amadeos.calendar.domain.ReminderType;
import eu.amadeos.calendar.domain.Repeat;
import eu.amadeos.calendar.domain.ToDoItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RepositoriesTest {
    @TempDir
    Path dir;

    @Test
    void calendarRoundTripAndEmptyWithoutFile() throws Exception {
        IcsCalendarRepository repository = new IcsCalendarRepository(dir.resolve("sub").resolve("calendar.ics"));
        assertEquals(CalendarData.EMPTY, repository.load());
        CalendarData data = new CalendarData(
                List.of(new Appointment("a", LocalDate.of(2026, 10, 13), LocalTime.of(9, 0), LocalTime.of(10, 0),
                        "Project meeting", List.of(), Repeat.ONCE, Privacy.TIME_AND_TEXT)),
                List.of(new ToDoItem("t", LocalDate.of(2026, 10, 14), "Report", false)), List.of());
        repository.save(data);
        assertTrue(Files.exists(dir.resolve("sub").resolve("calendar.ics")));
        assertEquals(data, repository.load());
        assertTrue(repository.location().endsWith("calendar.ics"));
    }

    @Test
    void preferencesDefaultsAndRoundTrip() {
        PreferencesRepository repository = new PreferencesRepository(dir.resolve("calendar.conf"), Locale.GERMANY);
        assertEquals(CalendarPreferences.defaultsFor(Locale.GERMANY), repository.load());
        CalendarPreferences prefs = new CalendarPreferences(30, LocalTime.of(8, 30),
                Map.of(ReminderType.BEEP, Duration.ofMinutes(5), ReminderType.POPUP, Duration.ofMinutes(15)),
                "me@example.org", Privacy.NOTHING, 7, 20, false, CalendarView.WEEK, DateOrder.YEAR_MONTH_DAY, '-');
        repository.save(prefs);
        assertEquals(prefs, repository.load());
        // No reminders at all is kept as "none", not replaced by the defaults.
        CalendarPreferences none = new CalendarPreferences(30, LocalTime.of(8, 30), Map.of(), "", Privacy.TIME_ONLY,
                7, 20, true, CalendarView.DAY, DateOrder.DAY_MONTH_YEAR, '.');
        repository.save(none);
        assertEquals(none, repository.load());
        CalendarPreferences printing = none.withPrint(new eu.amadeos.calendar.domain.PrintSettings(
                eu.amadeos.calendar.domain.PrintSettings.Destination.FILE, "Laser", "-o nobanner", "/tmp/c.ps", 2, 3, true));
        repository.save(printing);
        assertEquals(printing, repository.load());
    }
}
