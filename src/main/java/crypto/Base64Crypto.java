package crypto;
import org.bouncycastle.util.encoders.Base64;
public class Base64Crypto extends CryptoDecorator{

	public Base64Crypto(Crypto channelToBeDecorated, byte[] message) {
		super(channelToBeDecorated,message);
	}

	@Override
	public byte[] encode() throws Exception {
		// encode into Base64 format
		byte[] base64Message;
		if (super.channelToBeDecorated != null) {
			base64Message = Base64.encode(super.encode());
		} else {
			base64Message = Base64.encode(super.message);
		}
		return base64Message;
	}

	@Override
	public byte[] decode() throws Exception {
		// decode from Base64 format
		byte[] base64Message;
		if (super.channelToBeDecorated != null) {
			base64Message = Base64.decode(super.decode());
		} else {
			base64Message = (Base64.decode(super.message));
		}
		return base64Message;

	}
}
