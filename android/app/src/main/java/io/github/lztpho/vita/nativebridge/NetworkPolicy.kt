// SPDX-License-Identifier: Apache-2.0
package io.github.lztpho.vita.nativebridge

import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException

object NetworkPolicy {
    fun validate(raw: String): HttpUrl {
        val url = raw.trim().toHttpUrlOrNull() ?: throw IllegalArgumentException("API 地址格式无效")
        require(url.username.isEmpty() && url.password.isEmpty()) { "API 地址不能包含用户名或密码" }
        require(url.scheme == "https") { "API 地址必须使用 HTTPS" }
        require(url.host.isNotBlank()) { "API 地址缺少主机名" }
        require(url.query == null && url.fragment == null) { "API 基础地址不能包含查询参数或片段" }
        return url
    }

    fun validateForRequest(raw: String, delegate: Dns = Dns.SYSTEM): HttpUrl {
        val url = validate(raw)
        publicDns(delegate).lookup(url.host)
        return url
    }

    /** Resolves every call and fails closed if any answer is not globally routable. */
    fun publicDns(delegate: Dns = Dns.SYSTEM): Dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val addresses = delegate.lookup(hostname)
            if (addresses.isEmpty()) throw UnknownHostException(hostname)
            if (addresses.any { !isPublic(it) }) throw UnknownHostException("API 主机解析到了私有、保留或本地地址")
            return addresses
        }
    }

    fun isPublic(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress) return false
        val bytes = address.address.map { it.toInt() and 0xff }
        if (address is Inet4Address) {
            val a = bytes[0]
            val b = bytes[1]
            return !(a == 0 || a == 10 || a == 127 ||
                (a == 100 && b in 64..127) ||
                (a == 169 && b == 254) ||
                (a == 172 && b in 16..31) ||
                (a == 192 && b == 0 && bytes[2] in listOf(0, 2)) ||
                (a == 192 && b == 168) ||
                (a == 192 && b == 88 && bytes[2] == 99) ||
                (a == 198 && b in 18..19) ||
                (a == 198 && b == 51 && bytes[2] == 100) ||
                (a == 203 && b == 0 && bytes[2] == 113) ||
                a >= 224)
        }
        if (address is Inet6Address) {
            val prefix16 = (bytes[0] shl 8) or bytes[1]
            if ((bytes[0] and 0xfe) == 0xfc || (prefix16 and 0xffc0) == 0xfe80 ||
                (prefix16 and 0xffc0) == 0xfec0 || bytes[0] == 0xff) return false
            if (bytes[0] == 0x20 && bytes[1] == 0x01 && bytes[2] == 0x0d && bytes[3] == 0xb8) return false
            if (bytes[0] == 0x01 && bytes.drop(1).take(7).all { it == 0 }) return false
            if (bytes.take(10).all { it == 0 } && bytes[10] == 0xff && bytes[11] == 0xff) {
                return isPublic(InetAddress.getByAddress(bytes.drop(12).take(4).map(Int::toByte).toByteArray()))
            }
            return true
        }
        return false
    }
}
