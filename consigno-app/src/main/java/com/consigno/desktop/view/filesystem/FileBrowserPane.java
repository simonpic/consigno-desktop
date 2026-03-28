package com.consigno.desktop.view.filesystem;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Composant TreeView affichant l'arborescence du répertoire home,
 * filtré aux dossiers et fichiers PDF uniquement.
 *
 * <ul>
 *   <li>Double-clic sur un PDF → ouvre le document dans le viewer</li>
 *   <li>Clic droit sur un PDF → menu contextuel (Ouvrir, Signer,
 *       Valider les signatures, Révéler dans l'explorateur)</li>
 * </ul>
 */
public class FileBrowserPane extends VBox {

    private static final Logger log = LoggerFactory.getLogger(FileBrowserPane.class);

    private final TreeView<Path> treeView;

    private Consumer<Path> onPdfSelected;
    private Consumer<Path> onSignRequested;
    private Consumer<Path> onValidateRequested;
    private Consumer<Path> onConvertRequested;
    private Consumer<Path> onOpenWithAdobeRequested;
    private Consumer<Path> onDeleteRequested;
    private boolean        adobeAvailable = false;

    public FileBrowserPane() {
        Path root = Path.of(System.getProperty("user.home"));

        TreeItem<Path> rootItem = createDirItem(root);
        rootItem.setExpanded(true);

        treeView = new TreeView<>(rootItem);
        treeView.setShowRoot(true);
        treeView.setCellFactory(tv -> new PathTreeCell(
                path -> { if (onPdfSelected            != null) onPdfSelected.accept(path); },
                path -> { if (onSignRequested          != null) onSignRequested.accept(path); },
                path -> { if (onValidateRequested      != null) onValidateRequested.accept(path); },
                path -> { if (onConvertRequested       != null) onConvertRequested.accept(path); },
                path -> { if (onOpenWithAdobeRequested != null) onOpenWithAdobeRequested.accept(path); },
                path -> { if (onDeleteRequested        != null) onDeleteRequested.accept(path); },
                () -> adobeAvailable
        ));
        VBox.setVgrow(treeView, Priority.ALWAYS);

        // Double-clic gauche → ouvrir le PDF dans le viewer
        treeView.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                TreeItem<Path> selected = treeView.getSelectionModel().getSelectedItem();
                if (selected != null && selected.getValue() != null && isPdf(selected.getValue())) {
                    if (onPdfSelected != null) onPdfSelected.accept(selected.getValue());
                }
            }
        });

        getChildren().add(treeView);
        VBox.setVgrow(this, Priority.ALWAYS);
        setMaxHeight(Double.MAX_VALUE);
    }

    public void setOnPdfSelected(Consumer<Path> callback)            { this.onPdfSelected = callback; }
    public void setOnSignRequested(Consumer<Path> callback)          { this.onSignRequested = callback; }
    public void setOnValidateRequested(Consumer<Path> callback)      { this.onValidateRequested = callback; }
    public void setOnConvertRequested(Consumer<Path> callback)       { this.onConvertRequested = callback; }
    public void setOnOpenWithAdobeRequested(Consumer<Path> callback) { this.onOpenWithAdobeRequested = callback; }
    public void setOnDeleteRequested(Consumer<Path> callback)        { this.onDeleteRequested = callback; }
    public void setAdobeAvailable(boolean available)                 { this.adobeAvailable = available; }

    /** Recharge le nœud racine (utile après une signature in-place). */
    public void refresh() {
        TreeItem<Path> selected = treeView.getSelectionModel().getSelectedItem();
        Path selectedPath = selected != null ? selected.getValue() : null;

        if (selectedPath != null) {
            refreshParent(treeView.getRoot(), selectedPath.getParent());
        }
    }

    // -------------------------------------------------------------------------

    private void refreshParent(TreeItem<Path> node, Path targetDir) {
        if (node == null || node.getValue() == null) return;

        if (node.getValue().equals(targetDir) && node.isExpanded()) {
            node.getChildren().setAll(loadChildren(node.getValue()));
            return;
        }
        for (TreeItem<Path> child : node.getChildren()) {
            refreshParent(child, targetDir);
        }
    }

    private TreeItem<Path> createDirItem(Path dir) {
        TreeItem<Path> item = new TreeItem<>(dir);
        // Placeholder pour afficher la flèche d'expansion
        item.getChildren().add(new TreeItem<>(null));

        item.expandedProperty().addListener((obs, wasExpanded, isExpanded) -> {
            if (isExpanded && isPlaceholder(item)) {
                item.getChildren().setAll(loadChildren(dir));
            }
        });
        return item;
    }

    private boolean isPlaceholder(TreeItem<Path> item) {
        return item.getChildren().size() == 1
                && item.getChildren().get(0).getValue() == null;
    }

    private List<TreeItem<Path>> loadChildren(Path dir) {
        List<Path> dirs = new ArrayList<>();
        List<Path> pdfs = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, this::accept)) {
            for (Path p : stream) {
                if (Files.isDirectory(p)) dirs.add(p);
                else                      pdfs.add(p);
            }
        } catch (IOException e) {
            log.warn("Impossible de lister : {}", dir, e);
        }

        Comparator<Path> byName = Comparator.comparing(p -> p.getFileName().toString().toLowerCase());
        dirs.sort(byName);
        pdfs.sort(byName);

        List<TreeItem<Path>> items = new ArrayList<>(dirs.size() + pdfs.size());
        for (Path d : dirs) items.add(createDirItem(d));
        for (Path f : pdfs) items.add(new TreeItem<>(f));
        return items;
    }

    private boolean accept(Path path) {
        String name = path.getFileName().toString();
        if (name.startsWith(".")) return false;
        try {
            if (Files.isHidden(path)) return false;
        } catch (IOException e) {
            return false;
        }
        return Files.isDirectory(path) || isPdf(path);
    }

    private boolean isPdf(Path path) {
        if (Files.isDirectory(path)) return false;
        return path.getFileName().toString().toLowerCase().endsWith(".pdf");
    }

    // -------------------------------------------------------------------------
    // Cellule personnalisée
    // -------------------------------------------------------------------------

    private static final class PathTreeCell extends TreeCell<Path> {

        private final BooleanSupplier adobeAvailableSupplier;

        private final MenuItem openItem     = new MenuItem("Ouvrir");
        private final MenuItem signItem     = new MenuItem("Signer");
        private final MenuItem validateItem = new MenuItem("Valider les signatures");
        private final MenuItem convertItem  = new MenuItem("Convertir en PDF/A");
        private final MenuItem revealItem   = new MenuItem("Révéler dans l'explorateur");
        private final MenuItem adobeItem    = new MenuItem("Ouvrir avec Adobe Acrobat");
        private final MenuItem deleteItem   = new MenuItem("Supprimer");
        private final SeparatorMenuItem separator       = new SeparatorMenuItem();
        private final SeparatorMenuItem separatorDelete = new SeparatorMenuItem();
        private final ContextMenu pdfContextMenu;

        PathTreeCell(Consumer<Path> openCb, Consumer<Path> signCb,
                     Consumer<Path> validateCb, Consumer<Path> convertCb,
                     Consumer<Path> adobeCb, Consumer<Path> deleteCb,
                     BooleanSupplier adobeAvailableSupplier) {
            this.adobeAvailableSupplier = adobeAvailableSupplier;

            openItem.setOnAction(e     -> openCb.accept(getItem()));
            signItem.setOnAction(e     -> signCb.accept(getItem()));
            validateItem.setOnAction(e -> validateCb.accept(getItem()));
            convertItem.setOnAction(e  -> convertCb.accept(getItem()));
            revealItem.setOnAction(e   -> reveal(getItem()));
            adobeItem.setOnAction(e    -> adobeCb.accept(getItem()));
            deleteItem.setOnAction(e   -> deleteCb.accept(getItem()));

            pdfContextMenu = new ContextMenu(
                    openItem, signItem, validateItem, convertItem,
                    adobeItem, separator, revealItem, separatorDelete, deleteItem);
        }

        @Override
        protected void updateItem(Path item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                setContextMenu(null);
                return;
            }

            boolean isDir = Files.isDirectory(item);
            String icon = isDir ? "\uD83D\uDCC1" : "\uD83D\uDCC4"; // 📁 ou 📄
            setText(icon + "  " + item.getFileName());
            setGraphic(null);

            if (isDir) {
                setContextMenu(null);
            } else {
                adobeItem.setVisible(adobeAvailableSupplier.getAsBoolean());
                setContextMenu(pdfContextMenu);
            }
        }

        private static void reveal(Path pdf) {
            try {
                Desktop.getDesktop().browseFileDirectory(pdf.toFile());
            } catch (UnsupportedOperationException | NullPointerException ex) {
                // Fallback : ouvrir le dossier parent
                try {
                    Desktop.getDesktop().open(pdf.getParent().toFile());
                } catch (IOException ignored) {
                    // Rien à faire si le système ne supporte pas Desktop
                }
            }
        }
    }
}
