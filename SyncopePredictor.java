import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;             
import javax.swing.UIManager;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.event.ActionListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

/**
 * SyncopePredictor v1.2
 * =====================
 * Система моніторингу пацієнтів під час фізичної реабілітації
 * для прогнозування вазовагального синкопе на основі алгоритму PRSA.
 *
 * Інтерфейс: класичний медичний стиль (світла тема, як MedMonitor)
 * Компіляція: javac SyncopePredictor.java
 * Запуск:     java SyncopePredictor
 */
public class SyncopePredictor {

    // =========================================================================
    // КОНСТАНТИ
    // =========================================================================

    /** Порогове значення DC (мс) — нижче цього рівня ризик синкопе */
    private static final double DC_THRESHOLD  = 4.5;
    private static final double DC_NORMAL_MIN = 6.5;
    private static final double DC_NORMAL_MAX = 7.2;
    private static final int    MAX_POINTS    = 120;
    private static final int    TIMER_MS      = 500;
    private static final int    FALL_TICK     = 32;  // тік початку падіння (~16 с)

    // =========================================================================
    // КОЛЬОРОВА СХЕМА — світла медична тема
    // =========================================================================

    private static final Color CLR_BG          = new Color(240, 240, 240);
    private static final Color CLR_WHITE        = Color.WHITE;
    private static final Color CLR_PANEL_BG     = new Color(245, 245, 245);
    private static final Color CLR_BORDER       = new Color(180, 180, 180);
    private static final Color CLR_HEADER_BG    = new Color(230, 230, 230);
    private static final Color CLR_TEXT         = new Color(20, 20, 20);
    private static final Color CLR_TEXT_MUTED   = new Color(100, 100, 100);
    private static final Color CLR_LABEL        = new Color(60, 60, 60);
    private static final Color CLR_RED          = new Color(200, 0, 0);
    private static final Color CLR_ORANGE       = new Color(210, 100, 0);
    private static final Color CLR_GREEN        = new Color(0, 140, 0);
    private static final Color CLR_BLUE         = new Color(0, 90, 180);
    private static final Color CLR_CRITICAL     = new Color(200, 0, 0);
    private static final Color CLR_WARNING      = new Color(210, 100, 0);
    private static final Color CLR_INFO         = new Color(0, 90, 180);
    private static final Color CLR_CHART_BG     = Color.WHITE;
    private static final Color CLR_GRID         = new Color(220, 220, 220);
    private static final Color CLR_THRESHOLD    = new Color(200, 0, 0);
    private static final Color CLR_LINE_NORMAL  = new Color(180, 0, 0);   // червона крива (як на скріні)
    private static final Color CLR_LINE_ALARM   = new Color(220, 0, 0);
    private static final Color CLR_STATUS_OK    = new Color(0, 140, 0);
    private static final Color CLR_STATUS_ERR   = new Color(200, 0, 0);

    // =========================================================================
    // СТАН ПРОГРАМИ
    // =========================================================================

    private final List<Double> dcHistory  = new ArrayList<>();
    private final List<Long>   timeHistory = new ArrayList<>();  // мітки часу (мс)

    private double  currentDC   = 6.8;
    private double  currentHR   = 90.0;
    private double  currentSBP  = 120.0;  // систолічний тиск
    private double  currentDBP  = 80.0;   // діастолічний тиск
    private double  currentSpO2 = 97.0;
    private double  currentRR   = 18.0;   // частота дихання
    private double  currentTemp = 36.6;

    private int     tickCount        = 0;
    private boolean isAlarm          = false;
    private boolean treadmillStopped = false;
    private boolean fallStarted      = false;
    private double  fallSpeed        = 0.0;
    private long    sessionStart     = System.currentTimeMillis();

    private final Random random = new Random(42);

    // =========================================================================
    // UI КОМПОНЕНТИ
    // =========================================================================

    private JFrame            mainFrame;
    private DCChartPanel      chartPanel;
    private JLabel            lblHR, lblSBP, lblDBP, lblSpO2, lblRR, lblTemp;
    private JLabel            lblStatusText;
    private JLabel            lblStatusBar;
    private JLabel            lblTimeBar;
    private DefaultTableModel logTableModel;
    private Timer             simulationTimer;

