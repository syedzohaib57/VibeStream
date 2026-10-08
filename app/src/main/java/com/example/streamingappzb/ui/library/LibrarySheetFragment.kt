package com.example.streamingappzb.ui.library

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.streamingappzb.R
import com.example.streamingappzb.data.media.JellyfinConfig
import com.example.streamingappzb.data.media.LocalLibrary
import com.example.streamingappzb.data.prefs.AppPrefs
import com.example.streamingappzb.databinding.SheetRowsBinding
import com.example.streamingappzb.ui.base.BaseBottomSheet
import com.example.streamingappzb.ui.base.color
import com.example.streamingappzb.ui.base.dimen
import com.example.streamingappzb.ui.widget.MhSheetRow
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Where the viewer points the app at their own media.
 *
 * The folders are chosen through the system picker rather than typed or discovered: that
 * is the only way to get a durable read grant for storage this app does not own, and it
 * means no storage permission is ever requested — the grant covers exactly the folders
 * handed over and nothing else.
 */
class LibrarySheetFragment : BaseBottomSheet<SheetRowsBinding>() {

    private val prefs: AppPrefs by inject()
    private val library: LocalLibrary by inject()

    /**
     * Registered unconditionally at construction, as the contract requires — a launcher
     * created lazily inside a click handler throws, because registration has to happen
     * before the fragment reaches STARTED.
     */
    private val pickFolder = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> uri?.let(::addFolder) }

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): SheetRowsBinding =
        SheetRowsBinding.inflate(inflater, container, false)

    override fun onViewReady(savedInstanceState: Bundle?) {
        binding.sheetTitle.setText(R.string.library_sheet_title)
        binding.divider.isVisible = true
        render()
    }

    /**
     * Takes a *persistable* grant before the URI is stored.
     *
     * The grant that arrives with the picker's result dies with the process. Storing the
     * URI without persisting the permission produces a library that works until the app is
     * killed and then throws SecurityException on every scan — which presents as the
     * folder having silently emptied itself.
     */
    private fun addFolder(uri: Uri) {
        val stored = prefs.libraryFolders
        if (uri.toString() in stored) {
            showToast(R.string.library_folder_already_added)
            return
        }

        val taken = runCatching {
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }.isSuccess

        if (!taken) {
            showToast(R.string.library_folder_failed)
            return
        }

        prefs.libraryFolders = stored + uri.toString()
        library.invalidate()
        showToast(R.string.library_folder_added)
        render()
    }

    /**
     * Releases the grant as well as forgetting the URI.
     *
     * Dropping only the preference would leave the app holding a permission it no longer
     * uses, which the system lists under the app's storage access indefinitely. Removing a
     * folder should mean the access is gone, not just unused.
     */
    private fun removeFolder(treeUri: String) {
        runCatching {
            requireContext().contentResolver.releasePersistableUriPermission(
                Uri.parse(treeUri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }

        prefs.libraryFolders = prefs.libraryFolders - treeUri
        library.invalidate()
        showToast(R.string.library_folder_removed)
        render()
    }

    private fun render() {
        val folders = prefs.libraryFolders.sorted()

        binding.header.removeAllViews()
        binding.rows.removeAllViews()
        binding.footer.removeAllViews()

        binding.rows.addView(
            MhSheetRow(requireContext()).apply {
                setIcon(R.drawable.ic_add)
                setLabel(R.string.library_add_folder)
                setSub(R.string.library_add_folder_sub)
                setOnClickListener {
                    // No initial URI: the picker then opens wherever the viewer last
                    // browsed, which is the folder next to the one they just added far
                    // more often than it is the root of internal storage.
                    runCatching { pickFolder.launch(null) }
                        .onFailure { showToast(R.string.error_generic) }
                }
            },
        )

        folders.forEach { treeUri ->
            val name = library.folderName(treeUri)
            binding.rows.addView(
                MhSheetRow(requireContext()).apply {
                    setIcon(null)
                    setLabel(name)
                    setSub(R.string.library_remove_folder_sub)
                    setTrailingView(removeGlyph(name))
                    contentDescription = getString(R.string.cd_remove_folder, name)
                    setOnClickListener { removeFolder(treeUri) }
                },
            )
        }

        // Only offered once there is something to rescan. A refresh row above an empty
        // library is a control with nothing to act on.
        if (folders.isNotEmpty()) {
            binding.rows.addView(
                MhSheetRow(requireContext()).apply {
                    setIcon(R.drawable.ic_refresh)
                    setLabel(R.string.library_rescan)
                    setSub(R.string.library_rescan_sub)
                    setOnClickListener {
                        library.invalidate()
                        // Re-render rather than just rescanning: the footer is what reports
                        // the new count, and it is the only confirmation the viewer gets
                        // that the file they just copied across was actually seen.
                        render()
                    }
                },
            )
        }

        // The media server. One row whether connected or not — the sub-line and the tap
        // carry the state, mirroring how the folder rows read.
        binding.rows.addView(
            MhSheetRow(requireContext()).apply {
                setIcon(R.drawable.ic_cast)
                setLabel(R.string.jellyfin_row_label)
                val url = prefs.jellyfinUrl
                val token = prefs.jellyfinToken
                if (url.isNullOrBlank() || token.isNullOrBlank()) {
                    setSub(R.string.jellyfin_row_not_connected)
                    setOnClickListener { openConnectSheet() }
                } else {
                    val host = JellyfinConfig(url, token).displayHost
                    setSub(
                        getString(
                            R.string.jellyfin_row_connected,
                            prefs.jellyfinUserName ?: "",
                            host,
                        ).trim(' ', '·'),
                    )
                    setTrailingView(removeGlyph(host))
                    setOnClickListener { disconnectServer() }
                }
            },
        )

        binding.rows.addView(
            MhSheetRow(requireContext()).apply {
                setIcon(R.drawable.ic_search)
                setLabel(R.string.library_open_archive)
                setSub(R.string.library_open_archive_sub)
                withToggle(prefs.openArchiveSearch) { prefs.openArchiveSearch = it }
            },
        )

        binding.rows.addView(
            MhSheetRow(requireContext()).apply {
                setIcon(R.drawable.ic_info)
                setLabel(R.string.library_sample_fallback)
                setSub(R.string.library_sample_fallback_sub)
                withToggle(prefs.sampleFallback) { prefs.sampleFallback = it }
            },
        )

        renderFooter(folders)
    }

    /**
     * The footer carries the file count, which is the only honest confirmation that adding
     * a folder worked: a folder row proves a grant was taken, not that anything in it is
     * playable.
     */
    private fun renderFooter(folders: List<String>) {
        if (folders.isEmpty()) {
            binding.footer.addView(caption(getString(R.string.library_empty_footer)))
            return
        }

        val counting = caption(getString(R.string.library_scanning))
        binding.footer.addView(counting)

        viewLifecycleOwner.lifecycleScope.launch {
            val count = runCatching { library.videos().size }.getOrDefault(0)
            // The sheet can be dismissed mid-scan; the binding is gone by then and the
            // count has nowhere to go.
            if (!isAdded || view == null) return@launch
            counting.text = getString(
                R.string.library_indexed_footer,
                count,
                folderCount(folders.size),
            )
        }
    }

    private fun folderCount(size: Int): String = if (size == 1) {
        getString(R.string.library_one_folder)
    } else {
        getString(R.string.library_folders_count, size)
    }

    private fun openConnectSheet() {
        JellyfinConnectSheet()
            .apply { onConnected = { if (isAdded) render() } }
            .show(parentFragmentManager, JellyfinConnectSheet.TAG)
    }

    /**
     * Disconnecting clears the stored token as well as the address: a token left behind
     * would keep authenticating stream URLs for a server the viewer said to forget.
     */
    private fun disconnectServer() {
        prefs.jellyfinUrl = null
        prefs.jellyfinToken = null
        prefs.jellyfinUserName = null
        showToast(R.string.jellyfin_disconnected_toast)
        render()
    }

    private fun removeGlyph(name: String): ImageView = ImageView(requireContext()).apply {
        setImageResource(R.drawable.ic_delete)
        layoutParams = LinearLayout.LayoutParams(
            context.dimen(R.dimen.icon_md),
            context.dimen(R.dimen.icon_md),
        )
        imageTintList = android.content.res.ColorStateList.valueOf(context.color(R.color.text_low))
        // The row is the touch target and carries the description; a focusable glyph would
        // be a second TalkBack stop announcing nothing.
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        contentDescription = getString(R.string.cd_remove_folder, name)
    }

    private fun caption(text: CharSequence): TextView = TextView(requireContext()).apply {
        setTextAppearance(R.style.TextAppearance_Mh_Caption)
        setTextColor(context.color(R.color.text_low))
        this.text = text
        setPadding(0, context.dimen(R.dimen.gap_button_tight), 0, 0)
    }

    companion object {
        const val TAG = "library-sheet"
    }
}
