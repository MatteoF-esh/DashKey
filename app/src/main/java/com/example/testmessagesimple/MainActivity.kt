package com.example.testmessagesimple

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import com.example.testmessagesimple.BuildConfig
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.room.Room
import com.example.testmessagesimple.data.AuthRepository
import com.example.testmessagesimple.data.UserInfo
import com.example.testmessagesimple.ui.theme.TestMessageSimpleTheme
import com.example.testmessagesimple.utils.CryptoManager
import com.example.testmessagesimple.utils.SecurityUtils
import com.example.testmessagesimple.utils.TokenManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

// --- ViewModel ---
class AuthViewModel(private val tokenManager: TokenManager, private val context: Context) : ViewModel() {
    private val authRepository = AuthRepository()
    private val cryptoManager = CryptoManager(context)

    var currentUser by mutableStateOf<UserInfo?>(null)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    // --- Brute Force Protection ---
    private var failedAttempts = 0
    private val maxFailedAttempts = 5
    private val lockoutDurationSeconds = 30
    var lockoutTimeRemaining by mutableStateOf(0)
        private set
    val isLockedOut: Boolean
        get() = lockoutTimeRemaining > 0

    init {
        checkForSavedSession()
        checkForActiveLockout()
    }

    private fun checkForSavedSession() {
        val authData = tokenManager.getAuthData()
        if (authData != null) {
            currentUser = authData.second
            // Définir l'utilisateur actuel pour le CryptoManager
            cryptoManager.setCurrentUser(authData.second.id)
        }
    }

    private fun checkForActiveLockout() {
        val lockoutUntil = tokenManager.getLockoutUntil()
        val currentTime = System.currentTimeMillis()
        if (lockoutUntil > currentTime) {
            val remainingSeconds = TimeUnit.MILLISECONDS.toSeconds(lockoutUntil - currentTime).toInt()
            startLockoutCountdown(remainingSeconds, "Trop de tentatives. Veuillez patienter $remainingSeconds secondes.")
        }
    }

    private fun startLockoutCountdown(duration: Int, message: String) {
        errorMessage = message
        viewModelScope.launch {
            lockoutTimeRemaining = duration
            while (lockoutTimeRemaining > 0) {
                delay(1000)
                lockoutTimeRemaining--
            }
            errorMessage = null // Clear lockout message
        }
    }

    private fun handleFailedAttempt() {
        failedAttempts++
        if (failedAttempts >= maxFailedAttempts) {
            val lockoutUntil = System.currentTimeMillis() + (lockoutDurationSeconds * 1000)
            tokenManager.saveLockoutUntil(lockoutUntil)
            failedAttempts = 0 // Reset for the next cycle
            startLockoutCountdown(lockoutDurationSeconds, "Trop de tentatives. Veuillez patienter $lockoutDurationSeconds secondes.")
        }
    }

