package eu.amadeos.calendar.ui.lists;

import eu.amadeos.calendar.domain.Occurrence;
import eu.amadeos.calendar.ui.views.ViewContext;
import eu.amadeos.commons.swing.AmadeUI;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;

import static eu.amadeos.calendar.i18n.Texts.tr;

/**
 * The Appointment List of the View menu: the appointments of the current view's period in order,
 * with date, time and description. A double-click opens the appointment in the editor.
 */
public final class AppointmentListDialog extends JDialog {
    private final Model model = new Model();
    private final ViewContext context;

    public AppointmentListDialog(Frame owner, ViewContext context, Consumer<Occurrence> open) {
        super(owner, tr("list.appointments.title"), false);
        this.context = context;
        JTable table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(0).setPreferredWidth(110);
        table.getColumnModel().getColumn(1).setPreferredWidth(140);
        table.getColumnModel().getColumn(2).setPreferredWidth(360);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                if (e.getClickCount() == 2 && row >= 0) open.accept(model.rows.get(table.convertRowIndexToModel(row)));
            }
        });
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(AmadeUI.sunken());
        scroll.setPreferredSize(new Dimension(640, 320));

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        content.add(scroll, BorderLayout.CENTER);
        JButton close = new JButton(tr("dialog.close"));
        close.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttons.setOpaque(false);
        buttons.add(close);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        getRootPane().setDefaultButton(close);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        pack();
        setLocationRelativeTo(owner);
    }

    public void show(String period, List<Occurrence> occurrences) {
        setTitle(tr("list.appointments.title.with", period));
        model.rows = occurrences;
        model.fireTableDataChanged();
        setVisible(true);
        toFront();
    }

    private final class Model extends AbstractTableModel {
        List<Occurrence> rows = List.of();

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
                case 0 -> tr("list.column.date");
                case 1 -> tr("list.column.time");
                default -> tr("list.column.what");
            };
        }

        @Override
        public Object getValueAt(int row, int column) {
            Occurrence o = rows.get(row);
            return switch (column) {
                case 0 -> context.formats().date(o.date());
                case 1 -> context.formats().timeRange(o.appointment().start(), o.appointment().end());
                default -> o.appointment().summary();
            };
        }
    }
}
