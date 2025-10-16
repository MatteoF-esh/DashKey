package com.example.testmessagesimple

import android.os.Bundle
import android.util.Patterns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.room.Room
import com.example.testmessagesimple.ui.theme.TestMessageSimpleTheme
import kotlinx.coroutines.launch

// --- Modèles de données ---
data class User(val name: String, val email: String)

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
                AppShell(database.appDao())
            }
        }
    }
}

// --- Structure principale ---
@Composable
fun AppShell(dao: AppDao) {
    val navController = rememberNavController()
    val screens = listOf(Screen.Friends, Screen.Profile)
    var currentUser by remember { mutableStateOf<User?>(null) }

    if (currentUser == null) {
        LoginScreen(onLogin = { email, name -> currentUser = User(name, email) })
    } else {
        val friendships by dao.getAllFriendships(currentUser!!.email).collectAsState(initial = emptyList())

        Scaffold(
            topBar = {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                val canNavigateBack = navController.previousBackStackEntry != null

                val title = if (currentRoute?.startsWith("messaging/") == true) {
                    navBackStackEntry?.arguments?.getString("friendEmail") ?: "Conversation"
                } else {
                    "SecureMessage"
                }

                AppTopBar(title, canNavigateBack) { navController.navigateUp() }
            },
            bottomBar = { AppNavigationBar(navController, screens) }
        ) { innerPadding ->
            AppNavHost(
                navController = navController,
                modifier = Modifier.padding(innerPadding),
                currentUser = currentUser!!,
                friendships = friendships,
                appDao = dao,
                onDeleteAccount = { currentUser = null }
            )
        }
    }
}

// --- Écran de Connexion ---
@Composable
fun LoginScreen(onLogin: (email: String, name: String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text("Connexion", style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = email, onValueChange = { email = it; emailError = null },
                label = { Text("Adresse e-mail") }, isError = emailError != null,
                modifier = Modifier.fillMaxWidth(),
                supportingText = { if (emailError != null) Text(emailError!!, color = MaterialTheme.colorScheme.error) }
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Mot de passe") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(24.dp))

            Button(modifier = Modifier.fillMaxWidth(), onClick = {
                if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    emailError = "Format d'e-mail invalide."
                } else {
                    val name = email.split("@")[0].replaceFirstChar { it.uppercase() }
                    onLogin(email, name)
                }
            }) {
                Text("Se connecter")
            }
        }
    }
}


