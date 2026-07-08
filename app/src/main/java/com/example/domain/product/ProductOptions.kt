package com.smartprocurement.internal.domain.product

object ProductOptions {
    val primaryCategories = listOf("蔬菜", "水果", "肉禽", "水产", "粮油")
    val extraCategories = listOf("蛋奶", "调料", "其他")
    val allCategories = primaryCategories + extraCategories

    val primaryUnits = listOf("公斤", "斤", "箱", "袋", "个")
    val extraUnits = listOf("筐", "盒", "瓶", "份", "包")
    val allUnits = primaryUnits + extraUnits

    val supplyStatuses = listOf("正常供应", "库存紧张", "暂停供应")
    val storageMethods = listOf("常温", "冷藏", "冷冻", "阴凉干燥")
}
