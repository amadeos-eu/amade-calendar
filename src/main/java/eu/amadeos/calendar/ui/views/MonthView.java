package eu.amadeos.calendar.ui.views;

import eu.amadeos.calendar.domain.CalendarView;
import eu.amadeos.calendar.domain.Occurrence;
import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.theme.DesktopTheme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Month view: the month as a grid of days with their appointments; the selected day is highlighted.
 * A click on a day number opens Day view, a double-click in a cell the Appointment Editor.
 */
public final class MonthView extends CalendarPanel {
    private final Grid grid = new Grid();

    public MonthView(ViewContext context, ViewActions actions) {
        super(context, actions);
        setLayout(new BorderLayout());
        JPanel holder = new JPanel(new BorderLayout());
        holder.setBorder(AmadeUI.sunken());
        holder.add(grid, BorderLayout.CENTER);
        add(holder, BorderLayout.CENTER);
    }

    @Override
    public void refresh() {
        YearMonth month = YearMonth.from(selected);
        Map<LocalDate, List<Occurrence>> byDay = new HashMap<>();
        for (Occurrence o : context.calendar().occurrencesBetween(month.atDay(1), month.atEndOfMonth())) {
            byDay.computeIfAbsent(o.date(), d -> new java.util.ArrayList<>()).add(o);
        }
        grid.set(month, byDay);
    }

    private final class Grid extends JComponent {
        private YearMonth month = YearMonth.now();
        private Map<LocalDate, List<Occurrence>> byDay = Map.of();
        private int titleHeight = 30;
        private int headerHeight = 20;

