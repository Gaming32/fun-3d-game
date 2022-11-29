package io.github.gaming32.fungame.entity

import io.github.gaming32.fungame.Level
import io.github.gaming32.fungame.loader.LevelLoader
import io.github.gaming32.fungame.model.CollisionModel
import io.github.gaming32.fungame.model.CollisionType
import org.ode4j.math.DVector3C
import org.ode4j.ode.DContact
import org.ode4j.ode.DContactGeom
import org.ode4j.ode.DGeom
import org.ode4j.ode.OdeHelper

class LevelMeshEntity(
    level: Level, position: DVector3C, model: CollisionModel
) : Entity<LevelMeshEntity>(LevelMeshEntity, level, position) {
    companion object Type : EntityType<LevelMeshEntity>() {
        override fun create(level: Level, position: DVector3C, args: List<String>, loader: LevelLoader) =
            LevelMeshEntity(
                level, position,
                loader.loadCollision(loader.loadObj(args[0]), args[1])
            )
    }

    init {
        body.setKinematic()
    }

    private val collisionMeshes: Map<DGeom, CollisionType> =
        model.toMultiTriMeshData().entries.associate { (collision, mesh) ->
            val geom = OdeHelper.createTriMesh(
                level.space, mesh,
                { _, _, _ -> 1 },
                { _, _, _, _ -> },
                { _, _, _, _, _ -> 1 }
            )
            addGeom(geom)
            geom to collision
        }
    private val drawList = model.model.toDisplayList()

    override fun draw() = drawList.draw()

    override fun destroy() {
        super.destroy()
        drawList.close()
    }

    override fun collideWithEntity(
        other: Entity<*>,
        contact: DContactGeom,
        selfIsG1: Boolean
    ): DContact.DSurfaceParameters? = other.collideWithMesh(
        collisionMeshes.getValue(if (selfIsG1) contact.g1 else contact.g2),
        contact, !selfIsG1
    )
}