    // =========================================================================
    // ТОЧКА ВХОДУ
    // =========================================================================

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                // Системний L&F — щоб вікна виглядали нативно
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            new SyncopePredictor().launch();
        });
    }

    // =========================================================================
    // ІНІЦІАЛІЗАЦІЯ ГОЛОВНОГО ВІКНА
    // =========================================================================

    private void launch() {
        mainFrame = new JFrame("SyncopePredictor v1.2  —  Моніторинг пацієнта");
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.setSize(1100, 780);
        mainFrame.setMinimumSize(new Dimension(950, 680));
        mainFrame.setLocationRelativeTo(null);
        mainFrame.setBackground(CLR_BG);
        mainFrame.setLayout(new BorderLayout(0, 0));

        mainFrame.setJMenuBar(buildMenuBar());

        // Головний вміст: ліва панель + правий контент
        JPanel contentPane = new JPanel(new BorderLayout(4, 4));
        contentPane.setBackground(CLR_BG);
        contentPane.setBorder(BorderFactory.createEmptyBorder(4, 4, 0, 4));

        contentPane.add(buildLeftPanel(),   BorderLayout.WEST);
        contentPane.add(buildRightPanel(),  BorderLayout.CENTER);

        mainFrame.add(contentPane,        BorderLayout.CENTER);
        mainFrame.add(buildStatusBar(),   BorderLayout.SOUTH);

        mainFrame.setVisible(true);
        startDemoMode();
    }

    // =========================================================================
    // MENU BAR
    // =========================================================================

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();

        // ── Файл ──
        JMenu mFile = new JMenu("Файл");
        mFile.add(menuItem("Новий сеанс",         e -> resetSimulation()));
        mFile.add(menuItem("Відкрити запис...",    e -> infoMsg("Завантаження архіву буде доступне у версії 2.0")));
        mFile.addSeparator();
        mFile.add(menuItem("Зберегти звіт PDF",   e -> showExportDialog()));
        mFile.addSeparator();
        mFile.add(menuItem("Вихід",                e -> confirmExit()));
        bar.add(mFile);

        // ── Налаштування ──
        JMenu mSet = new JMenu("Налаштування");
        mSet.add(menuItem("Параметри алгоритму PRSA", e -> showPRSASettings()));
        mSet.add(menuItem("Профіль пацієнта",          e -> showPatientProfile()));
        mSet.addSeparator();
        mSet.add(menuItem("Звукові сповіщення",        e -> infoMsg("Звукові сповіщення: УВІМКНЕНО")));
        bar.add(mSet);

        // ── Інструменти ──
        JMenu mTools = new JMenu("Інструменти");
        mTools.add(menuItem("Аналіз HRV",              e -> showHRVAnalysis()));
        mTools.add(menuItem("Калібрування датчика",    e -> showSensorStatus()));
        mTools.addSeparator();
        mTools.add(menuItem("Перезапуск демо",         e -> resetSimulation()));
        mTools.add(menuItem("Про програму",            e -> showAbout()));
        bar.add(mTools);

        // ── Довідка ──
        JMenu mHelp = new JMenu("Довідка");
        mHelp.add(menuItem("Онлайн-документація", e -> infoMsg("Документація: docs.syncope-predictor.ua")));
        mHelp.add(menuItem("Про програму",        e -> showAbout()));
        bar.add(mHelp);

        return bar;
    }

    private JMenuItem menuItem(String title, ActionListener a) {
        JMenuItem item = new JMenuItem(title);
        item.addActionListener(a);
        return item;
    }

    // =========================================================================
    // ЛІВА ПАНЕЛЬ — інформація про пацієнта + поточні показники
    // =========================================================================

    private JPanel buildLeftPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(CLR_BG);
        panel.setPreferredSize(new Dimension(220, 0));

        panel.add(buildPatientInfoBlock());
        panel.add(buildVSpacer(4));
        panel.add(buildVitalsBlock());
        panel.add(buildVSpacer(4));
        // Розтяжний простір знизу
        JPanel filler = new JPanel();
        filler.setBackground(CLR_BG);
        filler.setMaximumSize(new Dimension(220, Integer.MAX_VALUE));
        panel.add(filler);

        return panel;
    }

    /** Блок "Інформація про пацієнта" */
    private JPanel buildPatientInfoBlock() {
        JPanel block = new JPanel(new GridBagLayout());
        block.setBackground(CLR_WHITE);
        block.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CLR_BORDER, 1),
                BorderFactory.createEmptyBorder(0, 0, 6, 0)
        ));
        block.setMaximumSize(new Dimension(220, Integer.MAX_VALUE));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.gridx = 0; gbc.gridy = 0;
        gbc.insets = new Insets(0, 0, 0, 0);

        // Заголовок блоку
        JLabel header = new JLabel("  Інформація про пацієнта");
        header.setFont(new Font("Dialog", Font.BOLD, 12));
        header.setForeground(CLR_TEXT);
        header.setBackground(CLR_HEADER_BG);
        header.setOpaque(true);
        header.setPreferredSize(new Dimension(220, 24));
        block.add(header, gbc);

        // Роздільник
        gbc.gridy++; gbc.insets = new Insets(0, 0, 2, 0);
        block.add(new JSeparator(), gbc);

        // Дані пацієнта
        String[][] data = {
                {"ID пацієнта:",   "P-2025-05-18-001"},
                {"ПІБ:",           "Іваненко Іван Іванович"},
                {"Вік:",           "58"},
                {"Стать:",         "Чоловіча"},
                {"Вага:",          "82 кг"},
                {"Зріст:",         "175 см"},
                {"Дата народж.:",  "12.04.1967"},
                {"Діагноз:",       "Гіпертонія, ІХС"},
        };

        for (String[] row : data) {
            gbc.gridy++;
            gbc.insets = new Insets(1, 6, 1, 4);
            JPanel rowPanel = new JPanel(new BorderLayout(4, 0));
            rowPanel.setBackground(CLR_WHITE);

            JLabel lKey = new JLabel(row[0]);
            lKey.setFont(new Font("Dialog", Font.PLAIN, 11));
            lKey.setForeground(CLR_TEXT_MUTED);
            lKey.setPreferredSize(new Dimension(90, 16));

            JLabel lVal = new JLabel(row[1]);
            lVal.setFont(new Font("Dialog", Font.PLAIN, 11));
            lVal.setForeground(CLR_TEXT);

            rowPanel.add(lKey, BorderLayout.WEST);
            rowPanel.add(lVal, BorderLayout.CENTER);
            block.add(rowPanel, gbc);
        }

        return block;
    }

    /** Блок "Поточні показники" (вітальні знаки) */
    private JPanel buildVitalsBlock() {
        JPanel block = new JPanel(new GridBagLayout());
        block.setBackground(CLR_WHITE);
        block.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CLR_BORDER, 1),
                BorderFactory.createEmptyBorder(0, 0, 6, 0)
        ));
        block.setMaximumSize(new Dimension(220, Integer.MAX_VALUE));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.gridx = 0; gbc.gridy = 0;
        gbc.insets = new Insets(0, 0, 0, 0);

        // Заголовок
        JLabel header = new JLabel("  Поточні показники");
        header.setFont(new Font("Dialog", Font.BOLD, 12));
        header.setForeground(CLR_TEXT);
        header.setBackground(CLR_HEADER_BG);
        header.setOpaque(true);
        header.setPreferredSize(new Dimension(220, 24));
        block.add(header, gbc);

        gbc.gridy++; gbc.insets = new Insets(0, 0, 2, 0);
        block.add(new JSeparator(), gbc);

        // Рядки показників
        gbc.insets = new Insets(2, 6, 2, 6);

        lblHR   = addVitalRow(block, gbc, "ЧСС:",                "—",   "уд/хв", CLR_RED);
        lblSBP  = addVitalRow(block, gbc, "Артеріальний тиск:",  "—",   "мм рт. ст.", CLR_RED);
        lblSpO2 = addVitalRow(block, gbc, "SpO2:",               "—",   "%",     CLR_ORANGE);
        lblRR   = addVitalRow(block, gbc, "Частота дихання:",    "—",   "/хв",   CLR_TEXT);
        lblTemp = addVitalRow(block, gbc, "Температура тіла:",   "—",   "°C",    CLR_TEXT);

        return block;
    }

    /**
     * Додає рядок вітального знаку до панелі.
     * Повертає JLabel значення для подальшого оновлення.
     */
    private JLabel addVitalRow(JPanel parent, GridBagConstraints gbc,
                               String keyText, String initVal,
                               String unit, Color valColor) {
        gbc.gridy++;
        JPanel row = new JPanel(new GridBagLayout());
        row.setBackground(CLR_WHITE);

        GridBagConstraints r = new GridBagConstraints();
        r.fill = GridBagConstraints.HORIZONTAL;

        // Назва показника
        r.gridx = 0; r.weightx = 0;
        r.insets = new Insets(0, 0, 0, 4);
        JLabel lKey = new JLabel(keyText);
        lKey.setFont(new Font("Dialog", Font.PLAIN, 11));
        lKey.setForeground(CLR_TEXT_MUTED);
        lKey.setPreferredSize(new Dimension(110, 18));
        row.add(lKey, r);

        // Значення
        r.gridx = 1; r.weightx = 1.0;
        JLabel lVal = new JLabel(initVal);
        lVal.setFont(new Font("Dialog", Font.BOLD, 13));
        lVal.setForeground(valColor);
        row.add(lVal, r);

        // Одиниця
        r.gridx = 2; r.weightx = 0;
        JLabel lUnit = new JLabel(unit);
        lUnit.setFont(new Font("Dialog", Font.PLAIN, 10));
        lUnit.setForeground(CLR_TEXT_MUTED);
        row.add(lUnit, r);

        parent.add(row, gbc);
        return lVal;
    }

    // =========================================================================
    // ПРАВА ПАНЕЛЬ — графік + статус + кнопки + журнал
    // =========================================================================

    private JPanel buildRightPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setBackground(CLR_BG);

        // Верхня частина: графік + статус + кнопки
        JPanel topArea = new JPanel(new BorderLayout(0, 4));
        topArea.setBackground(CLR_BG);

        topArea.add(buildChartBlock(),   BorderLayout.CENTER);
        topArea.add(buildStatusBlock(),  BorderLayout.SOUTH);

        panel.add(topArea,            BorderLayout.CENTER);
        panel.add(buildLogBlock(),    BorderLayout.SOUTH);

        return panel;
    }

    /** Блок з графіком DC */
    private JPanel buildChartBlock() {
        JPanel block = new JPanel(new BorderLayout());
        block.setBackground(CLR_WHITE);
        block.setBorder(BorderFactory.createLineBorder(CLR_BORDER, 1));

        // Заголовок графіка
        JLabel title = new JLabel("  Моніторинг DC у реальному часі", SwingConstants.CENTER);
        title.setFont(new Font("Dialog", Font.PLAIN, 13));
        title.setForeground(CLR_TEXT);
        title.setBackground(CLR_WHITE);
        title.setOpaque(true);
        title.setPreferredSize(new Dimension(0, 26));
        block.add(title, BorderLayout.NORTH);

        // Сам графік
        chartPanel = new DCChartPanel();
        chartPanel.setPreferredSize(new Dimension(0, 300));
        block.add(chartPanel, BorderLayout.CENTER);

        return block;
    }

    /** Блок статусу + кнопки управління */
    private JPanel buildStatusBlock() {
        JPanel block = new JPanel(new BorderLayout(0, 0));
        block.setBackground(CLR_WHITE);
        block.setBorder(BorderFactory.createLineBorder(CLR_BORDER, 1));

        // Рядок статусу
        lblStatusText = new JLabel("СТАН: НОРМА", SwingConstants.CENTER);
        lblStatusText.setFont(new Font("Dialog", Font.BOLD, 14));
        lblStatusText.setForeground(CLR_GREEN);
        lblStatusText.setPreferredSize(new Dimension(0, 30));
        block.add(lblStatusText, BorderLayout.NORTH);

        // Кнопки
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 6));
        btnRow.setBackground(CLR_WHITE);

        JButton btnStart = createBtn("Почати моніторинг");
        btnStart.addActionListener(e -> resetSimulation());

        JButton btnExport = createBtn("Експорт даних");
        btnExport.addActionListener(e -> showExportDialog());

        JButton btnStop = createBtn("Зупинка тредміла");
        btnStop.addActionListener(e -> onStopTreadmill());

        btnRow.add(btnStart);
        btnRow.add(btnExport);
        btnRow.add(btnStop);
        block.add(btnRow, BorderLayout.CENTER);

        return block;
    }

    private JButton createBtn(String text) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Dialog", Font.PLAIN, 12));
        btn.setPreferredSize(new Dimension(180, 28));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    /** Блок журналу подій */
    private JPanel buildLogBlock() {
        JPanel block = new JPanel(new BorderLayout());
        block.setBackground(CLR_WHITE);
        block.setBorder(BorderFactory.createLineBorder(CLR_BORDER, 1));
        block.setPreferredSize(new Dimension(0, 190));

        // Заголовок
        JLabel header = new JLabel("  Журнал подій");
        header.setFont(new Font("Dialog", Font.BOLD, 12));
        header.setBackground(CLR_HEADER_BG);
        header.setOpaque(true);
        header.setPreferredSize(new Dimension(0, 24));
        block.add(header, BorderLayout.NORTH);
        block.add(new JSeparator(), BorderLayout.AFTER_LAST_LINE); // не стандарт, замість цього:

        // Таблиця журналу
        String[] cols = {"Час", "Рівень", "Повідомлення"};
        logTableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        JTable logTable = new JTable(logTableModel);
        logTable.setFont(new Font("Dialog", Font.PLAIN, 11));
        logTable.setRowHeight(18);
        logTable.getTableHeader().setFont(new Font("Dialog", Font.BOLD, 11));
        logTable.getColumnModel().getColumn(0).setPreferredWidth(70);
        logTable.getColumnModel().getColumn(1).setPreferredWidth(100);
        logTable.getColumnModel().getColumn(2).setPreferredWidth(500);

        // Кастомний рендерер — колір рядка залежить від рівня
        logTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val,
                                                           boolean sel, boolean foc, int row, int col) {
                super.getTableCellRendererComponent(t, val, sel, foc, row, col);
                String level = (String) t.getValueAt(row, 1);
                if ("КРИТИЧНИЙ".equals(level))    setForeground(CLR_CRITICAL);
                else if ("ПОПЕРЕДЖЕННЯ".equals(level)) setForeground(CLR_WARNING);
                else if ("ІНФОРМАЦІЯ".equals(level))   setForeground(CLR_INFO);
                else                                    setForeground(CLR_TEXT);
                if (sel) setBackground(new Color(210, 225, 245));
                else     setBackground(row % 2 == 0 ? CLR_WHITE : new Color(248, 248, 248));
                return this;
            }
        });

        JScrollPane scroll = new JScrollPane(logTable);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        block.add(scroll, BorderLayout.CENTER);

        // Початковий запис у журнал
        addLogEntry("ІНФОРМАЦІЯ", "Пристрій підключено: SmartCardio v2.0 на COM3");
        addLogEntry("ІНФОРМАЦІЯ", "Моніторинг розпочато");

        return block;
    }

    // =========================================================================
    // СТАТУС-БАР (нижній)
    // =========================================================================

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(CLR_HEADER_BG);
        bar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, CLR_BORDER));
        bar.setPreferredSize(new Dimension(0, 22));

        // Ліво: статус підключення
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        left.setOpaque(false);
        JLabel lConnLabel = new JLabel("Статус:");
        lConnLabel.setFont(new Font("Dialog", Font.PLAIN, 11));
        lConnLabel.setForeground(CLR_TEXT_MUTED);
        lblStatusBar = new JLabel("Підключено");
        lblStatusBar.setFont(new Font("Dialog", Font.BOLD, 11));
        lblStatusBar.setForeground(CLR_STATUS_OK);
        left.add(lConnLabel);
        left.add(lblStatusBar);

        // Центр: користувач
        JLabel lUser = new JLabel("Користувач: student_lab", SwingConstants.CENTER);
        lUser.setFont(new Font("Dialog", Font.PLAIN, 11));
        lUser.setForeground(CLR_TEXT_MUTED);

        // Право: час
        lblTimeBar = new JLabel("", SwingConstants.RIGHT);
        lblTimeBar.setFont(new Font("Dialog", Font.PLAIN, 11));
        lblTimeBar.setForeground(CLR_TEXT_MUTED);
        lblTimeBar.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 8));

        // Оновлення годинника
        Timer clock = new Timer(1000, e ->
                lblTimeBar.setText(new SimpleDateFormat("dd.MM.yyyy  HH:mm:ss").format(new Date())));
        clock.start();
        lblTimeBar.setText(new SimpleDateFormat("dd.MM.yyyy  HH:mm:ss").format(new Date()));

        bar.add(left,   BorderLayout.WEST);
        bar.add(lUser,  BorderLayout.CENTER);
        bar.add(lblTimeBar, BorderLayout.EAST);
        return bar;
    }

    // =========================================================================
    // ДОПОМІЖНІ UI
    // =========================================================================

    private JPanel buildVSpacer(int h) {
        JPanel s = new JPanel();
        s.setBackground(CLR_BG);
        s.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
        s.setPreferredSize(new Dimension(0, h));
        return s;
    }

    // =========================================================================
    // ЖУРНАЛ ПОДІЙ
    // =========================================================================

    private void addLogEntry(String level, String message) {
        String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
        // Вставляємо на початок — нові записи зверху
        logTableModel.insertRow(0, new Object[]{time, level, message});
        // Обмежуємо журнал 200 записами
        if (logTableModel.getRowCount() > 200) {
            logTableModel.removeRow(logTableModel.getRowCount() - 1);
        }
    }

    // =========================================================================
    // ЛОГІКА СИМУЛЯЦІЇ
    // =========================================================================

    private void startDemoMode() {
        currentDC   = 6.8 + random.nextDouble() * 0.4;
        currentHR   = 85.0;
        currentSBP  = 120.0;
        currentDBP  = 80.0;
        currentSpO2 = 97.0;
        currentRR   = 17.0;
        currentTemp = 36.6;
        tickCount   = 0;
        isAlarm     = false;
        treadmillStopped = false;
        fallStarted = false;
        fallSpeed   = 0.0;
        sessionStart = System.currentTimeMillis();
        dcHistory.clear();
        timeHistory.clear();

        simulationTimer = new Timer(TIMER_MS, e -> simulationTick());
        simulationTimer.start();
    }

    /**
     * Один тік симуляції — імітує надходження нових даних з датчика
     * та перераховує DC за спрощеним PRSA-алгоритмом.
     */
    private void simulationTick() {
        tickCount++;

        // ── ЧСС ──
        double hrTarget = 100 + 20 * Math.sin(tickCount * 0.08) + random.nextGaussian() * 2;
        hrTarget = Math.max(78, Math.min(132, hrTarget));
        currentHR = currentHR * 0.85 + hrTarget * 0.15;

        // ── Артеріальний тиск (реагує на ЧСС) ──
        currentSBP = 115 + (currentHR - 80) * 0.6 + random.nextGaussian() * 2;
        currentDBP = 75  + (currentHR - 80) * 0.3 + random.nextGaussian() * 1.5;

        // ── SpO2 ──
        currentSpO2 = 97.0 - (isAlarm ? 3.0 : 0) + random.nextGaussian() * 0.3;
        currentSpO2 = Math.max(88, Math.min(100, currentSpO2));

        // ── Частота дихання ──
        currentRR = 17 + (isAlarm ? 4 : 0) + random.nextGaussian() * 0.5;
        currentRR = Math.max(10, Math.min(30, currentRR));

        // ── DC (PRSA) ──
        if (treadmillStopped) {
            // Режим відновлення
            double target = DC_NORMAL_MIN + random.nextDouble() * (DC_NORMAL_MAX - DC_NORMAL_MIN);
            currentDC = currentDC * 0.92 + target * 0.08;
            if (currentDC >= DC_THRESHOLD && isAlarm) {
                isAlarm = false;
                updateAlarm(false);
                addLogEntry("ІНФОРМАЦІЯ", String.format(
                        "DC відновився до %.2f мс. Стан нормалізовано.", currentDC));
            }
        } else if (!fallStarted) {
            // Нормальний режим
            double noise = random.nextGaussian() * 0.12;
            double drift = Math.sin(tickCount * 0.05) * 0.15;
            currentDC = 6.85 + drift + noise;
            currentDC = Math.max(6.3, Math.min(7.5, currentDC));

            if (tickCount >= FALL_TICK) {
                fallStarted = true;
                fallSpeed = 0.20 + random.nextDouble() * 0.08;
                addLogEntry("ПОПЕРЕДЖЕННЯ", "DC починає знижуватись. Спостерігайте за пацієнтом.");
            }
        } else {
            // Падіння DC
            currentDC -= fallSpeed + random.nextGaussian() * 0.04;
            currentDC = Math.max(1.5, currentDC);

            // Перевищення ЧСС
            if (currentHR > 120 && !isAlarm) {
                addLogEntry("КРИТИЧНИЙ", String.format(
                        "ЧСС = %.0f уд/хв (перевищення критичного рівня!)", currentHR));
            }
            // Гіпертонія
            if (currentSBP > 160 && !isAlarm) {
                addLogEntry("КРИТИЧНИЙ", String.format(
                        "Артеріальний тиск = %.0f/%.0f мм рт. ст. (гіпертензія!)",
                        currentSBP, currentDBP));
            }
            // SpO2 низький
            if (currentSpO2 < 93 && !isAlarm) {
                addLogEntry("ПОПЕРЕДЖЕННЯ", String.format(
                        "SpO2 = %.0f%% (нижче норми)", currentSpO2));
            }

            // Тривога DC
            if (currentDC < DC_THRESHOLD && !isAlarm) {
                isAlarm = true;
                updateAlarm(true);
                addLogEntry("КРИТИЧНИЙ", String.format(
                        "DC = %.2f мс — нижче порогу %.1f мс! РИЗИК СИНКОПЕ!", currentDC, DC_THRESHOLD));
                Toolkit.getDefaultToolkit().beep();
            }
        }

        // Попередження ЧСС (регулярно)
        if (currentHR > 118 && tickCount % 10 == 0) {
            addLogEntry("ПОПЕРЕДЖЕННЯ", String.format(
                    "ЧСС = %.0f уд/хв (вище норми)", currentHR));
        }

        // Запис в буфер графіка
        dcHistory.add(currentDC);
        timeHistory.add(System.currentTimeMillis() - sessionStart);
        if (dcHistory.size() > MAX_POINTS) {
            dcHistory.remove(0);
            timeHistory.remove(0);
        }

        // Оновлення UI
        updateVitals();
        chartPanel.repaint();
    }

    /** Оновлює мітки вітальних знаків */
    private void updateVitals() {
        // ЧСС
        lblHR.setText(String.format("%.0f", currentHR));
        lblHR.setForeground(currentHR > 120 ? CLR_CRITICAL : CLR_RED);

        // Тиск
        lblSBP.setText(String.format("%.0f / %.0f", currentSBP, currentDBP));
        lblSBP.setForeground(currentSBP > 160 ? CLR_CRITICAL : CLR_RED);

        // SpO2
        lblSpO2.setText(String.format("%.0f", currentSpO2));
        lblSpO2.setForeground(currentSpO2 < 93 ? CLR_CRITICAL : CLR_ORANGE);

        // Дихання
        lblRR.setText(String.format("%.0f", currentRR));
        lblRR.setForeground(currentRR > 24 ? CLR_ORANGE : CLR_TEXT);

        // Температура
        lblTemp.setText(String.format("%.1f", currentTemp));
        lblTemp.setForeground(CLR_TEXT);
    }

    /** Перемикає стан тривоги в UI */
    private void updateAlarm(boolean alarm) {
        if (alarm) {
            lblStatusText.setText("СТАН: КРИТИЧНИЙ! Ризик синкопе (DC < 4.5 мс)");
            lblStatusText.setForeground(CLR_CRITICAL);
            lblStatusBar.setText("ТРИВОГА");
            lblStatusBar.setForeground(CLR_CRITICAL);
            mainFrame.setTitle("⚠ ТРИВОГА!  SyncopePredictor v1.2  —  КРИТИЧНИЙ РИЗИК СИНКОПЕ");
        } else {
            lblStatusText.setText("СТАН: НОРМА");
            lblStatusText.setForeground(CLR_GREEN);
            lblStatusBar.setText("Підключено");
            lblStatusBar.setForeground(CLR_STATUS_OK);
            mainFrame.setTitle("SyncopePredictor v1.2  —  Моніторинг пацієнта");
        }
    }

    // =========================================================================
    // ОБРОБНИКИ КНОПОК
    // =========================================================================

    private void onStopTreadmill() {
        if (!treadmillStopped) {
            treadmillStopped = true;
            addLogEntry("ІНФОРМАЦІЯ", "Тредміл зупинено. Розпочато режим відновлення.");
            JOptionPane.showMessageDialog(mainFrame,
                    "Команду зупинки тредміла надіслано!\nDC починає повертатись до норми.",
                    "Тредміл зупинено", JOptionPane.WARNING_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(mainFrame,
                    "Тредміл вже зупинений.\nПацієнт у режимі відновлення.",
                    "Інформація", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void showExportDialog() {
        String fname = "syncope_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".csv";
        addLogEntry("ІНФОРМАЦІЯ", "Дані експортовано у файл: " + fname);
        JOptionPane.showMessageDialog(mainFrame,
                "✔  Дані успішно збережено!\n\n" +
                        "Файл:      " + fname + "\n" +
                        "Записів:   " + dcHistory.size() + " точок\n" +
                        "Формат:    CSV (роздільник «;»)\n" +
                        "Колонки:   Час (мс), DC (мс), ЧСС, SpO2, АТ сис., АТ діас.\n\n" +
                        "Шлях:      ~/Documents/SyncopePredictor/",
                "Експорт завершено", JOptionPane.INFORMATION_MESSAGE);
    }

    private void resetSimulation() {
        if (simulationTimer != null) simulationTimer.stop();
        isAlarm = false;
        updateAlarm(false);
        addLogEntry("ІНФОРМАЦІЯ", "Новий сеанс моніторингу розпочато.");
        startDemoMode();
    }

    private void confirmExit() {
        int r = JOptionPane.showConfirmDialog(mainFrame,
                "Завершити сеанс моніторингу та вийти?",
                "Підтвердження", JOptionPane.YES_NO_OPTION);
        if (r == JOptionPane.YES_OPTION) System.exit(0);
    }

    // =========================================================================
    // ДІАЛОГОВІ ВІКНА МЕНЮ
    // =========================================================================

    private void infoMsg(String msg) {
        JOptionPane.showMessageDialog(mainFrame, msg, "Інформація", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showPRSASettings() {
        infoMsg(
                "Параметри PRSA-алгоритму:\n" +
                        "────────────────────────────────\n" +
                        "Вікно усереднення (L):     2\n" +
                        "Шкала якоря (T):           ±2 відліки\n" +
                        "Мін. довжина RR:           300 мс\n" +
                        "Порогове значення DC:      4.5 мс\n" +
                        "Частота дискретизації:     1000 Гц"
        );
    }

    private void showPatientProfile() {
        infoMsg(
                "Пацієнт:    Іваненко Іван Іванович\n" +
                        "Вік:        58 років\n" +
                        "Датчик:     SmartCardio v2.0 (COM3)\n" +
                        "Протокол:   Брюс (модифікований)\n" +
                        "ID сесії:   P-2025-05-18-001"
        );
    }

    private void showHRVAnalysis() {
        infoMsg(
                "HRV-аналіз поточної сесії:\n" +
                        "────────────────────────────────\n" +
                        String.format("SDNN:       %.1f мс%n",  35.0 + random.nextDouble() * 20) +
                        String.format("RMSSD:      %.1f мс%n",  28.0 + random.nextDouble() * 15) +
                        String.format("pNN50:      %.1f%%%n",   12.0 + random.nextDouble() * 10) +
                        String.format("LF/HF:      %.2f%n",      1.2 + random.nextDouble() * 0.8) +
                        String.format("DC (PRSA):  %.2f мс",   currentDC)
        );
    }

    private void showSensorStatus() {
        infoMsg(
                "Датчик SmartCardio v2.0:\n" +
                        "Порт:              COM3\n" +
                        "Статус:            Підключено\n" +
                        "Якість сигналу:    98%\n" +
                        String.format("Останній R-R:      %.0f мс", 60000.0 / currentHR)
        );
    }

    private void showAbout() {
        infoMsg(
                "SyncopePredictor v1.2\n" +
                        "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                        "Система раннього прогнозування\n" +
                        "вазовагального синкопе\n\n" +
                        "Алгоритм: PRSA (Phase-Rectified\n" +
                        "          Signal Averaging)\n" +
                        "Показник: DC (Deceleration Capacity)\n\n" +
                        "© 2024  Дипломна робота\n" +
                        "Java SE 8 | Swing UI  (без залежностей)"
        );
    }

    // =========================================================================
    // ВНУТРІШНІЙ КЛАС: Кастомний графік DC
    // =========================================================================

    /**
     * DCChartPanel — малює лінійний графік DC у реальному часі.
     * Використовує лише стандартний Graphics2D без сторонніх бібліотек.
     */
    private class DCChartPanel extends JPanel {

        // Відступи полів графіка
        private final int PAD_L = 55;
        private final int PAD_R = 16;
        private final int PAD_T = 16;
        private final int PAD_B = 40;

        // Діапазон осі Y
        private final double Y_MIN = 0.0;
        private final double Y_MAX = 110.0; // відображаємо 0–110 мс (як на скріні)

        DCChartPanel() {
            setBackground(CLR_CHART_BG);
            setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int W = getWidth(), H = getHeight();
            int cW = W - PAD_L - PAD_R;
            int cH = H - PAD_T - PAD_B;

            drawGrid(g2, cW, cH);
            drawThreshold(g2, cW, cH);
            drawLine(g2, cW, cH);
            drawAxes(g2, W, H, cW, cH);
        }

        /** Малює фонову сітку */
        private void drawGrid(Graphics2D g2, int cW, int cH) {
            g2.setColor(CLR_GRID);
            g2.setStroke(new BasicStroke(0.6f));

            // Горизонтальні лінії: 0, 10, 20 ... 110
            for (int yv = 0; yv <= (int) Y_MAX; yv += 10) {
                int y = PAD_T + (int) ((Y_MAX - yv) / (Y_MAX - Y_MIN) * cH);
                g2.drawLine(PAD_L, y, PAD_L + cW, y);
            }
            // Вертикальні лінії (12 рівних стовпців)
            for (int i = 0; i <= 12; i++) {
                int x = PAD_L + (int) ((double) i / 12 * cW);
                g2.drawLine(x, PAD_T, x, PAD_T + cH);
            }
        }

        /**
         * Малює червону пунктирну горизонтальну лінію порогу.
         * На скріні поріг DC = 4.5 мс, але вісь Y іде від 0 до 110,
         * тому лінія на графіку знаходиться дуже низько.
         * Для читабельності демонстрації малюємо поріг на рівні 80 мс
         * (як на скріні MedMonitor — там лінія проходить приблизно на 80).
         * Реальну мітку "Threshold = 4.5" підписуємо поряд.
         */
        private void drawThreshold(Graphics2D g2, int cW, int cH) {
            // Порогова лінія на рівні 80 одиниць по осі Y (візуально)
            double thresholdVisual = 80.0;
            int yT = PAD_T + (int) ((Y_MAX - thresholdVisual) / (Y_MAX - Y_MIN) * cH);

            g2.setColor(CLR_THRESHOLD);
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    1f, new float[]{7, 4}, 0));
            g2.drawLine(PAD_L, yT, PAD_L + cW, yT);
        }

        /** Малює лінію кривої DC */
        private void drawLine(Graphics2D g2, int cW, int cH) {
            if (dcHistory.size() < 2) return;

            int n = dcHistory.size();
            double xStep = (double) cW / MAX_POINTS;
            int startX = PAD_L + (int) ((MAX_POINTS - n) * xStep);

            // Будуємо шлях кривої
            // Маштабуємо DC (0–10 мс) у діапазон осі Y (0–110):
            // dc_visual = dc * 10  (щоб крива займала верхню частину графіка)
            GeneralPath path = new GeneralPath();
            for (int i = 0; i < n; i++) {
                int x = startX + (int) (i * xStep);
                double dcScaled = dcHistory.get(i) * 10.0; // мс → візуальна шкала
                int y = PAD_T + (int) ((Y_MAX - dcScaled) / (Y_MAX - Y_MIN) * cH);
                if (i == 0) path.moveTo(x, y);
                else        path.lineTo(x, y);
            }

            // Крива — червона, як на скріні
            Color lineColor = isAlarm ? CLR_LINE_ALARM : CLR_LINE_NORMAL;
            g2.setColor(lineColor);
            g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(path);

            // Остання точка
            int lx = startX + (int) ((n - 1) * xStep);
            double lDCScaled = dcHistory.get(n - 1) * 10.0;
            int ly = PAD_T + (int) ((Y_MAX - lDCScaled) / (Y_MAX - Y_MIN) * cH);
            g2.setColor(lineColor);
            g2.fillOval(lx - 3, ly - 3, 6, 6);
        }

        /** Малює осі, мітки та мітки часу */
        private void drawAxes(Graphics2D g2, int W, int H, int cW, int cH) {
            g2.setColor(CLR_TEXT_MUTED);
            g2.setStroke(new BasicStroke(1f));

            // Вісь Y
            g2.drawLine(PAD_L, PAD_T, PAD_L, PAD_T + cH);
            // Вісь X
            g2.drawLine(PAD_L, PAD_T + cH, PAD_L + cW, PAD_T + cH);

            // Підписи осі Y (0, 10, 20 ... 110)
            g2.setFont(new Font("Dialog", Font.PLAIN, 10));
            g2.setColor(CLR_TEXT_MUTED);
            for (int yv = 0; yv <= (int) Y_MAX; yv += 10) {
                int y = PAD_T + (int) ((Y_MAX - yv) / (Y_MAX - Y_MIN) * cH);
                g2.drawString(String.valueOf(yv), PAD_L - 28, y + 4);
                g2.drawLine(PAD_L - 3, y, PAD_L, y);
            }

            // Підпис осі Y (вертикальний)
            AffineTransform orig = g2.getTransform();
            g2.translate(12, PAD_T + cH / 2 + 30);
            g2.rotate(-Math.PI / 2);
            g2.setFont(new Font("Dialog", Font.PLAIN, 10));
            g2.setColor(CLR_TEXT_MUTED);
            g2.drawString("Значення DC (мс)", 0, 0);
            g2.setTransform(orig);

            // Підписи осі X — імітація поточного часу
            g2.setFont(new Font("Dialog", Font.PLAIN, 10));
            g2.setColor(CLR_TEXT_MUTED);
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
            long now = System.currentTimeMillis();
            int nLabels = 7;
            for (int i = 0; i <= nLabels; i++) {
                int x = PAD_L + (int) ((double) i / nLabels * cW);
                long labelTime = now - (long) ((nLabels - i) * TIMER_MS * MAX_POINTS / nLabels);
                String ts = sdf.format(new Date(labelTime));
                g2.drawLine(x, PAD_T + cH, x, PAD_T + cH + 4);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(ts, x - fm.stringWidth(ts) / 2, PAD_T + cH + 14);
            }

            // Підпис осі X
            g2.setColor(CLR_TEXT_MUTED);
            g2.drawString("Час (с)", PAD_L + cW / 2 - 15, H - 4);
        }
    }

}
