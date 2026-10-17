package org.datazuul.amadeos.calendar.ui.views;

import org.datazuul.amadeos.commons.swing.AmadeUI;
import org.datazuul.amadeos.commons.theme.DesktopTheme;

import javax.swing.*;
import java.awt.*;

/** Fonts and colours the painted views share. */
final class Painting {
    private Painting() {
    }

    static DesktopTheme theme() {
        return AmadeUI.theme();
    }

    static Font font() {
        Font f = UIManager.getFont("Label.font");
        return f == null ? new Font(Font.SANS_SERIF, Font.PLAIN, 12) : f.deriveFont(Font.PLAIN);
    }

    static Font bold() {
        return font().deriveFont(Font.BOLD);
    }

    static Font small() {
        Font f = font();
        return f.deriveFont((float) Math.max(9, f.getSize() - 2));
    }

    static Font title() {
        Font f = font();
        return f.deriveFont(Font.BOLD, f.getSize() + 3f);
    }

    static void antialias(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    /** Shortens a text with an ellipsis so that it fits into {@code width} pixels. */
    static String fit(String text, FontMetrics fm, int width) {
        if (fm.stringWidth(text) <= width) return text;
        String ellipsis = "…";
        int w = width - fm.stringWidth(ellipsis);
        if (w <= 0) return "";
        int end = text.length();
        while (end > 0 && fm.stringWidth(text.substring(0, end)) > w) end--;
        return text.substring(0, end).stripTrailing() + ellipsis;
    }

    /** The colour of a busy block in the week grids: the theme's shadow, as CDE shaded them. */
    static Color busy() {
        return theme().shadow();
    }

    /** A faint line between cells. */
    static Color grid() {
        return theme().shadow();
    }
}
