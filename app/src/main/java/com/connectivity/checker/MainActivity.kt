package com.connectivity.checker

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.connectivity.checker.adapter.CheckResultAdapter
import com.connectivity.checker.databinding.ActivityMainBinding
import com.connectivity.checker.ui.EditCheckBottomSheet
import com.connectivity.checker.ui.VmSettingsDialog
import com.connectivity.checker.viewmodel.MainViewModel
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var adapter: CheckResultAdapter

    private val filePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@registerForActivityResult
        try {
            val content = contentResolver.openInputStream(uri)
                ?.bufferedReader()?.readText() ?: ""
            viewModel.loadConfig(content)
        } catch (e: Exception) {
            showError("Cannot read file: ${e.message}")
        }
    }

    private val exportFileLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri ?: return@registerForActivityResult
        try {
            val yaml = viewModel.exportYaml()
            contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(yaml) }
            showSuccess("Config exported")
        } catch (e: Exception) {
            showError("Export failed: ${e.message}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        setupList()
        setupButtons()
        observeViewModel()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_sample    -> { showSampleConfig(); true }
        R.id.action_export    -> { exportConfig(); true }
        R.id.action_vm        -> { VmSettingsDialog().show(supportFragmentManager, "vm"); true }
        else -> super.onOptionsItemSelected(item)
    }

    private fun setupList() {
        adapter = CheckResultAdapter(
            onRunClick    = { i -> viewModel.runSingle(i) },
            onEditClick   = { i ->
                val result = viewModel.checks.value.getOrNull(i) ?: return@CheckResultAdapter
                EditCheckBottomSheet.newEdit(i, result.config)
                    .show(supportFragmentManager, "edit")
            },
            onDeleteClick = { i -> viewModel.deleteCheck(i) }
        )
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }
    }

    private fun setupButtons() {
        binding.btnLoadConfig.setOnClickListener { filePicker.launch(arrayOf("*/*")) }
        binding.btnRunAll.setOnClickListener {
            if (adapter.itemCount == 0) showError("Load a config or add checks first")
            else viewModel.runAll()
        }
        binding.fabAddCheck.setOnClickListener {
            EditCheckBottomSheet.newAdd().show(supportFragmentManager, "add")
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.checks.collect { checks ->
                adapter.submitList(checks.toList())
                binding.tvEmpty.visibility    = if (checks.isEmpty()) View.VISIBLE else View.GONE
                binding.recyclerView.visibility = if (checks.isEmpty()) View.GONE  else View.VISIBLE
            }
        }
        lifecycleScope.launch {
            viewModel.summary.collect { binding.tvSummary.text = it }
        }
        lifecycleScope.launch {
            viewModel.isRunning.collect { running ->
                binding.btnRunAll.isEnabled = !running
                binding.btnRunAll.text = if (running) "Running…" else "Run All"
            }
        }
        lifecycleScope.launch {
            viewModel.events.collect { event ->
                when (event) {
                    is MainViewModel.UiEvent.ShowError   -> showError(event.message)
                    is MainViewModel.UiEvent.ShowSuccess -> showSuccess(event.message)
                }
            }
        }
    }

    private fun exportConfig() {
        if (viewModel.exportYaml().isBlank()) { showError("No checks to export"); return }
        exportFileLauncher.launch("connectivity_config.yaml")
    }

    private fun showSampleConfig() {
        val sample = try {
            assets.open("sample_config.yaml").bufferedReader().readText()
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
        val tv = TextView(this).apply {
            text = sample; typeface = android.graphics.Typeface.MONOSPACE
            textSize = 11f; setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this)
            .setTitle("Sample config (YAML)")
            .setView(ScrollView(this).apply { addView(tv) })
            .setPositiveButton("Close", null)
            .setNeutralButton("Copy") { _, _ ->
                (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("config", sample))
                showSuccess("Copied to clipboard")
            }
            .show()
    }

    private fun showError(msg: String)   = Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
    private fun showSuccess(msg: String) = Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
}
