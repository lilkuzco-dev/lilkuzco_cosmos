package dev.lilkuzco.cosmos.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lilkuzco.cosmos.Cosmos;
import dev.lilkuzco.cosmos.rocket.RocketEntity;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;

/**
 * Draws the rocket.
 *
 * <p>This replaces {@code InvisibleEntityRenderer} for the launch vehicle, and the history is
 * worth stating. Phase A registered <em>no</em> renderer at all, so launching a rocket dereferenced
 * a null on the render thread and hard-crashed the client. The fix was a real renderer that drew
 * nothing — correct as a crash fix, and it left the rocket invisible, which is what it was always
 * going to do. "No model yet" and "a model" are different jobs and only the first was done.
 *
 * <p>26.2 splits rendering in two: {@link #extractRenderState} reads the entity, {@link #submit}
 * draws with no access to the world. Everything the draw needs is copied across in between.
 */
public class RocketRenderer extends EntityRenderer<RocketEntity, RocketRenderer.State> {

	private static final Identifier TEXTURE = Cosmos.id("textures/entity/rocket.png");

	private final Model.Simple model;
	private final ModelPart root;

	public RocketRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.root = context.bakeLayer(RocketModel.LAYER);
		this.model = new Model.Simple(root, RenderTypes::entitySolid);
		this.shadowRadius = 0.8F;
	}

	/** Everything the draw needs, copied off the entity while it is still legal to read it. */
	public static class State extends EntityRenderState {
		public float scale = 1.0F;
		public float spin;
		public boolean burning;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(RocketEntity rocket, State state, float partialTick) {
		super.extractRenderState(rocket, state, partialTick);
		// Tier decides size. A sounding rocket and the lunar vehicle share this silhouette and
		// nothing else, so the scale is synced rather than guessed from the entity's dimensions.
		state.scale = rocket.renderScale();
		state.burning = rocket.throttle() > 0.0F;
		// A slow roll. Real vehicles roll on ascent, and a perfectly rigid object against a
		// featureless sky reads as a texture rather than a thing that is moving.
		state.spin = (rocket.tickCount + partialTick) * 1.5F;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector,
	                   CameraRenderState camera) {
		RenderType renderType = RenderTypes.entitySolid(TEXTURE);

		poseStack.pushPose();
		poseStack.scale(state.scale, state.scale, state.scale);
		poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(state.spin));
		// No manual 1/16 scale and no vertical flip: submitModel applies the model-space
		// transform itself, and the mesh is built positive-y-up to match.
		collector.submitModel(model, Unit.INSTANCE, poseStack, renderType, state.lightCoords,
				OverlayTexture.NO_OVERLAY, -1, null);
		poseStack.popPose();

		super.submit(state, poseStack, collector, camera);
	}
}
