package eu.amadeos.apps.calendar.ui.views;

import eu.amadeos.commons.theme.DesktopTheme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.function.Predicate;

/**
 * One month as a small grid of day numbers: the three-month strip of Day view and the twelve months
 * of Year view. The month name is a button that switches to Month view, a day number one that selects
 * the day. Days with appointments are shown bold.
 */
public final class MiniMonth extends JComponent {
    public interface Listener {
        void dayClicked(LocalDate date);

        void monthClicked(YearMonth month);
    }

    private final ViewContext context;
    private final Listener listener;
    private YearMonth month = YearMonth.now();
    private LocalDate selected;
    private Predicate<LocalDate> marked = d -> false;

    public MiniMonth(ViewContext context, Listener listener) {
        this.context = context;
        this.listener = listener;
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                Layout l = geometry();
                if (e.getY() < l.titleHeight) {
                    listener.monthClicked(month);
                    return;
                }
                LocalDate date = dateAt(e.getX(), e.getY(), l);
                if (date != null) listener.dayClicked(date);
            }
        });
    }

    public void setMonth(YearMonth newMonth) {
        month = newMonth;
        repaint();
    }

    public YearMonth month() {
        return month;
    }

    public void setSelected(LocalDate date) {
        selected = date;
        repaint();
    }

    /** Which days to show bold (days with appointments); Year view shows none. */
    public void setMarked(Predicate<LocalDate> predicate) {
        marked = predicate;
        repaint();
    }

    private record Layout(int titleHeight, int headerHeight, int cellWidth, int cellHeight, int left, int top) {
    }

    private Layout geometry() {
        FontMetrics fm = getFontMetrics(Painting.small());
        int cellWidth = Math.max(fm.stringWidth("00") + 8, (getWidth() - 4) / 7);
        int cellHeight = fm.getHeight() + 3;
        int titleHeight = getFontMetrics(Painting.bold()).getHeight() + 6;
        int headerHeight = fm.getHeight() + 2;
        int left = Math.max(2, (getWidth() - cellWidth * 7) / 2);
        return new Layout(titleHeight, headerHeight, cellWidth, cellHeight, left, titleHeight + headerHeight);
    }

    private LocalDate dateAt(int x, int y, Layout l) {
        if (y < l.top || x < l.left) return null;
        int col = (x - l.left) / l.cellWidth;
        int row = (y - l.top) / l.cellHeight;
        if (col > 6 || row > 5) return null;
        int offset = (month.atDay(1).getDayOfWeek().getValue() - context.firstDayOfWeek().getValue() + 7) % 7;
        int day = row * 7 + col - offset + 1;
        return day >= 1 && day <= month.lengthOfMonth() ? month.atDay(day) : null;
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(Painting.small());
        int cellWidth = fm.stringWidth("00") + 8;
        int cellHeight = fm.getHeight() + 3;
        int titleHeight = getFontMetrics(Painting.bold()).getHeight() + 6;
        return new Dimension(cellWidth * 7 + 4, titleHeight + fm.getHeight() + 2 + cellHeight * 6 + 4);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        Painting.antialias(g);
        DesktopTheme theme = Painting.theme();
        Layout l = geometry();
        g.setFont(Painting.bold());
        g.setColor(theme.textActive());
        String title = context.formats().monthName(month.getMonth()) + " " + month.getYear();
        FontMetrics tfm = g.getFontMetrics();
        g.drawString(title, (getWidth() - tfm.stringWidth(title)) / 2, tfm.getAscent() + 3);

        g.setFont(Painting.small());
        FontMetrics fm = g.getFontMetrics();
        DayOfWeek first = context.firstDayOfWeek();
        for (int i = 0; i < 7; i++) {
            DayOfWeek d = first.plus(i);
            String name = d.getDisplayName(TextStyle.NARROW_STANDALONE, context.locale());
            int x = l.left + i * l.cellWidth + (l.cellWidth - fm.stringWidth(name)) / 2;
            g.setColor(theme.textActive());
            g.drawString(name, x, l.titleHeight + fm.getAscent());
        }
        int offset = (month.atDay(1).getDayOfWeek().getValue() - first.getValue() + 7) % 7;
        LocalDate today = LocalDate.now();
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            int index = offset + day - 1;
            int col = index % 7;
            int row = index / 7;
            int x = l.left + col * l.cellWidth;
            int y = l.top + row * l.cellHeight;
            LocalDate date = month.atDay(day);
            boolean isSelected = date.equals(selected);
            if (isSelected) {
                g.setColor(theme.selectionBackground());
                g.fillRect(x, y, l.cellWidth, l.cellHeight);
            }
            if (date.equals(today)) {
                g.setColor(isSelected ? theme.selectionText() : theme.textActive());
                g.drawRect(x, y, l.cellWidth - 1, l.cellHeight - 1);
            }
            g.setFont(marked.test(date) ? Painting.small().deriveFont(Font.BOLD) : Painting.small());
            g.setColor(isSelected ? theme.selectionText() : theme.textActive());
            String s = Integer.toString(day);
            g.drawString(s, x + (l.cellWidth - g.getFontMetrics().stringWidth(s)) / 2, y + fm.getAscent() + 1);
        }
        g.dispose();
    }
}
