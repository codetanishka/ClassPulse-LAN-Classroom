package classpulse.ui;

import classpulse.brand.Brand;
import classpulse.model.Announcement;
import classpulse.model.AnswerReceipt;
import classpulse.model.JoinInfo;
import classpulse.model.Message;
import classpulse.model.NoteData;
import classpulse.model.NoteSummary;
import classpulse.model.QuizResult;
import classpulse.model.QuizView;
import classpulse.net.ClassroomClient;
import classpulse.net.ClientListener;
import classpulse.ui.component.Avatar;
import classpulse.ui.component.MiniBar;
import classpulse.ui.component.OptionButton;
import classpulse.ui.component.PillLabel;
import classpulse.ui.component.RoundedPanel;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Student console. The same frame holds the join form and the live classroom,
 * so losing the connection can return the student to the join form safely.
 */
public final class StudentFrame extends JFrame {
    private static final String SETUP_CARD = "setup";
    private static final String LIVE_CARD = "live";

    private final LauncherFrame launcher;
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cardHost = new JPanel(cardLayout);

    private volatile ClassroomClient client;
    private volatile boolean live;
    private volatile boolean leaving;

    private JTextField nameField;
    private JTextField hostField;
    private JTextField portField;
    private JLabel setupError;
    private JButton connectButton;
    private JLabel setupStatus;

    private JLabel classLabel;
    private JLabel teacherLabel;
    private JLabel studentLabel;
    private JLabel connectionLabel;
    private JLabel sidebarClassName;
    private JLabel sidebarTeacherName;
    private JLabel sidebarStudentName;
    private JLabel sidebarAddress;
    private JLabel statusLabel;
    private PillLabel connectedPill;
    private JTabbedPane tabs;

    private JPanel announcementListPanel;
    private JLabel announcementCountLabel;
    private final List<Announcement> announcements = new ArrayList<>();

    private JPanel quizBodyHost;
    private JLabel quizTitle;
    private PillLabel quizBadge;
    private JLabel studentCountdownLabel;
    private JLabel studentAnswerStatus;
    private JButton submitAnswerButton;
    private final List<OptionButton> optionButtons = new ArrayList<>();
    private ButtonGroup optionGroup;
    private Timer countdownTimer;
    private QuizView currentQuiz;
    private QuizResult lastResult;
    private int selectedIndex = -1;
    private boolean answerSubmitted;

    private JPanel notesListPanel;
    private JLabel notesCountLabel;
    private final List<NoteSummary> notes = new ArrayList<>();
    private final Map<String, Path> pendingDownloads = new HashMap<>();

    private final ClientListener clientListener = new ClientListener() {
        @Override
        public void onConnected(JoinInfo info) {
            onEdt(() -> handleConnected(info));
        }

        @Override
        public void onAnnouncement(Announcement announcement) {
            onEdt(() -> {
                announcements.add(announcement);
                refreshAnnouncements();
            });
        }

        @Override
        public void onQuizStarted(QuizView quiz) {
            onEdt(() -> handleQuizStarted(quiz));
        }

        @Override
        public void onQuizResult(QuizResult result) {
            onEdt(() -> handleQuizResult(result));
        }

        @Override
        public void onNoteList(List<NoteSummary> newNotes) {
            onEdt(() -> {
                notes.clear();
                notes.addAll(newNotes);
                refreshNotes();
            });
        }

        @Override
        public void onNoteContent(NoteData note) {
            onEdt(() -> saveDownloadedNote(note));
        }

        @Override
        public void onAnswerReceipt(AnswerReceipt receipt) {
            onEdt(() -> handleAnswerReceipt(receipt));
        }

        @Override
        public void onError(String message) {
            onEdt(() -> {
                setStatus(message);
                if (live) {
                    Ui.showError(StudentFrame.this, "Classroom message", message);
                }
            });
        }

        @Override
        public void onDisconnected(String message) {
            onEdt(() -> handleDisconnected(message));
        }
    };

