package controlles;

import javafx.animation.AnimationTimer;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Cursor;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
import physics.*;
import utils.Dialogs;
import utils.SimulationSnapshot;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class SimulationController {
    @FXML private BorderPane borderPane;
    @FXML private Canvas canvas;
    @FXML private MenuItem runToggle;
    @FXML private MenuItem saveMenuItem;
    @FXML private MenuItem loadMenuItem;
    @FXML private MenuItem updateMenuItem;
    @FXML private Menu exitMenuButton;
    @FXML private MenuBar menuBar;
    @FXML private CheckMenuItem gravityToggle;
    @FXML private CheckMenuItem reflectionToggle;
    @FXML private CheckMenuItem highPrecisionToggle;
    @FXML private CheckMenuItem nonLoseFriction;
    @FXML private CheckMenuItem statsToggle;

    @FXML private Menu ballsMenu;
    @FXML private Label statusLabel;
    @FXML private HBox bottomBox;

    final public static double INITIAL_WIDTH = 800.0;
    final public static double INITIAL_HEIGHT = 600.0;
    // Целевая частота физики
    private static final double PHYSICS_HZ = 60.0;
    private static final long PHYSICS_PERIOD_NANOS = (long)(1e9 / PHYSICS_HZ);

    // --- Состояние ---
    private Thread physicsThread;
    private volatile boolean physicsRunning = false;
    private volatile SimulationPhysics currentPhysics;
    private boolean pendingStart = false;
    private Timeline phasePollTimeline;
    private final ReentrantLock stateLock = new ReentrantLock();

    // Текущая позиция камеры (левый верхний угол видимой области)
    private double camX = 0;
    private double camY = 0;

    // Начало drag — запоминаем, чтобы считать дельту
    private double dragStartMouseX, dragStartMouseY;
    private double dragStartCamX, dragStartCamY;

    private GraphicsContext gc;
    private ObservableList<Group> groups = FXCollections.observableArrayList();
    private List<Ball> balls = new ArrayList<>();
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
                        () -> borderPane.getHeight() - menuBar.getHeight() - bottomBox.getHeight(),
                        borderPane.heightProperty(),
                        menuBar.heightProperty(),
                        bottomBox.heightProperty()
                )
        );

        canvas.widthProperty().addListener((o, ov, nv) -> render());
        canvas.heightProperty().addListener((o, ov, nv) -> render());

        // начальные группы (можно добавить одну)
        groups.add(new Group(10000, 3.0, 1.0, 0.0, Color.BLUE));
