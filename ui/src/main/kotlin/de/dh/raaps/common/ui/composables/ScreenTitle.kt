package de.dh.raaps.common.ui.composables

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.dh.raaps.common.R
import de.dh.raaps.common.ui.icons.Icon_Screen_Back
import de.dh.raaps.common.ui.theme.AppTheme

fun screenTitle(text: String): @Composable () -> Unit {
    return {
        ScreenTitle(text = text)
    }
}

@Composable
fun ScreenTitle(
    text: String
) {
    val modifiedText = text.replace("-", "-\u200B")
    Text(
        text = modifiedText,
        style = MaterialTheme.typography.headlineLarge,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .padding(start = 8.dp, end = 20.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ScreenTitlePreview() {
    AppTheme {
        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                LargeTopAppBar(
                    title = { ScreenTitle("Headline des Screens") },
                    navigationIcon = {
                        IconButton(onClick = {}) {
                            Icon(
                                imageVector = Icon_Screen_Back,
                                contentDescription = stringResource(R.string.cd_navigate_up)
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .contentScrollIndicator(scrollableState = scrollState)
                        .padding(16.dp)
                        .verticalScroll(scrollState)
                ) {
                    ScaffoldSubTitle(
                        scrollBehavior = scrollBehavior,
                        text = "Subtitel"
                    )

                    Text("Screen-Inhalt")
                }
            }
        }
    }
}