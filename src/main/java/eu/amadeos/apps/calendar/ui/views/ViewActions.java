package eu.amadeos.apps.calendar.ui.views;

import eu.amadeos.apps.calendar.domain.CalendarView;
import eu.amadeos.apps.calendar.domain.Occurrence;

import java.time.LocalDate;
import java.time.LocalTime;

/** What the views ask the presenter to do when the user clicks in them. */
public interface ViewActions {
    /** A day was clicked: it becomes the selected day, the view stays. */
    void selectDay(LocalDate date);

    /** A day name or month name was clicked: switch to that view at that date. */
    void showView(CalendarView view, LocalDate date);

    /** A day (or an hour of it) was double-clicked: open the Appointment Editor there. */
    void editDay(LocalDate date, LocalTime time);

    /** An appointment was double-clicked: open the Appointment Editor on it. */
    void editAppointment(Occurrence occurrence);
}
