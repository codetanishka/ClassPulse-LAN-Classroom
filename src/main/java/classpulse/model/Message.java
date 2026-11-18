package classpulse.model;

import java.io.Serializable;

/**
 * A small serializable envelope. Java object serialization keeps the network
 * layer simple and avoids needing an external JSON library.
 */
public final class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    private final MessageType type;
    private final Object payload;

    public Message(MessageType type, Object payload) {
        this.type = type;
        this.payload = payload;
    }

    public static Message of(MessageType type, Object payload) {
        return new Message(type, payload);
    }

    public MessageType getType() {
        return type;
    }

    public Object getPayload() {
        return payload;
    }
}
