package org.datazuul.amadeos.calendar.ui.main;

import com.google.inject.Inject;
import org.datazuul.amadeos.calendar.domain.Appointment;
import org.datazuul.amadeos.calendar.domain.CalendarData;
import org.datazuul.amadeos.calendar.domain.CalendarPreferences;
import org.datazuul.amadeos.calendar.domain.CalendarView;
import org.datazuul.amadeos.calendar.domain.Occurrence;
import org.datazuul.amadeos.calendar.domain.ReminderType;
import org.datazuul.amadeos.calendar.domain.ToDoItem;
import org.datazuul.amadeos.calendar.repository.CalendarRepository;
import org.datazuul.amadeos.calendar.repository.PreferencesRepository;
import org.datazuul.amadeos.calendar.service.CalendarService;
import org.datazuul.amadeos.calendar.service.DateFormats;
import org.datazuul.amadeos.calendar.service.ReminderCheck;
import org.datazuul.amadeos.calendar.ui.views.ViewActions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import static org.datazuul.amadeos.calendar.i18n.Texts.tr;

/**
 * Behaviour of the Calendar window: navigation between the views, editing through the service, saving
 * in the background and the reminders. Runs on the EDT.
 */
public final class MainPresenter implements ViewActions {
    private static final Logger LOG = LoggerFactory.getLogger(MainPresenter.class);

    private final MainView view;
    private final CalendarService calendar;
    private final CalendarRepository repository;
    private final PreferencesRepository preferencesRepository;
    private final ExecutorService executor;
    private final Locale locale;
    private final Supplier<LocalDateTime> clock;
    private CalendarPreferences preferences;
    private DateFormats formats;
    private CalendarView currentView = CalendarView.MONTH;
    private LocalDate selected = LocalDate.now();
    private ReminderCheck reminders;

    @Inject
    public MainPresenter(MainView view, CalendarService calendar, CalendarRepository repository,
                         PreferencesRepository preferencesRepository, ExecutorService executor, Locale locale) {
        this(view, calendar, repository, preferencesRepository, executor, locale, LocalDateTime::now);
    }

    MainPresenter(MainView view, CalendarService calendar, CalendarRepository repository,
                  PreferencesRepository preferencesRepository, ExecutorService executor, Locale locale,
                  Supplier<LocalDateTime> clock) {
        this.view = view;
        this.calendar = calendar;
        this.repository = repository;
        this.preferencesRepository = preferencesRepository;
        this.executor = executor;
        this.locale = locale;
        this.clock = clock;
        this.preferences = CalendarPreferences.defaultsFor(locale);
        this.formats = new DateFormats(preferences, locale);
    }

    public CalendarPreferences preferences() {
        return preferences;
    }

    public DateFormats formats() {
        return formats;
    }

    public CalendarView currentView() {
        return currentView;
    }

    public LocalDate selected() {
        return selected;
    }

    /** Loads settings and calendar and shows the default view on today. */
    public void start() {
        preferences = preferencesRepository.load();
        formats = new DateFormats(preferences, locale);
        view.setFormats(formats, preferences);
        try {
            calendar.load(repository.load());
        } catch (IOException | RuntimeException e) {
            LOG.error("Cannot read the calendar", e);
            view.showError(tr("error.load", repository.location(), e.getMessage()));
        }
        reminders = new ReminderCheck(calendar, clock.get());
        selected = clock.get().toLocalDate();
        showView(preferences.defaultView(), selected);
        view.setStatus(tr("status.loaded", calendar.appointmentCount(), calendar.toDoCount()));
    }

    // ---- navigation -----------------------------------------------------------------------------

    @Override
    public void showView(CalendarView newView, LocalDate date) {
        currentView = newView;
        selected = date;
        view.showView(newView, date);
    }

    public void showView(CalendarView newView) {
        showView(newView, selected);
    }

    @Override
    public void selectDay(LocalDate date) {
        showView(currentView, date);
    }

    public void next() {
        showView(currentView, currentView.step(selected, 1));
    }

    public void previous() {
        showView(currentView, currentView.step(selected, -1));
    }

    public void today() {
        showView(currentView, clock.get().toLocalDate());
    }

    public void goToDate() {
        view.askDate().ifPresent(this::selectDay);
    }

    /** The current view's period as the lists name it. */
    public String periodTitle() {
        LocalDate from = currentView.periodStart(selected, locale);
        LocalDate to = currentView.periodEnd(selected, locale);
        return switch (currentView) {
            case DAY -> formats.longDate(selected);
            case MONTH -> formats.monthAndYear(selected);
            case YEAR -> Integer.toString(selected.getYear());
            case WEEK -> formats.date(from) + " - " + formats.date(to);
        };
    }

