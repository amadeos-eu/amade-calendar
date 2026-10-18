package eu.amadeos.calendar.ui.main;

import com.google.inject.Inject;
import eu.amadeos.calendar.domain.Appointment;
import eu.amadeos.calendar.domain.CalendarData;
import eu.amadeos.calendar.domain.CalendarPreferences;
import eu.amadeos.calendar.domain.CalendarView;
import eu.amadeos.calendar.domain.EditScope;
import eu.amadeos.calendar.domain.Occurrence;
import eu.amadeos.calendar.domain.ToDoItem;
import eu.amadeos.calendar.repository.CalendarRepository;
import eu.amadeos.calendar.repository.PreferencesRepository;
import eu.amadeos.calendar.service.CalendarImporter;
import eu.amadeos.calendar.service.CalendarService;
import eu.amadeos.calendar.service.DateFormats;
import eu.amadeos.calendar.service.DtcmText;
import eu.amadeos.calendar.service.ReminderCheck;
import eu.amadeos.calendar.service.print.CalendarPrinter;
import eu.amadeos.calendar.service.print.PrintRequest;
import eu.amadeos.calendar.service.print.ReportPages;
import eu.amadeos.calendar.service.print.ReportType;
import eu.amadeos.commons.desktop.DesktopLauncher;
import eu.amadeos.calendar.ui.views.ViewActions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import static eu.amadeos.calendar.i18n.Texts.tr;

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
    private final DesktopLauncher desktop;
    private final Supplier<LocalDateTime> clock;
    private final CalendarImporter importer;
    private final CalendarPrinter printer;
    private CalendarPreferences preferences;
    private DateFormats formats;
    private CalendarView currentView = CalendarView.MONTH;
    private LocalDate selected = LocalDate.now();
    private ReminderCheck reminders;

    @Inject
    public MainPresenter(MainView view, CalendarService calendar, CalendarRepository repository,
                         PreferencesRepository preferencesRepository, ExecutorService executor, Locale locale,
                         DesktopLauncher desktop) {
        this(view, calendar, repository, preferencesRepository, executor, locale, desktop, new CalendarPrinter(),
                LocalDateTime::now);
    }

    MainPresenter(MainView view, CalendarService calendar, CalendarRepository repository,
                  PreferencesRepository preferencesRepository, ExecutorService executor, Locale locale,
                  DesktopLauncher desktop, CalendarPrinter printer, Supplier<LocalDateTime> clock) {
        this.printer = printer;
        this.view = view;
        this.calendar = calendar;
        this.repository = repository;
        this.preferencesRepository = preferencesRepository;
        this.executor = executor;
        this.locale = locale;
        this.desktop = desktop;
        this.clock = clock;
        this.importer = new CalendarImporter(calendar);
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

    /** Loads settings and calendar, shows the default view on today and schedules the files given. */
    public void start(List<String> arguments) {
        start();
        List<Path> files = arguments.stream().filter(a -> !a.startsWith("-")).map(Path::of).toList();
        if (!files.isEmpty()) importFiles(files);
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

    public void changeAppointment(Appointment original, LocalDate day, Appointment edited, EditScope scope) {
        calendar.change(original, day, edited, scope);
        changed(tr("status.changed", edited.summary()));
    }

    public void deleteAppointment(Appointment original, LocalDate day, EditScope scope) {
        calendar.delete(original, day, scope);
        changed(tr("status.deleted", original.summary()));
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

    // ---- interchange: files, drops and mail -----------------------------------------------------

    /** Schedules the appointments in the files (iCalendar or dtcm text), e.g. from the command line or a drop. */
    public void importFiles(List<Path> files) {
        List<Appointment> appointments = new java.util.ArrayList<>();
        List<ToDoItem> toDos = new java.util.ArrayList<>();
        for (Path file : files) {
            try {
                if (!Files.isRegularFile(file)) throw new IOException(tr("error.import.no-file"));
                String text = Files.readString(file);
                if (!CalendarImporter.looksLikeCalendar(text)) throw new IOException(tr("error.import.format"));
                CalendarImporter.Result result = importer.importText(text, formats);
                appointments.addAll(result.appointments());
                toDos.addAll(result.toDos());
            } catch (IOException | RuntimeException e) {
                LOG.warn("Cannot import {}", file, e);
                view.showError(tr("error.import.file", file.toString(), e.getMessage()));
            }
        }
        imported(new CalendarImporter.Result(appointments, toDos));
    }

    /** Schedules the appointments in dropped text (iCalendar or dtcm text). */
    public void importText(String text) {
        if (!CalendarImporter.looksLikeCalendar(text)) {
            view.setStatus(tr("error.import.format"));
            return;
        }
        imported(importer.importText(text, formats));
    }

    private void imported(CalendarImporter.Result result) {
        if (result.isEmpty()) {
            view.setStatus(tr("status.imported", 0, 0));
            return;
        }
        if (result.firstDate() != null) showView(CalendarView.DAY, result.firstDate());
        changed(tr("status.imported", result.appointments().size(), result.toDos().size()));
    }

    /** The Mail button of the editor: the mailer opens with the appointment as the message text. */
    public void mailAppointment(Appointment a) {
        mail("", tr("mail.appointment.subject", a.summary()), a);
    }

    private void mail(String to, String subject, Appointment a) {
        String uri = DtcmText.mailto(to, subject, a, formats);
        try {
            desktop.browse(uri);
            view.setStatus(tr("status.mail-opened", a.summary()));
        } catch (IOException | RuntimeException e) {
            LOG.error("Cannot open the mailer", e);
            view.showError(tr("error.mail", e.getMessage()));
        }
    }

    // ---- printing -------------------------------------------------------------------------------

    /** "Print Current View": the current view's period, as many units as the Printer Settings say. */
    public void printCurrentView() {
        ReportType type = ReportType.of(currentView);
        LocalDate from = currentView.periodStart(selected, locale);
        print(new PrintRequest(type, from, type.end(from, preferences.print().units(), locale), preferences.print()));
    }

    public void openPrintDialog() {
        view.openPrintDialog(preferences.print(), ReportType.of(currentView), currentView.periodStart(selected, locale),
                currentView.periodEnd(selected, locale));
    }

    /** Prints in the background and reports in the message line. */
    public void print(PrintRequest request) {
        ReportPages pages = new ReportPages(request, calendar, formats, labels(), ReportPages.paperFor(locale));
        String jobName = tr("print.job-name", tr("print.report." + request.type().name().toLowerCase(Locale.ROOT)));
        Path target = Path.of(request.settings().fileName());
        if (!target.isAbsolute()) target = Path.of(System.getProperty("user.home")).resolve(target);
        Path fileTarget = target;
        view.setStatus(tr("status.printing", pages.getNumberOfPages()));
        executor.execute(() -> {
            try {
                CalendarPrinter.Outcome outcome = printer.print(pages, request.settings(), fileTarget, jobName);
                javax.swing.SwingUtilities.invokeLater(() -> view.setStatus(outcome.file() != null
                        ? tr("status.printed-file", outcome.file().toString())
                        : tr("status.printed", pages.getNumberOfPages(), outcome.output())));
            } catch (IOException | java.awt.print.PrinterException | RuntimeException e) {
                LOG.error("Printing failed", e);
                javax.swing.SwingUtilities.invokeLater(() -> view.showError(tr("error.print", e.getMessage())));
            }
        });
    }

    private ReportPages.Labels labels() {
        return new ReportPages.Labels() {
            @Override
            public String reportTitle(ReportType type) {
                return tr("print.report." + type.name().toLowerCase(Locale.ROOT));
            }

            @Override
            public String footer() {
                return tr("print.footer", formats.date(clock.get().toLocalDate()));
            }

            @Override
            public String busy() {
                return tr("print.busy");
            }

            @Override
            public String column(String id) {
                return tr("list.column." + id);
            }
        };
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
                case MAIL -> mail(due.reminder().mailTo(), tr("mail.reminder.subject", a.summary()), a);
            }
            view.setStatus(tr("status.reminder", a.summary()));
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
