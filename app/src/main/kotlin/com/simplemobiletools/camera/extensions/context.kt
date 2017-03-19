package com.simplemobiletools.camera.extensions

import android.content.Context
import com.simplemobiletools.camera.Config

val Context.config: Config get() = Config.newInstance(this)
