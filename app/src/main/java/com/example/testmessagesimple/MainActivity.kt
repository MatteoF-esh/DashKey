package com.example.testmessagesimple

import android.os.Bundle
import android.util.Patterns
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
import com.example.testmessagesimple.utils.TokenManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

// --- ViewModel ---
class AuthViewModel(private val tokenManager: TokenManager) : ViewModel() {
    private val authRepository = AuthRepository()

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
            result.onSuccess {
                tokenManager.saveAuthData(it.token, it.user)
                tokenManager.clearLockout() // Clear lockout on success
                currentUser = it.user
                failedAttempts = 0 // Reset on success
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
            val result = authRepository.register(email, password)
            result.onSuccess {
                tokenManager.saveAuthData(it.token, it.user)
                tokenManager.clearLockout() // Clear lockout on success
                currentUser = it.user
                failedAttempts = 0 // Reset on success
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
        AuthViewModelFactory(TokenManager(applicationContext))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
    val coroutineScope = rememberCoroutineScope()

    val onAddMessage: (String, String) -> Unit = { friendEmail, text ->
        coroutineScope.launch {
            val conversationId = getConversationId(currentUser.email, friendEmail)
            val message = com.example.testmessagesimple.data.Message(id=0, senderId = currentUser.id, receiverId = 0, content = text, createdAt = "", sender=null, receiver = null)
            //appDao.insertMessage(message)
        }
    }

    NavHost(navController = navController, startDestination = Screen.Friends.route, modifier = modifier) {
        composable(
            route = "messaging/{friendEmail}",
            arguments = listOf(navArgument("friendEmail") { type = NavType.StringType })
        ) { backStackEntry ->
            val friendEmail = backStackEntry.arguments?.getString("friendEmail") ?: return@composable
            val conversationId = getConversationId(currentUser.email, friendEmail)
            val messages by appDao.getMessagesForConversation(conversationId).collectAsState(initial = emptyList())

            MessagingScreen(currentUser, messages) { text -> onAddMessage(friendEmail, text) }
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

    // Dialog de suppression
    friendToDelete?.let { friend ->
        AlertDialog(
            onDismissRequest = { friendToDelete = null },
            title = { Text("Confirmer la suppression") },
            text = { Text("Voulez-vous vraiment supprimer ${friend.email} ?") },
            confirmButton = {
                TextButton(onClick = {
                    friendshipViewModel.deleteFriend(friend.id)
                    friendToDelete = null
                }) { Text("Confirmer") }
            },
            dismissButton = {
                TextButton(onClick = { friendToDelete = null }) { Text("Annuler") }
            }
        )
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Ajouter un ami", style = MaterialTheme.typography.headlineSmall)

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
                            friendshipViewModel.sendFriendRequest(email) {
                                email = ""
                            }
                        }
                    }
                },
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text("Ajouter")
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

        // Demandes reçues
        if (friendshipViewModel.receivedRequests.isNotEmpty()) {
            Text(
                "Demandes reçues (${friendshipViewModel.receivedRequests.size})",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 24.dp)
            )
            LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
                items(friendshipViewModel.receivedRequests) { request ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Demande de: ${request.sender.email}",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Button(onClick = { friendshipViewModel.declineRequest(request.id) }) {
                                    Text("Refuser")
                                }
                                Button(
                                    onClick = { friendshipViewModel.acceptRequest(request.id) },
                                    modifier = Modifier.padding(start = 8.dp)
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
            items(friendshipViewModel.friends) { friend ->
                AcceptedFriendCard(
                    email = friend.email,
                    onChat = { navController.navigate("messaging/${friend.email}") },
                    onRemove = { friendToDelete = friend }
                )
            }
        }

        // Demandes envoyées
        if (friendshipViewModel.sentRequests.isNotEmpty()) {
            Text(
                "Demandes envoyées (${friendshipViewModel.sentRequests.size})",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 24.dp)
            )
            LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
                items(friendshipViewModel.sentRequests) { request ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(
                            text = "Demande envoyée à ${request.receiver.email}",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
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
    messages: List<com.example.testmessagesimple.Message>,
    onAddMessage: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.weight(1f).padding(8.dp), reverseLayout = true) {
            items(messages.reversed()) { message ->
                val isFromMe = message.sender == currentUser.email
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = if (isFromMe) Arrangement.End else Arrangement.Start
                ) {
                    MessageBubble(message, isFromMe)
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = text, onValueChange = { text = it }, label = {Text("Message...")}, modifier = Modifier.weight(1f))
            Button(onClick = { if (text.isNotBlank()) { onAddMessage(text); text = "" } }, modifier = Modifier.padding(start = 8.dp)) {
                Text("Envoyer")
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
fun MessageBubble(message: com.example.testmessagesimple.Message, isFromMe: Boolean) {
    val backgroundColor = if (isFromMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = backgroundColor)) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Affiche "Moi" ou l'email de l'expéditeur
            Text(
                text = if (isFromMe) "Moi" else message.sender,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(message.text)
        }
    }
}
