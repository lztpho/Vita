// SPDX-License-Identifier: Apache-2.0
package io.github.lztpho.vita

import android.os.Bundle
import com.getcapacitor.BridgeActivity
import io.github.lztpho.vita.nativebridge.VitaPlugin

class MainActivity : BridgeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        registerPlugin(VitaPlugin::class.java)
        super.onCreate(savedInstanceState)
    }
}
