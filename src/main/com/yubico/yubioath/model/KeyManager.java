/*
 * Copyright (c) 2013, Yubico AB.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
 * TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF
 * THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 */

package com.yubico.yubioath.model;

import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.Charset;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.*;

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
    private final Map<String, Set<String>> memStore;

    public KeyManager(SharedPreferences store) {
        this.store = store;
        memStore = new HashMap<String, Set<String>>();
    }

    public Set<byte[]> getSecrets(byte[] id) {
        String key = KEY + bytes2string(id);
        Object value = store.getAll().get(key);
        if(value instanceof String) { //Workaround for old data being stored as a String instead of a Set<String>.
            Set<String> set = new HashSet<String>();
            set.add((String) value);
            store.edit().putStringSet(key, set).apply();
        }
        return strings2bytes(store.getStringSet(key, getMem(key)));
    }

    private Set<String> getMem(String key) {
        Set<String> existing = memStore.get(key);
        if(existing == null) {
            existing = new HashSet<String>();
            memStore.put(key, existing);
        }
        return existing;
    }

    private void doStoreSecret(byte[] id, byte[] secret, boolean remember) {
        String key = KEY + bytes2string(id);
        SharedPreferences.Editor editor = store.edit();
        if (secret.length > 0) {
            String value = bytes2string(secret);
            Set<String> secrets = getMem(key);
            secrets.add(value);
            if (remember) {
                editor.putStringSet(key, secrets);
            } else {
                editor.remove(key);
            }
        } else {
            memStore.remove(key);
            editor.remove(key);
        }
        editor.apply();
    }

    public void storeSecret(byte[] id, byte[] secret, boolean remember) {
        doStoreSecret(id, secret, remember);
    }

    public void setOnlySecret(byte[] id, byte[] secret) {
        String key = KEY + bytes2string(id);
        boolean remember = store.contains(KEY + bytes2string(id));
        doStoreSecret(id, new byte[0], true); // Clear memory
        doStoreSecret(id, secret, remember);
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
        memStore.clear();
        SharedPreferences.Editor editor = store.edit();
        editor.clear();
        editor.apply();
    }

    public void clearMem() {
        memStore.clear();
    }

    public static byte[] calculateSecret(String password, byte[] id, boolean legacy) {
        if (password.isEmpty()) {
            return new byte[0];
        }

        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            SecretKeyFactory legacyFactory;
            try {
                legacyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1And8bit");
                return doCalculateSecret(legacy ? legacyFactory : factory, password.toCharArray(), id);
            } catch (NoSuchAlgorithmException e) {
                // Pre 4.4, standard key factory is wrong.
                legacyFactory = factory;
                factory = null;
                char[] pwChars = password.toCharArray();
                if(!legacy) { // Android < 4.4 only uses the lowest 8 bits of each character, so fix the char[].
                    byte[] pwBytes = password.getBytes(Charset.forName("UTF-8"));
                    pwChars = new char[pwBytes.length];
                    for(int i=0; i<pwBytes.length; i++) {
                        pwChars[i] = (char)pwBytes[i];
                    }
                }
                return doCalculateSecret(legacyFactory, pwChars, id);
            }
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] doCalculateSecret(SecretKeyFactory factory, char[] password, byte[] id) {
        PBEKeySpec keyspec = null;
        try {
            keyspec = new PBEKeySpec(password, id, 1000, 128);
            Key key = factory.generateSecret(keyspec);
            return key.getEncoded();
        } catch (InvalidKeySpecException e) {
            throw new RuntimeException(e);
        } finally {
            if(keyspec != null) {
                keyspec.clearPassword();
            }
        }
    }

    private static String bytes2string(byte[] bytes) {
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    private static byte[] string2bytes(String string) {
        return string == null ? null : Base64.decode(string, Base64.NO_WRAP);
    }

    private static Set<byte[]> strings2bytes(Set<String> strings) {
        Set<byte[]> bytes = new HashSet<byte[]>();
        for(String string : strings) {
            bytes.add(string2bytes(string));
        }
        return bytes;
    }
}