package com.consigno.desktop.controller.handler;

import java.nio.file.Path;

/**
 * Résultat du dialog de sélection de certificat et mot de passe.
 */
public record CertAndPassword(Path p12Path, char[] password) {}
