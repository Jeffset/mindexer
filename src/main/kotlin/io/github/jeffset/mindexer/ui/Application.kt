package io.github.jeffset.mindexer.ui

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.ListItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import io.github.jeffset.mindexer.VerboseLogger
import io.github.jeffset.mindexer.allowlist.AllowlistFileImpl
import io.github.jeffset.mindexer.core.Indexer
import io.github.jeffset.mindexer.core.ResolvingOptions
import io.github.jeffset.mindexer.data.SearchRanked
import io.github.jeffset.mindexer.data.openDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import java.io.File


private enum class IndexState {
    Indexing,
    Complete,
}

@Composable
@Preview
fun App() {
    val scope = rememberCoroutineScope()
    var searchPrompt by remember { mutableStateOf("") }

    var indexState by remember { mutableStateOf(IndexState.Complete) }
    val db = remember { openDatabase(dropExisting = false) }

    val artifactsCount: Long by remember {
        db.indexDBQueries.artifactsCount().asFlow().mapToOne(Dispatchers.IO)
    }.collectAsState(0)

    val artifacts: List<SearchRanked> by remember(searchPrompt) {
        if (searchPrompt.isNotBlank()) {
            db.indexDBQueries.searchRanked(searchPrompt).asFlow().mapToList(Dispatchers.IO)
        } else emptyFlow()
    }.collectAsState(emptyList())

    MaterialTheme {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
                .padding(all = 8.dp)
        ) {
            OutlinedTextField(
                label = { Text("Search") },
                leadingIcon = { Icon(Icons.Default.Search, "search") },
                placeholder = { Text("Input the search prompt here") },
                value = searchPrompt,
                onValueChange = { searchPrompt = it },
                modifier = Modifier.fillMaxWidth()
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(artifacts) { artifact ->
                    @OptIn(ExperimentalMaterialApi::class)
                    ListItem(
                        text = { Text("${artifact.group_id}:${artifact.artifact_id}:${artifact.version}") },
                        secondaryText = {
                            Text(artifact.supported_kmp_platforms.toString())
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                val statusText = remember(indexState) {
                    when(indexState) {
                        IndexState.Indexing -> "Indexing..."
                        IndexState.Complete -> "Complete"
                    }
                }
                Text(
                    "Indexed artifacts: $artifactsCount ($statusText)",
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        scope.launch(Dispatchers.Default) {
                            indexState = IndexState.Indexing
                            Indexer(
                                allowlist = AllowlistFileImpl(File("/home/jeffset/Downloads/maven-kmp-libraries.csv")),
                                resolveOptions = ResolvingOptions(
                                    resolveKmpLatestOnly = true,
                                ),
                                logger = VerboseLogger,
                                database = db,
                            ).index()
                            indexState = IndexState.Complete
                        }
                    },
                ) {
                    Text("Reindex")
                }
            }
        }
    }
}

fun runUi() = application {
    val windowState = rememberWindowState(
        size = DpSize(600.dp, 900.dp),
    )

    Window(
        state = windowState,
        onCloseRequest = ::exitApplication,
        title = "My Maven Indexer (mindexer)",
    ) {
        App()
    }
}
