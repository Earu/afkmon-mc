package gg.earu.afk.client.render

import org.joml.Matrix4f

/**
 * Where each player's model was actually drawn this frame, in view space, keyed by entity id.
 *
 * Physics mods (Create Aeronautics' Sable sub-levels, Valkyrien Skies) draw players with an extra
 * contraption transform the entity itself knows nothing about, so a halo placed from the entity's
 * position and world-up sits at the wrong tilt on a 45 degree deck. Capturing the pose at render
 * time keeps the halo glued to whatever the player is standing on; players that were not rendered
 * this frame fall back to the old entity-position path.
 *
 * Render thread only, like everything else in this package.
 */
object PlayerRenderPose {

    private var poses = HashMap<Int, Matrix4f>()

    @JvmStatic
    fun record(entityId: Int, pose: Matrix4f) {
        poses[entityId] = pose
    }

    /** Hands over this frame's captures and forgets them, so stale poses never outlive a frame. */
    fun drain(): Map<Int, Matrix4f> {
        val out = poses
        poses = HashMap()
        return out
    }
}
