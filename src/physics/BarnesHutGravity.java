package physics;

import java.util.List;

public final class BarnesHutGravity {

    private static final double THETA = 0.5;
    private static final int MAX_DEPTH = 20;

    private List<Ball> balls;
    private double G, soft2;
    private QuadNode root;

    public void compute(List<Ball> balls, double G, double softening) {
        this.balls = balls;
        this.G = G;
        this.soft2 = softening * softening;

        int n = balls.size();
        for (Ball b : balls) {
            b.ax = 0;
            b.ay = 0;
        }
        if (n == 0) return;

        // Границы
        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < n; i++) {
            Ball b = balls.get(i);
            if (b.x < minX) minX = b.x;
            if (b.x > maxX) maxX = b.x;
            if (b.y < minY) minY = b.y;
            if (b.y > maxY) maxY = b.y;
        }
        double cx = (minX + maxX) * 0.5;
        double cy = (minY + maxY) * 0.5;
        double half = Math.max(maxX - minX, maxY - minY) * 0.5 + 1e-6;

        root = new QuadNode();
        root.cx = cx; root.cy = cy; root.half = half; root.depth = 0;
        for (int i = 0; i < n; i++) insert(root, i);
        computeAggregates(root);
        for (int i = 0; i < n; i++) computeForce(root, i);
    }

    // --- Узел ---
    private static final class QuadNode {
        double cx, cy, half;
        int depth;
        int ballIndex = -1;
        QuadNode nw, ne, sw, se;
        double mass, comX, comY;
        int[] overflow;
        int overflowCount;
        boolean isLeaf() { return nw == null; }
    }

    // --- Вставка ---
    private void insert(QuadNode node, int idx) {
        if (node.isLeaf()) {
            if (node.ballIndex == -1 && node.overflowCount == 0) {
                node.ballIndex = idx;
                return;
            }
            if (node.depth >= MAX_DEPTH) {
                if (node.overflow == null) node.overflow = new int[4];
                if (node.overflowCount == node.overflow.length)
                    node.overflow = java.util.Arrays.copyOf(node.overflow, node.overflow.length * 2);
                node.overflow[node.overflowCount++] = idx;
                return;
            }
            int existing = node.ballIndex;
            int[] existingOv = node.overflow;
            int existingOvCount = node.overflowCount;

            node.ballIndex = -1;
            node.overflow = null;
            node.overflowCount = 0;

            double h = node.half * 0.5;
            node.nw = makeChild(node.cx - h, node.cy - h, h, node.depth + 1);
            node.ne = makeChild(node.cx + h, node.cy - h, h, node.depth + 1);
            node.sw = makeChild(node.cx - h, node.cy + h, h, node.depth + 1);
            node.se = makeChild(node.cx + h, node.cy + h, h, node.depth + 1);

            if (existing != -1) insert(node, existing);
            if (existingOv != null)
                for (int k = 0; k < existingOvCount; k++) insert(node, existingOv[k]);
            insert(node, idx);
        } else {
            Ball b = balls.get(idx);
            insert(childContaining(node, b.x, b.y), idx);
        }
    }

    private QuadNode makeChild(double cx, double cy, double half, int depth) {
        QuadNode c = new QuadNode();
        c.cx = cx; c.cy = cy; c.half = half; c.depth = depth;
        return c;
    }

    private QuadNode childContaining(QuadNode n, double px, double py) {
        if (px < n.cx) return (py < n.cy) ? n.nw : n.sw;
        return (py < n.cy) ? n.ne : n.se;
    }

    // --- Агрегаты ---
    private void computeAggregates(QuadNode node) {
        if (node.isLeaf()) {
            double m = 0, sx = 0, sy = 0;
            if (node.ballIndex != -1) {
                Ball b = balls.get(node.ballIndex);
                m += b.mass; sx += b.x * b.mass; sy += b.y * b.mass;
            }
            if (node.overflow != null) {
                for (int k = 0; k < node.overflowCount; k++) {
                    Ball b = balls.get(node.overflow[k]);
                    m += b.mass; sx += b.x * b.mass; sy += b.y * b.mass;
                }
            }
            node.mass = m;
            if (m > 0) { node.comX = sx / m; node.comY = sy / m; }
            return;
        }
        double m = 0, sx = 0, sy = 0;
        QuadNode[] children = {node.nw, node.ne, node.sw, node.se};
        for (QuadNode c : children) {
            computeAggregates(c);
            m += c.mass; sx += c.comX * c.mass; sy += c.comY * c.mass;
        }
        node.mass = m;
        if (m > 0) { node.comX = sx / m; node.comY = sy / m; }
    }

    // --- Силы ---
    private void computeForce(QuadNode node, int idx) {
        if (node.mass == 0) return;

        if (node.isLeaf()) {
            if (node.ballIndex != -1 && node.ballIndex != idx) directPair(idx, node.ballIndex);
            if (node.overflow != null) {
                for (int k = 0; k < node.overflowCount; k++) {
                    int j = node.overflow[k];
                    if (j != idx) directPair(idx, j);
                }
            }
            return;
        }

        Ball bi = balls.get(idx);
        double dx = node.comX - bi.x;
        double dy = node.comY - bi.y;
        double distSq = dx*dx + dy*dy + soft2;
        double s = node.half * 2.0;

        if (s * s < THETA * THETA * distSq) {
            double invDistSq = 1.0 / distSq;
            double invDist  = Math.sqrt(invDistSq);
            double factor   = G * node.mass * invDistSq * invDist;
            bi.ax += factor * dx;
            bi.ay += factor * dy;
        } else {
            computeForce(node.nw, idx);
            computeForce(node.ne, idx);
            computeForce(node.sw, idx);
            computeForce(node.se, idx);
        }
    }

    private void directPair(int i, int j) {
        Ball bi = balls.get(i);
        Ball bj = balls.get(j);
        double dx = bj.x - bi.x;
        double dy = bj.y - bi.y;
        double distSq = dx*dx + dy*dy + soft2;
        double invDistSq = 1.0 / distSq;
        double invDist  = Math.sqrt(invDistSq);
        double factor   = G * bj.mass * invDistSq * invDist;
        bi.ax += factor * dx;
        bi.ay += factor * dy;
    }
}