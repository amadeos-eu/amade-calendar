package eu.amadeos.calendar.ui.options;

import eu.amadeos.calendar.domain.CalendarPreferences;
import eu.amadeos.calendar.i18n.Texts;
import eu.amadeos.calendar.service.DateFormats;
import eu.amadeos.commons.i18n.LanguageChooser;
import eu.amadeos.commons.i18n.LocaleSettings;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.function.Consumer;

import static eu.amadeos.calendar.i18n.Texts.tr;

/**
 * Calendar Options, as in dtcm: a single dialog whose page is chosen with the "Category" menu: Editor
 * Defaults, Display Settings, Date Format, Printer Settings and Language.
 */
public final class OptionsDialog extends JDialog {
    public static final String EDITOR_DEFAULTS = "Editor Defaults";
    public static final String DISPLAY_SETTINGS = "Display Settings";
    public static final String DATE_FORMAT = "Date Format";
    public static final String PRINTER_SETTINGS = "Printer Settings";
    public static final String LANGUAGE = "Language";

    private final EditorDefaultsPanel editor;
    private final DisplaySettingsPanel display = new DisplaySettingsPanel();
    private final DateFormatPanel dateFormat = new DateFormatPanel();
    private final PrinterSettingsPanel printer = new PrinterSettingsPanel();
    private final JComboBox<String> category = new JComboBox<>(new String[]{EDITOR_DEFAULTS, DISPLAY_SETTINGS,
            DATE_FORMAT, PRINTER_SETTINGS, LANGUAGE});
    private final CardLayout cards = new CardLayout();
    private final JPanel pages = new JPanel(cards);
    private final LocaleSettings locales;
    private final LanguageChooser language;
    private CalendarPreferences current;

    public OptionsDialog(Frame owner, DateFormats formats, Consumer<CalendarPreferences> handler, LocaleSettings locales) {
        super(owner, tr("options.title"), true);
        this.locales = locales;
        language = new LanguageChooser(locales, Messages.commons(Texts.messages().locale()));
        editor = new EditorDefaultsPanel(formats);
        category.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                                                          boolean focus) {
                return super.getListCellRendererComponent(list, value == null ? "" : tr("options.page." + value),
                        index, selected, focus);
            }
        });
        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        top.setOpaque(false);
        top.add(new JLabel(tr("options.category")));
        top.add(Box.createHorizontalStrut(8));
        top.add(category);
        category.addActionListener(e -> cards.show(pages, (String) category.getSelectedItem()));
        content.add(top, BorderLayout.NORTH);
        pages.setOpaque(false);
        pages.add(editor, EDITOR_DEFAULTS);
        pages.add(display, DISPLAY_SETTINGS);
        pages.add(dateFormat, DATE_FORMAT);
        pages.add(printer, PRINTER_SETTINGS);
        pages.add(languagePage(), LANGUAGE);
        content.add(pages, BorderLayout.CENTER);

        JButton ok = new JButton(UIManager.getString("OptionPane.okButtonText"));
        JButton apply = new JButton(tr("options.apply"));
        JButton cancel = new JButton(UIManager.getString("OptionPane.cancelButtonText"));
        ok.addActionListener(e -> {
            handler.accept(read());
            applyLanguage();
            dispose();
        });
        apply.addActionListener(e -> {
            current = read();
            handler.accept(current);
            applyLanguage();
        });
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        buttons.setOpaque(false);
        buttons.add(ok);
        buttons.add(apply);
        buttons.add(cancel);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        getRootPane().setDefaultButton(ok);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }

    private CalendarPreferences read() {
        return printer.apply(dateFormat.apply(display.apply(editor.apply(current))));
    }

    private JPanel languagePage() {
        JPanel page = new JPanel(new GridBagLayout());
        page.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.NORTHWEST;
        c.insets = new Insets(4, 4, 4, 8);
        JLabel label = Mnemonics.apply(new JLabel(), Messages.commons(Texts.messages().locale()).get("language.label"));
        label.setLabelFor(language);
        page.add(label, c);
        c.gridx = 1;
        c.weightx = 1;
        c.weighty = 1;
        page.add(language, c);
        return page;
    }

    private void applyLanguage() {
        if (language.selectedLanguage().equals(locales.applicationLanguage())) return;
        try {
            locales.setApplicationLanguage(language.selectedLanguage());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), getTitle(), JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (locales.restartRequired()) {
            JOptionPane.showMessageDialog(this, Messages.commons(Texts.messages().locale())
                    .format("language.restart", tr("app.title")), getTitle(), JOptionPane.INFORMATION_MESSAGE);
        }
    }

    public void open(CalendarPreferences preferences, String page) {
        current = preferences;
        editor.set(preferences);
        display.set(preferences);
        dateFormat.set(preferences);
        printer.set(preferences);
        category.setSelectedItem(page);
        cards.show(pages, page);
        pack();
        setLocationRelativeTo(getOwner());
        setVisible(true);
    }
}
