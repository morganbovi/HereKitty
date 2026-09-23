package io.sweatshop.herekitty.mobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import io.sweatshop.herekitty.mobile.features.login.LoginContent
import io.sweatshop.herekitty.mobile.features.share.ShareContent
import io.sweatshop.herekitty.mobile.ui.HereKittyTheme
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HereKittyTheme { Surface(Modifier.fillMaxSize()) { CompanionApp() } } }
    }
}

private enum class AppStep { Welcome, Permissions, DeveloperOptions, Organization, Home }

@Composable
private fun CompanionApp() {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("herekitty_onboarding", android.content.Context.MODE_PRIVATE) }
    val signedInEmail = FirebaseAuth.getInstance().currentUser?.email
    var step by remember(signedInEmail) {
        mutableStateOf(
            if (signedInEmail != null && preferences.getString("completed_for", null) == signedInEmail) AppStep.Home
            else AppStep.Welcome,
        )
    }
    var checkingOrg by remember { mutableStateOf(false) }
    var hasOrganization by remember { mutableStateOf(false) }
    var developerOptionsEnabled by remember { mutableStateOf(developerOptionsEnabled(context)) }
    val scope = rememberCoroutineScope()

    fun continueAfterPermissions() {
        developerOptionsEnabled = developerOptionsEnabled(context)
        step = if (developerOptionsEnabled) AppStep.Organization else AppStep.DeveloperOptions
    }

    when (step) {
        AppStep.Welcome -> WelcomeScreen(
            signedIn = FirebaseAuth.getInstance().currentUser != null,
            onContinue = { step = AppStep.Permissions },
        )
        AppStep.Permissions -> PermissionScreen(onContinue = ::continueAfterPermissions)
        AppStep.DeveloperOptions -> DeveloperOptionsScreen(
            enabled = developerOptionsEnabled,
            onOpenSettings = {
                val action = if (developerOptionsEnabled) Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS
                else Settings.ACTION_DEVICE_INFO_SETTINGS
                context.startActivity(Intent(action))
            },
            onCheckAgain = {
                developerOptionsEnabled = developerOptionsEnabled(context)
                if (developerOptionsEnabled) step = AppStep.Organization
            },
        )
        AppStep.Organization -> OrganizationScreen(
            checking = checkingOrg,
            hasOrganization = hasOrganization,
            onCheck = {
                checkingOrg = true
                hasOrganization = false
                val user = FirebaseAuth.getInstance().currentUser
                if (user == null) {
                    checkingOrg = false
                    step = AppStep.Welcome
                } else {
                    scope.launch {
                        hasOrganization = runCatching {
                            organizationIdFrom(user.getIdToken(true).await().token.orEmpty())
                        }.getOrNull() != null
                        checkingOrg = false
                    }
                }
            },
            onContinue = {
                FirebaseAuth.getInstance().currentUser?.email?.let { email ->
                    preferences.edit().putString("completed_for", email).apply()
                }
                step = AppStep.Home
            },
        )
        AppStep.Home -> ShareContent()
    }
}

@Composable
private fun ProductFrame(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.herekitty_icon),
            contentDescription = "HereKitty",
            modifier = Modifier.size(88.dp),
            contentScale = ContentScale.Fit,
        )
        Spacer(Modifier.height(20.dp))
        content()
    }
}

@Composable
private fun WelcomeScreen(signedIn: Boolean, onContinue: () -> Unit) = ProductFrame {
    Text("HereKitty", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
    Text(
        "Share this Android device securely with your HereKitty desktop app.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
    )
    if (signedIn) Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
    else LoginContent(onSignedIn = onContinue)
}

@Composable
private fun PermissionScreen(onContinue: () -> Unit) {
    val context = LocalContext.current
    var permissionRevision by remember { mutableStateOf(0) }
    fun granted(permission: String): Boolean {
        permissionRevision
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
    val notificationGranted = Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS)
    val nearbyGranted = Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.NEARBY_WIFI_DEVICES)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionRevision++
    }

    ProductFrame {
        SetupCard(
            "Set up device sharing",
            "Nearby devices is needed to find this phone on your local network. Notifications are recommended so you can approve connection requests while the app is in the background.",
        )
        Spacer(Modifier.height(16.dp))
        PermissionRow("Nearby devices", nearbyGranted, required = true)
        PermissionRow("Notifications", notificationGranted, required = false)
        Spacer(Modifier.height(12.dp))
        if (!nearbyGranted || !notificationGranted) {
            Button(
                onClick = {
                    val missing = buildList {
                        if (!nearbyGranted && Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
                        if (!notificationGranted && Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                    }.toTypedArray()
                    launcher.launch(missing)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Allow permissions") }
        }
        Button(onClick = onContinue, enabled = nearbyGranted, modifier = Modifier.fillMaxWidth()) {
            Text("Continue")
        }
        if (!nearbyGranted) {
            Text(
                "Nearby devices is required to share this phone. If you declined it, enable it in Android Settings and return here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.parse("package:${context.packageName}")
                    })
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Open app settings") }
        }
    }
}

@Composable
private fun DeveloperOptionsScreen(enabled: Boolean, onOpenSettings: () -> Unit, onCheckAgain: () -> Unit) = ProductFrame {
    SetupCard(
        if (enabled) "Enable wireless debugging" else "Turn on Developer options",
        if (enabled) "In Developer options, turn on Wireless debugging. HereKitty will check it again whenever you begin sharing, but Android may ask you to confirm it after joining a new Wi-Fi network."
        else "Android keeps this setting hidden by default. Open About phone, tap Build number seven times, confirm your screen lock, then return here.",
    )
    Spacer(Modifier.height(16.dp))
    Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) { Text(if (enabled) "Open Developer options" else "Open About phone") }
    OutlinedButton(onClick = onCheckAgain, modifier = Modifier.fillMaxWidth()) { Text("I've enabled it") }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, required: Boolean) {
    Text(
        text = "$label · ${if (granted) "Allowed" else if (required) "Required" else "Optional"}",
        style = MaterialTheme.typography.bodyMedium,
        color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
    )
}

@Composable
private fun OrganizationScreen(checking: Boolean, hasOrganization: Boolean, onCheck: () -> Unit, onContinue: () -> Unit) {
    LaunchedEffect(Unit) { onCheck() }
    ProductFrame {
        when {
            checking -> {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text("Checking your HereKitty access…")
            }
            hasOrganization -> {
                SetupCard("You're all set", "Your account belongs to a HereKitty organization.")
                Spacer(Modifier.height(16.dp))
                Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("Go to my phone") }
            }
            else -> {
                SetupCard("Ask your administrator", "Your Google account is signed in, but it has not been added to a HereKitty organization yet. Ask your administrator to add this account, then tap Check again.")
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = onCheck, modifier = Modifier.fillMaxWidth()) { Text("Check again") }
            }
        }
    }
}

@Composable
private fun SetupCard(title: String, body: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun developerOptionsEnabled(context: android.content.Context): Boolean = runCatching {
    Settings.Global.getInt(context.contentResolver, "development_settings_enabled", 0) == 1
}.getOrDefault(false)

private fun organizationIdFrom(idToken: String): String? = runCatching {
    val payload = idToken.split('.')[1]
    val json = String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP))
    JSONObject(json).optString("orgId").takeIf { it.isNotBlank() }
}.getOrNull()
