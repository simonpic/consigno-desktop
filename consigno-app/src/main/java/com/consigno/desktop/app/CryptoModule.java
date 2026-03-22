package com.consigno.desktop.app;

import com.consigno.crypto.service.BouncyCastleCryptoServiceImpl;
import com.consigno.crypto.service.CryptoService;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import jakarta.inject.Singleton;

/**
 * Module Guice — liaison du service cryptographique.
 *
 * <p>Utilise {@code @Provides @Singleton} pour éviter l'instanciation
 * par réflexion de {@link BouncyCastleCryptoServiceImpl}.
 */
public class CryptoModule extends AbstractModule {

    @Override
    protected void configure() {
        // Liaisons déclaratives dans les méthodes @Provides ci-dessous
    }

    @Provides
    @Singleton
    CryptoService provideCryptoService() {
        return new BouncyCastleCryptoServiceImpl();
    }
}
