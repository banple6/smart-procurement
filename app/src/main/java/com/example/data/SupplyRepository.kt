package com.example.data

import com.example.domain.validation.ActivationInput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.util.UUID

class SupplyRepository(private val supplyDao: SupplyDao) {

    val allProducts: Flow<List<ProductEntity>> = supplyDao.getAllProductsFlow()
    val deletedProducts: Flow<List<ProductEntity>> = supplyDao.getDeletedProductsFlow()
    val cartItems: Flow<List<CartItemEntity>> = supplyDao.getCartItemsFlow()
    val allOrders: Flow<List<OrderEntity>> = supplyDao.getAllOrdersFlow()

    fun getOrderFlow(orderId: String): Flow<OrderEntity?> = supplyDao.getOrderFlow(orderId)
    fun getOrderItemsFlow(orderId: String): Flow<List<OrderItemEntity>> = supplyDao.getOrderItemsFlow(orderId)

    suspend fun getProductById(id: String): ProductEntity? = supplyDao.getProductById(id)
    suspend fun updateProduct(product: ProductEntity) = supplyDao.updateProduct(product)
    suspend fun replaceProducts(products: List<ProductEntity>) {
        supplyDao.clearProducts()
        supplyDao.insertProducts(products)
    }
    suspend fun saveProduct(product: ProductEntity) = supplyDao.insertProduct(product.withComputedSupplyStatus())
    suspend fun setProductAvailable(id: String, available: Boolean) {
        supplyDao.updateProductAvailability(
            id = id,
            available = available,
            status = if (available) "正常供应" else "已下架",
            updatedAt = System.currentTimeMillis()
        )
    }
    suspend fun softDeleteProduct(id: String) = supplyDao.softDeleteProduct(id, System.currentTimeMillis())
    suspend fun restoreProduct(id: String) = supplyDao.restoreProduct(id, System.currentTimeMillis())

    suspend fun getUserById(id: String): UserEntity? = supplyDao.getUserById(id)
    suspend fun getUserByUsername(username: String): UserEntity? = supplyDao.getUserByUsername(username)

    suspend fun activateUser(input: ActivationInput, role: String): Result<UserEntity> {
        if (supplyDao.getUserByUsername(input.username) != null) {
            return Result.failure(IllegalArgumentException("账号已存在"))
        }
        val password = PasswordHasher.hash(input.password)
        val now = System.currentTimeMillis()
        val user = UserEntity(
            id = UUID.randomUUID().toString(),
            username = input.username.trim(),
            passwordHash = password.hash,
            passwordSalt = password.salt,
            realName = input.realName.trim(),
            phone = input.phone.trim(),
            department = input.department.trim(),
            role = role,
            createdAt = now,
            updatedAt = now
        )
        supplyDao.insertUser(user)
        return Result.success(user)
    }

    suspend fun login(username: String, password: String): Result<UserEntity> {
        val user = supplyDao.getUserByUsername(username.trim())
            ?: return Result.failure(IllegalArgumentException("账号或密码错误"))
        if (user.status != "active") return Result.failure(IllegalArgumentException("账号已停用，请联系管理员"))
        if (!PasswordHasher.verify(password, user.passwordSalt, user.passwordHash)) {
            return Result.failure(IllegalArgumentException("账号或密码错误"))
        }
        return Result.success(user)
    }

    suspend fun getCartItemsDirect(): List<CartItemEntity> = supplyDao.getCartItemsDirect()
    suspend fun addToCart(productId: String, quantity: Double, remarks: String = "") {
        val existing = supplyDao.getCartItemsDirect().find { it.productId == productId }
        if (existing != null) {
            // Overwrite or add quantity
            supplyDao.insertCartItem(CartItemEntity(productId, quantity, remarks))
        } else {
            supplyDao.insertCartItem(CartItemEntity(productId, quantity, remarks))
        }
    }
    suspend fun updateCartItemQuantity(productId: String, quantity: Double) {
        if (quantity <= 0) {
            supplyDao.deleteCartItem(CartItemEntity(productId, 0.0))
        } else {
            val existing = supplyDao.getCartItemsDirect().find { it.productId == productId }
            val remarks = existing?.remarks ?: ""
            supplyDao.insertCartItem(CartItemEntity(productId, quantity, remarks))
        }
    }
    suspend fun updateCartItemRemarks(productId: String, remarks: String) {
        val existing = supplyDao.getCartItemsDirect().find { it.productId == productId }
        val qty = existing?.quantity ?: 1.0
        supplyDao.insertCartItem(CartItemEntity(productId, qty, remarks))
    }
    suspend fun deleteCartItem(productId: String) {
        supplyDao.deleteCartItem(CartItemEntity(productId, 0.0))
    }
    suspend fun clearCart() = supplyDao.clearCart()

