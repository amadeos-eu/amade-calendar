package org.datazuul.amadeos.calendar.ui.main;

import com.google.inject.Inject;
import org.datazuul.amadeos.calendar.domain.Appointment;
import org.datazuul.amadeos.calendar.domain.CalendarPreferences;
import org.datazuul.amadeos.calendar.domain.CalendarView;
import org.datazuul.amadeos.calendar.domain.Occurrence;
import org.datazuul.amadeos.calendar.domain.ToDoItem;
import org.datazuul.amadeos.calendar.service.CalendarService;
import org.datazuul.amadeos.calendar.service.DateFormats;
import org.datazuul.amadeos.calendar.ui.editor.AppointmentEditor;
import org.datazuul.amadeos.calendar.ui.editor.ToDoEditor;
import org.datazuul.amadeos.calendar.ui.lists.AppointmentListDialog;
import org.datazuul.amadeos.calendar.ui.lists.FindDialog;
import org.datazuul.amadeos.calendar.ui.lists.ToDoListDialog;
import org.datazuul.amadeos.calendar.ui.options.OptionsDialog;
import org.datazuul.amadeos.calendar.ui.views.CalendarPanel;
import org.datazuul.amadeos.calendar.ui.views.DayView;
import org.datazuul.amadeos.calendar.ui.views.MonthView;
import org.datazuul.amadeos.calendar.ui.views.ViewContext;
import org.datazuul.amadeos.calendar.ui.views.WeekView;
import org.datazuul.amadeos.calendar.ui.views.YearView;
import org.datazuul.amadeos.commons.i18n.LocaleSettings;
import org.datazuul.amadeos.commons.swing.AmadeUI;
import org.datazuul.amadeos.commons.swing.EdtExecutor;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.datazuul.amadeos.calendar.i18n.Texts.tr;

/**
 * The Calendar window: menu bar and tool bar on top, the chosen view below, the message line at the
 * bottom, as in dtcm.
 */
