package com.yubico.yubikit_fido_demo

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

const val URL = "https://passkey.org"

class MainViewModel : ViewModel() {
    val url = MutableLiveData(URL)
}