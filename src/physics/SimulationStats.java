public class SimulationStats {
    // Общие
    public long frameCount;
    public double fps;

    // Шары
    public int ballCount;
    public double maxSpeed;
    public double avgSpeed;
    public double totalKineticEnergy;
    public double totalMomentumX, totalMomentumY;

    // Взаимодействия
    public long collisionChecks;       // сколько пар проверено
    public long actualCollisions;      // сколько столкновений реально произошло
    public int iterationsUsed;         // сколько итераций разрешения понадобилось

    // Производительность
    public long physicsNanos;          // сколько времени занял updatePhysics
    public long renderNanos;

    public void reset() {
        ballCount = 0;
        maxSpeed = 0;
        avgSpeed = 0;
        totalKineticEnergy = 0;
        totalMomentumX = 0;
        totalMomentumY = 0;
        collisionChecks = 0;
        actualCollisions = 0;
        iterationsUsed = 0;
        physicsNanos = 0;
        renderNanos = 0;
    }
}