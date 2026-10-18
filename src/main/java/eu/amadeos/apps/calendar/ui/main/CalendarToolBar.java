package eu.amadeos.apps.calendar.ui.main;

import eu.amadeos.apps.calendar.domain.CalendarView;
import eu.amadeos.commons.swing.AmadeUI;

import javax.swing.*;
import java.awt.*;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

import static eu.amadeos.apps.calendar.i18n.Texts.tr;

/**
 * The dtcm tool bar: Appointment Editor, To Do Editor, Previous, Today, Next and the four views.
 */
final class CalendarToolBar extends JPanel {
    private final Map<CalendarView, JToggleButton> views = new EnumMap<>(CalendarView.class);
    private final JLabel period = new JLabel();

    CalendarToolBar(MainPresenter presenter) {
        super(new BorderLayout());
        setBorder(BorderFactory.createCompoundBorder(AmadeUI.raised(), BorderFactory.createEmptyBorder(3, 6, 3, 6)));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        left.setOpaque(false);
        left.add(button(tr("toolbar.appointment"), tr("toolbar.appointment.tip"), presenter::openAppointmentEditor));
        left.add(button(tr("toolbar.todo"), tr("toolbar.todo.tip"), presenter::openToDoEditor));
        left.add(Box.createHorizontalStrut(12));
        left.add(button("◀", tr("menu.view.previous"), presenter::previous));
        left.add(button(tr("toolbar.today"), tr("toolbar.today.tip"), presenter::today));
        left.add(button("▶", tr("menu.view.next"), presenter::next));
        left.add(Box.createHorizontalStrut(12));
        ButtonGroup group = new ButtonGroup();
        for (CalendarView v : CalendarView.values()) {
            JToggleButton b = new JToggleButton(tr("view." + v.name().toLowerCase(Locale.ROOT)));
            b.setFocusable(false);
            b.addActionListener(e -> presenter.showView(v));
            group.add(b);
            views.put(v, b);
            left.add(b);
        }
        add(left, BorderLayout.WEST);
        period.setFont(period.getFont().deriveFont(Font.BOLD));
        period.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 14));
        add(period, BorderLayout.EAST);
    }

    private static JButton button(String text, String tip, Runnable action) {
        JButton b = new JButton(text);
        b.setToolTipText(tip);
        b.setFocusable(false);
        b.addActionListener(e -> action.run());
        return b;
    }

    void setView(CalendarView v) {
        views.get(v).setSelected(true);
    }

    void setPeriod(String text) {
        period.setText(text);
    }
}
