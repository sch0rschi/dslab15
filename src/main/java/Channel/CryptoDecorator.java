package Channel;

import org.bouncycastle.asn1.cmp.ProtectedPart;

public class CryptoDecorator implements Crypto {
	protected Crypto channelToBeDecorated;
	protected String message;

	public CryptoDecorator(Crypto channelToBeDecorated, String message) {
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
