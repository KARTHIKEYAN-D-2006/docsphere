package com.docsphere.ui.viewer

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docsphere.data.model.DocumentEntity
import com.docsphere.data.repository.DocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FileViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val documentRepository: DocumentRepository
) : ViewModel() {

    private val rawDocId: String = savedStateHandle.get<String>("docId") ?: ""
    val docId: String = try { Uri.decode(rawDocId) } catch (_: Exception) { rawDocId }

    private val _document = MutableStateFlow<DocumentEntity?>(null)
    val document: StateFlow<DocumentEntity?> = _document.asStateFlow()

    init {
        loadDocument()
    }

    fun loadDocument() {
        viewModelScope.launch {
            if (docId.isNotBlank()) {
                val doc = documentRepository.getDocumentById(docId)
                _document.value = doc
            }
        }
    }

    fun deleteDocument(doc: DocumentEntity) {
        viewModelScope.launch {
            documentRepository.deleteDocument(doc.id)
            _document.value = null
        }
    }
}
