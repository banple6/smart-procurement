package com.smartprocurement.internal.domain.product

object ProductOptions {
    private val bulkUnits = setOf("公斤", "斤")
    private val automaticSpecs = setOf("散装", "预包装")

    val primaryUnits = listOf("公斤", "斤", "箱", "袋", "个")
    val extraUnits = listOf("筐", "盒", "瓶", "份", "包")
    val allUnits = primaryUnits + extraUnits

    val supplyStatuses = listOf("正常供应", "库存紧张", "暂停供应")
    val storageMethods = listOf("常温", "冷藏", "冷冻", "阴凉干燥")

    fun defaultSpecForUnit(unit: String): String = if (unit in bulkUnits) "散装" else "预包装"

    fun resolveSpecForUnit(unit: String, currentSpec: String): String {
        return if (currentSpec.isBlank() || currentSpec in automaticSpecs) {
            defaultSpecForUnit(unit)
        } else {
            currentSpec
        }
    }
}
