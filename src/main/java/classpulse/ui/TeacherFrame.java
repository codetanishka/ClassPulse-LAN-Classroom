package classpulse.ui;

import classpulse.brand.Brand;
import classpulse.model.Announcement;
import classpulse.model.NoteSummary;
import classpulse.model.QuizResult;
import classpulse.model.QuizStats;
import classpulse.model.QuizView;
import classpulse.model.StudentInfo;
import classpulse.net.ClassroomServer;
import classpulse.net.ServerListener;
import classpulse.ui.component.Avatar;
import classpulse.ui.component.MiniBar;
import classpulse.ui.component.PillLabel;
import classpulse.ui.component.RoundedButton;
import classpulse.ui.component.RoundedPanel;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.net.BindException;
import java.util.ArrayList;
import java.util.List;

/**
 * Teacher console. The frame starts on a setup card and switches to the live
 * classroom after the embedded server has bound to its port.
 */
public final class TeacherFrame extends JFrame {
    private static final String SETUP_CARD = "setup";
    private static final String LIVE_CARD = "live";

    private final LauncherFrame launcher;
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cardHost = new JPanel(cardLayout);

    private ClassroomServer server;
    private boolean classLive;
    private boolean returningToLauncher;
    private String serverAddress = "";
    private List<String> serverAddresses = new ArrayList<>();

    private JTextField classField;
    private JTextField teacherField;
    private JTextField portField;
    private JLabel setupError;
    private JButton startButton;

    private JLabel headerClassName;
    private JLabel headerTeacherName;
    private JLabel headerAddressLabel;
    private JLabel sidebarAddressLabel;
    private JLabel sameComputerLabel;
    private JLabel statusLabel;
    private PillLabel livePill;
    private JLabel studentCountLabel;
    private JLabel headerStudentCount;
    private JPanel studentListPanel;
    private JTabbedPane tabs;

    private JTextArea announcementInput;
    private JLabel announcementCharCount;
    private JLabel announcementCountLabel;
    private JPanel announcementListPanel;
    private final List<Announcement> announcements = new ArrayList<>();

    private JTextField questionField;
    private final JTextField[] optionFields = new JTextField[4];
    private final JRadioButton[] optionRadios = new JRadioButton[4];
    private JSpinner durationSpinner;
    private JButton sendQuestionButton;
    private JPanel resultsBody;
    private JLabel resultsTitle;
    private PillLabel resultsBadge;
    private JButton endQuestionButton;
    private JLabel resultsFooterLabel;
    private JLabel countdownLabel;
    private JLabel answeredLabel;
    private JLabel resultsQuestionLabel;
    private final List<BarRow> barRows = new ArrayList<>();
    private Timer countdownTimer;
    private QuizView activeQuiz;
    private QuizResult lastResult;

    private JLabel selectedNoteLabel;
    private JButton sendNoteButton;
    private JLabel notesCountLabel;
    private JPanel notesListPanel;
    private File selectedNoteFile;
    private final List<NoteSummary> notes = new ArrayList<>();

