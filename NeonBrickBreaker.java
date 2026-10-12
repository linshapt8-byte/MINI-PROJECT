import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import javax.sound.sampled.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

/**
 * NEON BRICK BREAKER - Java Swing mini project
 * Concepts: classes/objects, inheritance, polymorphism, encapsulation,
 * ArrayList, loops, methods, exception handling, file handling, Swing GUI.
 */
public class NeonBrickBreaker {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Neon Brick Breaker");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);
            frame.add(new GamePanel());
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}

/* ---------- Sound effects: tones generated in code, no audio files needed ---------- */
class SoundFX {
    private static void tone(double freq, int ms) {      // plays one tone and waits until it ends
        try {
            float rate = 44100f;
            byte[] buf = new byte[(int) (rate * ms / 1000)];
            for (int i = 0; i < buf.length; i++) {
                double fade = 1.0 - (double) i / buf.length;   // smooth fade-out
                buf[i] = (byte) (Math.sin(2 * Math.PI * freq * i / rate) * 90 * fade);
            }
            AudioFormat af = new AudioFormat(rate, 8, 1, true, false);
            try (SourceDataLine line = AudioSystem.getSourceDataLine(af)) {
                line.open(af, buf.length);
                line.start();
                line.write(buf, 0, buf.length);
                line.drain();
            }
        } catch (Exception e) { /* no sound device: ignore */ }
    }

    static void play(double freq, int ms) {
        new Thread(() -> tone(freq, ms)).start();         // separate thread so the game never lags
    }

    /** Party-popper "pop" followed by a happy victory melody. */
    static void fanfare() {
        new Thread(() -> {
            tone(150, 70);                                // the pop
            double[] notes = {523, 659, 784, 1046};       // C E G high-C
            int[] lengths = {120, 120, 120, 450};
            for (int i = 0; i < notes.length; i++) tone(notes[i], lengths[i]);
        }).start();
    }
}

class Confetti {
    double x, y, dx, dy, rot, vr;
    int w, h;
    Color c;
    Confetti(double x, double y, double dx, double dy, Color c, Random r) {
        this.x = x; this.y = y; this.dx = dx; this.dy = dy; this.c = c;
        w = 6 + r.nextInt(6); h = 4 + r.nextInt(4);
        rot = r.nextDouble() * 6; vr = (r.nextDouble() - 0.5) * 0.4;
    }
    void update() { x += dx; y += dy; dy += 0.15; dx *= 0.99; rot += vr; }
    void draw(Graphics2D g) {
        AffineTransform old = g.getTransform();
        g.translate(x, y); g.rotate(rot);
        g.setColor(c);
        g.fillRect(-w / 2, -h / 2, w, h);
        g.setTransform(old);
    }
}

/* ---------- Base class (Inheritance) ---------- */
abstract class GameObject {
    protected double x, y, w, h;
    GameObject(double x, double y, double w, double h) {
        this.x = x; this.y = y; this.w = w; this.h = h;
    }
    abstract void draw(Graphics2D g);
}

class Paddle extends GameObject {
    Paddle(double x, double y) { super(x, y, 110, 16); }
    void draw(Graphics2D g) {
        g.setColor(new Color(0, 255, 255, 60));
        g.fill(new RoundRectangle2D.Double(x - 4, y - 4, w + 8, h + 8, 24, 24));
        g.setPaint(new GradientPaint((float) x, (float) y, new Color(0, 255, 230),
                (float) x, (float) (y + h), new Color(0, 120, 255)));
        g.fill(new RoundRectangle2D.Double(x, y, w, h, 16, 16));
    }
}

