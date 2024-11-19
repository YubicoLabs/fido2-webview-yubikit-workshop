package com.yubico.yubikit_fido_demo.yubikit

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.yubico.yubikit.android.transport.usb.UsbYubiKeyDevice
import com.yubico.yubikit.core.YubiKeyDevice
import com.yubico.yubikit.core.util.Result
import com.yubico.yubikit.fido.client.BasicWebAuthnClient
import com.yubico.yubikit.fido.ctap.Ctap2Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.suspendCoroutine

data class YubiKeyAction(
    val message: String,
    // used to distinguish flow state for adapting the UI
    // values supported in this version:
    //  - register_1 - registration of credential before YK connected
    //  - register_2 - registration of credential after PIN entered
    //  - get_1      - authentication before YK connected
    //  - get_2      - authentication after PIN entered
    val flowState: String,
    val action: suspend (Result<YubiKeyDevice, Exception>) -> Unit
)

class YubiKitFidoViewModel : ViewModel() {

    val useNfc = MutableLiveData(true)
    val usbYubiKey = MutableLiveData<UsbYubiKeyDevice?>()

    private val _pendingYubiKeyAction = MutableLiveData<YubiKeyAction?>()
    val pendingYubiKeyAction: LiveData<YubiKeyAction?> = _pendingYubiKeyAction

    suspend fun provideYubiKey(result: Result<YubiKeyDevice, Exception>) =
        withContext(Dispatchers.IO) {
            pendingYubiKeyAction.value?.let {
                _pendingYubiKeyAction.postValue(null)
                it.action.invoke(result)
            }
        }


    /**
     * Requests a WebAuthn client, and uses it to produce some result
     */
    suspend fun <T> useWebAuthn(title: String, flowState: String, action: (BasicWebAuthnClient) -> T) =
        suspendCoroutine<T> { outer ->
            _pendingYubiKeyAction.postValue(YubiKeyAction(title, flowState) { result ->
                outer.resumeWith(runCatching {
                    suspendCoroutine { inner ->
                        Ctap2Session.create(result.value) {
                            inner.resumeWith(runCatching {
                                action.invoke(BasicWebAuthnClient(it.value))
                            })
                        }
                    }
                })
            })
        }
}