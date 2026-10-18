package org.datazuul.amadeos.calendar.ui.common;

import org.datazuul.amadeos.calendar.service.DateFormats;
import org.datazuul.amadeos.commons.swing.DateChooser;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.util.Optional;

/**
 * A date in the user's date format: a text field to type into and the small calendar button that
 * opens the {@link DateChooser}.
 */
public final class DateField extends JPanel {
    private final JTextField text = new JTextField(12);
    private final JButton choose;
    private DateFormats formats;

    public DateField(DateFormats formats) {
        super(new BorderLayout(2, 0));
        this.formats = formats;
        setOpaque(false);
        choose = DateChooser.button(() -> date().orElse(null), this::setDate, () -> this.formats.locale());
        add(text, BorderLayout.CENTER);
        add(choose, BorderLayout.EAST);
    }

    public void setFormats(DateFormats newFormats) {
        Optional<LocalDate> current = date();
        formats = newFormats;
        current.ifPresent(this::setDate);
    }

    public void setDate(LocalDate date) {
        text.setText(date == null ? "" : formats.date(date));
    }

    /** The date typed in, empty if the text is not a date. */
    public Optional<LocalDate> date() {
        return formats.parseDateLenient(text.getText());
    }

    public String getText() {
        return text.getText();
    }

    public void setText(String value) {
        text.setText(value);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        text.setEnabled(enabled);
        choose.setEnabled(enabled);
    }

    @Override
    public void setToolTipText(String tip) {
        text.setToolTipText(tip);
    }

    /** Focus goes to the text field, e.g. from a label's mnemonic. */
    @Override
    public boolean requestFocusInWindow() {
        return text.requestFocusInWindow();
    }

    @Override
    public void requestFocus() {
        text.requestFocus();
    }

    /** The text field, for labels and tests. */
    public JTextField textField() {
        return text;
    }
}