    fun login(email: String, password: String) {
        if (isLockedOut) {
            errorMessage = "Trop de tentatives. Veuillez patienter $lockoutTimeRemaining secondes."
            return
        }

        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            val result = authRepository.login(email, password)
            result.onSuccess { authResponse ->
                tokenManager.saveAuthData(authResponse.token, authResponse.user)
                tokenManager.clearLockout() // Clear lockout on success
                currentUser = authResponse.user
                failedAttempts = 0 // Reset on success

                // Définir l'utilisateur actuel pour le CryptoManager
                Log.d("AuthViewModel", "Définition de l'utilisateur ${authResponse.user.id} pour le CryptoManager (login)")
                cryptoManager.setCurrentUser(authResponse.user.id)

                // Si l'utilisateur n'a pas de clé publique, la générer et l'envoyer
                if (authResponse.user.publicKey.isNullOrEmpty()) {
                    Log.d("AuthViewModel", "L'utilisateur n'a pas de clé publique, génération en cours...")
                    try {
                        val publicKey = cryptoManager.initializeKeys()
                        Log.d("AuthViewModel", "Clé publique générée (login): ${publicKey.take(50)}...")

                        // Vérifier que la clé privée est bien stockée localement
                        if (cryptoManager.hasPrivateKey()) {
                            Log.d("AuthViewModel", "✅ Clé privée confirmée dans le Keystore local (login)")
                            cryptoManager.logKeysSummary()
                        }

                        // Envoyer la clé publique au serveur
                        Log.d("AuthViewModel", "Envoi de la clé publique au serveur (login)...")
                        val updateResult = authRepository.updatePublicKey("Bearer ${authResponse.token}", publicKey)
                        updateResult.onSuccess {
                            Log.d("AuthViewModel", "✅ Clé publique envoyée avec succès (login)")
                            // Mettre à jour l'utilisateur avec la nouvelle clé
                            currentUser = authResponse.user.copy(publicKey = publicKey)
                            tokenManager.saveAuthData(authResponse.token, currentUser!!)
                        }.onFailure { e ->
                            Log.e("AuthViewModel", "❌ Erreur lors de l'envoi de la clé publique (login): ${e.message}", e)
                        }
                    } catch (e: Exception) {
                        Log.e("AuthViewModel", "❌ Erreur lors de la génération des clés (login): ${e.message}", e)
                        e.printStackTrace()
                    }
                } else {
                    // Si l'utilisateur a déjà une clé publique, s'assurer que les clés locales existent
                    Log.d("AuthViewModel", "L'utilisateur a déjà une clé publique sur le serveur")
                    if (!cryptoManager.hasKeys()) {
                        Log.w("AuthViewModel", "⚠️ Clés locales manquantes ! Régénération nécessaire...")
                        Log.w("AuthViewModel", "⚠️ ATTENTION : Les messages précédents seront illisibles")
                        // Note: Dans un cas réel E2EE, il faudrait gérer ce cas (perte de clés = perte de messages)
                        // Pour l'instant, on génère de nouvelles clés et on met à jour le serveur
                        try {
                            val publicKey = cryptoManager.initializeKeys()
                            if (cryptoManager.hasPrivateKey()) {
                                Log.d("AuthViewModel", "✅ Nouvelles clés régénérées")
                                cryptoManager.logKeysSummary()
                            }
                            val updateResult = authRepository.updatePublicKey("Bearer ${authResponse.token}", publicKey)
                            updateResult.onSuccess {
                                Log.d("AuthViewModel", "✅ Nouvelles clés générées et envoyées")
                                currentUser = authResponse.user.copy(publicKey = publicKey)
                                tokenManager.saveAuthData(authResponse.token, currentUser!!)
                            }
                        } catch (e: Exception) {
                            Log.e("AuthViewModel", "❌ Erreur lors de la régénération des clés", e)
                        }
                    } else {
                        Log.d("AuthViewModel", "✅ Clés locales trouvées")
                        // Vérifier et afficher le résumé
                        if (cryptoManager.hasPrivateKey()) {
                            Log.d("AuthViewModel", "✅ Clé privée confirmée dans le Keystore local")
                            cryptoManager.logKeysSummary()
                        }
                    }
                }
            }.onFailure {
                errorMessage = it.message
                handleFailedAttempt()
            }
            isLoading = false
        }
    }

    fun register(email: String, password: String) {
        if (isLockedOut) {
            errorMessage = "Trop de tentatives. Veuillez patienter $lockoutTimeRemaining secondes."
            return
        }

        viewModelScope.launch {
            isLoading = true
            errorMessage = null

            // S'inscrire SANS clé publique d'abord
            val result = authRepository.register(email, password, null)
            result.onSuccess { authResponse ->
                tokenManager.saveAuthData(authResponse.token, authResponse.user)
                tokenManager.clearLockout() // Clear lockout on success
                currentUser = authResponse.user
                failedAttempts = 0 // Reset on success

                // Maintenant, générer les clés avec l'ID utilisateur
                try {
                    Log.d("AuthViewModel", "Définition de l'utilisateur ${authResponse.user.id} pour le CryptoManager")
                    cryptoManager.setCurrentUser(authResponse.user.id)

                    Log.d("AuthViewModel", "Génération de la clé publique...")
                    val publicKey = cryptoManager.initializeKeys()
                    Log.d("AuthViewModel", "Clé publique générée: ${publicKey.take(50)}...")

                    // Vérifier que la clé privée est bien stockée localement
                    if (cryptoManager.hasPrivateKey()) {
                        Log.d("AuthViewModel", "✅ Clé privée confirmée dans le Keystore local")
                        // Afficher le résumé des clés
                        cryptoManager.logKeysSummary()
                    } else {
                        Log.e("AuthViewModel", "❌ ERREUR : Clé privée non trouvée dans le Keystore !")
                    }

                    // Envoyer la clé publique au serveur
                    Log.d("AuthViewModel", "Envoi de la clé publique au serveur...")
                    val updateResult = authRepository.updatePublicKey("Bearer ${authResponse.token}", publicKey)
                    updateResult.onSuccess {
                        Log.d("AuthViewModel", "✅ Clé publique envoyée avec succès pour le nouvel utilisateur")
                        // Mettre à jour l'utilisateur avec la nouvelle clé
                        currentUser = authResponse.user.copy(publicKey = publicKey)
                        tokenManager.saveAuthData(authResponse.token, currentUser!!)
                    }.onFailure { e ->
                        Log.e("AuthViewModel", "❌ Erreur lors de l'envoi de la clé publique: ${e.message}", e)
                    }
                } catch (e: Exception) {
                    Log.e("AuthViewModel", "❌ Erreur lors de la génération des clés: ${e.message}", e)
                    e.printStackTrace()
                }
            }.onFailure {
                errorMessage = it.message
                handleFailedAttempt()
            }
            isLoading = false
        }
    }

    fun logout() {
        tokenManager.clearData()
        currentUser = null
    }
}

