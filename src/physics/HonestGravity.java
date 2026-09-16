package physics;

import java.util.List;

public class HonestGravity {
    public static void compute(List<Ball> balls, double G, double softening) {

        // 1. Обнуляем ускорения
        for (Ball b : balls) {
            b.ax = 0;
            b.ay = 0;
        }

        // 2. Вычисляем гравитационные силы
        for (int i = 0; i < balls.size(); i++) {
            Ball a = balls.get(i);
            double axi = a.x, ayi = a.y, ami = a.mass;

            for (int j = i + 1; j < balls.size(); j++) {
                Ball b = balls.get(j);

                double dx = b.x - axi;
                double dy = b.y - ayi;
                double distSq = dx * dx + dy * dy + softening; // смягчение
                double invDistSq = 1.0 / distSq;
                double invDist = Math.sqrt(invDistSq);
                double factor = G * invDistSq * invDist;

                // Ускорение для a (F / mass_a) и для b (F / mass_b) с противоположными знаками
                double ax_a = factor * b.mass * dx;
                double ay_a = factor * b.mass * dy;
                double ax_b = -factor * ami * dx;
                double ay_b = -factor * ami * dy;

                a.ax += ax_a;
                a.ay += ay_a;
                b.ax += ax_b;
                b.ay += ay_b;
            }
        }
    }
}
