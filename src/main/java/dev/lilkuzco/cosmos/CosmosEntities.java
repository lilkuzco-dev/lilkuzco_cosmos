package dev.lilkuzco.cosmos;

import dev.lilkuzco.cosmos.recovery.CapsuleEntity;
import dev.lilkuzco.cosmos.rocket.RocketEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * Three entities. Two are windows onto kinetics bodies; the third carries the crew between them.
 *
 * <p>The tracking ranges are generous because a rocket climbs out of ordinary view very quickly -
 * a player who lit it should be able to watch it go rather than have it vanish at 64 blocks.
 */
public final class CosmosEntities {

	public static final EntityType<RocketEntity> ROCKET = register("rocket",
			EntityType.Builder.<RocketEntity>of(RocketEntity::new, MobCategory.MISC)
					.noLootTable().sized(1.2F, 4.0F).clientTrackingRange(16).updateInterval(1));

	public static final EntityType<CapsuleEntity> CAPSULE = register("capsule",
			EntityType.Builder.<CapsuleEntity>of(CapsuleEntity::new, MobCategory.MISC)
					// TALL ENOUGH TO CONTAIN THE PARACHUTE. The hull is 0.6 blocks and the canopy
					// sits at 1.4-1.63, so at the old 1.6 the canopy was outside the entity's
					// own box - and geometry outside the culling box is dropped. The hull drew
					// and the canopy did not, which is precisely the symptom.
					.noLootTable().sized(1.6F, 3.0F).clientTrackingRange(16).updateInterval(1));

	/**
	 * The transfer vehicle for the trans-lunar coast.
	 *
	 * <p>Not a view of a kinetics body, and it is the one entity in cosmos that is not: the coast
	 * is a closed-form ellipse, propagated from an epoch, exactly as an orbit is. There is nothing
	 * being integrated for it to mirror.
	 */
	public static final EntityType<dev.lilkuzco.cosmos.moon.TransitEntity> TRANSIT =
			register("transit", EntityType.Builder
					.<dev.lilkuzco.cosmos.moon.TransitEntity>of(
							dev.lilkuzco.cosmos.moon.TransitEntity::new, MobCategory.MISC)
					.noLootTable().sized(1.6F, 2.4F).clientTrackingRange(16).updateInterval(1));

	private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Cosmos.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	public static void register() {
	}

	private CosmosEntities() {
	}
}