//        groups.add(new Group(3, 50.0, 1.0, 0.25, Color.RED));
        updateBallsFromGroups();

        // инициализация таймера
        // 1. Рендер — только если удалось взять блокировку
        // 2. Обновление статистики — тоже в UI-потоке, тоже под tryLock
        // ← здесь всё как раньше
        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                // 1. Рендер — только если удалось взять блокировку
                render();

                // 2. Обновление статистики — тоже в UI-потоке, тоже под tryLock
                if (statsToggle.isSelected()
                        && ++statsController.statsFrameCounter % statsController.STATS_UPDATE_EVERY == 0) {
                    statsController.update(stats);
                    statsStage.sizeToScene();// ← здесь всё как раньше
                }
            }
        };
        timer.start();

        // Подписываемся на изменения списка
        groups.addListener((ListChangeListener<Group>) change -> rebuildBallsMenu());
        rebuildBallsMenu();

        initStatsWindow();

        // Отслеживание таскания экрана.
        initDragFeature();
    }

    private void initDragFeature() {
        canvas.setOnMousePressed(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                dragStartMouseX = e.getX();
                dragStartMouseY = e.getY();
                dragStartCamX = camX;
                dragStartCamY = camY;
                canvas.setCursor(Cursor.CLOSED_HAND);
            }
        });

        canvas.setOnMouseDragged(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                double dx = e.getX() - dragStartMouseX;
                double dy = e.getY() - dragStartMouseY;
                // Мышь вправо → мир «едет» вправо → камера уходит влево
                camX = dragStartCamX - dx;
                camY = dragStartCamY - dy;
                // render() вызовется из AnimationTimer, специально ничего не делаем
            }
        });

        canvas.setOnMouseReleased(e -> {
            canvas.setCursor(Cursor.DEFAULT);
        });

        // Курсор-«рука» при наведении — намёк, что можно таскать
        canvas.setCursor(Cursor.OPEN_HAND);
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
            statsStage.sizeToScene();
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
            stage.setScene(new Scene(root));
            stage.sizeToScene();
            stage.setResizable(false);

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
            Group newGroup = new Group("Группа " + (groups.size() + 1), 10,5, 10, 0.0, Color.BLUE);
            openGroupEditor(newGroup, true); // true = это новая группа
        });
        ballsMenu.getItems().add(addItem);
    }

    private void regenerateBalls() {
        balls.clear();
        double width = canvas.getWidth() > 0 ? canvas.getWidth() : INITIAL_WIDTH;
        double height = canvas.getHeight() > 0 ? canvas.getHeight(): INITIAL_HEIGHT;
        // для каждой группы создаём нужное количество шариков со случайными позициями
        for (Group g : groups) {
            for (int i = 0; i < g.getCount(); i++) {
                balls.add(new Ball(width, height, g.getRadius(), g.getMass(), g.getMu(), g.getColor()));
            }
        }
    }

    private void updatePhysics(double dt) {
        var physics = new SimulationPhysics(balls, stats);

        // Настройки.
        physics.isHighPrecision = highPrecisionToggle.isSelected();
        physics.isGravityEnabled = gravityToggle.isSelected();
        physics.isReflectionEnabled = reflectionToggle.isSelected();
        physics.isStatsEnabled = statsToggle.isSelected();
        physics.isNonLoseFriction = nonLoseFriction.isSelected();

        physics.boxHeight = canvas.getHeight();
        physics.boxWidth = canvas.getWidth();

        currentPhysics = physics;
        try {
            physics.doCalc(dt);
        } finally {
            currentPhysics = null;
        }
    }

    private void render() {
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        for (Ball b : balls) {
            gc.setFill(b.getColor());
            double sx = b.x - camX;
            double sy = b.y - camY;
            double r = b.radius;

            if (sx + r < 0 || sx - r > canvas.getWidth()) continue;
            if (sy + r < 0 || sy - r > canvas.getHeight()) continue;

            gc.fillOval(sx - r,sy - r,2 * r, 2 * r);

            if (b.omega != 0.0) {            // Спица — метка вращения
                gc.setStroke(Color.WHITE);
                gc.setLineWidth(1.5);
                double cos = Math.cos(b.angle);
                double sin = Math.sin(b.angle);
                gc.strokeLine(sx, sy, sx + r * cos, sy + r * sin);
            }
        }
    }

    @FXML private void updateBallsFromGroups() {
        regenerateBalls();
        // Пересчитаем, чтобы разлепить шары.
        updatePhysics( 1 / 1000);
        render();
    }

    @FXML private void toggleRun() {
        if (physicsRunning || pendingStart)
            stopSimulation();
        else
            startSimulation();
    }


    private void startSimulation() {
        if (physicsRunning) return;

        // На случай, если предыдущая остановка ещё не завершилась
        // (пользователь быстро нажал Стоп → Запуск)
        if (physicsThread != null && physicsThread.isAlive()) {
            statusLabel.setText("Жду завершения предыдущей сессии…");
            // Запустим после того, как предыдущая остановится
            pendingStart = true;
            return;
        }

        physicsRunning = true;
        runToggle.setText("Стоп");

        // Сброс фазы — чтобы UI не показывал старую
        stats.currentPhase = Phase.IDLE;

        // Блокируем некоторые пункты меню.
        saveMenuItem.setDisable(true);
        loadMenuItem.setDisable(true);
        updateMenuItem.setDisable(true);
        ballsMenu.setDisable(true);
        statusLabel.setText("Работает");

        // Создаём поток
        physicsThread = new Thread(this::physicsLoop, "physics");
        physicsThread.setDaemon(true);
        physicsThread.start();

        // Обновление статуса фазы во время работы
        startPhasePolling();
    }

    // --- Остановка ---
    private void stopSimulation() {

        if (pendingStart) {
            // Отменяем отложенный запуск
            pendingStart = false;
            statusLabel.setText("Остановлено");
            return;
        }
        if (!physicsRunning) return;

        physicsRunning = false;

        SimulationPhysics p = currentPhysics;
        if (p != null) p.cancel();

        statusLabel.setText("Останавливаю…");
        waitForPhysicsStopAsync();
        startPhasePolling();
    }

    // --- Асинхронное ожидание ---
    private void waitForPhysicsStopAsync() {
        Thread waiter = new Thread(() -> {
            try {
                if (physicsThread != null) physicsThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            Platform.runLater(this::onPhysicsStopped);
        }, "physics-waiter");
        waiter.setDaemon(true);
        waiter.start();
    }

    // --- Обработка завершения ---
    private void onPhysicsStopped() {
        if (phasePollTimeline != null) phasePollTimeline.stop();

        saveMenuItem.setDisable(false);
        loadMenuItem.setDisable(false);
        updateMenuItem.setDisable(false);
        ballsMenu.setDisable(false);
        runToggle.setText("Пуск");

        if (pendingStart) {
            pendingStart = false;
            Platform.runLater(this::startSimulation);
        } else {
            statusLabel.setText("Остановлено");
        }
    }

    // --- Опрос фазы ---
    private void startPhasePolling() {
        if (phasePollTimeline != null) phasePollTimeline.stop();

        phasePollTimeline = new Timeline(new KeyFrame(Duration.millis(50), e -> {
            if (physicsRunning) {
                statusLabel.setText("Работает · " + stats.currentPhase.title);
            } else if (physicsThread != null && physicsThread.isAlive()) {
                statusLabel.setText("Останавливаю… " + stats.currentPhase.title);
            } else {
                phasePollTimeline.stop();
            }
        }));
        phasePollTimeline.setCycleCount(Timeline.INDEFINITE);
        phasePollTimeline.play();
    }

    // --- Физический цикл ---
    private void physicsLoop() {
        long lastTime = System.nanoTime();

        while (physicsRunning) {
            long frameStart = System.nanoTime();
            double dt = Math.min((frameStart - lastTime) / 1e9, 0.05);
            lastTime = frameStart;

            stateLock.lock();
            try {
                updatePhysics(dt);
            } finally {
                stateLock.unlock();
            }

            long elapsed = System.nanoTime() - frameStart;
            long sleep = PHYSICS_PERIOD_NANOS - elapsed;
            if (sleep > 1_000_000) {
                try {
                    Thread.sleep(sleep / 1_000_000, (int)(sleep % 1_000_000));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
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

    private File lastSaveDir;

    @FXML
    private void save() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Сохранить симуляцию");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Симуляция (*.bs)", "*.bs"));
        chooser.setInitialFileName("simulation.bs");

        // Стартовая директория — где сохраняли в прошлый раз
        if (lastSaveDir != null) chooser.setInitialDirectory(lastSaveDir);

        File file = chooser.showSaveDialog(primaryStage);
        if (file == null) return; // пользователь отменил

        // Если пользователь не дописал расширение — добавим
        if (!file.getName().toLowerCase().endsWith(".bs")) {
            file = new File(file.getParentFile(), file.getName() + ".bs");
        }

        lastSaveDir = file.getParentFile();

        var s = SimulationSnapshot.toSnapshot(balls, groups);
        s.isGravityEnabled = gravityToggle.isSelected();
        s.isHighPrecision= highPrecisionToggle.isSelected();
        s.isReflectionEnabled = reflectionToggle.isSelected();
        s.isStatsEnabled = statsToggle.isSelected();
        s.isNonLoseFriction = nonLoseFriction.isSelected();

        s.boxWidth = canvas.getWidth();
        s.boxHeight = canvas.getHeight();
        s.camX = camX;
        s.camY = camY;

        try (ObjectOutputStream out =
                     new ObjectOutputStream(
                             new GZIPOutputStream(
                             new BufferedOutputStream(
                             new FileOutputStream(file))))) {

            out.writeObject(s);
            // Status bar / диалог об успехе
            Dialogs.info(primaryStage, "Сохранено", "Файл сохранён: " + file.getName());
        } catch (IOException e) {
            e.printStackTrace();
            Dialogs.error(primaryStage, "Ошибка сохранения", e.getMessage());
        }
    }

    @FXML
    private void load() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Загрузить симуляцию");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Симуляция (*.bs)", "*.bs"));
        if (lastSaveDir != null) chooser.setInitialDirectory(lastSaveDir);

        File file = chooser.showOpenDialog(primaryStage);
        if (file == null) return;
        lastSaveDir = file.getParentFile();

        // Фильтр: разрешаем только наши классы и стандартные коллекции
        ObjectInputFilter loggingFilter = info -> {
            Class<?> clazz = info.serialClass();
            if (clazz != null) {
                System.out.println("[filter] " + clazz.getName());
            }
            return ObjectInputFilter.Status.UNDECIDED; // ничего не решаем, пропускаем дальше
        };

        ObjectInputFilter filter = ObjectInputFilter.Config.createFilter(
                "utils.*;java.util.*;java.lang.*;!*"
        );

        ObjectInputFilter combined = ObjectInputFilter.merge(
                loggingFilter, filter
        );

        try (ObjectInputStream in =
                     new ObjectInputStream(
                             new GZIPInputStream(
                             new BufferedInputStream(
                             new FileInputStream(file))))) {
            in.setObjectInputFilter(combined);

            Object obj = in.readObject();
            if (!(obj instanceof SimulationSnapshot snapshot)) {
                Dialogs.error(primaryStage, "Ошибка", "Неверный формат файла");
                return;
            }
            applySnapshot(snapshot);
            Dialogs.info(primaryStage, "Загружено", "Файл загружен: " + file.getName());
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            Dialogs.error(primaryStage, "Ошибка загрузки", e.getMessage());
        }
    }

    private void applySnapshot(SimulationSnapshot s) {
        // Восстанавливаем группы
        groups.clear();
        for (SimulationSnapshot.GroupData d : s.groups) {
            Group g = new Group(d.name, d.count, d.radius, d.mass, d.mu, SimulationSnapshot.fromArgb(d.colorArgb));
            groups.add(g);
        }

        // Восстанавливаем шары в точности
        balls.clear();
        for (SimulationSnapshot.BallData d : s.balls) {
            Ball b = new Ball(d.x, d.y, d.vx, d.vy, d.radius, d.mass, d.mu, d.omega, d.angle, SimulationSnapshot.fromArgb(d.colorArgb));
            balls.add(b);
        }

        // Настройки
        gravityToggle.setSelected(s.isGravityEnabled);
        highPrecisionToggle.setSelected(s.isHighPrecision);
        reflectionToggle.setSelected(s.isReflectionEnabled);
        statsToggle.setSelected(s.isStatsEnabled);
        nonLoseFriction.setSelected(s.isNonLoseFriction);
        toggleStats();

        var stage = borderPane.getScene().getWindow();
        stage.setWidth(s.boxWidth);
        stage.setHeight(s.boxHeight + menuBar.getHeight() + bottomBox.getHeight());

        camX = s.camX;
        camY = s.camY;

        render();
    }
}
