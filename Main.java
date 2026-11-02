import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Sholo Guti And Bagh-Chhagol Village Game - Java Swing games:
 * Sholo Guti (16 Guti) + Bagh-Chhagol (Tiger and Goat).
 *
 * 16 Guti : each player picks a guti type: Small betel nut, Stick, Brickbats or Small stone.
 * Bagh-Chhagol : 3 Bagh (tigers) against 16 Chhagol (goats).
 * Modes (both games): vs Computer, Two Players (same PC).
 * Run:  javac Main.java   then   java Main
 */
public class Main extends JFrame {

    static final int GREEN = 1, BROWN = 2;
    static final int TIGER = 1, GOAT = 2, GOATS_TOTAL = 16;
    /** 16 Guti guti types: 0 = Small betel nut, 1 = Stick, 2 = Brickbats, 3 = Small stone. kind[colour] = type used by that side. */
    static final String[] KIND_NAMES = {"Small betel nut", "Stick", "Brickbats", "Small stone"};
    static final int[] kind = {0, 0, 1};

    static String pieceName(int color) { return KIND_NAMES[kind[color]]; }

    static final Color LINE = new Color(86, 56, 28);
    static final Color LINE_LIGHT = new Color(232, 200, 150);
    static final Color TEXT_DARK = new Color(70, 42, 18);

    // =====================================================================
    //  SOUND  (tones are generated in code - no sound files needed)
    // =====================================================================
    static final class Sound {
        static volatile boolean on = true;

        static void play(final double[][] notes) {
            if (!on) return;
            Thread th = new Thread(() -> {
                try {
                    float sr = 22050f;
                    ByteArrayOutputStream bo = new ByteArrayOutputStream();
                    for (double[] n : notes) {
                        int len = (int) (sr * n[1] / 1000.0);
                        for (int i = 0; i < len; i++) {
                            double t = i / (double) sr;
                            double env = Math.min(1.0, Math.min(i / (sr * 0.01), (len - i) / (sr * 0.05)));
                            double v = 0;
                            if (n[0] > 0)
                                v = (Math.sin(2 * Math.PI * n[0] * t) * 0.6 + Math.sin(4 * Math.PI * n[0] * t) * 0.2) * env * 0.5;
                            short sv = (short) (v * 32767);
                            bo.write(sv & 0xff);
                            bo.write((sv >> 8) & 0xff);
                        }
                    }
                    byte[] data = bo.toByteArray();
                    AudioFormat af = new AudioFormat(sr, 16, 1, true, false);
                    final Clip clip = AudioSystem.getClip();
                    clip.open(af, data, 0, data.length);
                    clip.addLineListener(ev -> {
                        if (ev.getType() == LineEvent.Type.STOP) clip.close();
                    });
                    clip.start();
                } catch (Exception ignored) { }
            });
            th.setDaemon(true);
            th.start();
        }

        static void win()  { play(new double[][]{{523, 140}, {659, 140}, {784, 140}, {1047, 450}}); }
        static void lose() { play(new double[][]{{392, 260}, {349, 260}, {311, 260}, {196, 650}}); }
        static void draw() { play(new double[][]{{440, 200}, {0, 60}, {440, 320}}); }
        static void move() { play(new double[][]{{900, 35}}); }
        static void capture() { play(new double[][]{{260, 90}, {180, 120}}); }

    }

    // =====================================================================
    //  BOARD GEOMETRY - 16 GUTI (half-cell coordinates so the two triangles fit)
    // =====================================================================
    static final class Geo {
        static final int N;
        static final int[] X, Y;
        static final boolean[][] E;
        static final int[][] ADJ;
        static final int[][][] JUMP; // JUMP[a] = list of {over, land}

        static int key(int x, int y) { return (y + 5) * 20 + (x + 5); }

        static void link(int a, int b) { E[a][b] = true; E[b][a] = true; }

        static {
            List<int[]> p = new ArrayList<>();
            for (int y = 0; y <= 8; y += 2) for (int x = 0; x <= 8; x += 2) p.add(new int[]{x, y});
            int[][] tri = {{3, -1}, {4, -1}, {5, -1}, {2, -2}, {4, -2}, {6, -2},
                    {3, 9}, {4, 9}, {5, 9}, {2, 10}, {4, 10}, {6, 10}};
            for (int[] t : tri) p.add(t);
            N = p.size();
            X = new int[N];
            Y = new int[N];
            Map<Integer, Integer> idx = new HashMap<>();
            for (int i = 0; i < N; i++) {
                X[i] = p.get(i)[0];
                Y[i] = p.get(i)[1];
                idx.put(key(X[i], Y[i]), i);
            }
            E = new boolean[N][N];
            for (int i = 0; i < 25; i++)
                for (int j = i + 1; j < 25; j++) {
                    int dx = Math.abs(X[i] - X[j]), dy = Math.abs(Y[i] - Y[j]);
                    if ((dx == 2 && dy == 0) || (dx == 0 && dy == 2)) link(i, j);
                    else if (dx == 2 && dy == 2 && (X[i] / 2 + Y[i] / 2) % 2 == 0) link(i, j);
                }
            int[][] te = {{3, -1, 4, -1}, {4, -1, 5, -1}, {2, -2, 4, -2}, {4, -2, 6, -2},
                    {4, 0, 4, -1}, {4, -1, 4, -2}, {4, 0, 3, -1}, {3, -1, 2, -2},
                    {4, 0, 5, -1}, {5, -1, 6, -2}};
            for (int[] t : te) {
                link(idx.get(key(t[0], t[1])), idx.get(key(t[2], t[3])));
                link(idx.get(key(t[0], 8 - t[1])), idx.get(key(t[2], 8 - t[3])));
            }
            ADJ = new int[N][];
            JUMP = new int[N][][];
            for (int a = 0; a < N; a++) {
                List<Integer> adj = new ArrayList<>();
                for (int b = 0; b < N; b++) if (E[a][b]) adj.add(b);
                ADJ[a] = adj.stream().mapToInt(Integer::intValue).toArray();
            }
            for (int a = 0; a < N; a++) {
                List<int[]> js = new ArrayList<>();
                for (int b : ADJ[a])
                    for (int c : ADJ[b]) {
                        if (c == a) continue;
                        int dx1 = X[b] - X[a], dy1 = Y[b] - Y[a];
                        int dx2 = X[c] - X[b], dy2 = Y[c] - Y[b];
                        if (dx1 * dy2 - dy1 * dx2 == 0 && dx1 * dx2 + dy1 * dy2 > 0) js.add(new int[]{b, c});
                    }
                JUMP[a] = js.toArray(new int[0][]);
            }
        }
    }

    // =====================================================================
    //  RULES - 16 GUTI
    // =====================================================================
    static final class Rules {
        static int[] initial() {
            int[] c = new int[Geo.N];
            for (int i = 0; i < Geo.N; i++) c[i] = Geo.Y[i] <= 2 ? BROWN : (Geo.Y[i] >= 6 ? GREEN : 0);
            return c;
        }

        /** Next steps for the guti on 'from': {to, over}; over = -1 for a plain move. */
        static List<int[]> options(int[] c, int from, boolean jumpsOnly) {
            List<int[]> r = new ArrayList<>();
            int p = c[from];
            if (!jumpsOnly) for (int b : Geo.ADJ[from]) if (c[b] == 0) r.add(new int[]{b, -1});
            for (int[] j : Geo.JUMP[from]) if (c[j[0]] == 3 - p && c[j[1]] == 0) r.add(new int[]{j[1], j[0]});
            return r;
        }

        static boolean hasMove(int[] c, int p) {
            for (int i = 0; i < Geo.N; i++)
                if (c[i] == p && !options(c, i, false).isEmpty()) return true;
            return false;
        }

        static int count(int[] c, int p) {
            int n = 0;
            for (int v : c) if (v == p) n++;
            return n;
        }

        static int overOf(int a, int b) {
            for (int[] j : Geo.JUMP[a]) if (j[1] == b) return j[0];
            return -1;
        }

        static int step(int[] c, int a, int b) {
            int over = Geo.E[a][b] ? -1 : overOf(a, b);
            c[b] = c[a];
            c[a] = 0;
            if (over >= 0) c[over] = 0;
            return over;
        }

        static List<int[]> turns(int[] c, int p) {
            List<int[]> out = new ArrayList<>();
            for (int f = 0; f < Geo.N; f++) {
                if (c[f] != p) continue;
                for (int b : Geo.ADJ[f]) if (c[b] == 0) out.add(new int[]{f, b});
                chain(c, p, f, new int[]{f}, out);
            }
            return out;
        }

        private static void chain(int[] c, int p, int pos, int[] path, List<int[]> out) {
            boolean any = false;
            for (int[] j : Geo.JUMP[pos]) {
                if (c[j[0]] == 3 - p && c[j[1]] == 0) {
                    any = true;
                    int saved = c[j[0]];
                    c[pos] = 0; c[j[0]] = 0; c[j[1]] = p;
                    int[] np = Arrays.copyOf(path, path.length + 1);
                    np[path.length] = j[1];
                    chain(c, p, j[1], np, out);
                    c[j[1]] = 0; c[j[0]] = saved; c[pos] = p;
                }
            }
            if (!any && path.length > 1) out.add(path);
        }

        static void applyTurn(int[] c, int[] path) {
            for (int i = 1; i < path.length; i++) step(c, path[i - 1], path[i]);
        }
    }

    // =====================================================================
    //  COMPUTER PLAYER - 16 GUTI (0 easy/random, 1 medium, 2 hard)
    // =====================================================================
    static final class Ai {
        static final Random RND = new Random();

        static int[] choose(int[] c, int p, int level) {
            List<int[]> ts = Rules.turns(c, p);
            if (ts.isEmpty()) return null;
            if (level == 0) return ts.get(RND.nextInt(ts.size()));
            int depth = level == 1 ? 2 : 4;
            int best = Integer.MIN_VALUE;
            int[] bestT = ts.get(0);
            for (int[] t : ts) {
                int[] n = c.clone();
                Rules.applyTurn(n, t);
                int score = -search(n, 3 - p, depth - 1, -100000, 100000) * 10 + RND.nextInt(8);
                if (score > best) { best = score; bestT = t; }
            }
            return bestT;
        }

        static int search(int[] c, int p, int depth, int alpha, int beta) {
            List<int[]> ts = Rules.turns(c, p);
            if (ts.isEmpty()) return -10000 - depth;
            if (depth == 0) return Rules.count(c, p) * 100 - Rules.count(c, 3 - p) * 100;
            int best = Integer.MIN_VALUE;
            for (int[] t : ts) {
                int[] n = c.clone();
                Rules.applyTurn(n, t);
                int s = -search(n, 3 - p, depth - 1, -beta, -alpha);
                if (s > best) best = s;
                if (best > alpha) alpha = best;
                if (alpha >= beta) break;
            }
            return best;
        }
    }

    // =====================================================================
    //  BAGH-CHHAGOL : plays on the SAME 37-point board as Sholo Guti (class Geo).
    //  Points 25, 26, 27 = the middle row of the top triangle (tiger start points).
    // =====================================================================
    /** Bagh-Chhagol position: cells (0 empty, TIGER, GOAT), goats placed so far, goats captured. */
    static final class BState {
        int[] c = new int[Geo.N];
        int placed, captured;

        static BState initial() {
            BState s = new BState();
            s.c[25] = TIGER; s.c[26] = TIGER; s.c[27] = TIGER;   // middle row of the top triangle
            return s;
        }

        BState copy() {
            BState n = new BState();
            n.c = c.clone();
            n.placed = placed;
            n.captured = captured;
            return n;
        }
    }

