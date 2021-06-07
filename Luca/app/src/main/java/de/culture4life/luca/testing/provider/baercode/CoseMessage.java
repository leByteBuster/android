package de.culture4life.luca.testing.provider.baercode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;

import de.culture4life.luca.crypto.CryptoManager;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.Signature;
import java.util.ArrayList;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import androidx.annotation.NonNull;

class CoseMessage {

    public static final ObjectMapper MAPPER = new CBORMapper();
    private byte[] plaintext;
    protected byte[] signature;
    private JsonNode coseSignMessage;
    private JsonNode coseEncrypt0;
    private String algorithm;

    public CoseMessage(@NonNull byte[] data) throws IOException {
        parse(data);
    }

    private void parse(@NonNull byte[] data) throws IOException {
        coseSignMessage = MAPPER.readTree(data);
        plaintext = coseSignMessage.get(2).binaryValue();
        signature = coseSignMessage.get(3).get(0).get(2).binaryValue();
        algorithm = coseSignMessage.get(3).get(0).get(0).asText();
        coseEncrypt0 = MAPPER.readTree(coseSignMessage.get(2).binaryValue());
    }

    public boolean verify(@NonNull PublicKey key) throws IOException, GeneralSecurityException {
        byte[] encodedSigStructure = createSignatureStructure();
        Signature signatureChecker = Signature.getInstance("SHA384withECDSA");
        if (algorithm.equals("oQE4Iw==")) {  // TODO: improve this check
            signatureChecker = Signature.getInstance("SHA512withECDSA");
        }
        signatureChecker.initVerify(key);
        signatureChecker.update(encodedSigStructure);
        return signatureChecker.verify(CryptoManager.toDERSignature(signature));
    }

    public byte[] decodeCypherText(@NonNull BaercodeKey baercodeKey) throws GeneralSecurityException, IOException {
        byte[] cipherText = coseEncrypt0.get(2).binaryValue();
        byte[] iv = coseEncrypt0.get(1).get("5").binaryValue();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        SecretKeySpec keySpec = new SecretKeySpec(baercodeKey.getAesKey(), "AES");
        cipher.init(Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(iv));
        cipher.updateAAD(createAAD());
        return cipher.doFinal(cipherText);
    }

    /**
     * Additional data necessary for decryption of user data as defined in the Baercode algorithm on
     * their verification website scan.baercode.de
     *
     * @return byte[] to be used as the Authenticated Additional Data for AES decryption
     */
    private byte[] createAAD() throws IOException {
        byte[] eProt = coseEncrypt0.get(0).binaryValue();
        ArrayList<Object> data = new ArrayList<>();
        data.add("Encrypt0");
        data.add(eProt);
        data.add(new byte[]{});
        return MAPPER.writeValueAsBytes(data);
    }

    private byte[] createSignatureStructure() throws IOException {
        JsonNode signer = coseSignMessage.get(3).get(0);
        byte[] signerProt = signer.get(0).binaryValue();
        ArrayList<Object> data = new ArrayList<>();
        data.add("Signature");
        data.add(new byte[]{});
        data.add(signerProt);
        data.add(new byte[]{});
        data.add(plaintext);
        return MAPPER.writeValueAsBytes(data);
    }

    public JsonNode getCoseSignMessage() {
        return coseSignMessage;
    }

}
