import controlles.SimulationController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main extends Application {
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/resources/com/fx/main.fxml"));
        Parent root = loader.load();
        SimulationController mainController = loader.getController();
        mainController.setPrimaryStage(stage);

        stage.setTitle("Симуляция шариков");
        var scene = new Scene(root, SimulationController.INITIAL_WIDTH, SimulationController.INITIAL_HEIGHT);
        stage.setScene(scene);
        stage.show();
        stage.setOnCloseRequest(_ -> mainController.onClose());
    }
}