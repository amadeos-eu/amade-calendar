package org.datazuul.amadeos.calendar.ui.main;

import org.datazuul.amadeos.calendar.domain.Appointment;
import org.datazuul.amadeos.calendar.domain.CalendarData;
import org.datazuul.amadeos.calendar.domain.CalendarPreferences;
import org.datazuul.amadeos.calendar.domain.CalendarView;
import org.datazuul.amadeos.calendar.domain.Occurrence;
import org.datazuul.amadeos.calendar.domain.Privacy;
import org.datazuul.amadeos.calendar.domain.Reminder;
import org.datazuul.amadeos.calendar.domain.ReminderType;
import org.datazuul.amadeos.calendar.domain.Repeat;
import org.datazuul.amadeos.calendar.domain.ToDoItem;
import org.datazuul.amadeos.calendar.repository.CalendarRepository;
import org.datazuul.amadeos.calendar.repository.PreferencesRepository;
import org.datazuul.amadeos.calendar.service.CalendarService;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MainPresenterTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 13, 8, 54);
    private final MainView view = mock(MainView.class);
    private final CalendarService calendar = new CalendarService();
    private final CalendarRepository repository = mock(CalendarRepository.class);
    private final PreferencesRepository preferences = mock(PreferencesRepository.class);
    private final MainPresenter presenter = new MainPresenter(view, calendar, repository, preferences,
            new DirectExecutorService(), Locale.US, () -> NOW);

    private static Appointment meeting() {
        return new Appointment("m", NOW.toLocalDate(), LocalTime.of(9, 0), LocalTime.of(10, 0), "Project meeting",
                List.of(new Reminder(ReminderType.POPUP, Duration.ofMinutes(5))), Repeat.ONCE, Privacy.TIME_AND_TEXT);
    }

    private void start() throws IOException {
        when(preferences.load()).thenReturn(CalendarPreferences.defaultsFor(Locale.US).withDefaultView(CalendarView.WEEK));
        when(repository.load()).thenReturn(new CalendarData(List.of(meeting()), List.of(), List.of()));
        presenter.start();
    }

    @Test
    void startLoadsAndShowsTheDefaultViewOnToday() throws IOException {
        start();
        verify(view).showView(CalendarView.WEEK, NOW.toLocalDate());
        verify(view).setStatus("Calendar loaded: 1 appointment, no To Do items");
        assertEquals(1, calendar.appointmentCount());
        assertEquals(CalendarView.WEEK, presenter.currentView());
    }

    @Test
    void navigationStepsByTheCurrentView() throws IOException {
        start();
        presenter.showView(CalendarView.MONTH);
        presenter.next();
        verify(view).showView(CalendarView.MONTH, NOW.toLocalDate().plusMonths(1));
        presenter.previous();
        presenter.previous();
        verify(view).showView(CalendarView.MONTH, NOW.toLocalDate().minusMonths(1));
        presenter.today();
        verify(view, times(3)).showView(CalendarView.MONTH, NOW.toLocalDate());
        assertEquals("October 2026", presenter.periodTitle());
        when(view.askDate()).thenReturn(Optional.of(LocalDate.of(2027, 1, 1)));
        presenter.goToDate();
        verify(view).showView(CalendarView.MONTH, LocalDate.of(2027, 1, 1));
        assertEquals("January 2027", presenter.periodTitle());
    }

    @Test
    void editingSavesInTheBackgroundAndRefreshes() throws IOException {
        start();
        Appointment lunch = new Appointment("l", NOW.toLocalDate(), LocalTime.of(12, 30), LocalTime.of(13, 0), "eat lunch",
                List.of(), Repeat.ONCE, Privacy.TIME_ONLY);
        presenter.insertAppointment(lunch);
        verify(repository).save(argThat(d -> d.appointments().size() == 2));
        verify(view).setStatus("Inserted: eat lunch");
        presenter.deleteAppointment(lunch);
        verify(repository).save(argThat(d -> d.appointments().size() == 1));
        ToDoItem todo = new ToDoItem("t", NOW.toLocalDate(), "Report", false);
        presenter.insertToDo(todo);
        presenter.setToDosCompleted(Map.of("t", true));
        assertTrue(calendar.toDo("t").orElseThrow().completed());
        verify(view, times(4)).refresh();
        verify(view).setStatus("1 To Do item updated");
    }

    @Test
    void saveErrorsAreShown() throws IOException {
        start();
        doThrow(new IOException("disk full")).when(repository).save(any());
        when(repository.location()).thenReturn("/x/calendar.ics");
        presenter.insertToDo(new ToDoItem("t", NOW.toLocalDate(), "Report", false));
        // The error is reported on the EDT; wait for it.
        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
        } catch (Exception e) {
            // headless: the EDT still exists
        }
        verify(view, timeout(2000)).showError("Cannot save the calendar /x/calendar.ics: disk full");
    }

    @Test
    void listsCoverTheCurrentPeriod() throws IOException {
        start();
        presenter.showView(CalendarView.DAY);
        presenter.openAppointmentList();
        verify(view).openAppointmentList(eq("Tuesday, 13 October 2026"), argThat(l -> l.size() == 1));
        presenter.showView(CalendarView.YEAR, LocalDate.of(2025, 5, 5));
        presenter.openAppointmentList();
        verify(view).openAppointmentList(eq("2025"), argThat(List::isEmpty));
        presenter.openToDoList();
        verify(view).openToDoList(eq("2025"), argThat(List::isEmpty));
        List<Occurrence> found = presenter.find("project", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        assertEquals(1, found.size());
        presenter.showAppointment(found.getFirst());
        verify(view, times(2)).showView(CalendarView.DAY, NOW.toLocalDate());
        verify(view).openAppointmentEditor(NOW.toLocalDate(), null, meeting());
    }

    @Test
    void optionsAreSavedAndAppliedToTheViews() throws IOException {
        start();
        CalendarPreferences changed = CalendarPreferences.defaultsFor(Locale.GERMANY);
        presenter.applyOptions(changed);
        verify(preferences).save(changed);
        verify(view).setFormats(argThat(f -> f.twentyFourHour()), eq(changed));
        assertEquals("13.10.2026", presenter.formats().date(NOW.toLocalDate()));
    }

    @Test
    void remindersReachTheWindow() throws IOException {
        start();
        presenter.checkReminders();
        verify(view, never()).popup(anyString(), anyString());
        MainPresenter later = new MainPresenter(view, calendar, repository, preferences, new DirectExecutorService(),
                Locale.US, new java.util.function.Supplier<>() {
                    int calls;

                    @Override
                    public LocalDateTime get() {
                        return calls++ < 2 ? NOW : NOW.plusMinutes(1);
                    }
                });
        later.start();
        later.checkReminders();
        verify(view).popup("Calendar - Reminder", "10/13/2026 9:00 AM - 10:00 AM\nProject meeting");
    }

    /** Runs tasks on the calling thread. */
    static final class DirectExecutorService extends AbstractExecutorService {
        private boolean shutdown;

        @Override
        public void execute(Runnable command) {
            command.run();
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }
    }
}
