package crypto;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class AESCrypto extends CryptoDecorator {
	private SecretKey secretKey;
	private IvParameterSpec ivParameterSpec;

	public AESCrypto(Crypto channelToBeDecorated, String message, byte[] key, byte[] iv) {
		super(channelToBeDecorated, message);
		this.secretKey = new SecretKeySpec(key, "AES");
		this.ivParameterSpec = new IvParameterSpec(iv);
	}

	@Override
	public byte[] encode() throws Exception {
		// Encrypt cipher
		Cipher encryptCipher = Cipher.getInstance("AES/CTR/NoPadding", "BC");
		encryptCipher.init(Cipher.ENCRYPT_MODE, secretKey, ivParameterSpec);

		// Encrypt

		byte[] encrypt;
		if (super.channelToBeDecorated != null) {
			encrypt = encryptCipher.doFinal(super.encode());
		} else {
			encrypt = encryptCipher.doFinal(super.message.getBytes());
		}

		return encrypt;
	}

	@Override
	public byte[] decode() throws Exception {
		// Decrypt cipher
		Cipher decryptCipher = Cipher.getInstance("AES/CTR/NoPadding", "BC");

		decryptCipher.init(Cipher.DECRYPT_MODE, secretKey, ivParameterSpec);

		// Decrypt
		byte[] decrypt;
		if (super.channelToBeDecorated != null) {
			decrypt = decryptCipher.doFinal(super.decode());
		} else {

			decrypt = decryptCipher.doFinal(super.message.getBytes());
		}
		return decrypt;
	}

}
