package org.datazuul.amadeos.calendar.ui.views;

import org.datazuul.amadeos.calendar.domain.CalendarView;
import org.datazuul.amadeos.calendar.domain.Occurrence;
import org.datazuul.amadeos.commons.swing.AmadeUI;
import org.datazuul.amadeos.commons.theme.DesktopTheme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Day view: the day's appointments hour by hour on the left, the three-month mini calendar (previous,
 * current and next month) on the right.
 */
public final class DayView extends CalendarPanel {
    private final DayGrid grid;
    private final MiniMonth[] months = new MiniMonth[3];

    public DayView(ViewContext context, ViewActions actions) {
        super(context, actions);
        setLayout(new BorderLayout(8, 0));
        grid = new DayGrid();
        JScrollPane scroll = new JScrollPane(grid);
        scroll.setBorder(AmadeUI.sunken());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        AmadeUI.style(scroll.getViewport(), (v, t) -> v.setBackground(t.fieldBackground()));
        add(scroll, BorderLayout.CENTER);

        JPanel strip = new JPanel(new GridLayout(3, 1, 0, 10));
        strip.setOpaque(false);
        MiniMonth.Listener listener = new MiniMonth.Listener() {
            @Override
            public void dayClicked(LocalDate date) {
                actions.selectDay(date);
            }

            @Override
            public void monthClicked(YearMonth month) {
                actions.showView(CalendarView.MONTH, month.atDay(1));
            }
        };
        for (int i = 0; i < 3; i++) {
            months[i] = new MiniMonth(context, listener);
            strip.add(months[i]);
        }
        JPanel east = new JPanel(new BorderLayout());
        east.setOpaque(false);
        east.add(strip, BorderLayout.NORTH);
        add(east, BorderLayout.EAST);
    }

    @Override
    public void refresh() {
        YearMonth month = YearMonth.from(selected);
        for (int i = 0; i < 3; i++) {
            months[i].setMonth(month.plusMonths(i - 1));
            months[i].setSelected(selected);
            months[i].setMarked(context.calendar()::hasAppointments);
        }
        grid.setOccurrences(context.calendar().occurrencesOn(selected));
    }

    /** The hours of the day with the appointments next to them, as in dtcm's day view. */
    private final class DayGrid extends JComponent {
        private static final int MIN_ROW = 28;
        private static final int LABEL_WIDTH = 72;
        private List<Occurrence> occurrences = List.of();
        private List<Row> rows = List.of();
        private int headerHeight = 30;

        private record Row(int hour, List<Occurrence> items, int y, int height) {
        }

        DayGrid() {
            setOpaque(true);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (!SwingUtilities.isLeftMouseButton(e) || e.getClickCount() != 2) return;
                    for (Row row : rows) {
                        if (e.getY() >= row.y && e.getY() < row.y + row.height) {
                            Occurrence hit = itemAt(row, e.getY());
                            if (hit != null) actions.editAppointment(hit);
                            else actions.editDay(selected, row.hour < 0 ? null : LocalTime.of(row.hour, 0));
                            return;
                        }
                    }
                    actions.editDay(selected, null);
                }
            });
        }

        private Occurrence itemAt(Row row, int y) {
            int lineHeight = getFontMetrics(Painting.font()).getHeight();
            int index = (y - row.y - 4) / lineHeight;
            return index >= 0 && index < row.items.size() ? row.items.get(index) : null;
        }

        void setOccurrences(List<Occurrence> list) {
            occurrences = list;
            layoutRows();
            revalidate();
            repaint();
        }

        private void layoutRows() {
            int lineHeight = getFontMetrics(Painting.font()).getHeight();
            headerHeight = getFontMetrics(Painting.title()).getHeight() + 12;
            List<Row> result = new ArrayList<>();
            int y = headerHeight;
            List<Occurrence> untimed = new ArrayList<>();
            List<Occurrence> early = new ArrayList<>();
            List<Occurrence> late = new ArrayList<>();
            int begin = context.prefs().dayBegin();
            int end = context.prefs().dayEnd();
            for (Occurrence o : occurrences) {
                if (!o.appointment().hasTime()) untimed.add(o);
                else if (o.appointment().start().getHour() < begin) early.add(o);
                else if (o.appointment().start().getHour() >= end) late.add(o);
            }
            if (!untimed.isEmpty()) {
                int h = Math.max(MIN_ROW, untimed.size() * lineHeight + 8);
                result.add(new Row(-1, untimed, y, h));
                y += h;
            }
            if (!early.isEmpty()) {
                int h = Math.max(MIN_ROW, early.size() * lineHeight + 8);
                result.add(new Row(begin - 1, early, y, h));
                y += h;
            }
            for (int hour = begin; hour < end; hour++) {
                List<Occurrence> items = new ArrayList<>();
                for (Occurrence o : occurrences) {
                    if (o.appointment().hasTime() && o.appointment().start().getHour() == hour) items.add(o);
                }
                int h = Math.max(MIN_ROW, items.size() * lineHeight + 8);
                result.add(new Row(hour, items, y, h));
                y += h;
            }
            if (!late.isEmpty()) {
                int h = Math.max(MIN_ROW, late.size() * lineHeight + 8);
                result.add(new Row(end, late, y, h));
                y += h;
            }
            rows = result;
            setPreferredSize(new Dimension(420, y + 4));
        }

        @Override
        public void doLayout() {
            layoutRows();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Painting.antialias(g);
            DesktopTheme theme = Painting.theme();
            g.setColor(theme.fieldBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setFont(Painting.title());
            g.setColor(theme.fieldText());
            g.drawString(context.formats().longDate(selected), 8, g.getFontMetrics().getAscent() + 6);
            g.setColor(Painting.grid());
            g.drawLine(0, headerHeight - 1, getWidth(), headerHeight - 1);

            Font font = Painting.font();
            FontMetrics fm = g.getFontMetrics(font);
            int lineHeight = fm.getHeight();
            for (Row row : rows) {
                g.setColor(Painting.grid());
                g.drawLine(0, row.y + row.height - 1, getWidth(), row.y + row.height - 1);
                g.setFont(Painting.bold());
                g.setColor(theme.fieldText());
                String label = row.hour < 0 ? "" : row.hour >= context.prefs().dayEnd() || row.hour < context.prefs().dayBegin()
                        ? "…" : context.formats().hourLabel(row.hour);
                g.drawString(label, 8, row.y + fm.getAscent() + 4);
                g.setFont(font);
                int y = row.y + fm.getAscent() + 4;
                for (Occurrence o : row.items) {
                    String time = context.formats().timeRange(o.appointment().start(), o.appointment().end());
                    String text = (time.isEmpty() ? "" : time + "  ") + o.appointment().summary();
                    g.drawString(Painting.fit(text, fm, getWidth() - LABEL_WIDTH - 12), LABEL_WIDTH, y);
                    y += lineHeight;
                }
            }
            g.dispose();
        }
    }
}
