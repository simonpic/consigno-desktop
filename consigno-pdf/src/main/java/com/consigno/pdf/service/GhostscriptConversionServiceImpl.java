package com.consigno.pdf.service;

import com.consigno.common.exception.PdfConversionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Implémentation de {@link PdfConversionService} utilisant Ghostscript comme moteur de conversion.
 *
 * <p>Détection de Ghostscript (par ordre de priorité) :
 * <ol>
 *   <li>Chemin embarqué (jpackage) : {@code ${app.dir}/ghostscript/bin/gswin64c.exe} sur Windows,
 *       {@code ${app.dir}/ghostscript/bin/gs} sur Linux/macOS</li>
 *   <li>Fallback : commandes {@code gswin64c}, {@code gswin32c} ou {@code gs} dans le {@code PATH} système</li>
 * </ol>
 */
public class GhostscriptConversionServiceImpl implements PdfConversionService {

    private static final Logger log = LoggerFactory.getLogger(GhostscriptConversionServiceImpl.class);
    private static final int TIMEOUT_SECONDS = 120;
    private static final boolean IS_WINDOWS =
            System.getProperty("os.name", "").toLowerCase().contains("win");

    @Override
    public void convertToPdfA(Path inputPdf, Path outputPdf) throws PdfConversionException {
        String gsExecutable = findGhostscript();

        List<String> command = List.of(
                gsExecutable,
                "-dBATCH",
                "-dNOPAUSE",
                "-sDEVICE=pdfwrite",
                "-dPDFA=1",
                "-dPDFACompatibilityPolicy=1",
                "-sProcessColorModel=DeviceRGB",
                "-sOutputFile=" + outputPdf.toAbsolutePath(),
                inputPdf.toAbsolutePath().toString()
        );

        log.debug("Conversion PDF/A — commande : {}", command);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new PdfConversionException("Impossible de démarrer Ghostscript : " + gsExecutable, e);
        }

        String output;
        try {
            output = new String(process.getInputStream().readAllBytes());
        } catch (IOException e) {
            process.destroyForcibly();
            throw new PdfConversionException("Erreur de lecture de la sortie Ghostscript", e);
        }

        boolean finished;
        try {
            finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new PdfConversionException("Conversion interrompue", e);
        }

        if (!finished) {
            process.destroyForcibly();
            throw new PdfConversionException(
                    "Ghostscript n'a pas terminé dans le délai imparti (" + TIMEOUT_SECONDS + "s)");
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            log.error("Ghostscript a échoué (code {}). Sortie :\n{}", exitCode, output);
            try { Files.deleteIfExists(outputPdf); } catch (IOException ignored) {}
            throw new PdfConversionException(
                    "Ghostscript a échoué (code " + exitCode + "). Détails : " + output.strip());
        }

        log.info("Conversion PDF/A réussie : {} → {}", inputPdf.getFileName(), outputPdf.getFileName());
    }

    @Override
    public boolean isAvailable() {
        try {
            findGhostscript();
            return true;
        } catch (PdfConversionException e) {
            return false;
        }
    }

    // -------------------------------------------------------------------------

    private String findGhostscript() throws PdfConversionException {
        // 1. Chemin embarqué via jpackage (propriété app.dir)
        String appDir = System.getProperty("app.dir");
        if (appDir != null) {
            String gsExe = IS_WINDOWS ? "gswin64c.exe" : "gs";
            Path bundled = Path.of(appDir, "ghostscript", "bin", gsExe);
            if (Files.isExecutable(bundled)) {
                log.debug("Ghostscript embarqué trouvé : {}", bundled);
                return bundled.toString();
            }
        }

        // 2. Fallback : PATH système
        String[] candidates = IS_WINDOWS
                ? new String[]{"gswin64c", "gswin32c", "gs"}
                : new String[]{"gs"};

        for (String cmd : candidates) {
            if (isOnPath(cmd)) {
                log.debug("Ghostscript trouvé dans le PATH : {}", cmd);
                return cmd;
            }
        }

        throw new PdfConversionException(
                "Ghostscript est introuvable. Installez-le ou vérifiez l'installation de l'application.");
    }

    private boolean isOnPath(String command) {
        try {
            Process p = new ProcessBuilder(command, "--version")
                    .redirectErrorStream(true)
                    .start();
            p.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
            return p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return false;
        }
    }
}
