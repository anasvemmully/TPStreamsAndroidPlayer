package com.tpstreams.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class BackgroundPlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val _player = MutableLiveData<TPStreamsPlayer?>()
    val player: LiveData<TPStreamsPlayer?> = _player

    fun initializePlayer(assetId: String, accessToken: String) {
        if (_player.value != null) return // Already initialized

        _player.value = TPStreamsPlayer.create(
            context = getApplication(),
            assetId = assetId,
            accessToken = accessToken,
            shouldAutoPlay = true,
            enableDownload = true,
            enableBackgroundPlayback = true,
            disableCaption = true,
            customTitle = "Title sample",
            customArtist = "Hooman"
        )
    }

    override fun onCleared() {
        super.onCleared()
        _player.value?.release()
        _player.value = null
    }
}
