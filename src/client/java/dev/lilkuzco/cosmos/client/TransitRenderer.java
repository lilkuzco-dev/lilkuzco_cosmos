package dev.lilkuzco.cosmos.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lilkuzco.cosmos.Cosmos;
import dev.lilkuzco.cosmos.moon.TransitEntity;
import net.minecraft.client.model.Model;
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
 * Draws the transfer vehicle the crew ride to the Moon.
 *
 * <p>The capsule's hull, without the parachute — there is nothing to open a canopy against on a
 * trans-lunar coast, and a chute drawn out here would be a lie about what is holding you up.
 *
 * <p>Four minutes is a long time to spend riding something invisible.
 */
public class TransitRenderer extends EntityRenderer<TransitEntity, EntityRenderState> {

	private static final Identifier TEXTURE = Cosmos.id("textures/entity/capsule.png");

	private final Model.Simple model;

	public TransitRenderer(EntityRendererProvider.Context context) {
		super(context);
		net.minecraft.client.model.geom.ModelPart root = context.bakeLayer(CapsuleModel.LAYER);
		root.getChild(CapsuleModel.HULL).getChild(CapsuleModel.CANOPY).visible = false;
		this.model = new Model.Simple(root, RenderTypes::entitySolid);
		this.shadowRadius = 0.0F;   // nothing to cast a shadow on, 120 km up
	}

	@Override
	public EntityRenderState createRenderState() {
		return new EntityRenderState();
	}

	@Override
	public void submit(EntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
	                   CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.scale(1.4F, 1.4F, 1.4F);
		collector.submitModel(model, Unit.INSTANCE, poseStack, RenderTypes.entitySolid(TEXTURE),
				state.lightCoords, OverlayTexture.NO_OVERLAY, -1, null);
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}
}
