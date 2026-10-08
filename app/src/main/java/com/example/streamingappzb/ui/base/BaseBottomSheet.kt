package com.example.streamingappzb.ui.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.viewbinding.ViewBinding
import com.example.streamingappzb.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Shared sheet plumbing (PRD §5 `MhSheet`): `night800`, 22dp top corners, a 60% black
 * scrim, and the [R.style.ThemeOverlay_Mh_BottomSheet] overlay that supplies all three.
 */
abstract class BaseBottomSheet<VB : ViewBinding> : BottomSheetDialogFragment() {

    private var _binding: VB? = null

    protected val binding: VB get() = requireNotNull(_binding) { "binding accessed outside the view lifecycle" }

    protected abstract fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): VB

    override fun getTheme(): Int = R.style.ThemeOverlay_Mh_BottomSheet

    final override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = inflateBinding(inflater, container)
        return binding.root
    }

    final override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        onViewReady(savedInstanceState)
    }

    protected open fun onViewReady(savedInstanceState: Bundle?) = Unit

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    fun showToast(@StringRes messageRes: Int) {
        (activity as? BaseActivity<*>)?.showToast(messageRes)
    }

    fun showToast(message: CharSequence) {
        (activity as? BaseActivity<*>)?.showToast(message)
    }
}
