package eu.amadeos.apps.calendar.ui.options;

import eu.amadeos.apps.calendar.domain.CalendarPreferences;
import eu.amadeos.apps.calendar.domain.CalendarView;

import javax.swing.*;
import java.awt.*;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

import static eu.amadeos.apps.calendar.i18n.Texts.tr;

/** "Display Settings" category: the hours shown in Day and Week view, 12/24-hour time, default view. */
final class DisplaySettingsPanel extends JPanel {
    private final JSlider begin = new JSlider(0, 23, 8);
    private final JSlider end = new JSlider(1, 24, 18);
    private final JLabel beginValue = new JLabel();
    private final JLabel endValue = new JLabel();
    private final JRadioButton twelve = new JRadioButton(tr("options.display.12hour"));
    private final JRadioButton twentyFour = new JRadioButton(tr("options.display.24hour"));
    private final Map<CalendarView, JRadioButton> views = new EnumMap<>(CalendarView.class);

    DisplaySettingsPanel() {
        super(new GridBagLayout());
        setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 0, 3, 8);
        c.anchor = GridBagConstraints.LINE_START;
        c.gridy = 0;
        add(new JLabel(tr("options.display.begin")), c);
        c.gridx = 1;
        add(sliderRow(begin, beginValue), c);
        c.gridx = 0;
        c.gridy++;
        add(new JLabel(tr("options.display.end")), c);
        c.gridx = 1;
        add(sliderRow(end, endValue), c);
        begin.addChangeListener(e -> {
            if (end.getValue() <= begin.getValue()) end.setValue(begin.getValue() + 1);
            updateLabels();
        });
        end.addChangeListener(e -> {
            if (begin.getValue() >= end.getValue()) begin.setValue(end.getValue() - 1);
            updateLabels();
        });

        c.gridx = 0;
        c.gridy++;
        add(new JLabel(tr("options.display.time")), c);
        c.gridx = 1;
        JPanel hours = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        hours.setOpaque(false);
        ButtonGroup hourGroup = new ButtonGroup();
        for (JRadioButton b : new JRadioButton[]{twelve, twentyFour}) {
            b.setOpaque(false);
            hourGroup.add(b);
            if (hours.getComponentCount() > 0) hours.add(Box.createHorizontalStrut(10));
            hours.add(b);
        }
        add(hours, c);

        c.gridx = 0;
        c.gridy++;
        add(new JLabel(tr("options.display.default-view")), c);
        c.gridx = 1;
        JPanel viewRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        viewRow.setOpaque(false);
        ButtonGroup viewGroup = new ButtonGroup();
        for (CalendarView v : CalendarView.values()) {
            JRadioButton b = new JRadioButton(tr("view." + v.name().toLowerCase(Locale.ROOT)));
            b.setOpaque(false);
            viewGroup.add(b);
            if (viewRow.getComponentCount() > 0) viewRow.add(Box.createHorizontalStrut(10));
            viewRow.add(b);
            views.put(v, b);
        }
        add(viewRow, c);
        c.gridy++;
        c.weighty = 1;
        add(Box.createVerticalGlue(), c);
    }

    private JPanel sliderRow(JSlider slider, JLabel value) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        slider.setOpaque(false);
        slider.setPreferredSize(new Dimension(220, slider.getPreferredSize().height));
        value.setPreferredSize(new Dimension(60, value.getPreferredSize().height + 4));
        row.add(slider, BorderLayout.CENTER);
        row.add(value, BorderLayout.EAST);
        return row;
    }

    private void updateLabels() {
        beginValue.setText(String.format("%02d:00", begin.getValue()));
        endValue.setText(String.format("%02d:00", end.getValue()));
    }

    void set(CalendarPreferences p) {
        begin.setValue(p.dayBegin());
        end.setValue(p.dayEnd());
        (p.twentyFourHour() ? twentyFour : twelve).setSelected(true);
        views.get(p.defaultView()).setSelected(true);
        updateLabels();
    }

    CalendarPreferences apply(CalendarPreferences p) {
        CalendarView view = p.defaultView();
        for (Map.Entry<CalendarView, JRadioButton> e : views.entrySet()) if (e.getValue().isSelected()) view = e.getKey();
        return new CalendarPreferences(p.defaultDurationMinutes(), p.defaultStart(), p.defaultReminders(),
                p.defaultMailTo(), p.defaultPrivacy(), begin.getValue(), end.getValue(), twentyFour.isSelected(), view,
                p.dateOrder(), p.dateSeparator(), p.print());
    }
}