    // ---- editing --------------------------------------------------------------------------------

    @Override
    public void editDay(LocalDate date, LocalTime time) {
        selected = date;
        view.openAppointmentEditor(date, time, null);
    }

    @Override
    public void editAppointment(Occurrence occurrence) {
        selected = occurrence.date();
        view.openAppointmentEditor(occurrence.date(), null, occurrence.appointment());
    }

    public void openAppointmentEditor() {
        view.openAppointmentEditor(selected, null, null);
    }

    public void openToDoEditor() {
        view.openToDoEditor(selected, null);
    }

    public void insertAppointment(Appointment a) {
        calendar.put(a);
        changed(tr("status.inserted", a.summary()));
    }

    public void changeAppointment(Appointment a) {
        calendar.put(a);
        changed(tr("status.changed", a.summary()));
    }

    public void deleteAppointment(Appointment a) {
        calendar.removeAppointment(a.uid());
        changed(tr("status.deleted", a.summary()));
    }

    public void insertToDo(ToDoItem t) {
        calendar.put(t);
        changed(tr("status.inserted", t.summary()));
    }

    public void changeToDo(ToDoItem t) {
        calendar.put(t);
        changed(tr("status.changed", t.summary()));
    }

    public void deleteToDo(ToDoItem t) {
        calendar.removeToDo(t.uid());
        changed(tr("status.deleted", t.summary()));
    }

    /** From the To Do List dialog: the check boxes that were changed, by UID. */
    public void setToDosCompleted(Map<String, Boolean> completed) {
        if (completed.isEmpty()) return;
        completed.forEach((uid, done) -> calendar.toDo(uid).ifPresent(t -> calendar.put(t.withCompleted(done))));
        changed(tr("status.todos-updated", completed.size()));
    }

    private void changed(String status) {
        view.refresh();
        view.setStatus(status);
        save();
    }

    private void save() {
        CalendarData data = calendar.data();
        executor.execute(() -> {
            try {
                repository.save(data);
            } catch (IOException | RuntimeException e) {
                LOG.error("Cannot save the calendar", e);
                javax.swing.SwingUtilities.invokeLater(() ->
                        view.showError(tr("error.save", repository.location(), e.getMessage())));
            }
        });
    }

    // ---- lists and find -------------------------------------------------------------------------

    public void openAppointmentList() {
        LocalDate from = currentView.periodStart(selected, locale);
        LocalDate to = currentView.periodEnd(selected, locale);
        view.openAppointmentList(periodTitle(), calendar.occurrencesBetween(from, to));
    }

    public void openToDoList() {
        LocalDate from = currentView.periodStart(selected, locale);
        LocalDate to = currentView.periodEnd(selected, locale);
        view.openToDoList(periodTitle(), calendar.toDosBetween(from, to));
    }

    public void openFind() {
        view.openFind();
    }

    public List<Occurrence> find(String text, LocalDate from, LocalDate to) {
        return calendar.find(text, from, to);
    }

    /** "Show Appointment" in the Find dialog: Day view on that day with the editor open on it. */
    public void showAppointment(Occurrence occurrence) {
        showView(CalendarView.DAY, occurrence.date());
        view.openAppointmentEditor(occurrence.date(), null, occurrence.appointment());
    }

    // ---- options --------------------------------------------------------------------------------

    public void openOptions() {
        view.openOptions(preferences);
    }

    public void applyOptions(CalendarPreferences newPreferences) {
        preferences = newPreferences;
        formats = new DateFormats(preferences, locale);
        try {
            preferencesRepository.save(preferences);
        } catch (RuntimeException e) {
            LOG.error("Cannot save the settings", e);
            view.showError(tr("error.save-options", e.getMessage()));
        }
        view.setFormats(formats, preferences);
        view.refresh();
    }

    // ---- reminders ------------------------------------------------------------------------------

    /** Called every half minute by the window's timer. */
    public void checkReminders() {
        if (reminders == null) return;
        for (ReminderCheck.Due due : reminders.check(clock.get())) {
            Appointment a = due.occurrence().appointment();
            String when = formats.date(due.occurrence().date()) + " "
                    + formats.timeRange(a.start(), a.end());
            switch (due.reminder().type()) {
                case BEEP -> view.beep();
                case FLASH -> view.flash();
                case POPUP -> view.popup(tr("reminder.popup.title"), when + "\n" + a.what());
                case MAIL -> LOG.info("Mail reminder for '{}' to {} (sending comes with amade-mail integration)",
                        a.summary(), due.reminder().mailTo());
            }
            if (due.reminder().type() != ReminderType.MAIL) view.setStatus(tr("status.reminder", a.summary()));
        }
    }

    public void exit() {
        view.dispose();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.exit(0);
    }
}
