package `in`.instantconnect.httpprobe

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import `in`.instantconnect.httpprobe.data.AppDatabase
import `in`.instantconnect.httpprobe.databinding.FragmentFirstBinding
import `in`.instantconnect.httpprobe.service.ProbeService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null
    private val binding get() = _binding!!

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        checkPermissions()

        val adapter = ProbeConfigAdapter(
            onHitNowClick = { config ->
                val intent = Intent(requireContext(), ProbeService::class.java).apply {
                    action = "ACTION_HIT_NOW"
                    putExtra("EXTRA_CONFIG_ID", config.id)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    requireContext().startForegroundService(intent)
                } else {
                    requireContext().startService(intent)
                }
            },
            onEditClick = { config ->
                val action = FirstFragmentDirections.actionFirstFragmentToSecondFragment(config.id)
                findNavController().navigate(action)
            },
            onLogsClick = { config ->
                val action = FirstFragmentDirections.actionFirstFragmentToLogsFragment(config.id)
                findNavController().navigate(action)
            },
            onDeleteClick = { config ->
                viewLifecycleOwner.lifecycleScope.launch {
                    AppDatabase.getDatabase(requireContext()).probeDao().deleteConfig(config)
                }
            }
        )
        binding.recyclerViewConfigs.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                AppDatabase.getDatabase(requireContext()).probeDao().getAllConfigsFlow()
                    .collectLatest { configs ->
                        binding.recyclerViewConfigs.adapter?.let {
                            (it as ProbeConfigAdapter).submitList(configs)
                        }
                        binding.emptyView.visibility = if (configs.isEmpty()) View.VISIBLE else View.GONE
                    }
            }
        }
    }

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECEIVE_SMS)
        }
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_SMS)
        }
        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
