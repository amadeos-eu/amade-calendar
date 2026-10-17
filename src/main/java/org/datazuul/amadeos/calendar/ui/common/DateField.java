package org.datazuul.amadeos.calendar.ui.common;

import org.datazuul.amadeos.calendar.service.DateFormats;

import javax.swing.*;
import java.time.LocalDate;
import java.util.Optional;

/** A text field holding a date in the user's date format. */
public final class DateField extends JTextField {
    private DateFormats formats;

    public DateField(DateFormats formats) {
        super(12);
        this.formats = formats;
    }

    public void setFormats(DateFormats newFormats) {
        Optional<LocalDate> current = date();
        formats = newFormats;
        current.ifPresent(this::setDate);
    }

    public void setDate(LocalDate date) {
        setText(date == null ? "" : formats.date(date));
    }

    /** The date typed in, empty if the text is not a date. */
    public Optional<LocalDate> date() {
        return formats.parseDateLenient(getText());
    }
}
