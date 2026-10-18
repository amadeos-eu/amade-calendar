package eu.amadeos.apps.calendar.service.print;

import eu.amadeos.apps.calendar.domain.CalendarView;

import java.time.LocalDate;
import java.util.Locale;

/** The "Report Type" of the Print dialog: the four views, the Appointment List and the To Do List. */
public enum ReportType {
    DAY, WEEK, MONTH, YEAR, APPOINTMENT_LIST, TODO_LIST;

    public static ReportType of(CalendarView view) {
        return valueOf(view.name());
    }

    /** The view whose period this report is paged by; lists are paged by their rows. */
    public CalendarView view() {
        return switch (this) {
            case DAY, APPOINTMENT_LIST, TODO_LIST -> CalendarView.DAY;
            case WEEK -> CalendarView.WEEK;
            case MONTH -> CalendarView.MONTH;
            case YEAR -> CalendarView.YEAR;
        };
    }

    public boolean isList() {
        return this == APPOINTMENT_LIST || this == TODO_LIST;
    }

    /** Wide pages for the week and the month, as on screen. */
    public boolean landscape() {
        return this == WEEK || this == MONTH;
    }

    /** The last day of {@code units} periods of this report starting at {@code from}. */
    public LocalDate end(LocalDate from, int units, Locale locale) {
        CalendarView v = isList() ? CalendarView.DAY : view();
        return v.periodEnd(v.step(from, units - 1), locale);
    }
}
