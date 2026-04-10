package `in`.instantconnect.httpprobe

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import `in`.instantconnect.httpprobe.data.AppDatabase
import `in`.instantconnect.httpprobe.data.ProbeConfig
import `in`.instantconnect.httpprobe.databinding.FragmentSettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { exportConfigs(it) }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { importConfigs(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonExport.setOnClickListener {
            exportLauncher.launch("probes_export.json")
        }

        binding.buttonImport.setOnClickListener {
            importLauncher.launch("application/json")
        }
    }

    private fun exportConfigs(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(requireContext().applicationContext)
                val configs = db.probeDao().getAllConfigsSync()
                val json = Gson().toJson(configs)
                requireContext().contentResolver.openOutputStream(uri)?.use { 
                    it.write(json.toByteArray())
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Configs exported successfully", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun importConfigs(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputStream = requireContext().contentResolver.openInputStream(uri)
                val reader = BufferedReader(InputStreamReader(inputStream))
                val json = reader.use { it.readText() }
                val type = object : TypeToken<List<ProbeConfig>>() {}.type
                val configs: List<ProbeConfig> = Gson().fromJson(json, type)
                
                val db = AppDatabase.getDatabase(requireContext().applicationContext)
                configs.forEach { 
                    val newConfig = it.copy(id = 0)
                    db.probeDao().insertConfig(newConfig)
                }
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Imported ${configs.size} configs", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