// --- Fonctions utilitaires ---
fun getConversationId(user1: String, user2: String): String {
    return listOf(user1, user2).sorted().joinToString("_")
}

fun Friendship.getOtherUser(currentUserEmail: String): String {
    return if (userOneEmail == currentUserEmail) userTwoEmail else userOneEmail
}

// --- Définition des écrans ---
sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Friends : Screen("friends", "Amis", Icons.Default.People)
    object Profile : Screen("profile", "Profil", Icons.Default.AccountCircle)
}

private lateinit var database: AppDatabase

// --- Activité Principale ---
class MainActivity : ComponentActivity() {
    private val authViewModel: AuthViewModel by viewModels {
        AuthViewModelFactory(TokenManager(applicationContext), applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 🔒 SÉCURITÉ : Vérification de l'environnement avant démarrage
        performSecurityChecks()

        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "secure-message-db"
        ).fallbackToDestructiveMigration().build()

        setContent {
            TestMessageSimpleTheme {
                AppShell(database.appDao(), authViewModel)
            }
        }
    }

    /**
     * Effectue des vérifications de sécurité au démarrage de l'application
     * En production, on peut bloquer l'app si l'environnement est compromis
     */
    private fun performSecurityChecks() {
        if (BuildConfig.DEBUG) {
            // En mode debug, on log juste les avertissements
            val report = SecurityUtils.performSecurityCheck(this)
            Log.d("MainActivity", "📊 Rapport de sécurité : $report")

            if (report.threatLevel != com.example.testmessagesimple.utils.ThreatLevel.LOW) {
                Log.w("MainActivity", "⚠️ Niveau de menace : ${report.threatLevel}")
            }
        } else {
            // En mode release, on peut être plus strict
            val isSecure = SecurityUtils.isSecureEnvironment(this)
            if (!isSecure) {
                Log.e("MainActivity", "🚨 ENVIRONNEMENT NON SÉCURISÉ DÉTECTÉ")
                // En production, vous pouvez afficher un message et bloquer l'app :
                // showSecurityWarningAndExit()
            }
        }
    }
}

