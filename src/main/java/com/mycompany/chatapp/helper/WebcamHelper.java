package com.mycompany.chatapp.helper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;

public class WebcamHelper {

    private static final String URL_PREVIEW  = "http://127.0.0.1:7777/snapshot";
    private static final String URL_RAW      = "http://127.0.0.1:7777/snapshot-raw";
    private static final String URL_HAS_FACE = "http://127.0.0.1:7777/has-face";

    private static final Color COLOR_BG    = new Color(8, 10, 16);
    private static final Color COLOR_GREEN = new Color(0, 255, 136);
    private static final Color COLOR_BLUE  = new Color(0, 180, 255);
    private static final Color COLOR_GOLD  = new Color(255, 200, 0);
    private static final Color COLOR_MUTED = new Color(80, 100, 140);
    private static final Color COLOR_WHITE = new Color(220, 230, 255);
    private static final Color COLOR_RED   = new Color(255, 80, 80);

    // ── Các góc cần chụp ──
    private static final String[] ANGLES    = {"front","left","right","up","down"};
    private static final String[] ANGLES_VN = {"Nhìn thẳng","Quay trái","Quay phải","Ngửa lên","Cúi xuống"};
    private static final String[] EMOJIS    = {"😐","👈","👉","🙃","🙇"};
    private static final String[] HINTS     = {
        "Giữ mặt thẳng, nhìn vào camera",
        "Từ từ quay đầu sang trái",
        "Từ từ quay đầu sang phải",
        "Ngửa đầu nhẹ lên trên",
        "Cúi đầu nhẹ xuống dưới"
    };

    // ── Đếm ngược giữ mặt (giây) ──
    private static final int HOLD_SECONDS = 2;

