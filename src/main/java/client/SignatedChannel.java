package client;

import java.io.IOException;

/**
 * Channel uses <HMAC> signatures for its messages
 */
public interface SignatedChannel {

    /**
     * Prepend <HMAC> to wholeMessage and send it to corresponding client
     * @param wholeMessage The message-construct for which the signature shall be created
     */
    void write(String wholeMessage);

    /**
     * Wait for message from other client and return it.
     * @return the received message
     */
    String read() throws IOException;

    /**
     * Checks correctness of prepended HMAC of a message
     * @param message non-null non-empty String with a base64 encoded HMAC, a white-space and a message
     * @return true iff message content matches the HMAC
     */
    boolean verify(String message);
}
