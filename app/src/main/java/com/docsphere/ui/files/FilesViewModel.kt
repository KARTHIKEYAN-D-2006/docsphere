package com.docsphere.ui.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docsphere.data.model.DocumentEntity
import com.docsphere.data.model.FileCategory
import com.docsphere.data.repository.DocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FilesUiState(
    val files: List<DocumentEntity> = emptyList(),
    val selectedCategory: FileCategory = FileCategory.ALL,
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val searchResults: List<DocumentEntity> = emptyList(),
    val isGridView: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class FilesViewModel @Inject constructor(
    private val documentRepository: DocumentRepository
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow(FileCategory.ALL)
    private val _searchQuery = MutableStateFlow("")
    private val _isGridView = MutableStateFlow(true)
    private val _isSearching = MutableStateFlow(false)
    private val _searchResults = MutableStateFlow<List<DocumentEntity>>(emptyList())

    // Fix: combine only supports up to 5 flows — split into nested combines
    private val _firstFive = combine(
        _selectedCategory.flatMapLatest { cat -> documentRepository.observeByCategory(cat) },
        _selectedCategory,
        _searchQuery,
        _isSearching,
        _searchResults
    ) { files, category, query, isSearching, results ->
        object {
            val files = files
            val category = category
            val query = query
            val isSearching = isSearching
            val results = results
        }
    }

    val uiState: StateFlow<FilesUiState> = combine(
        _firstFive,
        _isGridView
    ) { bundle, isGrid ->
        FilesUiState(
            files = bundle.files,
            selectedCategory = bundle.category,
            searchQuery = bundle.query,
            isSearching = bundle.isSearching,
            searchResults = bundle.results,
            isGridView = isGrid
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FilesUiState())

    init {
        viewModelScope.launch {
            _searchQuery
                .debounce(300)
                .filter { it.isNotBlank() }
                .collect { query ->
                    _isSearching.value = true
                    _searchResults.value = documentRepository.search(query)
                    _isSearching.value = false
                }
        }
    }

    fun setCategory(category: FileCategory) {
        _selectedCategory.value = category
        _searchQuery.value = ""
        _searchResults.value = emptyList()
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) _searchResults.value = emptyList()
    }

    fun toggleViewMode() { _isGridView.value = !_isGridView.value }

    fun importDocuments(uris: List<android.net.Uri>) {
        viewModelScope.launch {
            for (uri in uris) {
                documentRepository.importDocument(uri)
            }
        }
    }

    fun deleteDocument(doc: DocumentEntity) {
        viewModelScope.launch {
            documentRepository.deleteDocument(doc.id)
        }
    }

    fun retryOcr(doc: DocumentEntity) {
        viewModelScope.launch {
            documentRepository.retryOcr(doc.id)
        }
    }
}
