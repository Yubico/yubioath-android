package com.yubico.yubioath.exc

import java.io.IOException

class DuplicateKeyException : IOException("A Credential with the same name already exists")