class Ball extends GameObject {
    double dx, dy, r = 8;
    LinkedList<Point2D.Double> trail = new LinkedList<>();
    Ball() { super(0, 0, 16, 16); }
    void move() {
        trail.addFirst(new Point2D.Double(x, y));
        if (trail.size() > 12) trail.removeLast();
        x += dx; y += dy;
    }
    void draw(Graphics2D g) {
        int i = 0;
        for (Point2D.Double p : trail) {                       // glowing tail
            int a = Math.max(0, 110 - i * 9);
            double s = r * (1 - i * 0.05);
            g.setColor(new Color(255, 240, 120, a));
            g.fill(new Ellipse2D.Double(p.x - s, p.y - s, s * 2, s * 2));
            i++;
        }
        g.setColor(new Color(255, 255, 255, 70));
        g.fill(new Ellipse2D.Double(x - r - 5, y - r - 5, (r + 5) * 2, (r + 5) * 2));
        g.setPaint(new RadialGradientPaint((float) x, (float) y, (float) r,
                new float[]{0f, 1f}, new Color[]{Color.WHITE, new Color(255, 200, 0)}));
        g.fill(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
    }
}

class Brick extends GameObject {
    protected int hits = 1;
    protected Color color;
    Brick(double x, double y, double w, double h, Color c) { super(x, y, w, h); color = c; }
    boolean hit() { hits--; return hits <= 0; }     // true = destroyed
    int points() { return 10; }
    void draw(Graphics2D g) {
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 60));
        g.fill(new RoundRectangle2D.Double(x - 2, y - 2, w + 4, h + 4, 12, 12));
        g.setPaint(new GradientPaint((float) x, (float) y, color.brighter(),
                (float) x, (float) (y + h), color.darker()));
        g.fill(new RoundRectangle2D.Double(x, y, w, h, 10, 10));
        g.setColor(new Color(255, 255, 255, 90));
        g.fill(new RoundRectangle2D.Double(x + 3, y + 2, w - 6, h / 3, 8, 8));
    }
}

/* Polymorphism: tougher brick needs 2 hits and gives more points */
class StrongBrick extends Brick {
    StrongBrick(double x, double y, double w, double h, Color c) { super(x, y, w, h, c); hits = 2; }
    @Override int points() { return 25; }
    @Override void draw(Graphics2D g) {
        super.draw(g);
        g.setStroke(new BasicStroke(hits == 2 ? 3f : 1f));
        g.setColor(hits == 2 ? Color.WHITE : new Color(255, 255, 255, 120));
        g.draw(new RoundRectangle2D.Double(x + 1, y + 1, w - 2, h - 2, 10, 10));
        g.setStroke(new BasicStroke(1f));
    }
}

class Particle {
    double x, y, dx, dy, life = 1.0;
    Color c;
    Particle(double x, double y, Color c, Random rnd) {
        this.x = x; this.y = y; this.c = c;
        double a = rnd.nextDouble() * Math.PI * 2, s = 1 + rnd.nextDouble() * 4;
        dx = Math.cos(a) * s; dy = Math.sin(a) * s;
    }
    void update() { x += dx; y += dy; dy += 0.12; life -= 0.03; }
    void draw(Graphics2D g) {
        g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) (Math.max(0, life) * 255)));
        g.fill(new Ellipse2D.Double(x, y, 5, 5));
    }
}

/* ---------- Main game panel ---------- */
class GamePanel extends JPanel implements ActionListener, KeyListener, MouseMotionListener, MouseListener {
    static final int W = 800, H = 600, MAX_LEVEL = 10;      // change this number to set how many levels the game has
    enum State { MENU, INSTRUCTIONS, PLAYING, PAUSED, LEVEL_CLEAR, GAME_OVER, WIN }
    static final Color[] RAINBOW = {
            new Color(255, 70, 110), new Color(255, 140, 40), new Color(255, 220, 50),
            new Color(80, 235, 120), new Color(50, 200, 255), new Color(160, 100, 255),
            new Color(255, 90, 220), new Color(120, 255, 220)
    };

    State state = State.MENU;
    Paddle paddle = new Paddle(W / 2 - 55, H - 50);
    Ball ball = new Ball();
    List<Brick> bricks = new ArrayList<>();
    List<Particle> particles = new ArrayList<>();
    List<Confetti> confetti = new ArrayList<>();
    double[] starX = new double[70], starY = new double[70], starS = new double[70];
    Random rnd = new Random();
    boolean left, right, launched;
    int score, lives, level, highScore, bannerTimer, celebrateTimer;
    final File scoreFile = new File("highscore.txt");

    GamePanel() {
        setPreferredSize(new Dimension(W, H));
        setFocusable(true);
        addKeyListener(this); addMouseMotionListener(this); addMouseListener(this);
        for (int i = 0; i < starX.length; i++) {
            starX[i] = rnd.nextInt(W); starY[i] = rnd.nextInt(H); starS[i] = 0.2 + rnd.nextDouble();
        }
        loadHighScore();
        new Timer(16, this).start();
    }

