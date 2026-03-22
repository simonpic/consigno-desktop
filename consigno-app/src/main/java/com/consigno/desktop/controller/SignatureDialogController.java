package com.consigno.desktop.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Path;

/**
 * Contrôleur de la popup de signature électronique (signature-dialog.fxml).
 */
public class SignatureDialogController {

    @FXML private TextField     certField;
    @FXML private PasswordField pwdField;
    @FXML private Button        browseBtn;

    private Path selectedCertPath;

    @FXML
    private void handleBrowse() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Sélectionner un certificat");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Certificats PKCS#12", "*.p12", "*.pfx"),
                new FileChooser.ExtensionFilter("Tous les fichiers", "*.*"));

        File file = chooser.showOpenDialog(browseBtn.getScene().getWindow());
        if (file != null) {
            selectedCertPath = file.toPath();
            certField.setText(file.getName());
            certField.setTooltip(new Tooltip(file.getAbsolutePath()));
            pwdField.requestFocus();
        }
    }

    public Path getSelectedCertPath() { return selectedCertPath; }
    public TextField getCertField()    { return certField; }
    public PasswordField getPwdField() { return pwdField; }
    public Button getBrowseBtn()       { return browseBtn; }
}