// --- Structure principale ---
@Composable
fun AppShell(dao: AppDao, viewModel: AuthViewModel) {
    val navController = rememberNavController()
    val screens = listOf(Screen.Friends, Screen.Profile)
    var showLogin by remember { mutableStateOf(true) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val tokenManager = remember { TokenManager(context) }

    if (viewModel.currentUser == null) {
        if (showLogin) {
            LoginScreen(
                viewModel = viewModel,
                onNavigateToRegister = { showLogin = false }
            )
        } else {
            RegistrationScreen(
                viewModel = viewModel,
                onNavigateToLogin = { showLogin = true }
            )
        }
    } else {
        val token = tokenManager.getAuthData()?.first ?: ""
        val friendshipViewModel = remember(token) { FriendshipViewModel(token) }

        Scaffold(
            topBar = {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                val canNavigateBack = navController.previousBackStackEntry != null

                val title = if (currentRoute?.startsWith("messaging/") == true) {
                    navBackStackEntry?.arguments?.getString("friendEmail") ?: "Conversation"
                } else {
                    "DashKey"
                }

                AppTopBar(title, canNavigateBack) { navController.navigateUp() }
            },
            bottomBar = { AppNavigationBar(navController, screens) }
        ) { innerPadding ->
            AppNavHost(
                navController = navController,
                modifier = Modifier.padding(innerPadding),
                currentUser = viewModel.currentUser!!,
                friendshipViewModel = friendshipViewModel,
                appDao = dao,
                onDeleteAccount = { viewModel.logout() }
            )
        }
    }
}

// --- Écran de Connexion ---
@Composable
fun LoginScreen(viewModel: AuthViewModel, onNavigateToRegister: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text("DashKey", style = MaterialTheme.typography.displayMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Connexion", style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = email, onValueChange = { email = it; emailError = null },
                label = { Text("Adresse e-mail") }, isError = emailError != null,
                modifier = Modifier.fillMaxWidth(),
                supportingText = { if (emailError != null) Text(emailError!!, color = MaterialTheme.colorScheme.error) },
                readOnly = viewModel.isLockedOut
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text("Mot de passe") }, visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                readOnly = viewModel.isLockedOut
            )
            Spacer(modifier = Modifier.height(24.dp))

            Button(modifier = Modifier.fillMaxWidth(), onClick = {
                if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    emailError = "Format d'e-mail invalide."
                } else {
                    viewModel.login(email, password)
                }
            }, enabled = !viewModel.isLoading && !viewModel.isLockedOut) {
                if (viewModel.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Text("Se connecter")
                }
            }
            viewModel.errorMessage?.let {
                val message = if (viewModel.isLockedOut) {
                    "Trop de tentatives. Veuillez patienter ${viewModel.lockoutTimeRemaining} secondes."
                } else {
                    it
                }
                Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }
            TextButton(onClick = onNavigateToRegister, enabled = !viewModel.isLockedOut) {
                Text("Pas de compte ? S'inscrire")
            }
        }
    }
}

// --- Écran d'Inscription ---
@Composable
fun RegistrationScreen(viewModel: AuthViewModel, onNavigateToLogin: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text("DashKey", style = MaterialTheme.typography.displayMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Créer un compte", style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = email, onValueChange = { email = it; emailError = null },
                label = { Text("Adresse e-mail") }, isError = emailError != null,
                modifier = Modifier.fillMaxWidth(),
                supportingText = { if (emailError != null) Text(emailError!!, color = MaterialTheme.colorScheme.error) },
                readOnly = viewModel.isLockedOut
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = password, onValueChange = { password = it; passwordError = null },
                label = { Text("Mot de passe") }, visualTransformation = PasswordVisualTransformation(),
                isError = passwordError != null,
                modifier = Modifier.fillMaxWidth(),
                readOnly = viewModel.isLockedOut
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = confirmPassword, onValueChange = { confirmPassword = it; passwordError = null },
                label = { Text("Confirmer le mot de passe") }, visualTransformation = PasswordVisualTransformation(),
                isError = passwordError != null,
                modifier = Modifier.fillMaxWidth(),
                supportingText = { if (passwordError != null) Text(passwordError!!, color = MaterialTheme.colorScheme.error) },
                readOnly = viewModel.isLockedOut
            )
            Spacer(modifier = Modifier.height(24.dp))

            Button(modifier = Modifier.fillMaxWidth(), onClick = {
                if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    emailError = "Format d'e-mail invalide."
                } else if (password.length < 6) {
                    passwordError = "Le mot de passe doit contenir au moins 6 caractères."
                } else if (password != confirmPassword) {
                    passwordError = "Les mots de passe ne correspondent pas."
                } else {
                    viewModel.register(email, password)
                }
            }, enabled = !viewModel.isLoading && !viewModel.isLockedOut) {
                if (viewModel.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Text("S'inscrire")
                }
            }
            viewModel.errorMessage?.let {
                val message = if (viewModel.isLockedOut) {
                    "Trop de tentatives. Veuillez patienter ${viewModel.lockoutTimeRemaining} secondes."
                } else {
                    it
                }
                Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }
            TextButton(onClick = onNavigateToLogin, enabled = !viewModel.isLockedOut) {
                Text("Déjà un compte ? Se connecter")
            }
        }
    }
}


