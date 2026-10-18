package eu.amadeos.calendar.ui.editor;

import eu.amadeos.calendar.domain.ToDoItem;
import eu.amadeos.calendar.ui.common.DateField;
import eu.amadeos.calendar.ui.views.ViewContext;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.swing.AmadeUI;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.util.Optional;

import static eu.amadeos.calendar.i18n.Texts.tr;

/**
 * The To Do Editor of dtcm: the To Do items in a list, Due Date, What and Completed; Insert, Change,
 * Delete, Clear and Cancel.
 */
public final class ToDoEditor extends JDialog {
    public interface Handler {
        void insert(ToDoItem item);

        void change(ToDoItem item);

        void delete(ToDoItem item);
    }

    private final ViewContext context;
    private final DefaultListModel<ToDoItem> listModel = new DefaultListModel<>();
    private final JList<ToDoItem> list = new JList<>(listModel);
    private final DateField dueField;
    private final JTextArea what = new JTextArea(4, 32);
    private final JCheckBox completed = new JCheckBox(tr("todo.completed"));
    private final JButton change = new JButton(tr("editor.change"));
    private final JButton delete = new JButton(tr("editor.delete"));
    private LocalDate due = LocalDate.now();
    private ToDoItem selected;
    private boolean filling;

    public ToDoEditor(Frame owner, ViewContext context, Handler handler) {
        super(owner, tr("editor.todo.title"), false);
        this.context = context;
        dueField = new DateField(context.formats());

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        list.setVisibleRowCount(6);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean sel, boolean focus) {
                ToDoItem t = (ToDoItem) value;
                return super.getListCellRendererComponent(l, (t.completed() ? "☑ " : "☐ ")
                        + context.formats().date(t.due()) + "  " + t.summary(), index, sel, focus);
            }
        });
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !filling && list.getSelectedValue() != null) select(list.getSelectedValue());
        });
        JPanel top = new JPanel(new BorderLayout(0, 3));
        top.setOpaque(false);
        JLabel title = new JLabel(tr("todo.list-title"));
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        top.add(title, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(AmadeUI.sunken());
        top.add(scroll, BorderLayout.CENTER);
        content.add(top, BorderLayout.NORTH);

        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 0, 3, 8);
        c.anchor = GridBagConstraints.LINE_START;
        c.gridy = 0;
        JLabel dueLabel = Mnemonics.apply(new JLabel(), tr("todo.due"));
        dueLabel.setLabelFor(dueField.textField());
        fields.add(dueLabel, c);
        c.gridx = 1;
        fields.add(dueField, c);
        c.gridx = 0;
        c.gridy++;
        c.anchor = GridBagConstraints.NORTHWEST;
        JLabel whatLabel = Mnemonics.apply(new JLabel(), tr("editor.what"));
        whatLabel.setLabelFor(what);
        fields.add(whatLabel, c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        what.setLineWrap(true);
        what.setWrapStyleWord(true);
        JScrollPane whatScroll = new JScrollPane(what);
        whatScroll.setBorder(AmadeUI.sunken());
        fields.add(whatScroll, c);
        c.gridx = 1;
        c.gridy++;
        c.fill = GridBagConstraints.NONE;
        completed.setOpaque(false);
        fields.add(completed, c);
        content.add(fields, BorderLayout.CENTER);

        JButton insert = new JButton(tr("editor.insert"));
        JButton clear = new JButton(tr("editor.clear"));
        JButton cancel = new JButton(UIManager.getString("OptionPane.cancelButtonText"));
        insert.addActionListener(e -> read(ToDoItem.newUid()).ifPresent(t -> {
            handler.insert(t);
            select(t);
        }));
        change.addActionListener(e -> {
            if (selected == null) return;
            read(selected.uid()).ifPresent(t -> {
                handler.change(t);
                select(t);
            });
        });
        delete.addActionListener(e -> {
            if (selected == null) return;
            handler.delete(selected);
            clear();
        });
        clear.addActionListener(e -> clear());
        cancel.addActionListener(e -> setVisible(false));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        buttons.setOpaque(false);
        buttons.add(insert);
        buttons.add(change);
        buttons.add(delete);
        buttons.add(clear);
        buttons.add(cancel);
        content.add(buttons, BorderLayout.SOUTH);

        setContentPane(content);
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        pack();
        setLocationRelativeTo(owner);
    }

    public void showFor(LocalDate day, ToDoItem item) {
        due = day;
        reload();
        if (item != null) select(item);
        else clear();
        // The list's row height is only known once it has rows: size the dialog for it.
        if (!isVisible()) pack();
        setVisible(true);
        toFront();
    }

    public void refresh() {
        dueField.setFormats(context.formats());
        reload();
    }

    private void reload() {
        filling = true;
        try {
            listModel.clear();
            for (ToDoItem t : context.calendar().toDos()) listModel.addElement(t);
            if (selected != null) {
                Optional<ToDoItem> still = context.calendar().toDo(selected.uid());
                if (still.isEmpty()) selected = null;
                else selectInList(selected.uid());
            }
            change.setEnabled(selected != null);
            delete.setEnabled(selected != null);
        } finally {
            filling = false;
        }
    }

    private void selectInList(String uid) {
        for (int i = 0; i < listModel.size(); i++) {
            if (listModel.get(i).uid().equals(uid)) {
                list.setSelectedIndex(i);
                list.ensureIndexIsVisible(i);
            }
        }
    }

    private void select(ToDoItem t) {
        selected = t;
        filling = true;
        try {
            dueField.setDate(t.due());
            what.setText(t.what());
            completed.setSelected(t.completed());
            selectInList(t.uid());
            change.setEnabled(true);
            delete.setEnabled(true);
        } finally {
            filling = false;
        }
    }

    private void clear() {
        selected = null;
        filling = true;
        try {
            list.clearSelection();
            dueField.setDate(due);
            what.setText("");
            completed.setSelected(false);
            change.setEnabled(false);
            delete.setEnabled(false);
        } finally {
            filling = false;
        }
        what.requestFocusInWindow();
    }

    private Optional<ToDoItem> read(String uid) {
        Optional<LocalDate> day = dueField.date();
        if (day.isEmpty()) return fail(tr("editor.error.date", context.formats().date(java.time.LocalDate.now())));
        if (what.getText().isBlank()) return fail(tr("editor.error.what"));
        due = day.get();
        return Optional.of(new ToDoItem(uid, day.get(), what.getText(), completed.isSelected(),
                selected != null && selected.uid().equals(uid) ? selected.extra() : java.util.List.of()));
    }

    private Optional<ToDoItem> fail(String message) {
        JOptionPane.showMessageDialog(this, message, getTitle(), JOptionPane.ERROR_MESSAGE);
        return Optional.empty();
    }
}
