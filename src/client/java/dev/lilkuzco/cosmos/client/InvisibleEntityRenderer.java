package dev.lilkuzco.cosmos.client;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;

/**
 * A renderer that draws nothing.
 *
 * <p>Phase A deliberately ships no rocket or capsule model — what a player sees is the server's
 * flame and smoke plume, the countdown, and the camera shake. That decision is sound. What is not
 * optional is <em>registering a renderer at all</em>: {@code EntityRenderDispatcher.getRenderer}
 * returns null for an unregistered type, and {@code LevelExtractor.isEntityVisible} dereferences
 * that null on the render thread the moment the entity enters view. The result is a hard client
 * crash on ignition — not an invisible rocket.
 *
 * <p>So this is the honest expression of "no model yet": a real renderer that renders nothing.
 * When Phase B brings a model layer, replace the registration in {@link CosmosClient}; nothing
 * else needs to change.
 *
 * <p>{@code createRenderState} is the only abstract member on {@link EntityRenderer} in 26.2, and
 * the inherited {@code submit} draws only the nametag, which these entities do not have. Shadow
 * radius stays at its default of zero, so the vehicle casts nothing either.
 */
public final class InvisibleEntityRenderer<T extends Entity> extends EntityRenderer<T, EntityRenderState> {

	public InvisibleEntityRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public EntityRenderState createRenderState() {
		return new EntityRenderState();
	}
}
