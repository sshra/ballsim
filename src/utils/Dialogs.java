package utils;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;

import java.util.Optional;

public final class Dialogs {
    private Dialogs() {}

    public static void info(Window owner, String title, String message) {
        show(owner, Alert.AlertType.INFORMATION, title, message);
    }

    public static void error(Window owner, String title, String message) {
        show(owner, Alert.AlertType.ERROR, title, message);
    }

    public static void warn(Window owner, String title, String message) {
        show(owner, Alert.AlertType.WARNING, title, message);
    }

    public static boolean confirm(Window owner, String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(owner);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    private static void show(Window owner, Alert.AlertType type,
                             String title, String message) {
        Alert alert = new Alert(type);
        alert.initOwner(owner);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}