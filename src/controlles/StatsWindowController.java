package controlles;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import physics.SimulationStats;

public class StatsWindowController {
    @FXML
    private Label fpsLabel, frameLabel, ballCountLabel;
    @FXML private Label maxSpeedLabel, avgSpeedLabel;
    @FXML private Label checksLabel, collisionsLabel, physicsLabel;
    @FXML private Label rotEnergyLabel, totalEnergyLabel, energyLabel;

    public int statsFrameCounter = 0;
    public static final int STATS_UPDATE_EVERY = 5;

    /** Вызывается из AnimationTimer с троттлингом. */
    public void update(SimulationStats s) {
        fpsLabel.setText(String.format("%.5f", s.fps));
        ballCountLabel.setText(String.format("%,d", s.ballCount));
        maxSpeedLabel.setText(String.format("%,.1f", s.maxSpeed));
        avgSpeedLabel.setText(String.format("%,.1f", s.avgSpeed));
        energyLabel.setText(String.format("%,.1f кДж", s.totalKineticEnergy * 0.001));
        rotEnergyLabel.setText(String.format("%,.1f кДж", s.totalRotationalEnergy * 0.001));
        totalEnergyLabel.setText(String.format("%,.1f кДж", (s.totalKineticEnergy + s.totalRotationalEnergy) * 0.001));

        checksLabel.setText(String.format("%,d", s.collisionChecks));
        collisionsLabel.setText(String.format("%,d", s.actualCollisions));
        physicsLabel.setText(String.format("%.2f мс", s.physicsNanos / 1e6));
    }
}