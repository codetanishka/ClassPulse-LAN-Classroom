package classpulse.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The question as students see it. The correct option is deliberately not
 * included, so it cannot be read from the packet before the quiz ends.
 */
public record QuizView(
        String id,
        String prompt,
        List<String> options,
        long startedAt,
        long endsAt,
        int totalStudents
) implements Serializable {
    private static final long serialVersionUID = 1L;

    public QuizView {
        options = Collections.unmodifiableList(new ArrayList<>(options));
    }
}
