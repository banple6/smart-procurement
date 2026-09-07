package com.smartprocurement.internal.domain.product

const val ALL_PRODUCT_CATEGORIES = "全部"

fun productCategoryFilters(categories: Iterable<String>): List<String> =
    listOf(ALL_PRODUCT_CATEGORIES) + categories.asSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
        .sorted()
        .toList()

fun selectedProductCategory(selected: String, categories: List<String>): String =
    selected.takeIf { it == ALL_PRODUCT_CATEGORIES || it in categories } ?: ALL_PRODUCT_CATEGORIES

fun matchesProductCategory(productCategory: String, selectedCategory: String): Boolean =
    selectedCategory == ALL_PRODUCT_CATEGORIES || productCategory.trim() == selectedCategory
