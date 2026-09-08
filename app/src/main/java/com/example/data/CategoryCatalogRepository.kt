package com.smartprocurement.internal.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ProductCategoryDefinition(
    val name: String,
    val sortOrder: Int,
)

class CategoryCatalogRepository(private val apiClient: ProcurementApiClient) {
    private val _categories = MutableStateFlow<List<ProductCategoryDefinition>>(emptyList())
    val categories = _categories.asStateFlow()

    fun refresh(token: String): List<ProductCategoryDefinition> {
        val categories = apiClient.productCategories(token)
        _categories.value = categories
        return categories
    }
}