// --- Navigation ---
@Composable
fun AppNavHost(
    navController: NavHostController, modifier: Modifier, currentUser: User,
    friendships: List<Friendship>, appDao: AppDao, onDeleteAccount: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    val onAddFriend: (String) -> String? = { friendEmail ->
        when {
            !Patterns.EMAIL_ADDRESS.matcher(friendEmail).matches() -> "Format d'e-mail invalide."
            friendships.any { it.getOtherUser(currentUser.email) == friendEmail } -> "Cet utilisateur est déjà dans votre liste."
            currentUser.email.equals(friendEmail, ignoreCase = true) -> "Vous ne pouvez pas vous ajouter vous-même."
            else -> {
                coroutineScope.launch {
                    val users = listOf(currentUser.email, friendEmail).sorted()
                    val friendship = Friendship(users[0], users[1], FriendshipStatus.PENDING, currentUser.email)
                    appDao.insertFriendship(friendship)
                }
                null
            }
        }
    }

    val onUpdateFriendshipStatus: (Friendship, FriendshipStatus) -> Unit = { friendship, newStatus ->
        coroutineScope.launch {
            if (newStatus == FriendshipStatus.DECLINED) {
                appDao.deleteFriendship(friendship.userOneEmail, friendship.userTwoEmail)
            } else {
                appDao.updateFriendship(friendship.copy(status = newStatus))
            }
        }
    }

    val onRemoveFriend: (Friendship) -> Unit = { friendship ->
        coroutineScope.launch {
            val conversationId = getConversationId(friendship.userOneEmail, friendship.userTwoEmail)
            appDao.deleteMessagesForConversation(conversationId)
            appDao.deleteFriendship(friendship.userOneEmail, friendship.userTwoEmail)
        }
    }

    val onAddMessage: (String, String) -> Unit = { friendEmail, text ->
        coroutineScope.launch {
            val conversationId = getConversationId(currentUser.email, friendEmail)
            val message = Message(text = text, sender = currentUser.email, conversationId = conversationId)
            appDao.insertMessage(message)
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
            FriendsScreen(currentUser, friendships, onAddFriend, onUpdateFriendshipStatus, onRemoveFriend, navController)
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
    currentUser: User,
    friendships: List<Friendship>,
    onAddFriend: (String) -> String?,
    onUpdateFriendshipStatus: (Friendship, FriendshipStatus) -> Unit,
    onRemoveFriend: (Friendship) -> Unit,
    navController: NavHostController
) {
    var email by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var friendToDelete by remember { mutableStateOf<Friendship?>(null) }

    val receivedRequests = friendships.filter { it.status == FriendshipStatus.PENDING && it.initiatorUserId != currentUser.email }
    val sentRequests = friendships.filter { it.status == FriendshipStatus.PENDING && it.initiatorUserId == currentUser.email }
    val acceptedFriends = friendships.filter { it.status == FriendshipStatus.ACCEPTED }

    friendToDelete?.let {
        AlertDialog(
            onDismissRequest = { friendToDelete = null },
            title = { Text("Confirmer la suppression") },
            text = { Text("Voulez-vous vraiment supprimer cet ami ? Les messages seront aussi effacés.") },
            confirmButton = { TextButton(onClick = { onRemoveFriend(it); friendToDelete = null }) { Text("Confirmer") } },
            dismissButton = { TextButton(onClick = { friendToDelete = null }) { Text("Annuler") } }
        )
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Ajouter un ami", style = MaterialTheme.typography.headlineSmall)
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = email, onValueChange = { email = it; errorMessage = null },
                label = { Text("E-mail de l'ami") }, modifier = Modifier.weight(1f),
                isError = errorMessage != null,
                supportingText = { if (errorMessage != null) Text(errorMessage!!, color = MaterialTheme.colorScheme.error) }
            )
            Button(onClick = { errorMessage = onAddFriend(email).also { if (it == null) email = "" } }, modifier = Modifier.padding(start = 8.dp)) {
                Text("Ajouter")
            }
        }

        if (receivedRequests.isNotEmpty()) {
            Text("Demandes reçues (${receivedRequests.size})", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 24.dp))
            LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
                items(receivedRequests) { friendship ->
                    FriendRequestCard(
                        friendship = friendship,
                        onAccept = { onUpdateFriendshipStatus(friendship, FriendshipStatus.ACCEPTED) },
                        onDecline = { onUpdateFriendshipStatus(friendship, FriendshipStatus.DECLINED) }
                    )
                }
            }
        }

        Text("Mes Amis (${acceptedFriends.size})", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 24.dp))
        LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
            items(acceptedFriends) { friendship ->
                AcceptedFriendCard(
                    email = friendship.getOtherUser(currentUser.email),
                    onChat = { navController.navigate("messaging/${friendship.getOtherUser(currentUser.email)}") },
                    onRemove = { friendToDelete = friendship }
                )
            }
        }

        if (sentRequests.isNotEmpty()) {
            Text("Demandes envoyées (${sentRequests.size})", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 24.dp))
            LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
                items(sentRequests) { friendship ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(
                            text = "Demande envoyée à ${friendship.getOtherUser(currentUser.email)}",
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
fun ProfileScreen(user: User?, onDeleteAccount: () -> Unit, modifier: Modifier = Modifier) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Supprimer le compte") },
            text = { Text("Toutes vos données locales seront effacées. Voulez-vous continuer ?") },
            confirmButton = { TextButton(onClick = { onDeleteAccount(); showDeleteDialog = false }) { Text("Confirmer") } },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Annuler") } }
        )
    }
    Column(modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (user != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Nom: ${user.name}", style = MaterialTheme.typography.bodyLarge)
                    Text("Email: ${user.email}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = { showDeleteDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                Text("Supprimer le compte local")
            }
        } else {
            Text("Déconnecté.")
        }
    }
}

@Composable
fun MessagingScreen(
    currentUser: User,
    messages: List<Message>,
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

@Composable
fun FriendRequestCard(friendship: Friendship, onAccept: () -> Unit, onDecline: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Demande de: ${friendship.initiatorUserId}", style = MaterialTheme.typography.bodyLarge)
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                Button(onClick = onDecline) { Text("Refuser") }
                Button(onClick = onAccept, modifier = Modifier.padding(start = 8.dp)) { Text("Accepter") }
            }
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
                text = if (isFromMe) "Moi" else message.sender,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(message.text)
        }
    }
}
