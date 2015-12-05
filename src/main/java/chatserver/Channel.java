package chatserver;

import java.io.IOException;

public interface Channel {
	
	/**
	 * Write the given {@code message} to the recipient
	 * @param message not null
	 */
	void write(String message) throws IOException;

}
