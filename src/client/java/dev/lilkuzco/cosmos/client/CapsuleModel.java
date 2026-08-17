package dev.lilkuzco.cosmos.client;

import dev.lilkuzco.cosmos.Cosmos;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * The recovery capsule: a blunt cone under a parachute.
 *
 * <p>Blunt on purpose. A capsule's shape is not styling — a high-drag blunt body is what puts the
 * shock wave out in front and keeps the heat off the hull, and it is the reason cosmos' capsule
 * profile carries a ballistic coefficient of 26 kg/m². The silhouette should say that.
 *
 * <p>The canopy is a separate part so the renderer can hide it until it inflates. It is enormous
 * relative to the capsule for the same reason the profile's chute area is: this world's atmosphere
 * carries a 155th of Earth's column mass, so recovery hardware here is necessarily large.
 *
 * <p>Positive y up, per {@code submitModel}'s convention.
 */
public final class CapsuleModel {

	public static final ModelLayerLocation LAYER =
			new ModelLayerLocation(Cosmos.id("capsule"), "main");

	public static final String HULL = "hull";
	public static final String CANOPY = "canopy";

	private CapsuleModel() {
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

		PartDefinition hull = root.addOrReplaceChild(HULL,
				CubeListBuilder.create()
						.texOffs(0, 0).addBox(-5.0F, 0.0F, -5.0F, 10, 3, 10)
						.texOffs(0, 13).addBox(-4.0F, 3.0F, -4.0F, 8, 5, 8)
						.texOffs(0, 26).addBox(-3.0F, 8.0F, -3.0F, 6, 2, 6),
				PartPose.ZERO);

		// The canopy: 12x4x12, NOT 16x4x16.
		//
		// A box 16 wide and 16 deep unwraps to 2*(16+16) = 64 pixels of UV width - the entire
		// width of a 64x64 sheet, with not one pixel of margin. That box never produced geometry
		// in any arrangement: its own layer, its own identifier, cubes at the origin, cubes
		// offset, submitted alone, submitted at a different order, and against a texture painted
		// magenta to prove the UVs were not the problem. At 12x12 the unwrap is 48 wide and it
		// draws. Leave it room.
		// A CHILD OF THE HULL, not a sibling of it.
		//
		// The working reference model in the empire - crude_empire's pumpjack - nests everything
		// under a single child of root, and every arrangement here that made the canopy a SECOND
		// child of root drew the hull and silently dropped the canopy. Nesting is the shape that
		// draws.
		PartDefinition canopy = hull.addOrReplaceChild(CANOPY,
				CubeListBuilder.create()
						.texOffs(0, 34).addBox(-6.0F, 22.0F, -6.0F, 12, 4, 12),
				PartPose.ZERO);

		// Risers, nested under the canopy so they inherit its visibility - a canopy floating free
		// of the capsule reads as a UFO rather than a parachute.
		for (int i = 0; i < 4; i++) {
			float angle = (float) (i * Math.PI / 2.0 + Math.PI / 4.0);
			canopy.addOrReplaceChild("riser_" + i,
					CubeListBuilder.create()
							.texOffs(0, 54).addBox(-0.5F, 10.0F, 3.5F, 1, 12, 1),
					PartPose.rotation(0.0F, angle, 0.0F));
		}

		return LayerDefinition.create(mesh, 64, 64);
	}
}
