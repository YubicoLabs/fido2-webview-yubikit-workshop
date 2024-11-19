package com.yubico.yubikit_fido_demo.yubikit

import android.app.AlertDialog
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.yubico.yubikit.android.YubiKitManager
import com.yubico.yubikit.android.transport.nfc.NfcConfiguration
import com.yubico.yubikit.android.transport.nfc.NfcNotAvailable
import com.yubico.yubikit.android.transport.usb.UsbConfiguration
import com.yubico.yubikit.core.util.Result
import com.yubico.yubikit_fido_demo.webhandler.CredentialManagerHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

const val USE_CREDENTIAL_MANAGER = true

class YubiKitWebauthnHelper(
    private val activity: ComponentActivity,
    credentialManagerHandler: CredentialManagerHandler
) {
    private val yubiKitFidoViewModel: YubiKitFidoViewModel by activity.viewModels()
    private val yubikit: YubiKitManager = YubiKitManager(activity)
    private val nfcConfiguration = NfcConfiguration()

    private val logger = LoggerFactory.getLogger(YubiKitWebauthnHelper::class.java)

    private val yubiKeyPrompt: AlertDialog = AlertDialog.Builder(activity)
        .setTitle("Provide a Security Key")
        .setMessage("Insert or tap your Security Key")
        .setNegativeButton("Cancel")
        { _, _ ->
            activity.lifecycleScope.launch {
                logger.debug("Prompt cancelled by user by clicking the Cancel button")
                yubiKitFidoViewModel.provideYubiKey(Result.failure(CancellationException("Cancelled by user")))
            }
        }
        .setOnCancelListener {
            activity.lifecycleScope.launch {
                logger.debug("Prompt dialog cancelled by user.")
                yubiKitFidoViewModel.provideYubiKey(Result.failure(CancellationException("Cancelled by user")))
            }
        }.also {
            if (USE_CREDENTIAL_MANAGER) {
                it.setNeutralButton("Other options") { dialogInterface, _ ->
                    activity.lifecycleScope.launch {
                        logger.debug("Click 'Other options' in dialog")
                        yubiKitFidoViewModel.provideYubiKey(
                            Result.failure(ChooseOtherOptionsException("Choose other options by user"))
                        )
                    }
                    dialogInterface.dismiss()
                }
            }
        }.create()

    private val _webAuthnHandler =
        YubiKitWebAuthnHandler(yubiKitFidoViewModel, credentialManagerHandler)

    val webAuthnHandler: YubiKitWebAuthnHandler
        get() = _webAuthnHandler

    init {
        // Watch for requests to use the YubiKey and prompts the user to insert/tap when needed
        yubiKitFidoViewModel.pendingYubiKeyAction.observe(activity) { action ->
            if (action != null) {
                logger.debug("Waiting for insertion of Security Key")
                activity.lifecycleScope.launch(Dispatchers.Main) {
                    val usbYubiKey = yubiKitFidoViewModel.usbYubiKey.value
                    if (usbYubiKey != null) {
                        yubiKitFidoViewModel.provideYubiKey(Result.success(usbYubiKey))
                        yubiKeyPrompt.dismiss()
                    } else {
                        val useNfc = yubiKitFidoViewModel.useNfc.value == true

                        yubiKeyPrompt.setMessage("Insert or tap your Security Key now")
                        yubiKeyPrompt.show()

                        // change dialog title based on the flow state
                        if (action.flowState.startsWith("register")) {
                            yubiKeyPrompt.setTitle("Create credential")
                        } else if (action.flowState.startsWith("get")) {
                            yubiKeyPrompt.setTitle("Get credential")
                        } else {
                            yubiKeyPrompt.setTitle("Invalid flow state")
                        }

                        // set the "Other options" button visibility based on the flow state
                        // we only show this in the first phase (register_1 or get_1)
                        // in other phases it does not make sense to offer Other options
                        yubiKeyPrompt.getButton(AlertDialog.BUTTON_NEUTRAL).visibility =
                            if (action.flowState.endsWith("_1")) View.VISIBLE else View.GONE

                        if (useNfc) {
                            try {
                                yubikit.startNfcDiscovery(
                                    nfcConfiguration,
                                    activity
                                ) { nfcYubiKey ->
                                    logger.debug("NFC Session started {}", nfcYubiKey)
                                    activity.lifecycleScope.launch(Dispatchers.Main) {
                                        yubiKeyPrompt.setMessage("Hold your Security Key still")
                                        yubiKitFidoViewModel.provideYubiKey(
                                            Result.success(
                                                nfcYubiKey
                                            )
                                        )
                                        logger.debug("Waiting for removal of NFC Security Key")
                                        yubiKeyPrompt.setMessage("Remove your Security Key")
                                        nfcYubiKey.remove {
                                            logger.debug("NFC Security Key was removed")
                                            activity.lifecycleScope.launch(Dispatchers.Main) {
                                                yubiKeyPrompt.dismiss()
                                            }
                                        }
                                    }
                                }
                            } catch (e: NfcNotAvailable) {
                                yubiKitFidoViewModel.useNfc.value = false
                                logger.error("Exception when trying to listen to NFC: ", e)
                            }
                        }
                    }
                }
            }
        }

        yubikit.startUsbDiscovery(UsbConfiguration()) { device ->
            logger.debug("USB device attached: {}", device)

            // usbYubiKey keeps a reference to the currently connected YubiKey over USB
            yubiKitFidoViewModel.usbYubiKey.postValue(device)
            device.setOnClosed { yubiKitFidoViewModel.usbYubiKey.postValue(null) }

            activity.lifecycleScope.launch(Dispatchers.Main) {
                yubiKitFidoViewModel.provideYubiKey(Result.success(device))
                // If we were asking the user to insert a YubiKey, close the dialog.
                yubiKeyPrompt.dismiss()
            }
        }
    }
}