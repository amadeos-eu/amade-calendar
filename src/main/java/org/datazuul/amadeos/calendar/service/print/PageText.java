package org.datazuul.amadeos.calendar.service.print;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

/** Fonts and text helpers of the printed pages: black on white, one serif-less family. */
final class PageText {
    static final Font TITLE = new Font(Font.SANS_SERIF, Font.BOLD, 16);
    static final Font HEADING = new Font(Font.SANS_SERIF, Font.BOLD, 10);
    static final Font BODY = new Font(Font.SANS_SERIF, Font.PLAIN, 9);
    static final Font SMALL = new Font(Font.SANS_SERIF, Font.PLAIN, 7);

    private PageText() {
    }

    /** Shortens with an ellipsis so that the text fits into {@code width} points. */
    static String fit(String text, FontMetrics fm, double width) {
        if (fm.stringWidth(text) <= width) return text;
        String ellipsis = "…";
        double w = width - fm.stringWidth(ellipsis);
        int end = text.length();
        while (end > 0 && fm.stringWidth(text.substring(0, end)) > w) end--;
        return end <= 0 ? "" : text.substring(0, end).stripTrailing() + ellipsis;
    }

    /** Breaks a text into lines no wider than {@code width} points; explicit line breaks are kept. */
    static List<String> wrap(String text, FontMetrics fm, double width) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\n")) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (fm.stringWidth(candidate) <= width || line.isEmpty()) {
                    line.setLength(0);
                    line.append(candidate);
                } else {
                    lines.add(line.toString());
                    line.setLength(0);
                    line.append(word);
                }
            }
            lines.add(line.toString());
        }
        return lines;
    }

    static void centered(Graphics2D g, String text, double centerX, double baseline) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, (float) (centerX - fm.stringWidth(text) / 2.0), (float) baseline);
    }
}
