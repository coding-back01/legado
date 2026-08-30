package io.legado.app.release

/**
 * 只在可丢弃的 Debug 模拟器数据空间运行的发布烟测标记。
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ReleaseSmoke
