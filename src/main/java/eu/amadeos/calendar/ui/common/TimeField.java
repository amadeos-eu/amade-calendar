package eu.amadeos.calendar.ui.common;

import eu.amadeos.calendar.service.DateFormats;

import javax.swing.*;
import java.time.LocalTime;
import java.util.Optional;

/**
 * An editable combo box with a time every half hour, as the Start and End fields of the CDE editor
 * offer them. An empty field means "no time".
 */
public final class TimeField extends JComboBox<String> {
    private DateFormats formats;

    public TimeField(DateFormats formats) {
        this.formats = formats;
        setEditable(true);
        fill();
        setPrototypeDisplayValue("12:30 AM  ");
    }

    public void setFormats(DateFormats newFormats) {
        Optional<LocalTime> current = time();
        formats = newFormats;
        fill();
        setTime(current.orElse(null));
    }

    private void fill() {
        removeAllItems();
        addItem("");
        for (int h = 0; h < 24; h++) {
            addItem(formats.time(LocalTime.of(h, 0)));
            addItem(formats.time(LocalTime.of(h, 30)));
        }
    }

    public void setTime(LocalTime time) {
        setSelectedItem(time == null ? "" : formats.time(time));
    }

    /** The time typed or chosen, empty for an empty field or an unreadable text. */
    public Optional<LocalTime> time() {
        Object item = getEditor().getItem();
        return formats.parseTime(item == null ? "" : item.toString());
    }

    public boolean isBlank() {
        Object item = getEditor().getItem();
        return item == null || item.toString().isBlank();
    }
}
