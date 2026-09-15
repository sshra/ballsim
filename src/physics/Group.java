import javafx.beans.property.*;
import javafx.scene.paint.Color;

public class Group {
    private final StringProperty name   = new SimpleStringProperty("Группа");
    private final IntegerProperty count = new SimpleIntegerProperty(10);
    private final DoubleProperty radius = new SimpleDoubleProperty(10.0);
    private final DoubleProperty mass = new SimpleDoubleProperty(1.0);
    private final ObjectProperty<Color> color = new SimpleObjectProperty<>(Color.BLUE);

    public Group(int ballCount, double ballRadius, double ballMass, Color ballColor) {
        this("Группа", ballCount, ballRadius, ballMass, ballColor);
    }

    public Group(String name, int ballCount, double ballRadius, double ballMass, Color ballColor) {
        this.name.set(name);
        count.set(ballCount);
        radius.set(ballRadius);
        mass.set(ballMass);
        color.set(ballColor);
    }

    public int getCount() {
        return count.get();
    }

    public IntegerProperty countProperty() {
        return count;
    }

    public double getRadius() {
        return radius.get();
    }

    public DoubleProperty radiusProperty() {
        return radius;
    }

    public double getMass() {
        return mass.get();
    }

    public DoubleProperty massProperty() {
        return mass;
    }

    public Color getColor() {
        return color.get();
    }

    public ObjectProperty<Color> colorProperty() {
        return color;
    }

    public String getName() {
        return name.get();
    }

    public StringProperty nameProperty() {
        return name;
    }

    @Override
    public String toString() {
        return "%s x%d, %f.0 кг.".formatted(getName(), getCount(), getMass());
    }

    public void setRadius(double v) {
        radius.set(v);
    }

    public void setMass(double v) {
        mass.set(v);
    }
}