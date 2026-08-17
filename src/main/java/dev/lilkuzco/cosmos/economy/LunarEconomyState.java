package dev.lilkuzco.cosmos.economy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.lilkuzco.cosmos.Cosmos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The lunar economy's persisted snapshot, shaped exactly like warfront's {@code EconomyState}.
 *
 * <p>One Base64 string, produced by the model itself behind a magic number, stored on the
 * overworld's data storage because that is where a world's singleton state lives. The model owns
 * its own format; this owns nothing but the string.
 */
public final class LunarEconomyState extends SavedData {

	private static final Codec<java.util.List<net.minecraft.core.BlockPos>> POSITIONS =
			net.minecraft.core.BlockPos.CODEC.listOf();

	private static final Codec<LunarEconomyState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.optionalFieldOf("snapshot", "").forGetter(s -> s.snapshot),
			Codec.STRING.optionalFieldOf("haze_snapshot", "").forGetter(s -> s.hazeSnapshot),
			POSITIONS.optionalFieldOf("electrolysers", java.util.List.of())
					.forGetter(s -> java.util.List.copyOf(s.electrolysers)),
			POSITIONS.optionalFieldOf("kilns", java.util.List.of())
					.forGetter(s -> java.util.List.copyOf(s.kilns))
	).apply(i, LunarEconomyState::new));

	public static final SavedDataType<LunarEconomyState> TYPE = new SavedDataType<>(
			Cosmos.id("lunar_economy"), LunarEconomyState::new, CODEC,
			DataFixTypes.LEVEL);

	private String snapshot = "";
	private String hazeSnapshot = "";
	private final java.util.Set<net.minecraft.core.BlockPos> electrolysers =
			new java.util.LinkedHashSet<>();
	private final java.util.Set<net.minecraft.core.BlockPos> kilns = new java.util.LinkedHashSet<>();

	public LunarEconomyState() {
	}

	private LunarEconomyState(String snapshot, String hazeSnapshot,
	                          java.util.List<net.minecraft.core.BlockPos> plants,
	                          java.util.List<net.minecraft.core.BlockPos> kilns) {
		this.snapshot = snapshot;
		this.hazeSnapshot = hazeSnapshot;
		this.electrolysers.addAll(plants);
		this.kilns.addAll(kilns);
	}

	private java.util.Set<net.minecraft.core.BlockPos> set(
			dev.lilkuzco.cosmos.isru.IsruRegistry.Kind kind) {
		return kind == dev.lilkuzco.cosmos.isru.IsruRegistry.Kind.KILN ? kilns : electrolysers;
	}

	/**
	 * The plants of one kind that exist in this world.
	 *
	 * <p>Persisted rather than counted from loaded chunks, so a base keeps producing while its
	 * chunks are unloaded - which is the only behaviour that makes sense for an economy that runs
	 * on a three-day shock clock.
	 */
	public java.util.Set<net.minecraft.core.BlockPos> plants(
			dev.lilkuzco.cosmos.isru.IsruRegistry.Kind kind) {
		return java.util.Set.copyOf(set(kind));
	}

	public void putPlants(dev.lilkuzco.cosmos.isru.IsruRegistry.Kind kind,
	                      java.util.Collection<net.minecraft.core.BlockPos> positions) {
		java.util.Set<net.minecraft.core.BlockPos> target = set(kind);
		target.clear();
		target.addAll(positions);
		setDirty();
	}

	public static LunarEconomyState get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public String snapshot() {
		return snapshot;
	}

	public void put(LunarEconomy model) {
		this.snapshot = model.encode();
		setDirty();
	}

	public String hazeSnapshot() { return hazeSnapshot; }

	public void putHaze(LunarEconomy model) {
		this.hazeSnapshot = model.encode();
		setDirty();
	}
}