    // =====================================================================
    //  BAGH-CHHAGOL : RULES   (a move is {from, to, over}; from = -1 means "place a goat")
    // =====================================================================
    static final class BRules {
        static List<int[]> moves(BState s, int side) {
            List<int[]> r = new ArrayList<>();
            if (side == GOAT) {
                if (s.placed < GOATS_TOTAL) {
                    for (int i = 0; i < Geo.N; i++) if (s.c[i] == 0) r.add(new int[]{-1, i, -1});
                } else {
                    for (int i = 0; i < Geo.N; i++)
                        if (s.c[i] == GOAT)
                            for (int b : Geo.ADJ[i]) if (s.c[b] == 0) r.add(new int[]{i, b, -1});
                }
            } else {
                for (int i = 0; i < Geo.N; i++) {
                    if (s.c[i] != TIGER) continue;
                    for (int b : Geo.ADJ[i]) if (s.c[b] == 0) r.add(new int[]{i, b, -1});
                    for (int[] j : Geo.JUMP[i])
                        if (s.c[j[0]] == GOAT && s.c[j[1]] == 0) r.add(new int[]{i, j[1], j[0]});
                }
            }
            return r;
        }

        static void apply(BState s, int[] m, int side) {
            if (side == GOAT) {
                s.c[m[1]] = GOAT;
                if (m[0] < 0) s.placed++; else s.c[m[0]] = 0;
            } else {
                s.c[m[1]] = TIGER;
                s.c[m[0]] = 0;
                if (m[2] >= 0) { s.c[m[2]] = 0; s.captured++; }
            }
        }

        /** Whole turns. A goat turn is one step. A tiger turn is one step, or a chain of jumps (it may stop any time). */
        static List<List<int[]>> turns(BState s, int side) {
            List<List<int[]>> out = new ArrayList<>();
            for (int[] m : moves(s, side)) {
                List<int[]> first = new ArrayList<>();
                first.add(m);
                if (side == TIGER && m[2] >= 0) chain(s, m, first, out);
                else out.add(first);
            }
            return out;
        }

        private static void chain(BState s, int[] m, List<int[]> path, List<List<int[]>> out) {
            out.add(path);                         // the tiger may stop here
            BState n = s.copy();
            apply(n, m, TIGER);
            for (int[] j : Geo.JUMP[m[1]])
                if (n.c[j[0]] == GOAT && n.c[j[1]] == 0) {
                    int[] nm = {m[1], j[1], j[0]};
                    List<int[]> np = new ArrayList<>(path);
                    np.add(nm);
                    chain(n, nm, np, out);
                }
        }

        static void applyTurn(BState s, List<int[]> path, int side) {
            for (int[] m : path) apply(s, m, side);
        }

        static int goatsOnBoard(BState s) {
            int n = 0;
            for (int v : s.c) if (v == GOAT) n++;
            return n;
        }
    }

    // =====================================================================
    //  BAGH-CHHAGOL : COMPUTER PLAYER (0 easy/random, 1 medium, 2 hard)
    // =====================================================================
    static final class BAi {
        static final Random RND = new Random();

        static List<int[]> choose(BState s, int side, int level, int win) {
            List<List<int[]>> ts = BRules.turns(s, side);
            if (ts.isEmpty()) return null;
            if (level == 0) {
                List<int[]> pick = ts.get(RND.nextInt(ts.size()));
                for (List<int[]> t : ts)                       // a tiger keeps eating while it can
                    if (t.get(0) == pick.get(0) && t.size() > pick.size()) pick = t;
                return pick;
            }
            int depth = level == 1 ? 2 : 4;
            int best = Integer.MIN_VALUE;
            List<int[]> bestT = ts.get(0);
            for (List<int[]> t : ts) {
                BState n = s.copy();
                BRules.applyTurn(n, t, side);
                int score = -search(n, 3 - side, depth - 1, -100000, 100000, win) * 10 + RND.nextInt(8);
                if (score > best) { best = score; bestT = t; }
            }
            return bestT;
        }

        static int search(BState s, int side, int depth, int alpha, int beta, int win) {
            if (s.captured >= win) return side == TIGER ? 10000 + depth : -(10000 + depth);
            List<List<int[]>> ts = BRules.turns(s, side);
            if (ts.isEmpty()) return -(10000 + depth);
            if (depth == 0) return eval(s, side, ts.size());
            int best = Integer.MIN_VALUE;
            for (List<int[]> t : ts) {
                BState n = s.copy();
                BRules.applyTurn(n, t, side);
                int sc = -search(n, 3 - side, depth - 1, -beta, -alpha, win);
                if (sc > best) best = sc;
                if (best > alpha) alpha = best;
                if (alpha >= beta) break;
            }
            return best;
        }

        static int eval(BState s, int side, int myTurns) {
            int tigerMoves = side == TIGER ? myTurns : BRules.moves(s, TIGER).size();
            int ts = 100 * s.captured + 6 * tigerMoves;
            return side == TIGER ? ts : -ts;
        }
    }

    // =====================================================================
    //  DRAWING HELPERS
    // =====================================================================
    static void paintSoil(Graphics2D g, int w, int h) {
        g.setPaint(new GradientPaint(0, 0, new Color(190, 142, 90), 0, h, new Color(150, 106, 62)));
        g.fillRect(0, 0, w, h);
        Random r = new Random(11);
        for (int i = 0; i < 700; i++) {
            int x = r.nextInt(Math.max(w, 1)), y = r.nextInt(Math.max(h, 1)), s = 2 + r.nextInt(6);
            g.setColor(r.nextBoolean() ? new Color(240, 210, 160, 55) : new Color(90, 55, 25, 55));
            g.fill(new Ellipse2D.Double(x, y, s, s * 0.7));
        }
    }

    /** s = distance between neighbouring board points (scale of the piece). */
    static void drawPiece(Graphics2D g, double x, double y, double s, int color, double angDeg, double k) {
        int kd = kind[color];
        if (kd == 0) drawSupari(g, x, y, 0.27 * s * k, angDeg);
        else if (kd == 2) drawBrick(g, x, y, 0.34 * s * k, angDeg);
        else if (kd == 3) drawStone(g, x, y, 0.3 * s * k, angDeg);
        else drawKathi(g, x, y, s * k, angDeg);
    }

    /** Small stone guti: a smooth grey pebble with speckles. */
    static void drawStone(Graphics2D g, double x, double y, double r, double angDeg) {
        AffineTransform old = g.getTransform();
        g.setColor(new Color(0, 0, 0, 80));
        g.fill(new Ellipse2D.Double(x - r * 0.9 + 2, y - r * 0.7 + 3, r * 1.9, r * 1.6));
        g.translate(x, y);
        g.rotate(Math.toRadians(angDeg));
        Path2D st = new Path2D.Double();
        st.moveTo(-0.9 * r, 0.0);
        st.curveTo(-0.9 * r, -0.75 * r, -0.2 * r, -1.0 * r, 0.45 * r, -0.85 * r);
        st.curveTo(1.0 * r, -0.6 * r, 1.05 * r, 0.3 * r, 0.7 * r, 0.75 * r);
        st.curveTo(0.3 * r, 1.05 * r, -0.7 * r, 0.95 * r, -0.9 * r, 0.0);
        st.closePath();
        g.setPaint(new RadialGradientPaint(new Point2D.Double(-0.3 * r, -0.4 * r), (float) (r * 1.7),
                new float[]{0f, 0.55f, 1f},
                new Color[]{new Color(206, 208, 210), new Color(138, 142, 148), new Color(70, 74, 82)}));
        g.fill(st);
        Shape oc = g.getClip();
        g.clip(st);
        g.setColor(new Color(60, 62, 68, 130));
        double[][] sp = {{-0.4, 0.2}, {0.3, -0.3}, {0.45, 0.45}, {-0.1, -0.55}, {-0.6, -0.1}, {0.1, 0.7}};
        for (double[] q : sp) g.fill(new Ellipse2D.Double(q[0] * r, q[1] * r, r * 0.12, r * 0.1));
        g.setColor(new Color(236, 238, 240, 120));
        g.setStroke(new BasicStroke(1f));
        g.draw(new Line2D.Double(-0.5 * r, 0.35 * r, 0.1 * r, 0.05 * r));
        g.setClip(oc);
        g.setColor(new Color(48, 50, 56));
        g.setStroke(new BasicStroke(1.3f));
        g.draw(st);
        g.setColor(new Color(255, 255, 255, 120));
        g.fill(new Ellipse2D.Double(-0.55 * r, -0.65 * r, r * 0.5, r * 0.28));
        g.setTransform(old);
    }

    /** Brickbats guti: a lump of crushed red brick with a few small crumbs. */
    static void drawBrick(Graphics2D g, double x, double y, double r, double angDeg) {
        AffineTransform old = g.getTransform();
        g.setColor(new Color(0, 0, 0, 80));
        g.fill(new Ellipse2D.Double(x - r * 0.9 + 2, y - r * 0.7 + 3, r * 1.9, r * 1.6));
        g.translate(x, y);
        g.rotate(Math.toRadians(angDeg));
        Path2D chip = new Path2D.Double();
        chip.moveTo(-0.85 * r, -0.2 * r);
        chip.lineTo(-0.35 * r, -0.85 * r);
        chip.lineTo(0.4 * r, -0.7 * r);
        chip.lineTo(0.9 * r, -0.1 * r);
        chip.lineTo(0.6 * r, 0.7 * r);
        chip.lineTo(-0.2 * r, 0.9 * r);
        chip.lineTo(-0.8 * r, 0.5 * r);
        chip.closePath();
        g.setPaint(new GradientPaint((float) (-r), (float) (-r), new Color(220, 104, 66),
                (float) r, (float) r, new Color(126, 42, 26)));
        g.fill(chip);
        Shape oc = g.getClip();
        g.clip(chip);
        g.setColor(new Color(90, 28, 16, 140));
        double[][] sp = {{-0.4, 0.1}, {0.2, -0.35}, {0.35, 0.35}, {-0.15, 0.5}, {0.05, 0.0}};
        for (double[] q : sp) g.fill(new Ellipse2D.Double(q[0] * r, q[1] * r, r * 0.14, r * 0.11));
        g.setColor(new Color(244, 170, 130, 120));
        g.fill(new Ellipse2D.Double(-0.55 * r, -0.55 * r, r * 0.45, r * 0.22));
        g.setClip(oc);
        g.setColor(new Color(84, 26, 14));
        g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(chip);
        g.setColor(new Color(176, 70, 44));
        g.fill(new Ellipse2D.Double(0.75 * r, 0.5 * r, 0.3 * r, 0.2 * r));
        g.fill(new Ellipse2D.Double(-1.05 * r, -0.65 * r, 0.24 * r, 0.18 * r));
        g.setColor(new Color(130, 46, 28));
        g.fill(new Ellipse2D.Double(0.95 * r, -0.4 * r, 0.16 * r, 0.12 * r));
        g.setTransform(old);
    }

