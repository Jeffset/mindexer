package io.github.jeffset.mindexer.ui

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import com.darkrockstudios.libraries.mpfilepicker.FilePicker
import io.github.jeffset.mindexer.VerboseLogger
import io.github.jeffset.mindexer.allowlist.Allowlist
import io.github.jeffset.mindexer.allowlist.AllowlistExampleGroupsImpl
import io.github.jeffset.mindexer.allowlist.AllowlistFileImpl
import io.github.jeffset.mindexer.core.Indexer
import io.github.jeffset.mindexer.core.ResolvingOptions
import io.github.jeffset.mindexer.data.SearchRanked
import io.github.jeffset.mindexer.data.openDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import java.io.File


private enum class IndexingState {
    Indexing,
    Complete,
}

@Stable
class IndexState {
    var allowlist: Allowlist by mutableStateOf(AllowlistExampleGroupsImpl)
    var indexingJob: Job? = null
}

@Composable
@Preview
fun App() {
    val scope = rememberCoroutineScope()
    var searchPrompt by remember { mutableStateOf("") }
    var platformSearchPrompt by remember { mutableStateOf("") }

    val state: IndexState = remember { IndexState() }

    var showAllowlistFilePicker by remember { mutableStateOf(false) }
    var allowlistChanged by remember { mutableStateOf(false) }
    FilePicker(
        show = showAllowlistFilePicker,
        fileExtensions = listOf("csv"),
        title = "Pick Allowlist CSV",
    ) { file ->
        if (file != null) {
            state.allowlist = AllowlistFileImpl(File(file.path))
            state.indexingJob?.cancel()
            allowlistChanged = true
        }
        showAllowlistFilePicker = false
    }

    var indexState by remember { mutableStateOf(IndexingState.Complete) }
    val db = remember { openDatabase(dropExisting = false) }

    val artifactsCount: Long by remember {
        db.indexDBQueries.artifactsCount().asFlow().mapToOne(Dispatchers.IO)
    }.collectAsState(0)

    val artifacts: List<SearchRanked> by remember(searchPrompt, platformSearchPrompt) {
        if (searchPrompt.isNotBlank()) {
            println("New search: $searchPrompt, $platformSearchPrompt")
            db.indexDBQueries.searchRanked(
                namePrompt = searchPrompt,
                platformPrompt = platformSearchPrompt.takeIf { it.isNotBlank() },
            ).asFlow().mapToList(Dispatchers.IO)
        } else emptyFlow()
    }.collectAsState(emptyList())

    val primary = MaterialTheme.colors.primary

    MaterialTheme {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
                .padding(all = 8.dp)
        ) {
            val searchBoxFocus = remember { FocusRequester() }

            OutlinedTextField(
                label = { Text("Search") },
                leadingIcon = { Icon(Icons.Default.Search, "search") },
                placeholder = { Text("Input the search prompt here") },
                value = searchPrompt,
                onValueChange = { searchPrompt = it },
                modifier = Modifier.fillMaxWidth().focusRequester(searchBoxFocus)
            )
            OutlinedTextField(
                label = { Text("Platform Filter") },
                leadingIcon = { Icon(Icons.Default.Info, "platform search") },
                placeholder = { Text("Input the platform filter here") },
                value = platformSearchPrompt,
                onValueChange = { platformSearchPrompt = it },
                modifier = Modifier.fillMaxWidth()
            )
            Divider(modifier = Modifier.fillMaxWidth())

            LaunchedEffect(Unit) {
                delay(100)
                searchBoxFocus.requestFocus()
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(artifacts) { artifact ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        elevation = 4.dp,
                    ) {
                        val title = remember(artifact.group_id, artifact.artifact_id) {
                            buildAnnotatedString {
                                pushStyle(SpanStyle(color = Color.DarkGray))
                                append(artifact.group_id)
                                pop()
                                append(" / ")
                                pushStyle(SpanStyle(
                                    fontWeight = FontWeight.Bold,
                                    color = primary,
                                ))
                                append(artifact.artifact_id)
                            }
                        }
                        val platforms = remember(artifact.supported_kmp_platforms) {
                            (artifact.supported_kmp_platforms ?: emptyList())
                                .map { it.substringBefore(':') }.distinct()
                                .sorted()
                                .joinToString(" \uD83D\uDF84 ")
                        }
                        Column(
                            modifier = Modifier.padding(all = 8.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(title)
                                Spacer(modifier = Modifier.weight(1f))
                                Text(" latest: ${artifact.version}")
                            }
                            if (platforms.isNotBlank()) {
                                Text(platforms)
                            }
                        }
                    }
                }
            }

            Divider(modifier = Modifier.fillMaxWidth())

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                val statusText = remember(indexState, artifactsCount, allowlistChanged) {
                    buildString {
                        append("Indexed artifacts: ").append(artifactsCount)
                        append(" (")
                        append(when(indexState) {
                            IndexingState.Indexing -> "Indexing..."
                            IndexingState.Complete -> "Complete"
                        })
                        append(")")
                        if (allowlistChanged) {
                            append("\nAllowlist changed - Reindex needed")
                        }
                    }
                }
                Text(
                    statusText,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        allowlistChanged = false
                        state.indexingJob?.cancel()
                        state.indexingJob = scope.launch(Dispatchers.Default) {
                            indexState = IndexingState.Indexing
                            Indexer(
                                allowlist = state.allowlist,
                                resolveOptions = ResolvingOptions(
                                    resolveKmpLatestOnly = true,
                                ),
                                logger = VerboseLogger,
                                database = db,
                            ).index()
                            indexState = IndexingState.Complete
                        }
                    },
                ) {
                    Text("Reindex")
                }
                OutlinedButton(
                    onClick = {
                        showAllowlistFilePicker = true
                    }
                ) {
                    Text("Pick Allowlist CSV")
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
