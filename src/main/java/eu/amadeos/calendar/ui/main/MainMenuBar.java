package eu.amadeos.calendar.ui.main;

import eu.amadeos.calendar.domain.CalendarView;
import eu.amadeos.commons.i18n.Mnemonics;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

import static eu.amadeos.calendar.i18n.Texts.tr;

/** The dtcm menu bar: File, Edit, View and Help. */
final class MainMenuBar extends JMenuBar {
    private final Map<CalendarView, JRadioButtonMenuItem> views = new EnumMap<>(CalendarView.class);

    MainMenuBar(MainPresenter presenter, MainWindow window) {
        int ctrl = InputEvent.CTRL_DOWN_MASK;

        JMenu file = menu("menu.file");
        file.add(item(tr("menu.file.print-view"), KeyStroke.getKeyStroke(KeyEvent.VK_P, ctrl), presenter::printCurrentView));
        file.add(item(tr("menu.file.print"), null, presenter::openPrintDialog));
        file.addSeparator();
        file.add(item(tr("menu.file.options"), null, presenter::openOptions));
        file.addSeparator();
        file.add(item(tr("menu.file.exit"), KeyStroke.getKeyStroke(KeyEvent.VK_Q, ctrl), presenter::exit));
        add(file);

        JMenu edit = menu("menu.edit");
        edit.add(item(tr("menu.edit.appointment"), KeyStroke.getKeyStroke(KeyEvent.VK_A, ctrl), presenter::openAppointmentEditor));
        edit.add(item(tr("menu.edit.todo"), KeyStroke.getKeyStroke(KeyEvent.VK_T, ctrl), presenter::openToDoEditor));
        add(edit);

        JMenu view = menu("menu.view");
        ButtonGroup group = new ButtonGroup();
        int[] keys = {KeyEvent.VK_1, KeyEvent.VK_2, KeyEvent.VK_3, KeyEvent.VK_4};
        for (CalendarView v : CalendarView.values()) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(tr("view." + v.name().toLowerCase(Locale.ROOT)));
            item.setAccelerator(KeyStroke.getKeyStroke(keys[v.ordinal()], ctrl));
            item.addActionListener(e -> presenter.showView(v));
            group.add(item);
            views.put(v, item);
            view.add(item);
        }
        view.addSeparator();
        view.add(item(tr("menu.view.previous"), KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0), presenter::previous));
        view.add(item(tr("menu.view.today"), KeyStroke.getKeyStroke(KeyEvent.VK_HOME, ctrl), presenter::today));
        view.add(item(tr("menu.view.next"), KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0), presenter::next));
        view.add(item(tr("menu.view.goto"), KeyStroke.getKeyStroke(KeyEvent.VK_G, ctrl), presenter::goToDate));
        view.addSeparator();
        view.add(item(tr("menu.view.appointment-list"), null, presenter::openAppointmentList));
        view.add(item(tr("menu.view.todo-list"), null, presenter::openToDoList));
        view.add(item(tr("menu.view.find"), KeyStroke.getKeyStroke(KeyEvent.VK_F, ctrl), presenter::openFind));
        add(view);

        add(Box.createHorizontalGlue());
        JMenu help = menu("menu.help");
        help.add(item(tr("menu.help.about"), null, window::showAbout));
        add(help);
    }

    void setView(CalendarView v) {
        views.get(v).setSelected(true);
    }

    private static JMenu menu(String key) {
        return Mnemonics.apply(new JMenu(), tr(key));
    }

    private static JMenuItem item(String text, KeyStroke accelerator, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        if (accelerator != null) item.setAccelerator(accelerator);
        item.addActionListener(e -> action.run());
        return item;
    }
}
