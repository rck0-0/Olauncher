package app.olauncher.ui

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.R
import app.olauncher.data.AppBlockerPrefs
import app.olauncher.data.AppModel
import app.olauncher.data.BlockedApp
import app.olauncher.data.Prefs
import app.olauncher.helper.getAppsList
import app.olauncher.helper.isAccessServiceEnabled
import app.olauncher.helper.showToast
import kotlinx.coroutines.launch
import java.util.Locale

class AppBlockerFragment : Fragment() {

    private lateinit var prefs: AppBlockerPrefs
    private lateinit var rvBlockedApps: RecyclerView
    private lateinit var btnAddBlockedApp: Button
    private lateinit var tvInstruction: TextView
    private lateinit var adapter: BlockedAppsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_app_blocker, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = AppBlockerPrefs(requireContext())
        rvBlockedApps = view.findViewById(R.id.rvBlockedApps)
        btnAddBlockedApp = view.findViewById(R.id.btnAddBlockedApp)
        tvInstruction = view.findViewById(R.id.tvInstruction)

        setupRecyclerView()
        checkAccessibilityService()

        btnAddBlockedApp.setOnClickListener {
            showAppSelectionDialog()
        }
    }

    private fun checkAccessibilityService() {
        if (!isAccessServiceEnabled(requireContext())) {
            tvInstruction.text = "IMPORTANT: You must enable Accessibility Service for blocking to work!\n\nDefault blocking time: 09:00 - 17:00."
            tvInstruction.setTextColor(resources.getColor(android.R.color.holo_red_light, null))
        }
    }

    private fun setupRecyclerView() {
        adapter = BlockedAppsAdapter(prefs.blockedApps.toMutableList(),
            onItemClick = { app -> showEditDialog(app) },
            onDeleteClick = { app ->
                prefs.removeBlockedApp(app.packageName)
                refreshList()
            },
            onToggleClick = { app, isEnabled ->
                val updatedApp = app.copy(isEnabled = isEnabled)
                prefs.addOrUpdateBlockedApp(updatedApp)
                // No need to refresh list fully, but good for consistency
            }
        )
        rvBlockedApps.layoutManager = LinearLayoutManager(requireContext())
        rvBlockedApps.adapter = adapter
    }

    private fun refreshList() {
        adapter.updateList(prefs.blockedApps)
    }

    private fun showAppSelectionDialog() {
        lifecycleScope.launch {
            val apps = getAppsList(requireContext(), Prefs(requireContext()))
            val appNames = apps.map { it.appLabel }.toTypedArray()

            AlertDialog.Builder(requireContext())
                .setTitle("Select App to Block")
                .setItems(appNames) { _, which ->
                    val selectedApp = apps[which]
                    showTimeSelectionDialog(selectedApp)
                }
                .show()
        }
    }

    private fun showTimeSelectionDialog(appModel: AppModel) {
        // Default 09:00 to 17:00
        var startTime = "09:00"
        var endTime = "17:00"

        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_time_range_picker, null)
        val btnStartTime = dialogView.findViewById<Button>(R.id.btnStartTime)
        val btnEndTime = dialogView.findViewById<Button>(R.id.btnEndTime)

        btnStartTime.text = "Start: $startTime"
        btnEndTime.text = "End: $endTime"

        btnStartTime.setOnClickListener {
            showTimePicker(startTime) { time ->
                startTime = time
                btnStartTime.text = "Start: $startTime"
            }
        }

        btnEndTime.setOnClickListener {
            showTimePicker(endTime) { time ->
                endTime = time
                btnEndTime.text = "End: $endTime"
            }
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Block ${appModel.appLabel}")
            .setView(dialogView)
            .setPositiveButton("Block") { _, _ ->
                val blockedApp = BlockedApp(
                    packageName = appModel.appPackage,
                    appName = appModel.appLabel,
                    startTime = startTime,
                    endTime = endTime,
                    isEnabled = true
                )
                prefs.addOrUpdateBlockedApp(blockedApp)
                refreshList()
                requireContext().showToast("${appModel.appLabel} blocked from $startTime to $endTime")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditDialog(blockedApp: BlockedApp) {
        var startTime = blockedApp.startTime
        var endTime = blockedApp.endTime

        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_time_range_picker, null)
        val btnStartTime = dialogView.findViewById<Button>(R.id.btnStartTime)
        val btnEndTime = dialogView.findViewById<Button>(R.id.btnEndTime)

        btnStartTime.text = "Start: $startTime"
        btnEndTime.text = "End: $endTime"

        btnStartTime.setOnClickListener {
            showTimePicker(startTime) { time ->
                startTime = time
                btnStartTime.text = "Start: $startTime"
            }
        }

        btnEndTime.setOnClickListener {
            showTimePicker(endTime) { time ->
                endTime = time
                btnEndTime.text = "End: $endTime"
            }
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Edit ${blockedApp.appName}")
            .setView(dialogView)
            .setPositiveButton("Update") { _, _ ->
                val updatedApp = blockedApp.copy(startTime = startTime, endTime = endTime)
                prefs.addOrUpdateBlockedApp(updatedApp)
                refreshList()
            }
            .setNeutralButton("Delete") { _, _ ->
                prefs.removeBlockedApp(blockedApp.packageName)
                refreshList()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showTimePicker(currentTime: String, onTimeSelected: (String) -> Unit) {
        val parts = currentTime.split(":")
        val hour = parts[0].toInt()
        val minute = parts[1].toInt()

        TimePickerDialog(requireContext(), { _, h, m ->
            val formattedTime = String.format(Locale.getDefault(), "%02d:%02d", h, m)
            onTimeSelected(formattedTime)
        }, hour, minute, true).show()
    }
}

class BlockedAppsAdapter(
    private var items: MutableList<BlockedApp>,
    private val onItemClick: (BlockedApp) -> Unit,
    private val onDeleteClick: (BlockedApp) -> Unit,
    private val onToggleClick: (BlockedApp, Boolean) -> Unit
) : RecyclerView.Adapter<BlockedAppsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvAppName: TextView = view.findViewById(R.id.tvAppName)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
        val switchEnabled: Switch = view.findViewById(R.id.switchEnabled)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_blocked_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvAppName.text = item.appName
        holder.tvTime.text = "${item.startTime} - ${item.endTime}"
        holder.switchEnabled.setOnCheckedChangeListener(null)
        holder.switchEnabled.isChecked = item.isEnabled

        holder.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            onToggleClick(item, isChecked)
        }

        holder.itemView.setOnClickListener { onItemClick(item) }

        // Long click to delete or separate delete button? Using long click for now or dialog
        holder.itemView.setOnLongClickListener {
            onDeleteClick(item)
            true
        }
    }

    override fun getItemCount() = items.size

    fun updateList(newItems: List<BlockedApp>) {
        items = newItems.toMutableList()
        notifyDataSetChanged()
    }
}
