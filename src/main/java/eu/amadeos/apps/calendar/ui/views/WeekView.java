package eu.amadeos.apps.calendar.ui.views;

import eu.amadeos.apps.calendar.domain.CalendarView;
import eu.amadeos.apps.calendar.domain.Occurrence;
import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.theme.DesktopTheme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;

/**
 * Week view: a column per day with its appointments, the selected day highlighted, and below it the
 * week grid that shades the hours that are taken.
 */
public final class WeekView extends CalendarPanel {
    private final DayColumn[] columns = new DayColumn[7];
    private final WeekGrid grid = new WeekGrid();
    private LocalDate weekStart = LocalDate.now();

    public WeekView(ViewContext context, ViewActions actions) {
        super(context, actions);
        setLayout(new BorderLayout(0, 8));
        JPanel days = new JPanel(new GridLayout(1, 7, 2, 0));
        days.setOpaque(false);
        for (int i = 0; i < 7; i++) {
            columns[i] = new DayColumn();
            days.add(columns[i]);
        }
        JScrollPane scroll = new JScrollPane(days);
        scroll.setBorder(AmadeUI.sunken());
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        AmadeUI.style(scroll.getViewport(), (v, t) -> v.setBackground(t.fieldBackground()));
        JPanel gridHolder = new JPanel(new BorderLayout());
        gridHolder.setBorder(AmadeUI.sunken());
        gridHolder.add(grid, BorderLayout.CENTER);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scroll, gridHolder);
        split.setResizeWeight(0.6);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setContinuousLayout(true);
        add(split, BorderLayout.CENTER);
    }

    @Override
    public void refresh() {
        weekStart = CalendarView.WEEK.periodStart(selected, context.locale());
        List<List<Occurrence>> perDay = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = weekStart.plusDays(i);
            List<Occurrence> items = context.calendar().occurrencesOn(date);
            perDay.add(items);
            columns[i].set(date, items);
        }
        grid.set(perDay);
    }

    /** One day of the week: a header with the day name and the appointments below it. */
    private final class DayColumn extends JComponent {
        private LocalDate date = LocalDate.now();
        private List<Occurrence> items = List.of();
        private int headerHeight = 36;

        DayColumn() {
            setOpaque(true);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (!SwingUtilities.isLeftMouseButton(e)) return;
                    if (e.getY() < headerHeight) {
                        actions.showView(CalendarView.DAY, date);
                        return;
                    }
                    if (e.getClickCount() == 2) {
                        Occurrence hit = itemAt(e.getY());
                        if (hit != null) actions.editAppointment(hit);
                        else actions.editDay(date, null);
                    } else {
                        actions.selectDay(date);
                    }
                }
            });
        }

        private Occurrence itemAt(int y) {
            int lineHeight = getFontMetrics(Painting.small()).getHeight() * 2 + 2;
            int index = (y - headerHeight - 4) / lineHeight;
            return index >= 0 && index < items.size() ? items.get(index) : null;
        }

        void set(LocalDate newDate, List<Occurrence> newItems) {
            date = newDate;
            items = newItems;
            int lineHeight = getFontMetrics(Painting.small()).getHeight() * 2 + 2;
            setPreferredSize(new Dimension(90, headerHeight + 8 + items.size() * lineHeight));
            revalidate();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Painting.antialias(g);
            DesktopTheme theme = Painting.theme();
            boolean isSelected = date.equals(selected);
            g.setColor(theme.fieldBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
            headerHeight = getFontMetrics(Painting.bold()).getHeight() * 2 + 8;
            g.setColor(isSelected ? theme.selectionBackground() : theme.base());
            g.fillRect(0, 0, getWidth(), headerHeight);
            g.setColor(Painting.grid());
            g.drawLine(0, headerHeight - 1, getWidth(), headerHeight - 1);
            g.drawLine(getWidth() - 1, 0, getWidth() - 1, getHeight());
            g.setFont(Painting.bold());
            FontMetrics bfm = g.getFontMetrics();
            g.setColor(isSelected ? theme.selectionText() : theme.textActive());
            String name = date.getDayOfWeek().getDisplayName(TextStyle.FULL_STANDALONE, context.locale());
            g.drawString(Painting.fit(name, bfm, getWidth() - 8), 4, bfm.getAscent() + 3);
            String number = context.formats().date(date);
            g.setFont(Painting.small());
            g.drawString(Painting.fit(number, g.getFontMetrics(), getWidth() - 8), 4, bfm.getHeight() + bfm.getAscent() + 3);
            if (date.equals(LocalDate.now())) {
                g.setColor(isSelected ? theme.selectionText() : theme.textActive());
                g.drawRect(1, 1, getWidth() - 4, headerHeight - 4);
            }

            g.setFont(Painting.small());
            FontMetrics fm = g.getFontMetrics();
            int y = headerHeight + 4 + fm.getAscent();
            g.setColor(theme.fieldText());
            for (Occurrence o : items) {
                String time = context.formats().timeRange(o.appointment().start(), o.appointment().end());
                g.drawString(Painting.fit(time, fm, getWidth() - 8), 4, y);
                g.drawString(Painting.fit(o.appointment().summary(), fm, getWidth() - 8), 4, y + fm.getHeight());
                y += fm.getHeight() * 2 + 2;
            }
            g.dispose();
        }
    }

    /** Hours by days, taken hours shaded, as the CDE week grid showed free and busy time. */
    private final class WeekGrid extends JComponent {
        private static final int LABEL_WIDTH = 60;
        private List<List<Occurrence>> perDay = List.of();

        WeekGrid() {
            setOpaque(true);
            setPreferredSize(new Dimension(500, 180));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (!SwingUtilities.isLeftMouseButton(e) || e.getX() < LABEL_WIDTH) return;
                    int headerHeight = getFontMetrics(Painting.small()).getHeight() + 6;
                    if (e.getY() < headerHeight) return;
                    int begin = context.prefs().dayBegin();
                    int hours = context.prefs().dayEnd() - begin;
                    int col = Math.min(6, (e.getX() - LABEL_WIDTH) * 7 / Math.max(1, getWidth() - LABEL_WIDTH));
                    int row = Math.min(hours - 1, (e.getY() - headerHeight) * hours / Math.max(1, getHeight() - headerHeight));
                    LocalDate date = weekStart.plusDays(col);
                    if (e.getClickCount() == 2) actions.editDay(date, java.time.LocalTime.of(begin + row, 0));
                    else actions.selectDay(date);
                }
            });
        }

        void set(List<List<Occurrence>> days) {
            perDay = days;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Painting.antialias(g);
            DesktopTheme theme = Painting.theme();
            g.setColor(theme.fieldBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setFont(Painting.small());
            FontMetrics fm = g.getFontMetrics();
            int headerHeight = fm.getHeight() + 6;
            int begin = context.prefs().dayBegin();
            int end = context.prefs().dayEnd();
            int hours = end - begin;
            double colWidth = (getWidth() - LABEL_WIDTH) / 7.0;
            double rowHeight = (getHeight() - headerHeight) / (double) hours;

            for (int d = 0; d < 7; d++) {
                LocalDate date = weekStart.plusDays(d);
                int x = LABEL_WIDTH + (int) Math.round(d * colWidth);
                int w = (int) Math.round((d + 1) * colWidth) - (int) Math.round(d * colWidth);
                if (date.equals(selected)) {
                    g.setColor(theme.selectionBackground());
                    g.fillRect(x, 0, w, headerHeight);
                    g.setColor(theme.selectionText());
                } else {
                    g.setColor(theme.fieldText());
                }
                String name = date.getDayOfWeek().getDisplayName(TextStyle.SHORT_STANDALONE, context.locale());
                g.drawString(Painting.fit(name, fm, w - 4), x + 3, fm.getAscent() + 3);
                // Busy blocks: minutes of each occurrence inside the shown hours.
                if (d < perDay.size()) {
                    for (Occurrence o : perDay.get(d)) {
                        if (!o.appointment().hasTime()) continue;
                        int startMin = o.appointment().start().toSecondOfDay() / 60;
                        int endMin = o.appointment().hasEnd() ? o.appointment().end().toSecondOfDay() / 60 : startMin + 60;
                        int top = Math.max(startMin, begin * 60);
                        int bottom = Math.min(endMin, end * 60);
                        if (bottom <= top) continue;
                        int y1 = headerHeight + (int) Math.round((top - begin * 60) / 60.0 * rowHeight);
                        int y2 = headerHeight + (int) Math.round((bottom - begin * 60) / 60.0 * rowHeight);
                        g.setColor(Painting.busy());
                        g.fillRect(x + 1, y1, w - 1, Math.max(2, y2 - y1));
                    }
                }
            }
            g.setColor(Painting.grid());
            for (int h = 0; h <= hours; h++) {
                int y = headerHeight + (int) Math.round(h * rowHeight);
                g.drawLine(0, y, getWidth(), y);
                if (h < hours) {
                    g.setColor(theme.fieldText());
                    g.drawString(context.formats().hourLabel(begin + h), 4, y + fm.getAscent() + 2);
                    g.setColor(Painting.grid());
                }
            }
            for (int d = 0; d <= 7; d++) {
                int x = LABEL_WIDTH + (int) Math.round(d * colWidth);
                g.drawLine(x, 0, x, getHeight());
            }
            g.dispose();
        }
    }
}
