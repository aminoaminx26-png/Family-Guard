package com.example.core.navigation

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.child.ChildRootScreen
import com.example.child.ChildViewModel
import com.example.core.data.local.FamilyGuardDatabase
import com.example.core.data.local.LocalSessionPrefs
import com.example.core.data.local.PreferencesManager
import com.example.core.data.remote.AuthRepository
import com.example.core.data.remote.FamilyGuardRepository
import com.example.core.domain.model.AppRole
import com.example.core.ui.components.AuthScreen
import com.example.core.ui.components.RoleSelectionScreen
import com.example.parent.ParentRootScreen
import com.example.parent.ParentViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

@Composable
fun FamilyGuardNavigation() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authRepository = remember { AuthRepository(Firebase.auth) }
    val prefsManager = remember { PreferencesManager(context.applicationContext) }

    val currentUser by authRepository.observeAuthState()
        .collectAsStateWithLifecycle(initialValue = authRepository.currentUser)
    val sessionPrefs by prefsManager.prefsFlow
        .collectAsStateWithLifecycle(initialValue = LocalSessionPrefs())

    var isAutoSigningIn by remember { mutableStateOf(authRepository.currentUser == null) }

    LaunchedEffect(Unit) {
        if (authRepository.currentUser == null) {
            authRepository.attemptAutoSignIn(context)
        }
        isAutoSigningIn = false
    }

    val user = currentUser
    if (user == null) {
        // Strict Auth Gate: Feature ViewModels and Firestore listeners are NOT instantiated until authenticated
        AuthScreen(
            authRepository = authRepository,
            isAutoSigningIn = isAutoSigningIn,
            onSignedIn = {}
        )
    } else {
        key(user.uid) {
            when (sessionPrefs.role) {
                AppRole.UNSELECTED -> {
                    RoleSelectionScreen(
                        onSelectRole = { chosenRole ->
                            scope.launch { prefsManager.setRole(chosenRole) }
                        },
                        onSignOut = {
                            scope.launch { authRepository.signOut(context) }
                        }
                    )
                }

                AppRole.PARENT -> {
                    val parentViewModel: ParentViewModel = viewModel(
                        key = "parent_${user.uid}",
                        factory = viewModelFactory {
                            initializer {
                                val app = checkNotNull(this[APPLICATION_KEY]) as Application
                                val db = FirebaseFirestore.getInstance(
                                    app.getString(R.string.firestore_database_id)
                                )
                                val localDb = FamilyGuardDatabase.getInstance(app)
                                val prefs = PreferencesManager(app)
                                val repo = FamilyGuardRepository(db, localDb, prefs, Firebase.auth)
                                ParentViewModel(repo, prefs, authRepository, user.uid)
                            }
                        }
                    )
                    ParentRootScreen(viewModel = parentViewModel)
                }

                AppRole.CHILD -> {
                    val childViewModel: ChildViewModel = viewModel(
                        key = "child_${user.uid}",
                        factory = viewModelFactory {
                            initializer {
                                val app = checkNotNull(this[APPLICATION_KEY]) as Application
                                val db = FirebaseFirestore.getInstance(
                                    app.getString(R.string.firestore_database_id)
                                )
                                val localDb = FamilyGuardDatabase.getInstance(app)
                                val prefs = PreferencesManager(app)
                                val repo = FamilyGuardRepository(db, localDb, prefs, Firebase.auth)
                                ChildViewModel(repo, prefs, authRepository, user.uid)
                            }
                        }
                    )
                    ChildRootScreen(viewModel = childViewModel)
                }
            }
        }
    }
}
