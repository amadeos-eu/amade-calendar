package org.datazuul.amadeos.calendar.ui.editor;

import org.datazuul.amadeos.calendar.domain.Appointment;
import org.datazuul.amadeos.calendar.domain.Occurrence;
import org.datazuul.amadeos.calendar.domain.Privacy;
import org.datazuul.amadeos.calendar.domain.Reminder;
import org.datazuul.amadeos.calendar.domain.ReminderType;
import org.datazuul.amadeos.calendar.domain.Repeat;
import org.datazuul.amadeos.calendar.domain.RepeatPreset;
import org.datazuul.amadeos.calendar.ui.common.DateField;
import org.datazuul.amadeos.calendar.ui.common.TimeField;
import org.datazuul.amadeos.calendar.ui.views.ViewContext;
import org.datazuul.amadeos.commons.i18n.Mnemonics;
import org.datazuul.amadeos.commons.swing.AmadeUI;

import javax.swing.*;
import java.awt.*;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.datazuul.amadeos.calendar.i18n.Texts.tr;

/**
 * The Appointment Editor of dtcm: the day's appointments in a list, the fields Date, Start, End and
 * What, behind "More" the reminders, the repeat (Occurs / For) and the privacy; Insert, Change,
 * Delete, Clear and Cancel.
 */
public final class AppointmentEditor extends JDialog {
    public interface Handler {
        void insert(Appointment appointment);

        void change(Appointment appointment);

        void delete(Appointment appointment);

        /** The Mail button: hand the appointment to the mailer. */
        void mail(Appointment appointment);
    }

    private final ViewContext context;
    private final Handler handler;
    private final DefaultListModel<Occurrence> listModel = new DefaultListModel<>();
    private final JList<Occurrence> list = new JList<>(listModel);
    private final JLabel listTitle = new JLabel(" ");
    private final DateField dateField;
    private final TimeField startField;
    private final TimeField endField;
    private final JTextArea what = new JTextArea(4, 32);
    private final JPanel more = new JPanel(new GridBagLayout());
    private final JButton moreButton = new JButton();
    private final Map<ReminderType, JCheckBox> reminderBoxes = new EnumMap<>(ReminderType.class);
    private final Map<ReminderType, JSpinner> reminderMinutes = new EnumMap<>(ReminderType.class);
    private final JTextField mailTo = new JTextField(20);
    private final JComboBox<RepeatPreset> occurs = new JComboBox<>(RepeatPreset.values());
    private final JComboBox<String> forTimes = new JComboBox<>(new String[]{"", "2", "3", "4", "5", "10", "12", "52"});
    private final JSpinner every = new JSpinner(new SpinnerNumberModel(1, 1, 999, 1));
    private final JComboBox<RepeatPreset.Unit> everyUnit = new JComboBox<>(RepeatPreset.Unit.values());
    private final JComboBox<Privacy> privacy = new JComboBox<>(Privacy.values());
    private final JButton change = new JButton(tr("editor.change"));
    private final JButton delete = new JButton(tr("editor.delete"));
    private LocalDate date = LocalDate.now();
    private Appointment selected;
    private boolean filling;

