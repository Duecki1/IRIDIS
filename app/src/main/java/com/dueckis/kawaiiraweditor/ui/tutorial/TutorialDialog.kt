package com.dueckis.kawaiiraweditor.ui.tutorial

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

sealed interface TutorialStep {
    val id: String
    val title: String
    val body: List<String>
    val continueLabel: String

    data class Info(
        override val id: String,
        override val title: String,
        override val body: List<String>,
        override val continueLabel: String = "Continue"
    ) : TutorialStep

    data class MultipleChoice(
        override val id: String,
        override val title: String,
        override val body: List<String>,
        val options: List<TutorialOption>,
        override val continueLabel: String = "Continue"
    ) : TutorialStep
}

data class TutorialOption(
    val id: String,
    val title: String,
    val description: String? = null
)

@Composable
fun TutorialDialog(
    step: TutorialStep,
    selectedOptionId: String?,
    continueEnabled: Boolean,
    onOptionSelected: (String) -> Unit,
    onContinue: () -> Unit,
    onBack: (() -> Unit)?
) {
    AlertDialog(
        onDismissRequest = { onBack?.invoke() },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                step.body.forEachIndexed { index, paragraph ->
                    if (index > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Text(
                        text = paragraph,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Start
                    )
                }
                if (step is TutorialStep.MultipleChoice) {
                    Spacer(modifier = Modifier.height(16.dp))
                    step.options.forEach { option ->
                        val selected = option.id == selectedOptionId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = selected,
                                    onClick = { onOptionSelected(option.id) },
                                    role = Role.RadioButton
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selected, onClick = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = option.title,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                option.description?.takeIf { it.isNotBlank() }?.let { description ->
                                    Text(
                                        text = description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onContinue, enabled = continueEnabled) {
                Text(step.continueLabel)
            }
        },
        dismissButton = onBack?.let { handler ->
            {
                TextButton(onClick = handler) {
                    Text("Back")
                }
            }
        }
    )
}
