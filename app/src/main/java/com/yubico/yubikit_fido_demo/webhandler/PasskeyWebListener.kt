package com.yubico.yubikit_fido_demo.webhandler

import android.app.Activity
import android.net.Uri
import android.webkit.WebView
import android.widget.Toast
import androidx.annotation.UiThread
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialException
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import com.yubico.yubikit_fido_demo.yubikit.YubiKitWebAuthnHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.slf4j.LoggerFactory


/**
This web listener looks for the 'postMessage()' call on the javascript web code, and when it
receives it, it will handle it in the manner dictated in this local codebase. This allows for
javascript on the web to interact with the local setup on device that contains more complex logic.

```
cat encode.js | grep -v '^let __webauthn_interface__;$' | \
curl -X POST --data-urlencode input@- \
https://www.toptal.com/developers/javascript-minifier/api/raw | tr '"' "'" | pbcopy
```
pbpaste should output the proper minimized code. In linux, you may have to alias as follows:
```
alias pbcopy='xclip -selection clipboard'
alias pbpaste='xclip -selection clipboard -o'
```
in your bashrc.
 */
class PasskeyWebListener(
    private val activity: Activity,
    private val coroutineScope: CoroutineScope,
    private val yubiKitWebAuthnHandler: YubiKitWebAuthnHandler
) : WebViewCompat.WebMessageListener {

    /** havePendingRequest is true if there is an outstanding WebAuthn request. There is only ever
    one request outstanding at a time.*/
    private var havePendingRequest = false

    /** pendingRequestIsDoomed is true if the WebView has navigated since starting a request. The
    fido module cannot be cancelled, but the response will never be delivered in this case.*/
    private var pendingRequestIsDoomed = false

    /** replyChannel is the port that the page is listening for a response on. It
    is valid iff `havePendingRequest` is true.*/
    private var replyChannel: ReplyChannel? = null

    private val logger = LoggerFactory.getLogger(PasskeyWebListener::class.java)

    /** Called by the page when it wants to do a WebAuthn `get` or 'post' request. */
    @UiThread
    override fun onPostMessage(
        view: WebView,
        message: WebMessageCompat,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        replyProxy: JavaScriptReplyProxy,
    ) {
        logger.debug("In Post Message: {} source: {}", message, sourceOrigin)
        val messageData = message.data ?: return
        onRequest(messageData, sourceOrigin, isMainFrame, JavaScriptReplyChannel(replyProxy))
    }

    private fun onRequest(
        msg: String?,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        reply: ReplyChannel,
    ) {
        msg?.let {
            val jsonObj = JSONObject(msg)
            val type = jsonObj.getString(TYPE_KEY)
            val message = jsonObj.getString(REQUEST_KEY)

            val isCreate = type == CREATE_UNIQUE_KEY
            val isGet = type == GET_UNIQUE_KEY

            if (havePendingRequest) {
                postErrorMessage(reply, "request already in progress", type)
                return
            }
            replyChannel = reply
            if (!isMainFrame) {
                reportFailure("requests from subframes are not supported", type)
                return
            }
            val originScheme = sourceOrigin.scheme
            if (originScheme == null || originScheme.lowercase() != "https") {
                reportFailure("WebAuthn not permitted for current URL", type)
                return
            }

            havePendingRequest = true
            pendingRequestIsDoomed = false

            val replyCurrent = replyChannel
            if (replyCurrent == null) {
                logger.error("reply channel was null, cannot continue")
                return
            }

            this.coroutineScope.launch {
                if (isCreate) {
                    handleCreateFlow(message, sourceOrigin, replyCurrent)
                } else if (isGet) {
                    handleGetFlow(message, sourceOrigin, replyCurrent)
                } else {
                    logger.error("Incorrect request json")
                }
            }
        }
    }

    private suspend fun handleGetFlow(
        message: String,
        sourceOrigin: Uri,
        reply: ReplyChannel,
    ) {
        try {
            havePendingRequest = false
            pendingRequestIsDoomed = false

            val response = yubiKitWebAuthnHandler.webauthnGetAssertion(
                activity,
                sourceOrigin.toString(),
                message
            )
            logger.trace("assertion: {}", response)
            val successArray = ArrayList<Any>()
            successArray.add("success")
            successArray.add(JSONObject(response))
            successArray.add(GET_UNIQUE_KEY)
            reply.send(JSONArray(successArray).toString())
            replyChannel = null // setting initial replyChannel for next request given temp 'reply'
        } catch (e: GetCredentialException) {
            reportFailure("Error: ${e.errorMessage} w type: ${e.type} w obj: $e", GET_UNIQUE_KEY)
        } catch (t: Throwable) {
            reportFailure("Error: ${t.message}", GET_UNIQUE_KEY)
        }
    }

    private suspend fun handleCreateFlow(
        message: String,
        sourceOrigin: Uri,
        reply: ReplyChannel,
    ) {
        try {
            havePendingRequest = false
            pendingRequestIsDoomed = false
            val result = yubiKitWebAuthnHandler.webauthnMakeCredential(
                activity,
                sourceOrigin.toString(),
                message,
            )

            logger.debug("webAuthnMakeCredential result: {}", result)

            val successArray = ArrayList<Any>()
            successArray.add("success")
            successArray.add(JSONObject(result))
            successArray.add(CREATE_UNIQUE_KEY)
            reply.send(JSONArray(successArray).toString())
            replyChannel = null // setting initial replyChannel for next request given temp 'reply'
        } catch (e: CreateCredentialException) {
            reportFailure(
                "Error: ${e.errorMessage} w type: ${e.type} w obj: $e",
                CREATE_UNIQUE_KEY
            )
        } catch (t: Throwable) {
            reportFailure("Error: ${t.message}", CREATE_UNIQUE_KEY)
        }
    }

    /** Invalidates any current request.  */
    fun onPageStarted() {
        if (havePendingRequest) {
            pendingRequestIsDoomed = true
        }
    }

    /** Sends an error result to the page.  */
    private fun reportFailure(message: String, type: String) {
        havePendingRequest = false
        pendingRequestIsDoomed = false
        val reply: ReplyChannel = replyChannel!! // verifies non null by throwing NPE
        replyChannel = null
        postErrorMessage(reply, message, type)
    }

    private fun postErrorMessage(reply: ReplyChannel, errorMessage: String, type: String) {
        logger.trace("Sending error message back to the page via replyChannel {}", errorMessage)
        val array: MutableList<Any?> = ArrayList()
        array.add("error")
        array.add(errorMessage)
        array.add(type)
        reply.send(JSONArray(array).toString())
        Toast.makeText(this.activity.applicationContext, errorMessage, Toast.LENGTH_SHORT).show()
    }

    private class JavaScriptReplyChannel(private val reply: JavaScriptReplyProxy) :
        ReplyChannel {
        private val logger = LoggerFactory.getLogger(JavaScriptReplyChannel::class.java)
        override fun send(message: String?) {
            try {
                reply.postMessage(message!!)
            } catch (t: Throwable) {
                logger.error("Reply failure due to: ", t)
            }
        }
    }

    /** ReplyChannel is the interface over which replies to the embedded site are sent. This allows
    for testing because AndroidX bans mocking its objects.*/
    interface ReplyChannel {
        fun send(message: String?)
    }

    companion object {
        /** INTERFACE_NAME is the name of the MessagePort that must be injected into pages. */
        const val INTERFACE_NAME = "__webauthn_interface__"

        const val CREATE_UNIQUE_KEY = "create"
        const val GET_UNIQUE_KEY = "get"
        const val TYPE_KEY = "type"
        const val REQUEST_KEY = "request"

        /** INJECTED_VAL is the minified version of the JavaScript code described at this class
         * heading. */
        const val INJECTED_VAL = """
            var __webauthn_interface__,__webauthn_hooks__;!function(e){console.log('In the hook.');let n=(e,n)=>n instanceof Uint8Array?i(n):n instanceof ArrayBuffer?i(new Uint8Array(n)):n,r=e=>JSON.stringify(e,n);__webauthn_interface__.addEventListener('message',function e(n){var r=JSON.parse(n.data),t=r[2];'get'===t?u(r):'create'===t?c(r):console.log('Incorrect response format for reply')});var t=null,a=null,o=null,l=null;function u(e){if(null===t||null===o){console.log('Reply failure: Resolve: '+a+' and reject: '+l);return}if('success'!=e[0]){var n=o;t=null,o=null,n(new DOMException(e[1],'NotAllowedError'));return}var r=p(e[1]),u=t;t=null,o=null,u(r)}function s(e){var n=e.length%4;return Uint8Array.from(atob(e.replace(/-/g,'+').replace(/_/g,'/').padEnd(e.length+(0===n?0:4-n),'=')),function(e){return e.charCodeAt(0)}).buffer}function i(e){return btoa(Array.from(new Uint8Array(e),function(e){return String.fromCharCode(e)}).join('')).replace(/\+/g,'-').replace(/\//g,'_').replace(/=+${'$'}/,'')}function c(e){if(null===a||null===l){console.log('Reply failure: Resolve: '+a+' and reject: '+l);return}if(console.log('Output back: '+e),'success'!=e[0]){var n=l;a=null,l=null,n(new DOMException(e[1],'NotAllowedError'));return}var r=p(e[1]),t=a;a=null,l=null,t(r)}function p(e){return e.rawId=s(e.rawId),e.response.clientDataJSON=s(e.response.clientDataJSON),e.response.hasOwnProperty('attestationObject')&&(e.response.attestationObject=s(e.response.attestationObject)),e.response.hasOwnProperty('authenticatorData')&&(e.response.authenticatorData=s(e.response.authenticatorData)),e.response.hasOwnProperty('signature')&&(e.response.signature=s(e.response.signature)),e.response.hasOwnProperty('userHandle')&&(e.response.userHandle=s(e.response.userHandle)),e.getClientExtensionResults=function e(){return{}},e}e.create=function n(t){if(!('publicKey'in t))return e.originalCreateFunction(t);var o=new Promise(function(e,n){a=e,l=n}),u=t.publicKey;if(u.hasOwnProperty('challenge')){var s=i(u.challenge);u.challenge=s}if(u.hasOwnProperty('user')&&u.user.hasOwnProperty('id')){var c=i(u.user.id);u.user.id=c}var p=r({type:'create',request:u});return __webauthn_interface__.postMessage(p),o},e.get=function n(a){if(!('publicKey'in a))return e.originalGetFunction(a);var l=new Promise(function(e,n){t=e,o=n}),u=a.publicKey;if(u.hasOwnProperty('challenge')){var s=i(u.challenge);u.challenge=s}var c=r({type:'get',request:u});return __webauthn_interface__.postMessage(c),l},e.onReplyGet=u,e.CM_base64url_decode=s,e.CM_base64url_encode=i,e.onReplyCreate=c}(__webauthn_hooks__||(__webauthn_hooks__={})),__webauthn_hooks__.originalGetFunction=navigator.credentials.get,__webauthn_hooks__.originalCreateFunction=navigator.credentials.create,navigator.credentials.get=__webauthn_hooks__.get,navigator.credentials.create=__webauthn_hooks__.create,window.PublicKeyCredential=function(){},window.PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable=function(){return Promise.resolve(!1)};
        """
        const val TAG = "PasskeyWebListener"
    }

}