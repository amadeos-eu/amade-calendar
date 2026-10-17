package org.datazuul.amadeos.calendar.ui.lists;

import org.datazuul.amadeos.calendar.domain.ToDoItem;
import org.datazuul.amadeos.calendar.ui.views.ViewContext;
import org.datazuul.amadeos.commons.swing.AmadeUI;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.datazuul.amadeos.calendar.i18n.Texts.tr;

/**
 * The To Do List of the View menu: a check box, the due date and the description per item. The filter
 * shows all, only pending or only completed items; OK and Apply hand the changed check boxes back.
 */
public final class ToDoListDialog extends JDialog {
    private enum Filter { ALL, PENDING, COMPLETED }

    private final Model model = new Model();
    private final JComboBox<Filter> filter = new JComboBox<>(Filter.values());
    private final ViewContext context;
    private List<ToDoItem> all = List.of();
    private final Map<String, Boolean> changed = new HashMap<>();

    public ToDoListDialog(Frame owner, ViewContext context, Consumer<Map<String, Boolean>> apply) {
        super(owner, tr("list.todos.title"), false);
        this.context = context;
        JTable table = new JTable(model);
        table.getColumnModel().getColumn(0).setMaxWidth(40);
        table.getColumnModel().getColumn(1).setPreferredWidth(110);
        table.getColumnModel().getColumn(2).setPreferredWidth(400);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(AmadeUI.sunken());
        scroll.setPreferredSize(new Dimension(600, 300));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        top.setOpaque(false);
        top.add(new JLabel(tr("list.todos.show")));
        filter.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean sel, boolean focus) {
                return super.getListCellRendererComponent(l, value == null ? "" : tr("list.todos.filter."
                        + ((Filter) value).name().toLowerCase(java.util.Locale.ROOT)), index, sel, focus);
            }
        });
        filter.addActionListener(e -> model.refilter());
        top.add(filter);

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        content.add(top, BorderLayout.NORTH);
        content.add(scroll, BorderLayout.CENTER);
        JButton ok = new JButton(UIManager.getString("OptionPane.okButtonText"));
        JButton applyButton = new JButton(tr("options.apply"));
        JButton cancel = new JButton(UIManager.getString("OptionPane.cancelButtonText"));
        ok.addActionListener(e -> {
            apply.accept(new HashMap<>(changed));
            dispose();
        });
        applyButton.addActionListener(e -> {
            apply.accept(new HashMap<>(changed));
            changed.clear();
        });
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        buttons.setOpaque(false);
        buttons.add(ok);
        buttons.add(applyButton);
        buttons.add(cancel);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        pack();
        setLocationRelativeTo(owner);
    }

    public void show(String period, List<ToDoItem> items) {
        setTitle(tr("list.todos.title.with", period));
        all = items;
        changed.clear();
        model.refilter();
        setVisible(true);
        toFront();
    }

    private final class Model extends AbstractTableModel {
        List<ToDoItem> rows = new ArrayList<>();

        void refilter() {
            Filter f = (Filter) filter.getSelectedItem();
            rows = new ArrayList<>();
            for (ToDoItem t : all) {
                boolean done = changed.getOrDefault(t.uid(), t.completed());
                if (f == Filter.ALL || (f == Filter.PENDING) != done) rows.add(t);
            }
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return 3;
        }

        @Override
        public String getColumnName(int column) {
            return switch (column) {
                case 0 -> "";
                case 1 -> tr("list.column.due");
                default -> tr("list.column.what");
            };
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == 0 ? Boolean.class : String.class;
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 0;
        }

        @Override
        public Object getValueAt(int row, int column) {
            ToDoItem t = rows.get(row);
            return switch (column) {
                case 0 -> changed.getOrDefault(t.uid(), t.completed());
                case 1 -> context.formats().date(t.due());
                default -> t.summary();
            };
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            ToDoItem t = rows.get(row);
            boolean done = Boolean.TRUE.equals(value);
            if (done == t.completed()) changed.remove(t.uid());
            else changed.put(t.uid(), done);
            fireTableCellUpdated(row, column);
        }
    }
}