    public AppointmentEditor(Frame owner, ViewContext context, Handler handler) {
        super(owner, tr("editor.appointment.title"), false);
        this.context = context;
        this.handler = handler;
        dateField = new DateField(context.formats());
        startField = new TimeField(context.formats());
        endField = new TimeField(context.formats());

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        // The day's appointments.
        list.setVisibleRowCount(5);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean sel, boolean focus) {
                Occurrence o = (Occurrence) value;
                String time = context.formats().timeRange(o.appointment().start(), o.appointment().end());
                return super.getListCellRendererComponent(l, (time.isEmpty() ? "" : time + "  ")
                        + o.appointment().summary(), index, sel, focus);
            }
        });
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !filling) {
                Occurrence o = list.getSelectedValue();
                if (o != null) select(o.appointment());
            }
        });
        JPanel top = new JPanel(new BorderLayout(0, 3));
        top.setOpaque(false);
        listTitle.setFont(listTitle.getFont().deriveFont(Font.BOLD));
        top.add(listTitle, BorderLayout.NORTH);
        JScrollPane listScroll = new JScrollPane(list);
        listScroll.setBorder(AmadeUI.sunken());
        top.add(listScroll, BorderLayout.CENTER);
        content.add(top, BorderLayout.NORTH);

        // Fields.
        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 0, 3, 8);
        c.anchor = GridBagConstraints.LINE_START;
        c.gridy = 0;
        fields.add(label("editor.date", dateField), c);
        c.gridx = 1;
        fields.add(dateField, c);
        c.gridx = 0;
        c.gridy++;
        fields.add(label("editor.start", startField), c);
        c.gridx = 1;
        JPanel times = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        times.setOpaque(false);
        times.add(startField);
        times.add(label("editor.end", endField));
        times.add(endField);
        fields.add(times, c);
        c.gridx = 0;
        c.gridy++;
        c.anchor = GridBagConstraints.NORTHWEST;
        fields.add(label("editor.what", what), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        what.setLineWrap(true);
        what.setWrapStyleWord(true);
        JScrollPane whatScroll = new JScrollPane(what);
        whatScroll.setBorder(AmadeUI.sunken());
        fields.add(whatScroll, c);
        c.gridx = 0;
        c.gridy++;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        c.insets = new Insets(8, 0, 3, 0);
        moreButton.addActionListener(e -> setMoreVisible(!more.isVisible()));
        fields.add(moreButton, c);
        c.gridy++;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.insets = new Insets(0, 0, 0, 0);
        buildMore();
        fields.add(more, c);
        content.add(fields, BorderLayout.CENTER);

        // Buttons.
        JButton insert = new JButton(tr("editor.insert"));
        JButton clear = new JButton(tr("editor.clear"));
        JButton cancel = new JButton(UIManager.getString("OptionPane.cancelButtonText"));
        insert.addActionListener(e -> read(Appointment.newUid()).ifPresent(a -> {
            handler.insert(a);
            select(a);
        }));
        change.addActionListener(e -> {
            if (selected == null) return;
            read(selected.uid()).ifPresent(a -> {
                if (selected.repeat().repeats() && !confirm(tr("editor.change.repeating"))) return;
                handler.change(a);
                select(a);
            });
        });
        delete.addActionListener(e -> {
            if (selected == null) return;
            if (selected.repeat().repeats() && !confirm(tr("editor.delete.repeating"))) return;
            handler.delete(selected);
            clear();
        });
        clear.addActionListener(e -> clear());
        cancel.addActionListener(e -> setVisible(false));
        JButton mail = new JButton(tr("editor.mail"));
        mail.setToolTipText(tr("editor.mail.tip"));
        mail.addActionListener(e -> read(selected != null ? selected.uid() : Appointment.newUid()).ifPresent(handler::mail));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        buttons.setOpaque(false);
        buttons.add(insert);
        buttons.add(change);
        buttons.add(delete);
        buttons.add(clear);
        buttons.add(mail);
        buttons.add(cancel);
        JPanel bottom = new JPanel(new BorderLayout(8, 0));
        bottom.setOpaque(false);
        bottom.add(dragIcon(), BorderLayout.WEST);
        bottom.add(buttons, BorderLayout.CENTER);
        content.add(bottom, BorderLayout.SOUTH);

        setContentPane(content);
        setMoreVisible(false);
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        pack();
        setLocationRelativeTo(owner);
    }

    /**
     * The Drag Appointment icon of dtcm: drag it onto a file manager, a mail attachment list or a text field
     * to hand the appointment over as an iCalendar file or as dtcm text.
     */
    private JLabel dragIcon() {
        Image image = new ImageIcon(getClass().getResource("/icons/amade-calendar.png")).getImage()
                .getScaledInstance(24, 24, Image.SCALE_SMOOTH);
        JLabel label = new JLabel(tr("editor.drag"), new ImageIcon(image), SwingConstants.LEFT);
        label.setToolTipText(tr("editor.drag.tip"));
        label.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        label.setTransferHandler(new TransferHandler() {
            @Override
            public int getSourceActions(JComponent c) {
                return COPY;
            }

            @Override
            protected java.awt.datatransfer.Transferable createTransferable(JComponent c) {
                return read(selected != null ? selected.uid() : Appointment.newUid())
                        .map(a -> new AppointmentTransferable(a, context.formats())).orElse(null);
            }
        });
        label.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseDragged(java.awt.event.MouseEvent e) {
                label.getTransferHandler().exportAsDrag(label, e, TransferHandler.COPY);
            }
        });
        return label;
    }

    private void buildMore() {
        more.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 0, 3, 8);
        c.anchor = GridBagConstraints.LINE_START;
        c.gridy = 0;
        c.gridx = 0;
        more.add(label("editor.occurs", occurs), c);
        c.gridx = 1;
        JPanel repeatRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        repeatRow.setOpaque(false);
        occurs.setRenderer(enumRenderer("repeat."));
        occurs.addActionListener(e -> updateRepeatFields());
        repeatRow.add(occurs);
        repeatRow.add(label("editor.for", forTimes));
        forTimes.setEditable(true);
        forTimes.setPrototypeDisplayValue("forever  ");
        repeatRow.add(forTimes);
        repeatRow.add(new JLabel(tr("editor.times")));
        more.add(repeatRow, c);
        c.gridx = 0;
        c.gridy++;
        more.add(label("editor.every", every), c);
        c.gridx = 1;
        JPanel everyRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        everyRow.setOpaque(false);
        everyRow.add(every);
        everyUnit.setRenderer(enumRenderer("unit."));
        everyRow.add(everyUnit);
        more.add(everyRow, c);

        c.gridx = 0;
        c.gridy++;
        c.anchor = GridBagConstraints.NORTHWEST;
        more.add(new JLabel(tr("editor.reminders")), c);
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
            JSpinner minutes = new JSpinner(new SpinnerNumberModel(5, 0, 1440, 1));
            box.addActionListener(e -> minutes.setEnabled(box.isSelected()));
            minutes.setEnabled(false);
            reminderBoxes.put(type, box);
            reminderMinutes.put(type, minutes);
            r.gridx = 0;
            reminders.add(box, r);
            r.gridx = 1;
            reminders.add(minutes, r);
            r.gridx = 2;
            reminders.add(new JLabel(tr("editor.minutes-before")), r);
            r.gridy++;
        }
        r.gridx = 0;
        r.gridwidth = 3;
        JPanel mailRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        mailRow.setOpaque(false);
        mailRow.add(label("editor.mail-to", mailTo));
        mailRow.add(mailTo);
        reminders.add(mailRow, r);
        more.add(reminders, c);

        c.gridx = 0;
        c.gridy++;
        c.anchor = GridBagConstraints.LINE_START;
        more.add(label("editor.privacy", privacy), c);
        c.gridx = 1;
        privacy.setRenderer(enumRenderer("privacy."));
        more.add(privacy, c);
    }

    private static <E extends Enum<E>> ListCellRenderer<E> enumRenderer(String prefix) {
        return new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean sel, boolean focus) {
                String text = value == null ? "" : tr(prefix + ((Enum<?>) value).name().toLowerCase(Locale.ROOT));
                return super.getListCellRendererComponent(l, text, index, sel, focus);
            }
        }::getListCellRendererComponent;
    }

    private static JLabel label(String key, JComponent field) {
        JLabel label = Mnemonics.apply(new JLabel(), tr(key));
        label.setLabelFor(field);
        return label;
    }

    private void setMoreVisible(boolean visible) {
        more.setVisible(visible);
        moreButton.setText(tr(visible ? "editor.less" : "editor.more"));
        pack();
    }

    private void updateRepeatFields() {
        RepeatPreset preset = (RepeatPreset) occurs.getSelectedItem();
        boolean repeats = preset != null && preset != RepeatPreset.ONE_TIME;
        forTimes.setEnabled(repeats);
        every.setEnabled(preset == RepeatPreset.REPEAT_EVERY);
        everyUnit.setEnabled(preset == RepeatPreset.REPEAT_EVERY);
    }

    // ---- showing --------------------------------------------------------------------------------

    /** Opens the editor on a day; {@code time} fills the Start field, {@code appointment} is selected. */
    public void showFor(LocalDate day, LocalTime time, Appointment appointment) {
        date = day;
        reload();
        if (appointment != null) select(appointment);
        else clear(time);
        // The list's row height is only known once it has rows: size the dialog for it.
        if (!isVisible()) pack();
        setVisible(true);
        toFront();
    }

    /** Re-reads the list after the calendar changed; keeps the selected appointment if it still exists. */
    public void refresh() {
        dateField.setFormats(context.formats());
        startField.setFormats(context.formats());
        endField.setFormats(context.formats());
        reload();
    }

    private void reload() {
        filling = true;
        try {
            listTitle.setText(tr("editor.appointments-on", context.formats().longDate(date)));
            listModel.clear();
            for (Occurrence o : context.calendar().occurrencesOn(date)) listModel.addElement(o);
            if (selected != null) {
                Optional<Appointment> still = context.calendar().appointment(selected.uid());
                if (still.isEmpty()) {
                    selected = null;
                } else {
                    for (int i = 0; i < listModel.size(); i++) {
                        if (listModel.get(i).appointment().uid().equals(selected.uid())) list.setSelectedIndex(i);
                    }
                }
            }
            change.setEnabled(selected != null);
            delete.setEnabled(selected != null);
        } finally {
            filling = false;
        }
    }

    private void select(Appointment a) {
        selected = a;
        filling = true;
        try {
            dateField.setDate(a.date());
            startField.setTime(a.start());
            endField.setTime(a.end());
            what.setText(a.what());
            RepeatPreset preset = RepeatPreset.of(a.repeat());
            occurs.setSelectedItem(preset);
            forTimes.setSelectedItem(a.repeat().count() == 0 ? "" : Integer.toString(a.repeat().count()));
            every.setValue(a.repeat().interval());
            everyUnit.setSelectedItem(RepeatPreset.unitOf(a.repeat()));
            for (ReminderType type : ReminderType.values()) {
                reminderBoxes.get(type).setSelected(false);
                reminderMinutes.get(type).setEnabled(false);
            }
            for (Reminder r : a.reminders()) {
                reminderBoxes.get(r.type()).setSelected(true);
                reminderMinutes.get(r.type()).setValue((int) Math.min(1440, r.minutes()));
                reminderMinutes.get(r.type()).setEnabled(true);
                if (r.type() == ReminderType.MAIL) mailTo.setText(r.mailTo());
            }
            privacy.setSelectedItem(a.privacy());
            updateRepeatFields();
            if (a.repeat().repeats() || !a.reminders().isEmpty()) setMoreVisible(true);
            for (int i = 0; i < listModel.size(); i++) {
                if (listModel.get(i).appointment().uid().equals(a.uid())) list.setSelectedIndex(i);
            }
            change.setEnabled(true);
            delete.setEnabled(true);
        } finally {
            filling = false;
        }
    }

    /** Resets the fields to the editor defaults for the day, as the Clear button does. */
    private void clear() {
        clear(null);
    }

    private void clear(LocalTime time) {
        selected = null;
        filling = true;
        try {
            list.clearSelection();
            dateField.setDate(date);
            LocalTime start = time != null ? time : context.prefs().defaultStart();
            startField.setTime(start);
            int minutes = context.prefs().defaultDurationMinutes();
            endField.setTime(minutes > 0 && start.plusMinutes(minutes).isAfter(start) ? start.plusMinutes(minutes) : null);
            what.setText("");
            occurs.setSelectedItem(RepeatPreset.ONE_TIME);
            forTimes.setSelectedItem("");
            every.setValue(1);
            everyUnit.setSelectedItem(RepeatPreset.Unit.DAYS);
            for (ReminderType type : ReminderType.values()) {
                Duration lead = context.prefs().defaultReminders().get(type);
                reminderBoxes.get(type).setSelected(lead != null);
                reminderMinutes.get(type).setEnabled(lead != null);
                reminderMinutes.get(type).setValue(lead == null ? 5 : (int) Math.min(1440, lead.toMinutes()));
            }
            mailTo.setText(context.prefs().defaultMailTo());
            privacy.setSelectedItem(context.prefs().defaultPrivacy());
            updateRepeatFields();
            change.setEnabled(false);
            delete.setEnabled(false);
        } finally {
            filling = false;
        }
        what.requestFocusInWindow();
    }

    // ---- reading --------------------------------------------------------------------------------

    private Optional<Appointment> read(String uid) {
        Optional<LocalDate> day = dateField.date();
        if (day.isEmpty()) return fail(tr("editor.error.date", context.formats().date(java.time.LocalDate.now())));
        LocalTime start = null;
        LocalTime end = null;
        if (!startField.isBlank()) {
            Optional<LocalTime> t = startField.time();
            if (t.isEmpty()) return fail(tr("editor.error.start"));
            start = t.get();
            if (!endField.isBlank()) {
                Optional<LocalTime> e = endField.time();
                if (e.isEmpty()) return fail(tr("editor.error.end"));
                end = e.get();
                if (!end.isAfter(start)) return fail(tr("editor.error.end-before-start"));
            }
        } else if (!endField.isBlank()) {
            return fail(tr("editor.error.end-without-start"));
        }
        if (what.getText().isBlank()) return fail(tr("editor.error.what"));
        List<Reminder> reminders = new ArrayList<>();
        for (ReminderType type : ReminderType.values()) {
            if (!reminderBoxes.get(type).isSelected()) continue;
            if (start == null) return fail(tr("editor.error.reminder-without-time"));
            Duration lead = Duration.ofMinutes((int) reminderMinutes.get(type).getValue());
            reminders.add(new Reminder(type, lead, type == ReminderType.MAIL ? mailTo.getText() : ""));
        }
        RepeatPreset preset = (RepeatPreset) occurs.getSelectedItem();
        int count = 0;
        Object times = forTimes.getEditor().getItem();
        if (times != null && !times.toString().isBlank()) {
            try {
                count = Integer.parseInt(times.toString().strip());
            } catch (NumberFormatException e) {
                count = 0; // "forever" or anything else
            }
            if (count < 0) count = 0;
        }
        Repeat repeat = preset == null ? Repeat.ONCE
                : preset.toRepeat(count, (int) every.getValue(), (RepeatPreset.Unit) everyUnit.getSelectedItem());
        if (preset == RepeatPreset.REPEAT_EVERY && selected != null && RepeatPreset.of(selected.repeat()) == RepeatPreset.REPEAT_EVERY
                && selected.uid().equals(uid)) {
            // Keep details the menu cannot express (UNTIL, odd weekday sets) of a rule read from a file.
            Repeat old = selected.repeat();
            if (old.until() != null || !old.byDay().isEmpty()) repeat = old.withCount(count);
        }
        Appointment a = new Appointment(uid, day.get(), start, end, what.getText(), reminders, repeat,
                (Privacy) privacy.getSelectedItem(), selected != null && selected.uid().equals(uid) ? selected.extra() : List.of());
        date = day.get();
        return Optional.of(a);
    }

    private Optional<Appointment> fail(String message) {
        JOptionPane.showMessageDialog(this, message, getTitle(), JOptionPane.ERROR_MESSAGE);
        return Optional.empty();
    }

    private boolean confirm(String message) {
        return JOptionPane.showConfirmDialog(this, message, getTitle(), JOptionPane.OK_CANCEL_OPTION)
                == JOptionPane.OK_OPTION;
    }
}
