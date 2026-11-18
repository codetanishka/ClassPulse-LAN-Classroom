package classpulse.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Final result of a question. This is broadcast only after the question closes.
 */
public record QuizResult(
        String quizId,
        String prompt,
        List<String> options,
        int correctIndex,
        int[] counts,
        int totalStudents,
        int answered,
        long endedAt
) implements Serializable {
    private static final long serialVersionUID = 1L;

    public QuizResult {
        options = Collections.unmodifiableList(new ArrayList<>(options));
        counts = counts.clone();
    }

    @Override
    public int[] counts() {
        return counts.clone();
    }
}
