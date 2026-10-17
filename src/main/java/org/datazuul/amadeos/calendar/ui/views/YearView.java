package org.datazuul.amadeos.calendar.ui.views;

import org.datazuul.amadeos.calendar.domain.CalendarView;
import org.datazuul.amadeos.commons.swing.AmadeUI;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.YearMonth;

/** Year view: the twelve months without appointments; a month name opens Month view. */
public final class YearView extends CalendarPanel {
    private final MiniMonth[] months = new MiniMonth[12];
    private final JLabel title = new JLabel("", SwingConstants.CENTER);

    public YearView(ViewContext context, ViewActions actions) {
        super(context, actions);
        setLayout(new BorderLayout(0, 6));
        title.setFont(Painting.title());
        add(title, BorderLayout.NORTH);
        JPanel grid = new JPanel(new GridLayout(3, 4, 12, 12));
        grid.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        AmadeUI.style(grid, (p, t) -> p.setBackground(t.fieldBackground()));
        MiniMonth.Listener listener = new MiniMonth.Listener() {
            @Override
            public void dayClicked(LocalDate date) {
                actions.showView(CalendarView.DAY, date);
            }

            @Override
            public void monthClicked(YearMonth month) {
                actions.showView(CalendarView.MONTH, month.atDay(1));
            }
        };
        for (int i = 0; i < 12; i++) {
            months[i] = new MiniMonth(context, listener);
            grid.add(months[i]);
        }
        JPanel holder = new JPanel(new BorderLayout());
        holder.setBorder(AmadeUI.sunken());
        holder.add(grid, BorderLayout.CENTER);
        add(holder, BorderLayout.CENTER);
    }

    @Override
    public void refresh() {
        title.setText(Integer.toString(selected.getYear()));
        for (int i = 0; i < 12; i++) {
            months[i].setMonth(YearMonth.of(selected.getYear(), i + 1));
            months[i].setSelected(selected);
        }
    }
}
