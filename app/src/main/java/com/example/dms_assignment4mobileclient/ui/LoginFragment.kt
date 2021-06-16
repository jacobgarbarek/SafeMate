package com.example.dms_assignment4mobileclient.ui

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.dms_assignment4mobileclient.databinding.FragmentLoginBinding
import com.example.dms_assignment4mobileclient.model.Location
import com.example.dms_assignment4mobileclient.viewmodel.MapViewModel

class LoginFragment : Fragment() {
    private val viewModel: MapViewModel by activityViewModels()
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.loginButton.setOnClickListener {
            val userLocation = Location(binding.usernameInput.text.toString())
            viewModel.setUserLocation(userLocation)
            val action = LoginFragmentDirections.actionMapsFragment()
            findNavController().navigate(action)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}