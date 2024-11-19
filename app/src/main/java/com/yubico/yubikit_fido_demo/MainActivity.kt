package com.yubico.yubikit_fido_demo

import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView

import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.ButtonColors
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity

import androidx.webkit.WebViewFeature
import com.yubico.yubikit_fido_demo.ui.theme.Purple700

import com.yubico.yubikit_fido_demo.webhandler.webhooks.hookWebAuthnWithListener
import com.yubico.yubikit_fido_demo.ui.theme.YubiKitFidoDemoTheme
import com.yubico.yubikit_fido_demo.webhandler.CredentialManagerHandler
import com.yubico.yubikit_fido_demo.yubikit.YubiKitWebauthnHelper
import org.slf4j.LoggerFactory

/**
 * Generates a WebView that, if [WebViewFeature.WEB_MESSAGE_LISTENER] is supported, hooks into
 * a WebListener to generate the web app. If it is not supported, it falls back to an earlier
 * injection scheme.
 */
class MainActivity : FragmentActivity() {
    private val mainViewModel: MainViewModel by viewModels()

    private val logger = LoggerFactory.getLogger(MainActivity::class.java)

    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val credentialManagerHandler = CredentialManagerHandler(this)

        val yubiKitWebauthnHelper = YubiKitWebauthnHelper(this, credentialManagerHandler)

        val activity = this

        setContent {
            val focusRequester = remember { FocusRequester() }
            val coroutineScope = rememberCoroutineScope()
            var urlValue by remember { mutableStateOf(TextFieldValue(URL)) }
            val keyboardController = LocalSoftwareKeyboardController.current
            val focusManager = LocalFocusManager.current

            @Composable
            fun CustomButton(
                label: String,
                colors: ButtonColors? = null,
                onClick: () -> Unit
            ) {
                Button(
                    colors = colors ?: ButtonDefaults.buttonColors(),
                    contentPadding = PaddingValues(4.dp),
                    onClick = onClick
                ) { Text(label, fontSize = 12.sp) }
            }

            @Composable
            fun CustomLinkButton(
                label: String,
                url: String
            ) {
                CustomButton(
                    label = label,
                    onClick = {
                        urlValue = urlValue.copy(
                            text = url
                        )
                        mainViewModel.url.postValue(urlValue.text)
                    })
            }

            YubiKitFidoDemoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(modifier = Modifier
                            .fillMaxWidth()
                            .background(color = Purple700)
                            .padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Yubico FIDO Demo", color = Color.White, style = MaterialTheme.typography.caption)
                        }
                        Row(modifier = Modifier
                            .fillMaxWidth()
                            .background(color = Purple700)
                            .padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            OutlinedTextField(
                                value = urlValue,
                                onValueChange = { urlValue = it },
                                colors = TextFieldDefaults.textFieldColors(
                                    backgroundColor = Color.White,
                                    textColor = Color.Black
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        if (!urlValue.text.startsWith("https://")) {
                                            urlValue = urlValue.copy(
                                                text = "https://" + urlValue.text
                                            )
                                        }
                                        mainViewModel.url.postValue(urlValue.text)
                                        keyboardController?.hide()
                                        focusManager.clearFocus()
                                    }
                                ),
                                singleLine = true,
                                maxLines = 1,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(0.dp)
                                    .onFocusChanged { focusState ->
                                        if (focusState.isFocused) {
                                            val selectionStart =
                                                if (urlValue.text.startsWith("https://")) {
                                                    8
                                                } else {
                                                    0
                                                }

                                            urlValue = urlValue.copy(
                                                selection = TextRange(
                                                    selectionStart,
                                                    urlValue.text.length
                                                )
                                            )
                                        }
                                    }
                                    .focusRequester(focusRequester)
                            )
                        }
                        Row(modifier = Modifier
                            .fillMaxWidth()
                            .background(color = Purple700)
                            .padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                CustomLinkButton(label = "passkey.org", url = "https://passkey.org")
                                CustomLinkButton(label = "webauthn.io", url = "https://webauthn.io")
                                CustomLinkButton(label = "Fido2 demo", url = "https://webauthn-android-demo.glitch.me")
                            }
                            CustomButton(
                                label = "Clear cookies",
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = Color.Red,
                                    contentColor = Color.White
                                )
                            ) {
                                CookieManager.getInstance().removeAllCookies {
                                    mainViewModel.url.postValue(urlValue.text)
                                }
                            }
                        }
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = {
                            WebView(it).apply {
                                settings.apply {
                                    domStorageEnabled = true
                                    javaScriptEnabled = true
                                    userAgentString = "Mozilla/5.0 (iPhone; CPU iPhone OS 16_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0.3 Mobile/15E148 Safari/604.1"
                                }

                                val listenerSupported = WebViewFeature.isFeatureSupported(
                                    WebViewFeature
                                        .WEB_MESSAGE_LISTENER
                                )
                                if (listenerSupported) {
                                    // This is where we inject the local javascript that allows calling
                                    // our local credential manager.
                                    hookWebAuthnWithListener(
                                        this,
                                        this@MainActivity,
                                        coroutineScope,
                                        yubiKitWebauthnHelper.webAuthnHandler
                                    )
                                } else {
                                    // This is supported L and above, passkeys are supported on K and
                                    // above. Thus, for now, there are two API levels, API 19 and 20,
                                    // where this will not work. If a fallback is required for those
                                    // cases, we can provide it. Just contact us by following the
                                    // readme.
                                }
                                mainViewModel.url.value?.let { url ->
                                    loadUrl(url)
                                }

                                mainViewModel.url.observe(activity) { newUrl ->
                                    logger.debug("Url change to: $newUrl")
                                    loadUrl(newUrl)
                                }
                            }
                        })
                    }
                }
            }
        }
    }
}

