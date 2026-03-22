package com.consigno.desktop.view.filesystem;

import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Composant TreeView affichant l'arborescence du répertoire home,
 * filtré aux dossiers et fichiers PDF uniquement.
 */
public class FileBrowserPane extends VBox {

    private static final Logger log = LoggerFactory.getLogger(FileBrowserPane.class);

    private final TreeView<Path> treeView;
    private Consumer<Path> onPdfSelected;

    public FileBrowserPane() {
        Path root = Path.of(System.getProperty("user.home"));

        TreeItem<Path> rootItem = createDirItem(root);
        rootItem.setExpanded(true);

        treeView = new TreeView<>(rootItem);
        treeView.setShowRoot(true);
        treeView.setCellFactory(tv -> new PathTreeCell());
        VBox.setVgrow(treeView, Priority.ALWAYS);

        treeView.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected != null && selected.getValue() != null && isPdf(selected.getValue())) {
                if (onPdfSelected != null) {
                    onPdfSelected.accept(selected.getValue());
                }
            }
        });

        getChildren().add(treeView);
        VBox.setVgrow(this, Priority.ALWAYS);
        setMaxHeight(Double.MAX_VALUE);
    }

    public void setOnPdfSelected(Consumer<Path> callback) {
        this.onPdfSelected = callback;
    }

    /** Recharge le nœud racine (utile après une signature in-place). */
    public void refresh() {
        TreeItem<Path> selected = treeView.getSelectionModel().getSelectedItem();
        Path selectedPath = selected != null ? selected.getValue() : null;

        // Recharger les enfants du parent du fichier sélectionné
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
        List<Path> dirs  = new ArrayList<>();
        List<Path> pdfs  = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, this::accept)) {
            for (Path p : stream) {
                if (Files.isDirectory(p)) {
                    dirs.add(p);
                } else {
                    pdfs.add(p);
                }
            }
        } catch (IOException e) {
            log.warn("Impossible de lister : {}", dir, e);
        }

        Comparator<Path> byName = Comparator.comparing(p -> p.getFileName().toString().toLowerCase());
        dirs.sort(byName);
        pdfs.sort(byName);

        List<TreeItem<Path>> items = new ArrayList<>(dirs.size() + pdfs.size());
        for (Path d : dirs)  items.add(createDirItem(d));
        for (Path f : pdfs)  items.add(new TreeItem<>(f));
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
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".pdf");
    }

    // -------------------------------------------------------------------------
    // Cellule personnalisée
    // -------------------------------------------------------------------------

    private static final class PathTreeCell extends TreeCell<Path> {
        @Override
        protected void updateItem(Path item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            boolean isDir = Files.isDirectory(item);
            String icon = isDir ? "\uD83D\uDCC1" : "\uD83D\uDCC4"; // 📁 ou 📄
            setText(icon + "  " + item.getFileName());
            setGraphic(null);
        }
    }
}
