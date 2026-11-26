package com.example.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ProjectSummary
import com.example.data.StudioRepository
import com.example.media.FontResolver
import com.example.model.AspectRatio
import com.example.model.StudioFont
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProjectsViewModel(
    private val repository: StudioRepository
) : ViewModel() {

    val projects: StateFlow<List<ProjectSummary>> = repository.projects
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    val fonts: StateFlow<List<StudioFont>> = repository.allFonts
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    private val _toastMessage = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    fun createProject(
        title: String,
        aspectRatio: AspectRatio,
        onCreated: (String) -> Unit
    ) {
        viewModelScope.launch {
            val summary = repository.createProject(title, aspectRatio)
            onCreated(summary.id)
        }
    }

    fun renameProject(id: String, newTitle: String) {
        viewModelScope.launch {
            repository.renameProject(id, newTitle)
            _toastMessage.tryEmit("Project renamed")
        }
    }

    fun duplicateProject(id: String) {
        viewModelScope.launch {
            repository.duplicateProject(id)
            _toastMessage.tryEmit("Project duplicated")
        }
    }

    fun deleteProject(id: String) {
        viewModelScope.launch {
            repository.deleteProject(id)
            _toastMessage.tryEmit("Project deleted")
        }
    }

    fun importFont(uri: Uri) {
        viewModelScope.launch {
            val result = repository.importCustomFont(uri)
            result.onSuccess { font ->
                _toastMessage.tryEmit("Imported font: ${font.displayName}")
            }.onFailure { err ->
                _toastMessage.tryEmit(err.message ?: "Failed to import font")
            }
        }
    }

    fun deleteFont(font: StudioFont) {
        viewModelScope.launch {
            repository.deleteCustomFont(font)
            FontResolver.clearEntry(font.id)
            _toastMessage.tryEmit("Removed font: ${font.displayName}")
        }
    }

    companion object {
        fun factory(repository: StudioRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ProjectsViewModel(repository) as T
                }
            }
    }
}
