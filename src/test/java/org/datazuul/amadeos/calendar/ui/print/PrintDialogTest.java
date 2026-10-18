package org.datazuul.amadeos.calendar.ui.print;

import org.datazuul.amadeos.calendar.domain.CalendarPreferences;
import org.datazuul.amadeos.calendar.domain.PrintSettings;
import org.datazuul.amadeos.calendar.service.CalendarService;
import org.datazuul.amadeos.calendar.service.DateFormats;
import org.datazuul.amadeos.calendar.service.print.PrintRequest;
import org.datazuul.amadeos.calendar.service.print.ReportType;
import org.datazuul.amadeos.calendar.ui.views.ViewContext;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class PrintDialogTest {
    @Test
    void rangeSnapsToWholePeriodsOfTheReportType() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        CalendarPreferences prefs = CalendarPreferences.defaultsFor(Locale.GERMANY);
        ViewContext context = new ViewContext(new CalendarService(), new DateFormats(prefs, Locale.GERMANY), prefs);
        List<PrintRequest> printed = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            JFrame owner = new JFrame();
            PrintDialog dialog = new PrintDialog(owner, context, printed::add);
            try {
                dialog.open(PrintSettings.DEFAULTS, ReportType.MONTH, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
                @SuppressWarnings("unchecked")
                JComboBox<ReportType> type = (JComboBox<ReportType>) field(dialog, "type");
                org.datazuul.amadeos.calendar.ui.common.DateField from = (org.datazuul.amadeos.calendar.ui.common.DateField) field(dialog, "from");
                org.datazuul.amadeos.calendar.ui.common.DateField to = (org.datazuul.amadeos.calendar.ui.common.DateField) field(dialog, "to");
                type.setSelectedItem(ReportType.YEAR);
                assertEquals("01.01.2026", from.getText());
                assertEquals("31.12.2026", to.getText());
                type.setSelectedItem(ReportType.WEEK);
                assertEquals("29.12.2025", from.getText(), "the Monday of the week of 1 January");
                assertEquals("03.01.2027", to.getText(), "the Sunday of the week of 31 December");
                to.setText("14.01.2026");
                type.setSelectedItem(ReportType.APPOINTMENT_LIST);
                assertEquals("14.01.2026", to.getText(), "lists keep the range");
                type.setSelectedItem(ReportType.DAY);
                assertEquals("29.12.2025", from.getText());
                assertEquals("14.01.2026", to.getText());
            } finally {
                dialog.dispose();
                owner.dispose();
            }
        });
    }

    private static Object field(Object target, String name) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
