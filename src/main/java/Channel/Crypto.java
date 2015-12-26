package Channel;

public interface Crypto {
	// Encrypt message
	public byte[] encode() throws Exception;

	// Decrypt message
	public byte[] decode() throws Exception;
}
