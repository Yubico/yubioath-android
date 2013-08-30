package com.yubico.yubioath.model;

import android.content.SharedPreferences;
import android.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.HashMap;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: dain
 * Date: 8/23/13
 * Time: 4:46 PM
 * To change this template use File | Settings | File Templates.
 */
public class KeyManager {
    private static final String KEY = "key_";
    private static final String NAME = "name_";

    private final SharedPreferences store;
    private final Map<String, String> memStore;

    public KeyManager(SharedPreferences store) {
        this.store = store;
        memStore = new HashMap<String, String>();
    }

    public byte[] getSecret(byte[] id) {
        String key = KEY + bytes2string(id);
        return string2bytes(store.getString(key, memStore.get(key)));
    }

    public void storeSecret(byte[] id, byte[] secret, boolean remember) {
        String key = KEY+bytes2string(id);
        SharedPreferences.Editor editor = store.edit();
        if (secret.length > 0) {
            String value = bytes2string(secret);
            memStore.put(key, value);
            if(remember) {
                editor.putString(key, value);
            } else {
                editor.remove(key);
            }
        } else {
            memStore.remove(key);
            editor.remove(key);
        }
        editor.apply();
    }

    public String getDisplayName(byte[] id, String defaultName) {
        return store.getString(NAME + bytes2string(id), defaultName);
    }

    public void setDisplayName(byte[] id, String name) {
        SharedPreferences.Editor editor = store.edit();
        editor.putString(NAME + bytes2string(id), name);
        editor.apply();
    }

    public void clearAll() {
        SharedPreferences.Editor editor = store.edit();
        editor.clear();
        editor.apply();
    }

    public static byte[] calculateSecret(String password, byte[] id) {
        if (password.isEmpty()) {
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
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    private static byte[] string2bytes(String string) {
        return string == null ? null : Base64.decode(string, Base64.NO_WRAP);
    }
}