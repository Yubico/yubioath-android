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

package com.yubico.yubioath.fragments;

import org.junit.Test;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;

import android.net.Uri;

import com.yubico.yubioath.model.UriParser;

@RunWith(RobolectricTestRunner.class)
public class AddCodeFragmentTest {
	UriParser u;
	
	@Before
	public void setUp() {
		u = new UriParser();
	}
	
	@Test
	public void testParseUriBad() {
		String failingUris[] = {"http://example.com/", "otpauth://foobar?secret=kaka", "foobar",
				"otpauth://totp/Example:alice@google.com?secret=balhonga1&issuer=Example",
				"otpauth:///foo:mallory@example.com?secret=kaka"};
		for(String uri : failingUris) {
			try {
				assertEquals("URI " + uri + " did not fail.", false, u.parseUri(Uri.parse(uri)));
			} catch(RuntimeException e) {
				System.err.println("Failed at uri: " + uri);
				throw(e);
			}
		}
	}
	
	@Test
	public void testParseUriGood() {
		String goodUris[] = {
				"otpauth://totp/Example:alice@google.com?secret=JBSWY3DPEHPK3PXP&issuer=Example",
				"otpauth://hotp/foobar:bob@example.com?secret=blahonga2"};
		for(String uri : goodUris) {
			try {
				assertEquals("URI " + uri + " failed unexpectedly.", true, u.parseUri(Uri.parse(uri)));
			} catch(RuntimeException e) {
				System.err.println("Failed at uri: " + uri);
				throw(e);
			}
		}
	}
}
