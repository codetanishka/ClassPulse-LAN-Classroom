package classpulse;

import classpulse.model.Announcement;
import classpulse.model.AnswerReceipt;
import classpulse.model.JoinInfo;
import classpulse.model.NoteData;
import classpulse.model.NoteSummary;
import classpulse.model.QuizResult;
import classpulse.model.QuizStats;
import classpulse.model.QuizView;
import classpulse.model.StudentInfo;
import classpulse.net.ClassroomClient;
import classpulse.net.ClassroomServer;
import classpulse.net.ClientListener;
import classpulse.net.ServerListener;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Headless integration test for the socket protocol. It starts a real server,
 * joins two real clients, exchanges announcements, a quiz, answers and a note.
 */
public final class NetworkSelfTest {
    private static final long WAIT_SECONDS = 8;

    public static void main(String[] args) throws Exception {
        Path noteFile = Files.createTempFile("classpulse-test-", ".txt");
        Files.writeString(noteFile, "ClassPulse note transfer test.", StandardCharsets.UTF_8);

        ServerProbe serverProbe = new ServerProbe();
        ClassroomServer server = new ClassroomServer("Self Test Class", "Test Teacher", 0, serverProbe);
        ClientProbe studentA = new ClientProbe();
        ClientProbe studentB = new ClientProbe();
        ClassroomClient clientA = null;
        ClassroomClient clientB = null;

        try {
            server.start();
            require(serverProbe.started.await(WAIT_SECONDS, TimeUnit.SECONDS), "Server did not start.");
            require(server.getPort() > 0, "Server did not bind a real port.");
            System.out.println("PASS  server started on port " + server.getPort());

            clientA = new ClassroomClient("127.0.0.1", server.getPort(), studentA);
            clientB = new ClassroomClient("127.0.0.1", server.getPort(), studentB);
            JoinInfo joinA = clientA.connect("Aarav");
            JoinInfo joinB = clientB.connect("Meera");
            require(joinA.className().equals("Self Test Class"), "Student A received the wrong class.");
            require(joinB.teacherName().equals("Test Teacher"), "Student B received the wrong teacher.");
            require(studentA.connected.await(WAIT_SECONDS, TimeUnit.SECONDS), "Student A listener not connected.");
            require(studentB.connected.await(WAIT_SECONDS, TimeUnit.SECONDS), "Student B listener not connected.");
            require(serverProbe.studentsTwo.await(WAIT_SECONDS, TimeUnit.SECONDS), "Roster did not reach two students.");
            System.out.println("PASS  two students joined and the roster updated");

            verifyDuplicateNameIsRejected(server.getPort(), "Aarav");
            System.out.println("PASS  duplicate student name was rejected");

            server.sendAnnouncement("Welcome to the self test.");
            require(studentA.announcement.await(WAIT_SECONDS, TimeUnit.SECONDS), "Student A missed the announcement.");
            require(studentB.announcement.await(WAIT_SECONDS, TimeUnit.SECONDS), "Student B missed the announcement.");
            require(studentA.lastAnnouncement.get().text().contains("self test"),
                    "Announcement text was not delivered intact.");
            System.out.println("PASS  announcement delivered to both students");

            List<String> options = List.of("3", "4", "5", "6");
            QuizView quiz = server.startQuiz("What is 2 + 2?", options, 1, 10);
            require(studentA.quiz.await(WAIT_SECONDS, TimeUnit.SECONDS), "Student A missed the question.");
            require(studentB.quiz.await(WAIT_SECONDS, TimeUnit.SECONDS), "Student B missed the question.");
            require(studentA.lastQuiz.get().options().equals(options), "Question options changed in transit.");
            require(studentA.lastQuiz.get().endsAt() - studentA.lastQuiz.get().startedAt() == 10_000,
                    "Countdown duration was not 10 seconds.");
            System.out.println("PASS  live question and countdown reached both students");

            clientA.sendAnswer(quiz.id(), 0);
            clientB.sendAnswer(quiz.id(), 1);
            require(studentA.receipt.await(WAIT_SECONDS, TimeUnit.SECONDS), "Student A did not receive an answer receipt.");
            require(studentB.receipt.await(WAIT_SECONDS, TimeUnit.SECONDS), "Student B did not receive an answer receipt.");
            require(studentA.lastReceipt.get().accepted(), "Student A answer was rejected.");
            require(studentB.lastReceipt.get().accepted(), "Student B answer was rejected.");
            require(serverProbe.statsTwoAnswers.await(WAIT_SECONDS, TimeUnit.SECONDS),
                    "Teacher did not receive live answer statistics.");
            require(serverProbe.lastStats.get().answered() == 2, "Live count did not reach two.");
            System.out.println("PASS  answers were counted live on the teacher side");

            server.endQuiz();
            require(serverProbe.result.await(WAIT_SECONDS, TimeUnit.SECONDS), "Teacher did not receive a result.");
            require(studentA.result.await(WAIT_SECONDS, TimeUnit.SECONDS), "Student A missed the result.");
            require(studentB.result.await(WAIT_SECONDS, TimeUnit.SECONDS), "Student B missed the result.");
            QuizResult result = studentA.lastResult.get();
            require(result.correctIndex() == 1, "Correct answer index was lost.");
            require(result.counts()[0] == 1 && result.counts()[1] == 1, "Answer counts were incorrect.");
            require(result.answered() == 2, "Final answered count was incorrect.");
            System.out.println("PASS  live results were shared after the question closed");

            NoteSummary note = server.addNote(noteFile.toFile());
            require(studentA.noteListWithItem.await(WAIT_SECONDS, TimeUnit.SECONDS),
                    "Student A did not see the shared note.");
            clientA.requestNote(note.id());
            require(studentA.note.await(WAIT_SECONDS, TimeUnit.SECONDS), "Student A did not receive the note.");
            String received = new String(studentA.lastNote.get().content(), StandardCharsets.UTF_8);
            require(received.equals("ClassPulse note transfer test."), "Downloaded note content was wrong.");
            System.out.println("PASS  note was shared and downloaded byte-for-byte");

            server.stop("Self-test complete.");
            require(serverProbe.stopped.await(WAIT_SECONDS, TimeUnit.SECONDS), "Server did not report shutdown.");
            require(studentA.disconnected.await(WAIT_SECONDS, TimeUnit.SECONDS), "Student A was not disconnected.");
            require(studentB.disconnected.await(WAIT_SECONDS, TimeUnit.SECONDS), "Student B was not disconnected.");
            System.out.println("PASS  server shutdown disconnected both students cleanly");
            System.out.println("ALL NETWORK TESTS PASSED");
        } finally {
            if (clientA != null) {
                clientA.disconnect();
            }
            if (clientB != null) {
                clientB.disconnect();
            }
            if (server.isRunning()) {
                server.stop("Test cleanup.");
            }
            Files.deleteIfExists(noteFile);
        }
    }

