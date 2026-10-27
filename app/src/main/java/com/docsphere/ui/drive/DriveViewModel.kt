package com.docsphere.ui.drive

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docsphere.data.model.DocumentEntity
import com.docsphere.data.repository.DriveRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DriveUiState(
    val isSignedIn: Boolean = false,
    val userEmail: String? = null,
    val driveDocuments: List<DocumentEntity> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val statusMessage: String? = null
)

@HiltViewModel
class DriveViewModel @Inject constructor(
    private val driveRepository: DriveRepository
) : ViewModel() {

    private val _userState = MutableStateFlow(
        Pair(driveRepository.isSignedIn(), driveRepository.getSignedInEmail())
    )
    private val _loading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _status = MutableStateFlow<String?>(null)

    val uiState: StateFlow<DriveUiState> = combine(
        _userState,
        driveRepository.observeDriveDocuments(),
        _loading,
        _error,
        _status
    ) { user, docs, loading, error, status ->
        DriveUiState(
            isSignedIn = user.first,
            userEmail = user.second,
            driveDocuments = docs,
            isLoading = loading,
            error = error,
            statusMessage = status
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DriveUiState())

    fun getSignInIntent(): Intent = driveRepository.getSignInIntent()

    fun signOut() {
        viewModelScope.launch {
            driveRepository.signOut()
            _userState.value = Pair(false, null)
            _error.value = null
        }
    }

    fun onSignInResult(success: Boolean, email: String?, errorMsg: String? = null) {
        _userState.value = Pair(success, email)
        _error.value = if (!success) (errorMsg ?: "Sign-in failed. Please verify Google Cloud OAuth setup.") else null
    }

    fun importDriveFiles(uris: List<Uri>) {
        viewModelScope.launch {
            _loading.value = true
            _status.value = "Importing and indexing ${uris.size} document(s) from Drive…"
            var count = 0
            for (uri in uris) {
                val doc = driveRepository.importDriveDocument(uri)
                if (doc != null) count++
            }
            _loading.value = false
            _status.value = "Successfully imported $count document(s) from Google Drive!"
        }
    }

    fun deleteDriveDocument(id: String) {
        viewModelScope.launch {
            driveRepository.removeDriveDocument(id)
        }
    }
}
