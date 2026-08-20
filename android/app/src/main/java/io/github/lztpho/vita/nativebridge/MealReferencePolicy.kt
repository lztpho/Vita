// SPDX-License-Identifier: Apache-2.0
package io.github.lztpho.vita.nativebridge

internal object MealReferencePolicy {
    fun resolveImageSource(source: MealEntity, lookup: (String) -> MealEntity?): MealEntity {
        var current = source
        val visited = mutableSetOf<String>()
        repeat(16) {
            require(visited.add(current.id)) { "历史餐食图片引用形成循环" }
            if (current.thumbnailCount > 0) return current
            val parentId = current.sourceMealId?.takeIf(String::isNotBlank)
                ?: throw IllegalArgumentException("历史餐食没有可复用的图片")
            current = lookup(parentId) ?: throw IllegalArgumentException("历史餐食的原始图片已经不存在")
        }
        throw IllegalArgumentException("历史餐食图片引用层级过深")
    }
}