    private final ServerListener serverListener = new ServerListener() {
        @Override
        public void onStarted(int port, List<String> addresses) {
            onEdt(() -> handleStarted(port, addresses));
        }

        @Override
        public void onStopped(String message) {
            onEdt(() -> {
                if (returningToLauncher) {
                    return;
                }
                classLive = false;
                setStatus(message);
            });
        }

        @Override
        public void onStudentsChanged(List<StudentInfo> students) {
            onEdt(() -> refreshStudents(students));
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
        public void onQuizStats(QuizStats stats) {
            onEdt(() -> handleQuizStats(stats));
        }

        @Override
        public void onQuizResult(QuizResult result) {
            onEdt(() -> handleQuizResult(result));
        }

        @Override
        public void onNoteAdded(NoteSummary note) {
            onEdt(() -> {
                notes.add(note);
                refreshNotes();
            });
        }

        @Override
        public void onLog(String message) {
            onEdt(() -> setStatus(message));
        }

        @Override
        public void onError(String message) {
            onEdt(() -> {
                setStatus(message);
                if (classLive) {
                    Ui.showError(TeacherFrame.this, "Classroom problem", message);
                }
            });
        }
    };

    public TeacherFrame(LauncherFrame launcher) {
        super(Brand.NAME + " - Teacher");
        this.launcher = launcher;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(1000, 680));
        setSize(1200, 760);
        setLocationRelativeTo(null);
        setIconImages(Ui.windowIcons());
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeTeacherWindow();
            }
        });

        cardHost.setBackground(Brand.BG);
        cardHost.add(buildSetupCard(), SETUP_CARD);
        cardHost.add(buildDashboard(), LIVE_CARD);
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

        JPanel info = buildSetupInfoCard();
        c.gridx = 0;
        c.weightx = 0.52;
        center.add(info, c);

        JPanel form = buildSetupFormCard();
        c.gridx = 1;
        c.weightx = 0.48;
        c.insets = new Insets(8, 0, 8, 0);
        center.add(form, c);

        page.add(center, BorderLayout.CENTER);

        JPanel footer = Ui.transparent(new BorderLayout());
        footer.setBorder(new EmptyBorder(10, 2, 0, 2));
        JLabel note = new JLabel("Tip: both apps use port 5050 by default. If students cannot connect, "
                + "allow ClassPulse through Windows Firewall (private networks).");
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
        text.add(Ui.subheading("Teacher console"));
        identity.add(text, BorderLayout.CENTER);
        bar.add(identity, BorderLayout.WEST);

        JPanel right = Ui.transparent(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.add(new PillLabel("TEACHER MODE", Brand.PRIMARY_SOFT, Brand.PRIMARY_DARK));
        JButton back = Ui.ghostButton("Back to home");
        back.addActionListener(e -> closeTeacherWindow());
        right.add(back);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private JPanel buildSetupInfoCard() {
        RoundedPanel card = new RoundedPanel(new Color(0x133D48), null, 24, false);
        card.setLayout(new BorderLayout());
        card.setBorder(new EmptyBorder(30, 30, 30, 30));

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JLabel logo = new JLabel(new javax.swing.ImageIcon(Brand.createLogo(64)));
        logo.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(logo);
        content.add(Box.createVerticalStrut(20));

        JLabel title = new JLabel("<html>Start a class<br>on this computer.</html>");
        title.setFont(Brand.font(Font.BOLD, 27));
        title.setForeground(Color.WHITE);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(title);
        content.add(Box.createVerticalStrut(12));

        JLabel copy = new JLabel("<html><div style='width:370px'>"
                + "The teacher app becomes the server. Students join from ClassPulse on the same "
                + "Wi-Fi or LAN, then receive announcements, questions and notes in real time."
                + "</div></html>");
        copy.setFont(Brand.font(Font.PLAIN, 13));
        copy.setForeground(new Color(0xC9DEE1));
        copy.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(copy);
        content.add(Box.createVerticalStrut(26));

        content.add(setupStep("1", "Name your class", "This is shown in every student window."));
        content.add(Box.createVerticalStrut(14));
        content.add(setupStep("2", "Choose a free port", "5050 is the default. Change it only if needed."));
        content.add(Box.createVerticalStrut(14));
        content.add(setupStep("3", "Share your address", "Students enter your IP address and port."));

        card.add(content, BorderLayout.NORTH);

        JLabel footer = new JLabel("WINDOWS DESKTOP  •  LOCAL NETWORK ONLY");
        footer.setFont(Brand.font(Font.BOLD, 9));
        footer.setForeground(new Color(0x8FB3B7));
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
        sub.setForeground(new Color(0xA9C5C8));
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
        headingBox.add(Ui.heading("Create your classroom", 22), BorderLayout.NORTH);
        headingBox.add(Ui.wrap("Students will see this class name and your name after joining.",
                390, Brand.font(Font.PLAIN, 12), Brand.MUTED), BorderLayout.CENTER);
        card.add(headingBox, BorderLayout.NORTH);

        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));

        classField = Ui.field();
        classField.setText("Class 12 - Computer Science");
        addFormField(form, "Class name", classField, 14);

        teacherField = Ui.field();
        teacherField.setText("Teacher");
        addFormField(form, "Teacher name", teacherField, 14);

        portField = Ui.field();
        portField.setText("5050");
        addFormField(form, "Port", portField, 10);

        JLabel portHint = new JLabel("Must be between 1 and 65535.");
        portHint.setFont(Brand.font(Font.PLAIN, 10));
        portHint.setForeground(Brand.MUTED);
        portHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(portHint);
        form.add(Box.createVerticalStrut(8));

        setupError = new JLabel(" ");
        setupError.setFont(Brand.font(Font.PLAIN, 11));
        setupError.setForeground(Brand.DANGER);
        setupError.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(setupError);

        card.add(form, BorderLayout.CENTER);

        JPanel actions = Ui.transparent(new BorderLayout(0, 8));
        startButton = Ui.primaryButton("Start class");
        startButton.setPreferredSize(new Dimension(0, 42));
        startButton.addActionListener(e -> startClass());
        actions.add(startButton, BorderLayout.NORTH);
        JLabel note = new JLabel("The class stays on this computer until you end it.");
        note.setFont(Brand.font(Font.PLAIN, 10));
        note.setForeground(Brand.MUTED);
        note.setHorizontalAlignment(SwingConstants.CENTER);
        actions.add(note, BorderLayout.SOUTH);
        card.add(actions, BorderLayout.SOUTH);
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

    // -------------------------------------------------------------- live view

    private JPanel buildDashboard() {
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
        headerClassName = Ui.heading("Classroom", 18);
        headerTeacherName = Ui.subheading("Teacher");
        names.add(headerClassName);
        names.add(headerTeacherName);
        identity.add(names, BorderLayout.CENTER);
        header.add(identity, BorderLayout.WEST);

        JPanel right = Ui.transparent(new FlowLayout(FlowLayout.RIGHT, 10, 4));
        livePill = new PillLabel("LIVE", Brand.SUCCESS_SOFT, Brand.SUCCESS);
        right.add(livePill);
        headerStudentCount = new JLabel("0 students");
        headerStudentCount.setFont(Brand.font(Font.PLAIN, 11));
        headerStudentCount.setForeground(Brand.MUTED);
        right.add(headerStudentCount);

        JPanel addressBox = Ui.transparent(new BorderLayout(5, 0));
        JLabel label = new JLabel("SERVER");
        label.setFont(Brand.font(Font.BOLD, 9));
        label.setForeground(Brand.MUTED);
        headerAddressLabel = new JLabel("0.0.0.0:5050");
        headerAddressLabel.setFont(Brand.font(Font.BOLD, 12));
        headerAddressLabel.setForeground(Brand.INK);
        addressBox.add(label, BorderLayout.NORTH);
        addressBox.add(headerAddressLabel, BorderLayout.CENTER);
        right.add(addressBox);

        JButton copy = Ui.secondaryButton("Copy address");
        copy.addActionListener(e -> copyAddress());
        right.add(copy);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    private JPanel buildLiveBody() {
        JPanel body = Ui.transparent(new BorderLayout(16, 0));
        body.add(buildRosterCard(), BorderLayout.WEST);
        body.add(buildTabs(), BorderLayout.CENTER);
        return body;
    }

    private JPanel buildRosterCard() {
        RoundedPanel card = Ui.card(new BorderLayout(0, 12));
        card.setPreferredSize(new Dimension(238, 0));

        JPanel heading = Ui.transparent(new BorderLayout());
        JLabel label = Ui.sectionLabel("In class");
        heading.add(label, BorderLayout.WEST);
        studentCountLabel = new JLabel("0");
        studentCountLabel.setFont(Brand.font(Font.BOLD, 11));
        studentCountLabel.setForeground(Brand.PRIMARY_DARK);
        studentCountLabel.setOpaque(true);
        studentCountLabel.setBackground(Brand.PRIMARY_SOFT);
        studentCountLabel.setBorder(new EmptyBorder(2, 8, 2, 8));
        heading.add(studentCountLabel, BorderLayout.EAST);
        card.add(heading, BorderLayout.NORTH);

        studentListPanel = new JPanel();
        studentListPanel.setOpaque(false);
        studentListPanel.setLayout(new BoxLayout(studentListPanel, BoxLayout.Y_AXIS));
        JScrollPane scroll = Ui.scroll(studentListPanel);
        scroll.setPreferredSize(new Dimension(200, 300));
        card.add(scroll, BorderLayout.CENTER);

        JPanel bottom = Ui.transparent(new BorderLayout(0, 8));
        bottom.setBorder(new EmptyBorder(8, 0, 0, 0));

        JPanel address = new RoundedPanel(new Color(0xF4F8F8), null, 12, false);
        address.setLayout(new BorderLayout(0, 4));
        address.setBorder(new EmptyBorder(10, 11, 10, 11));
        JLabel share = new JLabel("SHARE THIS ADDRESS");
        share.setFont(Brand.font(Font.BOLD, 9));
        share.setForeground(Brand.MUTED);
        address.add(share, BorderLayout.NORTH);
        JLabel addr = new JLabel("Starting...");
        addr.setFont(Brand.font(Font.BOLD, 12));
        addr.setForeground(Brand.INK);
        sidebarAddressLabel = addr;
        address.add(addr, BorderLayout.CENTER);
        sameComputerLabel = new JLabel(" ");
        sameComputerLabel.setFont(Brand.font(Font.PLAIN, 9));
        sameComputerLabel.setForeground(Brand.MUTED);
        address.add(sameComputerLabel, BorderLayout.SOUTH);
        bottom.add(address, BorderLayout.CENTER);

        JButton end = Ui.dangerButton("End class");
        end.setPreferredSize(new Dimension(0, 36));
        end.addActionListener(e -> endClass());
        bottom.add(end, BorderLayout.SOUTH);
        card.add(bottom, BorderLayout.SOUTH);
        return card;
    }

    private JTabbedPane buildTabs() {
        tabs = new JTabbedPane();
        Ui.styleTabs(tabs);
        tabs.addTab("Announcements", buildAnnouncementsTab());
        tabs.addTab("MCQ & Results", buildQuizTab());
        tabs.addTab("Notes", buildNotesTab());
        return tabs;
    }

    // ---------------------------------------------------------- announcements

    private JPanel buildAnnouncementsTab() {
        JPanel page = Ui.transparent(new BorderLayout(0, 14));
        page.setBorder(new EmptyBorder(16, 0, 0, 0));

        RoundedPanel composer = Ui.card(new BorderLayout(0, 10));
        JPanel heading = Ui.transparent(new BorderLayout());
        heading.add(Ui.heading("Send an announcement", 17), BorderLayout.WEST);
        announcementCountLabel = new JLabel("0 sent");
        announcementCountLabel.setFont(Brand.font(Font.PLAIN, 11));
        announcementCountLabel.setForeground(Brand.MUTED);
        heading.add(announcementCountLabel, BorderLayout.EAST);
        composer.add(heading, BorderLayout.NORTH);

        announcementInput = Ui.textArea(3);
        JScrollPane inputScroll = Ui.textAreaScroll(announcementInput);
        inputScroll.setPreferredSize(new Dimension(0, 82));
        composer.add(inputScroll, BorderLayout.CENTER);

        JPanel footer = Ui.transparent(new BorderLayout());
        announcementCharCount = new JLabel("0 / 1000");
        announcementCharCount.setFont(Brand.font(Font.PLAIN, 10));
        announcementCharCount.setForeground(Brand.MUTED);
        footer.add(announcementCharCount, BorderLayout.WEST);
        JButton send = Ui.primaryButton("Send to class");
        send.addActionListener(e -> sendAnnouncement());
        footer.add(send, BorderLayout.EAST);
        composer.add(footer, BorderLayout.SOUTH);
        announcementInput.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateAnnouncementCount();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateAnnouncementCount();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateAnnouncementCount();
            }
        });
        page.add(composer, BorderLayout.NORTH);

        RoundedPanel history = Ui.card(new BorderLayout(0, 10));
        JPanel h = Ui.transparent(new BorderLayout());
        h.add(Ui.heading("Recent announcements", 15), BorderLayout.WEST);
        JLabel note = Ui.subheading("Newest first");
        h.add(note, BorderLayout.EAST);
        history.add(h, BorderLayout.NORTH);

        announcementListPanel = new JPanel();
        announcementListPanel.setOpaque(false);
        announcementListPanel.setLayout(new BoxLayout(announcementListPanel, BoxLayout.Y_AXIS));
        JScrollPane scroll = Ui.cardScroll(announcementListPanel);
        history.add(scroll, BorderLayout.CENTER);
        page.add(history, BorderLayout.CENTER);
        refreshAnnouncements();
        return page;
    }

    private void updateAnnouncementCount() {
        if (announcementCharCount != null) {
            announcementCharCount.setText(announcementInput.getText().length() + " / 1000");
        }
    }

    private void sendAnnouncement() {
        try {
            if (server == null) {
                throw new IllegalStateException("Start the class first.");
            }
            server.sendAnnouncement(announcementInput.getText());
            announcementInput.setText("");
            setStatus("Announcement sent.");
        } catch (RuntimeException ex) {
            Ui.showError(this, "Could not send announcement", ex.getMessage());
        }
    }

    private void refreshAnnouncements() {
        if (announcementListPanel == null) {
            return;
        }
        announcementListPanel.removeAll();
        if (announcementCountLabel != null) {
            announcementCountLabel.setText(announcements.size() + " sent");
        }
        if (announcements.isEmpty()) {
            JPanel empty = Ui.emptyState("Nothing announced yet",
                    "Your first announcement will appear here and on every student screen.");
            empty.setMaximumSize(new Dimension(Integer.MAX_VALUE, 140));
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
        JLabel text = Ui.wrap(announcement.text(), 610, Brand.font(Font.PLAIN, 13), Brand.INK);
        card.add(text, BorderLayout.CENTER);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height + 4));
        return card;
    }

    // ------------------------------------------------------------------ quiz

    private JPanel buildQuizTab() {
        JPanel page = Ui.transparent(new BorderLayout());
        page.setBorder(new EmptyBorder(16, 0, 0, 0));
        javax.swing.JSplitPane split = new javax.swing.JSplitPane(
                javax.swing.JSplitPane.HORIZONTAL_SPLIT,
                buildQuizComposer(), buildResultsCard());
        split.setBorder(null);
        split.setOpaque(false);
        split.setDividerSize(14);
        split.setResizeWeight(0.49);
        split.setContinuousLayout(true);
        page.add(split, BorderLayout.CENTER);
        return page;
    }

    private JPanel buildQuizComposer() {
        RoundedPanel card = Ui.card(new BorderLayout(0, 12));

        JPanel heading = Ui.transparent(new BorderLayout());
        heading.add(Ui.heading("Ask an MCQ", 17), BorderLayout.WEST);
        JLabel hint = Ui.subheading("Starts the countdown");
        heading.add(hint, BorderLayout.EAST);
        card.add(heading, BorderLayout.NORTH);

        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));

        JLabel questionLabel = formLabel("Question");
        form.add(questionLabel);
        form.add(Box.createVerticalStrut(5));
        questionField = Ui.field();
        questionField.setToolTipText("Question");
        questionField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        questionField.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(questionField);
        form.add(Box.createVerticalStrut(14));

        JLabel optionsLabel = formLabel("Answer options - select the correct one");
        form.add(optionsLabel);
        form.add(Box.createVerticalStrut(7));
        ButtonGroup group = new ButtonGroup();
        for (int i = 0; i < 4; i++) {
            JPanel row = Ui.transparent(new BorderLayout(8, 0));
            JRadioButton radio = new JRadioButton(String.valueOf((char) ('A' + i)));
            radio.setOpaque(false);
            radio.setFont(Brand.font(Font.BOLD, 12));
            radio.setForeground(Brand.PRIMARY_DARK);
            radio.setFocusPainted(false);
            radio.setPreferredSize(new Dimension(34, 30));
            radio.setToolTipText("Mark option " + (char) ('A' + i) + " as correct");
            group.add(radio);
            optionRadios[i] = radio;
            row.add(radio, BorderLayout.WEST);
            JTextField field = Ui.field();
            field.setToolTipText("Option " + (char) ('A' + i));
            optionFields[i] = field;
            row.add(field, BorderLayout.CENTER);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            form.add(row);
            if (i < 3) {
                form.add(Box.createVerticalStrut(6));
            }
        }
        optionRadios[0].setSelected(true);
        form.add(Box.createVerticalStrut(14));

        JPanel durationRow = Ui.transparent(new BorderLayout(10, 0));
        JLabel durationLabel = formLabel("Countdown (seconds)");
        durationRow.add(durationLabel, BorderLayout.WEST);
        durationSpinner = new JSpinner(new SpinnerNumberModel(30, 5, 600, 5));
        Ui.styleSpinner(durationSpinner);
        durationSpinner.setPreferredSize(new Dimension(82, 34));
        durationRow.add(durationSpinner, BorderLayout.EAST);
        durationRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        durationRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(durationRow);

        JLabel help = Ui.wrap("Students see the options immediately. Their answers stay hidden until "
                + "you end the question.", 315, Brand.font(Font.PLAIN, 10), Brand.MUTED);
        help.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(Box.createVerticalStrut(8));
        form.add(help);
        card.add(form, BorderLayout.CENTER);

        sendQuestionButton = Ui.primaryButton("Start question");
        sendQuestionButton.setPreferredSize(new Dimension(0, 40));
        sendQuestionButton.addActionListener(e -> startQuestion());
        card.add(sendQuestionButton, BorderLayout.SOUTH);
        return card;
    }

    private JLabel formLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Brand.font(Font.BOLD, 11));
        label.setForeground(Brand.INK_SOFT);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private JPanel buildResultsCard() {
        RoundedPanel card = Ui.card(new BorderLayout(0, 12));

        JPanel heading = Ui.transparent(new BorderLayout());
        resultsTitle = Ui.heading("Live results", 17);
        heading.add(resultsTitle, BorderLayout.WEST);
        resultsBadge = new PillLabel("READY", new Color(0xEEF2F3), Brand.MUTED);
        heading.add(resultsBadge, BorderLayout.EAST);
        card.add(heading, BorderLayout.NORTH);

        resultsBody = Ui.transparent(new BorderLayout(0, 10));
        card.add(resultsBody, BorderLayout.CENTER);

        JPanel footer = Ui.transparent(new BorderLayout(0, 7));
        resultsFooterLabel = new JLabel(" ");
        resultsFooterLabel.setFont(Brand.font(Font.PLAIN, 10));
        resultsFooterLabel.setForeground(Brand.MUTED);
        footer.add(resultsFooterLabel, BorderLayout.NORTH);
        endQuestionButton = Ui.secondaryButton("End question & reveal");
        endQuestionButton.setPreferredSize(new Dimension(0, 36));
        endQuestionButton.setVisible(false);
        endQuestionButton.addActionListener(e -> {
            if (server != null) {
                server.endQuiz();
            }
        });
        footer.add(endQuestionButton, BorderLayout.SOUTH);
        card.add(footer, BorderLayout.SOUTH);
        showResultsEmpty();
        return card;
    }

    private void startQuestion() {
        try {
            if (server == null) {
                throw new IllegalStateException("Start the class first.");
            }
            String prompt = questionField.getText().trim();
            List<String> options = new ArrayList<>();
            boolean gap = false;
            for (int i = 0; i < optionFields.length; i++) {
                String value = optionFields[i].getText().trim();
                if (value.isEmpty()) {
                    gap = true;
                } else if (gap) {
                    throw new IllegalArgumentException("Fill the options in order from A.");
                } else {
                    options.add(value);
                }
            }
            if (options.size() < 2) {
                throw new IllegalArgumentException("Add at least two answer options.");
            }
            int correct = -1;
            for (int i = 0; i < optionRadios.length; i++) {
                if (optionRadios[i].isSelected() && i < options.size()) {
                    correct = i;
                }
            }
            if (correct < 0) {
                throw new IllegalArgumentException("Select the correct option using the radio buttons.");
            }
            int seconds = (Integer) durationSpinner.getValue();
            server.startQuiz(prompt, options, correct, seconds);
        } catch (RuntimeException ex) {
            Ui.showError(this, "Could not start the question", ex.getMessage());
        }
    }

    private void handleQuizStarted(QuizView quiz) {
        activeQuiz = quiz;
        lastResult = null;
        sendQuestionButton.setEnabled(false);
        sendQuestionButton.setText("Question is live...");
        showActiveResults(quiz, null);
        startCountdownTimer();
    }

    private void handleQuizStats(QuizStats stats) {
        if (lastResult != null && lastResult.quizId().equals(stats.quizId())) {
            return;
        }
        if (activeQuiz == null || !activeQuiz.id().equals(stats.quizId())) {
            return;
        }
        if (countdownLabel == null) {
            showActiveResults(activeQuiz, stats);
            return;
        }
        updateBars(stats.counts(), stats.totalStudents(), stats.answered(), -1);
        resultsBadge.setText(stats.closed() ? "CLOSED" : "LIVE");
        resultsBadge.setColors(stats.closed() ? new Color(0xEEF2F3) : Brand.SUCCESS_SOFT,
                stats.closed() ? Brand.MUTED : Brand.SUCCESS);
        if (stats.closed() && endQuestionButton != null) {
            endQuestionButton.setEnabled(false);
        }
    }

    private void handleQuizResult(QuizResult result) {
        lastResult = result;
        activeQuiz = null;
        if (countdownTimer != null) {
            countdownTimer.stop();
        }
        if (sendQuestionButton != null) {
            sendQuestionButton.setEnabled(true);
            sendQuestionButton.setText("Start question");
        }
        showQuizResult(result);
    }

    private void showResultsEmpty() {
        if (resultsBody == null) {
            return;
        }
        resultsBody.removeAll();
        resultsBody.add(Ui.emptyState("No question is open",
                "Ask an MCQ on the left. Live response counts will appear here."), BorderLayout.CENTER);
        if (resultsTitle != null) {
            resultsTitle.setText("Live results");
        }
        if (resultsBadge != null) {
            resultsBadge.setText("READY");
            resultsBadge.setColors(new Color(0xEEF2F3), Brand.MUTED);
        }
        if (resultsFooterLabel != null) {
            resultsFooterLabel.setText("Students never see the correct answer before you close the question.");
        }
        if (endQuestionButton != null) {
            endQuestionButton.setVisible(false);
        }
        barRows.clear();
        countdownLabel = null;
        answeredLabel = null;
        resultsQuestionLabel = null;
        resultsBody.revalidate();
        resultsBody.repaint();
    }

    private void showActiveResults(QuizView quiz, QuizStats stats) {
        resultsBody.removeAll();
        barRows.clear();

        JPanel top = Ui.transparent(new BorderLayout(0, 8));
        JPanel timerRow = Ui.transparent(new BorderLayout());
        countdownLabel = new JLabel("00:00");
        countdownLabel.setFont(Brand.font(Font.BOLD, 30));
        countdownLabel.setForeground(Brand.PRIMARY_DARK);
        timerRow.add(countdownLabel, BorderLayout.WEST);
        JLabel seconds = new JLabel("seconds left");
        seconds.setFont(Brand.font(Font.PLAIN, 10));
        seconds.setForeground(Brand.MUTED);
        JPanel timerText = Ui.transparent(new BorderLayout());
        timerText.add(countdownLabel, BorderLayout.NORTH);
        timerText.add(seconds, BorderLayout.SOUTH);
        timerRow.add(timerText, BorderLayout.WEST);
        answeredLabel = new JLabel("0 of " + quiz.totalStudents() + " answered");
        answeredLabel.setFont(Brand.font(Font.BOLD, 10));
        answeredLabel.setForeground(Brand.PRIMARY_DARK);
        timerRow.add(answeredLabel, BorderLayout.EAST);
        top.add(timerRow, BorderLayout.NORTH);

        resultsQuestionLabel = Ui.wrap(quiz.prompt(), 315,
                Brand.font(Font.BOLD, 14), Brand.INK);
        top.add(resultsQuestionLabel, BorderLayout.CENTER);
        resultsBody.add(top, BorderLayout.NORTH);

        JPanel bars = new JPanel();
        bars.setOpaque(false);
        bars.setLayout(new BoxLayout(bars, BoxLayout.Y_AXIS));
        for (int i = 0; i < quiz.options().size(); i++) {
            BarRow row = new BarRow(i, quiz.options().get(i));
            barRows.add(row);
            bars.add(row.panel);
            bars.add(Box.createVerticalStrut(9));
        }
        resultsBody.add(bars, BorderLayout.CENTER);

        if (resultsTitle != null) {
            resultsTitle.setText("Live results");
        }
        resultsBadge.setText("LIVE");
        resultsBadge.setColors(Brand.SUCCESS_SOFT, Brand.SUCCESS);
        resultsFooterLabel.setText("Answers are visible only on this screen until you reveal them.");
        endQuestionButton.setVisible(true);
        endQuestionButton.setEnabled(true);
        endQuestionButton.setPreferredSize(new Dimension(0, 36));

        if (stats != null) {
            updateBars(stats.counts(), stats.totalStudents(), stats.answered(), -1);
        } else {
            updateBars(new int[quiz.options().size()], quiz.totalStudents(), 0, -1);
        }
        resultsBody.revalidate();
        resultsBody.repaint();
    }

    private void showQuizResult(QuizResult result) {
        resultsBody.removeAll();
        barRows.clear();

        JPanel top = Ui.transparent(new BorderLayout(0, 8));
        JPanel meta = Ui.transparent(new BorderLayout());
        JLabel finalLabel = new JLabel("QUESTION CLOSED");
        finalLabel.setFont(Brand.font(Font.BOLD, 10));
        finalLabel.setForeground(Brand.MUTED);
        meta.add(finalLabel, BorderLayout.WEST);
        JLabel answered = new JLabel(result.answered() + " of " + result.totalStudents() + " answered");
        answered.setFont(Brand.font(Font.BOLD, 10));
        answered.setForeground(Brand.INK_SOFT);
        meta.add(answered, BorderLayout.EAST);
        top.add(meta, BorderLayout.NORTH);
        top.add(Ui.wrap(result.prompt(), 315, Brand.font(Font.BOLD, 14), Brand.INK),
                BorderLayout.CENTER);
        resultsBody.add(top, BorderLayout.NORTH);

        JPanel bars = new JPanel();
        bars.setOpaque(false);
        bars.setLayout(new BoxLayout(bars, BoxLayout.Y_AXIS));
        for (int i = 0; i < result.options().size(); i++) {
            BarRow row = new BarRow(i, result.options().get(i));
            row.setCorrect(i == result.correctIndex());
            row.setCounts(result.counts()[i], result.totalStudents());
            barRows.add(row);
            bars.add(row.panel);
            bars.add(Box.createVerticalStrut(9));
        }
        resultsBody.add(bars, BorderLayout.CENTER);

        resultsTitle.setText("Final results");
        resultsBadge.setText("SHARED");
        resultsBadge.setColors(Brand.SUCCESS_SOFT, Brand.SUCCESS);
        resultsFooterLabel.setText("Correct answer: "
                + (char) ('A' + result.correctIndex()) + ". Results are now visible to students.");
        endQuestionButton.setVisible(false);
        resultsBody.revalidate();
        resultsBody.repaint();
    }

    private void updateBars(int[] counts, int totalStudents, int answered, int highlighted) {
        if (answeredLabel != null) {
            answeredLabel.setText(answered + " of " + totalStudents + " answered");
        }
        for (int i = 0; i < barRows.size(); i++) {
            BarRow row = barRows.get(i);
            row.setCounts(i < counts.length ? counts[i] : 0, totalStudents);
            if (highlighted >= 0) {
                row.setCorrect(i == highlighted);
            }
        }
    }

    private void startCountdownTimer() {
        if (countdownTimer != null) {
            countdownTimer.stop();
        }
        countdownTimer = new Timer(250, e -> updateCountdown());
        countdownTimer.start();
        updateCountdown();
    }

    private void updateCountdown() {
        QuizView quiz = activeQuiz;
        if (quiz == null || countdownLabel == null) {
            if (countdownTimer != null) {
                countdownTimer.stop();
            }
            return;
        }
        long remaining = Math.max(0, quiz.endsAt() - System.currentTimeMillis());
        long seconds = (remaining + 999) / 1000;
        countdownLabel.setText(String.format("%02d:%02d", seconds / 60, seconds % 60));
        if (remaining <= 0) {
            countdownTimer.stop();
            resultsBadge.setText("CLOSING");
            resultsBadge.setColors(new Color(0xEEF2F3), Brand.MUTED);
            if (endQuestionButton != null) {
                endQuestionButton.setEnabled(false);
            }
        }
    }

    private final class BarRow {
        private final JPanel panel;
        private final JLabel optionLabel;
        private final JLabel countLabel;
        private final MiniBar bar;

        private BarRow(int index, String text) {
            panel = Ui.transparent(new BorderLayout(0, 5));
            JPanel top = Ui.transparent(new BorderLayout(8, 0));
            optionLabel = new JLabel((char) ('A' + index) + ". " + text);
            optionLabel.setFont(Brand.font(Font.PLAIN, 11));
            optionLabel.setForeground(Brand.INK_SOFT);
            optionLabel.setToolTipText(text);
            top.add(optionLabel, BorderLayout.CENTER);
            countLabel = new JLabel("0");
            countLabel.setFont(Brand.font(Font.BOLD, 11));
            countLabel.setForeground(Brand.MUTED);
            top.add(countLabel, BorderLayout.EAST);
            panel.add(top, BorderLayout.NORTH);
            bar = new MiniBar(Brand.PRIMARY);
            bar.setPreferredSize(new Dimension(10, 7));
            panel.add(bar, BorderLayout.CENTER);
            panel.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        }

        private void setCounts(int count, int total) {
            countLabel.setText(String.valueOf(count));
            bar.setValues(count, total);
        }

        private void setCorrect(boolean correct) {
            optionLabel.setForeground(correct ? Brand.SUCCESS : Brand.INK_SOFT);
            optionLabel.setFont(Brand.font(correct ? Font.BOLD : Font.PLAIN, 11));
            bar.setFillColor(correct ? Brand.SUCCESS : Brand.PRIMARY);
            if (correct) {
                optionLabel.setText("✓  " + optionLabel.getText());
            }
        }
    }

    // ----------------------------------------------------------------- notes

    private JPanel buildNotesTab() {
        JPanel page = Ui.transparent(new BorderLayout(0, 14));
        page.setBorder(new EmptyBorder(16, 0, 0, 0));

        RoundedPanel composer = Ui.card(new BorderLayout(0, 10));
        JPanel heading = Ui.transparent(new BorderLayout());
        heading.add(Ui.heading("Share class notes", 17), BorderLayout.WEST);
        notesCountLabel = new JLabel("0 files");
        notesCountLabel.setFont(Brand.font(Font.PLAIN, 11));
        notesCountLabel.setForeground(Brand.MUTED);
        heading.add(notesCountLabel, BorderLayout.EAST);
        composer.add(heading, BorderLayout.NORTH);

        JPanel picker = Ui.transparent(new BorderLayout(12, 0));
        selectedNoteLabel = new JLabel("No file selected");
        selectedNoteLabel.setFont(Brand.font(Font.PLAIN, 12));
        selectedNoteLabel.setForeground(Brand.MUTED);
        picker.add(selectedNoteLabel, BorderLayout.CENTER);
        JButton choose = Ui.secondaryButton("Choose file");
        choose.addActionListener(e -> chooseNoteFile());
        picker.add(choose, BorderLayout.EAST);
        composer.add(picker, BorderLayout.CENTER);

        JPanel footer = Ui.transparent(new BorderLayout());
        JLabel hint = new JLabel("PDF, DOCX, PPTX, images or text - up to 20 MB.");
        hint.setFont(Brand.font(Font.PLAIN, 10));
        hint.setForeground(Brand.MUTED);
        footer.add(hint, BorderLayout.WEST);
        sendNoteButton = Ui.primaryButton("Share with class");
        sendNoteButton.setEnabled(false);
        sendNoteButton.addActionListener(e -> sendNote());
        footer.add(sendNoteButton, BorderLayout.EAST);
        composer.add(footer, BorderLayout.SOUTH);
        page.add(composer, BorderLayout.NORTH);

        RoundedPanel listCard = Ui.card(new BorderLayout(0, 10));
        JPanel h = Ui.transparent(new BorderLayout());
        h.add(Ui.heading("Shared files", 15), BorderLayout.WEST);
        h.add(Ui.subheading("Available to download"), BorderLayout.EAST);
        listCard.add(h, BorderLayout.NORTH);
        notesListPanel = new JPanel();
        notesListPanel.setOpaque(false);
        notesListPanel.setLayout(new BoxLayout(notesListPanel, BoxLayout.Y_AXIS));
        listCard.add(Ui.cardScroll(notesListPanel), BorderLayout.CENTER);
        page.add(listCard, BorderLayout.CENTER);
        refreshNotes();
        return page;
    }

    private void chooseNoteFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose notes to share");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        if (file == null || !file.isFile()) {
            Ui.showError(this, "File not found", "That file could not be read.");
            return;
        }
        if (file.length() > ClassroomServer.MAX_NOTE_BYTES) {
            Ui.showError(this, "File too large",
                    "Please choose a file smaller than 20 MB.");
            return;
        }
        selectedNoteFile = file;
        selectedNoteLabel.setText(file.getName() + "  •  " + Ui.bytes(file.length()));
        selectedNoteLabel.setForeground(Brand.INK);
        sendNoteButton.setEnabled(true);
    }

    private void sendNote() {
        if (selectedNoteFile == null || server == null) {
            return;
        }
        try {
            server.addNote(selectedNoteFile);
            selectedNoteFile = null;
            selectedNoteLabel.setText("No file selected");
            selectedNoteLabel.setForeground(Brand.MUTED);
            sendNoteButton.setEnabled(false);
            setStatus("Notes shared with the class.");
        } catch (IOException | RuntimeException ex) {
            Ui.showError(this, "Could not share file", ex.getMessage());
        }
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
            JPanel empty = Ui.emptyState("No notes shared",
                    "Choose a file above to make it available to every student in the class.");
            empty.setMaximumSize(new Dimension(Integer.MAX_VALUE, 140));
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
        badge.setBackground(Brand.PRIMARY_SOFT);
        badge.setForeground(Brand.PRIMARY_DARK);
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

        JLabel available = new JLabel("Shared");
        available.setFont(Brand.font(Font.BOLD, 10));
        available.setForeground(Brand.SUCCESS);
        row.add(available, BorderLayout.EAST);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 62));
        return row;
    }

    private String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1 && name.length() - dot <= 6) {
            return name.substring(dot + 1).toUpperCase();
        }
        return "FILE";
    }

    // --------------------------------------------------------------- run/stop

    private void startClass() {
        setupError.setText(" ");
        String className = classField.getText().trim();
        String teacherName = teacherField.getText().trim();
        if (className.isEmpty()) {
            setupError.setText("Enter a class name.");
            classField.requestFocusInWindow();
            return;
        }
        if (teacherName.isEmpty()) {
            setupError.setText("Enter the teacher's name.");
            teacherField.requestFocusInWindow();
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

        startButton.setEnabled(false);
        startButton.setText("Starting...");
        ClassroomServer newServer = new ClassroomServer(className, teacherName, port, serverListener);
        server = newServer;
        try {
            newServer.start();
            classLive = true;
            cardLayout.show(cardHost, LIVE_CARD);
            setStatus("Class started. Waiting for students to join.");
        } catch (BindException ex) {
            server = null;
            startButton.setEnabled(true);
            startButton.setText("Start class");
            setupError.setText("Port " + port + " is already in use. Try another port.");
            Ui.showError(this, "Could not start class", "Port " + port
                    + " is already in use. Close the other program or choose another port.");
        } catch (IOException | RuntimeException ex) {
            server = null;
            startButton.setEnabled(true);
            startButton.setText("Start class");
            setupError.setText(ex.getMessage() == null ? "Could not start the server." : ex.getMessage());
        }
    }

    private void handleStarted(int port, List<String> addresses) {
        serverAddresses = new ArrayList<>(addresses);
        String primary = addresses.isEmpty() ? "127.0.0.1" : addresses.get(0);
        serverAddress = primary + ":" + port;
        if (headerClassName != null) {
            headerClassName.setText(server.getClassName());
            headerTeacherName.setText("Taught by " + server.getTeacherName());
        }
        if (headerAddressLabel != null) {
            headerAddressLabel.setText(serverAddress);
        }
        if (sidebarAddressLabel != null) {
            sidebarAddressLabel.setText(serverAddress);
        }
        if (sameComputerLabel != null) {
            sameComputerLabel.setText("On this PC: 127.0.0.1:" + port);
        }
        setStatus("Class is live at " + serverAddress + ". Share this address with students.");
    }

    private void copyAddress() {
        String text = serverAddress.isBlank() ? headerAddressLabel.getText() : serverAddress;
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(text), null);
            setStatus("Server address copied: " + text);
        } catch (RuntimeException ex) {
            Ui.showError(this, "Clipboard unavailable",
                    "Copy the address manually: " + text);
        }
    }

    private void refreshStudents(List<StudentInfo> students) {
        if (studentListPanel == null) {
            return;
        }
        studentListPanel.removeAll();
        if (studentCountLabel != null) {
            studentCountLabel.setText(String.valueOf(students.size()));
        }
        if (headerStudentCount != null) {
            headerStudentCount.setText(students.size()
                    + (students.size() == 1 ? " student" : " students"));
        }
        if (students.isEmpty()) {
            JPanel empty = Ui.emptyState("Waiting for students",
                    "Ask students to open ClassPulse, choose Join a class, and enter the server address.");
            empty.setMaximumSize(new Dimension(Integer.MAX_VALUE, 170));
            studentListPanel.add(empty);
        } else {
            for (StudentInfo student : students) {
                studentListPanel.add(studentRow(student));
                studentListPanel.add(Box.createVerticalStrut(3));
            }
        }
        studentListPanel.add(Box.createVerticalGlue());
        studentListPanel.revalidate();
        studentListPanel.repaint();
    }

    private JPanel studentRow(StudentInfo student) {
        JPanel row = Ui.transparent(new BorderLayout(8, 0));
        row.setBorder(new EmptyBorder(5, 3, 5, 3));
        row.add(new Avatar(student.name(), 30), BorderLayout.WEST);
        JPanel words = Ui.transparent(new BorderLayout());
        JLabel name = new JLabel(student.name());
        name.setFont(Brand.font(Font.BOLD, 12));
        name.setForeground(Brand.INK);
        words.add(name, BorderLayout.NORTH);
        JLabel joined = new JLabel("Joined " + Ui.formatTime(student.joinedAt()));
        joined.setFont(Brand.font(Font.PLAIN, 9));
        joined.setForeground(Brand.MUTED);
        words.add(joined, BorderLayout.SOUTH);
        row.add(words, BorderLayout.CENTER);
        JLabel online = new JLabel("●");
        online.setFont(Brand.font(Font.BOLD, 9));
        online.setForeground(Brand.SUCCESS);
        row.add(online, BorderLayout.EAST);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 45));
        return row;
    }

    private JPanel buildStatusBar() {
        JPanel bar = Ui.transparent(new BorderLayout());
        bar.setBorder(new EmptyBorder(8, 4, 0, 4));
        statusLabel = new JLabel("Starting...");
        statusLabel.setFont(Brand.font(Font.PLAIN, 10));
        statusLabel.setForeground(Brand.MUTED);
        bar.add(statusLabel, BorderLayout.WEST);
        JLabel save = new JLabel("Session log is saved when the class ends.");
        save.setFont(Brand.font(Font.PLAIN, 10));
        save.setForeground(Brand.MUTED);
        bar.add(save, BorderLayout.EAST);
        return bar;
    }

    private void setStatus(String message) {
        if (statusLabel != null) {
            statusLabel.setText(message);
        }
    }

    private void endClass() {
        if (!classLive) {
            return;
        }
        if (!Ui.confirm(this, "End class",
                "End the class for everyone? Connected students will be disconnected.")) {
            return;
        }
        ClassroomServer current = server;
        classLive = false;
        returningToLauncher = true;
        if (current != null) {
            current.stop("Class ended by the teacher.");
        }
        server = null;
        if (countdownTimer != null) {
            countdownTimer.stop();
        }
        dispose();
        launcher.showAgain();
    }

    private void closeTeacherWindow() {
        if (classLive) {
            if (!Ui.confirm(this, "Close teacher console",
                    "The class is still live. End it and close the teacher console?")) {
                return;
            }
            classLive = false;
            returningToLauncher = true;
            ClassroomServer current = server;
            server = null;
            if (current != null) {
                current.stop("Class ended when the teacher closed the app.");
            }
            if (countdownTimer != null) {
                countdownTimer.stop();
            }
            dispose();
            launcher.showAgain();
        } else {
            dispose();
            launcher.showAgain();
        }
    }

    private void onEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            SwingUtilities.invokeLater(action);
        }
    }
}