        Grid() {
            setOpaque(true);
            setPreferredSize(new Dimension(700, 480));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (!SwingUtilities.isLeftMouseButton(e)) return;
                    LocalDate date = dateAt(e.getX(), e.getY());
                    if (date == null) return;
                    if (e.getClickCount() == 2) {
                        Occurrence hit = itemAt(date, e.getX(), e.getY());
                        if (hit != null) actions.editAppointment(hit);
                        else actions.editDay(date, null);
                    } else if (onDayNumber(date, e.getX(), e.getY())) {
                        actions.showView(CalendarView.DAY, date);
                    } else {
                        actions.selectDay(date);
                    }
                }
            });
        }

        void set(YearMonth newMonth, Map<LocalDate, List<Occurrence>> items) {
            month = newMonth;
            byDay = items;
            repaint();
        }

        private int rows() {
            int offset = offset();
            return (offset + month.lengthOfMonth() + 6) / 7;
        }

        private int offset() {
            return (month.atDay(1).getDayOfWeek().getValue() - context.firstDayOfWeek().getValue() + 7) % 7;
        }

        private double cellWidth() {
            return getWidth() / 7.0;
        }

        private double cellHeight() {
            return (getHeight() - titleHeight - headerHeight) / (double) rows();
        }

        private LocalDate dateAt(int x, int y) {
            if (y < titleHeight + headerHeight) return null;
            int col = Math.min(6, (int) (x / cellWidth()));
            int row = Math.min(rows() - 1, (int) ((y - titleHeight - headerHeight) / cellHeight()));
            int day = row * 7 + col - offset() + 1;
            return day >= 1 && day <= month.lengthOfMonth() ? month.atDay(day) : null;
        }

        private Point cellOrigin(LocalDate date) {
            int index = offset() + date.getDayOfMonth() - 1;
            return new Point((int) Math.round((index % 7) * cellWidth()),
                    titleHeight + headerHeight + (int) Math.round((index / 7) * cellHeight()));
        }

        private boolean onDayNumber(LocalDate date, int x, int y) {
            Point o = cellOrigin(date);
            FontMetrics fm = getFontMetrics(Painting.bold());
            return x - o.x < fm.stringWidth("00") + 10 && y - o.y < fm.getHeight() + 4;
        }

        private Occurrence itemAt(LocalDate date, int x, int y) {
            Point o = cellOrigin(date);
            int numberHeight = getFontMetrics(Painting.bold()).getHeight() + 4;
            int lineHeight = getFontMetrics(Painting.small()).getHeight();
            int index = (y - o.y - numberHeight) / lineHeight;
            List<Occurrence> items = byDay.getOrDefault(date, List.of());
            return index >= 0 && index < items.size() ? items.get(index) : null;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Painting.antialias(g);
            DesktopTheme theme = Painting.theme();
            g.setColor(theme.fieldBackground());
            g.fillRect(0, 0, getWidth(), getHeight());

            g.setFont(Painting.title());
            FontMetrics tfm = g.getFontMetrics();
            titleHeight = tfm.getHeight() + 12;
            g.setColor(theme.fieldText());
            String title = context.formats().monthAndYear(month.atDay(1));
            g.drawString(title, (getWidth() - tfm.stringWidth(title)) / 2, tfm.getAscent() + 6);

            g.setFont(Painting.bold());
            FontMetrics bfm = g.getFontMetrics();
            headerHeight = bfm.getHeight() + 6;
            double cw = cellWidth();
            double ch = cellHeight();
            g.setColor(theme.base());
            g.fillRect(0, titleHeight, getWidth(), headerHeight);
            DayOfWeek first = context.firstDayOfWeek();
            for (int i = 0; i < 7; i++) {
                String name = first.plus(i).getDisplayName(TextStyle.FULL_STANDALONE, context.locale());
                int x = (int) Math.round(i * cw);
                g.setColor(theme.textActive());
                g.drawString(Painting.fit(name, bfm, (int) cw - 8), x + 4, titleHeight + bfm.getAscent() + 3);
            }

            Font small = Painting.small();
            FontMetrics sfm = g.getFontMetrics(small);
            LocalDate today = LocalDate.now();
            int rows = rows();
            for (int day = 1; day <= month.lengthOfMonth(); day++) {
                LocalDate date = month.atDay(day);
                Point o = cellOrigin(date);
                int w = (int) Math.round(cw);
                int h = (int) Math.round(ch);
                boolean isSelected = date.equals(selected);
                if (isSelected) {
                    g.setColor(theme.selectionBackground());
                    g.fillRect(o.x, o.y, w, h);
                }
                g.setFont(Painting.bold());
                g.setColor(isSelected ? theme.selectionText() : theme.fieldText());
                g.drawString(Integer.toString(day), o.x + 5, o.y + bfm.getAscent() + 2);
                if (date.equals(today)) {
                    g.drawRect(o.x + 1, o.y + 1, w - 3, h - 3);
                }
                g.setFont(small);
                int y = o.y + bfm.getHeight() + 4 + sfm.getAscent();
                List<Occurrence> items = byDay.getOrDefault(date, List.of());
                int maxLines = Math.max(0, (h - bfm.getHeight() - 6) / sfm.getHeight());
                for (int i = 0; i < items.size() && i < maxLines; i++) {
                    Occurrence oc = items.get(i);
                    String text = oc.appointment().hasTime()
                            ? context.formats().time(oc.appointment().start()) + " " + oc.appointment().summary()
                            : oc.appointment().summary();
                    if (i == maxLines - 1 && items.size() > maxLines) text = "+" + (items.size() - maxLines + 1) + " …";
                    g.drawString(Painting.fit(text, sfm, w - 8), o.x + 4, y);
                    y += sfm.getHeight();
                }
            }
            g.setColor(Painting.grid());
            for (int r = 0; r <= rows; r++) {
                int y = titleHeight + headerHeight + (int) Math.round(r * ch);
                g.drawLine(0, Math.min(y, getHeight() - 1), getWidth(), Math.min(y, getHeight() - 1));
            }
            for (int c = 0; c <= 7; c++) {
                int x = Math.min((int) Math.round(c * cw), getWidth() - 1);
                g.drawLine(x, titleHeight, x, getHeight());
            }
            g.dispose();
        }
    }
}
