package physics;

import java.util.List;

public class SimulationPhysics {

    /** Коэффициент для момента инерции: 0.5 для диска, 1.0 для обруча, 0.4 для сферы. */
    private static final double INERTIA_COEFFICIENT = 0.5;

    /** Момент инерции шара. */
    private static double inertia(Ball b) {
        return INERTIA_COEFFICIENT * b.mass * b.radius * b.radius;
    }

    SimulationStats stats, frameStats;
    List<Ball> balls;

    public boolean isHighPrecision = false;
    public boolean isGravityEnabled = false;
    public boolean isReflectionEnabled = false;
    public boolean isStatsEnabled = false;
    public boolean isNonLoseFriction = true; // трение без потерь
    public double boxWidth = 0;
    public double boxHeight = 0;

    private volatile boolean cancelled = false;

    public SimulationPhysics(List<Ball> balls, SimulationStats stats) {
        this.stats = new SimulationStats(); // Эта накопительная статистика.
        this.frameStats = stats; // Эта статистика привязанная к выводу в форму.
        this.balls = balls;
    }

    /** Вызывается из UI-потока — просит физику завершиться на ближайшей проверке. */
    public void cancel() { cancelled = true; }

    public void doCalc(double dt) {
        cancelled = false;

        // Статистика, инициализация.
        long start = System.nanoTime();
        stats.reset();

        // Точность расчетов.
        double dtAccumulator = 0.0, effectiveDt;

        while (dtAccumulator < dt) {
            if (cancelled) { frameStats.currentPhase = Phase.CANCELLED; return; }

            if (isHighPrecision) {
                effectiveDt = Math.min(getEffectiveDt(), dt - dtAccumulator);
            } else {
                effectiveDt = dt;
            }

            // Проверка гравитации (пересчет скорости).
            if (isGravityEnabled) {
                frameStats.currentPhase = Phase.GRAVITY;
                ballsGravity(effectiveDt);
                if (cancelled) { frameStats.currentPhase = Phase.CANCELLED; return; }
            }
            // Линейное смещение за dt
            frameStats.currentPhase = Phase.SHIFT;
            ballsShift(effectiveDt);
            if (cancelled) { frameStats.currentPhase = Phase.CANCELLED; return; }

            // Столкновение с клеткой.
            if (isReflectionEnabled) {
                frameStats.currentPhase = Phase.CELL_COLLISIONS;
                cellCollisions();
                if (cancelled) { frameStats.currentPhase = Phase.CANCELLED; return; }
            }

            // 3. Попарное разрешение столкновений
            frameStats.currentPhase = Phase.RESOLVE_COLLISIONS;
            final int iterations = 5; // количество итераций разрешения
            for (int iter = 0; iter < iterations; iter++) {
                if (cancelled) { frameStats.currentPhase = Phase.CANCELLED; return; }
                resolveCollisions();
            }

            dtAccumulator += effectiveDt;
        }

        // Подсчет статистики.
        if (isStatsEnabled) {
            calcStats();
            stats.physicsNanos = System.nanoTime() - start;
        }

        stats.currentPhase = Phase.DONE;
        frameStats.clone(stats);
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
        double maxSpeed = 0, sumSpeed = 0, ke = 0, re = 0, px = 0, py = 0;
        for (Ball b : balls) {
            double speed = Math.hypot(b.vx, b.vy);
            if (speed > maxSpeed) maxSpeed = speed;
            sumSpeed += speed;
            ke += 0.5 * b.mass * speed * speed;
            // Вращательная кинетическая энергия
            double I = inertia(b);
            re += 0.5 * I * b.omega * b.omega;
            px += b.mass * b.vx;
            py += b.mass * b.vy;
        }
        stats.maxSpeed = maxSpeed;
        stats.avgSpeed = balls.isEmpty() ? 0 : sumSpeed / balls.size();
        stats.totalKineticEnergy = ke;
        stats.totalRotationalEnergy = re;
        stats.totalMomentumX = px;
        stats.totalMomentumY = py;
        stats.ballCount = balls.size();
    }

    /* dt из расчета, что максимальное линейное смещение на 1px */
    private double getEffectiveDt() {
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
            if (b.omega != 0.0)
                b.angle += b.omega * dt;   // ← вращение
        }
    }

    private void ballsGravity(double dt) {
        final double G = 500.0;
        final double softening = 1.0;

        final BarnesHutGravity bh = new BarnesHutGravity();
        bh.compute(balls, G, softening);

        // HonestGravity.compute(balls, G, softening);

        // Полунеявный метод Эйлера.
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
        // if (relVn <= 0) return false;

        // Импульс (скаляр) для упругого удара
        double invMassA = 1.0 / a.mass;
        double invMassB = 1.0 / b.mass;
        double invMassSum = invMassA + invMassB;

        // нормальный импульс
        double Jn = -(2 * relVn) / (invMassSum);

        // Применяем импульс к скоростям
        a.vx += (Jn * invMassA) * nx;
        a.vy += (Jn * invMassA) * ny;
        b.vx -= (Jn * invMassB) * nx;
        b.vy -= (Jn * invMassB) * ny;

        // --- Ранний выход, если трение отсутствует ---
        // арифметическая модель коэф. трения
        double mu = 0.5 * (a.mu + b.mu);
        if (mu <= 0.0) return true;

        // --- Касательный импульс (только если mu > 0) ---
        double tx = -ny;
        double ty = nx;

        // Относительная касательная скорость точки контакта a относительно b.
        // Вклад вращения: +ω_a·r_a и +ω_b·r_b (знаки выведены из r_contact).
        double relVt = (a.vx - b.vx) * tx + (a.vy - b.vy) * ty
                + a.omega * a.radius + b.omega * b.radius;

        double invInertiaA = 1.0 / inertia(a);
        double invInertiaB = 1.0 / inertia(b);
        double denom = invMassSum
                + a.radius * a.radius * invInertiaA
                + b.radius * b.radius * invInertiaB;

        double Jt;
        if (isNonLoseFriction) {
            final double e_t = 1.0;
            Jt = -(1.0 + e_t) * relVt / denom;
        } else {
            // Ограничение Кулона: |J_t| ≤ μ·|J_n|
            double JtStop = -relVt / denom;
            double JtMax = mu * Math.abs(Jn); // Jn > 0 здесь
            Jt = Math.max(-JtMax, Math.min(JtMax, JtStop));
        }

        // Применяем касательный импульс к скоростям
        a.vx += (Jt * invMassA) * tx;
        a.vy += (Jt * invMassA) * ty;
        b.vx -= (Jt * invMassB) * tx;
        b.vy -= (Jt * invMassB) * ty;

        // Применяем момент к угловым скоростям
        // τ_a = +r_a·J_t, τ_b = +r_b·J_t (знаки выведены из r_contact)
        a.omega += (a.radius * Jt) * invInertiaA;
        b.omega += (b.radius * Jt) * invInertiaB;

        return true;
    }

}