package classpulse.net;

import classpulse.model.Announcement;
import classpulse.model.AnswerReceipt;
import classpulse.model.JoinInfo;
import classpulse.model.Message;
import classpulse.model.MessageType;
import classpulse.model.NoteData;
import classpulse.model.NoteSummary;
import classpulse.model.QuizAnswer;
import classpulse.model.QuizResult;
import classpulse.model.QuizStats;
import classpulse.model.QuizView;
import classpulse.model.StudentInfo;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.BindException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The teacher's embedded server. It owns the student sockets, the question
 * timer and the in-memory class history.
 */
public final class ClassroomServer {
    public static final int MAX_NOTE_BYTES = 20 * 1024 * 1024;
    public static final int MAX_ANNOUNCEMENT_CHARS = 1000;

    private final String className;
    private final String teacherName;
    private final int requestedPort;
    private final ServerListener listener;

    private final List<ClientSession> sessions = new CopyOnWriteArrayList<>();
    private final List<Announcement> announcements = new CopyOnWriteArrayList<>();
    private final List<NoteData> notes = new CopyOnWriteArrayList<>();
    private final List<QuizResult> quizHistory = new CopyOnWriteArrayList<>();
    private final Set<String> normalisedNames = new HashSet<>();
    private final Object rosterLock = new Object();
    private final Object quizLock = new Object();

    private volatile boolean running;
    private volatile int port;
    private volatile ServerSocket serverSocket;
    private volatile Thread acceptThread;
    private volatile ExecutorService clientPool;
    private volatile ScheduledExecutorService scheduler;
    private volatile ActiveQuiz activeQuiz;
    private volatile ScheduledFuture<?> quizTimer;
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private long startedAt = System.currentTimeMillis();

