package com.example.googlesigninexample

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
import org.json.JSONObject
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var signInClient: SignInClient
    private val nonce: String by lazy { Base64.encodeToString(Random.nextBytes(16), Base64.NO_WRAP) }

    private lateinit var getCredentials: ActivityResultLauncher<IntentSenderRequest>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        signInClient = Identity.getSignInClient(this)

        getCredentials = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            val credentials = signInClient.getSignInCredentialFromIntent(it.data)
            val token = credentials.googleIdToken

            Log.d(TAG, "ID Token: $token")
            showToken(token)
        }

        findViewById<TextView>(R.id.nonceTextView).text = nonce

        findViewById<Button>(R.id.signInButton).setOnClickListener { signIn() }
        findViewById<Button>(R.id.oneTapButton).setOnClickListener { oneTap() }
    }

    private fun signIn() {
        resetToken()

        val request = GetSignInIntentRequest.builder().apply {
            setServerClientId(getString(R.string.server_client_id))
            // no method to set the nonce
        }.build()

        signInClient.getSignInIntent(request)
            .addOnSuccessListener(this) { getCredentials.launch(IntentSenderRequest.Builder(it).build()) }
            .addOnFailureListener(this) { it.printStackTrace() }
    }

    private fun oneTap() {
        resetToken()

        val request = BeginSignInRequest.builder().apply {
            setGoogleIdTokenRequestOptions(BeginSignInRequest.GoogleIdTokenRequestOptions.builder().apply {
                setSupported(true)
                setServerClientId(getString(R.string.server_client_id))
                // setting the nonce
                setNonce(nonce)
            }.build())
            setAutoSelectEnabled(true)
        }.build()

        signInClient.beginSignIn(request)
            .addOnSuccessListener(this) { getCredentials.launch(IntentSenderRequest.Builder(it.pendingIntent).build()) }
            .addOnFailureListener(this) { it.printStackTrace() }
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
    }
}