package utils;

import javafx.scene.paint.Color;
import physics.Ball;
import physics.Group;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.stream.Collectors;

public class SimulationSnapshot implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public List<BallData> balls;
    public List<GroupData> groups;

    // Настройки симуляции
    public boolean isHighPrecision = false;
    public boolean isGravityEnabled = false;
    public boolean isReflectionEnabled = false;
    public boolean isStatsEnabled = false;
    public boolean isNonLoseFriction = true;
    public double boxWidth = 0;
    public double boxHeight = 0;

    public double camX = 0;
    public double camY = 0;

    public static class BallData implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        public double x, y, vx, vy;
        public double radius, mass;
        public double mu, omega, angle;
        public int colorArgb; // цвет как ARGB
    }

    public static class GroupData implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        public String name;
        public int count;
        public double radius, mass, mu;
        public int colorArgb;
    }

    public void fromSnapshot (List<Ball> balls, List<Group> groups) {
        // Восстанавливаем группы
        groups.clear();
        for (SimulationSnapshot.GroupData d : this.groups) {
            Group g = new Group(d.name, d.count, d.radius, d.mass, d.mu, SimulationSnapshot.fromArgb(d.colorArgb));
            groups.add(g);
        }

        // Восстанавливаем шары в точности
        balls.clear();
        for (SimulationSnapshot.BallData d : this.balls) {
            Ball b = new Ball(d.x, d.y, d.vx, d.vy, d.radius, d.mass, d.mu, d.omega, d.angle, SimulationSnapshot.fromArgb(d.colorArgb));
            balls.add(b);
        }
    }

    public static SimulationSnapshot toSnapshot(List<Ball> balls, List<Group> groups) {
        SimulationSnapshot s = new SimulationSnapshot();

        s.balls = balls.stream().map(b -> {
            SimulationSnapshot.BallData d = new SimulationSnapshot.BallData();
            d.x = b.x; d.y = b.y;
            d.vx = b.vx; d.vy = b.vy;
            d.radius = b.radius;
            d.mass = b.mass;
            d.angle = b.angle;
            d.mu = b.mu;
            d.omega = b.omega;
            d.colorArgb = toArgb(b.getColor());
            return d;
        }).collect(Collectors.toList());

        s.groups = groups.stream().map(g -> {
            SimulationSnapshot.GroupData d = new SimulationSnapshot.GroupData();
            d.name = g.getName();
            d.count = g.getCount();
            d.radius = g.getRadius();
            d.mass = g.getMass();
            d.mu = g.getMu();
            d.colorArgb = toArgb(g.getColor());
            return d;
        }).collect(Collectors.toList());

        return s;
    }

    public static int toArgb(Color c) {
        int a = (int) Math.round(c.getOpacity() * 255);
        int r = (int) Math.round(c.getRed() * 255);
        int g = (int) Math.round(c.getGreen() * 255);
        int b = (int) Math.round(c.getBlue() * 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static Color fromArgb(int argb) {
        int a = (argb >> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return Color.rgb(r, g, b, a / 255.0);
    }
}