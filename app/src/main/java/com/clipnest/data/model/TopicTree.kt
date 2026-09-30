package com.clipnest.data.model

import androidx.room.Embedded
import androidx.room.Relation

data class TopicTreeNode(
    @Embedded val topic: Topic,
    @Relation(
        parentColumn = "id",
        entityColumn = "parentId"
    )
    val children: List<Topic>
)

data class TopicHierarchyState(
    @Embedded val topic: Topic,
    val isParent: Boolean,
    val isChild: Boolean,
    val isFree: Boolean
)
