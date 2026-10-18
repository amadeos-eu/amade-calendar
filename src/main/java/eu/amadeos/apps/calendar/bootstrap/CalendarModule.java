package eu.amadeos.apps.calendar.bootstrap;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import eu.amadeos.apps.calendar.repository.CalendarRepository;
import eu.amadeos.apps.calendar.repository.IcsCalendarRepository;
import eu.amadeos.apps.calendar.repository.PreferencesRepository;
import eu.amadeos.apps.calendar.service.CalendarService;
import eu.amadeos.apps.calendar.ui.main.MainPresenter;
import eu.amadeos.apps.calendar.ui.main.MainView;
import eu.amadeos.apps.calendar.ui.main.MainWindow;

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
