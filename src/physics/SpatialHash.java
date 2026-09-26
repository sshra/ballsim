package physics;

import java.util.*;

public class SpatialHash {
    private final double cellSize;
    private final Map<Long, List<Ball>> cells = new HashMap<>();

    public SpatialHash(double cellSize) { this.cellSize = cellSize; }

    public void clear() { cells.clear(); }

    public void insert(Ball b) {
        int minX = (int) Math.floor((b.x - b.radius) / cellSize);
        int maxX = (int) Math.floor((b.x + b.radius) / cellSize);
        int minY = (int) Math.floor((b.y - b.radius) / cellSize);
        int maxY = (int) Math.floor((b.y + b.radius) / cellSize);
        for (int cx = minX; cx <= maxX; cx++)
            for (int cy = minY; cy <= maxY; cy++)
                cells.computeIfAbsent(hash(cx, cy), k -> new ArrayList<>()).add(b);
    }

    public List<Ball> getNeighbors(Ball b) {
        Set<Ball> result = new HashSet<>(); // защита от дубликатов
        int cx = (int) Math.floor(b.x / cellSize);
        int cy = (int) Math.floor(b.y / cellSize);
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++) {
                List<Ball> list = cells.get(hash(cx + dx, cy + dy));
                if (list != null) result.addAll(list);
            }
        result.remove(b); // исключаем сам шар
        return new ArrayList<>(result);
    }

    private long hash(int x, int y) {
        // Используем большие простые числа из статьи
        // Teschner et al., 2003 («Optimized Spatial Hashing for Collision Detection of Deformable Objects»).
        return (long) x * 73856093L ^ (long) y * 83492791L;
    }
}