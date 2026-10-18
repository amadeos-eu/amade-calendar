package org.datazuul.amadeos.calendar.ui.options;

import org.datazuul.amadeos.calendar.domain.CalendarPreferences;
import org.datazuul.amadeos.calendar.domain.PrintSettings;
import org.datazuul.amadeos.calendar.service.print.CalendarPrinter;
import org.datazuul.amadeos.commons.i18n.Mnemonics;

import javax.swing.*;
import java.awt.*;

import static org.datazuul.amadeos.calendar.i18n.Texts.tr;

/** "Printer Settings" category: where and how "Print Current View" prints. */
final class PrinterSettingsPanel extends JPanel {
    private final JRadioButton toPrinter = new JRadioButton(tr("print.destination.printer"));
    private final JRadioButton toFile = new JRadioButton(tr("print.destination.file"));
    private final JComboBox<String> printer = new JComboBox<>();
    private final JTextField options = new JTextField(20);
    private final JTextField fileName = new JTextField(20);
    private final JSpinner units = new JSpinner(new SpinnerNumberModel(1, 1, 52, 1));
    private final JSpinner copies = new JSpinner(new SpinnerNumberModel(1, 1, 99, 1));
    private final JCheckBox asOthersSee = new JCheckBox(tr("print.as-others-see"));

    PrinterSettingsPanel() {
        super(new GridBagLayout());
        setOpaque(false);
        ButtonGroup group = new ButtonGroup();
        JPanel destination = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        destination.setOpaque(false);
        for (JRadioButton b : new JRadioButton[]{toPrinter, toFile}) {
            b.setOpaque(false);
            group.add(b);
            if (destination.getComponentCount() > 0) destination.add(Box.createHorizontalStrut(10));
            destination.add(b);
        }
        printer.setEditable(true);
        printer.addItem("");
        for (String name : CalendarPrinter.printerNames()) printer.addItem(name);
        printer.setPrototypeDisplayValue("HP-Printer-Downstairs");
        asOthersSee.setOpaque(false);

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 0, 3, 8);
        c.anchor = GridBagConstraints.LINE_START;
        c.gridy = -1;
        row(c, "print.destination", destination);
        row(c, "print.printer", printer);
        row(c, "print.options", options);
        row(c, "print.file", fileName);
        row(c, "print.units", units);
        row(c, "print.copies", copies);
        c.gridx = 1;
        c.gridy++;
        add(asOthersSee, c);
        c.gridy++;
        c.weighty = 1;
        add(Box.createVerticalGlue(), c);
    }

    private void row(GridBagConstraints c, String key, JComponent field) {
        c.gridx = 0;
        c.gridy++;
        JLabel label = Mnemonics.apply(new JLabel(), tr(key));
        label.setLabelFor(field);
        add(label, c);
        c.gridx = 1;
        add(field, c);
    }

    void set(CalendarPreferences p) {
        PrintSettings s = p.print();
        (s.destination() == PrintSettings.Destination.FILE ? toFile : toPrinter).setSelected(true);
        printer.setSelectedItem(s.printerName());
        options.setText(s.options());
        fileName.setText(s.fileName());
        units.setValue(s.units());
        copies.setValue(s.copies());
        asOthersSee.setSelected(s.asOthersSee());
    }

    CalendarPreferences apply(CalendarPreferences p) {
        Object name = printer.getEditor().getItem();
        return p.withPrint(new PrintSettings(
                toFile.isSelected() ? PrintSettings.Destination.FILE : PrintSettings.Destination.PRINTER,
                name == null ? "" : name.toString(), options.getText(), fileName.getText(), (int) units.getValue(),
                (int) copies.getValue(), asOthersSee.isSelected()));
    }
}