// --- Navigation ---
@Composable
fun AppNavHost(
    navController: NavHostController, modifier: Modifier, currentUser: UserInfo,
    friendshipViewModel: FriendshipViewModel, appDao: AppDao, onDeleteAccount: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val tokenManager = remember { TokenManager(context) }
    val token = tokenManager.getAuthData()?.first ?: ""

    NavHost(navController = navController, startDestination = Screen.Friends.route, modifier = modifier) {
        composable(
            route = "messaging/{friendEmail}/{friendId}",
            arguments = listOf(
                navArgument("friendEmail") { type = NavType.StringType },
                navArgument("friendId") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val friendEmail = backStackEntry.arguments?.getString("friendEmail") ?: return@composable
            val friendId = backStackEntry.arguments?.getInt("friendId") ?: return@composable

            val context = LocalContext.current
            val messagingViewModel = remember(friendId) {
                MessagingViewModel(context.applicationContext as android.app.Application, token)
            }

            // Charger les messages au démarrage
            LaunchedEffect(friendId) {
                messagingViewModel.loadMessages(friendId)
            }

            MessagingScreen(
                currentUser = currentUser,
                friendEmail = friendEmail,
                friendId = friendId,
                viewModel = messagingViewModel
            )
        }

        composable(Screen.Friends.route) {
            FriendsScreen(currentUser, friendshipViewModel, navController)
        }

        composable(Screen.Profile.route) {
            ProfileScreen(currentUser, onDeleteAccount)
        }
    }
}

// --- Écrans principaux ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    currentUser: UserInfo,
    friendshipViewModel: FriendshipViewModel,
    navController: NavHostController
) {
    var email by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf<String?>(null) }
    var friendToDelete by remember { mutableStateOf<com.example.testmessagesimple.data.FriendResponse?>(null) }

    // Rafraîchir la liste d'amis quand l'écran devient visible
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                friendshipViewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Dialog de suppression
    friendToDelete?.let { friendResponse ->
        AlertDialog(
            onDismissRequest = { friendToDelete = null },
            title = { Text("Confirmer la suppression") },
            text = { Text("Voulez-vous vraiment supprimer ${friendResponse.friend.email} ?") },
            confirmButton = {
                TextButton(onClick = {
                    friendshipViewModel.deleteFriend(friendResponse.friendshipId)
                    friendToDelete = null
                }) { Text("Confirmer") }
            },
            dismissButton = {
                TextButton(onClick = { friendToDelete = null }) { Text("Annuler") }
            }
        )
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Ajouter un ami", style = MaterialTheme.typography.headlineSmall)
            Button(
                onClick = { friendshipViewModel.refresh() },
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text("Rafraîchir")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it; emailError = null; friendshipViewModel.clearError() },
                label = { Text("E-mail de l'ami") },
                modifier = Modifier.weight(1f),
                isError = emailError != null,
                supportingText = {
                    if (emailError != null) {
                        Text(emailError!!, color = MaterialTheme.colorScheme.error)
                    }
                }
            )
            Button(
                onClick = {
                    when {
                        !Patterns.EMAIL_ADDRESS.matcher(email).matches() ->
                            emailError = "Format d'e-mail invalide."
                        currentUser.email.equals(email, ignoreCase = true) ->
                            emailError = "Vous ne pouvez pas vous ajouter."
                        else -> {
                            emailError = null
                            friendshipViewModel.sendFriendRequestByEmail(email) {
                                email = ""
                            }
                        }
                    }
                },
                modifier = Modifier.padding(start = 8.dp),
                enabled = !friendshipViewModel.isLoading
            ) {
                if (friendshipViewModel.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Text("Ajouter")
                }
            }
        }

        // Afficher les erreurs API
        friendshipViewModel.errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        // Demandes reçues - TOUJOURS afficher cette section
        Text(
            "Demandes reçues (${friendshipViewModel.receivedRequests.size})",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 24.dp)
        )

        if (friendshipViewModel.receivedRequests.isEmpty()) {
            // Message quand il n'y a pas de demandes
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(
                    "Aucune demande d'ami en attente",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // Liste des demandes
            LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
                items(friendshipViewModel.receivedRequests) { request ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Demande de: ${request.sender.email}",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                            Text(
                                "Reçu le: ${request.createdAt}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Button(
                                    onClick = {
                                        println("DEBUG UI: Refus de la demande ${request.id}")
                                        friendshipViewModel.declineRequest(request.id)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Text("Refuser")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        println("DEBUG UI: Acceptation de la demande ${request.id}")
                                        friendshipViewModel.acceptRequest(request.id)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Text("Accepter")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Liste des amis
        Text(
            "Mes Amis (${friendshipViewModel.friends.size})",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 24.dp)
        )
        LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
            items(friendshipViewModel.friends) { friendResponse ->
                AcceptedFriendCard(
                    email = friendResponse.friend.email,
                    onChat = {
                        navController.navigate("messaging/${friendResponse.friend.email}/${friendResponse.friend.id}")
                    },
                    onRemove = { friendToDelete = friendResponse }
                )
            }
        }
    }
}

