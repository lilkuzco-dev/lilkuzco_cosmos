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
 * Two entities, both of them windows onto kinetics bodies.
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
					.noLootTable().sized(1.4F, 1.6F).clientTrackingRange(16).updateInterval(1));

	private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Cosmos.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	public static void register() {
	}

	private CosmosEntities() {
	}
}
