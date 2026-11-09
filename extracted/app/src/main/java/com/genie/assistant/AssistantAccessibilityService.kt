package com.genie.assistant

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AssistantAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: AssistantAccessibilityService? = null
    }

    private var nodes: List<AccessibilityNodeInfo> = emptyList()

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    /** يرجع وصف نصي للشاشة الحالية، كل عنصر له رقم. */
    fun dump(): String {
        val root = rootInActiveWindow ?: return "(الشاشة غير متاحة الآن)"
        val list = ArrayList<AccessibilityNodeInfo>()
        val sb = StringBuilder("التطبيق الحالي: ${root.packageName}\n")
        walk(root, list, sb)
        nodes = list
        return sb.toString().take(6000)
    }

    private fun walk(n: AccessibilityNodeInfo?, list: MutableList<AccessibilityNodeInfo>, sb: StringBuilder) {
        if (n == null || list.size >= 150) return
        if (n.isVisibleToUser) {
            val text = (n.text?.toString() ?: n.contentDescription?.toString() ?: "")
                .replace("\n", " ").take(80)
            if (text.isNotEmpty() || n.isClickable || n.isEditable) {
                list.add(n)
                sb.append("[").append(list.size - 1).append("] ").append(text)
                if (n.isEditable) sb.append(" (حقل نص)")
                else if (n.isClickable) sb.append(" (قابل للضغط)")
                sb.append("\n")
            }
        }
        for (i in 0 until n.childCount) walk(n.getChild(i), list, sb)
    }

    fun clickNode(id: Int): Boolean {
        var c: AccessibilityNodeInfo? = nodes.getOrNull(id) ?: return false
        while (c != null && !c.isClickable) c = c.parent
        return c?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
    }

    fun typeText(text: String): Boolean {
        val root = rootInActiveWindow
        val target = root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: nodes.firstOrNull { it.isEditable }
            ?: return false
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun findScrollable(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (n == null) return null
        if (n.isScrollable) return n
        for (i in 0 until n.childCount) {
            val r = findScrollable(n.getChild(i))
            if (r != null) return r
        }
        return null
    }

    fun scroll(down: Boolean): Boolean {
        val s = findScrollable(rootInActiveWindow) ?: return false
        return s.performAction(
            if (down) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        )
    }
}
