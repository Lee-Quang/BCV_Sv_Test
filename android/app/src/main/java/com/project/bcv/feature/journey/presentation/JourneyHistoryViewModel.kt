package com.project.bcv.feature.journey.presentation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.project.bcv.core.presentation.Remote
import com.project.bcv.core.presentation.loadInto
import com.project.bcv.feature.journey.domain.JourneyHistoryRepository
import com.project.bcv.feature.journey.domain.JourneyRecord

class JourneyHistoryViewModel(private val repository: JourneyHistoryRepository) : ViewModel() {
    private val mutableState = MutableLiveData<Remote<List<JourneyRecord>>>()
    val state: LiveData<Remote<List<JourneyRecord>>> = mutableState

    init { load() }

    fun load() = loadInto(mutableState, repository::history)
}
