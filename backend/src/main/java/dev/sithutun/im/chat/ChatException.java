package dev.sithutun.im.chat;

/** A send that was rejected; the code is returned to the client in an error frame. */
public class ChatException extends RuntimeException {

    private final String code;

    public ChatException(String code) {
        super(code);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
