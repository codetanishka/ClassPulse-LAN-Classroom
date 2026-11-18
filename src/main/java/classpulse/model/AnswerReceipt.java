package classpulse.model;

import java.io.Serializable;

public record AnswerReceipt(
        String quizId,
        int selectedIndex,
        boolean accepted,
        String message,
        int answered,
        int totalStudents
) implements Serializable {
    private static final long serialVersionUID = 1L;
}
