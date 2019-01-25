package com.yubico.yubikit.application.oath;

import android.util.SparseArray;

import com.yubico.yubikit.application.AbstractApplication;
import com.yubico.yubikit.application.ApduException;
import com.yubico.yubikit.application.Tlv;
import com.yubico.yubikit.application.Version;
import com.yubico.yubikit.transport.Iso7816Backend;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class OathApplication extends AbstractApplication {
    public static final byte[] AID = new byte[]{(byte) 0xa0, 0x00, 0x00, 0x05, 0x27, 0x21, 0x01, 0x01};

    public static final short SW_WRONG_DATA = 0x6a80;
    public static final short SW_FILE_NOT_FOUND = 0x6a82;
    public static final short SW_FILE_FULL = 0x6a84;
    public static final short SW_AUTH_REQUIRED = 0x6982;
    public static final short SW_DATA_INVALID = 0x6984;

    private static final byte INS_PUT = 0x01;
    private static final byte INS_DELETE = 0x02;
    private static final byte INS_SET_CODE = 0x03;
    private static final byte INS_RESET = 0x04;

    private static final byte INS_LIST = (byte) 0xa1;
    private static final byte INS_CALCULATE = (byte) 0xa2;
    private static final byte INS_VALIDATE = (byte) 0xa3;
    private static final byte INS_CALCULATE_ALL = (byte) 0xa4;

    private static final byte TAG_NAME = 0x71;
    private static final byte TAG_KEY = 0x73;
    private static final byte TAG_CHALLENGE = 0x74;
    private static final byte TAG_RESPONSE = 0x75;
    private static final byte TAG_PROPERTY = 0x78;
    private static final byte TAG_VERSION = 0x79;
    private static final byte TAG_IMF = 0x7a;

    private static final byte PROPERTY_REQUIRE_TOUCH = 0x02;

    private Version version;
    private byte[] deviceId;
    private byte[] challenge;

    public OathApplication(Iso7816Backend backend) {
        super(backend, AID, (byte) 0xa5);
    }

    @Override
    public byte[] select() throws IOException, ApduException {
        byte[] response = super.select();
        SparseArray<byte[]> data = new Tlv.Group(response).toDict();
        version = Version.parse(data.get(TAG_VERSION), 0);
        deviceId = data.get(TAG_NAME);
        challenge = data.get(TAG_CHALLENGE);

        return response;
    }

    public byte[] getDeviceId() {
        return Arrays.copyOf(deviceId, deviceId.length);
    }

    public Version getVersion() {
        return version;
    }

    public boolean isLocked() {
        return challenge != null;
    }

    public void unlock(ChallengeSigner signer) throws IOException, ApduException {
        byte[] response = signer.sign(challenge);
        byte[] myChallenge = new byte[8];
        SecureRandom random = new SecureRandom();
        random.nextBytes(myChallenge);
        byte[] myResponse = signer.sign(myChallenge);

        SparseArray<byte[]> resp = new Tlv.Group(send(INS_VALIDATE, 0, 0, Tlv.Group.of(
                TAG_RESPONSE, response,
                TAG_CHALLENGE, myChallenge
        ).getBytes())).toDict();

        if (!Arrays.equals(myResponse, resp.get(TAG_RESPONSE))) {
            throw new IOException("Invalid response");
        }

        challenge = null;
    }

    public void reset() throws IOException, ApduException {
        send(INS_RESET, 0xde, 0xad, new byte[0]);
    }

    public void setLockCode(byte[] secret) throws IOException, ApduException {
        byte[] challenge = new byte[8];
        SecureRandom random = new SecureRandom();
        random.nextBytes(challenge);

        byte[] response;
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, mac.getAlgorithm()));
            response = mac.doFinal();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }

        send(INS_SET_CODE, 0, 0, Tlv.Group.of(
                TAG_KEY, ByteBuffer.allocate(1 + secret.length).put((byte) (OathType.TOTP.value | HashAlgorithm.SHA1.value)).put(secret).array(),
                TAG_CHALLENGE, challenge,
                TAG_RESPONSE, response
        ).getBytes());
    }

    public void unsetLockCode() throws IOException, ApduException {
        send(INS_SET_CODE, 0, 0, Tlv.of(TAG_KEY).getBytes());
    }

    public void putCredential(String name, byte[] key, OathType oathType, HashAlgorithm hashAlgorithm, int digits, int imf, boolean touch) throws IOException, ApduException {
        if (touch && version.major < 4) {
            throw new IllegalArgumentException("Require touch requires YubiKey 4 or later");
        }

        try {
            key = hashAlgorithm.prepareKey(key);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }

        ByteBuffer buffer = ByteBuffer.allocate(4096)
                .put(Tlv.of(TAG_NAME, name.getBytes(Charset.forName("UTF-8"))).getBytes())
                .put(Tlv.of(TAG_KEY, ByteBuffer.allocate(2 + key.length).put((byte) (oathType.value | hashAlgorithm.value)).put((byte) digits).put(key).array()).getBytes());

        if (touch) {
            buffer.put(TAG_PROPERTY).put(PROPERTY_REQUIRE_TOUCH);
        }
        if (oathType == OathType.HOTP && imf > 0) {
            buffer.put(TAG_IMF).put((byte) 4).putInt(imf);
        }

        send(INS_PUT, 0, 0, Arrays.copyOfRange(buffer.array(), 0, buffer.position()));
    }

    public void deleteCredential(String name) throws IOException, ApduException {
        send(INS_DELETE, 0, 0, Tlv.of(TAG_NAME, name.getBytes(Charset.forName("UTF-8"))).getBytes());
    }

    public CalculateResponse calculate(String name, byte[] challenge, boolean truncate) throws IOException, ApduException {
        Tlv resp = new Tlv(send(INS_CALCULATE, 0, truncate ? 1 : 0, Tlv.Group.of(
                TAG_NAME, name.getBytes(Charset.forName("UTF-8")),
                TAG_CHALLENGE, challenge
        ).getBytes()));
        return new CalculateResponse(name, resp);
    }

    public List<CalculateResponse> calculateAll(byte[] challenge) throws IOException, ApduException {
        Iterator<Tlv> response = new Tlv.Group(send(INS_CALCULATE_ALL, 0, 1, Tlv.of(TAG_CHALLENGE, challenge).getBytes())).toList().iterator();
        List<CalculateResponse> result = new ArrayList<>();
        while (response.hasNext()) {
            result.add(new CalculateResponse(new String(response.next().getValue(), Charset.forName("UTF-8")), response.next()));
        }

        return result;
    }

    public List<ListResponse> listCredentials() throws IOException, ApduException {
        List<ListResponse> result = new ArrayList<>();
        for (Tlv tlv : new Tlv.Group(send(INS_LIST, 0, 0, new byte[0]), 0).toList()) {
            result.add(new ListResponse(tlv));
        }
        return result;
    }

    public static byte[] calculateKey(byte[] deviceId, String password) {
        if (password.isEmpty()) {
            return new byte[0];
        }
        SecretKeyFactory factory;
        PBEKeySpec keyspec = null;
        try {
            factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            keyspec = new PBEKeySpec(password.toCharArray(), deviceId, 1000, 128);
            return factory.generateSecret(keyspec).getEncoded();
        } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        } finally {
            if (keyspec != null) {
                keyspec.clearPassword();
            }
        }
    }

}