@Composable
fun ProfileScreen(user: UserInfo?, onDeleteAccount: () -> Unit, modifier: Modifier = Modifier) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Se déconnecter") },
            text = { Text("Voulez-vous vraiment vous déconnecter ?") },
            confirmButton = { TextButton(onClick = { onDeleteAccount(); showDeleteDialog = false }) { Text("Confirmer") } },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Annuler") } }
        )
    }
    Column(modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (user != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("ID: ${user.id}", style = MaterialTheme.typography.bodyLarge)
                    Text("Email: ${user.email}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = { showDeleteDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                Text("Se déconnecter")
            }
        } else {
            Text("Déconnecté.")
        }
    }
}

@Composable
fun MessagingScreen(
    currentUser: UserInfo,
    friendEmail: String,
    friendId: Int,
    viewModel: MessagingViewModel
) {
    var text by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        // Afficher les erreurs si présentes
        viewModel.errorMessage?.let { error ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).padding(8.dp),
            reverseLayout = true
        ) {
            items(viewModel.messages.reversed()) { message ->
                val isFromMe = message.senderId == currentUser.id
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = if (isFromMe) Arrangement.End else Arrangement.Start
                ) {
                    MessageBubbleApi(message, isFromMe, currentUser.email, friendEmail)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Message...") },
                modifier = Modifier.weight(1f),
                enabled = !viewModel.isLoading
            )
            Button(
                onClick = {
                    if (text.isNotBlank()) {
                        viewModel.sendMessage(friendId, text) {
                            text = ""
                        }
                    }
                },
                modifier = Modifier.padding(start = 8.dp),
                enabled = !viewModel.isLoading && text.isNotBlank()
            ) {
                if (viewModel.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Text("Envoyer")
                }
            }
        }
    }
}

// --- Composants UI ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(title: String, canNavigateBack: Boolean, onNavigateUp: () -> Unit) {
    TopAppBar(
        title = { Text(text = title) },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary),
        navigationIcon = {
            if (canNavigateBack) {
                IconButton(onClick = onNavigateUp) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour", tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    )
}

@Composable
fun AppNavigationBar(navController: NavHostController, screens: List<Screen>) {
    NavigationBar {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination
        screens.forEach { screen ->
            NavigationBarItem(
                icon = { Icon(screen.icon, contentDescription = screen.label) },
                label = { Text(screen.label) },
                selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                onClick = {
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcceptedFriendCard(email: String, onChat: () -> Unit, onRemove: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), onClick = onChat) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(email, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, "Supprimer", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun MessageBubble(message: Message, isFromMe: Boolean) {
    val backgroundColor = if (isFromMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = backgroundColor)) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Affiche "Moi" ou l'email de l'expéditeur
            Text(
                text = if (isFromMe) "Moi" else message.senderEmail,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(message.text)
        }
    }
}

@Composable
fun MessageBubbleApi(message: com.example.testmessagesimple.data.Message, isFromMe: Boolean, currentUserEmail: String, friendEmail: String) {
    val backgroundColor = if (isFromMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = backgroundColor)) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Affiche "Moi" ou l'email de l'ami
            Text(
                text = if (isFromMe) "Moi" else friendEmail,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(message.content)
        }
    }
}

