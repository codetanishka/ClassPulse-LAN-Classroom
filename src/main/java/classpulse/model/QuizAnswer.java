package classpulse.model;

import java.io.Serializable;

public record QuizAnswer(String quizId, int optionIndex) implements Serializable {
    private static final long serialVersionUID = 1L;
}
