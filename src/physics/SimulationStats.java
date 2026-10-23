package physics;

import java.time.Instant;
import java.util.LinkedList;

public class SimulationStats {

    /** Текущая фаза физики. volatile — пишет физический поток, читает UI. */
    public volatile Phase currentPhase = Phase.IDLE;

    // Общие
    public double fps;

    // Шары
    public int ballCount;
    public double maxSpeed;
    public double avgSpeed;
    public double totalKineticEnergy;
    public double totalRotationalEnergy;   // суммарная вращательная энергия
    public double totalMomentumX, totalMomentumY;

    // Взаимодействия
    public long collisionChecks;       // сколько пар проверено
    public long actualCollisions;      // сколько столкновений реально произошло
    public int iterationsUsed;         // сколько итераций разрешения понадобилось

    // Производительность
    final static int TIME_POINTS = 10;
    public long physicsNanos;          // сколько времени занял updatePhysics
    public long renderNanos;
    private LinkedList<Instant> time = new LinkedList<Instant>();
    private long startTimePoint;

    public void clone(SimulationStats s) {
        ballCount = s.ballCount;
        maxSpeed = s.maxSpeed;
        avgSpeed = s.avgSpeed;
        totalKineticEnergy = s.totalKineticEnergy;
        totalRotationalEnergy = s.totalRotationalEnergy;
        totalMomentumX = s.totalMomentumX;
        totalMomentumY = s.totalMomentumY;
        collisionChecks = s.collisionChecks;
        actualCollisions = s.actualCollisions;
        iterationsUsed = s.iterationsUsed;
        physicsNanos = s.physicsNanos;
        renderNanos = s.renderNanos;
        fps = s.fps;
        time = s.time;
    }

    public void addTimePoint() {
        time.add(Instant.now());
        if (time.size() > TIME_POINTS) {
            time.removeFirst();
        }
    }

    public void calcFPS() {
        if (time.size() > 1) {
            fps = 1 / (((double) time.getLast().toEpochMilli() - time.getFirst().toEpochMilli()) / (time.size() - 1) / 1000);
        }
    }

    public void measureFrameTime() {
        physicsNanos = System.nanoTime() - startTimePoint;
    }

    public void reset() {
        startTimePoint = System.nanoTime();
        ballCount = 0;
        maxSpeed = 0;
        avgSpeed = 0;
        totalKineticEnergy = 0;
        totalRotationalEnergy = 0;
        totalMomentumX = 0;
        totalMomentumY = 0;
        collisionChecks = 0;
        actualCollisions = 0;
        iterationsUsed = 0;
        physicsNanos = 0;
        renderNanos = 0;
    }

    public void setTime(LinkedList<Instant> time) {
        this.time = time;
    }

    public LinkedList<Instant> getTime() {
        return this.time;
    }
}