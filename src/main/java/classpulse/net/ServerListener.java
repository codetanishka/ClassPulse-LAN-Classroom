package classpulse.net;

import classpulse.model.Announcement;
import classpulse.model.NoteSummary;
import classpulse.model.QuizResult;
import classpulse.model.QuizStats;
import classpulse.model.QuizView;
import classpulse.model.StudentInfo;

import java.util.List;

/**
 * Callbacks from the server core to the teacher's Swing window. Callbacks may
 * arrive on a network thread, so the UI wraps them with SwingUtilities.
 */
public interface ServerListener {
    default void onStarted(int port, List<String> addresses) { }
    default void onStopped(String message) { }
    default void onStudentsChanged(List<StudentInfo> students) { }
    default void onAnnouncement(Announcement announcement) { }
    default void onQuizStarted(QuizView quiz) { }
    default void onQuizStats(QuizStats stats) { }
    default void onQuizResult(QuizResult result) { }
    default void onNoteAdded(NoteSummary note) { }
    default void onLog(String message) { }
    default void onError(String message) { }
}
