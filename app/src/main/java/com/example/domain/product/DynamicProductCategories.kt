package com.smartprocurement.internal.domain.product

import com.smartprocurement.internal.data.ProductCategoryDefinition

const val ALL_PRODUCT_CATEGORIES = "全部"

fun productCategoryFilters(categories: Iterable<String>): List<String> =
    listOf(ALL_PRODUCT_CATEGORIES) + categories.asSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
        .sorted()
        .toList()

fun productCategoryFilters(
    categories: Iterable<String>,
    catalog: Iterable<ProductCategoryDefinition>,
): List<String> {
    val actual = categories.asSequence().map(String::trim).filter(String::isNotBlank).toSet()
    if (actual.isEmpty()) return listOf(ALL_PRODUCT_CATEGORIES)
    val known = catalog.asSequence()
        .map { it.copy(name = it.name.trim()) }
        .filter { it.name.isNotBlank() && it.name in actual }
        .distinctBy { it.name }
        .sortedWith(compareBy<ProductCategoryDefinition> { it.sortOrder }.thenBy { it.name })
        .map { it.name }
        .toList()
    val orphan = (actual - known.toSet()).sorted()
    return listOf(ALL_PRODUCT_CATEGORIES) + known + orphan
}

fun adminProductCategoryOptions(
    catalog: Iterable<ProductCategoryDefinition>,
    fallbackCategories: Iterable<String>,
    currentCategory: String = "",
): List<String> {
    val active = catalog.asSequence()
        .map { it.copy(name = it.name.trim()) }
        .filter { it.name.isNotBlank() }
        .distinctBy { it.name }
        .sortedWith(compareBy<ProductCategoryDefinition> { it.sortOrder }.thenBy { it.name })
        .map { it.name }
        .toList()
        .ifEmpty { productCategoryFilters(fallbackCategories).drop(1) }
    val current = currentCategory.trim()
    return if (current.isNotBlank() && current !in active) active + current else active
}

fun selectedProductCategory(selected: String, categories: List<String>): String =
    selected.takeIf { it == ALL_PRODUCT_CATEGORIES || it in categories } ?: ALL_PRODUCT_CATEGORIES

fun matchesProductCategory(productCategory: String, selectedCategory: String): Boolean =
    selectedCategory == ALL_PRODUCT_CATEGORIES || productCategory.trim() == selectedCategory
