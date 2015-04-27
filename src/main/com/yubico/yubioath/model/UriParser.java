package com.yubico.yubioath.model;

import android.net.Uri;

import org.apache.commons.codec.binary.Base32;

/**
 * Created by jtyr on 4/28/15.
 */
public class UriParser {
    public String name;
    public byte[] key;
    public byte oath_type;
    public byte algorithm_type;
    public int digits;
    public int counter;

    public boolean parseUri(Uri uri) {
        String scheme = uri.getScheme();
        if(!uri.isHierarchical() || scheme == null || !scheme.equals("otpauth")) {
            return false;
        }

        String secret = uri.getQueryParameter("secret");
        if (secret == null || secret.isEmpty()) {
            return false;
        }
        Base32 base32 = new Base32();
        if(!base32.isInAlphabet(secret.toUpperCase())) {
            return false;
        }
        key = base32.decode(secret.toUpperCase());

        String path = uri.getPath(); // user name is stored in path...
        if(path == null || path.isEmpty()) {
            return false;
        }
        if (path.charAt(0) == '/') {
            path = path.substring(1);
        }
        if (path.length() > 64) {
            path = path.substring(0, 64);
        }
        name = path;

        String typeString = uri.getHost(); // type stored in host, totp/hotp
        if (typeString.equals("totp")) {
            oath_type = YubiKeyNeo.TOTP_TYPE;
        } else if (typeString.equals("hotp")) {
            oath_type = YubiKeyNeo.HOTP_TYPE;
        } else {
            return false;
        }

        String algorithm = uri.getQueryParameter("algorithm");
        if (algorithm == null || algorithm.isEmpty() || algorithm.equals("SHA1")) {
            algorithm_type = YubiKeyNeo.HMAC_SHA1;
        } else if (algorithm.equals("SHA256")) {
            algorithm_type = YubiKeyNeo.HMAC_SHA256;
        } else {
            return false;
        }

        String digit_string = uri.getQueryParameter("digits");
        if (digit_string == null || digit_string.isEmpty()) {
            digits = 6;
        } else {
            try {
                digits = Integer.parseInt(digit_string);
            } catch (NumberFormatException e) {
                return false;
            }
        }

        String counter_string = uri.getQueryParameter("counter");
        if(counter_string == null || counter_string.isEmpty()) {
            counter = 0;
        } else {
            try {
                counter = Integer.parseInt(counter_string);
            } catch (NumberFormatException e) {
                return false;
            }
        }

        return true;
    }
}
