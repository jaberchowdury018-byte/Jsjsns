package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AccessibilityHelperService : AccessibilityService() {

    companion object {
        var instance: AccessibilityHelperService? = null
            private set

        fun isEnabled(context: Context): Boolean {
            val expectedComponentName = "${context.packageName}/${AccessibilityHelperService::class.java.name}"
            val enabledServicesSetting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServicesSetting)
            while (colonSplitter.hasNext()) {
                val componentName = colonSplitter.next()
                if (componentName.equals(expectedComponentName, ignoreCase = true)) {
                    return true
                }
            }
            return false
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No-op
    }

    override fun onInterrupt() {
        // No-op
    }

    fun closeCurrentApp() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    fun goBack() {
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun clickOnText(text: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val nodes = rootNode.findAccessibilityNodeInfosByText(text)
        if (nodes != null) {
            for (node in nodes) {
                if (node.isClickable) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return true
                } else {
                    var parent = node.parent
                    while (parent != null) {
                        if (parent.isClickable) {
                            parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            return true
                        }
                        parent = parent.parent
                    }
                }
            }
        }
        return false
    }

    fun typeText(text: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val focusNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focusNode != null) {
            val arguments = Bundle()
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            focusNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            return true
        }
        return false
    }

    fun scrollDown(): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        return scrollNode(rootNode, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    fun scrollUp(): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        return scrollNode(rootNode, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
    }

    private fun scrollNode(node: AccessibilityNodeInfo, action: Int): Boolean {
        if (node.isScrollable) {
            node.performAction(action)
            return true
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                if (scrollNode(child, action)) {
                    return true
                }
            }
        }
        return false
    }
}
