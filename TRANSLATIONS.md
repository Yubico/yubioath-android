# Translations

Localisation is handled through Transifex. The project can be accessed through [this link](https://www.transifex.com/yubico-1/yubico-authenticator-android).

This Android project has been configured to work with [Transifex' CLI](https://docs.transifex.com/client/introduction) which, after installed, can easily be used to push and pull strings.

To push the source string file for translations, simply run this command on the project's root directory:

    tx push -s
To pull the translated strings, use this command on the project's root directory:

    tx pull -a