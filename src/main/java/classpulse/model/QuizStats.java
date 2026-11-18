package classpulse.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Live counts sent to the teacher's screen while a question is open.
 */
public record QuizStats(
        String quizId,
        String prompt,
        List<String> options,
        int[] counts,
        int answered,
        int totalStudents,
        long endsAt,
        boolean closed
) implements Serializable {
    private static final long serialVersionUID = 1L;

    public QuizStats {
        options = Collections.unmodifiableList(new ArrayList<>(options));
        counts = counts.clone();
    }

    @Override
    public int[] counts() {
        return counts.clone();
    }
}
