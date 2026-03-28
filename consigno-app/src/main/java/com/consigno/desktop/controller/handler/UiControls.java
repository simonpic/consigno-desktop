package com.consigno.desktop.controller.handler;

/**
 * Abstraction des éléments UI (champs @FXML) utilisés par les handlers.
 * Implémentée dans MainController après initialize().
 */
public interface UiControls {
    void setStatus(String message);
    void setBusy(boolean busy);
    void setSignButtonDisabled(boolean disabled);
    void setSignButtonVisible(boolean visible);
    void refreshFileBrowser();
}
