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

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
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
    private static final String ALTKEY = "altkey_";
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

    public byte[] getAltSecret(byte[] id) {
        String key = ALTKEY + bytes2string(id);
        return string2bytes(store.getString(key, memStore.get(key)));
    }

    private void doStoreSecret(byte[] id, byte[] secret, boolean remember, String prefix) {
        String key = prefix + bytes2string(id);
        SharedPreferences.Editor editor = store.edit();
        if (secret.length > 0) {
            String value = bytes2string(secret);
            memStore.put(key, value);
            if (remember) {
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

    public void storeSecret(byte[] id, byte[] secret, boolean remember) {
        doStoreSecret(id, secret, remember, KEY);
    }

    public void storeAltSecret(byte[] id, byte[] secret, boolean remember) {
        doStoreSecret(id, secret, remember, ALTKEY);
    }

    public void promoteAltSecret(byte[] id) {
        byte[] altSecret = getAltSecret(id);
        if(altSecret != null) {
            memStore.put(KEY + bytes2string(id), bytes2string(altSecret));
            if(store.contains(ALTKEY + bytes2string(id))) {
                storeSecret(id, altSecret, true);
            }
        } else {
            storeSecret(id, new byte[0], true);
        }
        storeAltSecret(id, new byte[0], true);
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

    public static byte[] calculateSecret(String password, byte[] id) {
        if (password.isEmpty()) {
            return new byte[0];
        }

        PBEKeySpec keyspec = null;
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            keyspec = new PBEKeySpec(password.toCharArray(), id, 1000, 128);
            Key key = factory.generateSecret(keyspec);
            return key.getEncoded();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
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
}