    // ════════════════════════════════════════════════════
    // API helpers
    // ════════════════════════════════════════════════════
    public static byte[] fetchRawSnapshot() throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(URL_RAW).openConnection();
        c.setConnectTimeout(5000); c.setReadTimeout(8000);
        if (c.getResponseCode() == 200) {
            byte[] b = c.getInputStream().readAllBytes();
            if (b.length > 100) return b;
        }
        throw new Exception("Camera server lỗi! Hãy chạy camera_live_server.py trước.");
    }

    private static byte[] fetchPreview() throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(URL_PREVIEW).openConnection();
        c.setConnectTimeout(3000); c.setReadTimeout(3000);
        if (c.getResponseCode() == 200) return c.getInputStream().readAllBytes();
        throw new Exception("No preview");
    }

    private static boolean checkHasFace() {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(URL_HAS_FACE).openConnection();
            c.setConnectTimeout(2000); c.setReadTimeout(2000);
            if (c.getResponseCode() == 200) {
                String r = new String(c.getInputStream().readAllBytes());
                return r.replace(" ", "").contains("\"has_face\":true");
            }
        } catch (Exception ignored) {}
        return false;
    }

    // ════════════════════════════════════════════════════
    // captureWithPreview — simple (1 ảnh)
    // ════════════════════════════════════════════════════
    public static byte[] captureWithPreview(Component parent) throws Exception {
        return captureWithPreview(parent, "Xác thực khuôn mặt", "Nhìn thẳng vào camera");
    }

    public static byte[] captureWithPreview(Component parent,
                                             String title,
                                             String instruction) throws Exception {
        AtomicReference<byte[]> result   = new AtomicReference<>(null);
        AtomicReference<Exception> error = new AtomicReference<>(null);
        AtomicBoolean running  = new AtomicBoolean(true);
        AtomicBoolean hasFace  = new AtomicBoolean(false);
        Object lock = new Object();

        SwingUtilities.invokeLater(() -> {
            try {
                JDialog dialog = buildBaseDialog(
                    SwingUtilities.getWindowAncestor(parent), title);

                // Camera panel
                CamPanel camPanel = new CamPanel(300, hasFace);

                JLabel instrLbl = makeLabel(instruction, 14, COLOR_MUTED, false);
                JLabel statusLbl = makeLabel("Đang tìm khuôn mặt...", 13, COLOR_BLUE, true);

                JButton btnCapture = makeCaptureBtn();
                JLabel  btnHint    = makeLabel("Nhấn để chụp", 11, COLOR_MUTED, false);

                // ── Layout ──
                JPanel root = buildRoot(dialog);
                root.add(buildTopBar(title, () -> {
                    running.set(false); result.set(null);
                    dialog.dispose();
                    synchronized (lock) { lock.notifyAll(); }
                }), BorderLayout.NORTH);

                JPanel center = new JPanel(new BorderLayout());
                center.setOpaque(false);
                JPanel camWrap = new JPanel(new GridBagLayout());
                camWrap.setOpaque(false);
                camWrap.add(camPanel);
                JPanel txtPanel = new JPanel();
                txtPanel.setLayout(new BoxLayout(txtPanel, BoxLayout.Y_AXIS));
                txtPanel.setOpaque(false);
                txtPanel.setBorder(new EmptyBorder(14, 20, 14, 20));
                instrLbl.setAlignmentX(Component.CENTER_ALIGNMENT);
                statusLbl.setAlignmentX(Component.CENTER_ALIGNMENT);
                txtPanel.add(instrLbl);
                txtPanel.add(Box.createVerticalStrut(6));
                txtPanel.add(statusLbl);
                center.add(camWrap,   BorderLayout.CENTER);
                center.add(txtPanel,  BorderLayout.SOUTH);

                JPanel btnPanel = new JPanel();
                btnPanel.setLayout(new BoxLayout(btnPanel, BoxLayout.Y_AXIS));
                btnPanel.setOpaque(false);
                btnPanel.setBorder(new EmptyBorder(0, 0, 30, 0));
                btnCapture.setAlignmentX(Component.CENTER_ALIGNMENT);
                btnHint.setAlignmentX(Component.CENTER_ALIGNMENT);
                btnPanel.add(btnCapture);
                btnPanel.add(Box.createVerticalStrut(8));
                btnPanel.add(btnHint);

                root.add(center,   BorderLayout.CENTER);
                root.add(btnPanel, BorderLayout.SOUTH);
                dialog.add(root);

                // ── Live thread ──
                startLiveThread(running, hasFace, camPanel, statusLbl,
                                btnCapture, btnHint, null, null, null);

                // ── Chụp ──
             // ── Chụp ──
                btnCapture.addActionListener(ev -> {
                    btnCapture.setEnabled(false);
                    statusLbl.setText("📸 Đang chụp...");
                    new Thread(() -> {
                        try {
                            result.set(fetchRawSnapshot());
                            running.set(false);
                            SwingUtilities.invokeLater(() -> {
                                statusLbl.setText("✅ Chụp thành công!");
                                // KHÔNG làm gì thêm ở đây — CSV do ChatClientUI xử lý
                            });
                            Thread.sleep(900);
                        } catch (Exception ex) { error.set(ex); }
                        finally {
                            running.set(false);
                            SwingUtilities.invokeLater(() -> {
                                dialog.dispose();
                                synchronized (lock) { lock.notifyAll(); }
                            });
                        }
                    }).start();
                });

                dialog.setVisible(true);
            } catch (Exception ex) {
                error.set(ex);
                synchronized (lock) { lock.notifyAll(); }
            }
        });

        synchronized (lock) { lock.wait(60_000); }
        if (error.get() != null) throw error.get();
        if (result.get() == null) throw new Exception("Người dùng đã hủy");
        return result.get();
    }

    // ════════════════════════════════════════════════════
    // captureMultiAngle — TỰ ĐỘNG hướng dẫn 5 góc
    // Callback: onAngleCaptured(angle, imageBytes)
    // ════════════════════════════════════════════════════
    public static void captureMultiAngle(Component parent,
            String mssv, String hoten,
            MultiAngleCallback callback) {

        AtomicBoolean running  = new AtomicBoolean(true);
        AtomicBoolean hasFace  = new AtomicBoolean(false);
        AtomicInteger angleIdx = new AtomicInteger(0);
        Object lock = new Object();

        SwingUtilities.invokeLater(() -> {
            try {
                JDialog dialog = buildBaseDialog(
                    SwingUtilities.getWindowAncestor(parent),
                    "Đăng ký khuôn mặt");
                dialog.setSize(400, 650);

                CamPanel camPanel = new CamPanel(280, hasFace);

                // ── Emoji góc hiện tại ──
                JLabel emojiLbl = new JLabel(EMOJIS[0], SwingConstants.CENTER);
                emojiLbl.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 36));
                emojiLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

                // ── Tên góc ──
                JLabel angleLbl = makeLabel(ANGLES_VN[0], 18, COLOR_WHITE, true);
                angleLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

                // ── Gợi ý ──
                JLabel hintLbl = makeLabel(HINTS[0], 12, COLOR_MUTED, false);
                hintLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

                // ── Status ──
                JLabel statusLbl = makeLabel("Đang tìm khuôn mặt...", 13, COLOR_BLUE, true);
                statusLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

                // ── Progress dots ──
                JPanel dotsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
                dotsPanel.setOpaque(false);
                JLabel[] dots = new JLabel[5];
                for (int i = 0; i < 5; i++) {
                    dots[i] = new JLabel("●");
                    dots[i].setFont(new Font("SansSerif", Font.PLAIN, 14));
                    dots[i].setForeground(i == 0 ? COLOR_BLUE : COLOR_MUTED);
                    dotsPanel.add(dots[i]);
                }

                // ── Countdown bar ──
                JProgressBar countdown = new JProgressBar(0, HOLD_SECONDS * 10);
                countdown.setValue(0);
                countdown.setForeground(COLOR_GREEN);
                countdown.setBackground(new Color(20, 26, 42));
                countdown.setBorder(new EmptyBorder(0, 0, 0, 0));
                countdown.setPreferredSize(new Dimension(260, 6));
                countdown.setMaximumSize(new Dimension(Integer.MAX_VALUE, 6));
                countdown.setVisible(false);

                // ── Layout ──
                JPanel root = buildRoot(dialog);
                root.add(buildTopBar("Đăng ký khuôn mặt", () -> {
                    running.set(false);
                    dialog.dispose();
                    synchronized (lock) { lock.notifyAll(); }
                }), BorderLayout.NORTH);

                JPanel center = new JPanel();
                center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
                center.setOpaque(false);
                center.setBorder(new EmptyBorder(10, 20, 10, 20));

                // Camera wrap
                JPanel camWrap = new JPanel(new GridBagLayout());
                camWrap.setOpaque(false);
                camWrap.setAlignmentX(Component.CENTER_ALIGNMENT);
                camWrap.add(camPanel);

                // Countdown wrap
                JPanel cdWrap = new JPanel(new FlowLayout(FlowLayout.CENTER));
                cdWrap.setOpaque(false);
                cdWrap.add(countdown);

                center.add(camWrap);
                center.add(Box.createVerticalStrut(10));
                center.add(dotsPanel);
                center.add(Box.createVerticalStrut(10));
                center.add(emojiLbl);
                center.add(Box.createVerticalStrut(4));
                center.add(angleLbl);
                center.add(Box.createVerticalStrut(4));
                center.add(hintLbl);
                center.add(Box.createVerticalStrut(10));
                center.add(cdWrap);
                center.add(Box.createVerticalStrut(6));
                center.add(statusLbl);

                root.add(center, BorderLayout.CENTER);
                dialog.add(root);

                // ── Auto-capture logic ──
                final boolean[] isCapturing = {false};
                final int[] holdCounter = {0};

                // Live thread với auto-capture
                Thread liveThread = new Thread(() -> {
                    while (running.get()) {
                        try {
                            // Cập nhật preview
                            byte[] bytes = fetchPreview();
                            BufferedImage img = ImageIO.read(
                                new ByteArrayInputStream(bytes));
                            if (img != null && running.get()) {
                                final BufferedImage fi = img;
                                SwingUtilities.invokeLater(() -> camPanel.setImage(fi));
                            }

                            // Kiểm tra mặt
                            boolean hf = checkHasFace();
                            hasFace.set(hf);

                            int idx = angleIdx.get();
                            if (idx >= ANGLES.length) break;

                            if (hf && !isCapturing[0]) {
                                holdCounter[0]++;
                                int progress = Math.min(holdCounter[0],
                                                        HOLD_SECONDS * 10);
                                final int prog = progress;
                                SwingUtilities.invokeLater(() -> {
                                    countdown.setVisible(true);
                                    countdown.setValue(prog);
                                    statusLbl.setText("Giữ yên...");
                                    statusLbl.setForeground(COLOR_GREEN);
                                    camPanel.repaint();
                                });

                                // Đủ thời gian → chụp tự động
                                if (holdCounter[0] >= HOLD_SECONDS * 10) {
                                    isCapturing[0] = true;
                                    holdCounter[0] = 0;

                                    SwingUtilities.invokeLater(() -> {
                                        countdown.setValue(0);
                                        countdown.setVisible(false);
                                        statusLbl.setText("📸 Đang chụp...");
                                        statusLbl.setForeground(COLOR_GOLD);
                                    });

                                    Thread.sleep(200);
                                    byte[] raw = fetchRawSnapshot();

                                    // Callback gửi ảnh lên server
                                    String angle = ANGLES[idx];
                                    callback.onAngleCaptured(angle, raw, idx + 1,
                                        (success, msg) -> {
                                            int nextIdx = idx + 1;
                                            SwingUtilities.invokeLater(() -> {
                                                // Cập nhật dot
                                                dots[idx].setForeground(
                                                    success ? COLOR_GREEN : COLOR_RED);

                                                if (nextIdx >= ANGLES.length) {
                                                    // Hoàn tất!
                                                    running.set(false);
                                                    statusLbl.setForeground(COLOR_GREEN);
                                                    statusLbl.setText("🎉 Đăng ký hoàn tất!");
                                                    javax.swing.Timer t =
                                                        new javax.swing.Timer(1500,
                                                            e -> {
                                                                dialog.dispose();
                                                                synchronized (lock) {
                                                                    lock.notifyAll();
                                                                }
                                                            });
                                                    t.setRepeats(false);
                                                    t.start();
                                                } else {
                                                    // Chuyển góc tiếp
                                                    angleIdx.set(nextIdx);
                                                    dots[nextIdx].setForeground(COLOR_BLUE);
                                                    emojiLbl.setText(EMOJIS[nextIdx]);
                                                    angleLbl.setText(ANGLES_VN[nextIdx]);
                                                    hintLbl.setText(HINTS[nextIdx]);
                                                    statusLbl.setForeground(COLOR_BLUE);
                                                    statusLbl.setText("✅ Xong! Chuyển sang góc tiếp...");
                                                }
                                                isCapturing[0] = false;
                                            });
                                        });
                                }
                            } else if (!hf) {
                                holdCounter[0] = 0;
                                SwingUtilities.invokeLater(() -> {
                                    countdown.setValue(0);
                                    countdown.setVisible(false);
                                    statusLbl.setText("🔍 Đang tìm khuôn mặt...");
                                    statusLbl.setForeground(COLOR_BLUE);
                                    camPanel.repaint();
                                });
                            }

                            Thread.sleep(100); // 10fps check
                        } catch (Exception ex) {
                            try { Thread.sleep(500); } catch (Exception ignored) {}
                        }
                    }
                });
                liveThread.setDaemon(true);
                liveThread.start();

                dialog.setVisible(true);
                synchronized (lock) {
                    try { lock.wait(300_000); } catch (Exception ignored) {}
                }

            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
    }

    // ════════════════════════════════════════════════════
    // Interface callback
    // ════════════════════════════════════════════════════
    public interface MultiAngleCallback {
        void onAngleCaptured(String angle, byte[] imageBytes,
                             int angleNumber, AngleResult onResult);
    }

    public interface AngleResult {
        void done(boolean success, String message);
    }

    // ════════════════════════════════════════════════════
    // CamPanel — panel vẽ camera hình tròn
    // ════════════════════════════════════════════════════
    public static class CamPanel extends JPanel {
        private BufferedImage currentImg = null;
        private final int size;
        private final AtomicBoolean hasFace;

        public CamPanel(int size, AtomicBoolean hasFace) {
            this.size    = size;
            this.hasFace = hasFace;
            setPreferredSize(new Dimension(size, size));
            setOpaque(false);
        }

        public void setImage(BufferedImage img) {
            this.currentImg = img;
            repaint();
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                                RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            int cx = size / 2, cy = size / 2;
            int r  = size / 2 - 8;
            Color glowColor = hasFace.get() ? COLOR_GREEN : COLOR_BLUE;

            // Glow
            for (int i = 8; i > 0; i--) {
                int alpha = (int)(12 * (1.0 - i / 8.0)) + 4;
                g2.setColor(new Color(glowColor.getRed(),
                                      glowColor.getGreen(),
                                      glowColor.getBlue(), alpha));
                g2.setStroke(new BasicStroke(i * 2));
                g2.drawOval(cx - r - i, cy - r - i,
                            (r + i) * 2, (r + i) * 2);
            }

            // Clip tròn
            Ellipse2D clip = new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2);
            g2.setClip(clip);
            if (currentImg != null) {
                g2.drawImage(currentImg, cx - r, cy - r, r * 2, r * 2, null);
            } else {
                g2.setColor(new Color(20, 26, 42));
                g2.fill(clip);
                g2.setColor(COLOR_MUTED);
                g2.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 42));
                g2.drawString("📷", cx - 22, cy + 16);
            }
            g2.setClip(null);

            // Viền
            g2.setColor(glowColor);
            g2.setStroke(new BasicStroke(2.5f));
            g2.drawOval(cx - r, cy - r, r * 2, r * 2);

            // 4 góc trang trí
            g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND,
                                          BasicStroke.JOIN_ROUND));
            int al = 22;
            int[][] corners = {
                {cx-r-4, cy-r-4,  1,  1},
                {cx+r-al+4, cy-r-4, -1,  1},
                {cx-r-4, cy+r-al+4,  1, -1},
                {cx+r-al+4, cy+r-al+4, -1, -1}
            };
            for (int[] c : corners) {
                g2.drawLine(c[0], c[1], c[0]+c[2]*al, c[1]);
                g2.drawLine(c[0], c[1], c[0], c[1]+c[3]*al);
            }

            g2.dispose();
        }
    }

    // ════════════════════════════════════════════════════
    // UI Helpers
    // ════════════════════════════════════════════════════
    private static JDialog buildBaseDialog(Window parent, String title) {
    	JDialog d = new JDialog(parent, title, Dialog.ModalityType.MODELESS);
        d.setSize(400, 600);
        d.setLocationRelativeTo(parent);
        d.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        d.setUndecorated(true);
        d.setBackground(COLOR_BG);
        try {
            d.setShape(new RoundRectangle2D.Double(0, 0, 400, 600, 20, 20));
        } catch (Exception ignored) {}
        return d;
    }

    private static JPanel buildRoot(JDialog dialog) {
        JPanel root = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(COLOR_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                g2.dispose();
            }
        };
        root.setOpaque(false);
        root.setBorder(BorderFactory.createLineBorder(new Color(30, 40, 65), 1));
        return root;
    }

    private static JPanel buildTopBar(String title, Runnable onClose) {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        bar.setBorder(new EmptyBorder(18, 20, 10, 20));

        JLabel titleLbl = new JLabel(title, SwingConstants.CENTER);
        titleLbl.setFont(new Font("SansSerif", Font.BOLD, 15));
        titleLbl.setForeground(COLOR_WHITE);

        JButton btnX = new JButton("✕");
        btnX.setFont(new Font("SansSerif", Font.BOLD, 13));
        btnX.setForeground(COLOR_MUTED);
        btnX.setContentAreaFilled(false);
        btnX.setBorderPainted(false);
        btnX.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnX.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { btnX.setForeground(COLOR_RED); }
            public void mouseExited(MouseEvent e)  { btnX.setForeground(COLOR_MUTED); }
        });
        btnX.addActionListener(e -> onClose.run());

        bar.add(titleLbl, BorderLayout.CENTER);
        bar.add(btnX,     BorderLayout.EAST);
        return bar;
    }

    private static JLabel makeLabel(String text, int size,
                                     Color color, boolean bold) {
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setFont(new Font("SansSerif", bold ? Font.BOLD : Font.PLAIN, size));
        l.setForeground(color);
        return l;
    }

    private static JButton makeCaptureBtn() {
        JButton btn = new JButton() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                Color c = isEnabled() ? COLOR_GREEN : COLOR_MUTED;
                g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 60));
                g2.setStroke(new BasicStroke(3f));
                g2.drawOval(4, 4, w-8, h-8);
                g2.setColor(isEnabled()
                    ? (getModel().isPressed() ? COLOR_GREEN.darker() : COLOR_GREEN)
                    : new Color(40, 50, 70));
                g2.fillOval(12, 12, w-24, h-24);
                g2.dispose();
            }
        };
        btn.setPreferredSize(new Dimension(72, 72));
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setEnabled(true);  // ← cho phép nhấn ngay
        return btn;
    }

    private static void startLiveThread(AtomicBoolean running,
            AtomicBoolean hasFace, CamPanel camPanel,
            JLabel statusLbl, JButton btnCapture,
            JLabel btnHint,
            JLabel angleLbl, JLabel hintLbl, JProgressBar countdown) {

        Thread t = new Thread(() -> {
            int fc = 0;
            while (running.get()) {
                try {
                    byte[] bytes = fetchPreview();
                    BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
                    if (img != null && running.get()) {
                        final BufferedImage fi = img;
                        SwingUtilities.invokeLater(() -> camPanel.setImage(fi));
                    }
                    fc++;
                    if (fc % 5 == 0) {
                        boolean hf = checkHasFace();
                        hasFace.set(hf);
                        SwingUtilities.invokeLater(() -> {
                            if (hf) {
                                statusLbl.setText("✅ Phát hiện khuôn mặt — nhấn Chụp");
                                statusLbl.setForeground(COLOR_GREEN);
                                if (btnCapture != null) {
                                    btnCapture.setEnabled(true);
                                    if (btnHint != null)
                                        btnHint.setForeground(COLOR_GREEN);
                                }
                            } else {
                                statusLbl.setText("🔍 Đang tìm khuôn mặt...");
                                statusLbl.setForeground(COLOR_BLUE);
                                if (btnCapture != null) {
                                    
                                }
                            }
                            camPanel.repaint();
                        });
                    }
                    Thread.sleep(50);
                } catch (Exception ex) {
                    try { Thread.sleep(500); } catch (Exception ignored) {}
                }
            }
        });
        t.setDaemon(true);
        t.start();
    }
}