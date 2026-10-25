package com.project.bcv.feature.statsnotify.stats.presentation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.project.bcv.core.presentation.Remote
import com.project.bcv.core.presentation.loadInto
import com.project.bcv.feature.statsnotify.stats.domain.ProfileStats
import com.project.bcv.feature.statsnotify.stats.domain.StatsRepository

class StatsViewModel(private val repository: StatsRepository) : ViewModel() {
    private val mutableState = MutableLiveData<Remote<ProfileStats>>()
    val state: LiveData<Remote<ProfileStats>> = mutableState

    init { load() }

    fun load() = loadInto(mutableState, repository::stats)
}
