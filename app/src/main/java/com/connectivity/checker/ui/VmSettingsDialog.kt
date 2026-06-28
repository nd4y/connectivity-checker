package com.connectivity.checker.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.connectivity.checker.databinding.DialogVmSettingsBinding
import com.connectivity.checker.viewmodel.MainViewModel
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

class VmSettingsDialog : BottomSheetDialogFragment() {

    private var _binding: DialogVmSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.behavior?.state = BottomSheetBehavior.STATE_EXPANDED
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = DialogVmSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val vm = viewModel
        binding.etVmUrl.setText(vm.vmUrl)
        binding.etVmUsername.setText(vm.vmUsername)
        binding.etVmPassword.setText(vm.vmPassword)

        refreshBufferCount()

        binding.btnTestConnection.setOnClickListener {
            saveSettings()
            lifecycleScope.launch {
                binding.tvConnectionStatus.text = "Testing…"
                val ok = vm.testVmConnection()
                binding.tvConnectionStatus.text = if (ok) "✓ Connected" else "✗ Connection failed"
            }
        }

        binding.btnClearBuffer.setOnClickListener {
            lifecycleScope.launch {
                vm.clearMetricBuffer()
                refreshBufferCount()
            }
        }

        binding.btnFlushBuffer.setOnClickListener {
            lifecycleScope.launch {
                binding.tvConnectionStatus.text = "Flushing…"
                val n = vm.flushMetrics()
                binding.tvConnectionStatus.text = "Flushed $n metric(s)"
                refreshBufferCount()
            }
        }

        binding.btnSaveSettings.setOnClickListener {
            saveSettings()
            dismiss()
        }

        binding.btnCancel.setOnClickListener { dismiss() }
    }

    private fun saveSettings() {
        viewModel.saveVmSettings(
            url      = binding.etVmUrl.text?.toString()?.trim() ?: "",
            username = binding.etVmUsername.text?.toString()?.trim() ?: "",
            password = binding.etVmPassword.text?.toString() ?: ""
        )
    }

    private fun refreshBufferCount() {
        lifecycleScope.launch {
            val n = viewModel.pendingMetricCount()
            binding.tvBufferStatus.text = "Buffered: $n metric point(s)"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
