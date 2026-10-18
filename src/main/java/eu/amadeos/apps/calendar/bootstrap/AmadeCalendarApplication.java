package eu.amadeos.apps.calendar.bootstrap;

import eu.amadeos.apps.calendar.ui.main.MainPresenter;
import eu.amadeos.apps.calendar.ui.main.MainWindow;
import eu.amadeos.commons.app.AmadeApplication;
import eu.amadeos.commons.app.AppInfo;

/** Entry point of the Calendar. Arguments are files to schedule: iCalendar or dtcm appointment text. */
public final class AmadeCalendarApplication {
    public static final AppInfo APP = new AppInfo("amade-calendar", "Calendar", "calendar");

    private AmadeCalendarApplication() {
    }

    public static void main(String[] args) {
        AmadeApplication.launch(APP, injector -> {
            MainWindow window = injector.getInstance(MainWindow.class);
            MainPresenter presenter = injector.getInstance(MainPresenter.class);
            window.bind(presenter);
            window.setVisible(true);
            presenter.start(java.util.List.of(args));
        }, new CalendarModule());
    }
}
