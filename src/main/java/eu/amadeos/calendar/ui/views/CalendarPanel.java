package eu.amadeos.calendar.ui.views;

import javax.swing.*;
import java.time.LocalDate;

/** One of the four views: shows the period around a selected day and redraws on request. */
public abstract class CalendarPanel extends JPanel {
    protected final ViewContext context;
    protected final ViewActions actions;
    protected LocalDate selected = LocalDate.now();

    protected CalendarPanel(ViewContext context, ViewActions actions) {
        this.context = context;
        this.actions = actions;
        setOpaque(false);
    }

    /** Shows the period that contains the date and marks the date as selected. */
    public void show(LocalDate date) {
        selected = date;
        refresh();
    }

    public LocalDate selected() {
        return selected;
    }

    /** Redraws with the current calendar data, formats and display settings. */
    public abstract void refresh();
}
