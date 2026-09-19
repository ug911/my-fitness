package com.ug911.myfitness.ui.exercise

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ug911.myfitness.data.model.ExerciseDoc
import com.ug911.myfitness.data.repository.KnowledgeRepository
import com.ug911.myfitness.di.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class ExerciseGuideViewModel(
    knowledge: KnowledgeRepository,
    private val exerciseId: String,
) : ViewModel() {

    val doc: StateFlow<ExerciseDoc?> = knowledge.observeExercises()
        .map { exercises -> exercises.firstOrNull { it.id == exerciseId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    companion object {
        fun factory(container: AppContainer, exerciseId: String): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { ExerciseGuideViewModel(container.knowledge, exerciseId) }
            }
    }
}