    public ClassroomServer(String className, String teacherName, int port, ServerListener listener) {
        if (className == null || className.isBlank()) {
            throw new IllegalArgumentException("Class name is required.");
        }
        if (teacherName == null || teacherName.isBlank()) {
            throw new IllegalArgumentException("Teacher name is required.");
        }
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535.");
        }
        this.className = className.trim();
        this.teacherName = teacherName.trim();
        this.requestedPort = port;
        this.listener = listener == null ? new ServerListener() { } : listener;
    }

    public void start() throws IOException {
        synchronized (this) {
            if (running) {
                throw new IllegalStateException("The class is already running.");
            }
            ServerSocket socket = new ServerSocket();
            try {
                socket.setReuseAddress(true);
                socket.bind(new InetSocketAddress(requestedPort));
            } catch (BindException ex) {
                socket.close();
                throw new BindException("Port " + requestedPort + " is already in use. Try another port.");
            }
            serverSocket = socket;
            port = socket.getLocalPort();
            running = true;
            stopped.set(false);
            startedAt = System.currentTimeMillis();

            clientPool = Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "classpulse-client-handler");
                t.setDaemon(true);
                return t;
            });
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "classpulse-quiz-timer");
                t.setDaemon(true);
                return t;
            });
            acceptThread = new Thread(this::acceptLoop, "classpulse-accept");
            acceptThread.setDaemon(true);
            acceptThread.start();
        }
        listener.onStarted(port, localAddresses());
        listener.onLog("Class started on port " + port + ".");
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
                ExecutorService pool = clientPool;
                if (pool != null && !pool.isShutdown()) {
                    pool.execute(() -> handleClient(socket));
                } else {
                    socket.close();
                }
            } catch (SocketException ex) {
                if (running) {
                    listener.onError("The server stopped accepting connections: " + ex.getMessage());
                }
                break;
            } catch (IOException ex) {
                if (running) {
                    listener.onError("Could not accept a student connection: " + ex.getMessage());
                }
            }
        }
    }

    private void handleClient(Socket socket) {
        ClientSession session = null;
        try {
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            session = new ClientSession(socket, in, out);
            while (running && !socket.isClosed()) {
                Object object = in.readObject();
                if (!(object instanceof Message message)) {
                    continue;
                }
                handleMessage(session, message);
            }
        } catch (java.io.EOFException | SocketException ex) {
            // Normal when a student leaves or the class is ended.
        } catch (IOException ex) {
            if (running) {
                listener.onLog("A student connection was interrupted: " + ex.getMessage());
            }
        } catch (ClassNotFoundException ex) {
            listener.onError("A student sent an unsupported packet.");
        } finally {
            if (session != null) {
                removeSession(session, "Student left the class.");
            }
            try {
                socket.close();
            } catch (IOException ignored) {
                // Nothing useful to do while closing.
            }
        }
    }

    private void handleMessage(ClientSession session, Message message) {
        try {
            switch (message.getType()) {
                case JOIN -> handleJoin(session, message);
                case ANSWER -> handleAnswer(session, message);
                case NOTE_DOWNLOAD -> handleNoteDownload(session, message);
                default -> session.send(Message.of(MessageType.ERROR, "Unsupported request."));
            }
        } catch (IOException ex) {
            removeSession(session, "Connection to " + safeName(session) + " was lost.");
        } catch (RuntimeException ex) {
            listener.onError("Could not process a student request: " + ex.getMessage());
            try {
                session.send(Message.of(MessageType.ERROR, "The server could not process that request."));
            } catch (IOException ignored) {
                removeSession(session, "Connection lost.");
            }
        }
    }

    private void handleJoin(ClientSession session, Message message) throws IOException {
        if (!(message.getPayload() instanceof String rawName)) {
            session.send(Message.of(MessageType.JOIN_REJECTED, "Please enter your name."));
            return;
        }
        String name = rawName.trim().replaceAll("\\s+", " ");
        String validation = validateStudentName(name);
        if (validation != null) {
            session.send(Message.of(MessageType.JOIN_REJECTED, validation));
            return;
        }
        String key = name.toLowerCase(Locale.ROOT);
        List<StudentInfo> roster;
        synchronized (rosterLock) {
            if (session.joined) {
                session.send(Message.of(MessageType.JOIN_REJECTED, "You have already joined this class."));
                return;
            }
            if (normalisedNames.contains(key)) {
                session.send(Message.of(MessageType.JOIN_REJECTED,
                        "That name is already in the class. Please use a different name."));
                return;
            }
            normalisedNames.add(key);
            session.name = name;
            session.joined = true;
            session.joinedAt = System.currentTimeMillis();
            sessions.add(session);
            roster = rosterSnapshot();
        }

        session.send(Message.of(MessageType.JOIN_OK,
                new JoinInfo(className, teacherName, session.id, name)));
        for (Announcement announcement : announcements) {
            session.send(Message.of(MessageType.ANNOUNCEMENT, announcement));
        }
        session.send(Message.of(MessageType.NOTE_LIST, noteSummaries()));
        ActiveQuiz quiz = activeQuiz;
        if (quiz != null) {
            session.send(Message.of(MessageType.QUIZ_STARTED, quiz.toView()));
        }

        listener.onStudentsChanged(roster);
        listener.onLog(name + " joined the class.");
    }

    private String validateStudentName(String name) {
        if (name.length() < 2) {
            return "Please use at least 2 characters for your name.";
        }
        if (name.length() > 30) {
            return "Please keep your name under 30 characters.";
        }
        if (!name.matches("[\\p{L}\\p{N} ._'\\-]+")) {
            return "Names can contain letters, numbers, spaces, dots, hyphens and underscores.";
        }
        return null;
    }

    private void handleAnswer(ClientSession session, Message message) throws IOException {
        if (!session.joined) {
            session.send(Message.of(MessageType.ANSWER_RESULT,
                    new AnswerReceipt("", -1, false, "Join the class first.", 0, 0)));
            return;
        }
        if (!(message.getPayload() instanceof QuizAnswer answer)) {
            session.send(Message.of(MessageType.ANSWER_RESULT,
                    new AnswerReceipt("", -1, false, "Invalid answer packet.", 0, 0)));
            return;
        }

        QuizStats stats;
        AnswerReceipt receipt;
        synchronized (quizLock) {
            ActiveQuiz quiz = activeQuiz;
            if (quiz == null || !quiz.id.equals(answer.quizId())) {
                receipt = new AnswerReceipt(answer.quizId(), answer.optionIndex(), false,
                        "That question is no longer open.", 0, 0);
                stats = null;
            } else if (System.currentTimeMillis() > quiz.endsAt) {
                receipt = new AnswerReceipt(answer.quizId(), answer.optionIndex(), false,
                        "Time is up for this question.", quiz.answeredCount(),
                        quiz.totalStudents);
                stats = quiz.toStats(true);
            } else if (answer.optionIndex() < 0 || answer.optionIndex() >= quiz.options.size()) {
                receipt = new AnswerReceipt(answer.quizId(), answer.optionIndex(), false,
                        "Choose one of the available options.", quiz.answeredCount(),
                        quiz.totalStudents);
                stats = null;
            } else if (!quiz.answeredStudentIds.add(session.id)) {
                receipt = new AnswerReceipt(answer.quizId(), answer.optionIndex(), false,
                        "You have already answered this question.", quiz.answeredCount(),
                        quiz.totalStudents);
                stats = null;
            } else {
                quiz.counts[answer.optionIndex()]++;
                receipt = new AnswerReceipt(answer.quizId(), answer.optionIndex(), true,
                        "Answer submitted.", quiz.answeredCount(), quiz.totalStudents);
                stats = quiz.toStats(false);
            }
        }
        session.send(Message.of(MessageType.ANSWER_RESULT, receipt));
        if (stats != null) {
            listener.onQuizStats(stats);
        }
    }

    private void handleNoteDownload(ClientSession session, Message message) throws IOException {
        if (!session.joined) {
            session.send(Message.of(MessageType.ERROR, "Join the class before downloading notes."));
            return;
        }
        if (!(message.getPayload() instanceof String noteId)) {
            session.send(Message.of(MessageType.ERROR, "Invalid note request."));
            return;
        }
        NoteData found = null;
        for (NoteData note : notes) {
            if (note.summary().id().equals(noteId)) {
                found = note;
                break;
            }
        }
        if (found == null) {
            session.send(Message.of(MessageType.ERROR, "That note is no longer available."));
            return;
        }
        session.send(Message.of(MessageType.NOTE_CONTENT, found));
    }

    public Announcement sendAnnouncement(String text) {
        requireRunning();
        String clean = text == null ? "" : text.trim();
        if (clean.isEmpty()) {
            throw new IllegalArgumentException("Write an announcement first.");
        }
        if (clean.length() > MAX_ANNOUNCEMENT_CHARS) {
            throw new IllegalArgumentException("Announcements are limited to "
                    + MAX_ANNOUNCEMENT_CHARS + " characters.");
        }
        Announcement announcement = new Announcement(UUID.randomUUID().toString(), clean,
                System.currentTimeMillis());
        announcements.add(announcement);
        broadcast(Message.of(MessageType.ANNOUNCEMENT, announcement));
        listener.onAnnouncement(announcement);
        listener.onLog("Announcement sent to the class.");
        return announcement;
    }

    public QuizView startQuiz(String prompt, List<String> options, int correctIndex, int durationSeconds) {
        requireRunning();
        String cleanPrompt = prompt == null ? "" : prompt.trim();
        if (cleanPrompt.isEmpty()) {
            throw new IllegalArgumentException("Enter the question text.");
        }
        if (cleanPrompt.length() > 500) {
            throw new IllegalArgumentException("Questions are limited to 500 characters.");
        }
        if (durationSeconds < 5 || durationSeconds > 600) {
            throw new IllegalArgumentException("The countdown must be between 5 and 600 seconds.");
        }
        List<String> cleanOptions = new ArrayList<>();
        if (options != null) {
            for (String option : options) {
                String value = option == null ? "" : option.trim();
                if (!value.isEmpty()) {
                    cleanOptions.add(value);
                }
            }
        }
        if (cleanOptions.size() < 2) {
            throw new IllegalArgumentException("Add at least two answer options.");
        }
        if (correctIndex < 0 || correctIndex >= cleanOptions.size()) {
            throw new IllegalArgumentException("Choose which option is correct.");
        }

        ActiveQuiz quiz;
        QuizView view;
        synchronized (quizLock) {
            if (activeQuiz != null) {
                throw new IllegalStateException("A question is already open. End it before starting another.");
            }
            long now = System.currentTimeMillis();
            quiz = new ActiveQuiz(UUID.randomUUID().toString(), cleanPrompt, cleanOptions,
                    correctIndex, now, now + durationSeconds * 1000L, currentStudentCount());
            activeQuiz = quiz;
            view = quiz.toView();
        }

        broadcast(Message.of(MessageType.QUIZ_STARTED, view));
        listener.onQuizStarted(view);
        listener.onQuizStats(quiz.toStats(false));
        listener.onLog("Question started with a " + durationSeconds + " second countdown.");

        ScheduledExecutorService timerService = scheduler;
        if (timerService != null && !timerService.isShutdown()) {
            quizTimer = timerService.schedule(() -> finishQuiz(quiz.id, "Time is up."),
                    durationSeconds, TimeUnit.SECONDS);
        }
        return view;
    }

    public void endQuiz() {
        ActiveQuiz quiz = activeQuiz;
        if (quiz != null) {
            finishQuiz(quiz.id, "Question closed by the teacher.");
        }
    }

    private void finishQuiz(String quizId, String reason) {
        QuizResult result;
        QuizStats stats;
        synchronized (quizLock) {
            ActiveQuiz quiz = activeQuiz;
            if (quiz == null || !quiz.id.equals(quizId)) {
                return;
            }
            quiz.closed = true;
            result = quiz.toResult();
            stats = quiz.toStats(true);
            activeQuiz = null;
            quizHistory.add(result);
            ScheduledFuture<?> timer = quizTimer;
            if (timer != null) {
                timer.cancel(false);
                quizTimer = null;
            }
        }
        broadcast(Message.of(MessageType.QUIZ_RESULT, result));
        listener.onQuizResult(result);
        listener.onQuizStats(stats);
        listener.onLog(reason + " Results were shared with students.");
    }

    public NoteSummary addNote(File file) throws IOException {
        requireRunning();
        if (file == null || !file.isFile()) {
            throw new IOException("Choose a file to share.");
        }
        long length = file.length();
        if (length <= 0) {
            throw new IOException("That file is empty.");
        }
        if (length > MAX_NOTE_BYTES) {
            throw new IOException("Notes are limited to " + (MAX_NOTE_BYTES / (1024 * 1024)) + " MB.");
        }
        String fileName = file.getName();
        if (fileName.isBlank()) {
            fileName = "class-notes";
        }
        byte[] bytes = Files.readAllBytes(file.toPath());
        NoteSummary summary = new NoteSummary(UUID.randomUUID().toString(), fileName,
                bytes.length, System.currentTimeMillis());
        notes.add(new NoteData(summary, bytes));
        broadcast(Message.of(MessageType.NOTE_LIST, noteSummaries()));
        listener.onNoteAdded(summary);
        listener.onLog("Shared notes: " + fileName + ".");
        return summary;
    }

    public List<StudentInfo> getStudents() {
        synchronized (rosterLock) {
            return rosterSnapshot();
        }
    }

    public List<Announcement> getAnnouncements() {
        return new ArrayList<>(announcements);
    }

    public List<NoteSummary> getNotes() {
        return noteSummaries();
    }

    public int getPort() {
        return port;
    }

    public String getClassName() {
        return className;
    }

    public String getTeacherName() {
        return teacherName;
    }

    public boolean isRunning() {
        return running;
    }

    public void stop(String reason) {
        if (!stopped.compareAndSet(false, true)) {
            return;
        }
        running = false;
        String cleanReason = reason == null || reason.isBlank() ? "The class has ended." : reason;
        broadcast(Message.of(MessageType.SERVER_CLOSED, cleanReason));

        synchronized (quizLock) {
            activeQuiz = null;
            if (quizTimer != null) {
                quizTimer.cancel(false);
                quizTimer = null;
            }
        }

        try {
            ServerSocket socket = serverSocket;
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
            // Closing the server socket is best-effort.
        }

        for (ClientSession session : new ArrayList<>(sessions)) {
            session.close();
        }
        sessions.clear();
        synchronized (rosterLock) {
            normalisedNames.clear();
        }

        ExecutorService pool = clientPool;
        if (pool != null) {
            pool.shutdownNow();
        }
        ScheduledExecutorService timerService = scheduler;
        if (timerService != null) {
            timerService.shutdownNow();
        }

        Path archive = null;
        try {
            archive = SessionStore.save(buildArchive(cleanReason));
            if (archive != null) {
                listener.onLog("Session saved to " + archive + ".");
            }
        } catch (IOException ex) {
            listener.onError("The class ended, but the session log could not be saved: " + ex.getMessage());
        }
        listener.onStopped(cleanReason);
    }

    private SessionArchive buildArchive(String reason) {
        return new SessionArchive(className, teacherName, startedAt, System.currentTimeMillis(),
                reason, getStudents(), new ArrayList<>(announcements),
                new ArrayList<>(quizHistory), noteSummaries());
    }

    private void removeSession(ClientSession session, String logMessage) {
        if (!session.close()) {
            return;
        }
        boolean wasJoined = session.joined;
        if (wasJoined) {
            synchronized (rosterLock) {
                sessions.remove(session);
                normalisedNames.remove(session.name.toLowerCase(Locale.ROOT));
            }
        }
        if (wasJoined) {
            listener.onStudentsChanged(getStudents());
            listener.onLog(logMessage);
        }
    }

    private List<StudentInfo> rosterSnapshot() {
        List<StudentInfo> result = new ArrayList<>();
        for (ClientSession session : sessions) {
            if (session.joined) {
                result.add(new StudentInfo(session.id, session.name, session.joinedAt));
            }
        }
        result.sort((a, b) -> Long.compare(a.joinedAt(), b.joinedAt()));
        return result;
    }

    private List<NoteSummary> noteSummaries() {
        List<NoteSummary> result = new ArrayList<>();
        for (NoteData note : notes) {
            result.add(note.summary());
        }
        return result;
    }

    private int currentStudentCount() {
        synchronized (rosterLock) {
            return sessions.size();
        }
    }

    private void broadcast(Message message) {
        for (ClientSession session : new ArrayList<>(sessions)) {
            if (!session.joined) {
                continue;
            }
            try {
                session.send(message);
            } catch (IOException ex) {
                removeSession(session, "Connection to " + safeName(session) + " was lost.");
            }
        }
    }

    private void requireRunning() {
        if (!running) {
            throw new IllegalStateException("Start the class first.");
        }
    }

    private static String safeName(ClientSession session) {
        return session.name == null ? "a student" : session.name;
    }

    public static List<String> localAddresses() {
        LinkedHashSet<String> addresses = new LinkedHashSet<>();
        try {
            var interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface network = interfaces.nextElement();
                if (!network.isUp() || network.isLoopback() || network.isVirtual()) {
                    continue;
                }
                for (InetAddress address : Collections.list(network.getInetAddresses())) {
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()
                            && !address.isLinkLocalAddress()) {
                        addresses.add(address.getHostAddress());
                    }
                }
            }
        } catch (SocketException ignored) {
            // Fall back to localhost below.
        }
        if (addresses.isEmpty()) {
            addresses.add("127.0.0.1");
        }
        return new ArrayList<>(addresses);
    }

    public static String timestampForFile() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    }

    private static final class ActiveQuiz {
        private final String id;
        private final String prompt;
        private final List<String> options;
        private final int correctIndex;
        private final long startedAt;
        private final long endsAt;
        private final int totalStudents;
        private final int[] counts;
        private final Set<String> answeredStudentIds = new HashSet<>();
        private boolean closed;

        private ActiveQuiz(String id, String prompt, List<String> options, int correctIndex,
                           long startedAt, long endsAt, int totalStudents) {
            this.id = id;
            this.prompt = prompt;
            this.options = List.copyOf(options);
            this.correctIndex = correctIndex;
            this.startedAt = startedAt;
            this.endsAt = endsAt;
            this.totalStudents = totalStudents;
            this.counts = new int[options.size()];
        }

        private int answeredCount() {
            return answeredStudentIds.size();
        }

        private QuizView toView() {
            return new QuizView(id, prompt, options, startedAt, endsAt, totalStudents);
        }

        private QuizStats toStats(boolean isClosed) {
            return new QuizStats(id, prompt, options, counts, answeredCount(), totalStudents,
                    endsAt, isClosed);
        }

        private QuizResult toResult() {
            return new QuizResult(id, prompt, options, correctIndex, counts, totalStudents,
                    answeredCount(), System.currentTimeMillis());
        }
    }

    private static final class ClientSession {
        private final String id = UUID.randomUUID().toString();
        private final Socket socket;
        private final ObjectInputStream in;
        private final ObjectOutputStream out;
        private final Object writeLock = new Object();
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private volatile String name;
        private volatile boolean joined;
        private volatile long joinedAt;

        private ClientSession(Socket socket, ObjectInputStream in, ObjectOutputStream out) {
            this.socket = socket;
            this.in = in;
            this.out = out;
        }

        private void send(Message message) throws IOException {
            synchronized (writeLock) {
                if (closed.get() || socket.isClosed()) {
                    throw new IOException("Connection is closed.");
                }
                out.writeObject(message);
                out.flush();
            }
        }

        private boolean close() {
            if (!closed.compareAndSet(false, true)) {
                return false;
            }
            try {
                socket.close();
            } catch (IOException ignored) {
                // Already closing.
            }
            return true;
        }
    }
}
