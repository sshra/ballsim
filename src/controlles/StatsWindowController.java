import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class StatsWindowController {
    @FXML
    private Label fpsLabel, frameLabel, ballCountLabel;
    @FXML private Label maxSpeedLabel, avgSpeedLabel, energyLabel;
    @FXML private Label checksLabel, collisionsLabel, physicsLabel;

    public int statsFrameCounter = 0;
    public static final int STATS_UPDATE_EVERY = 5;

    /** Вызывается из AnimationTimer с троттлингом. */
    public void update(SimulationStats s) {
        fpsLabel.setText(String.format("%.1f", s.fps));
        frameLabel.setText(Long.toString(s.frameCount));
        ballCountLabel.setText(Integer.toString(s.ballCount));
        maxSpeedLabel.setText(String.format("%.1f", s.maxSpeed));
        avgSpeedLabel.setText(String.format("%.1f", s.avgSpeed));
        energyLabel.setText(String.format("%.1f кДж", s.totalKineticEnergy / 1000));
        checksLabel.setText(Long.toString(s.collisionChecks));
        collisionsLabel.setText(Long.toString(s.actualCollisions));
        physicsLabel.setText(String.format("%.2f мс", s.physicsNanos / 1e6));
    }
}