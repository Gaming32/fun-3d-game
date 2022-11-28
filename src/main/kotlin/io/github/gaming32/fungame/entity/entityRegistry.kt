package io.github.gaming32.fungame.entity

import io.github.gaming32.fungame.Level

object EntityRegistry {
    private val entityTypes = mutableMapOf<String, EntityType<*>>()

    fun register(id: String, type: EntityType<*>) {
        entityTypes[id] = type
    }

    fun getType(id: String) = entityTypes.getValue(id)

    init {
        register("player", PlayerEntity)
    }
}

abstract class EntityType<T : Entity<T>> {
    abstract fun create(level: Level, args: List<String>): T
}
