package com.example.googlesigninexample

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.auth0.android.jwt.JWT
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.GetSignInIntentRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import net.openid.appauth.*
import org.json.JSONObject
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var signInClient: SignInClient

    private val clientId: String by lazy { getString(R.string.client_id) }
    private val serverClientId: String by lazy { getString(R.string.server_client_id) }
    private val nonce: String by lazy { Base64.encodeToString(Random.nextBytes(16), Base64.NO_WRAP) }

    private lateinit var googleSignIn: ActivityResultLauncher<IntentSenderRequest>
    private lateinit var appAuthSignIn: ActivityResultLauncher<Intent>

    private val authorizationService: AuthorizationService by lazy { AuthorizationService(this) }

    private val authorizationEndpoint: Uri
        get() = Uri.parse(AUTHORIZATION_ENDPOINT)

    private val tokenEndpoint: Uri
        get() = Uri.parse(TOKEN_ENDPOINT)

    private val redirectUrl: Uri
        get() {
            val clientIdentifierScheme = clientId
                .split(".")
                .reversed()
                .joinToString(".")
            return Uri.parse("$clientIdentifierScheme:$OAUTH2_CALLBACK_PATH")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        signInClient = Identity.getSignInClient(this)

        googleSignIn = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            val credentials = signInClient.getSignInCredentialFromIntent(it.data)
            val token = credentials.googleIdToken

            Log.d(TAG, "ID Token: $token")
            showToken(token)
        }

        appAuthSignIn = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val authorizationResponse = result.data?.let { AuthorizationResponse.fromIntent(it) }
            val exception = AuthorizationException.fromIntent(result.data)

            if (authorizationResponse == null) {
                exception?.printStackTrace()
            } else {
                val tokenRequest = authorizationResponse.createTokenExchangeRequest()
                authorizationService.performTokenRequest(tokenRequest) { tokenResponse, exception ->
                    if (tokenResponse == null) {
                        exception?.printStackTrace()
                    } else {
                        showToken(tokenResponse.idToken)
                    }
                }
            }
        }

        findViewById<TextView>(R.id.nonceTextView).text = nonce

        findViewById<Button>(R.id.signInButton).setOnClickListener { signIn() }
        findViewById<Button>(R.id.oneTapButton).setOnClickListener { oneTap() }
        findViewById<Button>(R.id.appAuthButton).setOnClickListener { appAuth() }
    }

    private fun signIn() {
        resetToken()

        val request = GetSignInIntentRequest.builder().apply {
            setServerClientId(serverClientId)
            // no method to set the nonce
        }.build()

        signInClient.getSignInIntent(request)
            .addOnSuccessListener(this) { googleSignIn.launch(IntentSenderRequest.Builder(it).build()) }
            .addOnFailureListener(this) { it.printStackTrace() }
    }

    private fun oneTap() {
        resetToken()

        val request = BeginSignInRequest.builder().apply {
            setGoogleIdTokenRequestOptions(BeginSignInRequest.GoogleIdTokenRequestOptions.builder().apply {
                setSupported(true)
                setServerClientId(serverClientId)
                // setting the nonce
                setNonce(nonce)
            }.build())
            setAutoSelectEnabled(true)
        }.build()

        signInClient.beginSignIn(request)
            .addOnSuccessListener(this) { googleSignIn.launch(IntentSenderRequest.Builder(it.pendingIntent).build()) }
            .addOnFailureListener(this) { it.printStackTrace() }
    }

    private fun appAuth() {
        resetToken()

        val configuration = AuthorizationServiceConfiguration(authorizationEndpoint, tokenEndpoint)
        val authorizationRequest = AuthorizationRequest.Builder(
            configuration,
            clientId,
            ResponseTypeValues.CODE,
            redirectUrl,
        ).apply {
            val additionalParameters = mapOf(
                "audience" to serverClientId,
            )

            setScope("${AuthorizationRequest.Scope.OPENID} ${AuthorizationRequest.Scope.EMAIL}")
            setAdditionalParameters(additionalParameters)
            // setting the nonce
            setNonce(nonce)
        }.build()

        val intent = authorizationService.getAuthorizationRequestIntent(authorizationRequest)
        appAuthSignIn.launch(intent)
    }

    private fun resetToken() {
        findViewById<TextView>(R.id.tokenTextView).text = "--"
    }

    private fun showToken(token: String?) {
        findViewById<TextView>(R.id.tokenTextView).text = token?.let { decodeToken(it) }
    }

    private fun decodeToken(token: String): String? {
        val jwt = JWT(token)
        val json = JSONObject().apply {
            jwt.claims.forEach {
                put(it.key, it.value.asString())
            }
        }

        return json.toString(2)
    }

    companion object {
        private const val TAG = "MainActivity"

        private const val AUTHORIZATION_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
        private const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"

        private const val OAUTH2_CALLBACK_PATH = "/oauth2callback"
    }
}