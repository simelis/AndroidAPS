package app.aaps.core.ui.compose.preference.navigable

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.aaps.core.ui.compose.preference.rememberPreferenceSectionState
import app.aaps.core.ui.compose.preference.verticalScrollIndicators

/**
 * Composable that hosts preference navigation with subscreens.
 * Displays a list of subscreens and navigates to them when clicked.
 *
 * Legacy pattern for NavigablePreferenceContent interface.
 *
 * @param content The navigable preference content
 * @param title Title for the main screen
 * @param onBackClick Callback when back button is clicked on main screen
 * @deprecated Legacy pattern - new code should use PreferenceSubScreenRenderer instead
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferenceNavigationHost(
    content: NavigablePreferenceContent,
    title: String,
    onBackClick: () -> Unit
) {
    var currentSubScreen by rememberSaveable { mutableStateOf<String?>(null) }
    val sectionState = rememberPreferenceSectionState()

    val selectedSubScreen = content.subscreens.find { it.key == currentSubScreen }

    AnimatedContent(
        targetState = selectedSubScreen,
        transitionSpec = {
            if (targetState != null) {
                // Navigating to subscreen - slide in from right
                slideInHorizontally { width -> width } togetherWith
                    slideOutHorizontally { width -> -width }
            } else {
                // Navigating back - slide in from left
                slideInHorizontally { width -> -width } togetherWith
                    slideOutHorizontally { width -> width }
            }
        },
        label = "PreferenceNavigation"
    ) { subScreen ->
        if (subScreen != null) {
            // Show subscreen
            PreferenceSubScreenScaffold(
                titleResId = subScreen.titleResId,
                onBackClick = { currentSubScreen = null }
            ) {
                val listState = rememberLazyListState()
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScrollIndicators(listState),
                    state = listState
                ) {
                    item {
                        subScreen.content(sectionState)
                    }
                }
            }
        } else {
            // Show main screen with main content + subscreen list
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onBackClick) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(app.aaps.core.ui.R.string.back)
                                )
                            }
                        }
                    )
                }
            ) { paddingValues ->
                val listState = rememberLazyListState()
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScrollIndicators(listState),
                    state = listState
                ) {
                    // Show main content first (if any)
                    content.mainContent?.let { mainContent ->
                        item(key = "${content.keyPrefix}_main") {
                            mainContent(sectionState)
                        }
                    }
                    // Then show subscreen navigation items
                    content.subscreens.forEach { subScreen ->
                        item(key = subScreen.key) {
                            NavigablePreferenceItem(
                                titleResId = subScreen.titleResId,
                                summaryResId = subScreen.summaryResId,
                                summaryItems = subScreen.effectiveSummaryItems(),
                                onClick = { currentSubScreen = subScreen.key }
                            )
                        }
                    }
                }
            }
        }
    }
}
