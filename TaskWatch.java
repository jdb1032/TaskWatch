import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;

public class TaskWatch extends JFrame {

    // --- State ---
    private long startTime = 0;
    private long elapsedAtPause = 0;
    private long lastLapTime = 0;
    private boolean running = false;
    private int lapNumber = 0;
    private final ArrayList<long[]> laps = new ArrayList<>(); // [lapMs, splitMs]

    // AHT state
    private int ahtValue = 0;          // raw number user typed
    private boolean ahtInSeconds = false; // false = minutes, true = seconds
    private boolean intervalBreached = false;
    private boolean avgBreached = false;

    // --- UI ---
    private JLabel timeDisplay;
    private JLabel splitDisplay;
    private JLabel ahtAlertBanner;
    private JTextField ahtField;
    private JComboBox<String> ahtUnitBox;
    private JButton playBtn, lapBtn, resetBtn;
    private DefaultTableModel tableModel;
    private JLabel totalLabel, avgLabel, paceLabel;
    private Timer swingTimer;

    // -------------------------------------------------------------------------
    // Entry point
    // -------------------------------------------------------------------------
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new TaskWatch().setVisible(true));
    }

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------
    public TaskWatch() {
        setTitle("Task Watch");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(false);
        setBackground(Color.decode("#0f0f0f"));

        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(Color.decode("#0f0f0f"));
        root.setBorder(BorderFactory.createEmptyBorder(28, 28, 28, 28));

        // ---- Clock panel ----
        JPanel clockPanel = new JPanel();
        clockPanel.setLayout(new BoxLayout(clockPanel, BoxLayout.Y_AXIS));
        clockPanel.setBackground(Color.decode("#0f0f0f"));
        clockPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 20, 0));

        timeDisplay = new JLabel("00:00:00.00", SwingConstants.CENTER);
        timeDisplay.setFont(loadMono(64f));
        timeDisplay.setForeground(Color.decode("#e8e8e8"));
        timeDisplay.setAlignmentX(Component.CENTER_ALIGNMENT);

        JSeparator sep = new JSeparator(SwingConstants.HORIZONTAL);
        sep.setForeground(Color.decode("#2a2a2a"));
        sep.setBackground(Color.decode("#0f0f0f"));
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));

        JLabel intervalTag = new JLabel("HANDLE TIME", SwingConstants.CENTER);
        intervalTag.setFont(loadMono(10f));
        intervalTag.setForeground(Color.decode("#969696"));
        intervalTag.setAlignmentX(Component.CENTER_ALIGNMENT);
        intervalTag.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));

        splitDisplay = new JLabel("00:00:00.00", SwingConstants.CENTER);
        splitDisplay.setFont(loadMono(30f));
        splitDisplay.setForeground(Color.decode("#1a8cff"));
        splitDisplay.setAlignmentX(Component.CENTER_ALIGNMENT);
        splitDisplay.setBorder(BorderFactory.createEmptyBorder(2, 0, 4, 0));

        clockPanel.add(timeDisplay);
        clockPanel.add(sep);
        clockPanel.add(intervalTag);
        clockPanel.add(splitDisplay);

        // ---- AHT input row ----
        JPanel ahtRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        ahtRow.setBackground(Color.decode("#0f0f0f"));
        ahtRow.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        JLabel ahtLabel = new JLabel("AHT:");
        ahtLabel.setFont(loadMono(12f));
        ahtLabel.setForeground(Color.decode("#969696"));

        ahtField = new JTextField(4);
        ahtField.setFont(loadMono(14f));
        ahtField.setBackground(Color.decode("#1a1a1a"));
        ahtField.setForeground(Color.decode("#e8e8e8"));
        ahtField.setCaretColor(Color.decode("#e8e8e8"));
        ahtField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.decode("#333333"), 1),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        ahtField.setHorizontalAlignment(SwingConstants.CENTER);
        ahtField.setToolTipText("Enter a whole number");
        ahtField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e)  { parseAht(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e)  { parseAht(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { parseAht(); }
        });

        // Unit dropdown: min / sec
        ahtUnitBox = new JComboBox<>(new String[]{"min", "sec"});
        ahtUnitBox.setFont(loadMono(12f));
        ahtUnitBox.setBackground(Color.decode("#1a1a1a"));
        ahtUnitBox.setForeground(Color.decode("#e8e8e8"));
        ahtUnitBox.setFocusable(false);
        ahtUnitBox.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        ahtUnitBox.addActionListener(e -> {
            ahtInSeconds = ahtUnitBox.getSelectedIndex() == 1;
            parseAht();
        });

        ahtRow.add(ahtLabel);
        ahtRow.add(ahtField);
        ahtRow.add(ahtUnitBox);
        clockPanel.add(ahtRow);

        // ---- Interval AHT alert banner ----
        ahtAlertBanner = new JLabel("! TASK OVER AHT !", SwingConstants.CENTER);
        ahtAlertBanner.setFont(loadMono(14f).deriveFont(Font.BOLD));
        ahtAlertBanner.setForeground(Color.decode("#ff2222"));
        ahtAlertBanner.setBackground(Color.decode("#2a0000"));
        ahtAlertBanner.setOpaque(true);
        ahtAlertBanner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.decode("#cc0000"), 2),
                BorderFactory.createEmptyBorder(6, 0, 6, 0)));
        ahtAlertBanner.setAlignmentX(Component.CENTER_ALIGNMENT);
        ahtAlertBanner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        ahtAlertBanner.setVisible(false);
        clockPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        clockPanel.add(ahtAlertBanner);

        root.add(clockPanel, BorderLayout.NORTH);

        // ---- Buttons ----
        JPanel btnPanel = new JPanel(new GridLayout(1, 3, 12, 0));
        btnPanel.setBackground(Color.decode("#0f0f0f"));
        btnPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 24, 0));

        playBtn  = makeButton("[ Play ]",  "#1a8cff", "#0f0f0f");
        lapBtn   = makeButton("[ Log ]",   "#2a2a2a", "#888888");
        resetBtn = makeButton("[ Reset ]", "#2a2a2a", "#888888");

        lapBtn.setEnabled(false);
        resetBtn.setEnabled(false);

        playBtn.addActionListener(e -> onPlayPause());
        lapBtn.addActionListener(e -> onLap());
        resetBtn.addActionListener(e -> onReset());

        btnPanel.add(playBtn);
        btnPanel.add(lapBtn);
        btnPanel.add(resetBtn);
        root.add(btnPanel, BorderLayout.CENTER);

        // ---- Table ----
        String[] cols = {"#", "From", "To", "Handle Time"};
        tableModel = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };

        JTable table = new JTable(tableModel);
        table.setFont(loadMono(13f));
        table.setBackground(Color.decode("#131313"));
        table.setForeground(Color.decode("#cccccc"));
        table.setSelectionBackground(Color.decode("#1a1a2e"));
        table.setSelectionForeground(Color.decode("#e8e8e8"));
        table.setRowHeight(28);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.getTableHeader().setFont(loadMono(12f));
        table.getTableHeader().setBackground(Color.decode("#1a1a1a"));
        table.getTableHeader().setForeground(Color.decode("#666666"));
        table.getTableHeader().setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.decode("#2a2a2a")));

        int[] widths = {36, 110, 110, 110};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }

        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable t, Object val, boolean sel, boolean focus, int row, int col) {
                super.getTableCellRendererComponent(t, val, sel, focus, row, col);
                setFont(loadMono(13f));
                if (sel) {
                    setBackground(Color.decode("#1e1e3a"));
                    setForeground(Color.decode("#e8e8e8"));
                } else {
                    setBackground(row % 2 == 0 ? Color.decode("#131313") : Color.decode("#161616"));
                    setForeground(col == 3 ? Color.decode("#1a8cff") : Color.decode("#cccccc"));
                }
                setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
                if (col == 0) setHorizontalAlignment(CENTER);
                else setHorizontalAlignment(col == 3 ? CENTER : LEFT);
                return this;
            }
        });

        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(400, 280));
        scroll.setBorder(BorderFactory.createLineBorder(Color.decode("#2a2a2a"), 1));
        scroll.getViewport().setBackground(Color.decode("#131313"));
        scroll.setBackground(Color.decode("#131313"));

        // ---- Summary strip (3 tiles: Total | Avg | Pace) ----
        JPanel sumPanel = new JPanel(new GridLayout(1, 3, 10, 0));
        sumPanel.setBackground(Color.decode("#0f0f0f"));
        sumPanel.setBorder(BorderFactory.createEmptyBorder(14, 0, 0, 0));
        totalLabel = summaryLabel("Total   —");
        avgLabel   = summaryLabel("Avg   —");
        paceLabel  = summaryLabel("Pace   —");
        sumPanel.add(totalLabel);
        sumPanel.add(avgLabel);
        sumPanel.add(paceLabel);

        JPanel bottomPanel = new JPanel(new BorderLayout(0, 0));
        bottomPanel.setBackground(Color.decode("#0f0f0f"));
        bottomPanel.add(scroll, BorderLayout.CENTER);
        bottomPanel.add(sumPanel, BorderLayout.SOUTH);
        root.add(bottomPanel, BorderLayout.SOUTH);

        setContentPane(root);
        pack();
        setLocationRelativeTo(null);

        swingTimer = new Timer(10, e -> updateDisplay());
    }

    // -------------------------------------------------------------------------
    // Actions
    // -------------------------------------------------------------------------
    private void onPlayPause() {
        if (!running) {
            startTime = System.currentTimeMillis();
            if (elapsedAtPause == 0) lastLapTime = 0;
            running = true;
            swingTimer.start();
            styleButton(playBtn, "[ Pause ]", "#ff6b35", "#0f0f0f");
            lapBtn.setEnabled(true);
            resetBtn.setEnabled(false);
        } else {
            elapsedAtPause += System.currentTimeMillis() - startTime;
            running = false;
            swingTimer.stop();
            styleButton(playBtn, "[ Resume ]", "#1a8cff", "#0f0f0f");
            lapBtn.setEnabled(false);
            resetBtn.setEnabled(true);
        }
    }

    private void onLap() {
        long now = elapsed();
        long splitMs = laps.isEmpty() ? now : now - lastLapTime;
        lastLapTime = now;
        lapNumber++;
        laps.add(new long[]{now, splitMs});

        String fromStr  = lapNumber == 1 ? "00:00:00.00" : formatMs(laps.get(lapNumber - 2)[0]);
        String toStr    = formatMs(now);
        String splitStr = formatMs(splitMs);
        tableModel.insertRow(0, new Object[]{lapNumber, fromStr, toStr, splitStr});

        clearIntervalAlert();
        updateSummary();
    }

    private void onReset() {
        elapsedAtPause = 0;
        lastLapTime = 0;
        lapNumber = 0;
        laps.clear();
        tableModel.setRowCount(0);
        timeDisplay.setText("00:00:00.00");
        splitDisplay.setText("00:00:00.00");
        totalLabel.setText("Total   —");
        avgLabel.setText("Avg   —");
        clearIntervalAlert();
        clearAvgAlert();
        resetPaceLabel();
        styleButton(playBtn, "[ Play ]", "#1a8cff", "#0f0f0f");
        styleButton(lapBtn, "[ Lap ]", "#2a2a2a", "#888888");
        lapBtn.setEnabled(false);
        resetBtn.setEnabled(false);
    }

    // -------------------------------------------------------------------------
    // Display & alert logic
    // -------------------------------------------------------------------------
    private void updateDisplay() {
        long now = elapsed();
        long intervalMs = now - lastLapTime;
        timeDisplay.setText(formatMs(now));
        splitDisplay.setText(formatMs(intervalMs));
        checkIntervalBreach(intervalMs);
        updatePace();
    }

    /** AHT threshold in milliseconds; 0 if not configured. */
    private long ahtLimitMs() {
        if (ahtValue <= 0) return 0;
        return ahtInSeconds
                ? (long) ahtValue * 1000
                : (long) ahtValue * 60 * 1000;
    }

    private void checkIntervalBreach(long intervalMs) {
        long limit = ahtLimitMs();
        if (limit <= 0) {
            if (intervalBreached) clearIntervalAlert();
            return;
        }
        boolean over = intervalMs >= limit;
        if (over && !intervalBreached) {
            intervalBreached = true;
            ahtAlertBanner.setVisible(true);
            splitDisplay.setForeground(Color.decode("#ff2222"));
            // Main clock stays white — only avg breach changes it
            pack();
        } else if (!over && intervalBreached) {
            clearIntervalAlert();
        }
    }

    private void checkAvgBreach(long avgMs) {
        long limit = ahtLimitMs();
        if (limit <= 0) {
            if (avgBreached) clearAvgAlert();
            return;
        }
        boolean over = avgMs > limit;
        if (over && !avgBreached) {
            avgBreached = true;
            avgLabel.setBackground(Color.decode("#2a0000"));
            avgLabel.setForeground(Color.decode("#ff4444"));
            avgLabel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Color.decode("#cc0000"), 2),
                    BorderFactory.createEmptyBorder(5, 0, 5, 0)));
            // Only here does the main clock turn red
            timeDisplay.setForeground(Color.decode("#ff6666"));
        } else if (!over && avgBreached) {
            clearAvgAlert();
        }
    }

    private void clearIntervalAlert() {
        intervalBreached = false;
        ahtAlertBanner.setVisible(false);
        splitDisplay.setForeground(Color.decode("#1a8cff"));
        // Do NOT touch main clock color here — that belongs to avg alert
        pack();
    }

    private void clearAvgAlert() {
        avgBreached = false;
        avgLabel.setBackground(Color.decode("#111111"));
        avgLabel.setForeground(Color.decode("#555555"));
        avgLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.decode("#1e1e1e"), 1),
                BorderFactory.createEmptyBorder(6, 0, 6, 0)));
        timeDisplay.setForeground(Color.decode("#e8e8e8")); // restore main clock to white
    }

    private void parseAht() {
        String txt = ahtField.getText().trim();
        try {
            int val = Integer.parseInt(txt);
            ahtValue = val > 0 ? val : 0;
        } catch (NumberFormatException ex) {
            ahtValue = 0;
        }
        // Re-evaluate all alerts immediately
        long intervalMs = elapsed() - lastLapTime;
        checkIntervalBreach(intervalMs);
        if (!laps.isEmpty()) {
            long total = 0;
            for (long[] lap : laps) total += lap[1];
            checkAvgBreach(total / laps.size());
        } else {
            if (avgBreached) clearAvgAlert();
        }
        updatePace();
    }

    private void updateSummary() {
        long total = 0;
        for (long[] lap : laps) total += lap[1];
        long avg = laps.isEmpty() ? 0 : total / laps.size();
        totalLabel.setText("Total   " + formatMs(total));
        avgLabel.setText("Avg   " + formatMs(avg));
        checkAvgBreach(avg);
        updatePace();
    }

    // -------------------------------------------------------------------------
    // Pace indicator
    // -------------------------------------------------------------------------

    /**
     * Pace = (completedTasks × ahtLimitMs) − elapsed
     *
     * Positive → ahead of target (green)  e.g. "+8:00"
     * Negative → behind target  (red)     e.g. "-5:30"
     *
     * Only meaningful when AHT is set and at least one task has been logged.
     * Updated on every timer tick so it counts down in real-time.
     */
    private void updatePace() {
        long limit = ahtLimitMs();
        if (limit <= 0 || laps.isEmpty()) {
            resetPaceLabel();
            return;
        }

        long target = (long) laps.size() * limit;
        long pace   = target - elapsed();    // + = ahead, - = behind
        boolean ahead = pace >= 0;

        String sign     = ahead ? "+" : "-";
        String timeStr  = formatPace(Math.abs(pace));
        paceLabel.setText("Pace  " + sign + timeStr);
        paceLabel.setOpaque(true);

        if (ahead) {
            paceLabel.setForeground(Color.decode("#22cc66"));
            paceLabel.setBackground(Color.decode("#0a1f12"));
            paceLabel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Color.decode("#1a6635"), 1),
                    BorderFactory.createEmptyBorder(6, 0, 6, 0)));
        } else {
            paceLabel.setForeground(Color.decode("#ff5555"));
            paceLabel.setBackground(Color.decode("#1f0a0a"));
            paceLabel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Color.decode("#661a1a"), 1),
                    BorderFactory.createEmptyBorder(6, 0, 6, 0)));
        }
    }

    /** Restore pace tile to neutral/empty state. */
    private void resetPaceLabel() {
        paceLabel.setText("Pace   —");
        paceLabel.setForeground(Color.decode("#555555"));
        paceLabel.setBackground(Color.decode("#111111"));
        paceLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.decode("#1e1e1e"), 1),
                BorderFactory.createEmptyBorder(6, 0, 6, 0)));
    }

    /**
     * Format milliseconds as M:SS or H:MM:SS (no centiseconds — seconds
     * precision is appropriate for a budget/pace metric).
     */
    private String formatPace(long ms) {
        long totalSecs = ms / 1000;
        long s = totalSecs % 60;
        long m = (totalSecs / 60) % 60;
        long h = totalSecs / 3600;
        if (h > 0) return String.format("%d:%02d:%02d", h, m, s);
        return String.format("%d:%02d", m, s);
    }

    // -------------------------------------------------------------------------
    // Shared helpers
    // -------------------------------------------------------------------------
    private long elapsed() {
        return running
                ? elapsedAtPause + (System.currentTimeMillis() - startTime)
                : elapsedAtPause;
    }

    private String formatMs(long ms) {
        long centis = ms / 10;
        long cs = centis % 100;
        long s  = (centis / 100) % 60;
        long m  = (centis / 6000) % 60;
        long h  = centis / 360000;
        return String.format("%02d:%02d:%02d.%02d", h, m, s, cs);
    }

    // -------------------------------------------------------------------------
    // UI factory helpers
    // -------------------------------------------------------------------------
    private JButton makeButton(String text, String bg, String fg) {
        JButton b = new JButton(text);
        styleButton(b, text, bg, fg);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setPreferredSize(new Dimension(120, 44));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private void styleButton(JButton b, String text, String bg, String fg) {
        b.setText(text);
        b.setBackground(Color.decode(bg));
        b.setForeground(Color.decode(fg));
        b.setFont(loadMono(14f).deriveFont(Font.BOLD));
        b.setOpaque(true);
        b.repaint();
    }

    private JLabel summaryLabel(String text) {
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setFont(loadMono(13f));
        l.setForeground(Color.decode("#555555"));
        l.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.decode("#1e1e1e"), 1),
                BorderFactory.createEmptyBorder(6, 0, 6, 0)));
        l.setBackground(Color.decode("#111111"));
        l.setOpaque(true);
        return l;
    }

    private Font loadMono(float size) {
        return new Font("JetBrains Mono", Font.PLAIN, (int) size)
                .getFamily().equals("JetBrains Mono")
                ? new Font("JetBrains Mono", Font.PLAIN, (int) size)
                : new Font(Font.MONOSPACED, Font.PLAIN, (int) size);
    }
}