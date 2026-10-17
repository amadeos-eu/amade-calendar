package org.datazuul.amadeos.calendar.bootstrap;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import org.datazuul.amadeos.calendar.repository.CalendarRepository;
import org.datazuul.amadeos.calendar.repository.IcsCalendarRepository;
import org.datazuul.amadeos.calendar.repository.PreferencesRepository;
import org.datazuul.amadeos.calendar.service.CalendarService;
import org.datazuul.amadeos.calendar.ui.main.MainPresenter;
import org.datazuul.amadeos.calendar.ui.main.MainView;
import org.datazuul.amadeos.calendar.ui.main.MainWindow;

/**
 * Calendar bindings. Executor, directories, theme, locale and desktop launcher come from
 * {@link org.datazuul.amadeos.commons.app.CommonsModule}.
 */
public final class CalendarModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(CalendarRepository.class).to(IcsCalendarRepository.class).in(Scopes.SINGLETON);
        bind(PreferencesRepository.class).in(Scopes.SINGLETON);
        bind(CalendarService.class).in(Scopes.SINGLETON);
        bind(MainWindow.class).in(Scopes.SINGLETON);
        bind(MainView.class).to(MainWindow.class);
        bind(MainPresenter.class).in(Scopes.SINGLETON);
    }
}
