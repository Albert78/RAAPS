package de.dh.raaps

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import de.dh.raaps.common.navigation.DashboardRoute
import de.dh.raaps.common.navigation.NavigationViewModel
import de.dh.raaps.common.navigation.combineEntryProviders
import de.dh.raaps.common.ui.composables.EdgeToEdgeHandler
import de.dh.raaps.common.ui.theme.AppTheme
import de.dh.raaps.common.ui.theme.rememberUseDarkTheme
import de.dh.raaps.services.ApsService
import de.dh.raaps.ui.navigation.MainFeatureNavGraph

class MainActivity : ComponentActivity() {
    private lateinit var navViewModel: NavigationViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Start the background service
        startForegroundService(Intent(this, ApsService::class.java))

        navViewModel = ViewModelProvider(
            this,
            NavigationViewModel.Companion.NavigationViewModelFactory(listOf(DashboardRoute))
        )[NavigationViewModel::class.java]

        handleIntent(intent)

        val application = application as MainApplication

        setContent {
            val useDarkTheme = rememberUseDarkTheme(application.appPreferencesRepository)
            EdgeToEdgeHandler(useDarkTheme)
            AppTheme(darkTheme = useDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainApp()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            val data = intent.data
            if (data?.scheme == "app" && data.host == "raaps.dh.de") {
                if (data.path == "/dashboard") {
                    navViewModel.reset(listOf(DashboardRoute))
                }
            }
        }
    }

    @Composable
    fun MainApp() {
        val backStack by navViewModel.backstack.collectAsState()

        val extraGraphs = getExtraNavGraphs(this, navViewModel)

        val extraDashboardContent = @Composable {
            extraGraphs.forEach { it.DashboardExtension() }
        }

        val mainGraph = MainFeatureNavGraph(this, navViewModel, extraDashboardContent)
        val allGraphs = listOf(mainGraph) + extraGraphs

        val combinedProvider = combineEntryProviders(*allGraphs.toTypedArray())

        NavDisplay(
            backStack = backStack,
            onBack = { navViewModel.pop() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator()
            ),
            entryProvider = combinedProvider
        )
    }

    companion object {
        fun createStartDashboardIntent(context: Context): Intent {
            return Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse("app://raaps.dh.de/dashboard")
            }
        }
    }
}