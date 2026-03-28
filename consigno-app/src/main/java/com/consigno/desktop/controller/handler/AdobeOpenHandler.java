package com.consigno.desktop.controller.handler;

import com.consigno.desktop.service.NotificationService;
import com.consigno.desktop.service.SystemService;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Gère l'ouverture d'un PDF avec Adobe Acrobat.
 */
public final class AdobeOpenHandler {

    private final SystemService systemService;
    private final NotificationService notificationService;

    public AdobeOpenHandler(SystemService systemService, NotificationService notificationService) {
        this.systemService = systemService;
        this.notificationService = notificationService;
    }

    public void handle(Path path) {
        try {
            systemService.openWithAdobeAcrobat(path);
        } catch (IOException e) {
            notificationService.showError("Impossible d'ouvrir avec Adobe Acrobat", e);
        }
    }
}
