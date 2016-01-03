package crypto;
import org.bouncycastle.util.encoders.Base64;
public class Base64Crypto extends CryptoDecorator{

	byte[] message;

	public Base64Crypto(Crypto channelToBeDecorated,String message) {
		super(channelToBeDecorated,message);
	}

	public Base64Crypto(Crypto channelToBeDecorated, byte[] message){
		super(channelToBeDecorated, new String(message));
		this.message = message;
	}

	@Override
	public byte[] encode() throws Exception {
		// encode into Base64 format
		if(message != null){
			return Base64.encode(message);
		} else{
			byte[] base64Message;
			if (super.channelToBeDecorated != null) {
				base64Message = Base64.encode(super.encode());
			} else {
				base64Message = Base64.encode(super.message.getBytes());
			}
			return base64Message;
		}

	}

	@Override
	public byte[] decode() throws Exception {
		// decode from Base64 format
		if(message != null){
			return Base64.decode(message);
		} else{
			byte[] base64Message;
			if (super.channelToBeDecorated != null) {
				base64Message = Base64.decode(super.decode());
			} else {
				base64Message = (Base64.decode(super.message));
			}
			return base64Message;
		}


	}
}
