package com.yubico.yubikit_fido_demo.webhandler

import android.app.Activity
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.exceptions.CreateCredentialException
import org.slf4j.LoggerFactory

/**
 * This contains the credential manager object, and allows utilizing it's key get and create APIs.
 */
class CredentialManagerHandler(private val activity: Activity) {
    /** The credential manager object, setup via a required context from the base activity */
    private val mCredentialManager = CredentialManager.create(this.activity)

    private val logger = LoggerFactory.getLogger(CredentialManagerHandler::class.java)


    /**
     * Encapsulates the create passkey API for credential manager in a less error prone manner.
     * Helps shuttle the output back to any required source.
     *
     * @param request a create public key credential request json required by
     * [CreatePublicKeyCredentialRequest]
     */
    suspend fun createPasskey(request: String):
        CreatePublicKeyCredentialResponse {
        val cr = CreatePublicKeyCredentialRequest(request)
        val ret: CreatePublicKeyCredentialResponse?
        try {
            ret = mCredentialManager.createCredential(
                this.activity, cr
            ) as CreatePublicKeyCredentialResponse
        } catch (e: CreateCredentialException) {
            logger.error("Issue while creating credential: ErrMessage: {} ErrType: {}.", e.errorMessage, e.type, e)
            throw e
        }
        return ret
    }

    /**
     * Encapsulates the get passkey API for credential manager in a less error prone manner.
     * Helps shuttle the output back to any required source.
     *
     * @param request a get public key credential request json required by
     * [GetCredentialRequest]
     */
    suspend fun getPasskey(request: String): GetCredentialResponse {
        val cr = GetCredentialRequest(listOf(GetPublicKeyCredentialOption(request, null, emptySet())))
        val result: GetCredentialResponse?
        try {
            result = mCredentialManager.getCredential(this.activity, cr)
        } catch (e: androidx.credentials.exceptions.GetCredentialException) {
            logger.error("Issue while retrieving credential: {}", e.message, e)
            throw e
        }
        // Can be cast into types if desired here (or later depending on use case)
        // For now we generically give back the response and assume it is converted later
        return result
    }
}
