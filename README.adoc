NOTE: The development of Yubico Authenticator for Android has moved to https://github.com/Yubico/yubioath-flutter.

== Android application for OATH with YubiKeys
This app is hosted on Google Play as
https://play.google.com/store/apps/details?id=com.yubico.yubioath[Yubico Authenticator].

See the file COPYING for copyright and license information.

=== Introduction
This is an authenticator app compatible with the OATH standard for time and
counter based numeric OTPs, as used by many online services. To store these
credentials and generate the codes, it uses a compatible YubiKey, connected
either via NFC or USB (requiresa USB On-the-go cable, or USB-C). The USB
functionality requires your mobile device to support USB Host mode, and for CCID
to be enabled on your YubiKey.

Add credentials by tapping the + action button near the bottom right, and
selecting either to add a credential by scanning a QR code, or by entering a
Base32 encoded secret manually.

Once credentials have been added, simply tap or connect your YubiKey to display
codes.

=== Development
This app is developed in Android Studio, using gradle for building. To build the
APK from the command line, run:

  $ ./gradlew assemble

Once done the .apk file can be found in the app/build/outputs/apk/ directory.

=== Issues

Please report app issues in
https://github.com/Yubico/yubioath-android[the issue tracker on GitHub].
