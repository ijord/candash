package dev.nmullaney.tesladashboard

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import dagger.hilt.android.AndroidEntryPoint

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
@AndroidEntryPoint
class FullscreenActivity : AppCompatActivity() {

    private lateinit var viewModel: DashViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_fullscreen)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.hide(WindowInsets.Type.statusBars())
        } else {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
        }

        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION xor View.SYSTEM_UI_FLAG_FULLSCREEN

        supportFragmentManager
            .beginTransaction()
            .add(R.id.fragment_container, InfoFragment())
            .commit()

        viewModel = ViewModelProvider(this).get(DashViewModel::class.java)
        viewModel.fragmentNameToShow().observe(this, {
            when(it) {
                "dash" -> switchToFragment(DashFragment())
                "info" -> switchToFragment(InfoFragment())
                else -> throw IllegalStateException("Attempting to switch to unknown fragment: $it")
        }})
    }

    private fun switchToFragment(fragment: Fragment) {
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION xor View.SYSTEM_UI_FLAG_FULLSCREEN
    }

}