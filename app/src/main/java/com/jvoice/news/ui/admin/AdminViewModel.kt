package com.jvoice.news.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jvoice.news.data.model.Category
import com.jvoice.news.data.model.NewsStatus
import com.jvoice.news.data.model.ReporterStats
import com.jvoice.news.data.model.UserRole
import com.jvoice.news.data.repository.NewsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.jvoice.core.i18n.LocalizedText

data class AdminStats(
    val totalUsers: Int = 0,
    val totalReporters: Int = 0,
    val totalEditors: Int = 0,
    val totalNews: Int = 0,
    val publishedNews: Int = 0,
    val pendingNews: Int = 0,
    val reportedNews: Int = 0
)

class AdminViewModel : ViewModel() {

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _categoryFilter = MutableStateFlow<String?>(null)
    val categoryFilter: StateFlow<String?> = _categoryFilter.asStateFlow()

    private val _statusFilter = MutableStateFlow<NewsStatus?>(null)
    val statusFilter: StateFlow<NewsStatus?> = _statusFilter.asStateFlow()

    val categories: StateFlow<List<Category>> = NewsRepository.categories
    val locations: List<String> = NewsRepository.locations

    val stats: StateFlow<AdminStats> = combine(
        NewsRepository.articles,
        NewsRepository.users
    ) { articles, users ->
        AdminStats(
            totalUsers = users.count { it.role == UserRole.READER },
            totalReporters = users.count { it.role == UserRole.REPORTER },
            totalEditors = users.count { it.role == UserRole.EDITOR },
            totalNews = articles.size,
            publishedNews = articles.count { it.status == NewsStatus.PUBLISHED },
            pendingNews = articles.count {
                it.status == NewsStatus.SUBMITTED || it.status == NewsStatus.UNDER_REVIEW
            },
            reportedNews = articles.count { it.reportCount > 0 }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AdminStats())

    val filteredArticles = combine(
        NewsRepository.articles,
        _query,
        _categoryFilter,
        _statusFilter
    ) { _, query, category, status ->
        NewsRepository.filterArticles(query, category, status)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val reporterStats: StateFlow<List<ReporterStats>> = combine(
        NewsRepository.articles,
        NewsRepository.reporters
    ) { _, _ -> NewsRepository.reporterStats() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categoryUsage: StateFlow<List<Pair<Category, Int>>> = combine(
        NewsRepository.categories,
        NewsRepository.articles
    ) { categories, _ ->
        categories.map { it to NewsRepository.articleCountForCategory(it.id) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pendingApprovedCount: StateFlow<Int> = NewsRepository.articles
        .map { list -> list.count { it.status == NewsStatus.APPROVED } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        viewModelScope.launch {
            delay(500)
            _isLoading.value = false
        }
    }

    fun setQuery(value: String) { _query.value = value }
    fun setCategoryFilter(value: String?) { _categoryFilter.value = value }
    fun setStatusFilter(value: NewsStatus?) { _statusFilter.value = value }

    fun categoryName(id: String) = NewsRepository.categoryName(id)

    // news management
    fun toggleBreaking(id: String) = NewsRepository.toggleBreaking(id)
    fun toggleFeatured(id: String) = NewsRepository.toggleFeatured(id)
    fun removeArticle(id: String) = NewsRepository.removeArticle(id)
    fun publish(id: String) = NewsRepository.publishApproved(id)
    fun unpublish(id: String) = NewsRepository.unpublish(id)

    // category management
    fun addCategory(name: LocalizedText, emoji: String) =
        NewsRepository.addCategory(name, emoji)

    fun updateCategory(id: String, name: LocalizedText, emoji: String) =
        NewsRepository.updateCategory(id, name, emoji)

    fun toggleCategory(id: String) = NewsRepository.toggleCategoryEnabled(id)

    /** @return false when the category still has articles attached. */
    fun deleteCategory(id: String) = NewsRepository.deleteCategory(id)

    // reporter management - each goes through the staffAccounts function
    suspend fun toggleReporterActive(userId: String) = NewsRepository.toggleReporterActive(userId)
    suspend fun updateReporterLocation(userId: String, location: String) =
        NewsRepository.updateReporterLocation(userId, location)
    suspend fun nextReporterId(area: String) = NewsRepository.nextReporterId(area)
    suspend fun createReporter(area: String, name: String, phone: String, location: String, password: String) =
        NewsRepository.createReporter(area, name, phone, location, password)
    suspend fun setReporterPassword(userId: String, password: String) =
        NewsRepository.setReporterPassword(userId, password)
}
