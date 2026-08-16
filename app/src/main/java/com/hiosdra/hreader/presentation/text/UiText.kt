package com.hiosdra.hreader.presentation.text

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

sealed interface UiText {
    data class Resource(
        @StringRes val id: Int,
        val args: List<Any> = emptyList()
    ) : UiText

    data class Plural(
        @PluralsRes val id: Int,
        val count: Int,
        val args: List<Any> = emptyList()
    ) : UiText

    data class Plain(val value: String) : UiText
}

@Composable
fun UiText.resolve(): String = when (this) {
    is UiText.Resource -> stringResource(id, *args.map { it.resolveArgument() }.toTypedArray())
    is UiText.Plural -> pluralStringResource(id, count, *args.map { it.resolveArgument() }.toTypedArray())
    is UiText.Plain -> value
}

@Composable
private fun Any.resolveArgument(): Any = (this as? UiText)?.resolve() ?: this
