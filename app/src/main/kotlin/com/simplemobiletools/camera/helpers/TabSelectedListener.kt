package com.simplemobiletools.camera.helpers

import com.google.android.material.tabs.TabLayout

interface TabSelectedListener : TabLayout.OnTabSelectedListener {
    override fun onTabReselected(tab: TabLayout.Tab?) {}

    override fun onTabUnselected(tab: TabLayout.Tab?) {}
}
