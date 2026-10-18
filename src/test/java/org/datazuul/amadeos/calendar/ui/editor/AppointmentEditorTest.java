package org.datazuul.amadeos.calendar.ui.editor;

import org.datazuul.amadeos.calendar.domain.Appointment;
import org.datazuul.amadeos.calendar.domain.CalendarPreferences;
import org.datazuul.amadeos.calendar.domain.Privacy;
import org.datazuul.amadeos.calendar.domain.RepeatPreset;
import org.datazuul.amadeos.calendar.service.CalendarService;
import org.datazuul.amadeos.calendar.service.DateFormats;
import org.datazuul.amadeos.calendar.ui.views.ViewContext;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class AppointmentEditorTest {
    @Test
    void insertReadsTheFieldsAndChangeKeepsTheUid() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        List<Appointment> inserted = new ArrayList<>();
        List<Appointment> changed = new ArrayList<>();
        CalendarService calendar = new CalendarService();
        CalendarPreferences prefs = CalendarPreferences.defaultsFor(Locale.US);
        ViewContext context = new ViewContext(calendar, new DateFormats(prefs, Locale.US), prefs);
        SwingUtilities.invokeAndWait(() -> {
            JFrame owner = new JFrame();
            AppointmentEditor editor = new AppointmentEditor(owner, context, new AppointmentEditor.Handler() {
                @Override
                public void insert(Appointment appointment) {
                    inserted.add(appointment);
                    calendar.put(appointment);
                }

                @Override
                public void change(Appointment appointment) {
                    changed.add(appointment);
                    calendar.put(appointment);
                }

                @Override
                public void delete(Appointment appointment) {
                }

                @Override
                public void mail(Appointment appointment) {
                }
            });
            try {
                editor.showFor(LocalDate.of(2026, 10, 13), LocalTime.of(14, 0), null);
                assertEquals("10/13/2026", ((JTextField) field(editor, "dateField")).getText());
                ((JTextArea) field(editor, "what")).setText("Staff meeting\nMonet room");
                @SuppressWarnings("unchecked")
                JComboBox<RepeatPreset> occurs = (JComboBox<RepeatPreset>) field(editor, "occurs");
                occurs.setSelectedItem(RepeatPreset.WEEKLY);
                click(editor, "Insert");
                assertEquals(1, inserted.size());
                Appointment a = inserted.getFirst();
                assertEquals(LocalTime.of(14, 0), a.start());
                assertEquals(LocalTime.of(15, 0), a.end(), "default duration of one hour");
                assertEquals("Staff meeting", a.summary());
                assertEquals(RepeatPreset.WEEKLY, RepeatPreset.of(a.repeat()));
                assertEquals(Privacy.TIME_ONLY, a.privacy(), "dtcm's default privacy");

                ((JTextArea) field(editor, "what")).setText("Staff meeting moved");
                editor.refresh();
                // Change asks about the repeating appointment; answer through the default option is not
                // possible here, so change a one-time copy instead.
                occurs.setSelectedItem(RepeatPreset.ONE_TIME);
                click(editor, "Insert");
                Appointment once = inserted.get(1);
                ((JTextArea) field(editor, "what")).setText("Renamed");
                click(editor, "Change");
                assertEquals(1, changed.size());
                assertEquals(once.uid(), changed.getFirst().uid());
                assertEquals("Renamed", changed.getFirst().what());
            } finally {
                editor.dispose();
                owner.dispose();
            }
        });
    }

    private static Object field(Object target, String name) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void click(Container root, String text) {
        for (Component c : root.getComponents()) {
            if (c instanceof JButton b && text.equals(b.getText())) {
                b.doClick();
                return;
            }
            if (c instanceof Container inner) {
                try {
                    click(inner, text);
                    return;
                } catch (AssertionError ignored) {
                    // not in this branch
                }
            }
        }
        throw new AssertionError("No button " + text);
    }
}
