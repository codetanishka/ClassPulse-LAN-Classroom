package classpulse.model;

import java.io.Serializable;

public record JoinInfo(
        String className,
        String teacherName,
        String studentId,
        String studentName
) implements Serializable {
    private static final long serialVersionUID = 1L;
}
