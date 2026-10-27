package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.cloudstream.CloudStreamRepository
import com.nuvio.app.features.plugins.CloudStreamSettingsSection

internal actual fun LazyListScope.cloudStreamSettingsContent() {
    item {
        val cloudStreamState by remember {
            CloudStreamRepository.initialize()
            CloudStreamRepository.uiState
        }.collectAsStateWithLifecycle()
        val extensionLanguages = remember(cloudStreamState.plugins) {
            cloudStreamState.plugins
                .mapNotNull { it.metadata.language?.trim()?.lowercase()?.takeIf(String::isNotEmpty) }
                .distinct()
                .sorted()
        }
        CloudStreamExtensionLanguageSettings(isTablet = false, availableLanguages = extensionLanguages)
    }
    item {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CloudStreamSettingsSection()
        }
    }
}
