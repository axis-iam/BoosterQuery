package com.chaosguide.jpa.booster.support

import jakarta.persistence.Tuple
import jakarta.persistence.TupleElement
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Kotlin data class 映射集成测试。
 *
 * 验证 JpaResultMapper 对无默认构造器的 Kotlin data class 的映射支持，
 * 通过 `-java-parameters` 编译标志解析构造器参数名。
 */
class KotlinDataClassMappingTest {

    // ---- 测试用 data class ----

    data class UserDto(val name: String, val email: String)

    data class UnderscoreDto(val userName: String, val emailAddress: String)

    data class TypeConvertDto(val id: Long, val name: String)

    data class PrimitiveDto(val id: Long, val count: Int, val active: Boolean, val score: Double)

    data class NullableDto(val name: String?, val email: String?)

    // ---- Tuple mock 辅助 ----

    @Suppress("UNCHECKED_CAST")
    private fun mockElement(alias: String): TupleElement<Any> {
        val el = mock(TupleElement::class.java) as TupleElement<Any>
        `when`(el.alias).thenReturn(alias)
        return el
    }

    private fun mockTuple(data: Map<String, Any?>): Tuple {
        val tuple = mock(Tuple::class.java)
        val elements = data.keys.map { mockElement(it) }
        `when`(tuple.elements).thenReturn(elements)
        for ((key, value) in data) {
            `when`(tuple.get(key)).thenReturn(value)
        }
        return tuple
    }

    // ---- 正向路径 ----

    @Test
    @DisplayName("Kotlin data class — 基本字段映射")
    fun basicFieldMapping() {
        val tuple = mockTuple(mapOf("name" to "Alice", "email" to "a@b.com"))
        val result = JpaResultMapper.map(tuple, UserDto::class.java)
        assertThat(result.name).isEqualTo("Alice")
        assertThat(result.email).isEqualTo("a@b.com")
    }

    @Test
    @DisplayName("Kotlin data class — 下划线 alias 转驼峰")
    fun underscoreAliasToCamelCase() {
        val tuple = mockTuple(mapOf("user_name" to "alice", "email_address" to "a@b.com"))
        val result = JpaResultMapper.map(tuple, UnderscoreDto::class.java)
        assertThat(result.userName).isEqualTo("alice")
        assertThat(result.emailAddress).isEqualTo("a@b.com")
    }

    @Test
    @DisplayName("Kotlin data class — 类型转换 Integer→Long")
    fun typeConversion() {
        val tuple = mockTuple(mapOf("id" to 42, "name" to "Alice"))
        val result = JpaResultMapper.map(tuple, TypeConvertDto::class.java)
        assertThat(result.id).isEqualTo(42L)
        assertThat(result.name).isEqualTo("Alice")
    }

    @Test
    @DisplayName("Kotlin data class — List 映射")
    fun listMapping() {
        val t1 = mockTuple(mapOf("name" to "Alice", "email" to "a@b"))
        val t2 = mockTuple(mapOf("name" to "Bob", "email" to "b@b"))
        val result = JpaResultMapper.map(listOf(t1, t2), UserDto::class.java)
        assertThat(result).hasSize(2)
        assertThat(result[0].name).isEqualTo("Alice")
        assertThat(result[1].name).isEqualTo("Bob")
    }

    // ---- 边界条件 ----

    @Test
    @DisplayName("Kotlin data class — 基本类型参数缺失使用默认零值")
    fun primitiveDefaults() {
        val tuple = mockTuple(emptyMap())
        val result = JpaResultMapper.map(tuple, PrimitiveDto::class.java)
        assertThat(result.id).isEqualTo(0L)
        assertThat(result.count).isEqualTo(0)
        assertThat(result.active).isFalse()
        assertThat(result.score).isEqualTo(0.0)
    }

    @Test
    @DisplayName("Kotlin data class — 可空类型参数缺失返回 null")
    fun nullableFieldsMissing() {
        val tuple = mockTuple(emptyMap())
        val result = JpaResultMapper.map(tuple, NullableDto::class.java)
        assertThat(result.name).isNull()
        assertThat(result.email).isNull()
    }

    @Test
    @DisplayName("Kotlin data class — 多余 Tuple 列静默忽略")
    fun extraColumnsIgnored() {
        val tuple = mockTuple(mapOf("name" to "Alice", "email" to "a@b", "extra" to "ignored"))
        val result = JpaResultMapper.map(tuple, UserDto::class.java)
        assertThat(result.name).isEqualTo("Alice")
        assertThat(result.email).isEqualTo("a@b")
    }

    @Test
    @DisplayName("Kotlin data class — 基本类型部分有值部分缺失")
    fun primitiveMixed() {
        val tuple = mockTuple(mapOf("id" to 99, "active" to true))
        val result = JpaResultMapper.map(tuple, PrimitiveDto::class.java)
        assertThat(result.id).isEqualTo(99L)
        assertThat(result.count).isEqualTo(0)
        assertThat(result.active).isTrue()
        assertThat(result.score).isEqualTo(0.0)
    }
}