    /** Small realistic shupari (areca nut): egg shaped, marbled, with a dark scar at the base. */
    static void drawSupari(Graphics2D g, double x, double y, double r, double angDeg) {
        AffineTransform old = g.getTransform();
        g.setColor(new Color(0, 0, 0, 75));
        g.fill(new Ellipse2D.Double(x - r * 0.85 + 2, y - r * 0.7 + 3, r * 1.8, r * 1.8));
        g.translate(x, y);
        g.rotate(Math.toRadians(angDeg));
        Path2D egg = new Path2D.Double();
        egg.moveTo(0, -1.1 * r);
        egg.curveTo(0.55 * r, -0.9 * r, 0.95 * r, -0.2 * r, 0.9 * r, 0.2 * r);
        egg.curveTo(0.85 * r, 0.7 * r, 0.4 * r, 1.0 * r, 0, 1.0 * r);
        egg.curveTo(-0.4 * r, 1.0 * r, -0.85 * r, 0.7 * r, -0.9 * r, 0.2 * r);
        egg.curveTo(-0.95 * r, -0.2 * r, -0.55 * r, -0.9 * r, 0, -1.1 * r);
        egg.closePath();
        g.setPaint(new RadialGradientPaint(new Point2D.Double(-r * 0.3, -r * 0.3), (float) (r * 1.6),
                new float[]{0f, 0.5f, 1f},
                new Color[]{new Color(156, 204, 96), new Color(78, 134, 46), new Color(30, 68, 20)}));
        g.fill(egg);
        Shape oldClip = g.getClip();
        g.clip(egg);
        g.setStroke(new BasicStroke((float) Math.max(1.0, r * 0.12)));
        for (int i = -3; i <= 3; i++) {
            Path2D st = new Path2D.Double();
            st.moveTo(0, -1.1 * r);
            st.quadTo(i * 0.55 * r, 0, i * 0.28 * r, r);
            g.setColor(i % 2 == 0 ? new Color(20, 60, 15, 90) : new Color(195, 228, 135, 85));
            g.draw(st);
        }
        g.setClip(oldClip);
        g.setColor(new Color(24, 52, 14));
        g.setStroke(new BasicStroke(1.3f));
        g.draw(egg);
        g.setColor(new Color(60, 40, 20, 210));
        g.fill(new Ellipse2D.Double(-r * 0.28, r * 0.74, r * 0.56, r * 0.22));
        g.setColor(new Color(255, 255, 255, 105));
        g.fill(new Ellipse2D.Double(-r * 0.55, -r * 0.62, r * 0.5, r * 0.3));
        g.setTransform(old);
    }

