package com.example.streamingappzb.ui.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.viewbinding.ViewBinding
import com.example.streamingappzb.ui.widget.MhToastHost

/**
 * Shared Activity plumbing.
 *
 * Every screen is edge to edge with transparent system bars and light bar icons **off**
 * (PRD §8) — the app is true black, so dark icons would vanish.
 *
 * View binding is generic rather than per-Activity boilerplate: subclasses supply
 * [inflateBinding] and use [binding].
 */
abstract class BaseActivity<VB : ViewBinding> : AppCompatActivity() {

    protected lateinit var binding: VB
        private set

    private var toastHost: MhToastHost? = null

    protected abstract fun inflateBinding(inflater: LayoutInflater): VB

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = inflateBinding(layoutInflater)
        setContentView(binding.root)

        WindowCompat.getInsetsController(window, binding.root).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        onViewReady(savedInstanceState)
    }

    protected open fun onViewReady(savedInstanceState: Bundle?) = Unit

    /**
     * The in-app toast from the design: a centred pill that shows for 2.6 s and replaces
     * whatever was already on screen. Not [android.widget.Toast], which cannot be
     * positioned or restyled.
     */
    fun showToast(message: CharSequence, bottomMarginPx: Int = 0) {
        val root = binding.root as? ViewGroup ?: return
        val host = toastHost ?: MhToastHost(root).also { toastHost = it }
        host.show(message, bottomMarginPx)
    }

    fun showToast(@StringRes messageRes: Int, bottomMarginPx: Int = 0) =
        showToast(getString(messageRes), bottomMarginPx)

    override fun onDestroy() {
        toastHost?.dismiss()
        toastHost = null
        super.onDestroy()
    }
}
