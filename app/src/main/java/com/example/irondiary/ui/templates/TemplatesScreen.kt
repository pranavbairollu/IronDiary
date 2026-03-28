package com.example.irondiary.ui.templates

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.irondiary.viewmodel.MainViewModel
import com.example.irondiary.viewmodel.MainViewModelFactory
import com.example.irondiary.viewmodel.TaskTemplate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesScreen() {
    val application = LocalContext.current.applicationContext as Application
    val mainViewModel: MainViewModel = viewModel(factory = MainViewModelFactory(application))
    val categorizedTemplates by mainViewModel.categorizedTemplates.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    if (showAddDialog) {
        var newTemplateName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("New Custom Template", style = MaterialTheme.typography.titleLarge) },
            text = {
                OutlinedTextField(
                    value = newTemplateName,
                    onValueChange = { newTemplateName = it },
                    label = { Text("Task description") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTemplateName.isNotBlank()) {
                            mainViewModel.addTemplate(newTemplateName)
                            showAddDialog = false
                        }
                    },
                    enabled = newTemplateName.isNotBlank()
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Template")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 80.dp) // padding for FAB
        ) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Task Templates",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "Tap any template to immediately add it to your daily list.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            items(categorizedTemplates.entries.toList(), key = { it.key }) { (category, templateList) ->
                Text(
                    text = category,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp, top = 24.dp, bottom = 8.dp)
                )
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column {
                        templateList.forEachIndexed { index, template ->
                            TemplateItemRow(
                                template = template,
                                onAdd = { mainViewModel.addTemplateToToday(template) },
                                onDelete = {
                                    if (template.category == "Custom") {
                                        mainViewModel.removeTemplate(template.title)
                                    }
                                }
                            )
                            if (index < templateList.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 56.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TemplateItemRow(
    template: TaskTemplate,
    onAdd: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onAdd() }
            .padding(vertical = 16.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = template.emoji,
            fontSize = 22.sp,
            modifier = Modifier.padding(end = 16.dp)
        )
        Text(
            text = template.title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        if (template.category == "Custom") {
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Template",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "Add",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}