    private static void verifyDuplicateNameIsRejected(int port, String name) throws Exception {
        ClientProbe probe = new ClientProbe();
        ClassroomClient duplicate = new ClassroomClient("127.0.0.1", port, probe);
        try {
            duplicate.connect(name);
            throw new AssertionError("A duplicate student name was accepted.");
        } catch (IOException expected) {
            require(expected.getMessage() != null && expected.getMessage().toLowerCase().contains("already"),
                    "Duplicate name gave an unclear error: " + expected.getMessage());
        } finally {
            duplicate.disconnect();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class ServerProbe implements ServerListener {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch stopped = new CountDownLatch(1);
        private final CountDownLatch studentsTwo = new CountDownLatch(1);
        private final CountDownLatch statsTwoAnswers = new CountDownLatch(1);
        private final CountDownLatch result = new CountDownLatch(1);
        private final AtomicReference<QuizStats> lastStats = new AtomicReference<>();
        private final List<String> errors = new ArrayList<>();

        @Override
        public void onStarted(int port, List<String> addresses) {
            started.countDown();
        }

        @Override
        public void onStopped(String message) {
            stopped.countDown();
        }

        @Override
        public void onStudentsChanged(List<StudentInfo> students) {
            if (students.size() >= 2) {
                studentsTwo.countDown();
            }
        }

        @Override
        public void onQuizStats(QuizStats stats) {
            lastStats.set(stats);
            if (stats.answered() >= 2) {
                statsTwoAnswers.countDown();
            }
        }

        @Override
        public void onQuizResult(QuizResult quizResult) {
            result.countDown();
        }

        @Override
        public void onError(String message) {
            errors.add(message);
        }
    }

    private static final class ClientProbe implements ClientListener {
        private final CountDownLatch connected = new CountDownLatch(1);
        private final CountDownLatch announcement = new CountDownLatch(1);
        private final CountDownLatch quiz = new CountDownLatch(1);
        private final CountDownLatch receipt = new CountDownLatch(1);
        private final CountDownLatch result = new CountDownLatch(1);
        private final CountDownLatch noteListWithItem = new CountDownLatch(1);
        private final CountDownLatch note = new CountDownLatch(1);
        private final CountDownLatch disconnected = new CountDownLatch(1);
        private final AtomicReference<Announcement> lastAnnouncement = new AtomicReference<>();
        private final AtomicReference<QuizView> lastQuiz = new AtomicReference<>();
        private final AtomicReference<AnswerReceipt> lastReceipt = new AtomicReference<>();
        private final AtomicReference<QuizResult> lastResult = new AtomicReference<>();
        private final AtomicReference<NoteData> lastNote = new AtomicReference<>();

        @Override
        public void onConnected(JoinInfo info) {
            connected.countDown();
        }

        @Override
        public void onAnnouncement(Announcement value) {
            lastAnnouncement.set(value);
            announcement.countDown();
        }

        @Override
        public void onQuizStarted(QuizView value) {
            lastQuiz.set(value);
            quiz.countDown();
        }

        @Override
        public void onQuizResult(QuizResult value) {
            lastResult.set(value);
            result.countDown();
        }

        @Override
        public void onNoteList(List<NoteSummary> notes) {
            if (!notes.isEmpty()) {
                noteListWithItem.countDown();
            }
        }

        @Override
        public void onNoteContent(NoteData value) {
            lastNote.set(value);
            note.countDown();
        }

        @Override
        public void onAnswerReceipt(AnswerReceipt value) {
            lastReceipt.set(value);
            receipt.countDown();
        }

        @Override
        public void onDisconnected(String message) {
            disconnected.countDown();
        }
    }
}
