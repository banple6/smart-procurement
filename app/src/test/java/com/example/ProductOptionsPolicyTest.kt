package com.smartprocurement.internal

import com.smartprocurement.internal.domain.product.ProductOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductOptionsPolicyTest {
    @Test
    fun product_option_sets_match_the_admin_form_contract() {
        assertEquals(listOf("蔬菜", "水果", "肉禽", "水产", "粮油"), ProductOptions.primaryCategories)
        assertEquals(listOf("蛋奶", "调料", "其他"), ProductOptions.extraCategories)
        assertEquals(listOf("公斤", "斤", "箱", "袋", "个"), ProductOptions.primaryUnits)
        assertEquals(listOf("筐", "盒", "瓶", "份", "包"), ProductOptions.extraUnits)
        assertEquals(listOf("正常供应", "库存紧张", "暂停供应"), ProductOptions.supplyStatuses)
        assertEquals(listOf("常温", "冷藏", "冷冻", "阴凉干燥"), ProductOptions.storageMethods)
    }

    @Test
    fun product_option_sets_do_not_model_off_shelf_as_supply_status() {
        assertFalse(ProductOptions.supplyStatuses.contains("已下架"))
        assertTrue(ProductOptions.allCategories.contains("其他"))
        assertTrue(ProductOptions.allUnits.contains("包"))
    }

    @Test
    fun default_spec_tracks_bulk_and_prepackaged_units() {
        assertEquals("散装", ProductOptions.defaultSpecForUnit("公斤"))
        assertEquals("散装", ProductOptions.defaultSpecForUnit("斤"))
        assertEquals("预包装", ProductOptions.defaultSpecForUnit("箱"))
        assertEquals("预包装", ProductOptions.defaultSpecForUnit("瓶"))
    }

    @Test
    fun changing_unit_only_replaces_blank_or_automatic_specs() {
        assertEquals("预包装", ProductOptions.resolveSpecForUnit("箱", ""))
        assertEquals("预包装", ProductOptions.resolveSpecForUnit("箱", "散装"))
        assertEquals("散装", ProductOptions.resolveSpecForUnit("公斤", "预包装"))
        assertEquals("12瓶/箱", ProductOptions.resolveSpecForUnit("瓶", "12瓶/箱"))
    }
}
