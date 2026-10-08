package com.example.streamingappzb.ui.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding

/**
 * Shared Fragment plumbing: generic view binding that is released in [onDestroyView], so
 * a detached view can never be leaked by a pending callback.
 */
abstract class BaseFragment<VB : ViewBinding> : Fragment() {

    private var _binding: VB? = null

    /** Valid between [onCreateView] and [onDestroyView]. */
    protected val binding: VB get() = requireNotNull(_binding) { "binding accessed outside the view lifecycle" }

    protected val bindingOrNull: VB? get() = _binding

    protected abstract fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): VB

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

    fun showToast(message: CharSequence) {
        (activity as? BaseActivity<*>)?.showToast(message)
    }

    fun showToast(@StringRes messageRes: Int) {
        (activity as? BaseActivity<*>)?.showToast(messageRes)
    }
}
