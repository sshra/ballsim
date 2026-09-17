package physics;

import javafx.scene.paint.Color;

import java.util.Random;

public class Ball {
    public double x, y;
    public double vx, vy;
    public double ax, ay;
    public double radius;
    public double mass;
    public double mu; // трение
    public double omega; // угловая скорость
    public double angle; // текущий угол
    private Color color;

    public Ball(double width, double height, double radius, double mass, Double mu, Color color) {
        Random rand = new Random();
        double x = rand.nextDouble() * width;
        double y = rand.nextDouble() * height;
        double vx = (rand.nextDouble() - 0.5) * 200; // пикселей/сек
        double vy = (rand.nextDouble() - 0.5) * 200;
        this(x, y, vx, vy, radius, mass, mu, 0, 0, color);
    }

    public Ball(double x, double y, double vx, double vy, double radius, double mass, double mu, Color color) {
        this(x, y, vx, vy, radius, mass, mu, 0, 0, color);
    }

    public Ball(double x, double y, double vx, double vy,
                double radius, double mass,
                double mu, double omega, double angle,
                Color color) {
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.radius = radius;
        this.mass = mass;
        this.color = color;
        this.ax = 0;
        this.ay = 0;
        this.mu = mu;
        this.angle = angle;
        this.omega = omega;
    }

    public Color getColor() {
        return color;
    }
    // конструктор, геттеры/сеттеры
}