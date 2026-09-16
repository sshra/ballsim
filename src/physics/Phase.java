package physics;

public enum Phase {
    IDLE("Ожидание"),
    GRAVITY("Гравитация"),
    SHIFT("Перемещение"),
    CELL_COLLISIONS("Стенки"),
    RESOLVE_COLLISIONS("Столкновения"),
    STATS("Статистика"),
    DONE("Готово"),
    CANCELLED("Прервано");

    public final String title;
    Phase(String title) { this.title = title; }
}