    /** Small realistic jhatar kathi (thin stick, badami wood colour with grain). */
    static void drawKathi(Graphics2D g, double x, double y, double s, double angDeg) {
        double L = 0.8 * s, W = 0.2 * s;
        AffineTransform old = g.getTransform();
        g.translate(x, y);
        g.rotate(Math.toRadians(angDeg));
        g.setColor(new Color(0, 0, 0, 75));
        g.fill(new RoundRectangle2D.Double(-L / 2 + 2, -W / 2 + 3, L, W, W, W));
        RoundRectangle2D rr = new RoundRectangle2D.Double(-L / 2, -W / 2, L, W, W * 0.7, W * 0.7);
        g.setPaint(new LinearGradientPaint(0f, (float) (-W / 2), 0f, (float) (W / 2),
                new float[]{0f, 0.35f, 1f},
                new Color[]{new Color(216, 174, 122), new Color(160, 108, 62), new Color(94, 56, 26)}));
        g.fill(rr);
        Shape oldClip = g.getClip();
        g.clip(rr);
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(70, 40, 18, 120));
        g.draw(new Line2D.Double(-L / 2, -W * 0.15, L / 2, -W * 0.1));
        g.draw(new Line2D.Double(-L / 2, W * 0.2, L / 2, W * 0.28));
        g.setColor(new Color(60, 34, 14, 150));
        g.fill(new Ellipse2D.Double(L * 0.12, -W * 0.22, W * 0.55, W * 0.4));
        g.setColor(new Color(255, 235, 200, 90));
        g.fill(new Rectangle2D.Double(-L / 2, -W / 2 + 1, L, W * 0.18));
        g.setClip(oldClip);
        g.setColor(new Color(238, 208, 160));
        g.fill(new Ellipse2D.Double(L / 2 - W * 0.5, -W * 0.33, W * 0.4, W * 0.66));
        g.fill(new Ellipse2D.Double(-L / 2 + W * 0.1, -W * 0.33, W * 0.4, W * 0.66));
        g.setColor(new Color(66, 38, 16));
        g.setStroke(new BasicStroke(1.3f));
        g.draw(rr);
        g.setTransform(old);
    }

    /** Bagh (tiger): orange face with dark stripes, ears, muzzle. r = face radius. */
    static void drawTiger(Graphics2D g, double x, double y, double r) {
        g.setColor(new Color(0, 0, 0, 75));
        g.fill(new Ellipse2D.Double(x - r * 0.9 + 2, y - r * 0.8 + 3, r * 1.9, r * 1.9));
        g.setColor(new Color(190, 92, 16));
        g.fill(new Ellipse2D.Double(x - r * 0.98, y - r * 1.08, r * 0.58, r * 0.58));
        g.fill(new Ellipse2D.Double(x + r * 0.40, y - r * 1.08, r * 0.58, r * 0.58));
        g.setColor(new Color(250, 214, 170));
        g.fill(new Ellipse2D.Double(x - r * 0.8, y - r * 0.95, r * 0.3, r * 0.3));
        g.fill(new Ellipse2D.Double(x + r * 0.5, y - r * 0.95, r * 0.3, r * 0.3));
        Ellipse2D face = new Ellipse2D.Double(x - r, y - r * 0.85, 2 * r, 1.85 * r);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(x - r * 0.3, y - r * 0.4), (float) (r * 1.5),
                new float[]{0f, 1f}, new Color[]{new Color(255, 178, 66), new Color(212, 108, 22)}));
        g.fill(face);
        Shape oc = g.getClip();
        g.clip(face);
        g.setColor(new Color(40, 20, 8));
        g.setStroke(new BasicStroke((float) Math.max(1.5, r * 0.15), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = -1; i <= 1; i++)
            g.draw(new Line2D.Double(x + i * 0.24 * r, y - 0.85 * r, x + i * 0.14 * r, y - 0.42 * r));
        for (int k = -1; k <= 1; k += 2) {
            g.draw(new Line2D.Double(x + k * r, y - 0.25 * r, x + k * 0.62 * r, y - 0.08 * r));
            g.draw(new Line2D.Double(x + k * r, y + 0.2 * r, x + k * 0.66 * r, y + 0.25 * r));
            g.draw(new Line2D.Double(x + k * 0.9 * r, y + 0.62 * r, x + k * 0.62 * r, y + 0.5 * r));
        }
        g.setClip(oc);
        g.setColor(new Color(252, 234, 205));
        g.fill(new Ellipse2D.Double(x - r * 0.46, y + r * 0.05, r * 0.92, r * 0.66));
        g.setColor(new Color(50, 25, 15));
        g.fill(new Ellipse2D.Double(x - r * 0.14, y + r * 0.1, r * 0.28, r * 0.18));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(new Line2D.Double(x, y + r * 0.28, x, y + r * 0.42));
        g.fill(new Ellipse2D.Double(x - r * 0.46, y - r * 0.28, r * 0.26, r * 0.2));
        g.fill(new Ellipse2D.Double(x + r * 0.2, y - r * 0.28, r * 0.26, r * 0.2));
        g.setColor(new Color(110, 55, 10));
        g.setStroke(new BasicStroke(1.4f));
        g.draw(face);
    }

    /** Chhagol (goat): small side-view goat with horns. r = body half length. */
    static void drawGoat(Graphics2D g, double x, double y, double r) {
        g.setColor(new Color(0, 0, 0, 70));
        g.fill(new Ellipse2D.Double(x - r * 0.95 + 2, y - r * 0.3 + 3, r * 2.1, r * 1.3));
        g.setStroke(new BasicStroke((float) Math.max(1.5, r * 0.18), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(96, 78, 60));
        g.draw(new Line2D.Double(x - r * 0.55, y + r * 0.4, x - r * 0.55, y + r * 0.95));
        g.draw(new Line2D.Double(x - r * 0.2, y + r * 0.4, x - r * 0.2, y + r * 0.95));
        g.draw(new Line2D.Double(x + r * 0.35, y + r * 0.4, x + r * 0.35, y + r * 0.95));
        g.draw(new Line2D.Double(x + r * 0.65, y + r * 0.4, x + r * 0.65, y + r * 0.95));
        g.setColor(new Color(150, 130, 108));
        g.draw(new Line2D.Double(x - r * 0.95, y - r * 0.2, x - r * 1.15, y - r * 0.45));
        Ellipse2D body = new Ellipse2D.Double(x - r, y - r * 0.5, 2 * r, r * 1.0);
        g.setPaint(new GradientPaint((float) x, (float) (y - r * 0.5), new Color(252, 246, 232),
                (float) x, (float) (y + r * 0.5), new Color(206, 192, 170)));
        g.fill(body);
        g.setColor(new Color(110, 92, 72));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(body);
        g.setStroke(new BasicStroke((float) Math.max(1.5, r * 0.2), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(236, 226, 206));
        g.draw(new Line2D.Double(x + r * 0.7, y - r * 0.25, x + r * 1.0, y - r * 0.6));
        Ellipse2D head = new Ellipse2D.Double(x + r * 0.78, y - r * 0.95, r * 0.8, r * 0.62);
        g.setColor(new Color(250, 242, 226));
        g.fill(head);
        g.setColor(new Color(110, 92, 72));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(head);
        g.setStroke(new BasicStroke((float) Math.max(1.4, r * 0.14), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(70, 55, 40));
        g.draw(new Line2D.Double(x + r * 0.95, y - r * 0.92, x + r * 0.85, y - r * 1.32));
        g.draw(new Line2D.Double(x + r * 1.2, y - r * 0.95, x + r * 1.18, y - r * 1.35));
        g.fill(new Ellipse2D.Double(x + r * 1.18, y - r * 0.78, r * 0.12, r * 0.12));
        g.fill(new Ellipse2D.Double(x + r * 1.5, y - r * 0.62, r * 0.1, r * 0.1));
    }

    static double angOf(int n, int color) {
        return kind[color] == 1 ? (n * 37) % 180 - 15 : (n * 53) % 360;
    }

    // =====================================================================
    //  BOARD PANEL - 16 GUTI (game screen)
    // =====================================================================
    static final class BoardPanel extends JPanel {
        final Runnable onHome;
        int[] cell = Rules.initial();
        int turn = GREEN, winner;
        boolean over;
        int[] lost = new int[3];
        String[] names = {"", "Player 1", "Player 2"};
        int humanSide, level;
        boolean flipped, chaining, aiBusy, paused, active;
        int gen;
        int selected = -1;
        List<int[]> targets = new ArrayList<>();
        String winReason = "";
        int totalSeconds, remaining;
        final JButton endTurnBtn = new JButton("End Turn");
        final JButton pauseBtn = new JButton("Pause");
        final JButton newBtn = new JButton("New Game");
        final JButton hintBtn = new JButton("Hint: OFF");
        final Timer clock;
        boolean hintOn;
        int[] hintPath;
        double s, cx, cy;

        BoardPanel(Runnable onHome) {
            this.onHome = onHome;
            setPreferredSize(new Dimension(900, 640));
            clock = new Timer(1000, e -> tick());
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { click(e.getX(), e.getY()); }
            });
        }

        void start(String greenName, String brownName, int humanSide, int level, int seconds) {
            names[GREEN] = greenName;
            names[BROWN] = brownName;
            this.humanSide = humanSide;
            this.level = level;
            this.flipped = humanSide == BROWN;
            this.totalSeconds = seconds;
            this.active = true;
            restart();
        }

        void restart() {
            gen++;
            cell = Rules.initial();
            turn = GREEN;
            winner = 0;
            over = false;
            winReason = "";
            lost = new int[3];
            selected = -1;
            chaining = false;
            aiBusy = false;
            paused = false;
            pauseBtn.setText("Pause");
            targets = new ArrayList<>();
            endTurnBtn.setEnabled(false);
            remaining = totalSeconds;
            if (totalSeconds > 0) clock.restart(); else clock.stop();
            updateHint();
            repaint();
            if (isComputerTurn()) startAi();
        }

        void updateHint() {
            hintPath = null;
            if (hintOn && !over && !chaining && !paused && !isComputerTurn())
                hintPath = Ai.choose(cell, turn, 1);
        }

        void setHint(boolean on) {
            hintOn = on;
            hintBtn.setText(on ? "Hint: ON" : "Hint: OFF");
            updateHint();
            repaint();
        }

        boolean isComputerTurn() { return humanSide != 0 && turn != humanSide && !over; }

        void leave() {
            active = false;
            gen++;
            clock.stop();
            onHome.run();
        }

        void exitGame() {
            int r = JOptionPane.showConfirmDialog(this, "Exit to the home page? The current game will end.",
                    "Exit", JOptionPane.YES_NO_OPTION);
            if (r != JOptionPane.YES_OPTION) return;
            leave();
        }

        void setPaused(boolean p) {
            if (over) return;
            paused = p;
            pauseBtn.setText(p ? "Resume" : "Pause");
            updateHint();
            repaint();
        }

        void tick() {
            if (paused || over || totalSeconds <= 0 || !active) return;
            remaining--;
            if (remaining <= 0) { remaining = 0; timeUp(); }
            repaint();
        }

        void timeUp() {
            selected = -1;
            chaining = false;
            targets = new ArrayList<>();
            int g = Rules.count(cell, GREEN), b = Rules.count(cell, BROWN);
            if (g == b) finish(0, "Time is up! Both players have " + g + " guti.");
            else {
                int w = g > b ? GREEN : BROWN;
                finish(w, "Time is up! " + names[w] + " has more guti (" + Math.max(g, b) + " vs " + Math.min(g, b) + ").");
            }
        }

        // ---------------- input ----------------
        int nodeAt(int mx, int my) {
            layoutVals();
            int best = -1;
            double bd = 0.5 * s;
            for (int n = 0; n < Geo.N; n++) {
                double d = pt(n).distance(mx, my);
                if (d < bd) { bd = d; best = n; }
            }
            return best;
        }

        void click(int mx, int my) {
            if (over || paused || aiBusy || isComputerTurn()) return;
            int n = nodeAt(mx, my);
            if (n < 0) return;
            if (chaining) {
                if (n == selected) { endTurn(); return; }
                for (int[] t : targets) if (t[0] == n) { doStep(selected, t); return; }
                return;
            }
            if (cell[n] == turn) {
                selected = n;
                targets = Rules.options(cell, n, false);
                repaint();
                return;
            }
            if (selected >= 0)
                for (int[] t : targets) if (t[0] == n) { doStep(selected, t); return; }
            selected = -1;
            targets = new ArrayList<>();
            repaint();
        }

        void doStep(int from, int[] t) {
            if (t[1] >= 0) { lost[cell[t[1]]]++; Sound.capture(); } else Sound.move();
            Rules.step(cell, from, t[0]);
            selected = t[0];
            if (t[1] >= 0) {
                targets = Rules.options(cell, selected, true);   // manual multi-jump
                if (!targets.isEmpty()) {
                    chaining = true;
                    hintPath = null;
                    endTurnBtn.setEnabled(true);
                    repaint();
                    return;
                }
            }
            endTurn();
        }

        /** Called when the LOCAL player (human or computer) finishes the turn. */
        void endTurn() {
            endTurnCore();
        }

        void endTurnCore() {
            selected = -1;
            chaining = false;
            targets = new ArrayList<>();
            endTurnBtn.setEnabled(false);
            int mover = turn;
            turn = 3 - turn;
            if (Rules.count(cell, turn) == 0) {
                finish(mover, names[turn] + " has no guti left.");
            } else if (!Rules.hasMove(cell, turn)) {
                finish(mover, names[turn] + " is stuck and cannot move.");
            } else if (isComputerTurn()) {
                startAi();
            }
            updateHint();
            repaint();
        }

        void finish(final int w, final String why) {
            over = true;
            winner = w;
            winReason = why;
            chaining = false;
            selected = -1;
            targets = new ArrayList<>();
            endTurnBtn.setEnabled(false);
            hintPath = null;
            clock.stop();
            repaint();
            if (w == 0) Sound.draw();
            else if (humanSide != 0 && w != humanSide) Sound.lose();
            else Sound.win();
            final int g0 = gen;
            SwingUtilities.invokeLater(() -> {
                String title = w == 0 ? "It's a draw!" : names[w] + " wins!";
                Object[] opts = {"Play Again", "Home"};
                int r = JOptionPane.showOptionDialog(this, title + "\n" + why, "Game Over",
                        JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, opts, opts[0]);
                if (g0 != gen) return;
                if (r == 0) restart();
                else if (r == 1) leave();
            });
        }

        // ---------------- computer ----------------
        void startAi() {
            aiBusy = true;
            final int g0 = gen;
            Timer wait = new Timer(650, e -> {
                Timer self = (Timer) e.getSource();
                if (g0 != gen || over) { aiBusy = false; return; }
                if (paused) { self.restart(); return; }
                int[] path = Ai.choose(cell, turn, level);
                if (path == null) { aiBusy = false; return; }
                animate(path, g0);
            });
            wait.setRepeats(false);
            wait.start();
        }

        void animate(final int[] path, final int g0) {
            final int[] i = {1};
            selected = path[0];
            final Timer t = new Timer(520, null);
            t.addActionListener(e -> {
                if (g0 != gen || over) { t.stop(); aiBusy = false; return; }
                if (paused) return;
                if (i[0] >= path.length) {
                    t.stop();
                    aiBusy = false;
                    endTurn();
                    return;
                }
                int a = path[i[0] - 1], b = path[i[0]];
                int o = Geo.E[a][b] ? -1 : Rules.overOf(a, b);
                if (o >= 0) { lost[cell[o]]++; Sound.capture(); } else Sound.move();
                Rules.step(cell, a, b);
                selected = b;
                i[0]++;
                repaint();
            });
            t.start();
        }

        // ---------------- painting ----------------
        void layoutVals() {
            int W = getWidth(), H = getHeight();
            s = Math.min((H - 120) / 14.4, (W - 40) / 17.8);
            cx = W / 2.0;
            cy = 100 + (H - 120) / 2.0;
        }

        Point2D pt(int n) {
            int x = Geo.X[n], y = Geo.Y[n];
            if (flipped) { x = 8 - x; y = 8 - y; }
            return new Point2D.Double(cx + (x - 4) * s, cy + (y - 4) * s);
        }

        @Override protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            paintSoil(g, getWidth(), getHeight());
            layoutVals();

            RoundRectangle2D plate = new RoundRectangle2D.Double(cx - 5.2 * s, cy - 7.2 * s, 10.4 * s, 14.4 * s, 44, 44);
            g.setColor(new Color(0, 0, 0, 50));
            g.fill(new RoundRectangle2D.Double(cx - 5.2 * s + 6, cy - 7.2 * s + 8, 10.4 * s, 14.4 * s, 44, 44));
            g.setPaint(new GradientPaint(0, (float) (cy - 7 * s), new Color(214, 170, 118),
                    0, (float) (cy + 7 * s), new Color(198, 152, 100)));
            g.fill(plate);
            Shape oldClip = g.getClip();
            g.clip(plate);
            Random pr = new Random(3);
            for (int i = 0; i < 260; i++) {
                double x = cx - 5.2 * s + pr.nextDouble() * 10.4 * s, y = cy - 7.2 * s + pr.nextDouble() * 14.4 * s;
                double sz = 2 + pr.nextInt(5);
                g.setColor(pr.nextBoolean() ? new Color(245, 220, 175, 60) : new Color(110, 70, 35, 45));
                g.fill(new Ellipse2D.Double(x, y, sz, sz * 0.7));
            }
            g.setClip(oldClip);
            g.setColor(new Color(132, 90, 48));
            g.setStroke(new BasicStroke(6f));
            g.draw(plate);

            for (int a = 0; a < Geo.N; a++)
                for (int b = a + 1; b < Geo.N; b++)
                    if (Geo.E[a][b]) {
                        Point2D p = pt(a), q = pt(b);
                        g.setStroke(new BasicStroke(4.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g.setColor(LINE_LIGHT);
                        g.draw(new Line2D.Double(p.getX() + 1.5, p.getY() + 1.5, q.getX() + 1.5, q.getY() + 1.5));
                        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g.setColor(LINE);
                        g.draw(new Line2D.Double(p, q));
                    }
            g.setColor(LINE);
            for (int n = 0; n < Geo.N; n++) {
                Point2D p = pt(n);
                g.fill(new Ellipse2D.Double(p.getX() - 4, p.getY() - 4, 8, 8));
            }

            drawPit(g, cx - 7.2 * s, cy, GREEN);
            drawPit(g, cx + 7.2 * s, cy, BROWN);

            double hr = 0.3 * s;
            for (int[] t : targets) {
                Point2D p = pt(t[0]);
                g.setColor(t[1] >= 0 ? new Color(230, 80, 50, 175) : new Color(255, 235, 90, 195));
                g.fill(new Ellipse2D.Double(p.getX() - hr, p.getY() - hr, 2 * hr, 2 * hr));
                g.setColor(new Color(60, 30, 10));
                g.setStroke(new BasicStroke(2f));
                g.draw(new Ellipse2D.Double(p.getX() - hr, p.getY() - hr, 2 * hr, 2 * hr));
            }

            for (int n = 0; n < Geo.N; n++)
                if (cell[n] != 0) {
                    Point2D p = pt(n);
                    drawPiece(g, p.getX(), p.getY(), s, cell[n], angOf(n, cell[n]), 1.0);
                }
            if (selected >= 0 && cell[selected] != 0) {
                Point2D p = pt(selected);
                double sr = 0.38 * s;
                g.setColor(new Color(255, 225, 40));
                g.setStroke(new BasicStroke(3.2f));
                g.draw(new Ellipse2D.Double(p.getX() - sr, p.getY() - sr, 2 * sr, 2 * sr));
            }

            if (hintOn && hintPath != null && !chaining && !over && !paused) {
                g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, new float[]{10f, 8f}, 0f));
                g.setColor(new Color(25, 105, 225, 230));
                for (int i = 1; i < hintPath.length; i++) {
                    Point2D p = pt(hintPath[i - 1]), q = pt(hintPath[i]);
                    g.draw(new Line2D.Double(p, q));
                    double a = Math.atan2(q.getY() - p.getY(), q.getX() - p.getX());
                    double hx = q.getX() - 0.2 * s * Math.cos(a), hy = q.getY() - 0.2 * s * Math.sin(a);
                    Stroke old = g.getStroke();
                    g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.draw(new Line2D.Double(hx, hy, hx - 0.18 * s * Math.cos(a - 0.5), hy - 0.18 * s * Math.sin(a - 0.5)));
                    g.draw(new Line2D.Double(hx, hy, hx - 0.18 * s * Math.cos(a + 0.5), hy - 0.18 * s * Math.sin(a + 0.5)));
                    g.setStroke(old);
                }
                Point2D st = pt(hintPath[0]), en = pt(hintPath[hintPath.length - 1]);
                g.setStroke(new BasicStroke(3.5f));
                g.draw(new Ellipse2D.Double(st.getX() - 0.42 * s, st.getY() - 0.42 * s, 0.84 * s, 0.84 * s));
                g.setColor(new Color(25, 105, 225, 120));
                g.fill(new Ellipse2D.Double(en.getX() - 0.22 * s, en.getY() - 0.22 * s, 0.44 * s, 0.44 * s));
            }

            paintHud(g);

            if (paused) {
                g.setColor(new Color(70, 40, 18, 228));
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(new Color(245, 225, 190));
                g.setFont(new Font("SansSerif", Font.BOLD, 56));
                String t = "PAUSED";
                g.drawString(t, (getWidth() - g.getFontMetrics().stringWidth(t)) / 2, getHeight() / 2 - 6);
                g.setFont(new Font("SansSerif", Font.PLAIN, 18));
                String t2 = "Click Resume to continue";
                g.drawString(t2, (getWidth() - g.getFontMetrics().stringWidth(t2)) / 2, getHeight() / 2 + 30);
            }
            g.dispose();
        }

        void drawPit(Graphics2D g, double px, double py, int color) {
            double R = 1.75 * s;
            g.setColor(new Color(0, 0, 0, 45));
            g.fill(new Ellipse2D.Double(px - R + 4, py - R + 5, 2 * R, 2 * R));
            g.setPaint(new RadialGradientPaint(new Point2D.Double(px, py), (float) R,
                    new float[]{0f, 0.8f, 1f},
                    new Color[]{new Color(112, 74, 40), new Color(138, 94, 54), new Color(170, 124, 78)}));
            g.fill(new Ellipse2D.Double(px - R, py - R, 2 * R, 2 * R));
            g.setColor(new Color(105, 68, 34));
            g.setStroke(new BasicStroke(4f));
            g.draw(new Ellipse2D.Double(px - R, py - R, 2 * R, 2 * R));
            int n = lost[color];
            for (int i = 0; i < n && i < 16; i++) {
                double x = px, y = py, ang = (i * 61) % 180;
                if (i >= 1 && i <= 6) {
                    double a = Math.toRadians(60 * (i - 1));
                    x += 0.85 * s * Math.cos(a); y += 0.85 * s * Math.sin(a);
                    ang = Math.toDegrees(a) + 90;
                } else if (i >= 7) {
                    double a = Math.toRadians(40 * (i - 7) + 20);
                    x += 1.3 * s * Math.cos(a); y += 1.3 * s * Math.sin(a);
                    ang = Math.toDegrees(a) + 90;
                }
                drawPiece(g, x, y, s, color, ang, kind[color] == 1 ? 0.85 : 1.0);
            }
            g.setFont(new Font("SansSerif", Font.BOLD, 14));
            g.setColor(TEXT_DARK);
            String lbl = names[color] + " lost: " + n;
            int w = g.getFontMetrics().stringWidth(lbl);
            g.drawString(lbl, (float) (px - w / 2.0), (float) (py + R + 24));
        }

        void paintHud(Graphics2D g) {
            int W = getWidth();
            drawCard(g, 18, 14, GREEN);
            drawCard(g, W - 218, 14, BROWN);
            String main, sub;
            if (over) {
                main = winner == 0 ? "Draw!" : names[winner] + " wins!";
                sub = winReason;
            } else {
                main = "Turn: " + names[turn] + " (" + pieceName(turn) + ")";
                if (paused) sub = "Game paused";
                else if (aiBusy || isComputerTurn()) sub = "Computer is thinking...";
                else if (chaining) sub = "Jump again (highlighted spot) or click End Turn.";
                else if (selected >= 0) sub = "Click a highlighted spot to move.";
                else sub = "Select one of your guti.";
            }
            g.setFont(new Font("SansSerif", Font.BOLD, 24));
            g.setColor(TEXT_DARK);
            g.drawString(main, (W - g.getFontMetrics().stringWidth(main)) / 2, 34);
            g.setFont(new Font("SansSerif", Font.PLAIN, 14));
            g.drawString(sub, (W - g.getFontMetrics().stringWidth(sub)) / 2, 56);
            String clk;
            if (totalSeconds <= 0) clk = "No time limit";
            else clk = String.format("Time left: %02d:%02d:%02d", remaining / 3600, (remaining / 60) % 60, remaining % 60);
            g.setFont(new Font("SansSerif", Font.BOLD, 18));
            g.setColor(totalSeconds > 0 && remaining <= 30 && !over ? new Color(170, 30, 20) : TEXT_DARK);
            g.drawString(clk, (W - g.getFontMetrics().stringWidth(clk)) / 2, 82);
        }

        void drawCard(Graphics2D g, int x, int y, int color) {
            g.setColor(new Color(220, 176, 122, 215));
            g.fill(new RoundRectangle2D.Double(x, y, 200, 52, 18, 18));
            g.setColor(new Color(132, 90, 48));
            g.setStroke(new BasicStroke(2f));
            g.draw(new RoundRectangle2D.Double(x, y, 200, 52, 18, 18));
            drawPiece(g, x + 26, y + 26, 44, color, kind[color] == 1 ? -35 : 20, kind[color] == 1 ? 1.0 : kind[color] == 2 ? 1.2 : kind[color] == 3 ? 1.4 : 1.5);
            g.setColor(TEXT_DARK);
            g.setFont(new Font("SansSerif", Font.BOLD, 14));
            String nm = names[color];
            if (nm.length() > 14) nm = nm.substring(0, 13) + "...";
            g.drawString(nm, x + 50, y + 22);
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g.drawString(pieceName(color) + " - " + Rules.count(cell, color) + " left",
                    x + 50, y + 40);
        }
    }

    // =====================================================================
    //  BOARD PANEL - BAGH-CHHAGOL (game screen)
    // =====================================================================
    static final class BaghPanel extends JPanel {
        final Runnable onHome;
        BState st = BState.initial();
        int turn = GOAT, winner;
        boolean over;
        String[] names = {"", "Tiger player", "Goat player"};
        int humanSide, level, winCount = GOATS_TOTAL;
        boolean aiBusy, paused, active, hintOn;
        int gen;
        int selected = -1;
        List<int[]> targets = new ArrayList<>();
        String winReason = "";
        int totalSeconds, remaining;
        final JButton pauseBtn = new JButton("Pause");
        final JButton newBtn = new JButton("New Game");
        final JButton hintBtn = new JButton("Hint: OFF");
        final Timer clock;
        List<int[]> hintMove;
        boolean chaining;
        final JButton endTurnBtn = new JButton("End Turn");
        double s, cx, cy;

        BaghPanel(Runnable onHome) {
            this.onHome = onHome;
            setPreferredSize(new Dimension(900, 640));
            clock = new Timer(1000, e -> tick());
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { click(e.getX(), e.getY()); }
            });
        }

        void start(String tigerName, String goatName, int humanSide, int level, int seconds, int winCount) {
            names[TIGER] = tigerName;
            names[GOAT] = goatName;
            this.humanSide = humanSide;
            this.level = level;
            this.totalSeconds = seconds;
            this.winCount = winCount;
            this.active = true;
            restart();
        }

        void restart() {
            gen++;
            st = BState.initial();
            turn = GOAT;                      // the goat player always moves first
            winner = 0;
            over = false;
            winReason = "";
            selected = -1;
            chaining = false;
            endTurnBtn.setEnabled(false);
            aiBusy = false;
            paused = false;
            pauseBtn.setText("Pause");
            targets = new ArrayList<>();
            remaining = totalSeconds;
            if (totalSeconds > 0) clock.restart(); else clock.stop();
            updateHint();
            repaint();
            if (isComputerTurn()) startAi();
        }

        boolean isComputerTurn() { return humanSide != 0 && turn != humanSide && !over; }

        void updateHint() {
            hintMove = null;
            if (hintOn && !over && !paused && !chaining && !isComputerTurn())
                hintMove = BAi.choose(st, turn, 1, winCount);
        }

        void setHint(boolean on) {
            hintOn = on;
            hintBtn.setText(on ? "Hint: ON" : "Hint: OFF");
            updateHint();
            repaint();
        }

        void leave() {
            active = false;
            gen++;
            clock.stop();
            onHome.run();
        }

        void exitGame() {
            int r = JOptionPane.showConfirmDialog(this, "Exit to the home page? The current game will end.",
                    "Exit", JOptionPane.YES_NO_OPTION);
            if (r != JOptionPane.YES_OPTION) return;
            leave();
        }

        void setPaused(boolean p) {
            if (over) return;
            paused = p;
            pauseBtn.setText(p ? "Resume" : "Pause");
            updateHint();
            repaint();
        }

        void tick() {
            if (paused || over || totalSeconds <= 0 || !active) return;
            remaining--;
            if (remaining <= 0) { remaining = 0; timeUp(); }
            repaint();
        }

        void timeUp() {
            selected = -1;
            targets = new ArrayList<>();
            if (st.captured >= 8)
                finish(TIGER, "Time is up! The tigers captured " + st.captured + " goats.");
            else
                finish(GOAT, "Time is up! The tigers captured only " + st.captured + " goat(s).");
        }

        // ---------------- input ----------------
        int nodeAt(int mx, int my) {
            layoutVals();
            int best = -1;
            double bd = 0.5 * s;
            for (int n = 0; n < Geo.N; n++) {
                double d = pt(n).distance(mx, my);
                if (d < bd) { bd = d; best = n; }
            }
            return best;
        }

        List<int[]> optionsFor(int n) {
            List<int[]> r = new ArrayList<>();
            for (int[] m : BRules.moves(st, turn)) if (m[0] == n) r.add(new int[]{m[1], m[2]});
            return r;
        }

        void click(int mx, int my) {
            if (over || paused || aiBusy || isComputerTurn()) return;
            int n = nodeAt(mx, my);
            if (n < 0) return;
            if (chaining) {                                    // tiger may keep eating
                if (n == selected) { endTurn(); return; }
                for (int[] t : targets) if (t[0] == n) { doStep(new int[]{selected, n, t[1]}); return; }
                return;
            }
            if (turn == GOAT && st.placed < GOATS_TOTAL) {      // placing phase
                if (st.c[n] == 0) doStep(new int[]{-1, n, -1});
                return;
            }
            if (st.c[n] == turn) {
                selected = n;
                targets = optionsFor(n);
                repaint();
                return;
            }
            if (selected >= 0)
                for (int[] t : targets) if (t[0] == n) { doStep(new int[]{selected, n, t[1]}); return; }
            selected = -1;
            targets = new ArrayList<>();
            repaint();
        }

        /** Further jumps (over a goat into an empty point) for the tiger standing on point n. */
        List<int[]> jumpTargets(int n) {
            List<int[]> r = new ArrayList<>();
            for (int[] j : Geo.JUMP[n]) if (st.c[j[0]] == GOAT && st.c[j[1]] == 0) r.add(new int[]{j[1], j[0]});
            return r;
        }

        /** One step by the LOCAL human player. A tiger that has just eaten may jump again (multi-jump). */
        void doStep(int[] m) {
            applyStep(m);
            selected = m[1];
            if (turn == TIGER && m[2] >= 0 && st.captured < winCount) {
                targets = jumpTargets(selected);
                if (!targets.isEmpty()) {
                    chaining = true;
                    hintMove = null;
                    endTurnBtn.setEnabled(true);
                    repaint();
                    return;
                }
            }
            endTurn();
        }

        /** Called when the LOCAL player (human or computer) finishes the turn. */
        void endTurn() {
            endTurnCore();
        }

        void applyStep(int[] m) {
            if (m[2] >= 0) Sound.capture(); else Sound.move();
            BRules.apply(st, m, turn);
        }

        void endTurnCore() {
            selected = -1;
            targets = new ArrayList<>();
            chaining = false;
            endTurnBtn.setEnabled(false);
            turn = 3 - turn;
            if (st.captured >= winCount) {
                finish(TIGER, "The tigers captured " + st.captured + " goats.");
            } else if (turn == TIGER && BRules.moves(st, TIGER).isEmpty()) {
                finish(GOAT, "All the tigers are trapped and cannot move.");
            } else if (turn == GOAT && st.placed >= GOATS_TOTAL && BRules.moves(st, GOAT).isEmpty()) {
                finish(TIGER, "The goats are stuck and cannot move.");
            } else if (isComputerTurn()) {
                startAi();
            }
            updateHint();
            repaint();
        }

        void finish(final int w, final String why) {
            over = true;
            winner = w;
            winReason = why;
            selected = -1;
            targets = new ArrayList<>();
            hintMove = null;
            chaining = false;
            endTurnBtn.setEnabled(false);
            clock.stop();
            repaint();
            if (humanSide != 0 && w != humanSide) Sound.lose(); else Sound.win();
            final int g0 = gen;
            SwingUtilities.invokeLater(() -> {
                String title = names[w] + " wins!  (" + (w == TIGER ? "Bagh" : "Chhagol") + ")";
                Object[] opts = {"Play Again", "Home"};
                int r = JOptionPane.showOptionDialog(this, title + "\n" + why, "Game Over",
                        JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, opts, opts[0]);
                if (g0 != gen) return;
                if (r == 0) restart();
                else if (r == 1) leave();
            });
        }

        // ---------------- computer ----------------
        void startAi() {
            aiBusy = true;
            final int g0 = gen;
            Timer wait = new Timer(650, e -> {
                Timer self = (Timer) e.getSource();
                if (g0 != gen || over) { aiBusy = false; return; }
                if (paused) { self.restart(); return; }
                List<int[]> path = BAi.choose(st, turn, level, winCount);
                if (path == null) { aiBusy = false; return; }
                animate(path, g0);
            });
            wait.setRepeats(false);
            wait.start();
        }

        /** The computer plays its turn step by step (a tiger may chain several jumps). */
        void animate(final List<int[]> path, final int g0) {
            final int[] i = {0};
            final Timer t = new Timer(520, null);
            t.addActionListener(e -> {
                if (g0 != gen || over) { t.stop(); aiBusy = false; return; }
                if (paused) return;
                if (i[0] >= path.size()) {
                    t.stop();
                    aiBusy = false;
                    endTurn();
                    return;
                }
                int[] m = path.get(i[0]++);
                applyStep(m);
                selected = m[1];
                repaint();
            });
            t.start();
        }

        // ---------------- painting ----------------
        void layoutVals() {
            int W = getWidth(), H = getHeight();
            s = Math.min((H - 120) / 14.4, (W - 40) / 17.8);
            cx = W / 2.0;
            cy = 100 + (H - 120) / 2.0;
        }

        Point2D pt(int n) {
            return new Point2D.Double(cx + (Geo.X[n] - 4) * s, cy + (Geo.Y[n] - 4) * s);
        }

        @Override protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            paintSoil(g, getWidth(), getHeight());
            layoutVals();

            RoundRectangle2D plate = new RoundRectangle2D.Double(cx - 5.2 * s, cy - 7.2 * s, 10.4 * s, 14.4 * s, 44, 44);
            g.setColor(new Color(0, 0, 0, 50));
            g.fill(new RoundRectangle2D.Double(cx - 5.2 * s + 6, cy - 7.2 * s + 8, 10.4 * s, 14.4 * s, 44, 44));
            g.setPaint(new GradientPaint(0, (float) (cy - 7 * s), new Color(214, 170, 118),
                    0, (float) (cy + 7 * s), new Color(198, 152, 100)));
            g.fill(plate);
            Shape oldClip = g.getClip();
            g.clip(plate);
            Random pr = new Random(3);
            for (int i = 0; i < 260; i++) {
                double x = cx - 5.2 * s + pr.nextDouble() * 10.4 * s, y = cy - 7.2 * s + pr.nextDouble() * 14.4 * s;
                double sz = 2 + pr.nextInt(5);
                g.setColor(pr.nextBoolean() ? new Color(245, 220, 175, 60) : new Color(110, 70, 35, 45));
                g.fill(new Ellipse2D.Double(x, y, sz, sz * 0.7));
            }
            g.setClip(oldClip);
            g.setColor(new Color(132, 90, 48));
            g.setStroke(new BasicStroke(6f));
            g.draw(plate);

            for (int a = 0; a < Geo.N; a++)
                for (int b = a + 1; b < Geo.N; b++)
                    if (Geo.E[a][b]) {
                        Point2D p = pt(a), q = pt(b);
                        g.setStroke(new BasicStroke(4.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g.setColor(LINE_LIGHT);
                        g.draw(new Line2D.Double(p.getX() + 1.5, p.getY() + 1.5, q.getX() + 1.5, q.getY() + 1.5));
                        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g.setColor(LINE);
                        g.draw(new Line2D.Double(p, q));
                    }
            g.setColor(LINE);
            for (int n = 0; n < Geo.N; n++) {
                Point2D p = pt(n);
                g.fill(new Ellipse2D.Double(p.getX() - 4, p.getY() - 4, 8, 8));
            }

            drawPit(g, cx - 7.2 * s, cy, GOATS_TOTAL - st.placed, "Goats in hand: " + (GOATS_TOTAL - st.placed));
            drawPit(g, cx + 7.2 * s, cy, st.captured, "Captured goats: " + st.captured);

            if (turn == GOAT && st.placed < GOATS_TOTAL && !over && !paused && !isComputerTurn()) {
                g.setColor(new Color(255, 235, 90, 120));
                for (int n = 0; n < Geo.N; n++)
                    if (st.c[n] == 0) {
                        Point2D p = pt(n);
                        g.fill(new Ellipse2D.Double(p.getX() - 0.16 * s, p.getY() - 0.16 * s, 0.32 * s, 0.32 * s));
                    }
            }
            double hr = 0.3 * s;
            for (int[] t : targets) {
                Point2D p = pt(t[0]);
                g.setColor(t[1] >= 0 ? new Color(230, 80, 50, 175) : new Color(255, 235, 90, 195));
                g.fill(new Ellipse2D.Double(p.getX() - hr, p.getY() - hr, 2 * hr, 2 * hr));
                g.setColor(new Color(60, 30, 10));
                g.setStroke(new BasicStroke(2f));
                g.draw(new Ellipse2D.Double(p.getX() - hr, p.getY() - hr, 2 * hr, 2 * hr));
            }

            for (int n = 0; n < Geo.N; n++) {
                if (st.c[n] == 0) continue;
                Point2D p = pt(n);
                if (st.c[n] == TIGER) drawTiger(g, p.getX(), p.getY(), 0.46 * s);
                else drawGoat(g, p.getX() - 0.05 * s, p.getY() - 0.05 * s, 0.34 * s);
            }
            if (selected >= 0 && st.c[selected] != 0) {
                Point2D p = pt(selected);
                double sr = 0.6 * s;
                g.setColor(new Color(255, 225, 40));
                g.setStroke(new BasicStroke(3.2f));
                g.draw(new Ellipse2D.Double(p.getX() - sr, p.getY() - sr, 2 * sr, 2 * sr));
            }

            if (hintOn && hintMove != null && !over && !paused && !chaining) {
                g.setColor(new Color(25, 105, 225, 230));
                int[] firstStep = hintMove.get(0), lastStep = hintMove.get(hintMove.size() - 1);
                for (int[] m : hintMove) {
                    if (m[0] < 0) continue;
                    Point2D p = pt(m[0]), q = pt(m[1]);
                    g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, new float[]{10f, 8f}, 0f));
                    g.draw(new Line2D.Double(p, q));
                    double a = Math.atan2(q.getY() - p.getY(), q.getX() - p.getX());
                    double hx = q.getX() - 0.2 * s * Math.cos(a), hy = q.getY() - 0.2 * s * Math.sin(a);
                    g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.draw(new Line2D.Double(hx, hy, hx - 0.18 * s * Math.cos(a - 0.5), hy - 0.18 * s * Math.sin(a - 0.5)));
                    g.draw(new Line2D.Double(hx, hy, hx - 0.18 * s * Math.cos(a + 0.5), hy - 0.18 * s * Math.sin(a + 0.5)));
                }
                if (firstStep[0] >= 0) {
                    Point2D p = pt(firstStep[0]);
                    g.setStroke(new BasicStroke(3.5f));
                    g.draw(new Ellipse2D.Double(p.getX() - 0.62 * s, p.getY() - 0.62 * s, 1.24 * s, 1.24 * s));
                }
                Point2D en = pt(lastStep[1]);
                g.setStroke(new BasicStroke(3.5f));
                g.draw(new Ellipse2D.Double(en.getX() - 0.5 * s, en.getY() - 0.5 * s, s, s));
                g.setColor(new Color(25, 105, 225, 110));
                g.fill(new Ellipse2D.Double(en.getX() - 0.25 * s, en.getY() - 0.25 * s, 0.5 * s, 0.5 * s));
            }

            paintHud(g);

            if (paused) {
                g.setColor(new Color(70, 40, 18, 228));
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(new Color(245, 225, 190));
                g.setFont(new Font("SansSerif", Font.BOLD, 56));
                String t = "PAUSED";
                g.drawString(t, (getWidth() - g.getFontMetrics().stringWidth(t)) / 2, getHeight() / 2 - 6);
                g.setFont(new Font("SansSerif", Font.PLAIN, 18));
                String t2 = "Click Resume to continue";
                g.drawString(t2, (getWidth() - g.getFontMetrics().stringWidth(t2)) / 2, getHeight() / 2 + 30);
            }
            g.dispose();
        }

        /** A soil circle holding small goats (goats still in hand, or goats already captured). */
        void drawPit(Graphics2D g, double px, double py, int count, String label) {
            double R = 1.75 * s;
            g.setColor(new Color(0, 0, 0, 45));
            g.fill(new Ellipse2D.Double(px - R + 4, py - R + 5, 2 * R, 2 * R));
            g.setPaint(new RadialGradientPaint(new Point2D.Double(px, py), (float) R,
                    new float[]{0f, 0.8f, 1f},
                    new Color[]{new Color(112, 74, 40), new Color(138, 94, 54), new Color(170, 124, 78)}));
            g.fill(new Ellipse2D.Double(px - R, py - R, 2 * R, 2 * R));
            g.setColor(new Color(105, 68, 34));
            g.setStroke(new BasicStroke(4f));
            g.draw(new Ellipse2D.Double(px - R, py - R, 2 * R, 2 * R));
            for (int i = 0; i < count && i < 20; i++) {
                int row = i / 5, col = i % 5;
                drawGoat(g, px + (col - 2) * 0.6 * s - 0.05 * s, py + (row - 1.5) * 0.6 * s, 0.24 * s);
            }
            g.setFont(new Font("SansSerif", Font.BOLD, 14));
            g.setColor(TEXT_DARK);
            int w = g.getFontMetrics().stringWidth(label);
            g.drawString(label, (float) (px - w / 2.0), (float) (py + R + 24));
        }

        void paintHud(Graphics2D g) {
            int W = getWidth();
            drawCard(g, 18, 14, TIGER);
            drawCard(g, W - 238, 14, GOAT);
            String main, sub;
            if (over) {
                main = names[winner] + " wins!";
                sub = winReason;
            } else {
                main = "Turn: " + names[turn] + (turn == TIGER ? " (Bagh)" : " (Chhagol)");
                if (paused) sub = "Game paused";
                else if (aiBusy || isComputerTurn()) sub = "Computer is thinking...";
                else if (turn == GOAT && st.placed < GOATS_TOTAL)
                    sub = "Click an empty point to place a goat (" + (GOATS_TOTAL - st.placed) + " left).";
                else if (chaining) sub = "Eat again (red spot) or click End Turn.";
                else if (selected >= 0) sub = "Click a highlighted spot to move" + (turn == TIGER ? " or jump." : ".");
                else sub = turn == TIGER ? "Select one of your tigers." : "Select one of your goats to move.";
            }
            g.setFont(new Font("SansSerif", Font.BOLD, 24));
            g.setColor(TEXT_DARK);
            g.drawString(main, (W - g.getFontMetrics().stringWidth(main)) / 2, 34);
            g.setFont(new Font("SansSerif", Font.PLAIN, 14));
            g.drawString(sub, (W - g.getFontMetrics().stringWidth(sub)) / 2, 56);
            String clk;
            if (totalSeconds <= 0) clk = "No time limit";
            else clk = String.format("Time left: %02d:%02d:%02d", remaining / 3600, (remaining / 60) % 60, remaining % 60);
            g.setFont(new Font("SansSerif", Font.BOLD, 18));
            g.setColor(totalSeconds > 0 && remaining <= 30 && !over ? new Color(170, 30, 20) : TEXT_DARK);
            g.drawString(clk, (W - g.getFontMetrics().stringWidth(clk)) / 2, 82);
        }

        void drawCard(Graphics2D g, int x, int y, int side) {
            g.setColor(new Color(220, 176, 122, 215));
            g.fill(new RoundRectangle2D.Double(x, y, 220, 52, 18, 18));
            g.setColor(new Color(132, 90, 48));
            g.setStroke(new BasicStroke(2f));
            g.draw(new RoundRectangle2D.Double(x, y, 220, 52, 18, 18));
            if (side == TIGER) drawTiger(g, x + 28, y + 28, 15);
            else drawGoat(g, x + 24, y + 28, 13);
            g.setColor(TEXT_DARK);
            g.setFont(new Font("SansSerif", Font.BOLD, 14));
            String nm = names[side];
            if (nm.length() > 16) nm = nm.substring(0, 15) + "...";
            g.drawString(nm, x + 54, y + 22);
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            String info = side == TIGER
                    ? "Bagh - 3 tigers, ate " + st.captured + "/" + winCount
                    : "Chhagol - " + BRules.goatsOnBoard(st) + " on, " + (GOATS_TOTAL - st.placed) + " in hand";
            g.drawString(info, x + 54, y + 40);
        }
    }

    // =====================================================================
    //  HOME SCREEN
    // =====================================================================
    static final class HomePanel extends JPanel {
        final JRadioButton rbG16 = new JRadioButton("16 Guti (Sholo Guti)", true);
        final JRadioButton rbBagh = new JRadioButton("Bagh-Chhagol (Tiger & Goat)");
        final JRadioButton rbCpu = new JRadioButton("Play with Computer", true);
        final JRadioButton rbTwo = new JRadioButton("Two Players");
        final JTextField tf1 = new JTextField("Player 1", 14);
        final JTextField tf2 = new JTextField("Player 2", 14);
        final DefaultComboBoxModel<String> mG16 = new DefaultComboBoxModel<>(new String[]{"First player (moves first)", "Second player"});
        final DefaultComboBoxModel<String> mBagh = new DefaultComboBoxModel<>(new String[]{"Bagh (Tiger)", "Chhagol (Goat)"});
        final JComboBox<String> side = new JComboBox<>(mG16);
        final JComboBox<String> kindMine = new JComboBox<>(KIND_NAMES);
        final JComboBox<String> kindOpp = new JComboBox<>(KIND_NAMES);
        final JComboBox<String> diff = new JComboBox<>(new String[]{"Easy", "Medium", "Hard"});
        final JComboBox<String> wins = new JComboBox<>(new String[]{"Capture 8 goats", "Capture all 16 goats"});
        final JCheckBox cbTime = new JCheckBox("Time limit (whole game)");
        final JSpinner spH = new JSpinner(new SpinnerNumberModel(0, 0, 23, 1));
        final JSpinner spM = new JSpinner(new SpinnerNumberModel(10, 0, 59, 1));
        final JSpinner spS = new JSpinner(new SpinnerNumberModel(0, 0, 59, 1));
        final JCheckBox cbSound = new JCheckBox("Sound ON", true);
        final JCheckBox cbHint = new JCheckBox("Show hint (suggested move)");
        final JLabel title = new JLabel("<html><div style='text-align:center'>SHOLO GUTI AND BAGH-CHHAGOL<br>VILLAGE GAME</div></html>", SwingConstants.CENTER);
        final JLabel sub = new JLabel("16 Guti", SwingConstants.CENTER);
        final JLabel l1 = new JLabel("Your name");
        final JLabel l2 = new JLabel("Player 2 name");
        final JLabel lSide = new JLabel("Your guti");
        final JLabel lDiff = new JLabel("Difficulty");
        final JLabel lWin = new JLabel("Tigers win by");
        final JLabel lStyle = new JLabel("Your guti type");
        final JLabel lStyle2 = new JLabel("Opponent's guti type");

        HomePanel(Main app) {
            setLayout(new GridBagLayout());
            final JPanel plate = new JPanel(new GridBagLayout()) {
                @Override protected void paintComponent(Graphics g0) {
                    Graphics2D g = (Graphics2D) g0.create();
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setColor(new Color(214, 170, 118, 240));
                    g.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 40, 40));
                    g.setColor(new Color(132, 90, 48));
                    g.setStroke(new BasicStroke(5f));
                    g.draw(new RoundRectangle2D.Double(2, 2, getWidth() - 4, getHeight() - 4, 40, 40));
                    g.dispose();
                }
            };
            plate.setOpaque(false);
            plate.setBorder(BorderFactory.createEmptyBorder(18, 40, 18, 40));

            title.setFont(new Font("Serif", Font.BOLD, 30));
            title.setForeground(TEXT_DARK);
            sub.setFont(new Font("SansSerif", Font.ITALIC, 15));
            sub.setForeground(TEXT_DARK);

            ButtonGroup bgGame = new ButtonGroup();
            bgGame.add(rbG16); bgGame.add(rbBagh);
            ButtonGroup bg = new ButtonGroup();
            bg.add(rbCpu); bg.add(rbTwo);
            for (AbstractButton b : new AbstractButton[]{rbG16, rbBagh, rbCpu, rbTwo, cbTime, cbSound, cbHint}) {
                b.setOpaque(false);
                b.setFont(new Font("SansSerif", Font.BOLD, 14));
                b.setForeground(TEXT_DARK);
                b.setFocusPainted(false);
            }
            for (JLabel l : new JLabel[]{l1, l2, lSide, lDiff, lWin, lStyle, lStyle2}) {
                l.setFont(new Font("SansSerif", Font.BOLD, 14));
                l.setForeground(TEXT_DARK);
            }

            kindOpp.setSelectedIndex(1);
            JPanel gameRow = flow(rbG16, rbBagh);
            JPanel modeRow = flow(rbCpu, rbTwo);
            final JPanel timeRow = flow(cbTime, spH, lbl("h"), spM, lbl("m"), spS, lbl("s"));

            Runnable refresh = () -> {
                boolean bagh = rbBagh.isSelected();
                boolean cpu = rbCpu.isSelected(), two = rbTwo.isSelected();
                sub.setText(bagh ? "Tiger and Goat  -  3 Bagh vs 16 Chhagol" : "16 Guti");
                DefaultComboBoxModel<String> want = bagh ? mBagh : mG16;
                if (side.getModel() != want) side.setModel(want);
                l1.setText(two ? "Player 1 name" : "Your name");
                l2.setVisible(two);
                tf2.setVisible(two);
                lSide.setText(two ? "Player 1 side" : "Your side");
                lStyle.setText(two ? "Player 1 guti type" : "Your guti type");
                lStyle2.setText(two ? "Player 2 guti type" : "Opponent's guti type");
                lDiff.setVisible(cpu);
                diff.setVisible(cpu);
                lWin.setVisible(bagh);
                wins.setVisible(bagh);
                lStyle.setVisible(!bagh);
                kindMine.setVisible(!bagh);
                lStyle2.setVisible(!bagh);
                kindOpp.setVisible(!bagh);
                plate.revalidate();
                plate.repaint();
            };
            rbG16.addActionListener(e -> refresh.run());
            rbBagh.addActionListener(e -> refresh.run());
            rbCpu.addActionListener(e -> refresh.run());
            rbTwo.addActionListener(e -> refresh.run());
            cbTime.addActionListener(e -> {
                boolean on = cbTime.isSelected();
                spH.setEnabled(on); spM.setEnabled(on); spS.setEnabled(on);
            });
            spH.setEnabled(false); spM.setEnabled(false); spS.setEnabled(false);
            cbSound.addActionListener(e -> {
                Sound.on = cbSound.isSelected();
                cbSound.setText(Sound.on ? "Sound ON" : "Sound OFF");
            });

            JButton start = styledButton("START", new Color(88, 140, 52));
            JButton how = styledButton("How to Play", new Color(150, 100, 52));
            JButton exit = styledButton("Exit", new Color(150, 70, 50));
            start.addActionListener(e -> app.startGame(this));
            how.addActionListener(e -> showRules(app, rbBagh.isSelected()));
            exit.addActionListener(e -> System.exit(0));
            JPanel btnRow = flow(start, how, exit);

            GridBagConstraints c = new GridBagConstraints();
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(3, 0, 3, 0);
            c.gridx = 0; c.gridwidth = 2;
            c.gridy = 0; plate.add(title, c);
            c.gridy = 1; c.insets = new Insets(0, 0, 8, 0); plate.add(sub, c);
            c.insets = new Insets(3, 0, 3, 0);
            c.gridy = 2; plate.add(gameRow, c);
            c.gridy = 3; plate.add(modeRow, c);
            c.gridwidth = 1;
            c.gridy = 6; c.gridx = 0; plate.add(l1, c); c.gridx = 1; plate.add(tf1, c);
            c.gridy = 7; c.gridx = 0; plate.add(l2, c); c.gridx = 1; plate.add(tf2, c);
            c.gridy = 8; c.gridx = 0; plate.add(lSide, c); c.gridx = 1; plate.add(side, c);
            c.gridy = 9; c.gridx = 0; plate.add(lDiff, c); c.gridx = 1; plate.add(diff, c);
            c.gridy = 10; c.gridx = 0; plate.add(lWin, c); c.gridx = 1; plate.add(wins, c);
            c.gridy = 11; c.gridx = 0; plate.add(lStyle, c); c.gridx = 1; plate.add(kindMine, c);
            c.gridy = 12; c.gridx = 0; plate.add(lStyle2, c); c.gridx = 1; plate.add(kindOpp, c);
            c.gridwidth = 2; c.gridx = 0;
            c.gridy = 13; plate.add(timeRow, c);
            c.gridy = 14; plate.add(flow(cbSound, cbHint), c);
            c.gridy = 15; c.insets = new Insets(10, 0, 3, 0); plate.add(btnRow, c);
            add(plate);
            refresh.run();
        }

        static JPanel flow(Component... comps) {
            JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 2));
            p.setOpaque(false);
            for (Component c : comps) p.add(c);
            return p;
        }

        static JLabel lbl(String t) {
            JLabel l = new JLabel(t);
            l.setFont(new Font("SansSerif", Font.BOLD, 14));
            l.setForeground(TEXT_DARK);
            return l;
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            paintSoil((Graphics2D) g, getWidth(), getHeight());
        }
    }

    static JButton styledButton(String text, Color bg) {
        JButton b = new JButton(text);
        styleBar(b, bg);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    static void styleBar(JButton b, Color bg) {
        b.setFont(new Font("SansSerif", Font.BOLD, 15));
        b.setBackground(bg);
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setOpaque(true);
        b.setBorder(BorderFactory.createEmptyBorder(9, 20, 9, 20));
    }

    static JPanel newBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 8)) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(new Color(150, 106, 62));
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        bar.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        return bar;
    }

    static final String RULES_16 =
            "SHOLO GUTI (16 GUTI) - HOW TO PLAY\n\n"
                    + "GOAL\n"
                    + "Capture all of your opponent's guti, or block them so that they cannot move.\n\n"
                    + "THE BOARD AND THE GUTI\n"
                    + "- The board has 37 points joined by lines (a big grid with a triangle on the top and the bottom).\n"
                    + "- Each player has 16 guti. On the home page you choose the guti type of each player:\n"
                    + "  Small betel nut, Stick, Brickbats or Small stone (the two players must use different types).\n"
                    + "- At the start the first player's 16 guti stand at the bottom and the second player's 16 guti stand\n"
                    + "  at the top. The middle row is empty.\n"
                    + "- The first player (bottom side) moves first. Then you take turns. In one turn you move ONE guti.\n\n"
                    + "HOW TO MOVE\n"
                    + "1. Click one of your guti. A yellow ring appears around it.\n"
                    + "2. Yellow dots show where it can go: the empty points next to it, along a line.\n"
                    + "3. Click a yellow dot. The guti slides there and your turn ends.\n"
                    + "   (To change your mind, click another guti of yours.)\n\n"
                    + "HOW TO CAPTURE (JUMP)\n"
                    + "1. If an enemy guti is next to your guti ON A LINE, and the point right behind it\n"
                    + "   (in the same straight line) is empty, you can jump over it.\n"
                    + "2. Red dots show the jumps you can make. Click a red dot.\n"
                    + "3. The guti you jumped over is removed and goes into the soil circle.\n"
                    + "- Capturing is NOT compulsory. You may make a normal move instead.\n"
                    + "- You can only jump in a straight line along a drawn line, and only over an ENEMY guti.\n\n"
                    + "MULTI-JUMP\n"
                    + "After a capture, if the SAME guti can capture again, red dots appear again.\n"
                    + "Click a red dot to jump again, or click the 'End Turn' button (or your guti) to stop.\n\n"
                    + "HOW TO WIN\n"
                    + "- Your opponent has no guti left, or\n"
                    + "- your opponent is stuck: none of their guti can move.\n"
                    + "With a time limit: when the time is up, the player with MORE guti wins (equal = draw).\n\n"
                    + "BUTTONS\n"
                    + "End Turn - stop a multi-jump.     Pause / Resume - freeze the game and the clock.\n"
                    + "Hint - shows a blue arrow with a good move.     New Game - start again.\n"
                    + "Rules - show this help.     Exit - go back to the home page.\n\n"
                    + "PLAY MODES\n"
                    + "Play with Computer: Easy = random moves, Medium and Hard think ahead.\n"
                    + "Two Players: take turns on the same computer.";

    static final String RULES_BAGH =
            "BAGH-CHHAGOL (TIGER AND GOAT) - HOW TO PLAY\n\n"
                    + "PLAYERS\n"
                    + "One player is the BAGH (tiger) and has 3 tigers.\n"
                    + "The other player is the CHHAGOL (goat) and has 16 goats.\n\n"
                    + "GOAL\n"
                    + "- Goats win by TRAPPING all 3 tigers so that none of them can move. If the tiger player has no\n"
                    + "  move at all on their turn (no step and no jump), the goats win.\n"
                    + "- Tigers win by EATING (capturing) 8 goats, or all 16 goats (you choose on the home page),\n"
                    + "  or when the goats are stuck and cannot move.\n\n"
                    + "THE BOARD AND THE START\n"
                    + "- The game uses the same 37-point board as Sholo Guti. Pieces stand on the points where lines meet.\n"
                    + "- The 3 tigers start on the middle row of the top triangle.\n"
                    + "- All 16 goats start OUTSIDE the board. THE GOAT PLAYER MOVES FIRST.\n\n"
                    + "PART 1 - PLACING THE GOATS\n"
                    + "- On your turn click any EMPTY point to put one goat there (faint yellow dots show the free points).\n"
                    + "- Only one goat is placed per turn.\n"
                    + "- Goats cannot move until all 16 goats are on the board.\n\n"
                    + "PART 2 - MOVING THE GOATS\n"
                    + "- When all 16 are placed, click a goat, then click a yellow dot to slide it ONE step along a line\n"
                    + "  to an empty neighbouring point.\n"
                    + "- Goats never jump and never capture.\n\n"
                    + "THE TIGER'S TURN\n"
                    + "1. Click one of your tigers.\n"
                    + "2. Yellow dots = a step to an empty neighbouring point.\n"
                    + "3. Red dots = EAT a goat. If a goat is next to the tiger on a line, and the point right behind the\n"
                    + "   goat (in the same straight line) is empty, the tiger jumps over it. The goat is removed and the\n"
                    + "   tiger lands on the empty point.\n"
                    + "- MULTI-JUMP: after eating a goat, if the SAME tiger can eat another goat, a red dot appears again.\n"
                    + "  Click it to eat again and again in the same turn. To stop, click the 'End Turn' button\n"
                    + "  (or click the tiger). Every jump removes one goat.\n"
                    + "- Eating is not compulsory. Tigers may eat from the very first move.\n"
                    + "- A tiger cannot jump over another tiger. Eaten goats never come back.\n\n"
                    + "TIPS\n"
                    + "Goats should stay close together so that no empty point is left behind them.\n"
                    + "Tigers try to separate the goats and jump over the lonely ones.\n\n"
                    + "TIME LIMIT (optional)\n"
                    + "When the time is up, the tigers win if they have eaten 8 or more goats, otherwise the goats win.\n\n"
                    + "BUTTONS\n"
                    + "End Turn - stop a tiger's multi-jump.     Pause / Resume - freeze the game and the clock.\n"
                    + "Hint - shows a blue move suggestion.\n"
                    + "New Game - start again.     Rules - show this help.     Exit - go back to the home page.\n\n"
                    + "PLAY MODES\n"
                    + "Play with Computer (Easy / Medium / Hard), or Two Players on the same computer.";

    static void showRules(Component parent, boolean bagh) {
        JTextArea ta = new JTextArea(bagh ? RULES_BAGH : RULES_16);
        ta.setEditable(false);
        ta.setFont(new Font("SansSerif", Font.PLAIN, 14));
        ta.setBackground(new Color(240, 220, 185));
        ta.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        ta.setCaretPosition(0);
        JScrollPane sp = new JScrollPane(ta);
        sp.setPreferredSize(new Dimension(700, 480));
        JOptionPane.showMessageDialog(parent, sp, bagh ? "How to Play - Bagh-Chhagol" : "How to Play - 16 Guti",
                JOptionPane.PLAIN_MESSAGE);
    }

    // =====================================================================
    //  MAIN WINDOW
    // =====================================================================
    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);
    private final BoardPanel board = new BoardPanel(this::goHome);
    private final BaghPanel baghBoard = new BaghPanel(this::goHome);

    Main() {
        super("Sholo Guti And Bagh-Chhagol Village Game");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        HomePanel home = new HomePanel(this);

        // ---- 16 Guti screen ----
        JPanel game = new JPanel(new BorderLayout());
        JPanel bar = newBar();
        styleBar(board.endTurnBtn, new Color(88, 140, 52));
        board.endTurnBtn.setEnabled(false);
        board.endTurnBtn.addActionListener(e -> { if (board.chaining) board.endTurn(); });
        styleBar(board.pauseBtn, new Color(60, 110, 150));
        board.pauseBtn.addActionListener(e -> board.setPaused(!board.paused));
        styleBar(board.newBtn, new Color(150, 100, 52));
        board.newBtn.addActionListener(e -> board.restart());
        JButton rules = styledButton("Rules", new Color(120, 90, 60));
        rules.addActionListener(e -> showRules(this, false));
        JButton exit = styledButton("Exit", new Color(150, 70, 50));
        exit.addActionListener(e -> board.exitGame());
        styleBar(board.hintBtn, new Color(40, 100, 190));
        board.hintBtn.addActionListener(e -> board.setHint(!board.hintOn));
        bar.add(board.endTurnBtn);
        bar.add(board.pauseBtn);
        bar.add(board.hintBtn);
        bar.add(board.newBtn);
        bar.add(rules);
        bar.add(exit);
        game.add(board, BorderLayout.CENTER);
        game.add(bar, BorderLayout.NORTH);

        // ---- Bagh-Chhagol screen ----
        JPanel game2 = new JPanel(new BorderLayout());
        JPanel bar2 = newBar();
        styleBar(baghBoard.endTurnBtn, new Color(88, 140, 52));
        baghBoard.endTurnBtn.setEnabled(false);
        baghBoard.endTurnBtn.addActionListener(e -> { if (baghBoard.chaining) baghBoard.endTurn(); });
        styleBar(baghBoard.pauseBtn, new Color(60, 110, 150));
        baghBoard.pauseBtn.addActionListener(e -> baghBoard.setPaused(!baghBoard.paused));
        styleBar(baghBoard.hintBtn, new Color(40, 100, 190));
        baghBoard.hintBtn.addActionListener(e -> baghBoard.setHint(!baghBoard.hintOn));
        styleBar(baghBoard.newBtn, new Color(150, 100, 52));
        baghBoard.newBtn.addActionListener(e -> baghBoard.restart());
        JButton rules2 = styledButton("Rules", new Color(120, 90, 60));
        rules2.addActionListener(e -> showRules(this, true));
        JButton exit2 = styledButton("Exit", new Color(150, 70, 50));
        exit2.addActionListener(e -> baghBoard.exitGame());
        bar2.add(baghBoard.endTurnBtn);
        bar2.add(baghBoard.pauseBtn);
        bar2.add(baghBoard.hintBtn);
        bar2.add(baghBoard.newBtn);
        bar2.add(rules2);
        bar2.add(exit2);
        game2.add(baghBoard, BorderLayout.CENTER);
        game2.add(bar2, BorderLayout.NORTH);

        JScrollPane hs = new JScrollPane(home, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        hs.setBorder(null);
        hs.getVerticalScrollBar().setUnitIncrement(16);
        root.add(hs, "home");
        root.add(game, "game");
        root.add(game2, "bagh");
        setContentPane(root);
        Dimension scr = Toolkit.getDefaultToolkit().getScreenSize();
        setMinimumSize(new Dimension(640, 520));
        setSize(Math.min(1100, scr.width - 40), Math.min(840, scr.height - 80));
        setLocationRelativeTo(null);
    }

    void goHome() { cards.show(root, "home"); }

    void startGame(HomePanel h) {
        try { h.spH.commitEdit(); h.spM.commitEdit(); h.spS.commitEdit(); } catch (java.text.ParseException ignored) { }
        Sound.on = h.cbSound.isSelected();
        boolean isBagh = h.rbBagh.isSelected();
        board.hintOn = h.cbHint.isSelected();
        board.hintBtn.setText(board.hintOn ? "Hint: ON" : "Hint: OFF");
        baghBoard.hintOn = h.cbHint.isSelected();
        baghBoard.hintBtn.setText(baghBoard.hintOn ? "Hint: ON" : "Hint: OFF");
        int secs = 0;
        if (h.cbTime.isSelected()) {
            secs = (Integer) h.spH.getValue() * 3600 + (Integer) h.spM.getValue() * 60 + (Integer) h.spS.getValue();
            if (secs <= 0) {
                JOptionPane.showMessageDialog(this, "Set a time greater than 0, or turn off the time limit.");
                return;
            }
        }
        String n1 = h.tf1.getText().trim().replace('\n', ' ');
        String n2 = h.tf2.getText().trim().replace('\n', ' ');
        int pick = h.side.getSelectedIndex() + 1;               // 1 = Green / Bagh(Tiger), 2 = Brown / Chhagol(Goat)
        int win = h.wins.getSelectedIndex() == 0 ? 8 : GOATS_TOTAL;
        if (!isBagh) {
            int mine = h.kindMine.getSelectedIndex(), opp = h.kindOpp.getSelectedIndex();
            if (mine == opp) {
                JOptionPane.showMessageDialog(this, "Please choose two different guti types for the two players.");
                return;
            }
            kind[pick] = mine;
            kind[3 - pick] = opp;
        }
        if (h.rbCpu.isSelected()) {
            if (n1.isEmpty()) n1 = "You";
            String first = pick == 1 ? n1 : "Computer";
            String second = pick == 2 ? n1 : "Computer";
            if (!isBagh) {
                board.start(first, second, pick, h.diff.getSelectedIndex(), secs);
                cards.show(root, "game");
            } else {
                baghBoard.start(first, second, pick, h.diff.getSelectedIndex(), secs, win);
                cards.show(root, "bagh");
            }
        } else {
            if (n1.isEmpty()) n1 = "Player 1";
            if (n2.isEmpty()) n2 = "Player 2";
            String first = pick == 1 ? n1 : n2;
            String second = pick == 1 ? n2 : n1;
            if (!isBagh) {
                board.start(first, second, 0, 0, secs);
                cards.show(root, "game");
            } else {
                baghBoard.start(first, second, 0, 0, secs, win);
                cards.show(root, "bagh");
            }
        }
    }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); } catch (Exception ignored) { }
        SwingUtilities.invokeLater(() -> new Main().setVisible(true));
    }
}
