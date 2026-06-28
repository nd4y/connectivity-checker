package com.connectivity.checker.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import com.connectivity.checker.databinding.FragmentEditCheckBinding
import com.connectivity.checker.model.CheckConfig
import com.connectivity.checker.model.CheckType
import com.connectivity.checker.viewmodel.MainViewModel
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip

class EditCheckBottomSheet : BottomSheetDialogFragment() {

    private var _binding: FragmentEditCheckBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()
    private var editIndex: Int = -1

    companion object {
        private const val ARG_INDEX = "edit_index"

        fun newAdd() = EditCheckBottomSheet()

        fun newEdit(index: Int, config: CheckConfig) = EditCheckBottomSheet().apply {
            arguments = Bundle().apply {
                putInt(ARG_INDEX, index)
                putString("name", config.name)
                putString("type", config.type.name)
                putString("host", config.host ?: "")
                putInt("port", config.port ?: 0)
                putString("url", config.url ?: "")
                putString("method", config.method)
                putInt("timeout", config.timeout)
                putInt("interval", config.interval)
                putInt("expected_code", config.expectedCode ?: 0)
                putString("dns_server", config.dnsServer ?: "")
                putString("sni", config.sni ?: "")
                putString("body", config.body ?: "")
                val headerLines = config.headers.entries.joinToString("\n") { "${it.key}: ${it.value}" }
                putString("headers", headerLines)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.behavior?.state = BottomSheetBehavior.STATE_EXPANDED
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentEditCheckBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        editIndex = arguments?.getInt(ARG_INDEX, -1) ?: -1

        populateFromArgs()
        setupTypeChips()
        setupButtons()
    }

    private fun populateFromArgs() {
        val args = arguments ?: return
        binding.etName.setText(args.getString("name", ""))
        binding.etTimeout.setText(args.getInt("timeout", 5000).toString())
        binding.etInterval.setText(args.getInt("interval", 0).toString())

        val typeName = args.getString("type", CheckType.ICMP.name)
        val type = runCatching { CheckType.valueOf(typeName!!) }.getOrDefault(CheckType.ICMP)
        selectType(type)

        binding.etHost.setText(args.getString("host", ""))
        val port = args.getInt("port", 0)
        if (port > 0) binding.etPort.setText(port.toString())
        binding.etUrl.setText(args.getString("url", ""))
        setupMethodDropdown(args.getString("method", "GET") ?: "GET")
        val code = args.getInt("expected_code", 0)
        if (code > 0) binding.etExpectedCode.setText(code.toString())
        binding.etDnsServer.setText(args.getString("dns_server", ""))
        binding.etSni.setText(args.getString("sni", ""))
        binding.etBody.setText(args.getString("body", ""))
        binding.etHeaders.setText(args.getString("headers", ""))

        binding.btnDelete.isVisible = editIndex >= 0
        binding.tvTitle.text = if (editIndex >= 0) "Edit check" else "Add check"
    }

    private fun setupTypeChips() {
        val chips = mapOf(
            CheckType.ICMP to binding.chipIcmp,
            CheckType.DNS  to binding.chipDns,
            CheckType.HTTP to binding.chipHttp,
            CheckType.TCP  to binding.chipTcp,
            CheckType.UDP  to binding.chipUdp,
            CheckType.TLS  to binding.chipTls
        )
        chips.forEach { (type, chip) ->
            chip.setOnClickListener { selectType(type) }
        }
    }

    private fun selectType(type: CheckType) {
        val chips = mapOf(
            CheckType.ICMP to binding.chipIcmp,
            CheckType.DNS  to binding.chipDns,
            CheckType.HTTP to binding.chipHttp,
            CheckType.TCP  to binding.chipTcp,
            CheckType.UDP  to binding.chipUdp,
            CheckType.TLS  to binding.chipTls
        )
        chips.values.forEach { it.isChecked = false }
        chips[type]?.isChecked = true
        updateFieldVisibility(type)
    }

    private fun updateFieldVisibility(type: CheckType) {
        val hasHost = type != CheckType.HTTP
        val hasPort = type == CheckType.TCP || type == CheckType.UDP || type == CheckType.TLS
        val isHttp  = type == CheckType.HTTP
        val isDns   = type == CheckType.DNS
        val isTls   = type == CheckType.TLS

        binding.tilHost.isVisible        = hasHost
        binding.tilPort.isVisible        = hasPort
        binding.tilUrl.isVisible         = isHttp
        binding.tilMethod.isVisible      = isHttp
        binding.tilExpectedCode.isVisible = isHttp
        binding.tilBody.isVisible        = isHttp
        binding.tilHeaders.isVisible     = isHttp
        binding.tilDnsServer.isVisible   = isDns
        binding.tilSni.isVisible         = isTls
    }

    private fun setupMethodDropdown(current: String) {
        val methods = listOf("GET", "HEAD", "POST")
        val adapter = android.widget.ArrayAdapter(
            requireContext(), android.R.layout.simple_dropdown_item_1line, methods
        )
        binding.actvMethod.setAdapter(adapter)
        binding.actvMethod.setText(current, false)
    }

    private fun setupButtons() {
        binding.btnCancel.setOnClickListener { dismiss() }
        binding.btnDelete.setOnClickListener {
            if (editIndex >= 0) viewModel.deleteCheck(editIndex)
            dismiss()
        }
        binding.btnSave.setOnClickListener { saveCheck() }
    }

    private fun saveCheck() {
        val name = binding.etName.text?.toString()?.trim()
        if (name.isNullOrBlank()) {
            binding.tilName.error = "Name is required"
            return
        }
        binding.tilName.error = null

        val type = currentSelectedType() ?: CheckType.ICMP
        val timeout  = binding.etTimeout.text?.toString()?.toIntOrNull() ?: 5000
        val interval = binding.etInterval.text?.toString()?.toIntOrNull() ?: 0
        val host     = binding.etHost.text?.toString()?.trim()?.ifBlank { null }
        val port     = binding.etPort.text?.toString()?.toIntOrNull()
        val url      = binding.etUrl.text?.toString()?.trim()?.ifBlank { null }
        val method   = binding.actvMethod.text?.toString()?.uppercase()?.ifBlank { "GET" } ?: "GET"
        val expected = binding.etExpectedCode.text?.toString()?.toIntOrNull()
        val dns      = binding.etDnsServer.text?.toString()?.trim()?.ifBlank { null }
        val sni      = binding.etSni.text?.toString()?.trim()?.ifBlank { null }
        val body     = binding.etBody.text?.toString()?.trim()?.ifBlank { null }
        val headers  = parseHeaders(binding.etHeaders.text?.toString() ?: "")

        val config = CheckConfig(
            name = name, type = type, host = host, port = port, url = url,
            method = method, timeout = timeout, interval = interval,
            expectedCode = expected, dnsServer = dns, sni = sni,
            body = body, headers = headers
        )

        if (editIndex >= 0) viewModel.updateCheck(editIndex, config) else viewModel.addCheck(config)
        dismiss()
    }

    private fun currentSelectedType(): CheckType? = when {
        binding.chipIcmp.isChecked -> CheckType.ICMP
        binding.chipDns.isChecked  -> CheckType.DNS
        binding.chipHttp.isChecked -> CheckType.HTTP
        binding.chipTcp.isChecked  -> CheckType.TCP
        binding.chipUdp.isChecked  -> CheckType.UDP
        binding.chipTls.isChecked  -> CheckType.TLS
        else -> null
    }

    private fun parseHeaders(raw: String): Map<String, String> =
        raw.lines()
            .mapNotNull { line ->
                val idx = line.indexOf(':')
                if (idx > 0) line.substring(0, idx).trim() to line.substring(idx + 1).trim()
                else null
            }
            .toMap()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