public final class MainWindow extends JFrame implements MainView {
    private final LocaleSettings locales;
    private final ViewContext context;
    private final CardLayout cards = new CardLayout();
    private final JPanel viewArea = new JPanel(cards);
    private final Map<CalendarView, CalendarPanel> panels = new EnumMap<>(CalendarView.class);
    private final JLabel status = new JLabel(" ");
    private final JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 3));
    private MainPresenter presenter;
    private MainMenuBar menuBar;
    private CalendarToolBar toolBar;
    private CalendarView currentView = CalendarView.MONTH;
    private AppointmentEditor appointmentEditor;
    private ToDoEditor toDoEditor;
    private AppointmentListDialog appointmentList;
    private ToDoListDialog toDoList;
    private FindDialog findDialog;
    private Timer reminderTimer;

    @Inject
    public MainWindow(LocaleSettings locales, CalendarService calendar, Locale locale) {
        super(tr("app.title"));
        this.locales = locales;
        EdtExecutor.requireEdt();
        CalendarPreferences defaults = CalendarPreferences.defaultsFor(locale);
        this.context = new ViewContext(calendar, new DateFormats(defaults, locale), defaults);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setIconImage(new ImageIcon(getClass().getResource("/icons/amade-calendar.png")).getImage());

        viewArea.setOpaque(false);
        viewArea.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        statusBar.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
        status.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        statusBar.add(status);

        JPanel content = new JPanel(new BorderLayout());
        content.add(viewArea, BorderLayout.CENTER);
        content.add(statusBar, BorderLayout.SOUTH);
        setContentPane(content);
        setPreferredSize(new Dimension(980, 720));
        setLocationRelativeTo(null);
    }

    public void bind(MainPresenter presenter) {
        EdtExecutor.requireEdt();
        this.presenter = presenter;
        menuBar = new MainMenuBar(presenter, this);
        setJMenuBar(menuBar);
        toolBar = new CalendarToolBar(presenter);
        getContentPane().add(toolBar, BorderLayout.NORTH);
        panels.put(CalendarView.DAY, new DayView(context, presenter));
        panels.put(CalendarView.WEEK, new WeekView(context, presenter));
        panels.put(CalendarView.MONTH, new MonthView(context, presenter));
        panels.put(CalendarView.YEAR, new YearView(context, presenter));
        panels.forEach((v, p) -> viewArea.add(p, v.name()));
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                presenter.exit();
            }
        });
        reminderTimer = new Timer(30_000, e -> presenter.checkReminders());
        reminderTimer.start();
        pack();
        setLocationRelativeTo(null);
    }

    // ---- MainView -------------------------------------------------------------------------------

    @Override
    public void showView(CalendarView view, LocalDate date) {
        currentView = view;
        panels.get(view).show(date);
        cards.show(viewArea, view.name());
        menuBar.setView(view);
        toolBar.setView(view);
        toolBar.setPeriod(presenter.periodTitle());
        setTitle(tr("app.title.with", presenter.periodTitle()));
    }

    @Override
    public void refresh() {
        panels.get(currentView).refresh();
        if (appointmentEditor != null) appointmentEditor.refresh();
        if (toDoEditor != null) toDoEditor.refresh();
    }

    @Override
    public void setFormats(DateFormats formats, CalendarPreferences preferences) {
        context.update(formats, preferences);
        if (appointmentEditor != null) appointmentEditor.refresh();
        if (toDoEditor != null) toDoEditor.refresh();
        if (presenter != null && toolBar != null) {
            toolBar.setPeriod(presenter.periodTitle());
            setTitle(tr("app.title.with", presenter.periodTitle()));
        }
    }

    @Override
    public void setStatus(String text) {
        status.setText(text == null || text.isBlank() ? " " : text);
    }

    @Override
    public void showError(String message) {
        JOptionPane.showMessageDialog(this, message, tr("app.title"), JOptionPane.ERROR_MESSAGE);
    }

    @Override
    public boolean confirm(String message) {
        return JOptionPane.showConfirmDialog(this, message, tr("app.title"), JOptionPane.OK_CANCEL_OPTION)
                == JOptionPane.OK_OPTION;
    }

    @Override
    public void openAppointmentEditor(LocalDate date, LocalTime time, Appointment select) {
        if (appointmentEditor == null) {
            appointmentEditor = new AppointmentEditor(this, context, new AppointmentEditor.Handler() {
                @Override
                public void insert(Appointment appointment) {
                    presenter.insertAppointment(appointment);
                }

                @Override
                public void change(Appointment appointment) {
                    presenter.changeAppointment(appointment);
                }

                @Override
                public void delete(Appointment appointment) {
                    presenter.deleteAppointment(appointment);
                }
            });
            appointmentEditor.setIconImage(getIconImage());
        }
        appointmentEditor.showFor(date, time, select);
    }

    @Override
    public void openToDoEditor(LocalDate date, ToDoItem select) {
        if (toDoEditor == null) {
            toDoEditor = new ToDoEditor(this, context, new ToDoEditor.Handler() {
                @Override
                public void insert(ToDoItem item) {
                    presenter.insertToDo(item);
                }

                @Override
                public void change(ToDoItem item) {
                    presenter.changeToDo(item);
                }

                @Override
                public void delete(ToDoItem item) {
                    presenter.deleteToDo(item);
                }
            });
            toDoEditor.setIconImage(getIconImage());
        }
        toDoEditor.showFor(date, select);
    }

    @Override
    public void openAppointmentList(String period, List<Occurrence> occurrences) {
        if (appointmentList == null || !appointmentList.isDisplayable()) {
            appointmentList = new AppointmentListDialog(this, context, presenter::editAppointment);
            appointmentList.setIconImage(getIconImage());
        }
        appointmentList.show(period, occurrences);
    }

    @Override
    public void openToDoList(String period, List<ToDoItem> items) {
        if (toDoList == null || !toDoList.isDisplayable()) {
            toDoList = new ToDoListDialog(this, context, presenter::setToDosCompleted);
            toDoList.setIconImage(getIconImage());
        }
        toDoList.show(period, items);
    }

    @Override
    public void openFind() {
        if (findDialog == null || !findDialog.isDisplayable()) {
            findDialog = new FindDialog(this, context, presenter::find, presenter::showAppointment);
            findDialog.setIconImage(getIconImage());
        }
        findDialog.open();
    }

    @Override
    public Optional<LocalDate> askDate() {
        String answer = (String) JOptionPane.showInputDialog(this, tr("goto.prompt"), tr("goto.title"),
                JOptionPane.QUESTION_MESSAGE, null, null, context.formats().date(presenter.selected()));
        if (answer == null) return Optional.empty();
        Optional<LocalDate> date = context.formats().parseDateLenient(answer);
        if (date.isEmpty()) showError(tr("editor.error.date", context.formats().date(java.time.LocalDate.now())));
        return date;
    }

    @Override
    public void openOptions(CalendarPreferences preferences) {
        OptionsDialog dialog = new OptionsDialog(this, context.formats(), presenter::applyOptions, locales);
        dialog.open(preferences, OptionsDialog.EDITOR_DEFAULTS);
    }

    @Override
    public void beep() {
        Toolkit.getDefaultToolkit().beep();
    }

    @Override
    public void flash() {
        // Invert the message line a few times and bring the window up, as dtcm flashed its icon.
        Color normal = statusBar.getBackground();
        int[] count = {0};
        Timer timer = new Timer(250, null);
        timer.addActionListener(e -> {
            boolean on = count[0] % 2 == 0;
            statusBar.setBackground(on ? AmadeUI.theme().selectionBackground() : normal);
            status.setForeground(on ? AmadeUI.theme().selectionText() : AmadeUI.theme().textActive());
            if (++count[0] >= 8) {
                timer.stop();
                statusBar.setBackground(normal);
                status.setForeground(AmadeUI.theme().textActive());
            }
        });
        timer.start();
        if (getState() == Frame.ICONIFIED) setState(Frame.NORMAL);
        toFront();
    }

    @Override
    public void popup(String title, String text) {
        JOptionPane pane = new JOptionPane(text, JOptionPane.INFORMATION_MESSAGE);
        JDialog dialog = pane.createDialog(this, title);
        dialog.setModal(false);
        dialog.setAlwaysOnTop(true);
        dialog.setVisible(true);
        beep();
    }

    @Override
    public void dispose() {
        if (reminderTimer != null) reminderTimer.stop();
        for (Window w : Window.getWindows()) if (w != this && w.isDisplayable()) w.dispose();
        super.dispose();
    }

    void showAbout() {
        JOptionPane.showMessageDialog(this, tr("about.text") + "\nhttps://github.com/datazuul/amade-calendar",
                tr("menu.help.about"), JOptionPane.INFORMATION_MESSAGE, new ImageIcon(getIconImage()));
    }
}
