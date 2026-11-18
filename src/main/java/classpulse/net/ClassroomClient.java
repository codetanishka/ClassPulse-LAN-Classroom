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
import classpulse.model.QuizView;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A student connection. Socket reads run on a background thread; the listener
 * is responsible for moving UI changes onto the Swing event thread.
 */
public final class ClassroomClient {
    private static final int CONNECT_TIMEOUT_MS = 6_000;
    private static final int JOIN_TIMEOUT_SECONDS = 8;

    private final String host;
    private final int port;
    private final ClientListener listener;
    private final CountDownLatch joinLatch = new CountDownLatch(1);
    private final AtomicReference<JoinInfo> joinInfo = new AtomicReference<>();
    private final AtomicReference<String> joinError = new AtomicReference<>();
    private final AtomicBoolean everConnected = new AtomicBoolean(false);
    private final AtomicBoolean disconnectNotified = new AtomicBoolean(false);
    private final AtomicBoolean userClosed = new AtomicBoolean(false);

    private volatile Socket socket;
    private volatile ObjectOutputStream out;
    private volatile ObjectInputStream in;
    private volatile Thread readerThread;
    private volatile boolean running;

    public ClassroomClient(String host, int port, ClientListener listener) {
        this.host = host == null ? "" : host.trim();
        this.port = port;
        this.listener = listener == null ? new ClientListener() { } : listener;
    }

