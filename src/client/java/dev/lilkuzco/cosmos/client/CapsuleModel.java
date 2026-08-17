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

	/** The hull. Its own layer, so it can be submitted without the canopy. */
	public static final ModelLayerLocation LAYER =
			new ModelLayerLocation(Cosmos.id("capsule"), "main");

	/**
	 * The parachute, as a SEPARATE baked layer.
	 *
	 * <p>Not a part of the hull layer toggled with {@code visible}. A {@link ModelPart} is shared
	 * mutable state owned by the bake, so flipping a flag from inside a draw races every other
	 * capsule in the world — and it did not put a canopy on screen. Splitting by
	 * {@code getChild} instead did not work either: {@code Model.Simple} wants a root, and handing
	 * it a child rendered nothing at all, which is how the capsule went from visible back to
	 * invisible between two runs of the render battery.
	 *
	 * <p>Two layers, two roots, no shared state, no flags.
	 */
	public static final ModelLayerLocation CANOPY_LAYER =
			new ModelLayerLocation(Cosmos.id("capsule"), "canopy");

	public static final String HULL = "hull";
	public static final String CANOPY = "canopy";

	private CapsuleModel() {
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

		// Heat shield at the bottom, tapering up to a shoulder and a docking collar.
		root.addOrReplaceChild(HULL,
				CubeListBuilder.create()
						.texOffs(0, 0).addBox(-5.0F, 0.0F, -5.0F, 10, 3, 10)
						.texOffs(0, 13).addBox(-4.0F, 3.0F, -4.0F, 8, 5, 8)
						.texOffs(0, 26).addBox(-3.0F, 8.0F, -3.0F, 6, 2, 6),
				PartPose.ZERO);

		return LayerDefinition.create(mesh, 64, 64);
	}

	public static LayerDefinition createCanopyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

		// Cubes sit AT the part's origin and the part is offset, rather than the cubes being
		// baked 22 units away from it. A part whose geometry is far from its own origin renders
		// unreliably - the canopy did not appear at all, even submitted unconditionally, until
		// the offset moved into the PartPose where it belongs.
		PartDefinition canopy = root.addOrReplaceChild(CANOPY,
				CubeListBuilder.create()
						.texOffs(0, 34).addBox(-8.0F, 0.0F, -8.0F, 16, 4, 16),
				PartPose.offset(0.0F, 22.0F, 0.0F));
		for (int i = 0; i < 4; i++) {
			float angle = (float) (i * Math.PI / 2.0 + Math.PI / 4.0);
			canopy.addOrReplaceChild("riser_" + i,
					CubeListBuilder.create()
							.texOffs(0, 54).addBox(-0.5F, -12.0F, 4.0F, 1, 12, 1),
					PartPose.rotation(0.0F, angle, 0.0F));
		}

		return LayerDefinition.create(mesh, 64, 64);
	}
}
