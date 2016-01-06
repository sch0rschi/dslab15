package crypto;

public class CryptoDecorator implements Crypto {
	protected Crypto channelToBeDecorated;
	protected byte[] message;

	public CryptoDecorator(Crypto channelToBeDecorated, byte[] message) {
		this.channelToBeDecorated = channelToBeDecorated;
		this.message = message;
	}

	@Override
	public byte[] encode() throws Exception {
		return channelToBeDecorated.encode();
	}

	@Override
	public byte[] decode() throws Exception {
		return channelToBeDecorated.decode();
	}

}
