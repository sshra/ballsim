import javafx.fxml.FXML;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class GroupEditController {
    @FXML private TextField nameField;
    @FXML private Spinner<Integer> countSpinner;
    @FXML private TextField radiusField;
    @FXML private TextField massField;
    @FXML private ColorPicker colorPicker;

    private Group group;
    private SimulationController simulationController;
    private boolean isNew;
    private Stage stage;

    public void setGroup(Group g) { this.group = g; }
    public void setSimulationController(SimulationController c) { this.simulationController = c; }
    public void setIsNew(boolean isNew) { this.isNew = isNew; }

    @FXML
    public void initialize() {
        countSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 10000, 10));
    }

    // Вызывается после setGroup — здесь настраиваем двустороннюю привязку
    public void bindToGroup() {
        nameField.textProperty().bindBidirectional(group.nameProperty());
        colorPicker.valueProperty().bindBidirectional(group.colorProperty());

        // Spinner не поддерживает bindBidirectional напрямую — сделаем вручную
        countSpinner.getValueFactory().valueProperty().bindBidirectional(
                group.countProperty().asObject());

        // Для чисел — простые привязки с парсингом
        radiusField.setText(String.valueOf(group.getRadius()));
        massField.setText(String.valueOf(group.getMass()));

        radiusField.textProperty().addListener((obs, o, n) -> {
            try { group.setRadius(Double.parseDouble(n)); } catch (NumberFormatException ignored) {}
        });
        massField.textProperty().addListener((obs, o, n) -> {
            try { group.setMass(Double.parseDouble(n)); } catch (NumberFormatException ignored) {}
        });
    }

    /** Нужно вызвать после setGroup, например из openGroupEditor. */
    public void initForm() {
        bindToGroup();
    }

    public void setStage(Stage stage) { this.stage = stage; }

    @FXML
    private void onApply() {
        if (isNew) {
            simulationController.addGroup(group);
        }
        stage.close();
    }

    @FXML
    private void onCancel() {
        stage.close();
    }
}