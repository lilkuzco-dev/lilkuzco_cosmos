package dev.lilkuzco.cosmos.client;

import dev.lilkuzco.cosmos.Cosmos;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * The launch vehicle's geometry: body, nose, engine bell, four fins.
 *
 * <p>Built in code rather than as a JSON model because the vehicle has to be sized per tier — a
 * sounding rocket and a 179-tonne lunar vehicle are the same silhouette at very different scales —
 * and because the bell needs to be a separate part so a plume can be anchored to it.
 *
 * <p><b>Positive y is up here, and that is deliberate.</b> {@code submitModel} applies the 1/16
 * model-space scale itself and does <em>not</em> apply the vertical flip an entity renderer
 * traditionally does by hand. crude_empire's Phase 2 paid for this lesson: a model built to the old
 * flipped convention rendered underground, and one that also applied the 1/16 scale manually
 * rendered as an eight-pixel smudge. Neither is done here.
 *
 * <p>The texture is this model's <em>own</em> 64x64 sheet under {@code textures/entity/}, not a
 * sprite on the block atlas — the same lesson, second half.
 */
public final class RocketModel {

	public static final ModelLayerLocation LAYER =
			new ModelLayerLocation(Cosmos.id("rocket"), "main");

	public static final String BODY = "body";
	public static final String NOSE = "nose";
	public static final String BELL = "bell";

	/** Model-space height of the vehicle, in pixels. 16 to a block, so this is four blocks. */
	public static final float HEIGHT = 64.0F;

	private RocketModel() {
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

		// The stack. 8x8 in cross-section rather than 6x6: at six it read as a needle from any
		// distance, which is what the first render battery showed.
		PartDefinition body = root.addOrReplaceChild(BODY,
				CubeListBuilder.create()
						.texOffs(0, 0).addBox(-4.0F, 4.0F, -4.0F, 8, 40, 8)
						// Interstage band, so a multi-stage vehicle reads as one.
						.texOffs(0, 48).addBox(-4.5F, 21.0F, -4.5F, 9, 3, 9),
				PartPose.ZERO);

		// Nose: a shoulder and a tip. As close to a cone as cubes get without a staircase.
		body.addOrReplaceChild(NOSE,
				CubeListBuilder.create()
						.texOffs(36, 48).addBox(-2.5F, 44.0F, -2.5F, 5, 7, 5)
						.texOffs(32, 0).addBox(-1.0F, 51.0F, -1.0F, 2, 5, 2),
				PartPose.ZERO);

		// The engine bell, its own part so a plume has something to hang from.
		body.addOrReplaceChild(BELL,
				CubeListBuilder.create()
						.texOffs(32, 8).addBox(-2.5F, 0.0F, -2.5F, 5, 4, 5),
				PartPose.ZERO);

		// Four fins at the base — the thing that makes a rocket read as a rocket in silhouette.
		for (int i = 0; i < 4; i++) {
			float angle = (float) (i * Math.PI / 2.0);
			root.addOrReplaceChild("fin_" + i,
					CubeListBuilder.create()
							.texOffs(32, 20).addBox(-0.5F, 4.0F, 3.5F, 1, 12, 6),
					PartPose.rotation(0.0F, angle, 0.0F));
		}

		return LayerDefinition.create(mesh, 64, 64);
	}
}
