# Fido2 Android Demo app

Demonstration of fido module functionality of `yubikit-android` in an Android app with a web-view.

## Usage
On startup the app will open https://passkey.org, where the users can create a passkey on a Security 
key, or authenticate with a passkey on a Security key. Use the text field to enter different URL, 
or the shortcuts (passkey.org, webauthn.io, Fido2 demo) to quickly open sites used during development.

The Clear cookies button will remove all cookies from the web view.

### Create a FIDO2 credential
1. Run the application, passkey.org will open in the web view.
2. Scroll down until you see the _Demo/Try it out_ section.
3. Enter a random username, click _Sign up_ and _Continue_.
4. The application opens dialog with _Create credential_ title.
5. Either connect your YubiKey to the USB port, or tap the phone with your NFC YubiKey.
6. If the YubiKey has a FIDO2 PIN, the app will ask for it - enter the PIN and tap again with
   the NFC YubiKey. If there is no PIN configured, some sites will refuse to create credential.
7. Now the credential is created and passkey.org should show "Welcome" screen.
8. It is now possible to go to `passkey.org` on a desktop computer and sign in with the created 
   credential.

### Sign in with a FIDO2 credential
1. Create a credential on a YubiKey for `passkey.org`.
2. Run the application.
3. Click the `passkey.org` button and also `Clear cookies` button.
4. Scroll down to the _Demo/Try it out_ section.
5. Click the _Sign in with passkey_ button.
6. The application opens dialog with _Get credential_ title.
7. Either connect your YubiKey to the USB port, or tap the phone with your NFC YubiKey.
8. If the YubiKey has a FIDO2 PIN, the app will ask for it - enter the PIN and tap again with
   the NFC YubiKey. If there is no PIN configured, some sites will refuse to create credential.
9. If there were several credentials for `passkey.org` on the YubiKey, a selection window will 
   appear. Choose one by tapping on it.
10. The web view shows "Welcome" screen.

## Credential Manager
The application also supports Credential manager. Currently, to access this functionality, there
must be no USB key connected to the phone.

To use Credential Manager, follow the _Create/Sign in_ steps and when the _Create credential_ or
_Get credential_ dialog is shown, click the _OTHER OPTIONS_ button to start Credential Manager 
FIDO2 flows.

To remove the button from the dialog, change the value of `USE_CREDENTIAL_MANAGER` in 
`YubiKitWebauthnHelper.kt` to false.

More information about Credential Manager can be found at https://developer.android.com/identity/sign-in/credential-manager.

## Set Up Instructions

### Prerequisites
- access to git@github.com:YubicoLabs/fido2-webview-yubikit-workshop.git (this repository)
- Android development setup
- ANDROID_SDK environmental variable
- Android SDK installed
- Internet connection (for getting maven dependencies)
- Android phone
    - API 21+

### Build instructions
1. create working directory and checkout sources
    ```shell
    mkdir yubikit_fido_demo
    cd yubikit_fido_demo
    git clone git@github.com:YubicoLabs/fido2-webview-yubikit-workshop.git
    ```
2. build [fido2-webview-yubikit-workshop](git@github.com:YubicoLabs/fido2-webview-yubikit-workshop.git)
    ```shell
    cd fido2-webview-yubikit-workshop
    echo "sdk.dir=$ANDROID_SDK" > local.properties
    ./gradlew assembleDebug
    ```
3. Install and run
    - `adb install` the apk into connected android device and run
    - The apk is present in `./app/build/outputs/apk/debug/app-debug.apk`. 
4. Use Android Studio
    - The app was developed in Android Studio and it should be possible to just import the project 
      and run it from there.