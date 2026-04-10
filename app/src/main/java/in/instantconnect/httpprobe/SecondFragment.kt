package `in`.instantconnect.httpprobe

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import `in`.instantconnect.httpprobe.data.AppDatabase
import `in`.instantconnect.httpprobe.data.ProbeConfig
import `in`.instantconnect.httpprobe.databinding.FragmentSecondBinding
import kotlinx.coroutines.launch

import androidx.navigation.fragment.navArgs

class SecondFragment : Fragment() {

    private var _binding: FragmentSecondBinding? = null
    private val binding get() = _binding!!
    private val args: SecondFragmentArgs by navArgs()
    private var existingConfig: ProbeConfig? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSecondBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (args.configId != -1L) {
            loadExistingConfig(args.configId)
        }

        binding.radioGroupMethod.setOnCheckedChangeListener { _, checkedId ->
            binding.layoutPayload.visibility = if (checkedId == R.id.radioPost) View.VISIBLE else View.GONE
        }

        binding.checkBoxReadSms.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutSmsFilter.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        binding.checkBoxNtfy.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutNtfy.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        binding.buttonSave.setOnClickListener {
            val url = binding.editTextUrl.text.toString()
            val method = if (binding.radioGet.isChecked) "GET" else "POST"
            val payload = binding.editTextPayload.text.toString().takeIf { method == "POST" }
            val intervalValue = binding.editTextInterval.text.toString().toLongOrNull() ?: 60L
            
            val unitPosition = binding.spinnerUnit.selectedItemPosition
            val intervalInSeconds = when (unitPosition) {
                1 -> intervalValue * 60 // Minutes
                2 -> intervalValue * 3600 // Hours
                else -> intervalValue // Seconds
            }

            val onlyOnNetworkChange = binding.checkBoxNetworkChange.isChecked
            val readSms = binding.checkBoxReadSms.isChecked
            val username = binding.editTextUsername.text.toString().takeIf { it.isNotBlank() }
            val password = binding.editTextPassword.text.toString().takeIf { it.isNotBlank() }
            val customHeaders = binding.editTextHeaders.text.toString().takeIf { it.isNotBlank() }

            // ntfy fields
            val isNtfy = binding.checkBoxNtfy.isChecked
            val ntfyUrl = binding.editTextNtfyUrl.text.toString().takeIf { it.isNotBlank() }
            val ntfyUsername = binding.editTextNtfyUsername.text.toString().takeIf { it.isNotBlank() }
            val ntfyPassword = binding.editTextNtfyPassword.text.toString().takeIf { it.isNotBlank() }
            val ntfyConsumerMode = binding.checkBoxNtfyConsumer.isChecked
            val ntfySince = binding.editTextNtfySince.text.toString().takeIf { it.isNotBlank() } ?: "all"
            val ntfyScheduled = binding.checkBoxNtfyScheduled.isChecked
            val ntfyFilters = binding.editTextNtfyFilters.text.toString().takeIf { it.isNotBlank() }
            val smsFilterRegex = binding.editTextSmsFilter.text.toString().takeIf { it.isNotBlank() }

            if (url.isBlank() && !ntfyConsumerMode) {
                binding.editTextUrl.error = "URL cannot be empty"
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val db = AppDatabase.getDatabase(requireContext())
                val config = existingConfig?.copy(
                    url = url,
                    method = method,
                    payload = payload,
                    intervalSeconds = intervalInSeconds,
                    onlyOnNetworkChange = onlyOnNetworkChange,
                    username = username,
                    password = password,
                    customHeaders = customHeaders,
                    readSms = readSms,
                    isNtfy = isNtfy,
                    ntfyUrl = ntfyUrl,
                    ntfyUsername = ntfyUsername,
                    ntfyPassword = ntfyPassword,
                    ntfyConsumerMode = ntfyConsumerMode,
                    ntfySince = ntfySince,
                    ntfyScheduled = ntfyScheduled,
                    ntfyFilters = ntfyFilters,
                    smsFilterRegex = smsFilterRegex
                ) ?: ProbeConfig(
                    url = url,
                    method = method,
                    payload = payload,
                    intervalSeconds = intervalInSeconds,
                    onlyOnNetworkChange = onlyOnNetworkChange,
                    username = username,
                    password = password,
                    customHeaders = customHeaders,
                    readSms = readSms,
                    isNtfy = isNtfy,
                    ntfyUrl = ntfyUrl,
                    ntfyUsername = ntfyUsername,
                    ntfyPassword = ntfyPassword,
                    ntfyConsumerMode = ntfyConsumerMode,
                    ntfySince = ntfySince,
                    ntfyScheduled = ntfyScheduled,
                    ntfyFilters = ntfyFilters,
                    smsFilterRegex = smsFilterRegex
                )

                if (existingConfig != null) {
                    db.probeDao().updateConfig(config)
                } else {
                    db.probeDao().insertConfig(config)
                }
                findNavController().navigateUp()
            }
        }
    }

    private fun loadExistingConfig(id: Long) {
        lifecycleScope.launch {
            val config = AppDatabase.getDatabase(requireContext()).probeDao().getConfigById(id)
            if (config != null) {
                existingConfig = config
                binding.editTextUrl.setText(config.url)
                if (config.method == "POST") {
                    binding.radioPost.isChecked = true
                    binding.layoutPayload.visibility = View.VISIBLE
                    binding.editTextPayload.setText(config.payload)
                } else {
                    binding.radioGet.isChecked = true
                    binding.layoutPayload.visibility = View.GONE
                }
                
                binding.editTextInterval.setText(config.intervalSeconds.toString())
                binding.spinnerUnit.setSelection(0)
                
                binding.checkBoxNetworkChange.isChecked = config.onlyOnNetworkChange
                binding.checkBoxReadSms.isChecked = config.readSms
                binding.editTextUsername.setText(config.username)
                binding.editTextPassword.setText(config.password)
                binding.editTextHeaders.setText(config.customHeaders)

                // ntfy fields
                binding.checkBoxNtfy.isChecked = config.isNtfy
                binding.layoutNtfy.visibility = if (config.isNtfy) View.VISIBLE else View.GONE
                binding.editTextNtfyUrl.setText(config.ntfyUrl)
                binding.editTextNtfyUsername.setText(config.ntfyUsername)
                binding.editTextNtfyPassword.setText(config.ntfyPassword)
                binding.checkBoxNtfyConsumer.isChecked = config.ntfyConsumerMode
                binding.editTextNtfySince.setText(config.ntfySince)
                binding.checkBoxNtfyScheduled.isChecked = config.ntfyScheduled
                binding.editTextNtfyFilters.setText(config.ntfyFilters)
                
                binding.buttonSave.text = "Update Configuration"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
