package crypto;

import javax.crypto.Cipher;
import java.security.PrivateKey;
import java.security.PublicKey;

public class RSACrypto extends CryptoDecorator {
	static PublicKey publicKey;
	static PrivateKey privateKey;

	public RSACrypto(Crypto channelToBeDecorated, byte[] message, PublicKey publicKey, PrivateKey privateKey)
			throws Exception {
		super(channelToBeDecorated, message);
		this.publicKey = publicKey;
		this.privateKey = privateKey;

	}

	@Override
	public byte[] encode() throws Exception {
		Cipher encryptCipher = Cipher.getInstance("RSA/NONE/OAEPWithSHA256AndMGF1Padding");
		encryptCipher.init(Cipher.ENCRYPT_MODE, publicKey);
		byte[] encrypt;
		if (super.channelToBeDecorated != null) {
			encrypt = encryptCipher.doFinal(super.encode());
		} else {
			encrypt = encryptCipher.doFinal(super.message);
		}

		return encrypt;

	}

	@Override
	public byte[] decode() throws Exception {

		Cipher decryptCipher = Cipher.getInstance("RSA/NONE/OAEPWithSHA256AndMGF1Padding");
		decryptCipher.init(Cipher.DECRYPT_MODE, privateKey);
		byte[] decrypt;
		if (super.channelToBeDecorated != null) {
			decrypt = decryptCipher.doFinal(super.decode());
		} else {

			decrypt = decryptCipher.doFinal(super.message);
		}
		return decrypt;

	}

}
