package org.datazuul.amadeos.calendar.ui.print;

import org.datazuul.amadeos.calendar.domain.PrintSettings;
import org.datazuul.amadeos.calendar.service.print.CalendarPrinter;
import org.datazuul.amadeos.calendar.service.print.PrintRequest;
import org.datazuul.amadeos.calendar.service.print.ReportType;
import org.datazuul.amadeos.calendar.ui.common.DateField;
import org.datazuul.amadeos.calendar.ui.views.ViewContext;
import org.datazuul.amadeos.commons.i18n.Mnemonics;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

import static org.datazuul.amadeos.calendar.i18n.Texts.tr;

/**
 * The Print dialog of dtcm: printer name, report type, copies, the range of time, print command
 * options and "Print to file" with the file name; Print and Close.
 */
public final class PrintDialog extends JDialog {
    private final JComboBox<String> printer = new JComboBox<>();
    private final JComboBox<ReportType> type = new JComboBox<>(ReportType.values());
    private final JSpinner copies = new JSpinner(new SpinnerNumberModel(1, 1, 99, 1));
    private final DateField from;
    private final DateField to;
    private final JTextField options = new JTextField(24);
    private final JCheckBox toFile = new JCheckBox(tr("print.to-file"));
    private final JTextField fileName = new JTextField(24);
    private final ViewContext context;

    public PrintDialog(Frame owner, ViewContext context, Consumer<PrintRequest> print) {
        super(owner, tr("print.title"), false);
        this.context = context;
        from = new DateField(context.formats());
        to = new DateField(context.formats());
        printer.setEditable(true);
        printer.addItem("");
        for (String name : CalendarPrinter.printerNames()) printer.addItem(name);
        printer.setPrototypeDisplayValue("HP-Printer-Downstairs");
        type.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean sel, boolean focus) {
                return super.getListCellRendererComponent(l, value == null ? ""
                        : tr("print.report." + ((ReportType) value).name().toLowerCase(Locale.ROOT)), index, sel, focus);
            }
        });
        toFile.setOpaque(false);
        toFile.addActionListener(e -> fileName.setEnabled(toFile.isSelected()));

        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 0, 3, 8);
        c.anchor = GridBagConstraints.LINE_START;
        c.gridy = 0;
        row(fields, c, "print.printer", printer);
        row(fields, c, "print.report", type);
        row(fields, c, "print.copies", copies);
        JPanel range = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        range.setOpaque(false);
        range.add(from);
        range.add(new JLabel(tr("print.to")));
        range.add(to);
        row(fields, c, "print.from", range);
        row(fields, c, "print.options", options);
        c.gridx = 1;
        c.gridy++;
        fields.add(toFile, c);
        row(fields, c, "print.file", fileName);

        JButton printButton = new JButton(tr("print.print"));
        JButton close = new JButton(tr("dialog.close"));
        printButton.addActionListener(e -> read().ifPresent(print));
        close.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        buttons.setOpaque(false);
        buttons.add(printButton);
        buttons.add(close);

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        content.add(fields, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        getRootPane().setDefaultButton(printButton);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }

    private static void row(JPanel panel, GridBagConstraints c, String key, JComponent field) {
        c.gridx = 0;
        c.gridy++;
        JLabel label = Mnemonics.apply(new JLabel(), tr(key));
        label.setLabelFor(field);
        panel.add(label, c);
        c.gridx = 1;
        panel.add(field, c);
    }

    /** Opens with the settings and the current view's report and period filled in. */
    public void open(PrintSettings settings, ReportType reportType, LocalDate start, LocalDate end) {
        printer.setSelectedItem(settings.printerName());
        type.setSelectedItem(reportType);
        copies.setValue(settings.copies());
        from.setDate(start);
        to.setDate(end);
        options.setText(settings.options());
        toFile.setSelected(settings.destination() == PrintSettings.Destination.FILE);
        fileName.setText(settings.fileName());
        fileName.setEnabled(toFile.isSelected());
        pack();
        setLocationRelativeTo(getOwner());
        setVisible(true);
        toFront();
    }

    private Optional<PrintRequest> read() {
        Optional<LocalDate> start = from.date();
        Optional<LocalDate> end = to.date();
        if (start.isEmpty() || end.isEmpty() || end.get().isBefore(start.get())) {
            JOptionPane.showMessageDialog(this, tr("find.error.range"), getTitle(), JOptionPane.ERROR_MESSAGE);
            return Optional.empty();
        }
        Object name = printer.getEditor().getItem();
        PrintSettings settings = new PrintSettings(
                toFile.isSelected() ? PrintSettings.Destination.FILE : PrintSettings.Destination.PRINTER,
                name == null ? "" : name.toString(), options.getText(), fileName.getText(), 1, (int) copies.getValue(),
                context.prefs().print().asOthersSee());
        return Optional.of(new PrintRequest((ReportType) type.getSelectedItem(), start.get(), end.get(), settings));
    }
}
