package classpulse.net;

import classpulse.model.Announcement;
import classpulse.model.NoteSummary;
import classpulse.model.QuizResult;
import classpulse.model.StudentInfo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Serialized when a class ends. The binary file can be reopened from Java,
 * and a matching text summary is written for easy reading.
 */
public record SessionArchive(
        String className,
        String teacherName,
        long startedAt,
        long endedAt,
        String endReason,
        List<StudentInfo> students,
        List<Announcement> announcements,
        List<QuizResult> quizResults,
        List<NoteSummary> notes
) implements Serializable {
    private static final long serialVersionUID = 1L;

    public SessionArchive {
        students = Collections.unmodifiableList(new ArrayList<>(students));
        announcements = Collections.unmodifiableList(new ArrayList<>(announcements));
        quizResults = Collections.unmodifiableList(new ArrayList<>(quizResults));
        notes = Collections.unmodifiableList(new ArrayList<>(notes));
    }
}
