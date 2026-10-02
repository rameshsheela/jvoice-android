package com.jvoice.news.data.repository

import com.jvoice.news.data.model.NewsClip
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.jvoice.aishorts.studio.StudioShorts

/**
 * Short-video clips for the Clips tab.
 *
 * A separate singleton so the existing [NewsRepository] is untouched. Same
 * in-memory pattern, so a real media backend can replace the source later.
 */
object ClipsRepository {

    // Published AI Shorts (Firestore `shorts`, status published) - made in the
    // studio on the web or in the app, and approved by an editor.
    private val _clips = MutableStateFlow<List<NewsClip>>(emptyList())
    val clips: StateFlow<List<NewsClip>> = _clips.asStateFlow()

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            StudioShorts.published().collect { shorts ->
                val likes = _clips.value.associate { it.id to it.likes }
                _clips.value = shorts.filter { it.videoUrl.startsWith("http") }.map { s ->
                    NewsClip(
                        id = s.id,
                        title = s.title,
                        description = s.script.ifBlank { s.content },
                        categoryId = "",
                        location = "",
                        thumbnailUrl = s.posterUrl.ifBlank { s.thumbnailUrl },
                        videoUrl = s.videoUrl,
                        durationSeconds = s.durationSeconds,
                        reporterName = s.reporterName,
                        publishedAt = s.publishedAt,
                        views = s.views,
                        likes = likes[s.id] ?: 0,
                        relatedArticleId = s.articleId.ifBlank { null }
                    )
                }
            }
        }
    }

    private val _likedIds = MutableStateFlow(emptySet<String>())
    val likedIds: StateFlow<Set<String>> = _likedIds.asStateFlow()

    private val _savedIds = MutableStateFlow(emptySet<String>())
    val savedIds: StateFlow<Set<String>> = _savedIds.asStateFlow()

    fun clipById(id: String): NewsClip? = _clips.value.firstOrNull { it.id == id }

    /** Breaking clips first, then newest. */
    fun feed(categoryId: String? = null): List<NewsClip> =
        _clips.value
            .filter { categoryId == null || it.categoryId == categoryId }
            .sortedWith(compareByDescending<NewsClip> { it.isBreaking }.thenByDescending { it.publishedAt })

    fun toggleLike(clipId: String): Boolean {
        var liked = false
        _likedIds.update { current ->
            if (current.contains(clipId)) {
                liked = false
                current - clipId
            } else {
                liked = true
                current + clipId
            }
        }
        _clips.update { list ->
            list.map {
                if (it.id == clipId) it.copy(likes = it.likes + if (liked) 1 else -1) else it
            }
        }
        return liked
    }

    fun toggleSave(clipId: String): Boolean {
        var saved = false
        _savedIds.update { current ->
            if (current.contains(clipId)) {
                saved = false
                current - clipId
            } else {
                saved = true
                current + clipId
            }
        }
        return saved
    }

    fun registerView(clipId: String) {
        _clips.update { list ->
            list.map { if (it.id == clipId) it.copy(views = it.views + 1) else it }
        }
    }

    fun categoriesInUse(): List<String> = _clips.value.map { it.categoryId }.distinct()
}
