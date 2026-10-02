package com.jvoice.core.i18n

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The authoring side of the bilingual build.
 *
 * Every content form - a story, a study page, a question, a category - now edits
 * a [LocalizedText] instead of a [String]. Rather than doubling the number of
 * boxes on screen, the form keeps one set of fields and a language tab above
 * them: the tab decides which side of each [LocalizedText] the boxes are bound
 * to.
 *
 * One language is required, the other optional. That is a deliberate choice over
 * requiring both: a reporter filing breaking news in Telugu should not be blocked
 * on an English translation, and readers never see a gap because
 * [LocalizedText.get] falls back. What the desk gets instead is a visible
 * reminder - the tab badge and [rememberLocalizedFormState]'s
 * [LocalizedFormState.missingLanguages].
 */

/* ------------------------------------------------------------------- state */

/**
 * Tracks which language tab an authoring form is on, and how complete the fields
 * bound to it are.
 *
 * [fields] is every [LocalizedText] the form edits. Pass them all so the tab
 * badges and the summary line describe the whole form rather than one box.
 */
class LocalizedFormState internal constructor(
    initialLanguage: AppLanguage
) {
    var language: AppLanguage by mutableStateOf(initialLanguage)
        private set

    fun select(next: AppLanguage) {
        language = next
    }

    companion object {
        /** Fields with nothing written in [language] at all. */
        fun emptyFieldCount(fields: List<LocalizedText>, language: AppLanguage): Int =
            fields.count { it.rawFor(language).isBlank() }

        /** True when at least one language is filled across every field. */
        fun isSubmittable(fields: List<LocalizedText>): Boolean =
            fields.isNotEmpty() && fields.all { !it.isBlank }

        /** Languages still missing from at least one field. */
        fun missingLanguages(fields: List<LocalizedText>): List<AppLanguage> =
            AppLanguage.entries.filter { language ->
                fields.any { it.rawFor(language).isBlank() }
            }
    }
}

/**
 * Remembers the active authoring tab. Starts on the reader's own language, which
 * is almost always the one the desk writes in first.
 */
@Composable
fun rememberLocalizedFormState(
    initialLanguage: AppLanguage = LocalAppLanguage.current
): LocalizedFormState {
    val saved = rememberSaveable { mutableStateOf(initialLanguage.code) }
    val state = remember { LocalizedFormState(AppLanguage.fromCode(saved.value)) }
    // Keeps the remembered tab across configuration changes.
    saved.value = state.language.code
    return state
}

/* -------------------------------------------------------------------- tabs */

/**
 * The `[ English ] [ తెలుగు ● ]` row that sits above a bilingual form.
 *
 * A tab carries a dot badge when any field is still empty in that language, so
 * the author can see at a glance which side needs work without switching to it.
 */
@Composable
fun ContentLanguageTabs(
    state: LocalizedFormState,
    fields: List<LocalizedText>,
    modifier: Modifier = Modifier,
    showHint: Boolean = true
) {
    Column(modifier.fillMaxWidth()) {
        TabRow(selectedTabIndex = AppLanguage.entries.indexOf(state.language)) {
            AppLanguage.entries.forEach { language ->
                val emptyCount = LocalizedFormState.emptyFieldCount(fields, language)
                Tab(
                    selected = state.language == language,
                    // Only the chosen tab in the brand colour; red on both
                    // read as both being selected.
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { state.select(language) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (emptyCount > 0) {
                                // Beside the name, not over it: a corner badge
                                // clipped the last letters of both labels.
                                Text(language.labelNative)
                                Spacer(Modifier.width(6.dp))
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ) { Text(emptyCount.toString()) }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text(language.labelNative)
                                }
                            }
                        }
                    }
                )
            }
        }
        if (showHint) {
            Spacer(Modifier.height(8.dp))
            LocalizedFormStatus(fields = fields, language = state.language)
        }
    }
}

/**
 * One line under the tabs saying where the form stands - which language is
 * missing, or that both are done.
 */
@Composable
fun LocalizedFormStatus(
    fields: List<LocalizedText>,
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    val missing = LocalizedFormState.missingLanguages(fields)
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (missing.isEmpty()) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                tr(Strings.Desk.bothFilled),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            // Informational, not an error: one language is enough to file.
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                missing.joinToString(", ") { lang ->
                    if (lang == AppLanguage.TELUGU) Strings.Desk.teluguEmpty.get(language)
                    else Strings.Desk.englishEmpty.get(language)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/* ------------------------------------------------------------------ fields */

/**
 * An [OutlinedTextField] bound to one side of a [LocalizedText].
 *
 * Binds to [LocalizedText.rawFor], not [LocalizedText.get] - the box must stay
 * visibly empty when that language has not been written. Binding to the
 * fallback would echo the Telugu into the English box and the author would
 * "save" a translation they never made.
 */
@Composable
fun LocalizedOutlinedTextField(
    value: LocalizedText,
    onValueChange: (LocalizedText) -> Unit,
    language: AppLanguage,
    label: LocalizedText,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    singleLine: Boolean = false,
    required: Boolean = false,
    placeholder: LocalizedText? = null,
    /** Turn on after a submit attempt - an untouched form is not an error. */
    showError: Boolean = false
) {
    val other = value.rawFor(language.other)
    val isEmpty = value.rawFor(language).isBlank()

    Column(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value.rawFor(language),
            onValueChange = { typed -> onValueChange(value.with(language, typed)) },
            label = {
                Text(
                    label.get(LocalAppLanguage.current) +
                        " (" + language.labelNative + ")" +
                        if (required) " *" else ""
                )
            },
            placeholder = placeholder?.let { { Text(it.get(language)) } },
            minLines = minLines,
            singleLine = singleLine,
            isError = showError && required && value.isBlank,
            modifier = Modifier.fillMaxWidth()
        )
        // Offer a copy across only when there is something to copy and nothing
        // to overwrite - a one-tap start for the translator, never a silent
        // clobber of work already done.
        if (isEmpty && other.isNotBlank()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { onValueChange(value.with(language, other)) }) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        tr(Strings.Desk.copyToOther) + " · " + language.other.labelNative,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

/**
 * Header block for a bilingual form: the tabs, plus the one-off explanation of
 * why there are two of everything.
 */
@Composable
fun LocalizedFormHeader(
    state: LocalizedFormState,
    fields: List<LocalizedText>,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth()) {
        Text(
            tr(Strings.Desk.languageTabsTitle),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            tr(Strings.Desk.languageTabsHint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(10.dp))
        ContentLanguageTabs(state = state, fields = fields)
    }
}
