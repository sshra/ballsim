import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SimulationController {
    @FXML private BorderPane borderPane;
    @FXML private Canvas canvas;
    @FXML private MenuItem runToggle;
    @FXML private Menu exitMenuButton;
    @FXML private MenuBar menuBar;
    @FXML private CheckMenuItem gravityToggle;
    @FXML private CheckMenuItem reflectionToggle;
    @FXML private CheckMenuItem highPrecisionToggle;
    @FXML private CheckMenuItem statsToggle;
    @FXML private Menu ballsMenu;

    final public static double INITIAL_WIDTH = 800.0;
    final public static double INITIAL_HEIGHT = 600.0;

    private GraphicsContext gc;
    private ObservableList<Group> groups = FXCollections.observableArrayList();
    private List<Ball> balls = new ArrayList<>();
    private AnimationTimer timer;
    private boolean running = false;
    private Stage primaryStage;

    private final SimulationStats stats = new SimulationStats();
    private StatsWindowController statsController;
    private Stage statsStage;

    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    @FXML
    private void toggleStats() {
        boolean statsVisible = statsToggle.isSelected();
        if (statsVisible) {
            statsStage.show();
            statsStage.toFront();
        } else {
            statsStage.hide();
        }
    }

    @FXML
    public void initialize() {
        gc = canvas.getGraphicsContext2D();
        // привязка размеров Canvas к контейнеру
        canvas.widthProperty().bind(borderPane.widthProperty());
        canvas.heightProperty().bind(
                Bindings.createDoubleBinding(
                        () -> borderPane.getHeight() - menuBar.getHeight(),
                        borderPane.heightProperty(),
                        menuBar.heightProperty()
                )
        );
        // начальные группы (можно добавить одну)
        groups.add(new Group(10000, 3.0, 1.0, Color.BLUE));
//        groups.add(new Group(1, 30.0, 1000.0, Color.RED));
        updateBallsFromGroups();

        // инициализация таймера
        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (running) {
                    updatePhysics(1.0/60.0);
                    render();

                    if (statsToggle.isSelected() && ++statsController.statsFrameCounter % statsController.STATS_UPDATE_EVERY == 0) {
                        statsController.update(stats);
                    }
                }
            }
        };

        // Подписываемся на изменения списка
        groups.addListener((ListChangeListener<Group>) change -> rebuildBallsMenu());
        rebuildBallsMenu();

        initStatsWindow();
    }

    private void initStatsWindow() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/resources/com/fx/stats-window.fxml"));
            Parent root = loader.load();
            statsController = loader.getController();

            statsStage = new Stage();
            statsStage.setTitle("Статистика");
            statsStage.initOwner(primaryStage);
            statsStage.initModality(Modality.NONE);
            statsStage.setScene(new Scene(root));
            // Не показываем сразу — ждём, когда пользователь включит флаг

            statsStage.setOnShowing(e -> {
                statsStage.setX(primaryStage.getX() + primaryStage.getWidth() + 10);
                statsStage.setY(primaryStage.getY());
            });

            statsStage.setOnHidden(e -> {
                statsToggle.setSelected(false);
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private final Map<Group, Stage> editorStages = new HashMap<>();

    private void openGroupEditor(Group group, boolean isNew) {
        // Если форма для этой группы уже открыта — просто фокусируемся
        Stage existing = editorStages.get(group);
        if (existing != null && existing.isShowing()) {
            existing.toFront();
            existing.requestFocus();
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/resources/com/fx/group-edit.fxml"));
            Parent root = loader.load();

            GroupEditController controller = loader.getController();

            Stage stage = new Stage();
            stage.setTitle(isNew ? "Новая группа" : "Редактирование группы");
            stage.initModality(Modality.NONE);          // немодальное
            stage.initOwner(primaryStage);              // остаётся поверх главного окна
            stage.setScene(new Scene(root, 350, 400));

            // Настраиваем контроллер формы
            controller.setGroup(group);
            controller.setSimulationController(this);
            controller.setIsNew(isNew);
            controller.setStage(stage);   // ← передаём stage в контроллер
            controller.initForm();        // ← привязываем поля к модели

            // Обработчик закрытия окна:
            // - если это новая группа и пользователь нажал "Отмена" — не добавляем
            // - если новая и OK — добавляем в список
            stage.setOnCloseRequest(e -> editorStages.remove(group));

            editorStages.put(group, stage);
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void rebuildBallsMenu() {
        ballsMenu.getItems().clear();

        for (Group g : groups) {
            MenuItem item = new MenuItem();
            // текст всегда отражает текущее имя — привяжемся к свойству
            item.textProperty().bind(g.nameProperty());
            item.setOnAction(e -> openGroupEditor(g, false));
            ballsMenu.getItems().add(item);
        }

        if (!groups.isEmpty()) {
            ballsMenu.getItems().add(new SeparatorMenuItem());
        }

        MenuItem addItem = new MenuItem("Добавить…");
        addItem.setOnAction(e -> {
            Group newGroup = new Group("Группа " + (groups.size() + 1), 10, 10, 1, Color.BLUE);
            openGroupEditor(newGroup, true); // true = это новая группа
        });
        ballsMenu.getItems().add(addItem);
    }

    private void regenerateBalls() {
        balls.clear();
        double width = canvas.getWidth() > 0 ? canvas.getWidth() : INITIAL_WIDTH;
        double height = canvas.getHeight() > 0 ? canvas.getHeight(): INITIAL_WIDTH;
        // для каждой группы создаём нужное количество шариков со случайными позициями
        for (Group g : groups) {
            for (int i = 0; i < g.getCount(); i++) {
                balls.add(new Ball(width, height, g.getRadius(), g.getMass(), g.getColor()));
            }
        }
    }

    private void updatePhysics(double dt) {
        var physics = new SimulationPhysics(balls, stats);
        physics.isHighPrecision = highPrecisionToggle.isSelected();
        physics.isGravityEnabled = gravityToggle.isSelected();
        physics.isReflectionEnabled = reflectionToggle.isSelected();
        physics.isStatsEnabled = reflectionToggle.isSelected();

        physics.boxHeight = canvas.getHeight();
        physics.boxWidth = canvas.getWidth();

        physics.doCalc(dt);
    }

    private void render() {
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        for (Ball b : balls) {
            gc.setFill(b.getColor());
            double radius = b.radius;
            gc.fillOval(b.x - radius, b.y - radius,
                    2 * radius, 2 * radius);
        }
    }

    @FXML private void updateBallsFromGroups() {
        regenerateBalls();
        updatePhysics( 1 / 120);
        render();
    }

    @FXML private void toggleRun() {
        if (running) {
            timer.stop();
            runToggle.setText("Пуск");
        } else {
            timer.start();
            runToggle.setText("Стоп");
        }
        running = !running;
    }

    @FXML private void toggleGravity() {
    }

    @FXML private void toggleReflection() {
    }

    public void applyGroups(ObservableList<Group> newGroups) {
        groups.setAll(newGroups);
        regenerateBalls();
    }

    @FXML private void exit() {
        Platform.exit(); }

    public ObservableList<Group> getGroups() {
        return groups;
    }

    public void addGroup(Group group) {
        groups.add(group);
    }

    public void onClose() {
        statsStage.close();
    }
}
