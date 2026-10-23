package care.cat.offline

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

class MainActivity : ComponentActivity() {
    private val model: CareViewModel by viewModels {
        viewModelFactory {
            initializer {
                CareViewModel(application, createSavedStateHandle(), CareStore(application))
            }
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLanguage.context(base))
    }

    override fun onResume() {
        super.onResume()
        model.retry()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        model.navigate("today")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CatCareApp(model) }
    }
}
