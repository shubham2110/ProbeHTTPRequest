package `in`.instantconnect.httpprobe

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import `in`.instantconnect.httpprobe.data.AppDatabase
import `in`.instantconnect.httpprobe.databinding.FragmentLogsBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LogsFragment : Fragment() {

    private var _binding: FragmentLogsBinding? = null
    private val binding get() = _binding!!
    private val args: LogsFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLogsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = ProbeLogAdapter()
        binding.recyclerViewLogs.adapter = adapter

        lifecycleScope.launch {
            AppDatabase.getDatabase(requireContext()).probeDao().getAllLogsFlow()
                .collectLatest { logs ->
                    // Filter logs for the specific configId
                    val filteredLogs = logs.filter { it.configId == args.configId }
                    adapter.submitList(filteredLogs)
                }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
