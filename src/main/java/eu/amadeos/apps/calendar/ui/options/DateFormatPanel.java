package eu.amadeos.apps.calendar.ui.options;

import eu.amadeos.apps.calendar.domain.CalendarPreferences;
import eu.amadeos.apps.calendar.domain.DateOrder;

import javax.swing.*;
import java.awt.*;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static eu.amadeos.apps.calendar.i18n.Texts.tr;

/** "Date Format" category: the order of day, month and year and the separator. */
final class DateFormatPanel extends JPanel {
    private final Map<DateOrder, JRadioButton> orders = new EnumMap<>(DateOrder.class);
    private final Map<Character, JRadioButton> separators = new LinkedHashMap<>();
    private final JLabel example = new JLabel();

    DateFormatPanel() {
        super(new GridBagLayout());
        setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 0, 3, 8);
        c.anchor = GridBagConstraints.NORTHWEST;
        c.gridy = 0;
        add(new JLabel(tr("options.date.order")), c);
        c.gridx = 1;
        JPanel orderColumn = new JPanel(new GridLayout(0, 1));
        orderColumn.setOpaque(false);
        ButtonGroup orderGroup = new ButtonGroup();
        for (DateOrder o : DateOrder.values()) {
            JRadioButton b = new JRadioButton(tr("options.date.order." + o.name().toLowerCase(java.util.Locale.ROOT)));
            b.setOpaque(false);
            b.addActionListener(e -> updateExample());
            orderGroup.add(b);
            orderColumn.add(b);
            orders.put(o, b);
        }
        add(orderColumn, c);
        c.gridx = 0;
        c.gridy++;
        add(new JLabel(tr("options.date.separator")), c);
        c.gridx = 1;
        JPanel separatorRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        separatorRow.setOpaque(false);
        ButtonGroup separatorGroup = new ButtonGroup();
        for (char s : new char[]{'/', '-', '.'}) {
            JRadioButton b = new JRadioButton(String.valueOf(s));
            b.setOpaque(false);
            b.addActionListener(e -> updateExample());
            separatorGroup.add(b);
            if (separatorRow.getComponentCount() > 0) separatorRow.add(Box.createHorizontalStrut(10));
            separatorRow.add(b);
            separators.put(s, b);
        }
        add(separatorRow, c);
        c.gridx = 0;
        c.gridy++;
        add(new JLabel(tr("options.date.example")), c);
        c.gridx = 1;
        add(example, c);
        c.gridy++;
        c.weighty = 1;
        add(Box.createVerticalGlue(), c);
    }

    private DateOrder order() {
        for (Map.Entry<DateOrder, JRadioButton> e : orders.entrySet()) if (e.getValue().isSelected()) return e.getKey();
        return DateOrder.DAY_MONTH_YEAR;
    }

    private char separator() {
        for (Map.Entry<Character, JRadioButton> e : separators.entrySet()) if (e.getValue().isSelected()) return e.getKey();
        return '/';
    }

    private void updateExample() {
        example.setText(java.time.format.DateTimeFormatter.ofPattern(order().pattern(separator()))
                .format(java.time.LocalDate.now()));
    }

    void set(CalendarPreferences p) {
        orders.get(p.dateOrder()).setSelected(true);
        JRadioButton s = separators.get(p.dateSeparator());
        (s == null ? separators.get('/') : s).setSelected(true);
        updateExample();
    }

    CalendarPreferences apply(CalendarPreferences p) {
        return new CalendarPreferences(p.defaultDurationMinutes(), p.defaultStart(), p.defaultReminders(),
                p.defaultMailTo(), p.defaultPrivacy(), p.dayBegin(), p.dayEnd(), p.twentyFourHour(), p.defaultView(),
                order(), separator(), p.print());
    }
}
