package com.yubico.yubikit_fido_demo.yubikit

import kotlinx.coroutines.CancellationException

class ChooseOtherOptionsException(message: String) : CancellationException(message)