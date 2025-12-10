/*
 * Copyright 2025 Phil Burk
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.softsynth.ksyn

// IMPORTS: These look like Android, but they are the Multiplatform versions
// provided by the JetBrains Compose Multiplatform Gradle plugin.
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

// IMPORTS: Voyager (Multiplatform Navigation)
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow

// 1. The Home Screen
class HomeScreen : Screen {

    @Composable
    override fun Content() {
        // LocalNavigator is a "CompositionLocal" provided by Voyager.
        // It works on iOS, Web, Desktop, and Android equally.
        val navigator = LocalNavigator.currentOrThrow

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Button(
                onClick = {
                    // Push the new screen onto the stack
                    navigator.push(TestAudioBridge())
                }
            ) {
                Text("Go to Audio Bridge")
            }
        }
    }
}
