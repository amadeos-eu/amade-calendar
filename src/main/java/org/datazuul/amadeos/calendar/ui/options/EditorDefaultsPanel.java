package org.datazuul.amadeos.calendar.ui.options;

import org.datazuul.amadeos.calendar.domain.CalendarPreferences;
import org.datazuul.amadeos.calendar.domain.Privacy;
import org.datazuul.amadeos.calendar.domain.ReminderType;
import org.datazuul.amadeos.calendar.service.DateFormats;
import org.datazuul.amadeos.calendar.ui.common.TimeField;
import org.datazuul.amadeos.commons.i18n.Mnemonics;

import javax.swing.*;
import java.awt.*;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

import static org.datazuul.amadeos.calendar.i18n.Texts.tr;

/** "Editor Defaults" category: duration, start, reminders and privacy of new appointments. */
final class EditorDefaultsPanel extends JPanel {
    private final JSpinner duration = new JSpinner(new SpinnerNumberModel(60, 0, 1440, 15));
    private final TimeField start;
    private final Map<ReminderType, JCheckBox> boxes = new EnumMap<>(ReminderType.class);
    private final Map<ReminderType, JSpinner> minutes = new EnumMap<>(ReminderType.class);
    private final JTextField mailTo = new JTextField(20);
    private final JComboBox<Privacy> privacy = new JComboBox<>(Privacy.values());

    EditorDefaultsPanel(DateFormats formats) {
        super(new GridBagLayout());
        setOpaque(false);
        start = new TimeField(formats);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 0, 3, 8);
        c.anchor = GridBagConstraints.LINE_START;
        c.gridy = 0;
        add(label("options.editor.duration", duration), c);
        c.gridx = 1;
        JPanel durationRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        durationRow.setOpaque(false);
        durationRow.add(duration);
        durationRow.add(new JLabel(tr("options.editor.minutes")));
        add(durationRow, c);
        c.gridx = 0;
        c.gridy++;
        add(label("options.editor.start", start), c);
        c.gridx = 1;
        add(start, c);
        c.gridx = 0;
        c.gridy++;
        c.anchor = GridBagConstraints.NORTHWEST;
        add(new JLabel(tr("editor.reminders")), c);
        c.gridx = 1;
        JPanel reminders = new JPanel(new GridBagLayout());
        reminders.setOpaque(false);
        GridBagConstraints r = new GridBagConstraints();
        r.anchor = GridBagConstraints.LINE_START;
        r.insets = new Insets(1, 0, 1, 6);
        r.gridy = 0;
        for (ReminderType type : ReminderType.values()) {
            JCheckBox box = new JCheckBox(tr("reminder." + type.name().toLowerCase(Locale.ROOT)));
            box.setOpaque(false);
            JSpinner lead = new JSpinner(new SpinnerNumberModel(5, 0, 1440, 1));
            lead.setEnabled(false);
            box.addActionListener(e -> lead.setEnabled(box.isSelected()));
            boxes.put(type, box);
            minutes.put(type, lead);
            r.gridx = 0;
            reminders.add(box, r);
            r.gridx = 1;
            reminders.add(lead, r);
            r.gridx = 2;
            reminders.add(new JLabel(tr("editor.minutes-before")), r);
            r.gridy++;
        }
        add(reminders, c);
        c.gridx = 0;
        c.gridy++;
        c.anchor = GridBagConstraints.LINE_START;
        add(label("editor.mail-to", mailTo), c);
        c.gridx = 1;
        add(mailTo, c);
        c.gridx = 0;
        c.gridy++;
        add(label("editor.privacy", privacy), c);
        c.gridx = 1;
        privacy.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean sel, boolean focus) {
                return super.getListCellRendererComponent(l, value == null ? ""
                        : tr("privacy." + ((Privacy) value).name().toLowerCase(Locale.ROOT)), index, sel, focus);
            }
        });
        add(privacy, c);
        c.gridy++;
        c.weighty = 1;
        add(Box.createVerticalGlue(), c);
    }

    private static JLabel label(String key, JComponent field) {
        JLabel label = Mnemonics.apply(new JLabel(), tr(key));
        label.setLabelFor(field);
        return label;
    }

    void setFormats(DateFormats formats) {
        start.setFormats(formats);
    }

    void set(CalendarPreferences p) {
        duration.setValue(p.defaultDurationMinutes());
        start.setTime(p.defaultStart());
        for (ReminderType type : ReminderType.values()) {
            Duration lead = p.defaultReminders().get(type);
            boxes.get(type).setSelected(lead != null);
            minutes.get(type).setEnabled(lead != null);
            minutes.get(type).setValue(lead == null ? 5 : (int) Math.min(1440, lead.toMinutes()));
        }
        mailTo.setText(p.defaultMailTo());
        privacy.setSelectedItem(p.defaultPrivacy());
    }

    /** The preferences with this page's values replaced. */
    CalendarPreferences apply(CalendarPreferences p) {
        Map<ReminderType, Duration> reminders = new EnumMap<>(ReminderType.class);
        for (ReminderType type : ReminderType.values()) {
            if (boxes.get(type).isSelected()) reminders.put(type, Duration.ofMinutes((int) minutes.get(type).getValue()));
        }
        return new CalendarPreferences((int) duration.getValue(), start.time().orElse(p.defaultStart()), reminders,
                mailTo.getText(), (Privacy) privacy.getSelectedItem(), p.dayBegin(), p.dayEnd(), p.twentyFourHour(),
                p.defaultView(), p.dateOrder(), p.dateSeparator());
    }
}
