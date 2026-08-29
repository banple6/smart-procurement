package com.smartprocurement.internal

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CartDeletionPolicyTest {
    @Test
    fun cart_deletion_targets_the_product_primary_key() {
        val daoSource = File("src/main/java/com/example/data/AppDatabase.kt").readText()
        val repositorySource = File("src/main/java/com/example/data/SupplyRepository.kt").readText()

        assertTrue(daoSource.contains("DELETE FROM cart_items WHERE productId = :productId"))
        assertTrue(repositorySource.contains("supplyDao.deleteCartItemByProductId(productId)"))
    }
}
