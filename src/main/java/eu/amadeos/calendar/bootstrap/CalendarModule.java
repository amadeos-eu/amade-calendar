package eu.amadeos.calendar.bootstrap;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import eu.amadeos.calendar.repository.CalendarRepository;
import eu.amadeos.calendar.repository.IcsCalendarRepository;
import eu.amadeos.calendar.repository.PreferencesRepository;
import eu.amadeos.calendar.service.CalendarService;
import eu.amadeos.calendar.ui.main.MainPresenter;
import eu.amadeos.calendar.ui.main.MainView;
import eu.amadeos.calendar.ui.main.MainWindow;

/**
 * Calendar bindings. Executor, directories, theme, locale and desktop launcher come from
 * {@link eu.amadeos.commons.app.CommonsModule}.
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
