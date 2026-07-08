package com.smartprocurement.internal

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WorkflowTutorialPolicyTest {
    private val root = File(System.getProperty("user.dir"))

    @Test
    fun help_tutorial_uses_role_specific_workflow_assets_and_steps() {
        val extraScreens = File(root, "src/main/java/com/example/ui/ExtraScreens.kt").readText()

        assertTrue(File(root, "src/main/res/drawable-nodpi/workflow_admin_tutorial.png").exists())
        assertTrue(File(root, "src/main/res/drawable-nodpi/workflow_unit_tutorial.png").exists())
        assertTrue(extraScreens.contains("管理员常用流程"))
        assertTrue(extraScreens.contains("子单位常用流程"))
        assertTrue(extraScreens.contains("查看完整流程图"))
        assertTrue(extraScreens.contains("workflow_admin_tutorial"))
        assertTrue(extraScreens.contains("workflow_unit_tutorial"))

        listOf("完善单位", "创建账号", "维护食材", "处理订单", "备货配送", "检查系统").forEach {
            assertTrue("missing admin workflow step: $it", extraScreens.contains(it))
        }
        listOf("账号登录", "浏览食材", "加入清单", "提交订单", "确认收货", "异常说明").forEach {
            assertTrue("missing unit workflow step: $it", extraScreens.contains(it))
        }
    }
}
