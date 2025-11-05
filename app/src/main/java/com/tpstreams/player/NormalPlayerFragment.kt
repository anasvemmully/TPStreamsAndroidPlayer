package com.tpstreams.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels

class NormalPlayerFragment : Fragment() {

    private val viewModel: NormalPlayerViewModel by viewModels()
    private var playerView: TPStreamsPlayerView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_normal_player, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        playerView = view.findViewById(R.id.playerView)

        // Initialize player with sample video
        viewModel.initializePlayer(
            assetId = "8rEx9apZHFF",
            accessToken = "19aa0055-d965-4654-8fce-b804e70a46b0"
        )

        // Observe player and set to view
        viewModel.player.observe(viewLifecycleOwner) { player ->
            playerView?.setPlayer(player)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        playerView?.setPlayer(null)
        playerView = null
    }
}