    public StudentFrame(LauncherFrame launcher) {
        super(Brand.NAME + " - Student");
        this.launcher = launcher;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(900, 620));
        setSize(1080, 720);
        setLocationRelativeTo(null);
        setIconImages(Ui.windowIcons());
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeStudentWindow();
            }
        });

        cardHost.setBackground(Brand.BG);
        cardHost.add(buildSetupCard(), SETUP_CARD);
        cardHost.add(buildLobby(), LIVE_CARD);
        setContentPane(cardHost);
        cardLayout.show(cardHost, SETUP_CARD);
    }

    // ---------------------------------------------------------------- setup

    private JPanel buildSetupCard() {
        JPanel page = Ui.page();
        page.setBorder(new EmptyBorder(24, 28, 24, 28));
        page.add(buildSetupTopBar(), BorderLayout.NORTH);

        JPanel center = Ui.transparent(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.fill = GridBagConstraints.BOTH;
        c.weighty = 1;
        c.insets = new Insets(8, 0, 8, 18);

        c.gridx = 0;
        c.weightx = 0.50;
        center.add(buildSetupInfoCard(), c);

        c.gridx = 1;
        c.weightx = 0.50;
        c.insets = new Insets(8, 0, 8, 0);
        center.add(buildSetupFormCard(), c);

        page.add(center, BorderLayout.CENTER);

        JPanel footer = Ui.transparent(new BorderLayout());
        footer.setBorder(new EmptyBorder(10, 2, 0, 2));
        JLabel note = new JLabel("Your teacher will give you the IP address and port. "
                + "Both computers must be on the same Wi-Fi or LAN.");
        note.setFont(Brand.font(Font.PLAIN, 10));
        note.setForeground(Brand.MUTED);
        footer.add(note, BorderLayout.WEST);
        page.add(footer, BorderLayout.SOUTH);
        return page;
    }

    private JPanel buildSetupTopBar() {
        JPanel bar = Ui.transparent(new BorderLayout());
        bar.setBorder(new EmptyBorder(0, 2, 20, 2));

        JPanel identity = Ui.transparent(new BorderLayout(10, 0));
        identity.add(new JLabel(new javax.swing.ImageIcon(Brand.createLogo(38))), BorderLayout.WEST);
        JPanel text = Ui.transparent(new GridLayout(2, 1, 0, 0));
        text.add(Ui.heading(Brand.NAME, 17));
        text.add(Ui.subheading("Student console"));
        identity.add(text, BorderLayout.CENTER);
        bar.add(identity, BorderLayout.WEST);

        JPanel right = Ui.transparent(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.add(new PillLabel("STUDENT MODE", Brand.ACCENT_SOFT, new Color(0x8A5A18)));
        JButton back = Ui.ghostButton("Back to home");
        back.addActionListener(e -> closeStudentWindow());
        right.add(back);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private JPanel buildSetupInfoCard() {
        RoundedPanel card = new RoundedPanel(new Color(0x233B47), null, 24, false);
        card.setLayout(new BorderLayout());
        card.setBorder(new EmptyBorder(30, 30, 30, 30));

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JLabel logo = new JLabel(new javax.swing.ImageIcon(Brand.createLogo(64)));
        logo.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(logo);
        content.add(Box.createVerticalStrut(20));

        JLabel title = new JLabel("<html>Join the class.<br>Stay in sync.</html>");
        title.setFont(Brand.font(Font.BOLD, 27));
        title.setForeground(Color.WHITE);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(title);
        content.add(Box.createVerticalStrut(12));

        JLabel copy = new JLabel("<html><div style='width:370px'>"
                + "Use the address shown on your teacher's screen. Ask a question, submit an "
                + "answer and download notes without leaving the app."
                + "</div></html>");
        copy.setFont(Brand.font(Font.PLAIN, 13));
        copy.setForeground(new Color(0xC9DEE1));
        copy.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(copy);
        content.add(Box.createVerticalStrut(26));

        content.add(setupStep("1", "Get the server address", "Your teacher sees it in the classroom window."));
        content.add(Box.createVerticalStrut(14));
        content.add(setupStep("2", "Type your name", "Use a name your teacher will recognise."));
        content.add(Box.createVerticalStrut(14));
        content.add(setupStep("3", "Join and listen", "Announcements and questions arrive automatically."));

        card.add(content, BorderLayout.NORTH);

        JLabel footer = new JLabel("NO ACCOUNT  •  NO INTERNET REQUIRED");
        footer.setFont(Brand.font(Font.BOLD, 9));
        footer.setForeground(new Color(0x9DB8BC));
        card.add(footer, BorderLayout.SOUTH);
        return card;
    }

    private JPanel setupStep(String number, String title, String detail) {
        JPanel row = Ui.transparent(new BorderLayout(12, 0));
        JLabel n = new JLabel(number, SwingConstants.CENTER);
        n.setOpaque(true);
        n.setBackground(new Color(255, 255, 255, 28));
        n.setForeground(new Color(0xF5C27D));
        n.setFont(Brand.font(Font.BOLD, 11));
        n.setPreferredSize(new Dimension(26, 26));
        row.add(n, BorderLayout.WEST);
        JPanel text = Ui.transparent(new GridLayout(2, 1, 0, 2));
        JLabel heading = new JLabel(title);
        heading.setFont(Brand.font(Font.BOLD, 13));
        heading.setForeground(Color.WHITE);
        JLabel sub = new JLabel(detail);
        sub.setFont(Brand.font(Font.PLAIN, 11));
        sub.setForeground(new Color(0xADC7CA));
        text.add(heading);
        text.add(sub);
        row.add(text, BorderLayout.CENTER);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        return row;
    }

    private JPanel buildSetupFormCard() {
        RoundedPanel card = Ui.card(new BorderLayout(0, 18));
        card.setBorder(new EmptyBorder(28, 30, 26, 30));

        JPanel headingBox = Ui.transparent(new BorderLayout(0, 5));
        headingBox.add(Ui.heading("Join a classroom", 22), BorderLayout.NORTH);
        headingBox.add(Ui.wrap("Enter the details exactly as they appear on the teacher's screen.",
                390, Brand.font(Font.PLAIN, 12), Brand.MUTED), BorderLayout.CENTER);
        card.add(headingBox, BorderLayout.NORTH);

        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));

        nameField = Ui.field();
        addFormField(form, "Your name", nameField, 12);

        hostField = Ui.field();
        hostField.setText("127.0.0.1");
        addFormField(form, "Teacher's IP address", hostField, 5);
        JLabel hostHint = new JLabel("On this PC use 127.0.0.1. On another computer use the teacher's IP.");
        hostHint.setFont(Brand.font(Font.PLAIN, 9));
        hostHint.setForeground(Brand.MUTED);
        hostHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(hostHint);
        form.add(Box.createVerticalStrut(12));

        portField = Ui.field();
        portField.setText("5050");
        addFormField(form, "Port", portField, 7);

        setupStatus = new JLabel("Ready when you are.");
        setupStatus.setFont(Brand.font(Font.PLAIN, 10));
        setupStatus.setForeground(Brand.MUTED);
        setupStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(setupStatus);
        form.add(Box.createVerticalStrut(6));

        setupError = new JLabel(" ");
        setupError.setFont(Brand.font(Font.PLAIN, 11));
        setupError.setForeground(Brand.DANGER);
        setupError.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(setupError);

        card.add(form, BorderLayout.CENTER);

        connectButton = Ui.primaryButton("Join class");
        connectButton.setPreferredSize(new Dimension(0, 42));
        connectButton.addActionListener(e -> joinClass());
        card.add(connectButton, BorderLayout.SOUTH);
        return card;
    }

    private void addFormField(JPanel form, String label, JTextField field, int gap) {
        JLabel l = new JLabel(label);
        l.setFont(Brand.font(Font.BOLD, 12));
        l.setForeground(Brand.INK_SOFT);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(l);
        form.add(Box.createVerticalStrut(5));
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        form.add(field);
        form.add(Box.createVerticalStrut(gap));
    }

    // --------------------------------------------------------------- lobby

    private JPanel buildLobby() {
        JPanel page = Ui.page();
        page.setBorder(new EmptyBorder(18, 22, 16, 22));
        page.add(buildLiveHeader(), BorderLayout.NORTH);
        page.add(buildLiveBody(), BorderLayout.CENTER);
        page.add(buildStatusBar(), BorderLayout.SOUTH);
        return page;
    }

    private JPanel buildLiveHeader() {
        JPanel header = Ui.transparent(new BorderLayout(16, 0));
        header.setBorder(new EmptyBorder(0, 2, 16, 2));

        JPanel identity = Ui.transparent(new BorderLayout(11, 0));
        identity.add(new JLabel(new javax.swing.ImageIcon(Brand.createLogo(40))), BorderLayout.WEST);
        JPanel names = Ui.transparent(new GridLayout(2, 1, 0, 1));
        classLabel = Ui.heading("Classroom", 18);
        teacherLabel = Ui.subheading("Waiting for connection");
        names.add(classLabel);
        names.add(teacherLabel);
        identity.add(names, BorderLayout.CENTER);
        header.add(identity, BorderLayout.WEST);

        JPanel right = Ui.transparent(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        connectedPill = new PillLabel("CONNECTED", Brand.SUCCESS_SOFT, Brand.SUCCESS);
        right.add(connectedPill);
        studentLabel = new JLabel("You");
        studentLabel.setFont(Brand.font(Font.PLAIN, 11));
        studentLabel.setForeground(Brand.MUTED);
        right.add(studentLabel);
        JButton leave = Ui.secondaryButton("Leave class");
        leave.addActionListener(e -> leaveClass());
        right.add(leave);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    private JPanel buildLiveBody() {
        JPanel body = Ui.transparent(new BorderLayout(16, 0));
        body.add(buildSidebar(), BorderLayout.WEST);
        body.add(buildTabs(), BorderLayout.CENTER);
        return body;
    }

    private JPanel buildSidebar() {
        RoundedPanel card = Ui.card(new BorderLayout(0, 14));
        card.setPreferredSize(new Dimension(224, 0));

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JLabel section = Ui.sectionLabel("Your class");
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(section);
        content.add(Box.createVerticalStrut(7));
        sidebarClassName = new JLabel("Classroom");
        sidebarClassName.setFont(Brand.font(Font.BOLD, 16));
        sidebarClassName.setForeground(Brand.INK);
        sidebarClassName.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(sidebarClassName);
        sidebarTeacherName = new JLabel("Teacher");
        sidebarTeacherName.setFont(Brand.font(Font.PLAIN, 11));
        sidebarTeacherName.setForeground(Brand.MUTED);
        sidebarTeacherName.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(sidebarTeacherName);
        content.add(Box.createVerticalStrut(16));
        content.add(Ui.divider());
        content.add(Box.createVerticalStrut(16));

        JLabel you = Ui.sectionLabel("Signed in as");
        you.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(you);
        content.add(Box.createVerticalStrut(8));
        JPanel userRow = Ui.transparent(new BorderLayout(9, 0));
        userRow.add(new Avatar("S", 32), BorderLayout.WEST);
        sidebarStudentName = new JLabel("Student");
        sidebarStudentName.setFont(Brand.font(Font.BOLD, 12));
        sidebarStudentName.setForeground(Brand.INK);
        userRow.add(sidebarStudentName, BorderLayout.CENTER);
        userRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        userRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        content.add(userRow);
        content.add(Box.createVerticalStrut(8));
        sidebarAddress = new JLabel("Connecting...");
        sidebarAddress.setFont(Brand.font(Font.PLAIN, 9));
        sidebarAddress.setForeground(Brand.MUTED);
        sidebarAddress.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(sidebarAddress);
        content.add(Box.createVerticalStrut(18));

        RoundedPanel tip = new RoundedPanel(new Color(0xF4F8F8), null, 12, false);
        tip.setLayout(new BorderLayout());
        tip.setBorder(new EmptyBorder(12, 12, 12, 12));
        JLabel tipText = Ui.wrap("Questions are answered only once. Results appear after your "
                + "teacher closes the question.", 160, Brand.font(Font.PLAIN, 10), Brand.INK_SOFT);
        tip.add(tipText, BorderLayout.CENTER);
        tip.setAlignmentX(Component.LEFT_ALIGNMENT);
        tip.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
        content.add(tip);

        content.add(Box.createVerticalGlue());
        card.add(content, BorderLayout.CENTER);
        return card;
    }

    private JTabbedPane buildTabs() {
        tabs = new JTabbedPane();
        Ui.styleTabs(tabs);
        tabs.addTab("Announcements", buildAnnouncementsTab());
        tabs.addTab("Questions", buildQuestionsTab());
        tabs.addTab("Notes", buildNotesTab());
        return tabs;
    }

    // ---------------------------------------------------------- announcements

    private JPanel buildAnnouncementsTab() {
        JPanel page = Ui.transparent(new BorderLayout());
        page.setBorder(new EmptyBorder(16, 0, 0, 0));

        RoundedPanel card = Ui.card(new BorderLayout(0, 10));
        JPanel heading = Ui.transparent(new BorderLayout());
        heading.add(Ui.heading("Announcements", 17), BorderLayout.WEST);
        announcementCountLabel = new JLabel("0 messages");
        announcementCountLabel.setFont(Brand.font(Font.PLAIN, 10));
        announcementCountLabel.setForeground(Brand.MUTED);
        heading.add(announcementCountLabel, BorderLayout.EAST);
        card.add(heading, BorderLayout.NORTH);

        announcementListPanel = new JPanel();
        announcementListPanel.setOpaque(false);
        announcementListPanel.setLayout(new BoxLayout(announcementListPanel, BoxLayout.Y_AXIS));
        card.add(Ui.cardScroll(announcementListPanel), BorderLayout.CENTER);
        page.add(card, BorderLayout.CENTER);
        refreshAnnouncements();
        return page;
    }

    private void refreshAnnouncements() {
        if (announcementListPanel == null) {
            return;
        }
        announcementListPanel.removeAll();
        if (announcementCountLabel != null) {
            announcementCountLabel.setText(announcements.size()
                    + (announcements.size() == 1 ? " message" : " messages"));
        }
        if (announcements.isEmpty()) {
            JPanel empty = Ui.emptyState("No announcements yet",
                    "Messages from your teacher will appear here as soon as they are sent.");
            empty.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));
            announcementListPanel.add(empty);
        } else {
            for (int i = announcements.size() - 1; i >= 0; i--) {
                announcementListPanel.add(announcementCard(announcements.get(i)));
                announcementListPanel.add(Box.createVerticalStrut(8));
            }
        }
        announcementListPanel.add(Box.createVerticalGlue());
        announcementListPanel.revalidate();
        announcementListPanel.repaint();
    }

    private JPanel announcementCard(Announcement announcement) {
        RoundedPanel card = new RoundedPanel(new Color(0xF8FAFA), null, 14, false);
        card.setLayout(new BorderLayout(0, 6));
        card.setBorder(new EmptyBorder(12, 14, 12, 14));
        JLabel time = new JLabel(Ui.formatTime(announcement.sentAt()));
        time.setFont(Brand.font(Font.BOLD, 9));
        time.setForeground(Brand.PRIMARY_DARK);
        card.add(time, BorderLayout.NORTH);
        card.add(Ui.wrap(announcement.text(), 650, Brand.font(Font.PLAIN, 13), Brand.INK),
                BorderLayout.CENTER);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height + 4));
        return card;
    }

    // -------------------------------------------------------------- questions

    private JPanel buildQuestionsTab() {
        JPanel page = Ui.transparent(new BorderLayout());
        page.setBorder(new EmptyBorder(16, 0, 0, 0));

        RoundedPanel card = Ui.card(new BorderLayout(0, 12));
        JPanel heading = Ui.transparent(new BorderLayout());
        quizTitle = Ui.heading("Question", 17);
        heading.add(quizTitle, BorderLayout.WEST);
        quizBadge = new PillLabel("WAITING", new Color(0xEEF2F3), Brand.MUTED);
        heading.add(quizBadge, BorderLayout.EAST);
        card.add(heading, BorderLayout.NORTH);

        quizBodyHost = Ui.transparent(new BorderLayout());
        card.add(quizBodyHost, BorderLayout.CENTER);
        page.add(card, BorderLayout.CENTER);
        showQuizEmpty();
        return page;
    }

    private void showQuizEmpty() {
        if (quizBodyHost == null) {
            return;
        }
        quizBodyHost.removeAll();
        quizBodyHost.add(Ui.emptyState("No question is open",
                "When your teacher starts an MCQ, it will appear here with a live countdown."),
                BorderLayout.CENTER);
        quizTitle.setText("Question");
        quizBadge.setText("WAITING");
        quizBadge.setColors(new Color(0xEEF2F3), Brand.MUTED);
        optionButtons.clear();
        studentCountdownLabel = null;
        studentAnswerStatus = null;
        submitAnswerButton = null;
        quizBodyHost.revalidate();
        quizBodyHost.repaint();
    }

    private void handleQuizStarted(QuizView quiz) {
        currentQuiz = quiz;
        lastResult = null;
        selectedIndex = -1;
        answerSubmitted = false;
        showActiveQuiz(quiz);
        startCountdownTimer();
    }

    private void showActiveQuiz(QuizView quiz) {
        quizBodyHost.removeAll();
        optionButtons.clear();
        optionGroup = new ButtonGroup();

        JPanel body = Ui.transparent(new BorderLayout(0, 16));

        JPanel top = Ui.transparent(new BorderLayout(0, 10));
        JPanel liveRow = Ui.transparent(new BorderLayout());
        JLabel live = new JLabel("LIVE QUESTION");
        live.setFont(Brand.font(Font.BOLD, 9));
        live.setForeground(Brand.SUCCESS);
        liveRow.add(live, BorderLayout.WEST);
        studentCountdownLabel = new JLabel("00:00");
        studentCountdownLabel.setFont(Brand.font(Font.BOLD, 24));
        studentCountdownLabel.setForeground(Brand.PRIMARY_DARK);
        liveRow.add(studentCountdownLabel, BorderLayout.EAST);
        top.add(liveRow, BorderLayout.NORTH);
        top.add(Ui.wrap(quiz.prompt(), 660, Brand.font(Font.BOLD, 19), Brand.INK),
                BorderLayout.CENTER);
        body.add(top, BorderLayout.NORTH);

        JPanel options = new JPanel();
        options.setOpaque(false);
        options.setLayout(new BoxLayout(options, BoxLayout.Y_AXIS));
        for (int i = 0; i < quiz.options().size(); i++) {
            String letter = String.valueOf((char) ('A' + i));
            OptionButton button = new OptionButton(letter, quiz.options().get(i));
            button.setAlignmentX(Component.LEFT_ALIGNMENT);
            button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
            button.addActionListener(e -> selectedIndex = indexOfOption(button));
            optionGroup.add(button);
            optionButtons.add(button);
            options.add(button);
            options.add(Box.createVerticalStrut(8));
        }
        JScrollPane optionScroll = Ui.scroll(options);
        optionScroll.setBorder(null);
        body.add(optionScroll, BorderLayout.CENTER);

        JPanel footer = Ui.transparent(new BorderLayout(10, 6));
        studentAnswerStatus = new JLabel("Choose one option, then submit your answer.");
        studentAnswerStatus.setFont(Brand.font(Font.PLAIN, 10));
        studentAnswerStatus.setForeground(Brand.MUTED);
        footer.add(studentAnswerStatus, BorderLayout.NORTH);
        submitAnswerButton = Ui.primaryButton("Submit answer");
        submitAnswerButton.setPreferredSize(new Dimension(145, 40));
        submitAnswerButton.addActionListener(e -> submitAnswer());
        footer.add(submitAnswerButton, BorderLayout.EAST);

        body.add(footer, BorderLayout.SOUTH);
        quizBodyHost.add(body, BorderLayout.CENTER);
        quizTitle.setText("Question");
        quizBadge.setText("LIVE");
        quizBadge.setColors(Brand.SUCCESS_SOFT, Brand.SUCCESS);
        quizBodyHost.revalidate();
        quizBodyHost.repaint();
        updateStudentCountdown();
    }

    private int indexOfOption(OptionButton button) {
        return optionButtons.indexOf(button);
    }

    private void submitAnswer() {
        if (currentQuiz == null || answerSubmitted || client == null) {
            return;
        }
        if (selectedIndex < 0) {
            studentAnswerStatus.setText("Choose an option first.");
            studentAnswerStatus.setForeground(Brand.DANGER);
            return;
        }
        try {
            client.sendAnswer(currentQuiz.id(), selectedIndex);
            submitAnswerButton.setEnabled(false);
            submitAnswerButton.setText("Sending...");
            studentAnswerStatus.setText("Sending your answer...");
            studentAnswerStatus.setForeground(Brand.MUTED);
            for (OptionButton button : optionButtons) {
                button.setEnabled(false);
            }
        } catch (IOException ex) {
            studentAnswerStatus.setText(ex.getMessage());
            studentAnswerStatus.setForeground(Brand.DANGER);
        }
    }

    private void handleAnswerReceipt(AnswerReceipt receipt) {
        if (currentQuiz == null || !currentQuiz.id().equals(receipt.quizId())) {
            return;
        }
        if (receipt.accepted()) {
            answerSubmitted = true;
            selectedIndex = receipt.selectedIndex();
            if (submitAnswerButton != null) {
                submitAnswerButton.setText("Answer sent");
                submitAnswerButton.setEnabled(false);
            }
            if (studentAnswerStatus != null) {
                studentAnswerStatus.setText("Answer submitted. Wait for the teacher to reveal results.");
                studentAnswerStatus.setForeground(Brand.SUCCESS);
            }
            for (OptionButton button : optionButtons) {
                button.setEnabled(false);
            }
        } else {
            if (submitAnswerButton != null) {
                submitAnswerButton.setEnabled(true);
                submitAnswerButton.setText("Submit answer");
            }
            if (studentAnswerStatus != null) {
                studentAnswerStatus.setText(receipt.message());
                studentAnswerStatus.setForeground(Brand.DANGER);
            }
            if (receipt.message() != null && receipt.message().toLowerCase().contains("time")) {
                for (OptionButton button : optionButtons) {
                    button.setEnabled(false);
                }
                if (submitAnswerButton != null) {
                    submitAnswerButton.setEnabled(false);
                }
            }
        }
    }

    private void handleQuizResult(QuizResult result) {
        if (countdownTimer != null) {
            countdownTimer.stop();
        }
        lastResult = result;
        currentQuiz = null;
        showQuizResult(result);
    }

    private void showQuizResult(QuizResult result) {
        quizBodyHost.removeAll();
        optionButtons.clear();

        JPanel body = Ui.transparent(new BorderLayout(0, 14));

        JPanel top = Ui.transparent(new BorderLayout(0, 8));
        JPanel meta = Ui.transparent(new BorderLayout());
        JLabel closed = new JLabel("QUESTION CLOSED");
        closed.setFont(Brand.font(Font.BOLD, 9));
        closed.setForeground(Brand.MUTED);
        meta.add(closed, BorderLayout.WEST);
        JLabel count = new JLabel(result.answered() + " of " + result.totalStudents() + " answered");
        count.setFont(Brand.font(Font.PLAIN, 10));
        count.setForeground(Brand.MUTED);
        meta.add(count, BorderLayout.EAST);
        top.add(meta, BorderLayout.NORTH);
        top.add(Ui.wrap(result.prompt(), 660, Brand.font(Font.BOLD, 19), Brand.INK),
                BorderLayout.CENTER);
        body.add(top, BorderLayout.NORTH);

        boolean correct = selectedIndex == result.correctIndex();
        RoundedPanel feedback = new RoundedPanel(
                selectedIndex < 0 ? new Color(0xF3F6F7) : correct ? Brand.SUCCESS_SOFT : Brand.DANGER_SOFT,
                null, 12, false);
        feedback.setLayout(new BorderLayout(0, 3));
        feedback.setBorder(new EmptyBorder(11, 13, 11, 13));
        String feedbackText;
        Color feedbackColor;
        if (selectedIndex < 0) {
            feedbackText = "You did not submit an answer. Correct answer: "
                    + (char) ('A' + result.correctIndex()) + ". " + result.options().get(result.correctIndex());
            feedbackColor = Brand.INK_SOFT;
        } else if (correct) {
            feedbackText = "Correct! You chose " + (char) ('A' + selectedIndex) + ".";
            feedbackColor = Brand.SUCCESS;
        } else {
            feedbackText = "Not quite. You chose " + (char) ('A' + selectedIndex)
                    + ". Correct answer: " + (char) ('A' + result.correctIndex())
                    + ". " + result.options().get(result.correctIndex());
            feedbackColor = Brand.DANGER;
        }
        JLabel feedbackLabel = new JLabel("<html><div style='width:620px'>"
                + Ui.escapeHtml(feedbackText) + "</div></html>");
        feedbackLabel.setFont(Brand.font(Font.BOLD, 12));
        feedbackLabel.setForeground(feedbackColor);
        feedback.add(feedbackLabel, BorderLayout.CENTER);

        JPanel content = Ui.transparent(new BorderLayout(0, 12));
        content.add(feedback, BorderLayout.NORTH);

        JPanel bars = new JPanel();
        bars.setOpaque(false);
        bars.setLayout(new BoxLayout(bars, BoxLayout.Y_AXIS));
        for (int i = 0; i < result.options().size(); i++) {
            bars.add(resultRow(i, result));
            bars.add(Box.createVerticalStrut(8));
        }
        content.add(bars, BorderLayout.CENTER);
        body.add(content, BorderLayout.CENTER);

        quizBodyHost.add(body, BorderLayout.CENTER);
        quizTitle.setText("Results");
        quizBadge.setText("RESULTS");
        quizBadge.setColors(Brand.SUCCESS_SOFT, Brand.SUCCESS);
        quizBodyHost.revalidate();
        quizBodyHost.repaint();
    }

    private JPanel resultRow(int index, QuizResult result) {
        boolean correct = index == result.correctIndex();
        JPanel row = Ui.transparent(new BorderLayout(0, 5));
        JPanel top = Ui.transparent(new BorderLayout(8, 0));
        JLabel label = new JLabel((correct ? "✓  " : "") + (char) ('A' + index) + ". "
                + result.options().get(index));
        label.setFont(Brand.font(correct ? Font.BOLD : Font.PLAIN, 11));
        label.setForeground(correct ? Brand.SUCCESS : Brand.INK_SOFT);
        top.add(label, BorderLayout.CENTER);
        JLabel count = new JLabel(result.counts()[index] + "  ("
                + percent(result.counts()[index], result.answered()) + ")");
        count.setFont(Brand.font(Font.PLAIN, 10));
        count.setForeground(Brand.MUTED);
        top.add(count, BorderLayout.EAST);
        row.add(top, BorderLayout.NORTH);
        MiniBar bar = new MiniBar(correct ? Brand.SUCCESS : Brand.PRIMARY);
        bar.setPreferredSize(new Dimension(10, 7));
        bar.setValues(result.counts()[index], result.answered());
        row.add(bar, BorderLayout.CENTER);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        return row;
    }

    private String percent(int value, int total) {
        if (total <= 0) {
            return "0%";
        }
        return Math.round(value * 100.0 / total) + "%";
    }

    private void startCountdownTimer() {
        if (countdownTimer != null) {
            countdownTimer.stop();
        }
        countdownTimer = new Timer(250, e -> updateStudentCountdown());
        countdownTimer.start();
        updateStudentCountdown();
    }

    private void updateStudentCountdown() {
        QuizView quiz = currentQuiz;
        if (quiz == null || studentCountdownLabel == null) {
            if (countdownTimer != null) {
                countdownTimer.stop();
            }
            return;
        }
        long remaining = Math.max(0, quiz.endsAt() - System.currentTimeMillis());
        long seconds = (remaining + 999) / 1000;
        studentCountdownLabel.setText(String.format("%02d:%02d", seconds / 60, seconds % 60));
        if (remaining <= 0) {
            countdownTimer.stop();
            for (OptionButton button : optionButtons) {
                button.setEnabled(false);
            }
            if (submitAnswerButton != null) {
                submitAnswerButton.setEnabled(false);
            }
            if (studentAnswerStatus != null && !answerSubmitted) {
                studentAnswerStatus.setText("Time is up. Waiting for the teacher to reveal results.");
                studentAnswerStatus.setForeground(Brand.MUTED);
            }
            quizBadge.setText("CLOSED");
            quizBadge.setColors(new Color(0xEEF2F3), Brand.MUTED);
        }
    }

    // ------------------------------------------------------------------ notes

    private JPanel buildNotesTab() {
        JPanel page = Ui.transparent(new BorderLayout());
        page.setBorder(new EmptyBorder(16, 0, 0, 0));

        RoundedPanel card = Ui.card(new BorderLayout(0, 10));
        JPanel heading = Ui.transparent(new BorderLayout());
        heading.add(Ui.heading("Class notes", 17), BorderLayout.WEST);
        notesCountLabel = new JLabel("0 files");
        notesCountLabel.setFont(Brand.font(Font.PLAIN, 10));
        notesCountLabel.setForeground(Brand.MUTED);
        heading.add(notesCountLabel, BorderLayout.EAST);
        card.add(heading, BorderLayout.NORTH);

        JLabel hint = new JLabel("Files shared by your teacher can be saved to this computer.");
        hint.setFont(Brand.font(Font.PLAIN, 10));
        hint.setForeground(Brand.MUTED);
        card.add(hint, BorderLayout.SOUTH);

        notesListPanel = new JPanel();
        notesListPanel.setOpaque(false);
        notesListPanel.setLayout(new BoxLayout(notesListPanel, BoxLayout.Y_AXIS));
        card.add(Ui.cardScroll(notesListPanel), BorderLayout.CENTER);
        page.add(card, BorderLayout.CENTER);
        refreshNotes();
        return page;
    }

    private void refreshNotes() {
        if (notesListPanel == null) {
            return;
        }
        notesListPanel.removeAll();
        if (notesCountLabel != null) {
            notesCountLabel.setText(notes.size() + (notes.size() == 1 ? " file" : " files"));
        }
        if (notes.isEmpty()) {
            JPanel empty = Ui.emptyState("No notes yet",
                    "When your teacher shares a file, it will appear here with a download button.");
            empty.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));
            notesListPanel.add(empty);
        } else {
            for (int i = notes.size() - 1; i >= 0; i--) {
                notesListPanel.add(noteRow(notes.get(i)));
                notesListPanel.add(Box.createVerticalStrut(8));
            }
        }
        notesListPanel.add(Box.createVerticalGlue());
        notesListPanel.revalidate();
        notesListPanel.repaint();
    }

    private JPanel noteRow(NoteSummary note) {
        RoundedPanel row = new RoundedPanel(new Color(0xF8FAFA), null, 14, false);
        row.setLayout(new BorderLayout(12, 0));
        row.setBorder(new EmptyBorder(11, 13, 11, 13));

        String extension = extensionOf(note.fileName());
        JLabel badge = new JLabel(extension, SwingConstants.CENTER);
        badge.setOpaque(true);
        badge.setBackground(Brand.ACCENT_SOFT);
        badge.setForeground(new Color(0x8A5A18));
        badge.setFont(Brand.font(Font.BOLD, 10));
        badge.setPreferredSize(new Dimension(42, 38));
        row.add(badge, BorderLayout.WEST);

        JPanel words = Ui.transparent(new GridLayout(2, 1, 0, 2));
        JLabel name = new JLabel(note.fileName());
        name.setFont(Brand.font(Font.BOLD, 12));
        name.setForeground(Brand.INK);
        JLabel detail = new JLabel(Ui.bytes(note.sizeBytes()) + "  •  shared "
                + Ui.formatTime(note.uploadedAt()));
        detail.setFont(Brand.font(Font.PLAIN, 10));
        detail.setForeground(Brand.MUTED);
        words.add(name);
        words.add(detail);
        row.add(words, BorderLayout.CENTER);

        JButton download = Ui.secondaryButton("Download");
        download.setPreferredSize(new Dimension(100, 32));
        download.addActionListener(e -> downloadNote(note));
        row.add(download, BorderLayout.EAST);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
        return row;
    }

    private void downloadNote(NoteSummary note) {
        if (client == null) {
            Ui.showError(this, "Not connected", "Join the class before downloading notes.");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save " + note.fileName());
        chooser.setSelectedFile(defaultDownloadPath(note.fileName()).toFile());
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File selected = chooser.getSelectedFile();
        if (selected == null) {
            return;
        }
        Path target = selected.toPath();
        if (Files.exists(target) && !Ui.confirm(this, "Replace file",
                selected.getName() + " already exists. Replace it?")) {
            return;
        }
        pendingDownloads.put(note.id(), target);
        try {
            client.requestNote(note.id());
            setStatus("Downloading " + note.fileName() + "...");
        } catch (IOException ex) {
            pendingDownloads.remove(note.id());
            Ui.showError(this, "Download failed", ex.getMessage());
        }
    }

    private void saveDownloadedNote(NoteData note) {
        Path target = pendingDownloads.remove(note.summary().id());
        if (target == null) {
            Ui.showError(this, "Download error", "No save location was chosen for this file.");
            return;
        }
        setStatus("Saving " + note.summary().fileName() + "...");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                Files.write(target, note.content());
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    setStatus("Saved " + target.getFileName() + " to Downloads.");
                    Ui.showInfo(StudentFrame.this, "Download complete",
                            note.summary().fileName() + " was saved to:\n" + target.toAbsolutePath());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    Ui.showError(StudentFrame.this, "Download failed", "The save was interrupted.");
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause();
                    Ui.showError(StudentFrame.this, "Download failed",
                            cause == null ? "The file could not be saved." : cause.getMessage());
                }
            }
        }.execute();
    }

    private Path defaultDownloadPath(String fileName) {
        Path downloads = Paths.get(System.getProperty("user.home"), "Downloads");
        if (!Files.isDirectory(downloads)) {
            downloads = Paths.get(System.getProperty("user.home"));
        }
        return downloads.resolve(Ui.safeFileName(fileName));
    }

    private String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1 && name.length() - dot <= 6) {
            return name.substring(dot + 1).toUpperCase();
        }
        return "FILE";
    }

    // ------------------------------------------------------------- connection

    private void joinClass() {
        setupError.setText(" ");
        String name = nameField.getText().trim();
        String host = hostField.getText().trim();
        if (name.length() < 2 || name.length() > 30) {
            setupError.setText("Enter a name between 2 and 30 characters.");
            nameField.requestFocusInWindow();
            return;
        }
        if (host.isEmpty()) {
            setupError.setText("Enter the teacher's IP address.");
            hostField.requestFocusInWindow();
            return;
        }
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
            if (port < 1 || port > 65535) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException ex) {
            setupError.setText("Port must be a number between 1 and 65535.");
            portField.requestFocusInWindow();
            return;
        }

        connectButton.setEnabled(false);
        connectButton.setText("Connecting...");
        setupStatus.setText("Looking for a classroom at " + host + ":" + port + "...");
        setupStatus.setForeground(Brand.MUTED);

        new SwingWorker<JoinInfo, Void>() {
            private ClassroomClient workerClient;

            @Override
            protected JoinInfo doInBackground() throws Exception {
                workerClient = new ClassroomClient(host, port, clientListener);
                client = workerClient;
                return workerClient.connect(name);
            }

            @Override
            protected void done() {
                connectButton.setEnabled(true);
                connectButton.setText("Join class");
                try {
                    get();
                    setupStatus.setText("Connected. Opening the classroom...");
                    setupStatus.setForeground(Brand.SUCCESS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    failJoin("The connection attempt was interrupted.");
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause();
                    String message = cause == null || cause.getMessage() == null
                            ? "Could not reach the classroom." : cause.getMessage();
                    failJoin(message);
                }
            }

            private void failJoin(String message) {
                if (workerClient != null) {
                    workerClient.disconnect();
                }
                client = null;
                setupStatus.setText("Not connected.");
                setupStatus.setForeground(Brand.MUTED);
                setupError.setText(message);
                connectButton.setEnabled(true);
                connectButton.setText("Join class");
            }
        }.execute();
    }

    private void handleConnected(JoinInfo info) {
        live = true;
        classLabel.setText(info.className());
        teacherLabel.setText("with " + info.teacherName());
        studentLabel.setText("You: " + info.studentName());
        sidebarClassName.setText(info.className());
        sidebarTeacherName.setText("Taught by " + info.teacherName());
        sidebarStudentName.setText(info.studentName());
        sidebarAddress.setText(client == null ? "Connected"
                : "Connected via " + client.getHost() + ":" + client.getPort());
        connectedPill.setText("CONNECTED");
        connectedPill.setColors(Brand.SUCCESS_SOFT, Brand.SUCCESS);
        cardLayout.show(cardHost, LIVE_CARD);
        tabs.setSelectedIndex(0);
        setStatus("Joined " + info.className() + ". You are connected.");
    }

    private void handleDisconnected(String message) {
        if (!live || leaving) {
            return;
        }
        live = false;
        client = null;
        stopCountdown();
        Ui.showError(this, "Connection lost", message);
        resetToSetup("You were disconnected. Check the address and join again.");
    }

    private void leaveClass() {
        if (!Ui.confirm(this, "Leave class", "Leave this classroom?")) {
            return;
        }
        leaving = true;
        if (client != null) {
            client.disconnect();
        }
        client = null;
        live = false;
        leaving = false;
        resetToSetup("You left the class.");
    }

    private void resetToSetup(String message) {
        stopCountdown();
        currentQuiz = null;
        lastResult = null;
        selectedIndex = -1;
        answerSubmitted = false;
        announcements.clear();
        notes.clear();
        pendingDownloads.clear();
        refreshAnnouncements();
        refreshNotes();
        showQuizEmpty();
        connectButton.setEnabled(true);
        connectButton.setText("Join class");
        setupStatus.setText("Not connected.");
        setupStatus.setForeground(Brand.MUTED);
        setupError.setText(message == null ? " " : message);
        setStatus("Not connected.");
        cardLayout.show(cardHost, SETUP_CARD);
    }

    private void stopCountdown() {
        if (countdownTimer != null) {
            countdownTimer.stop();
        }
    }

    private JPanel buildStatusBar() {
        JPanel bar = Ui.transparent(new BorderLayout());
        bar.setBorder(new EmptyBorder(8, 4, 0, 4));
        statusLabel = new JLabel("Not connected.");
        statusLabel.setFont(Brand.font(Font.PLAIN, 10));
        statusLabel.setForeground(Brand.MUTED);
        bar.add(statusLabel, BorderLayout.WEST);
        connectionLabel = new JLabel("Student app • ClassPulse");
        connectionLabel.setFont(Brand.font(Font.PLAIN, 10));
        connectionLabel.setForeground(Brand.MUTED);
        bar.add(connectionLabel, BorderLayout.EAST);
        return bar;
    }

    private void setStatus(String message) {
        if (statusLabel != null) {
            statusLabel.setText(message);
        }
    }

    private void closeStudentWindow() {
        if (live) {
            if (!Ui.confirm(this, "Close ClassPulse",
                    "You are still in class. Leave the class and close the app?")) {
                return;
            }
            leaving = true;
            if (client != null) {
                client.disconnect();
            }
            client = null;
            live = false;
            stopCountdown();
        }
        dispose();
        launcher.showAgain();
    }

    private void onEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            SwingUtilities.invokeLater(action);
        }
    }
}
