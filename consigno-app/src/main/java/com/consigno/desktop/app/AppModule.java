package com.consigno.desktop.app;

import com.consigno.desktop.service.NotificationService;
import com.consigno.desktop.service.SystemService;
import com.consigno.desktop.service.SystemServiceImpl;
import com.consigno.desktop.view.pdf.PdfViewerPane;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import jakarta.inject.Singleton;

/**
 * Module Guice — services et composants UI de l'application.
 */
public class AppModule extends AbstractModule {

    @Override
    protected void configure() {
        // NotificationService — singleton eagerly créé au démarrage
        bind(NotificationService.class).in(Singleton.class);

        // PdfViewerPane — singleton (un seul viewer dans l'application)
        bind(PdfViewerPane.class).in(Singleton.class);
    }

    @Provides
    @Singleton
    SystemService provideSystemService() {
        return new SystemServiceImpl();
    }
}
