package com.dannyk.xirea.ui.report

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.dannyk.xirea.util.CrashReporter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrashReportScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lastCrash = remember { CrashReporter.getLastCrash(context) }
    var includeCrash by remember { mutableStateOf(true) }
    var userNotes by remember { mutableStateOf(TextFieldValue("")) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Crash Report") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    val subject = "Xirea Crash Report"
                    val body = buildString {
                        appendLine("User notes:")
                        appendLine(userNotes.text.ifBlank { "(none)" })
                        appendLine()
                        appendLine("Device:")
                        appendLine("${Build.MANUFACTURER} ${Build.MODEL}")
                        appendLine("Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                        appendLine()
                        if (includeCrash && !lastCrash.isNullOrBlank()) {
                            appendLine("Last crash:")
                            appendLine(lastCrash)
                        }
                    }

                    val emailIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_EMAIL, arrayOf("Danyalkhattak739@gmail.com"))
                        putExtra(Intent.EXTRA_SUBJECT, subject)
                        putExtra(Intent.EXTRA_TEXT, body)
                    }

                    try {
                        context.startActivity(Intent.createChooser(emailIntent, "Send report"))
                    } catch (e: ActivityNotFoundException) {
                        val mailto = "mailto:Danyalkhattak739@gmail.com" +
                            "?subject=" + Uri.encode(subject) +
                            "&body=" + Uri.encode(body)
                        val fallback = Intent(Intent.ACTION_VIEW, Uri.parse(mailto))
                        context.startActivity(fallback)
                    }
                }
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Describe what happened and send the report by email.",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = userNotes,
                onValueChange = { userNotes = it },
                label = { Text("What were you doing?") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(
                    checked = includeCrash,
                    onCheckedChange = { includeCrash = it },
                    enabled = !lastCrash.isNullOrBlank()
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Include last crash log")
            }

            if (lastCrash.isNullOrBlank()) {
                Text(
                    text = "No crash log found.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Last crash (preview):",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    tonalElevation = 1.dp,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = lastCrash,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}
