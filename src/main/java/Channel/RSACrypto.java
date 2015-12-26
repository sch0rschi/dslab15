package Channel;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.MGF1ParameterSpec;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

import com.sun.corba.se.impl.protocol.giopmsgheaders.Message;

import util.Keys;

public class RSACrypto extends CryptoDecorator {
	static PublicKey publicKey;
	static PrivateKey privateKey;

	public RSACrypto(Crypto channelToBeDecorated, String message, PublicKey publicKey, PrivateKey privateKey)
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
			encrypt = encryptCipher.doFinal(super.message.getBytes());
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

			decrypt = decryptCipher.doFinal(super.message.getBytes());
		}
		return decrypt;

	}

}
