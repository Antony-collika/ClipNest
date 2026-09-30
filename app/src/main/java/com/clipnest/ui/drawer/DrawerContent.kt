package com.clipnest.ui.drawer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clipnest.R
import com.clipnest.data.model.Topic
import com.clipnest.data.model.TopicOrigins
import com.clipnest.data.model.TopicTreeNode
import com.clipnest.data.local.TopicDao

@Composable
fun ClipNestDrawer(
    topicDao: TopicDao,
    onClose: () -> Unit,
    onTopicClick: (Topic) -> Unit = {}
) {
    val topicTree by topicDao.observeTopicTree(TopicOrigins.USER)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var selectedArea by remember { mutableStateOf(1) }

    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(320.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
            DrawerHeader(
                selectedArea = selectedArea,
                onAreaSelected = { selectedArea = it },
                onClose = onClose
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                item {
                    DrawerPlaceholderSection(
                        title = stringResource(R.string.drawer_recent),
                        enabled = selectedArea == 0
                    )
                }
                item {
                    DrawerPlaceholderSection(
                        title = stringResource(R.string.drawer_trending),
                        enabled = selectedArea == 0
                    )
                }
                item {
                    DrawerPlaceholderSection(
                        title = stringResource(R.string.drawer_insight),
                        enabled = selectedArea == 0
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                }
                item {
                    DrawerSectionHeader(
                        title = stringResource(R.string.drawer_my_topics),
                        trailing = {
                            IconButton(
                                onClick = {},
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = stringResource(R.string.drawer_add_topic)
                                )
                            }
                        }
                    )
                }

                if (selectedArea == 1) {
                    if (topicTree.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.drawer_no_topics),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                            )
                        }
                    } else {
                        items(topicTree.size) { index ->
                            TopicTreeItem(
                                node = topicTree[index],
                                onTopicClick = onTopicClick
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerHeader(
    selectedArea: Int,
    onAreaSelected: (Int) -> Unit,
    onClose: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.clipboard_manager),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.close)
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            DrawerTab(
                title = stringResource(R.string.drawer_mini_me),
                selected = selectedArea == 0,
                onClick = { onAreaSelected(0) },
                modifier = Modifier.weight(1f)
            )
            DrawerTab(
                title = stringResource(R.string.drawer_personal_area),
                selected = selectedArea == 1,
                onClick = { onAreaSelected(1) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DrawerTab(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier
) {
    Column(
        modifier = modifier.clickable(onClick = onClick).padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            ),
            color = if (selected) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 10.dp)
        )
        HorizontalDivider(
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant
        )
    }
}

@Composable
private fun DrawerSectionHeader(
    title: String,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.weight(1f)
        )
        trailing()
    }
}

@Composable
private fun DrawerPlaceholderSection(title: String, enabled: Boolean) {
    if (enabled) {
        DrawerSectionHeader(title = title)
    }
}

@Composable
private fun TopicTreeItem(
    node: TopicTreeNode,
    onTopicClick: (Topic) -> Unit
) {
    var expanded by remember(node.topic.id) { mutableStateOf(false) }
    val hasChildren = node.children.isNotEmpty()

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onTopicClick(node.topic) }
                .padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = node.topic.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            if (hasChildren) {
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                }
            }
        }

        if (expanded) {
            node.children.forEach { child ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTopicClick(child) }
                        .padding(start = 44.dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = child.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
