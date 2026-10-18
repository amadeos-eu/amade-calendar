package org.datazuul.amadeos.calendar.ui.lists;

import org.datazuul.amadeos.calendar.domain.Occurrence;
import org.datazuul.amadeos.calendar.ui.common.DateField;
import org.datazuul.amadeos.calendar.ui.views.ViewContext;
import org.datazuul.amadeos.commons.i18n.Mnemonics;
import org.datazuul.amadeos.commons.swing.AmadeUI;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.datazuul.amadeos.calendar.i18n.Texts.tr;

/**
 * The Find dialog of the View menu: a text, by default searched six months back and ahead, "Search
 * all" for the whole calendar, the matches in a list and "Show Appointment" to open one.
 */
public final class FindDialog extends JDialog {
    public interface Finder {
        List<Occurrence> find(String text, LocalDate from, LocalDate to);
    }

    private final JTextField text = new JTextField(24);
    private final DateField from;
    private final DateField to;
    private final JCheckBox searchAll = new JCheckBox(tr("find.search-all"));
    private final DefaultListModel<Occurrence> model = new DefaultListModel<>();
    private final JList<Occurrence> results = new JList<>(model);
    private final JLabel status = new JLabel(" ");
    private final ViewContext context;

    public FindDialog(Frame owner, ViewContext context, Finder finder, Consumer<Occurrence> show) {
        super(owner, tr("find.title"), false);
        this.context = context;
        from = new DateField(context.formats());
        to = new DateField(context.formats());
        from.setDate(LocalDate.now().minusMonths(6));
        to.setDate(LocalDate.now().plusMonths(6));
        searchAll.setOpaque(false);
        searchAll.addActionListener(e -> {
            from.setEnabled(!searchAll.isSelected());
            to.setEnabled(!searchAll.isSelected());
        });

        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 0, 3, 8);
        c.anchor = GridBagConstraints.LINE_START;
        c.gridy = 0;
        JLabel findLabel = Mnemonics.apply(new JLabel(), tr("find.text"));
        findLabel.setLabelFor(text);
        fields.add(findLabel, c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        fields.add(text, c);
        c.gridx = 0;
        c.gridy++;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        fields.add(new JLabel(tr("find.from")), c);
        c.gridx = 1;
        JPanel range = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        range.setOpaque(false);
        range.add(from);
        range.add(Box.createHorizontalStrut(6));
        range.add(new JLabel(tr("find.to")));
        range.add(Box.createHorizontalStrut(6));
        range.add(to);
        range.add(Box.createHorizontalStrut(6));
        range.add(searchAll);
        fields.add(range, c);

        results.setVisibleRowCount(8);
        results.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean sel, boolean focus) {
                Occurrence o = (Occurrence) value;
                String time = context.formats().timeRange(o.appointment().start(), o.appointment().end());
                return super.getListCellRendererComponent(l, context.formats().date(o.date()) + "  "
                        + (time.isEmpty() ? "" : time + "  ") + o.appointment().summary(), index, sel, focus);
            }
        });
        JScrollPane scroll = new JScrollPane(results);
        scroll.setBorder(AmadeUI.sunken());
        scroll.setPreferredSize(new Dimension(560, 220));

        JButton find = new JButton(tr("find.find"));
        JButton showButton = new JButton(tr("find.show"));
        JButton close = new JButton(tr("dialog.close"));
        Runnable search = () -> {
            String needle = text.getText().strip();
            if (needle.isEmpty()) return;
            LocalDate start;
            LocalDate end;
            if (searchAll.isSelected()) {
                start = LocalDate.of(1900, 1, 1);
                end = LocalDate.of(2200, 12, 31);
            } else {
                Optional<LocalDate> f = from.date();
                Optional<LocalDate> t = to.date();
                if (f.isEmpty() || t.isEmpty() || t.get().isBefore(f.get())) {
                    JOptionPane.showMessageDialog(this, tr("find.error.range"), getTitle(), JOptionPane.ERROR_MESSAGE);
                    return;
                }
                start = f.get();
                end = t.get();
            }
            model.clear();
            for (Occurrence o : finder.find(needle, start, end)) model.addElement(o);
            status.setText(tr("find.matches", model.size()));
            if (!model.isEmpty()) results.setSelectedIndex(0);
        };
        find.addActionListener(e -> search.run());
        text.addActionListener(e -> search.run());
        showButton.addActionListener(e -> {
            Occurrence o = results.getSelectedValue();
            if (o != null) show.accept(o);
        });
        results.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && results.getSelectedValue() != null) show.accept(results.getSelectedValue());
            }
        });
        close.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        buttons.setOpaque(false);
        buttons.add(find);
        buttons.add(showButton);
        buttons.add(close);

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        content.add(fields, BorderLayout.NORTH);
        JPanel middle = new JPanel(new BorderLayout(0, 4));
        middle.setOpaque(false);
        middle.add(scroll, BorderLayout.CENTER);
        middle.add(status, BorderLayout.SOUTH);
        content.add(middle, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        getRootPane().setDefaultButton(find);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        pack();
        setLocationRelativeTo(owner);
    }

    public void open() {
        setVisible(true);
        toFront();
        text.requestFocusInWindow();
        text.selectAll();
    }
}