    suspend fun insertOrder(order: OrderEntity) = supplyDao.insertOrder(order)
    suspend fun insertOrderItems(items: List<OrderItemEntity>) = supplyDao.insertOrderItems(items)
    suspend fun replaceOrders(orderBundles: List<RemoteOrderBundle>) {
        supplyDao.clearOrderItems()
        supplyDao.clearOrders()
        if (orderBundles.isNotEmpty()) {
            supplyDao.insertOrders(orderBundles.map { it.order })
            supplyDao.insertOrderItems(orderBundles.flatMap { it.items })
        }
    }
    suspend fun upsertOrder(orderBundle: RemoteOrderBundle) {
        supplyDao.insertOrder(orderBundle.order)
        supplyDao.clearOrderItems(orderBundle.order.orderId)
        if (orderBundle.items.isNotEmpty()) supplyDao.insertOrderItems(orderBundle.items)
    }

    suspend fun updateOrderStatus(orderId: String, status: String) {
        val order = supplyDao.getOrderById(orderId)
        if (order != null) {
            supplyDao.insertOrder(order.copy(status = status))
        }
    }

    suspend fun populateInitialDataIfNeeded() {
        if (supplyDao.getUserCount() == 0) {
            val adminPassword = PasswordHasher.hash("admin123")
            supplyDao.insertUser(
                UserEntity(
                    id = "seed-admin",
                    username = "admin",
                    passwordHash = adminPassword.hash,
                    passwordSalt = adminPassword.salt,
                    realName = "系统管理员",
                    phone = "13800000000",
                    department = "后勤管理处",
                    role = "admin"
                )
            )
            val staffPassword = PasswordHasher.hash("staff123")
            supplyDao.insertUser(
                UserEntity(
                    id = "seed-staff",
                    username = "staff",
                    passwordHash = staffPassword.hash,
                    passwordSalt = staffPassword.salt,
                    realName = "普通员工",
                    phone = "13900000000",
                    department = "综合管理处",
                    role = "staff"
                )
            )
        }

        val existingProducts = supplyDao.getAllProductsFlow().first()
        if (existingProducts.isEmpty()) {
            val defaultProducts = listOf(
                ProductEntity(
                    id = "tomato",
                    name = "西红柿",
                    spec = "普通大红款 / 规格: kg",
                    unit = "kg",
                    imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuD7BfdAZCz8SqqZfvDag3Kmq9lSvY_TAhJumOWJWqJQdw3dau-srem4YibyxiZ6dQWp_Cjw3SS5vuXtiiJ1O3oq4fR3EZSIfJAsb-52SLvmr6tK2-HT48SpmYVlCEskiJLKt9Eo6oTs9dWdos5dKcUDqvcsZWEo5Z2htmpnowgY7ilNHbdiu0nAmojs_OpQ8mPKKBobF4MfrhA_B-PC1YdhuDwk6GTEt4KbXsfE1CzkaM5ohJQQ5YfOJ_0CMuL4FWh4Mz8vJixbGO8",
                    origin = "山东寿光",
                    minQty = 1.0,
                    stepQty = 0.5,
                    allowSubstitute = true,
                    stockStatus = "充足",
                    price = 4.5,
                    category = "蔬菜",
                    code = "VEG-TOMATO",
                    stockQuantity = "86",
                    warningQuantity = "20",
                    availableQuantity = "60",
                    storageMethod = "常温",
                    shelfLife = "建议 3 天内使用",
                    createdBy = "seed-admin"
                ),
                ProductEntity(
                    id = "potato",
                    name = "土豆",
                    spec = "黄心品种 / 规格: kg",
                    unit = "kg",
                    imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuAZDdgDG_DH_NB0Y-u9A1SrQhdBT-euSt_mc-4Hbs1PxNgAhxDO46hDlw0hueQGWz5Vbj-kZpv6KS5PneokbdT9_uGtLkG3vGCivuLpphfAyqDn4JCz-86YeC4v3dOwln2AygnWtuyJrtetQtNLzJxZSh1DoA9xXzxwEduyKJGiS1fu0eUIPNWlKeBOWMuw5Y3dDCu9kSqwj9D4__T5t1aNgtqHMdVlfNpPVXKbdU6JMBSeDN55M2IEaTud9ZEgT2t-ngXFfevnfbM",
                    origin = "内蒙古武川",
                    minQty = 1.0,
                    stepQty = 1.0,
                    allowSubstitute = true,
                    stockStatus = "紧张",
                    price = 3.2,
                    category = "蔬菜",
                    code = "VEG-POTATO",
                    stockQuantity = "12",
                    warningQuantity = "20",
                    availableQuantity = "8",
                    storageMethod = "阴凉干燥",
                    shelfLife = "建议 7 天内使用",
                    status = "库存紧张",
                    createdBy = "seed-admin"
                ),
                ProductEntity(
                    id = "broccoli",
                    name = "精品西兰花",
                    spec = "规格：一级 / 新鲜",
                    unit = "kg",
                    imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuAIrmrHXTi0EoVD3PLpkphIGmc6jbo0znuTYqaTbmLABHx-p2rZ8_YooJyAp13vtMBHRikETJ09mSGY1dXSzTl1ASNsDJvat48EXFZRCByTAAl7A1Fn_qYqCBqHeG-iSntg3NSMdSJFdBBajZ1-1Aqto_o78gexO6uJHVN-27R9uKxFHhIslVNBQ6dVJBlNR_ztkuOofu1fVPAaP-d_1m9OqNxVE84XrrBnQ_tXtCFarGcaa3nW5zjEr7QdSqO_v25szNyWIGP11ow",
                    origin = "河北张家口",
                    minQty = 1.0,
                    stepQty = 0.5,
                    allowSubstitute = true,
                    stockStatus = "充足",
                    price = 6.8,
                    category = "蔬菜",
                    code = "VEG-BROCCOLI",
                    stockQuantity = "40",
                    warningQuantity = "10",
                    availableQuantity = "30",
                    storageMethod = "冷藏",
                    shelfLife = "建议 2 天内使用",
                    createdBy = "seed-admin"
                ),
                ProductEntity(
                    id = "pork_ribs",
                    name = "本地冷鲜猪肋排",
                    spec = "规格：标准分切 / 抽真空",
                    unit = "kg",
                    imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuAUJZUAnB5VoGqJ4GamIItFHIoAwBLsH8RxIWQ1txkh7YLhAAF9obPY7BBqBjD8XPW0A8JTS-AfQzCTpPBPHiKo07Pns9UcQatCknrfvV3tKuKxO6QkvuhUQ27UtrXUH5HvXKl63NU1Yrdt8X6bUhwVKea0IVTXNmLj-h7gc7tZwzjp6tY6CpulXhRS1bbDeG6-L9UNw_ijknF4wCfCAFEBGRpR_lXybvuwXmXLii7m5kSZfypcyyBQ3FbzcBufkxR8tQ2E9Velqyc",
                    origin = "当地品牌供应",
                    minQty = 1.0,
                    stepQty = 1.0,
                    allowSubstitute = true,
                    stockStatus = "充足",
                    price = 28.0,
                    category = "肉禽",
                    code = "MEAT-RIBS",
                    stockQuantity = "36",
                    warningQuantity = "8",
                    availableQuantity = "25",
                    storageMethod = "冷藏",
                    shelfLife = "到货后 48 小时内使用",
                    createdBy = "seed-admin"
                ),
                ProductEntity(
                    id = "rice",
                    name = "特等长粒香米",
                    spec = "规格：5kg/袋",
                    unit = "袋",
                    imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuCbgIOptuG0MZ0vDrqxq8XJ2mJzXJxHN7nA--jG62Ejd11URcpsHd6FHyhgWt2lt419gfTIEykxxVH9r2ouNB2tEGOOeMhOmVeJZLZ-0IxS9icrGfjDrdGGmeZQI4g7h_01H_Ek5_v-hLizSH-oy7WnILkK1hV0r0l9ove_uh05qrEbLKFzYxEsgyoRW7WVX6AHnAPuCZJgFkt489ad0U-39_gk_cHoX2DWS1pqYif0KFXh8LhLEkhxhiEcBlyZtJG_DxGAV1DAVa8",
                    origin = "黑龙江五常",
                    minQty = 1.0,
                    stepQty = 1.0,
                    allowSubstitute = false,
                    stockStatus = "充足",
                    price = 45.0,
                    category = "粮油",
                    code = "GRAIN-RICE",
                    stockQuantity = "120",
                    warningQuantity = "30",
                    availableQuantity = "80",
                    storageMethod = "阴凉干燥",
                    shelfLife = "保质期 12 个月",
                    createdBy = "seed-admin"
                ),
                ProductEntity(
                    id = "egg",
                    name = "农家鲜鸡蛋",
                    spec = "规格：30枚/箱",
                    unit = "箱",
                    imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuDJUOyCZoT999W_ufNcTe0F99CvqdvfPKvs5eYHI1PW_BgdJCgNOfbRC5nsNvmBPk9JC-t7sUbZ6bg_1KQvq592V4Hys0y9m_Qew3AU7JSZ__yVeTfN47evufzU4Pja9x-TgYFklNheHwEJXdiir3_KtcBigaOELR5KMKEAaFCxeiVhxsopn9zf-N1u9c19x23qVhc-xEuds1Ycr3TdR7VmdfJTX0SRMZ4i1wsVP9aVKxPFd_iJxm3zvbagyFXG1FDVOg1inOKsMtg",
                    origin = "本土生态农业",
                    minQty = 1.0,
                    stepQty = 1.0,
                    allowSubstitute = true,
                    stockStatus = "充足",
                    price = 18.0,
                    category = "蛋奶",
                    code = "EGG-FRESH",
                    stockQuantity = "0",
                    warningQuantity = "15",
                    availableQuantity = "0",
                    storageMethod = "冷藏",
                    shelfLife = "建议 7 天内使用",
                    status = "暂停供应",
                    isAvailable = false,
                    createdBy = "seed-admin"
                ),
                ProductEntity(
                    id = "bok_choy",
                    name = "新鲜上海青 (一级)",
                    spec = "规格: 散装",
                    unit = "kg",
                    imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuA48Bu7iUpGXzWEdc5Iq8Uvws9YEWKRwhlSxTqxz0qpvoRmOMo8Z_96saBZBRXrhtP-dG_715__7ypj8yLW8lIc8cDttEjLRzfBYUMCqGWswdhH35S0Wm4VuDm2kRZEI9HKfZ9NiTrWuRrarCW7sYvloAEMW9L5c4Hy7GIkYZ-rrKkTOXkLO0RmagN0n1VdHo7x9EX0mOC_E-mzPSHdmj-UTMHsK1xAwyFYPGr0xoT0WyQmwOAPUwFCsq-GAmV0A8wgk5yrpID1ECc",
                    origin = "郊区有机菜地",
                    minQty = 1.0,
                    stepQty = 0.5,
                    allowSubstitute = true,
                    stockStatus = "充足",
                    price = 3.5,
                    category = "蔬菜",
                    code = "VEG-BOKCHOY",
                    stockQuantity = "55",
                    warningQuantity = "12",
                    availableQuantity = "45",
                    storageMethod = "冷藏",
                    shelfLife = "建议 2 天内使用",
                    createdBy = "seed-admin"
                ),
                ProductEntity(
                    id = "milk",
                    name = "全脂高钙鲜牛奶",
                    spec = "规格: 1L/盒",
                    unit = "盒",
                    imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuCfm1VPxLPr2byFefRir703AXoLGcDta5Jd-7Nhf4sSBknCl6w9ffwWryOusgNUYppEMVkWBDUKxwsoZD69hOpX_ULqdigmyIzO2OaDzZpA6M3GULYFuXp3E-IQcXhgl7Hy4r7zat-e3Vv05mQDAeogmu7Aqr1_TJLJc1tAAtsyAkuKPIKU-Fa937AY5hxuYSPATkl1bw5z8qR41ko0YTa-Wel9VSMsKZyuxQUBB2AOUD7XiBgzfR8NgIAHr8ww8JNLLvLikDfEmHg",
                    origin = "优质本土乳业",
                    minQty = 1.0,
                    stepQty = 1.0,
                    allowSubstitute = true,
                    stockStatus = "充足",
                    price = 12.0,
                    category = "蛋奶",
                    code = "MILK-FRESH",
                    stockQuantity = "48",
                    warningQuantity = "10",
                    availableQuantity = "32",
                    storageMethod = "冷藏",
                    shelfLife = "见包装日期",
                    createdBy = "seed-admin"
                )
            )
            supplyDao.insertProducts(defaultProducts)
        }

        val existingOrders = supplyDao.getAllOrdersFlow().first()
        if (existingOrders.isEmpty()) {
            val defaultOrders = listOf(
                OrderEntity(
                    orderId = "ORD-20231024-001",
                    submitTime = "2026-06-27 14:30",
                    deliveryPoint = "第二机关办公区",
                    status = "待确认",
                    requesterName = "张伟",
                    department = "机关后勤保障处",
                    phone = "138-xxxx-8888",
                    remarks = "需送往2楼储藏室",
                    estimatedDelivery = "明日 08:30 - 09:30"
                ),
                OrderEntity(
                    orderId = "ORD-20231024-089",
                    submitTime = "2026-06-27 11:20",
                    deliveryPoint = "市政中心主食堂",
                    status = "分拣中",
                    requesterName = "张伟",
                    department = "机关后勤保障处",
                    phone = "138-xxxx-8888",
                    progressPercent = 0.65f,
                    progressText = "正在打包生鲜类目 (6/12)"
                ),
                OrderEntity(
                    orderId = "ORD-20231024-042",
                    submitTime = "2026-06-27 10:15",
                    deliveryPoint = "高新科技园食堂",
                    status = "配送中",
                    requesterName = "张伟",
                    department = "机关后勤保障处",
                    phone = "138-xxxx-8888",
                    driverName = "李师傅",
                    driverPhone = "138****0000",
                    licensePlate = "苏E·88888",
                    distanceInfo = "距离目的地还剩 3.2km (约12分钟)"
                ),
                OrderEntity(
                    orderId = "ORD-20231023-112",
                    submitTime = "2026-06-26 09:15",
                    deliveryPoint = "教育局职工之家",
                    status = "已完成",
                    requesterName = "张伟",
                    department = "机关后勤保障处",
                    phone = "138-xxxx-8888"
                ),
                OrderEntity(
                    orderId = "ORD-20231024-005",
                    submitTime = "2026-06-27 13:00",
                    deliveryPoint = "第一人民医院分部",
                    status = "异常",
                    requesterName = "张伟",
                    department = "机关后勤保障处",
                    phone = "138-xxxx-8888",
                    exceptionText = "品类缺货：特级有机胡萝卜 (暂无库存，需更换)"
                ),
                // This is Screen 1: ORD-20231024-082
                OrderEntity(
                    orderId = "ORD-20231024-082",
                    submitTime = "2026-06-27 13:45",
                    deliveryPoint = "朝阳区幸福大街102号 A座负一楼食堂收货处",
                    status = "配送中",
                    requesterName = "张伟 (采购主管)",
                    department = "后勤管理处",
                    phone = "138-xxxx-8888",
                    remarks = "本次采购主要用于本周四的部门会议午餐接待，请确保食材新鲜，尤其是蔬菜类。",
                    estimatedDelivery = "预计 14:30 抵达",
                    driverName = "配送员",
                    driverPhone = "138****9999",
                    licensePlate = "京A·88888"
                )
            )
            supplyDao.insertOrders(defaultOrders)

            // Populate some default items for historical orders
            val orderItems = listOf(
                // For ORD-20231024-082 (Screen 1 details)
                OrderItemEntity(
                    orderId = "ORD-20231024-082",
                    productId = "tomato_sub",
                    productName = "精品红番茄 (代)",
                    productSpec = "原定: 普通大番茄 -> 现选: 特级精品红番茄",
                    productUnit = "kg",
                    productImageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuBVQdOSw17EkhGH8JzRC8d4h6EMbqP9o0KqQJniuUp8aRb55pBvcy7BqFEx9QcajAqKnfT6CUvMPnArkgZUG1TqplZEQ_H0pGPOdWYc6fGbA18Mf3eaRKs7qrrfYm6xk3vkZe--fUfJJQS7O8Fz6HxxLbdCkkPqBpNnSEoi76kuYGYf6kLzvOzxNekKiVqSGUnRW-I14Tf67k62LeAP5B_VwNbIOQLeBE8IRHLyh4sasPpNtcu8lXoQjKf0sKPLQkvzzN62-m9pSIQ",
                    requestedQty = 10.0,
                    confirmedQty = 8.0,
                    deliveredQty = 8.0,
                    price = 6.0,
                    isSubstitute = true,
                    originalProductName = "普通大番茄",
                    currentProductName = "特级精品红番茄"
                ),
                OrderItemEntity(
                    orderId = "ORD-20231024-082",
                    productId = "bok_choy",
                    productName = "上海青 (一级)",
                    productSpec = "规格: 500g/捆",
                    productUnit = "捆",
                    productImageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuCQRXgJiiUMGQxzFLHKUoHXxdxQtMozC3lA2JQHMkDJ_OJgq_4DzZyVBDQTOlLFbq5ugeNpY5qIZEFKFjz23em2ENEW0kQ7L7XmgPDAmfcQ9FH8cnKJPM0hY1SSxMVmu7gCMiORI8ieBH9LjAOuzsG8gcA0CpkvAEkddqCFu8Pm1IjpMCzffnkISDNNtdlm6oDbhiWTagdm1S4754_vWBbj-xXANxXw7z4oo-Pm54yZuzsl80EnQtY7ZZ8UkrljrQZ0OorEFmh5zTk",
                    requestedQty = 20.0,
                    confirmedQty = 20.0,
                    deliveredQty = 20.0,
                    price = 3.5
                ),
                OrderItemEntity(
                    orderId = "ORD-20231024-082",
                    productId = "pork_loin",
                    productName = "精选猪里脊肉",
                    productSpec = "规格: 冷鲜/5kg箱",
                    productUnit = "箱",
                    productImageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuBdFCF-yy4lksCptKia_-DosSTm-5tCEtrdMnJJXN5KzYAVskeGe_rJQEiiXa7e2wzeZA171618LP3v4jPamqAc4nrpHTLNXekHcluAoBEyW-RWjOmjTMsf0SpjG0ndfryisIalCOn9zkbW4EDOTsZ6qpoqTICf-Z40WhPgLknmFSFyDizlzSFd1q2w4MT3RUTZn57S54fYIWwBGQaHGGN0RkwLsvEbX1qUOfXJsBiAGYbqVb28JHzUFK4z9G1bvNdOBOfmlPLPaUc",
                    requestedQty = 4.0,
                    confirmedQty = 4.0,
                    deliveredQty = 4.0,
                    price = 120.0
                )
            )
            supplyDao.insertOrderItems(orderItems)
        }
    }

    private fun ProductEntity.withComputedSupplyStatus(): ProductEntity {
        val stock = stockQuantity.toBigDecimalOrNullCompat()
        val warning = warningQuantity.toBigDecimalOrNullCompat()
        val computedStatus = when {
            isDeleted -> "已下架"
            !isAvailable -> "已下架"
            stock != null && warning != null && stock < warning -> "库存紧张"
            status == "暂停供应" -> "暂停供应"
            else -> "正常供应"
        }
        return copy(
            status = computedStatus,
            stockStatus = if (computedStatus == "库存紧张") "紧张" else if (stock == BigDecimal.ZERO) "已售罄" else "充足",
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun String.toBigDecimalOrNullCompat(): BigDecimal? = runCatching {
        if (isBlank()) null else BigDecimal(trim())
    }.getOrNull()
}
