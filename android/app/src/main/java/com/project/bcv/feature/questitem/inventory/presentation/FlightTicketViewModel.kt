package com.project.bcv.feature.questitem.inventory.presentation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.project.bcv.core.presentation.Event
import com.project.bcv.core.presentation.Remote
import com.project.bcv.core.presentation.launchRemote
import com.project.bcv.feature.questitem.inventory.domain.FlightPlace
import com.project.bcv.feature.questitem.inventory.domain.FlightResult
import com.project.bcv.feature.questitem.inventory.domain.FlightTicketStatus
import com.project.bcv.feature.questitem.inventory.domain.ItemUseRepository
import com.project.bcv.feature.questitem.inventory.domain.RegionOption

/** Chosen region and place; [places] is null while that region's places are loading. */
data class FlightSelection(
    val region: RegionOption? = null,
    val places: List<FlightPlace>? = null,
    val place: FlightPlace? = null,
    val moving: Boolean = false
)

class FlightTicketViewModel(private val repository: ItemUseRepository) : ViewModel() {
    private val mutableState = MutableLiveData<Remote<FlightTicketStatus>>()
    val state: LiveData<Remote<FlightTicketStatus>> = mutableState
    private val mutableSelection = MutableLiveData(FlightSelection())
    val selection: LiveData<FlightSelection> = mutableSelection
    private val mutableMessage = MutableLiveData<Event<String>>()
    val message: LiveData<Event<String>> = mutableMessage
    private val mutableMoved = MutableLiveData<Event<FlightResult>>()
    val moved: LiveData<Event<FlightResult>> = mutableMoved

    init { load() }

    fun load() = launchRemote<FlightTicketStatus>({ result ->
        mutableState.value = result
        // The first region is preselected so the screen opens on a usable choice.
        if (result is Remote.Ready) result.value.regions.firstOrNull()?.let(::selectRegion)
    }, repository::flight)

    fun selectRegion(region: RegionOption) {
        if (region == mutableSelection.value?.region && mutableSelection.value?.places != null) return
        mutableSelection.value = FlightSelection(region = region)
        launchRemote<List<FlightPlace>>({ result ->
            if (mutableSelection.value?.region != region) return@launchRemote
            when (result) {
                is Remote.Ready -> mutableSelection.value =
                    FlightSelection(region, result.value, result.value.firstOrNull())
                is Remote.Failed -> mutableMessage.value = Event(result.message)
                Remote.SessionExpired -> mutableState.value = Remote.SessionExpired
                Remote.Loading -> Unit
            }
        }) { repository.places(region.id) }
    }

    fun selectPlace(place: FlightPlace) {
        mutableSelection.value = mutableSelection.value?.copy(place = place)
    }

    fun move() {
        val current = mutableSelection.value ?: return
        val target = current.place ?: return
        if (current.moving) return
        launchRemote<FlightResult>({ result ->
            mutableSelection.value = mutableSelection.value?.copy(moving = result == Remote.Loading)
            when (result) {
                is Remote.Ready -> mutableMoved.value = Event(result.value)
                is Remote.Failed -> mutableMessage.value = Event(result.message)
                Remote.SessionExpired -> mutableState.value = Remote.SessionExpired
                Remote.Loading -> Unit
            }
        }) { repository.useFlight(target.id) }
    }
}