    /**
     * Connects, performs the JOIN handshake and waits for the teacher's reply.
     */
    public JoinInfo connect(String studentName) throws IOException {
        if (running) {
            throw new IllegalStateException("Already connected.");
        }
        if (studentName == null || studentName.isBlank()) {
            throw new IllegalArgumentException("Please enter your name.");
        }

        Socket newSocket = new Socket();
        try {
            newSocket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            newSocket.setTcpNoDelay(true);
            newSocket.setKeepAlive(true);
            ObjectOutputStream newOut = new ObjectOutputStream(newSocket.getOutputStream());
            newOut.flush();
            ObjectInputStream newIn = new ObjectInputStream(newSocket.getInputStream());
            socket = newSocket;
            out = newOut;
            in = newIn;
            running = true;

            readerThread = new Thread(this::readLoop, "classpulse-client-reader");
            readerThread.setDaemon(true);
            readerThread.start();
            send(Message.of(MessageType.JOIN, studentName.trim()));

            boolean replied = joinLatch.await(JOIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!replied) {
                closeSocket();
                throw new IOException("The teacher's server did not reply. Check the IP address and port.");
            }
            String error = joinError.get();
            if (error != null) {
                closeSocket();
                throw new IOException(error);
            }
            JoinInfo info = joinInfo.get();
            if (info == null) {
                closeSocket();
                throw new IOException("The teacher's server sent an incomplete response.");
            }
            listener.onConnected(info);
            return info;
        } catch (IOException ex) {
            closeSocket();
            if (ex.getMessage() != null && !ex.getMessage().isBlank()) {
                throw ex;
            }
            throw new IOException("Could not join the classroom. Check the connection details.", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            closeSocket();
            throw new IOException("The connection attempt was interrupted.", ex);
        }
    }

    private void readLoop() {
        try {
            while (running) {
                Object object = in.readObject();
                if (object instanceof Message message) {
                    handleMessage(message);
                }
            }
        } catch (java.io.EOFException | java.net.SocketException ex) {
            if (!userClosed.get()) {
                if (everConnected.get()) {
                    notifyDisconnected("The connection to the classroom was closed.");
                } else {
                    joinError.compareAndSet(null, "The connection was closed before the class accepted you.");
                    joinLatch.countDown();
                }
            }
        } catch (IOException ex) {
            if (!userClosed.get()) {
                if (everConnected.get()) {
                    notifyDisconnected("Connection lost: " + ex.getMessage());
                } else {
                    joinError.compareAndSet(null, "The connection was interrupted while joining.");
                    joinLatch.countDown();
                }
            }
        } catch (ClassNotFoundException ex) {
            if (everConnected.get()) {
                notifyDisconnected("The classroom sent an unsupported message.");
            } else {
                joinError.compareAndSet(null, "The classroom sent an unsupported response.");
                joinLatch.countDown();
            }
        } finally {
            running = false;
            closeSocket();
            joinLatch.countDown();
        }
    }

    private void handleMessage(Message message) {
        switch (message.getType()) {
            case JOIN_OK -> {
                if (message.getPayload() instanceof JoinInfo info) {
                    joinInfo.set(info);
                    everConnected.set(true);
                } else {
                    joinError.set("The teacher's server sent an invalid join response.");
                }
                joinLatch.countDown();
            }
            case JOIN_REJECTED -> {
                joinError.set(message.getPayload() instanceof String text ? text : "The teacher rejected the join request.");
                joinLatch.countDown();
            }
            case ANNOUNCEMENT -> {
                if (message.getPayload() instanceof Announcement announcement) {
                    listener.onAnnouncement(announcement);
                }
            }
            case QUIZ_STARTED -> {
                if (message.getPayload() instanceof QuizView quiz) {
                    listener.onQuizStarted(quiz);
                }
            }
            case QUIZ_RESULT -> {
                if (message.getPayload() instanceof QuizResult result) {
                    listener.onQuizResult(result);
                }
            }
            case NOTE_LIST -> {
                if (message.getPayload() instanceof List<?> list) {
                    @SuppressWarnings("unchecked")
                    List<NoteSummary> notes = (List<NoteSummary>) list;
                    listener.onNoteList(notes);
                }
            }
            case NOTE_CONTENT -> {
                if (message.getPayload() instanceof NoteData note) {
                    listener.onNoteContent(note);
                }
            }
            case ANSWER_RESULT -> {
                if (message.getPayload() instanceof AnswerReceipt receipt) {
                    listener.onAnswerReceipt(receipt);
                }
            }
            case ERROR -> listener.onError(message.getPayload() instanceof String text
                    ? text : "The server reported an error.");
            case SERVER_CLOSED -> {
                String reason = message.getPayload() instanceof String text
                        ? text : "The teacher ended the class.";
                notifyDisconnected(reason);
                closeSocket();
            }
            default -> {
                // Ignore packet types that are only used by the server side.
            }
        }
    }

    private void notifyDisconnected(String reason) {
        if (userClosed.get() || !disconnectNotified.compareAndSet(false, true)) {
            return;
        }
        connectLost();
        listener.onDisconnected(reason == null || reason.isBlank()
                ? "The connection to the classroom was closed." : reason);
    }

    private void connectLost() {
        running = false;
        closeSocket();
    }

    public void sendAnswer(String quizId, int optionIndex) throws IOException {
        send(Message.of(MessageType.ANSWER, new QuizAnswer(quizId, optionIndex)));
    }

    public void requestNote(String noteId) throws IOException {
        send(Message.of(MessageType.NOTE_DOWNLOAD, noteId));
    }

    private void send(Message message) throws IOException {
        ObjectOutputStream stream = out;
        Socket currentSocket = socket;
        if (!running || stream == null || currentSocket == null || currentSocket.isClosed()) {
            throw new IOException("You are not connected to the classroom.");
        }
        synchronized (stream) {
            stream.writeObject(message);
            stream.flush();
        }
    }

    public void disconnect() {
        userClosed.set(true);
        running = false;
        closeSocket();
    }

    private void closeSocket() {
        running = false;
        Socket current = socket;
        socket = null;
        if (current != null) {
            try {
                current.close();
            } catch (IOException ignored) {
                // Already closed.
            }
        }
    }

    public boolean isConnected() {
        Socket current = socket;
        return running && current != null && !current.isClosed();
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }
}
