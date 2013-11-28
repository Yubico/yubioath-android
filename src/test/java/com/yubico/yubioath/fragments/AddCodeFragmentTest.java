package com.yubico.yubioath.fragments;

import org.junit.Test;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;

import android.net.Uri;

@RunWith(RobolectricTestRunner.class)
public class AddCodeFragmentTest {
	AddCodeFragment fragment;
	
	@Before
	public void setUp() {
		fragment = new AddCodeFragment();
	}
	
	@Test
	public void testParseUriBad() {
		String failingUris[] = {"http://example.com/", "otpauth://foobar?secret=kaka", "foobar",
				"otpauth://totp/Example:alice@google.com?secret=balhonga1&issuer=Example"};
		for(String uri : failingUris) {
			try {
				assertEquals("URI " + uri + " did not fail.", false, fragment.parseUri(Uri.parse(uri)));
			} catch(RuntimeException e) {
				System.err.println("Failed at uri: " + uri);
				throw(e);
			}
		}
	}
	
	@Test
	public void testParseUriGood() {
		String goodUris[] = {"otpauth://totp/Example:alice@google.com?secret=JBSWY3DPEHPK3PXP&issuer=Example"};
		for(String uri : goodUris) {
			try {
				assertEquals("URI " + uri + " failed unexpectedly.", true, fragment.parseUri(Uri.parse(uri)));
			} catch(RuntimeException e) {
				System.err.println("Failed at uri: " + uri);
				throw(e);
			}
		}
	}
}
