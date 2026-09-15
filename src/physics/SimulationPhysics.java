import java.util.ArrayList;
import java.util.List;

public class SimulationPhysics {

    SimulationStats stats;
    List<Ball> balls;

    boolean isHighPrecision = false;
    boolean isGravityEnabled = false;
    boolean isReflectionEnabled = false;
    boolean isStatsEnabled = false;
    double boxWidth = 0;
    double boxHeight = 0;

    SimulationPhysics(List<Ball> balls, SimulationStats stats) {
        this.stats = stats;
        this.balls = balls;
    }

    public void doCalc(double dt) {

        // Статистика, инициализация.
        long start = System.nanoTime();
        stats.reset();

        // Точность расчетов.
        double dtAccumulator = 0.0, effectiveDt;

        while (dtAccumulator < dt) {
            if (isHighPrecision) {
                effectiveDt = Math.min(getEffectiveDt(dt), dt - dtAccumulator);
            } else {
                effectiveDt = dt;
            }

            // Проверка гравитации (пересчет скорости).
            if (isGravityEnabled) {
                ballsGravity(effectiveDt);
            }
            // Линейное смещение за dt
            ballsShift(effectiveDt);
            // Столкновение с клеткой.
            if (isReflectionEnabled) {
                cellCollisions();
            }

            // 3. Попарное разрешение столкновений
            final int iterations = 5; // количество итераций разрешения
            for (int iter = 0; iter < iterations; iter++) {
                resolveCollisions();
            }

            dtAccumulator += effectiveDt;
        }

        // Подсчет статистики.
        if (isStatsEnabled) {
            calcStats();
            stats.physicsNanos = System.nanoTime() - start;
        }
    }

    private void cellCollisions() {
        for (Ball b : balls) {
            // отражение от границ (с учётом радиуса)
            if (b.x - b.radius < 0) { b.x = b.radius; b.vx = -b.vx; }
            if (b.x + b.radius > boxWidth) { b.x = boxWidth - b.radius; b.vx = -b.vx; }
            if (b.y - b.radius < 0) { b.y = b.radius; b.vy = -b.vy; }
            if (b.y + b.radius > boxHeight) { b.y = boxHeight - b.radius; b.vy = -b.vy; }
        }
    }

    private void calcStats() {
        // Подсчёт скоростей и энергии — один проход по шарам в конце
        double maxSpeed = 0, sumSpeed = 0, ke = 0, px = 0, py = 0;
        for (Ball b : balls) {
            double speed = Math.hypot(b.vx, b.vy);
            if (speed > maxSpeed) maxSpeed = speed;
            sumSpeed += speed;
            ke += 0.5 * b.mass * speed * speed;
            px += b.mass * b.vx;
            py += b.mass * b.vy;
        }
        stats.maxSpeed = maxSpeed;
        stats.avgSpeed = balls.isEmpty() ? 0 : sumSpeed / balls.size();
        stats.totalKineticEnergy = ke;
        stats.totalMomentumX = px;
        stats.totalMomentumY = py;
        stats.ballCount = balls.size();
    }

    /* dt из расчета, что максимальное линейное смещение на 1пикс */
    private double getEffectiveDt(double dt) {
        double maxSpeed = balls.stream().mapToDouble(a -> a.vx * a.vx + a.vy * a.vy)
                .max().orElse(1.0);
        maxSpeed = Math.sqrt(maxSpeed);
        return 1 / maxSpeed;
    }

    private void resolveCollisions() {
        double maxRadius = balls.stream().mapToDouble(a -> a.radius)
                .max().orElse(1.0);

        SpatialHash grid = new SpatialHash(2 * maxRadius);
        for (Ball a : balls) grid.insert(a);
        int checkCount = 0, collisionCount = 0;

        for (Ball a: balls) {
            for (Ball b : grid.getNeighbors(a)) {
                if (a.hashCode() < b.hashCode()) { // чтобы не проверять пару дважды
                    collisionCount += resolveCollision(a, b) ? 1 : 0;
                    checkCount++;
                }
            }
        }

        stats.collisionChecks += checkCount;
        stats.actualCollisions += collisionCount;
    }

    private void ballsShift(double dt) {
        for (Ball b : balls) {
            b.x += b.vx * dt;
            b.y += b.vy * dt;
        }
    }

    private void ballsGravity(double dt) {
        final double G = 50000.0; // гравитационная постоянная (подберите под свой масштаб)
        final double softening = 1.0; // смягчение, чтобы избежать сингулярности

        // 1. Обнуляем ускорения
        for (Ball b : balls) {
            b.ax = 0;
            b.ay = 0;
        }

        // 2. Вычисляем гравитационные силы
        for (int i = 0; i < balls.size(); i++) {
            Ball a = balls.get(i);

            for (int j = i + 1; j < balls.size(); j++) {
                Ball b = balls.get(j);

                double dx = b.x - a.x;
                double dy = b.y - a.y;
                double distSq = dx*dx + dy*dy + softening*softening; // смягчение
                double dist = Math.sqrt(distSq);

                // Сила (скаляр) по закону Ньютона
                double force = G * a.mass * b.mass / distSq;

                // Ускорение для a (F / mass_a) и для b (F / mass_b) с противоположными знаками
                double ax_a = force / a.mass * (dx / dist);
                double ay_a = force / a.mass * (dy / dist);
                double ax_b = -force / b.mass * (dx / dist);
                double ay_b = -force / b.mass * (dy / dist);

                a.ax += ax_a;
                a.ay += ay_a;
                b.ax += ax_b;
                b.ay += ay_b;
            }
        }

        // 3. Полунеявный метод Эйлера
        for (Ball b : balls) {
            b.vx += b.ax * dt;
            b.vy += b.ay * dt;
        }
    }

    /* Рассчитывает коллизию шаров, вернет флаг - было ли столкновение. */
    private boolean resolveCollision(Ball a, Ball b) {
        double dx = b.x - a.x;
        double dy = b.y - a.y;
        double dist = Math.sqrt(dx*dx + dy*dy);
        double minDist = a.radius + b.radius;

        if (dist >= minDist || dist < 0.0001) return false; // нет столкновения или нулевое расстояние

        // Нормаль столкновения (единичный вектор от a к b)
        double nx = dx / dist;
        double ny = dy / dist;

        // Величина перекрытия
        double overlap = minDist - dist;

        // Раздвигаем шары пропорционально массам (чтобы тяжёлый меньше смещался)
        double totalMass = a.mass + b.mass;
        double aRatio = b.mass / totalMass;   // лёгкий смещается больше
        double bRatio = a.mass / totalMass;

        a.x -= nx * overlap * aRatio;
        a.y -= ny * overlap * aRatio;
        b.x += nx * overlap * bRatio;
        b.y += ny * overlap * bRatio;

        // ---- Теперь обновляем скорости для упругого удара ----
        // Относительная скорость вдоль нормали
        double dvx = a.vx - b.vx;
        double dvy = a.vy - b.vy;
        double relVn = dvx * nx + dvy * ny;

        // Если шары уже разлетаются, ничего не делаем
        //        if (relVn > 0) return;

        // Импульс (скаляр) для упругого удара
        double impulse = -(2 * relVn) / (1/a.mass + 1/b.mass);

        // Применяем импульс к скоростям
        a.vx += (impulse / a.mass) * nx;
        a.vy += (impulse / a.mass) * ny;
        b.vx -= (impulse / b.mass) * nx;
        b.vy -= (impulse / b.mass) * ny;

        return true;
    }

}
