package com.tpstreams.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController

class HomeFragment : Fragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize TPStreamsPlayer SDK
        TPStreamsPlayer.init("6332n7")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Button>(R.id.btnNormalPlayer).setOnClickListener {
            findNavController().navigate(R.id.action_home_to_normalPlayer)
        }

        view.findViewById<Button>(R.id.btnBackgroundPlayer).setOnClickListener {
            findNavController().navigate(R.id.action_home_to_backgroundPlayer)
        }
    }
}
