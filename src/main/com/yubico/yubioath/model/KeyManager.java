package com.yubico.yubioath.model;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.UnsupportedEncodingException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;

/**
 * Created with IntelliJ IDEA.
 * User: dain
 * Date: 8/23/13
 * Time: 4:46 PM
 * To change this template use File | Settings | File Templates.
 */
public class KeyManager {
    private static final String NAME_STORE = "com.yubico.yubioath.NAME_STORE";
    private static final String SECRET_STORE = "com.yubico.yubioath.SECRET_STORE";
    private final SharedPreferences nameStore;
    private final SharedPreferences secretStore;

    public KeyManager(Context context) {
        nameStore = context.getSharedPreferences(NAME_STORE, Context.MODE_PRIVATE);
        secretStore = context.getSharedPreferences(SECRET_STORE, Context.MODE_PRIVATE);
    }

    public byte[] getSecret(byte[] id) {
        return string2bytes(secretStore.getString(bytes2string(id), null));
    }

    public void storeSecret(byte[] id, byte[] secret) {
        SharedPreferences.Editor editor = secretStore.edit();
        if(secret.length > 0) {
            editor.putString(bytes2string(id), bytes2string(secret));
        } else {
            editor.remove(bytes2string(id));
        }
        editor.apply();
    }

    public String getDisplayName(byte[] id, String defaultName) {
        return nameStore.getString(bytes2string(id), defaultName);
    }

    public void setDisplayName(byte[] id, String name) {
        SharedPreferences.Editor editor = nameStore.edit();
        editor.putString(bytes2string(id), name);
        editor.apply();
    }

    public void clearAll() {
        SharedPreferences.Editor nameEditor = nameStore.edit();
        nameEditor.clear();
        nameEditor.apply();
        SharedPreferences.Editor secretEditor = secretStore.edit();
        secretEditor.clear();
        secretEditor.apply();
    }

    public static byte[] calculateSecret(String password, byte[] id) {
        if(password.isEmpty()) {
            return new byte[0];
        }

        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            KeySpec keyspec = new PBEKeySpec(password.toCharArray(), id, 1000, 128);
            Key key = factory.generateSecret(keyspec);
            return key.getEncoded();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (InvalidKeySpecException e) {
            throw new RuntimeException(e);
        }
    }

    private static String bytes2string(byte[] bytes) {
        return Base64.encodeToString(bytes, Base64.DEFAULT);
    }

    private static byte[] string2bytes(String string) {
        return string == null ? null : Base64.decode(string, Base64.DEFAULT);
    }
}
