package classpulse.net;

import classpulse.model.Announcement;
import classpulse.model.AnswerReceipt;
import classpulse.model.JoinInfo;
import classpulse.model.NoteData;
import classpulse.model.NoteSummary;
import classpulse.model.QuizResult;
import classpulse.model.QuizView;

import java.util.List;

/**
 * Callbacks from the client's reader thread to the student's Swing window.
 */
public interface ClientListener {
    default void onConnected(JoinInfo info) { }
    default void onJoinRejected(String message) { }
    default void onAnnouncement(Announcement announcement) { }
    default void onQuizStarted(QuizView quiz) { }
    default void onQuizResult(QuizResult result) { }
    default void onNoteList(List<NoteSummary> notes) { }
    default void onNoteContent(NoteData note) { }
    default void onAnswerReceipt(AnswerReceipt receipt) { }
    default void onError(String message) { }
    default void onDisconnected(String message) { }
}
