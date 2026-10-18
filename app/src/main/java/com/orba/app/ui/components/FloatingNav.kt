package com.orba.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.orba.app.ui.Tab
import com.orba.app.ui.theme.Coral
import com.orba.app.ui.theme.NavBarHeight
import com.orba.app.ui.theme.darkGlass

/** Barra de navegación flotante y translúcida (deja ver el video por detrás). */
@Composable
fun FloatingNav(
    tab: Int,
    onTab: (Int) -> Unit,
    modifier: Modifier = Modifier,
    showVideo: Boolean = true,
    showMusic: Boolean = true,
) {
    Row(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .fillMaxWidth()
            .height(NavBarHeight)
            .darkGlass(RoundedCornerShape(26.dp), alpha = 0.62f)
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavItem(Icons.Rounded.Home, "Inicio", tab == Tab.HOME, Modifier.weight(1f)) { onTab(Tab.HOME) }
        if (showVideo) NavItem(Icons.Rounded.SmartDisplay, "Ver", tab == Tab.FEED, Modifier.weight(1f)) { onTab(Tab.FEED) }
        if (showMusic) NavItem(Icons.Rounded.Headphones, "Escuchar", tab == Tab.LISTEN, Modifier.weight(1f)) { onTab(Tab.LISTEN) }
        NavItem(Icons.Rounded.VideoLibrary, "Biblioteca", tab == Tab.LIBRARY, Modifier.weight(1f)) { onTab(Tab.LIBRARY) }
        NavItem(Icons.Rounded.Settings, "Ajustes", tab == Tab.SETTINGS, Modifier.weight(1f)) { onTab(Tab.SETTINGS) }
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val tint by animateColorAsState(if (selected) Coral else Color.White.copy(alpha = 0.65f), label = "navTint")
    Column(
        modifier
            .fillMaxHeight()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) Coral.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
    }
}
