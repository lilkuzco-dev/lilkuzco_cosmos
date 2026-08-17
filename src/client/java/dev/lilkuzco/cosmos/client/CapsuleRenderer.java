package dev.lilkuzco.cosmos.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lilkuzco.cosmos.Cosmos;
import dev.lilkuzco.cosmos.recovery.CapsuleEntity;
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
 * Draws the recovery capsule, its parachute, and the fact that it is on fire.
 *
 * <p>Everything this needs was already being synced to the client and thrown away: {@code GLOW}
 * tracks reentry heating and {@code CHUTE} tracks canopy inflation, and both existed for a renderer
 * that drew nothing. The particles were the only visible sign a capsule was coming down at all.
 *
 * <p>Heating is shown as a tint rather than a separate glow layer, because the tint rides the same
 * submit and cannot desynchronise from the model.
 */
public class CapsuleRenderer extends EntityRenderer<CapsuleEntity, CapsuleRenderer.State> {

	private static final Identifier TEXTURE = Cosmos.id("textures/entity/capsule.png");

	private final Model.Simple model;
	private final net.minecraft.client.model.geom.ModelPart canopy;

	public CapsuleRenderer(EntityRendererProvider.Context context) {
		super(context);
		net.minecraft.client.model.geom.ModelPart root = context.bakeLayer(CapsuleModel.LAYER);
		this.model = new Model.Simple(root, RenderTypes::entitySolid);
		this.canopy = root.getChild(CapsuleModel.HULL).getChild(CapsuleModel.CANOPY);
		this.shadowRadius = 0.6F;
	}

	public static class State extends EntityRenderState {
		public boolean chute;
		public float glow;
		public float sway;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(CapsuleEntity capsule, State state, float partialTick) {
		super.extractRenderState(capsule, state, partialTick);
		state.chute = capsule.chuteOut();
		state.glow = capsule.glow();
		// A slow pendulum under the canopy. A capsule that hangs perfectly still reads as a
		// dropped block rather than something on a parachute.
		state.sway = state.chute
				? (float) Math.sin((capsule.tickCount + partialTick) * 0.08) * 7.0F : 0.0F;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector,
	                   CameraRenderState camera) {
		RenderType renderType = RenderTypes.entitySolid(TEXTURE);

		// Reentry heating, as a tint from white through orange to white-hot.
		int tint = -1;
		if (state.glow > 0.01F) {
			float g = Math.min(1.0F, state.glow);
			int red = 255;
			int green = (int) (255 - 150 * g);
			int blue = (int) (255 - 220 * g);
			tint = 0xFF000000 | (red << 16) | (green << 8) | blue;
		}

		poseStack.pushPose();
		poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(state.sway));
		canopy.visible = state.chute;
		collector.submitModel(model, Unit.INSTANCE, poseStack, renderType, state.lightCoords,
				OverlayTexture.NO_OVERLAY, tint, null);
		poseStack.popPose();

		super.submit(state, poseStack, collector, camera);
	}
}
