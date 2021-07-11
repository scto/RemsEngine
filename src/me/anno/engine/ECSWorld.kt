package me.anno.engine

import com.bulletphysics.collision.dispatch.CollisionWorld
import me.anno.ecs.Entity

class ECSWorld : Entity() {

    // will be shared over the network, and available to all clients
    // should be written only to by the server, or for particle effects
    val globallyShared = Entity("Globally Shared")

    /**
     * only enabled in scene editor
     * the base for all players
     * */
    val playerPrefab = Entity("Player Prefab")

    /**
     * a list of players,
     * can be created using the playerPrefab
     * players on this computer
     * */
    val localPlayers = Entity("Local Players")

    /**
     * e.g. for custom ui layouts,
     * will not be shared with the server
     * */
    val locallyShared = Entity("Locally Shared")

    /**
     * a list of players,
     * remote players, will be controlled by the server connection
     * if the server is local, it may be controlled by the remotely playing
     * */
    val remotePlayers = Entity("Remote Players")

    init {
        addChild(globallyShared)
        addChild(playerPrefab)
        addChild(locallyShared)
        addChild(localPlayers)
        addChild(remotePlayers)
    }

    fun createLocalPlayer(name: String): Entity {
        val instance = playerPrefab.clone()
        instance.name = name
        instance.isEnabled = true
        localPlayers.add(instance)
        return instance
    }

    fun createRemotePlayer(name: String): Entity {
        val instance = playerPrefab.clone()
        instance.name = name
        instance.isEnabled = true
        remotePlayers.add(instance)
        return instance
    }

    fun removePlayer(player: Entity) {
        localPlayers.remove(player)
        remotePlayers.remove(player)
    }

    var physics: CollisionWorld? = null

    override val className get() = "RootEntity"

}