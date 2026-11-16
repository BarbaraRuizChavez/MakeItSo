/*
Copyright 2022 Google LLC

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package com.example.makeitso.model.service.impl

import com.example.makeitso.model.User
import com.example.makeitso.model.service.AccountService
import com.example.makeitso.model.service.trace
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AccountServiceImpl @Inject constructor(private val auth: FirebaseAuth) : AccountService {

  override val currentUserId: String
    get() = auth.currentUser?.uid.orEmpty()

  override val hasUser: Boolean
    get() = auth.currentUser != null

  override val currentUser: Flow<User>
    get() = callbackFlow {
      val listener =
        FirebaseAuth.AuthStateListener { auth ->
          this.trySend(auth.currentUser?.let { User(it.uid, it.isAnonymous) } ?: User())
        }
      auth.addAuthStateListener(listener)
      awaitClose { auth.removeAuthStateListener(listener) }
    }

  override suspend fun authenticate(email: String, password: String): Unit =
    trace(AUTHENTICATE_TRACE) {
      auth.signInWithEmailAndPassword(email, password).await()
      Unit
    }

  override suspend fun sendRecoveryEmail(email: String): Unit =
    trace(RECOVERY_EMAIL_TRACE) {
      auth.sendPasswordResetEmail(email).await()
    }

  override suspend fun createAnonymousAccount(): Unit =
    trace(CREATE_ANONYMOUS_ACCOUNT_TRACE) {
      auth.signInAnonymously().await()
      Unit
    }

  override suspend fun linkAccount(email: String, password: String): Unit =
    trace(LINK_ACCOUNT_TRACE) {
      val credential = EmailAuthProvider.getCredential(email, password)
      auth.currentUser!!.linkWithCredential(credential).await()
      Unit
    }

  override suspend fun deleteAccount(): Unit =
    trace(DELETE_ACCOUNT_TRACE) {
      auth.currentUser!!.delete().await()
    }

  override suspend fun signOut() {
    if (auth.currentUser!!.isAnonymous) {
      auth.currentUser!!.delete()
    }
    auth.signOut()

    // Sign the user back in anonymously.
    createAnonymousAccount()
  }

  companion object {
    private const val AUTHENTICATE_TRACE = "authenticate"
    private const val RECOVERY_EMAIL_TRACE = "sendRecoveryEmail"
    private const val CREATE_ANONYMOUS_ACCOUNT_TRACE = "createAnonymousAccount"
    private const val DELETE_ACCOUNT_TRACE = "deleteAccount"
    private const val LINK_ACCOUNT_TRACE = "linkAccount"
  }
}
