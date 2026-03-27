package com.consigno.desktop.app;

import com.consigno.crypto.service.CryptoService;
import com.consigno.pdf.service.GhostscriptConversionServiceImpl;
import com.consigno.pdf.service.PdfBoxFormServiceImpl;
import com.consigno.pdf.service.PdfBoxRenderServiceImpl;
import com.consigno.pdf.service.PdfBoxSignatureServiceImpl;
import com.consigno.pdf.service.PdfBoxValidationServiceImpl;
import com.consigno.pdf.service.PdfConversionService;
import com.consigno.pdf.service.PdfFormService;
import com.consigno.pdf.service.PdfRenderService;
import com.consigno.pdf.service.PdfSignatureService;
import com.consigno.pdf.service.PdfValidationService;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import jakarta.inject.Singleton;

/**
 * Module Guice — liaison des services PDF.
 *
 * <p>Utilise {@code @Provides @Singleton} pour construire manuellement
 * les implémentations PDFBox, en passant les dépendances via paramètres.
 */
public class PdfModule extends AbstractModule {

    @Override
    protected void configure() {
        // Liaisons dans les méthodes @Provides ci-dessous
    }

    @Provides
    @Singleton
    PdfSignatureService providePdfSignatureService(CryptoService cryptoService) {
        return new PdfBoxSignatureServiceImpl(cryptoService);
    }

    @Provides
    @Singleton
    PdfValidationService providePdfValidationService() {
        return new PdfBoxValidationServiceImpl();
    }

    @Provides
    @Singleton
    PdfFormService providePdfFormService() {
        return new PdfBoxFormServiceImpl();
    }

    @Provides
    @Singleton
    PdfRenderService providePdfRenderService() {
        return new PdfBoxRenderServiceImpl();
    }

    @Provides
    @Singleton
    PdfConversionService providePdfConversionService() {
        return new GhostscriptConversionServiceImpl();
    }
}
