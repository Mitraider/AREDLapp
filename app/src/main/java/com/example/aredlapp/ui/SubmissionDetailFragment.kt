package com.example.aredlapp.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.method.KeyListener
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.aredlapp.databinding.FragmentSubmissionDetailBinding
import com.example.aredlapp.models.SubmissionDetailResponse
import com.example.aredlapp.models.SubmissionScreenMode
import com.example.aredlapp.models.SubmissionDetailUiState
import com.example.aredlapp.models.SubmissionEditForm
import com.example.aredlapp.models.LevelResponse
import com.example.aredlapp.utils.ThemeUtils
import com.example.aredlapp.viewmodel.AredlViewModel
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class SubmissionDetailFragment : Fragment() {

    private var _binding: FragmentSubmissionDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AredlViewModel by activityViewModels()
    private var currentDetail: SubmissionDetailResponse? = null
    private var levelLabels: List<String> = emptyList()
    private var levelsByLabel: Map<String, LevelResponse> = emptyMap()
    private var levelKeyListener: KeyListener? = null
    private var lastRenderedMode: SubmissionScreenMode? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSubmissionDetailBinding.inflate(inflater, container, false)
        levelKeyListener = binding.editSubmissionLevel.keyListener
        return binding.root
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyColors()
        setupStaticDropdowns()
        binding.btnBackSubmission.setOnClickListener { findNavController().navigateUp() }
        binding.btnSaveSubmission.setOnClickListener {
            val mode = viewModel.selectedSubmissionDetail.value?.mode ?: SubmissionScreenMode.VIEW
            val deviceText = binding.editSubmissionDevice.text?.toString().orEmpty().trim()
            val mobile = when (deviceText) {
                DEVICE_PC -> false
                DEVICE_MOBILE -> true
                else -> {
                    Toast.makeText(requireContext(), "Device is required", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
            }
            val levelId = resolveSelectedLevelId()
            if (levelId == null) {
                Toast.makeText(requireContext(), "Level is required", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val form = SubmissionEditForm(
                levelId = levelId,
                mobile = mobile,
                ldmId = binding.editSubmissionLdmId.text?.toString().orEmpty(),
                videoUrl = binding.editSubmissionVideoUrl.text?.toString().orEmpty(),
                rawUrl = binding.editSubmissionRawUrl.text?.toString().orEmpty(),
                modMenu = binding.editSubmissionModMenu.text?.toString().orEmpty(),
                userNotes = binding.editSubmissionUserNotes.text?.toString().orEmpty()
            )
            viewLifecycleOwner.lifecycleScope.launch {
                val result = if (mode == SubmissionScreenMode.CREATE) {
                    viewModel.createSubmission(form)
                } else {
                    viewModel.updateSelectedSubmission(form)
                }
                result.onSuccess {
                    val message = if (mode == SubmissionScreenMode.CREATE) "Submission created" else "Submission updated"
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                }.onFailure { error ->
                    val fallback = if (mode == SubmissionScreenMode.CREATE) {
                        "Failed to create submission"
                    } else {
                        "Failed to update submission"
                    }
                    Toast.makeText(requireContext(), error.message ?: fallback, Toast.LENGTH_LONG).show()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selectedSubmissionDetail.collect { state ->
                render(state)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun render(state: SubmissionDetailUiState?) {
        val mode = state?.mode ?: SubmissionScreenMode.VIEW
        val detail = state?.detail
        lastRenderedMode = mode
        currentDetail = detail
        binding.submissionDetailScroll.isVisible = detail != null || mode == SubmissionScreenMode.CREATE
        binding.btnSaveSubmission.text = if (mode == SubmissionScreenMode.CREATE) "Submit" else "Update"

        if (detail == null && mode != SubmissionScreenMode.CREATE) {
            binding.textSubmissionDetailTitle.text = if (state?.isLoading == true) "Loading..." else "Submission"
            return
        }

        if (mode == SubmissionScreenMode.CREATE) {
            renderCreateState(state)
        } else {
            renderExistingSubmission(state, detail!!)
        }
        binding.textSubmissionGuidelines.text = GUIDELINES_TEXT
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun renderExistingSubmission(state: SubmissionDetailUiState?, detail: SubmissionDetailResponse) {
        binding.textSubmissionDetailTitle.text = detail.level.name
        updateLevelDropdown(detail.level)
        binding.editSubmissionLevel.setText(formatLevelLabel(detail.level.position, detail.level.name), false)
        binding.editSubmissionDevice.setText(if (detail.mobile) DEVICE_MOBILE else DEVICE_PC, false)
        binding.editSubmissionVideoUrl.setText(detail.videoUrl)
        updateModMenuDropdown(detail.modMenu)
        binding.editSubmissionModMenu.setText(detail.modMenu.orEmpty(), false)
        binding.editSubmissionLdmId.setText(detail.ldmId?.toString().orEmpty())
        binding.editSubmissionRawUrl.setText(detail.rawUrl.orEmpty())
        binding.editSubmissionUserNotes.setText(detail.userNotes.orEmpty())
        binding.textSubmissionTimestamps.isVisible = true
        binding.textSubmissionTimestamps.text =
            "Created: ${formatDate(detail.createdAt)}\nLast updated: ${formatDate(detail.updatedAt)}"

        binding.textSubmissionReviewerNotes.isVisible = !detail.reviewerNotes.isNullOrBlank()
        binding.textSubmissionReviewerNotes.text = if (!detail.reviewerNotes.isNullOrBlank()) {
            "Reviewer notes: ${detail.reviewerNotes}"
        } else {
            ""
        }

        val bannerColor = when (detail.displayStatus) {
            "Accepted" -> Color.parseColor("#1B8F3A")
            "Refused" -> Color.parseColor("#C62828")
            else -> Color.parseColor("#1565C0")
        }
        binding.cardSubmissionBanner.setCardBackgroundColor(bannerColor)
        binding.textSubmissionBannerStatus.text = detail.displayStatus
        binding.textSubmissionBannerQueue.text = buildQueueText(state, detail)
        binding.textSubmissionBannerWarning.text = buildWarningText(detail)

        val editable = !detail.locked && !detail.isActivelyReviewed
        binding.inputLevel.alpha = 0.72f
        binding.inputLevel.isEnabled = false
        binding.editSubmissionLevel.isEnabled = false
        binding.editSubmissionLevel.keyListener = null
        binding.editSubmissionLevel.isFocusable = false
        binding.editSubmissionLevel.isFocusableInTouchMode = false
        binding.inputDevice.isEnabled = editable
        binding.editSubmissionDevice.isEnabled = editable
        binding.editSubmissionVideoUrl.isEnabled = editable
        binding.inputModMenu.isEnabled = editable
        binding.editSubmissionModMenu.isEnabled = editable
        binding.editSubmissionLdmId.isEnabled = editable
        binding.editSubmissionRawUrl.isEnabled = editable
        binding.editSubmissionUserNotes.isEnabled = editable
        binding.btnSaveSubmission.isEnabled = editable
    }

    private fun renderCreateState(state: SubmissionDetailUiState?) {
        binding.textSubmissionDetailTitle.text = "New Submission"
        updateLevelDropdown(null)
        clearCreateForm()
        updateModMenuDropdown(null)
        binding.textSubmissionTimestamps.isVisible = false
        binding.textSubmissionReviewerNotes.isVisible = false
        binding.textSubmissionReviewerNotes.text = ""

        val queueOpen = state?.submissionsOpen == true
        val bannerColor = if (queueOpen) Color.parseColor("#1565C0") else Color.parseColor("#C62828")
        binding.cardSubmissionBanner.setCardBackgroundColor(bannerColor)
        binding.textSubmissionBannerStatus.text = if (queueOpen) "Queue Open" else "Queue Closed"
        binding.textSubmissionBannerQueue.text = buildCreateQueueText(state)
        binding.textSubmissionBannerWarning.text = if (queueOpen) {
            "Create a new submission with the fields below. The selected level cannot be changed after submission."
        } else {
            "The submission queue is currently closed. You cannot create a new submission right now."
        }

        binding.inputLevel.alpha = 1f
        binding.inputLevel.isEnabled = true
        binding.editSubmissionLevel.isEnabled = true
        binding.editSubmissionLevel.keyListener = levelKeyListener
        binding.editSubmissionLevel.isFocusable = true
        binding.editSubmissionLevel.isFocusableInTouchMode = true
        binding.editSubmissionLevel.threshold = 1
        binding.inputDevice.isEnabled = queueOpen
        binding.editSubmissionDevice.isEnabled = queueOpen
        binding.editSubmissionVideoUrl.isEnabled = queueOpen
        binding.inputModMenu.isEnabled = queueOpen
        binding.editSubmissionModMenu.isEnabled = queueOpen
        binding.editSubmissionLdmId.isEnabled = queueOpen
        binding.editSubmissionRawUrl.isEnabled = queueOpen
        binding.editSubmissionUserNotes.isEnabled = queueOpen
        binding.btnSaveSubmission.isEnabled = queueOpen
    }

    private fun clearCreateForm() {
        binding.editSubmissionLevel.setText("", false)
        binding.editSubmissionDevice.setText(DEVICE_PLACEHOLDER, false)
        binding.editSubmissionVideoUrl.setText("")
        binding.editSubmissionModMenu.setText(DEFAULT_MOD_MENU_OPTIONS.first(), false)
        binding.editSubmissionLdmId.setText("")
        binding.editSubmissionRawUrl.setText("")
        binding.editSubmissionUserNotes.setText("")
    }

    private fun setupStaticDropdowns() {
        binding.editSubmissionDevice.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, listOf(DEVICE_PLACEHOLDER, DEVICE_PC, DEVICE_MOBILE))
        )
        binding.editSubmissionDevice.keyListener = null
        binding.editSubmissionModMenu.keyListener = null
    }

    private fun updateLevelDropdown(currentLevel: LevelResponse?) {
        val submittedLevelIds = viewModel.submissionInfoByLevel.value.keys
        val levelEntries = viewModel.levels.value
            .filter { currentLevel != null || it.id !in submittedLevelIds }
            .sortedBy { it.position }
            .map { formatLevelLabel(it.position, it.name) to it }
            .distinctBy { it.first }
            .toMutableList()

        currentLevel?.let {
            val currentLabel = formatLevelLabel(it.position, it.name)
            if (levelEntries.none { entry -> entry.first == currentLabel }) {
                levelEntries.add(0, currentLabel to it)
            }
        }

        val labels = levelEntries.map { it.first }
        if (labels == levelLabels) return
        levelLabels = labels
        levelsByLabel = levelEntries.associate { it.first to it.second }
        binding.editSubmissionLevel.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels)
        )
    }

    private fun updateModMenuDropdown(currentValue: String?) {
        val options = DEFAULT_MOD_MENU_OPTIONS.toMutableList()
        val trimmedCurrent = currentValue?.trim().orEmpty()
        if (trimmedCurrent.isNotEmpty() && trimmedCurrent !in options) {
            options.add(0, trimmedCurrent)
        }
        binding.editSubmissionModMenu.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, options)
        )
    }

    private fun formatLevelLabel(position: Int, name: String): String = "#$position - $name"

    private fun resolveSelectedLevelId(): String? {
        val selectedLabel = binding.editSubmissionLevel.text?.toString().orEmpty().trim()
        if (selectedLabel.isBlank()) return currentDetail?.level?.id
        return levelsByLabel[selectedLabel]?.id ?: currentDetail?.level?.id
    }

    private fun buildCreateQueueText(state: SubmissionDetailUiState?): String {
        val queueSummary = state?.queueSummary
        return if (queueSummary != null) {
            val overallTotal = queueSummary.regularSubmissionsInQueue + queueSummary.prioritySubmissionsInQueue
            "Total currently pending submissions: $overallTotal\n" +
                "Total priority submissions: ${queueSummary.prioritySubmissionsInQueue}"
        } else {
            when (state?.submissionsOpen) {
                true -> "You can submit a new record right now."
                false -> "The queue is currently closed."
                else -> "Checking queue status..."
            }
        }
    }

    private fun buildQueueText(state: SubmissionDetailUiState?, detail: SubmissionDetailResponse): String {
        if (detail.isActivelyReviewed) {
            return "This submission is currently being reviewed and cannot be edited."
        }
        val queuePosition = state?.queuePosition
        val queueSummary = state?.queueSummary
        return if (queuePosition != null && queueSummary != null) {
            val queueLabel = if (queuePosition.priority) "priority queue" else "regular queue"
            val overallTotal = queueSummary.regularSubmissionsInQueue + queueSummary.prioritySubmissionsInQueue
            "Position in $queueLabel: ${queuePosition.position}\n" +
                "Total currently pending submissions: $overallTotal\n" +
                "Total priority submissions: ${queueSummary.prioritySubmissionsInQueue}"
        } else {
            when (detail.displayStatus) {
                "Accepted" -> "This submission has already been accepted."
                "Refused" -> "This submission was refused and can be updated for review again."
                else -> "Queue position unavailable right now."
            }
        }
    }

    private fun buildWarningText(detail: SubmissionDetailResponse): String {
        return when {
            detail.locked -> "This submission is locked and cannot be edited."
            detail.isActivelyReviewed -> "This submission is actively under review. Editing is disabled until review ends."
            detail.displayStatus == "Accepted" -> "If you modify this accepted submission, it will go back through the review process."
            detail.displayStatus == "Refused" -> "If you modify this refused submission, it will be sent back for review as pending."
            else -> "Pending submissions can be updated without losing their place in queue."
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun formatDate(value: String?): String {
        if (value.isNullOrBlank()) return "-"
        return try {
            val parsed = OffsetDateTime.parse(value)
            parsed.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(Locale.getDefault()))
        } catch (_: Exception) {
            value
        }
    }

    private fun applyColors() {
        val color = ThemeUtils.getSecondaryColor(requireContext())
        binding.btnBackSubmission.imageTintList = ColorStateList.valueOf(color)
        binding.btnSaveSubmission.backgroundTintList = ColorStateList.valueOf(color)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val DEVICE_PC = "PC"
        private const val DEVICE_MOBILE = "Mobile"
        private const val DEVICE_PLACEHOLDER = "Select a device below"
        private val DEFAULT_MOD_MENU_OPTIONS = listOf(
            "None",
            "Mega Hack v9",
            "Mega Hack v8",
            "Mega Hack v7",
            "Mega Hack v6",
            "QOLMod",
            "Eclipse",
            "iCreate",
            "Prism Menu",
            "GDHM",
            "GDH",
            "Other (specify in notes)"
        )
        private const val GUIDELINES_TEXT =
            "- Your completion video and raw footage must have all clicks fully audible throughout the entire completion.\n" +
                "- You must record and upload a video of your completion to YouTube or another video sharing platform.\n" +
                "- Completion videos may not be deleted after they are accepted.\n" +
                "- If your video was uploaded after July 2nd, 2023, your record must have all cheat indicators visible on the level's end screen.\n" +
                "- Your record must show the stats on the endscreen (attempts, orbs, etc.).\n" +
                "- If your record is in the top 400, you must have raw footage, with isolated clicks, uploaded in a downloadable format (e.g. Google Drive) and submitted along with your public video.\n" +
                "- Your record may not use any disallowed mods.\n" +
                "- If you are using a custom copy of a level, it must either be approved by list staff, or not significantly affect the level's gameplay or difficulty without a doubt.\n" +
                "- During your completion, you may not use any skips that make any section of the level significantly easier.\n" +
                "- Globed 2P Completions are allowed, however, you need to include both POVs in the video or provide a link to watch the other POV, and the completion must be on an approved Globed 2P copy."
    }
}
