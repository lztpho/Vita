// SPDX-License-Identifier: Apache-2.0
package io.github.lztpho.vita.nativebridge

import org.junit.Assert.assertThrows
import org.junit.Test

class HttpResponsePolicyTest {
    @Test fun allRedirectsAreRejected() {
        listOf(300, 301, 302, 303, 307, 308, 399).forEach { code ->
            assertThrows(IllegalArgumentException::class.java) { HttpResponsePolicy.rejectRedirect(code) }
        }
    }

    @Test fun normalStatusesAreNotClassifiedAsRedirects() {
        HttpResponsePolicy.rejectRedirect(200)
        HttpResponsePolicy.rejectRedirect(401)
    }
}
