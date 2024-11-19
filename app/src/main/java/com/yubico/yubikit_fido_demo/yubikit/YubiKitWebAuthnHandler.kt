package com.yubico.yubikit_fido_demo.yubikit

import android.content.Context
import android.util.Base64
import androidx.credentials.CreatePublicKeyCredentialRequest
import com.yubico.yubikit.core.fido.CtapException
import com.yubico.yubikit.fido.client.ClientError
import com.yubico.yubikit.fido.client.MultipleAssertionsAvailable
import com.yubico.yubikit.fido.client.PinInvalidClientError
import com.yubico.yubikit.fido.client.PinRequiredClientError
import com.yubico.yubikit.fido.webauthn.PublicKeyCredential
import com.yubico.yubikit.fido.webauthn.PublicKeyCredentialCreationOptions
import com.yubico.yubikit.fido.webauthn.PublicKeyCredentialRequestOptions
import com.yubico.yubikit_fido_demo.webhandler.CredentialManagerHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.slf4j.LoggerFactory
import java.net.URL

class YubiKitWebAuthnHandler(
    private val viewModel: YubiKitFidoViewModel,
    private val credentialManagerHandler: CredentialManagerHandler
) {

    private val logger = LoggerFactory.getLogger(YubiKitWebAuthnHandler::class.java)

    /**
     * Creates a basic WebAuthn ClientData object
     */
    private fun buildClientData(
        type: String,
        origin: String,
        challenge: String
    ): ByteArray {
        return """
            {
                "type": "$type",
                "challenge": "$challenge",
                "origin": "$origin"
            }
        """.trimIndent().toByteArray()
    }

    private fun JSONObject.toMap(): Map<String, *> = keys().asSequence().associateWith {
        when (val value = this[it]) {
            is JSONArray -> {
                val map = (0 until value.length()).associate { mapValue ->
                    Pair(
                        mapValue.toString(),
                        value[mapValue]
                    )
                }
                JSONObject(map).toMap().values.toList()
            }

            is JSONObject -> value.toMap()
            JSONObject.NULL -> null
            else -> value
        }
    }

    /**
     * Create a new WebAuthn credential
     */
    suspend fun webauthnMakeCredential(
        context: Context,
        origin: String,
        message: String
    ): String = withContext(Dispatchers.IO) {
        try {

            val effectiveDomain = URL(origin).host
            val createRequest = CreatePublicKeyCredentialRequest(message)
            val options =
                PublicKeyCredentialCreationOptions.fromMap(JSONObject(createRequest.requestJson).toMap())
            val clientData = buildClientData(
                "webauthn.create",
                origin,
                Base64.encodeToString(
                    options.challenge,
                    Base64.NO_PADDING or Base64.NO_WRAP or Base64.URL_SAFE
                )
            )

            var pin: CharArray? = null
            var credential: PublicKeyCredential?

            do {
                val flowState = if (pin == null) "register_1" else "register_2"

                credential = try {
                    viewModel.useWebAuthn("Register Security Key", flowState) {
                        it.makeCredential(
                            clientData,
                            options,
                            effectiveDomain,
                            pin,
                            null,
                            null
                        )
                    }
                } catch (e: PinRequiredClientError) {
                    // have to ask for pin
                    pin = enterPin(context, "Enter Security Key PIN")
                    // retry the flow
                    continue
                } catch (e: PinInvalidClientError) {
                    showError(context, "Invalid PIN. Retries left: ${e.pinRetries}.")

                    if (e.pinRetries > 0) {
                        pin = enterPin(context, "Enter Security Key PIN")
                        continue
                    }

                    // there are no retries left - throw the exception
                    throw e

                } catch (e: ClientError) {
                    val pinCtapError = getPinError(e)
                    if (pinCtapError != null) {
                        if (pinCtapError.ctapError == CtapException.ERR_PIN_REQUIRED ||
                            pinCtapError.ctapError == CtapException.ERR_UV_BLOCKED ||
                            pinCtapError.ctapError == CtapException.ERR_UV_INVALID
                        ) {
                            pin = enterPin(context, "Enter Security Key PIN")
                            continue
                        }
                        showError(context, getPinErrorMessage(pinCtapError))
                    }
                    throw e
                } catch (e: Exception) {
                    throw e
                }

                if (credential != null) {
                    break
                }
            } while (true)

            JSONObject(credential!!.toMap()).toString()

        } catch (e: ChooseOtherOptionsException) {
            logger.debug("User chooses other options for CREATE")
            credentialManagerHandler.createPasskey(message).registrationResponseJson
        }
    }

    private fun getPinError(e: ClientError): CtapException? {
        return if (e.errorCode == ClientError.Code.BAD_REQUEST
            && e.cause is CtapException
            && (e.cause as CtapException).ctapError in listOf(
                CtapException.ERR_PIN_NOT_SET,
                CtapException.ERR_PIN_INVALID,
                CtapException.ERR_PIN_POLICY_VIOLATION,
                CtapException.ERR_PIN_BLOCKED,
                CtapException.ERR_PIN_AUTH_BLOCKED,
                CtapException.ERR_PIN_REQUIRED,
                CtapException.ERR_PIN_AUTH_INVALID,
                CtapException.ERR_UV_BLOCKED,
                CtapException.ERR_UV_INVALID
            )
        ) e.cause as CtapException else null
    }

    private fun getPinErrorMessage(pinCtapError: CtapException): String {
        return when (pinCtapError.ctapError) {
            CtapException.ERR_PIN_AUTH_BLOCKED -> "Too many wrong PIN attempts, reconnect the Security Key."
            CtapException.ERR_PIN_BLOCKED -> "PIN is blocked, reset the Security Key."
            CtapException.ERR_PIN_AUTH_INVALID -> "Authentication failed, please try again."
            CtapException.ERR_PIN_NOT_SET -> "PIN is not set on authenticator, cannot continue."
            CtapException.ERR_PIN_REQUIRED -> "PIN is required."
            CtapException.ERR_UV_BLOCKED -> "UV is blocked, PIN is required."
            CtapException.ERR_UV_INVALID -> "UV is invalid, PIN is required."
            else -> "PIN error: ${pinCtapError.ctapError}."
        }
    }

    /**
     * Authenticates using a WebAuthn credential
     */
    suspend fun webauthnGetAssertion(
        context: Context,
        origin: String,
        message: String,
    ): String = withContext(Dispatchers.IO) {

        try {
            val options = PublicKeyCredentialRequestOptions.fromMap(JSONObject(message).toMap())

            val effectiveDomain = URL(origin).host
            val clientData = buildClientData(
                "webauthn.get",
                origin,
                Base64.encodeToString(
                    options.challenge,
                    Base64.NO_PADDING or Base64.NO_WRAP or Base64.URL_SAFE
                )

            )

            var pin: CharArray? = null
            var credential: PublicKeyCredential?

            do {

                val flowState = if (pin == null) "get_1" else "get_2"

                try {
                    credential =
                        viewModel.useWebAuthn("Security Key Authentication", flowState) { client ->
                            client.getAssertion(clientData, options, effectiveDomain, pin, null)
                        }
                } catch (e: PinRequiredClientError) {
                    // have to ask for pin
                    pin = enterPin(context, "Enter Security Key PIN")
                    // retry the flow
                    continue

                } catch (e: PinInvalidClientError) {
                    showError(context, "Invalid PIN. Retries left: ${e.pinRetries}.")

                    if (e.pinRetries > 0) {
                        pin = enterPin(context, "Enter Security Key PIN")
                        continue
                    }

                    // there are no retries left - throw the exception
                    throw e

                } catch (e: ClientError) {
                    val pinCtapError = getPinError(e)
                    if (pinCtapError != null) {
                        if (pinCtapError.ctapError == CtapException.ERR_PIN_REQUIRED ||
                            pinCtapError.ctapError == CtapException.ERR_UV_BLOCKED ||
                            pinCtapError.ctapError == CtapException.ERR_UV_INVALID
                        ) {
                            pin = enterPin(context, "Enter Security Key PIN")
                            continue
                        }
                        showError(context, getPinErrorMessage(pinCtapError))
                    } else {
                        showError(context, e.message ?: "Error ${e.errorCode}")
                    }


                    throw e
                } catch (e: MultipleAssertionsAvailable) {
                    // Handle multiple stored credentials by showing a selector
                    val index = selectItem(context, "Select account", e.users) {
                        it.displayName
                    }
                    credential = e.select(index)
                }

                if (credential != null) {
                    break
                }

            } while (true)

            JSONObject(credential!!.toMap()).toString()

        } catch (e: ChooseOtherOptionsException) {
            logger.debug("User chooses other options for GET")
            (credentialManagerHandler.getPasskey(message).credential as androidx.credentials.PublicKeyCredential)
                .authenticationResponseJson
        }
    }

}