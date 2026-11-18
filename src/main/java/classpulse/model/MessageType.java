package classpulse.model;

/**
 * Every packet exchanged between the teacher server and a student client
 * uses one of these message types.
 */
public enum MessageType {
    JOIN,
    JOIN_OK,
    JOIN_REJECTED,
    ANNOUNCEMENT,
    QUIZ_STARTED,
    QUIZ_RESULT,
    ANSWER,
    ANSWER_RESULT,
    NOTE_LIST,
    NOTE_DOWNLOAD,
    NOTE_CONTENT,
    SERVER_CLOSED,
    ERROR
}
