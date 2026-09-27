// MapLibre's license notices for the 출처 · 라이선스 screen, copied unchanged from the official sources:
//   MapLibre Native   each platform's own file, with the notices of the code it bundles:
//                     Android  MapLibreNativeNotices.android.kt (libmaplibre.so)
//                     iOS      MapLibreNativeNotices.ios.kt (the MapLibre framework from Swift Package Manager)
//   MapLibre Compose  https://raw.githubusercontent.com/maplibre/maplibre-compose/v0.12.1/LICENSE
//                     (sha256 da90527705e69ff478e12335fea885af162f267bbfb5a265174608ed7840e377)
package com.coffeejournal.ui.about

internal object MapLibreNotices {
    /** Both files in full, each under a heading naming its source. */
    val text: String
        get() = mapLibreNativeNotices + "\n\nMapLibre Compose 0.12.1 — LICENSE\n\n" + COMPOSE

    private const val COMPOSE: String = """Copyright (c) 2024, MapLibre Compose contributors
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright notice, this
  list of conditions and the following disclaimer.

* Redistributions in binary form must reproduce the above copyright notice,
  this list of conditions and the following disclaimer in the documentation
  and/or other materials provided with the distribution.

* Neither the name of [project] nor the names of its
  contributors may be used to endorse or promote products derived from
  this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
"""
}

/** This platform's MapLibre Native license file in full, under a heading naming it and its version. */
internal expect val mapLibreNativeNotices: String
