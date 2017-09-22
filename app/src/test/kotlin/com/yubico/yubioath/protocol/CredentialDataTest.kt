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

package com.yubico.yubioath.protocol

import android.net.Uri
import com.yubico.yubioath.BuildConfig
import org.junit.Assert
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(constants = BuildConfig::class, sdk = intArrayOf(21))
class CredentialDataTest {

    @Test
    fun testParseUriBad() {
        val failingUris = listOf("http://example.com/", "otpauth://foobar?secret=kaka", "foobar", "otpauth://totp/Example:alice@google.com?secret=balhonga1&issuer=Example", "otpauth:///foo:mallory@example.com?secret=kaka")
        for (uri in failingUris) {
            try {
                CredentialData.from_uri(Uri.parse(uri))
                fail("URL $uri did not fail")
            } catch (e: IllegalArgumentException) {
                // Should fail.
            }

        }
    }

    @Test
    fun testParseUriGood() {
        val goodUris = listOf("otpauth://totp/Example:alice@google.com?secret=JBSWY3DPEHPK3PXP&issuer=Example", "otpauth://hotp/foobar:bob@example.com?secret=blahonga2")
        for (uri in goodUris) {
            try {
                CredentialData.from_uri(Uri.parse(uri))
            } catch (e: IllegalArgumentException) {
                System.err.println("Failed at uri: " + uri)
                throw e
            }
        }
    }

    @Test
    fun testParseIssuer() {
        val noIssuer = CredentialData.from_uri(Uri.parse("otpauth://totp/account?secret=abba"))
        Assert.assertNull(noIssuer.issuer)
        val usingParam = CredentialData.from_uri(Uri.parse("otpauth://totp/account?secret=abba&issuer=Issuer"))
        Assert.assertEquals(usingParam.issuer, "Issuer")
        val usingSeparator = CredentialData.from_uri(Uri.parse("otpauth://totp/Issuer:account?secret=abba"))
        Assert.assertEquals(usingSeparator.issuer, "Issuer")
        val usingBoth = CredentialData.from_uri(Uri.parse("otpauth://totp/IssuerA:account?secret=abba&issuer=IssuerB"))
        Assert.assertEquals(usingBoth.issuer, "IssuerA")
    }
}
