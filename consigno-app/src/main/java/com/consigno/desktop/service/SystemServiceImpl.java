package com.consigno.desktop.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implémentation de {@link SystemService}.
 *
 * <p>La détection d'Adobe Acrobat est effectuée une seule fois à la construction
 * (ce service est un singleton Guice). Le résultat est mis en cache dans {@link #adobeAcrobatPath}.
 */
public class SystemServiceImpl implements SystemService {

    private static final Logger log = LoggerFactory.getLogger(SystemServiceImpl.class);

    private final Optional<Path> adobeAcrobatPath;

    public SystemServiceImpl() {
        this.adobeAcrobatPath = detectAdobeAcrobat();
        adobeAcrobatPath.ifPresentOrElse(
                p -> log.info("Adobe Acrobat détecté : {}", p),
                ()  -> log.debug("Adobe Acrobat non détecté sur ce système")
        );
    }

    @Override
    public Optional<Path> getAdobeAcrobatPath() {
        return adobeAcrobatPath;
    }

    @Override
    public void openWithAdobeAcrobat(Path pdf) throws IOException {
        Path adobe = adobeAcrobatPath.orElseThrow(() ->
                new UnsupportedOperationException("Adobe Acrobat n'est pas disponible"));

        String os = System.getProperty("os.name", "").toLowerCase();
        List<String> command = new ArrayList<>();

        if (os.contains("win")) {
            command.add(adobe.toString());
            command.add(pdf.toAbsolutePath().toString());
        } else if (os.contains("mac")) {
            // adobe est le chemin du bundle .app — on utilise `open -a`
            command.add("open");
            command.add("-a");
            command.add(adobe.toString());
            command.add(pdf.toAbsolutePath().toString());
        } else {
            // Linux : acroread <fichier>
            command.add(adobe.toString());
            command.add(pdf.toAbsolutePath().toString());
        }

        log.debug("Ouverture avec Adobe : {}", command);
        new ProcessBuilder(command).start();
    }

    // -------------------------------------------------------------------------

    private static Optional<Path> detectAdobeAcrobat() {
        String os = System.getProperty("os.name", "").toLowerCase();

        if (os.contains("win")) {
            return detectWindows();
        } else if (os.contains("mac")) {
            return detectMacOs();
        } else {
            return detectLinux();
        }
    }

    private static Optional<Path> detectWindows() {
        String pf64 = System.getenv("ProgramFiles");
        String pf86 = System.getenv("ProgramFiles(x86)");

        List<String> candidates = new ArrayList<>();
        if (pf64 != null) {
            candidates.add(pf64 + "\\Adobe\\Acrobat DC\\Acrobat\\Acrobat.exe");
            candidates.add(pf64 + "\\Adobe\\Acrobat 2024\\Acrobat\\Acrobat.exe");
            candidates.add(pf64 + "\\Adobe\\Acrobat 2023\\Acrobat\\Acrobat.exe");
            candidates.add(pf64 + "\\Adobe\\Acrobat 2020\\Acrobat\\Acrobat.exe");
        }
        if (pf86 != null) {
            candidates.add(pf86 + "\\Adobe\\Acrobat Reader DC\\Reader\\AcroRd32.exe");
            candidates.add(pf86 + "\\Adobe\\Reader 11.0\\Reader\\AcroRd32.exe");
            candidates.add(pf86 + "\\Adobe\\Reader 9.0\\Reader\\AcroRd32.exe");
        }

        return candidates.stream()
                .map(Path::of)
                .filter(Files::isExecutable)
                .findFirst();
    }

    private static Optional<Path> detectMacOs() {
        List<String> bundles = List.of(
                "/Applications/Adobe Acrobat DC.app",
                "/Applications/Adobe Acrobat Reader DC.app",
                "/Applications/Adobe Acrobat.app",
                "/Applications/Adobe Acrobat Reader.app"
        );

        return bundles.stream()
                .map(Path::of)
                .filter(Files::isDirectory)   // un .app est un dossier
                .findFirst();
    }

    private static Optional<Path> detectLinux() {
        // acroread est rare sur Linux mais on teste quand même
        return List.of("/usr/bin/acroread", "/usr/local/bin/acroread").stream()
                .map(Path::of)
                .filter(Files::isExecutable)
                .findFirst();
    }
}
