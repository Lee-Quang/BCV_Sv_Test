package com.project.bcv.feature.questitem.inventory.presentation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.project.bcv.core.presentation.Event
import com.project.bcv.core.presentation.Remote
import com.project.bcv.core.presentation.launchRemote
import com.project.bcv.core.presentation.loadInto
import com.project.bcv.feature.questitem.inventory.domain.ItemUseRepository
import com.project.bcv.feature.questitem.inventory.domain.SpeedShoesStatus

class SpeedShoesViewModel(private val repository: ItemUseRepository) : ViewModel() {
    private val mutableState = MutableLiveData<Remote<SpeedShoesStatus>>()
    val state: LiveData<Remote<SpeedShoesStatus>> = mutableState
    private val mutableUsing = MutableLiveData(false)
    val using: LiveData<Boolean> = mutableUsing
    private val mutableUsed = MutableLiveData<Event<Result<SpeedShoesStatus>>>()
    /** Outcome of "Sử dụng 1 giày": the new status, or the reason it was refused. */
    val used: LiveData<Event<Result<SpeedShoesStatus>>> = mutableUsed

    init { load() }

    fun load() = loadInto(mutableState, repository::shoes)

    fun use() {
        if (mutableUsing.value == true) return
        launchRemote<SpeedShoesStatus>({ result ->
            mutableUsing.value = result == Remote.Loading
            when (result) {
                is Remote.Ready -> {
                    mutableState.value = result
                    mutableUsed.value = Event(Result.success(result.value))
                }
                is Remote.Failed -> mutableUsed.value = Event(Result.failure(IllegalStateException(result.message)))
                Remote.SessionExpired -> mutableState.value = result
                Remote.Loading -> Unit
            }
        }, repository::useShoes)
    }
}
