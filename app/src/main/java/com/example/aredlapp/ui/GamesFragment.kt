package com.example.aredlapp.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.aredlapp.R
import com.example.aredlapp.databinding.FragmentGamesBinding

class GamesFragment : Fragment() {

    private var _binding: FragmentGamesBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentGamesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnGameRandom.setOnClickListener {
            findNavController().navigate(R.id.action_games_to_random)
        }

        binding.btnGameRoulette.setOnClickListener {
            findNavController().navigate(R.id.action_games_to_roulette)
        }

        binding.btnGameAlphabet.setOnClickListener {
            findNavController().navigate(R.id.action_games_to_alphabet)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