    /* ----- game setup ----- */
    void newGame() {
        score = 0; lives = 3; level = 1;
        buildLevel(); resetBall();
        state = State.PLAYING;
    }

    void buildLevel() {
        bricks.clear();
        int rows = Math.min(3 + level, 8), cols = 10;
        double bw = 70, bh = 24, gap = 6, startX = (W - (cols * (bw + gap) - gap)) / 2, startY = 70;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                double bx = startX + c * (bw + gap), by = startY + r * (bh + gap);
                Color col = RAINBOW[r % RAINBOW.length];
                boolean strong = level > 1 && (r + c * level) % 5 == 0;
                bricks.add(strong ? new StrongBrick(bx, by, bw, bh, col) : new Brick(bx, by, bw, bh, col));
            }
        }
        bannerTimer = 90;
    }

    void resetBall() {
        launched = false;
        ball.trail.clear();
        double speed = Math.min(5 + level * 0.6, 9);
        ball.dx = speed * 0.5; ball.dy = -speed;
    }

    /* ----- file handling ----- */
    void loadHighScore() {
        try (BufferedReader br = new BufferedReader(new FileReader(scoreFile))) {
            highScore = Integer.parseInt(br.readLine().trim());
        } catch (IOException | RuntimeException e) { highScore = 0; }
    }
    void saveHighScore() {
        if (score > highScore) {
            highScore = score;
            try (PrintWriter pw = new PrintWriter(new FileWriter(scoreFile))) {
                pw.println(highScore);
            } catch (IOException e) { System.out.println("Could not save score: " + e.getMessage()); }
        }
    }

    /* ----- game loop ----- */
    @Override public void actionPerformed(ActionEvent e) {
        for (int i = 0; i < starX.length; i++) {                // moving star field
            starY[i] += starS[i];
            if (starY[i] > H) { starY[i] = 0; starX[i] = rnd.nextInt(W); }
        }
        if (state == State.PLAYING) update();
        else if (state == State.LEVEL_CLEAR || state == State.WIN) updateCelebration();
        repaint();
    }

    void update() {
        if (left) paddle.x -= 9;
        if (right) paddle.x += 9;
        paddle.x = Math.max(0, Math.min(W - paddle.w, paddle.x));
        if (bannerTimer > 0) bannerTimer--;

        if (!launched) {
            ball.x = paddle.x + paddle.w / 2;
            ball.y = paddle.y - ball.r - 2;
        } else {
            ball.move();
            if (ball.x < ball.r) { ball.x = ball.r; ball.dx = Math.abs(ball.dx); SoundFX.play(220, 40); }
            if (ball.x > W - ball.r) { ball.x = W - ball.r; ball.dx = -Math.abs(ball.dx); SoundFX.play(220, 40); }
            if (ball.y < ball.r) { ball.y = ball.r; ball.dy = Math.abs(ball.dy); SoundFX.play(220, 40); }

            // paddle bounce: angle depends on where the ball lands
            if (ball.dy > 0 && ball.y + ball.r >= paddle.y && ball.y + ball.r <= paddle.y + paddle.h + 8
                    && ball.x >= paddle.x - 4 && ball.x <= paddle.x + paddle.w + 4) {
                double hitPos = (ball.x - (paddle.x + paddle.w / 2)) / (paddle.w / 2);
                double speed = Math.hypot(ball.dx, ball.dy);
                double angle = hitPos * Math.toRadians(60);
                ball.dx = speed * Math.sin(angle);
                ball.dy = -speed * Math.cos(angle);
                SoundFX.play(330, 60);                 // paddle bounce
            }
            checkBricks();
            if (ball.y > H + 20) loseLife();
        }
        for (Iterator<Particle> it = particles.iterator(); it.hasNext(); ) {
            Particle p = it.next(); p.update();
            if (p.life <= 0) it.remove();
        }
    }

    void checkBricks() {
        for (Iterator<Brick> it = bricks.iterator(); it.hasNext(); ) {
            Brick b = it.next();
            double nx = Math.max(b.x, Math.min(ball.x, b.x + b.w));
            double ny = Math.max(b.y, Math.min(ball.y, b.y + b.h));
            double dx = ball.x - nx, dy = ball.y - ny;
            if (dx * dx + dy * dy <= ball.r * ball.r) {
                if (Math.abs(dx) > Math.abs(dy)) ball.dx = -ball.dx; else ball.dy = -ball.dy;
                if (b.hit()) {
                    score += b.points();
                    for (int i = 0; i < 14; i++) particles.add(new Particle(b.x + b.w / 2, b.y + b.h / 2, b.color, rnd));
                    it.remove();
                    SoundFX.play(700 + rnd.nextInt(300), 90);   // brick BREAK sound
                } else {
                    SoundFX.play(450, 60);                      // hit but not broken
                }
                break;
            }
        }
        if (bricks.isEmpty()) {
            score += 50 * level;                               // level-clear bonus
            if (level >= MAX_LEVEL) { saveHighScore(); state = State.WIN; }
            else state = State.LEVEL_CLEAR;
            startCelebration();
        }
    }

    /* ----- celebration: party popper, fanfare and trophy ----- */
    void startCelebration() {
        celebrateTimer = 0;
        confetti.clear();
        for (int i = 0; i < 70; i++) {                         // two "popper" cannons in the bottom corners
            confetti.add(new Confetti(0, H, 2 + rnd.nextDouble() * 5, -(5 + rnd.nextDouble() * 6),
                    RAINBOW[rnd.nextInt(RAINBOW.length)], rnd));
            confetti.add(new Confetti(W, H, -(2 + rnd.nextDouble() * 5), -(5 + rnd.nextDouble() * 6),
                    RAINBOW[rnd.nextInt(RAINBOW.length)], rnd));
        }
        SoundFX.fanfare();
    }

    void updateCelebration() {
        celebrateTimer++;
        if (celebrateTimer < 100 && celebrateTimer % 2 == 0)   // confetti rain from the top
            for (int i = 0; i < 4; i++)
                confetti.add(new Confetti(rnd.nextInt(W), -10, rnd.nextDouble() * 2 - 1, 1 + rnd.nextDouble() * 2,
                        RAINBOW[rnd.nextInt(RAINBOW.length)], rnd));
        for (Iterator<Confetti> it = confetti.iterator(); it.hasNext(); ) {
            Confetti c = it.next(); c.update();
            if (c.y > H + 30) it.remove();
        }
        if (state == State.LEVEL_CLEAR && celebrateTimer > 260) nextLevel();   // auto-continue
    }

    void nextLevel() {
        confetti.clear();
        level++;
        buildLevel();
        resetBall();
        state = State.PLAYING;
    }

    void loseLife() {
        lives--;
        SoundFX.play(140, 350);                 // lost a life
        if (lives <= 0) { saveHighScore(); state = State.GAME_OVER; }
        else resetBall();
    }

    /* ----- drawing ----- */
    @Override protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setPaint(new GradientPaint(0, 0, new Color(10, 5, 40), 0, H, new Color(60, 10, 90)));
        g.fillRect(0, 0, W, H);
        g.setColor(new Color(255, 255, 255, 140));
        for (int i = 0; i < starX.length; i++) g.fill(new Ellipse2D.Double(starX[i], starY[i], starS[i] * 2.5, starS[i] * 2.5));

        switch (state) {
            case MENU: drawMenu(g); break;
            case INSTRUCTIONS: drawInstructions(g); break;
            default:
                drawGame(g);
                if (state == State.PAUSED) overlay(g, "PAUSED", "Press P to resume", new Color(80, 200, 255));
                if (state == State.GAME_OVER) overlay(g, "GAME OVER", "Score: " + score + "   |   R = Restart   M = Menu", new Color(255, 80, 100));
                if (state == State.LEVEL_CLEAR)
                    drawCelebration(g, "LEVEL " + level + " COMPLETE!", "Bonus +" + (50 * level) + "    Score: " + score,
                            "Press SPACE or click for next level");
                if (state == State.WIN)
                    drawCelebration(g, "YOU WIN!", "Final score: " + score, "R = Play again    M = Menu");
        }
    }

    void drawGame(Graphics2D g) {
        for (Brick b : bricks) b.draw(g);                 // polymorphic call
        paddle.draw(g); ball.draw(g);
        for (Particle p : particles) p.draw(g);
        g.setFont(new Font("SansSerif", Font.BOLD, 18));
        g.setColor(Color.WHITE);
        g.drawString("SCORE " + score, 20, 28);
        g.drawString("LEVEL " + level + "/" + MAX_LEVEL, W / 2 - 45, 28);
        g.drawString("BEST " + highScore, W - 120, 28);
        for (int i = 0; i < lives; i++) {                 // lives as glowing balls
            g.setColor(new Color(255, 90, 130));
            g.fill(new Ellipse2D.Double(20 + i * 24, 40, 16, 16));
        }
        if (!launched && state == State.PLAYING)
            centered(g, "Press SPACE or click to launch", H - 90, 18, new Color(255, 255, 255, 200));
        if (bannerTimer > 0 && state == State.PLAYING)
            centered(g, "LEVEL " + level, H / 2 + 40, 48, new Color(255, 255, 255, Math.min(255, bannerTimer * 6)));
    }

    void drawCelebration(Graphics2D g, String title, String line1, String line2) {
        g.setColor(new Color(0, 0, 0, 170));
        g.fillRect(0, 0, W, H);
        double bob = Math.sin(celebrateTimer * 0.1) * 6;               // floating trophy
        double scale = Math.min(1.0, 0.3 + celebrateTimer / 20.0);     // pop-in effect
        drawTrophy(g, W / 2, H / 2 - 70 + bob, scale);
        centered(g, title, H / 2 + 100, 46, new Color(255, 220, 60));
        centered(g, line1, H / 2 + 140, 22, Color.WHITE);
        centered(g, line2, H / 2 + 175, 18, new Color(180, 220, 255));
        for (Confetti c : confetti) c.draw(g);
    }

    /** Trophy drawn with Java2D shapes - no image file needed. */
    void drawTrophy(Graphics2D g, double cx, double cy, double s) {
        AffineTransform old = g.getTransform();
        g.translate(cx, cy);
        g.scale(s, s);
        g.setPaint(new RadialGradientPaint(0, 0, 130, new float[]{0f, 1f},
                new Color[]{new Color(255, 215, 0, 120), new Color(255, 215, 0, 0)}));
        g.fill(new Ellipse2D.Double(-130, -130, 260, 260));            // golden glow
        Color gold = new Color(255, 200, 30), dark = new Color(200, 140, 10);
        g.setStroke(new BasicStroke(10f));
        g.setColor(dark);
        g.draw(new Arc2D.Double(-92, -60, 62, 70, 90, 180, Arc2D.OPEN));   // left handle
        g.draw(new Arc2D.Double(30, -60, 62, 70, 270, 180, Arc2D.OPEN));   // right handle
        g.setStroke(new BasicStroke(1f));
        Path2D cup = new Path2D.Double();
        cup.moveTo(-60, -70); cup.lineTo(60, -70);
        cup.curveTo(60, 0, 35, 40, 0, 40);
        cup.curveTo(-35, 40, -60, 0, -60, -70);
        cup.closePath();
        g.setPaint(new GradientPaint(-60, 0, new Color(255, 235, 100), 60, 0, dark));
        g.fill(cup);
        g.setColor(gold);
        g.fillRect(-9, 40, 18, 24);                                    // stem
        g.setColor(dark);
        g.fill(new RoundRectangle2D.Double(-45, 62, 90, 18, 8, 8));    // base
        g.setColor(gold);
        g.fill(new RoundRectangle2D.Double(-32, 56, 64, 10, 6, 6));
        Path2D star = new Path2D.Double();                             // star on the cup
        for (int i = 0; i < 10; i++) {
            double r = (i % 2 == 0) ? 22 : 9, a = -Math.PI / 2 + i * Math.PI / 5;
            double px = Math.cos(a) * r, py = -20 + Math.sin(a) * r;
            if (i == 0) star.moveTo(px, py); else star.lineTo(px, py);
        }
        star.closePath();
        g.setColor(Color.WHITE);
        g.fill(star);
        g.setTransform(old);
    }

    void drawMenu(Graphics2D g) {
        for (int i = 0; i < 8; i++) {                      // decorative rainbow bricks
            g.setPaint(new GradientPaint(0, 0, RAINBOW[i].brighter(), 0, 20, RAINBOW[i].darker()));
            g.fill(new RoundRectangle2D.Double(110 + i * 70, 120, 62, 22, 10, 10));
        }
        centered(g, "NEON", 230, 80, new Color(0, 255, 230));
        centered(g, "BRICK BREAKER", 300, 52, new Color(255, 90, 220));
        centered(g, "ENTER  -  Play", 390, 26, Color.WHITE);
        centered(g, "I  -  Instructions", 430, 26, Color.WHITE);
        centered(g, "Q  -  Quit", 470, 26, Color.WHITE);
        centered(g, "High score: " + highScore, 540, 20, new Color(255, 220, 60));
    }

    void drawInstructions(Graphics2D g) {
        centered(g, "HOW TO PLAY", 110, 48, new Color(0, 255, 230));
        String[] lines = {
                "Move the paddle:  Mouse  or  LEFT / RIGHT (A / D)",
                "Launch the ball:  SPACE or Click",
                "Clear all bricks to win each level (10 levels)",
                "White-outlined bricks need 2 hits (25 pts), normal = 10 pts",
                "Hit the ball with the paddle edge to change its angle",
                "You have 3 lives - don't let the ball fall!",
                "P = Pause    R = Restart    M = Menu"
        };
        for (int i = 0; i < lines.length; i++) centered(g, lines[i], 190 + i * 45, 22, Color.WHITE);
        centered(g, "Press M to go back", 550, 20, new Color(255, 220, 60));
    }

    void overlay(Graphics2D g, String title, String sub, Color c) {
        g.setColor(new Color(0, 0, 0, 170));
        g.fillRect(0, 0, W, H);
        centered(g, title, H / 2 - 10, 64, c);
        centered(g, sub, H / 2 + 45, 22, Color.WHITE);
    }

    void centered(Graphics2D g, String text, int y, int size, Color c) {
        g.setFont(new Font("SansSerif", Font.BOLD, size));
        int x = (W - g.getFontMetrics().stringWidth(text)) / 2;
        g.setColor(new Color(0, 0, 0, 120));
        g.drawString(text, x + 2, y + 2);
        g.setColor(c);
        g.drawString(text, x, y);
    }

    /* ----- input ----- */
    void launch() { if (state == State.PLAYING && !launched) launched = true; }

    @Override public void keyPressed(KeyEvent e) {
        int k = e.getKeyCode();
        if (k == KeyEvent.VK_LEFT || k == KeyEvent.VK_A) left = true;
        if (k == KeyEvent.VK_RIGHT || k == KeyEvent.VK_D) right = true;
        if (k == KeyEvent.VK_SPACE) launch();
        if ((k == KeyEvent.VK_SPACE || k == KeyEvent.VK_ENTER) && state == State.LEVEL_CLEAR) nextLevel();
        if (state == State.MENU) {
            if (k == KeyEvent.VK_ENTER) newGame();
            if (k == KeyEvent.VK_I) state = State.INSTRUCTIONS;
            if (k == KeyEvent.VK_Q) System.exit(0);
        } else if (k == KeyEvent.VK_M) state = State.MENU;
        if (k == KeyEvent.VK_R && state != State.INSTRUCTIONS) newGame();
        if (k == KeyEvent.VK_P) {
            if (state == State.PLAYING) state = State.PAUSED;
            else if (state == State.PAUSED) state = State.PLAYING;
        }
    }
    @Override public void keyReleased(KeyEvent e) {
        int k = e.getKeyCode();
        if (k == KeyEvent.VK_LEFT || k == KeyEvent.VK_A) left = false;
        if (k == KeyEvent.VK_RIGHT || k == KeyEvent.VK_D) right = false;
    }
    @Override public void mouseMoved(MouseEvent e) {
        if (state == State.PLAYING) paddle.x = Math.max(0, Math.min(W - paddle.w, e.getX() - paddle.w / 2));
    }
    @Override public void mouseDragged(MouseEvent e) { mouseMoved(e); }
    @Override public void mousePressed(MouseEvent e) {
        requestFocusInWindow(); launch();
        if (state == State.LEVEL_CLEAR) nextLevel();
    }
    @Override public void keyTyped(KeyEvent e) {}
    @Override public void mouseClicked(MouseEvent e) {}
    @Override public void mouseReleased(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e) {}
}
