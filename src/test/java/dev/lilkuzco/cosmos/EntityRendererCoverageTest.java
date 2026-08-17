package dev.lilkuzco.cosmos;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards the invariant that broke the first live ignition: <strong>every entity type cosmos
 * registers must also have a renderer registered on the client.</strong>
 *
 * <p>Minecraft does not treat a missing renderer as "draw nothing". {@code EntityRenderers}
 * keeps a provider map, {@code EntityRenderDispatcher.getRenderer} returns null for a type that
 * is absent from it, and {@code LevelExtractor.isEntityVisible} dereferences that null on the
 * render thread. Adding an entity type and forgetting the renderer therefore produces a hard
 * client crash the moment the entity comes into view — which is precisely what
 * {@code /cosmos testlaunch} did: the server logged a clean ignition, then the client died with
 * {@code NullPointerException ... because "renderer" is null}.
 *
 * <p>This is deliberately a <em>source-level</em> check rather than a runtime one. Verifying the
 * real registry would require booting a Minecraft client inside the test JVM, which no unit test
 * can reasonably do. Scanning the two files that must agree is cheap, needs no game, and fails
 * for exactly the reason a human would care about. Its one limitation is honest and worth
 * stating: it proves a registration line exists, not that the renderer behaves at runtime.
 */
@DisplayName("every registered entity type has a client renderer")
class EntityRendererCoverageTest {

	/** {@code public static final EntityType<Foo> NAME = ...} */
	private static final Pattern ENTITY_TYPE_FIELD =
			Pattern.compile("static\\s+final\\s+EntityType\\s*<[^>]*>\\s+([A-Z][A-Z0-9_]*)\\s*=");

	/** {@code EntityRendererRegistry.register(CosmosEntities.NAME, ...)} */
	private static final Pattern RENDERER_REGISTRATION =
			Pattern.compile("EntityRendererRegistry\\s*\\.\\s*register\\s*\\(\\s*CosmosEntities\\s*\\.\\s*([A-Z][A-Z0-9_]*)");

	private static Path projectDir() {
		// Gradle runs tests with the project directory as CWD.
		return Path.of("").toAbsolutePath();
	}

	private static String read(Path p) throws IOException {
		assertTrue(Files.exists(p), () -> "expected source file is missing: " + p
				+ " — if the file moved, update this test rather than deleting it");
		return Files.readString(p);
	}

	private static Set<String> matches(Pattern pattern, String source) {
		Set<String> found = new LinkedHashSet<>();
		Matcher m = pattern.matcher(source);
		while (m.find()) {
			found.add(m.group(1));
		}
		return found;
	}

	@Test
	void everyEntityTypeHasARendererRegistered() throws IOException {
		Path root = projectDir();
		String entities = read(root.resolve("src/main/java/dev/lilkuzco/cosmos/CosmosEntities.java"));
		String client = read(root.resolve("src/client/java/dev/lilkuzco/cosmos/client/CosmosClient.java"));

		Set<String> declared = matches(ENTITY_TYPE_FIELD, entities);
		Set<String> rendered = matches(RENDERER_REGISTRATION, client);

		assertFalse(declared.isEmpty(),
				"parsed zero EntityType fields out of CosmosEntities.java — the pattern has gone "
						+ "stale and this test is no longer guarding anything");

		List<String> missing = new ArrayList<>();
		for (String type : declared) {
			if (!rendered.contains(type)) {
				missing.add(type);
			}
		}

		assertTrue(missing.isEmpty(), () -> String.format(
				"%d entity type(s) registered with no client renderer: %s%n"
						+ "  declared in CosmosEntities: %s%n"
						+ "  rendered in CosmosClient:   %s%n"
						+ "Any of the missing types will HARD CRASH the client when it comes into "
						+ "view (NullPointerException in EntityRenderDispatcher.shouldRender). "
						+ "If the entity should have no model, register InvisibleEntityRenderer::new "
						+ "— a registered renderer that draws nothing is not the same as no renderer.",
				missing.size(), missing, declared, rendered));
	}

	@Test
	void rendererRegistrationsReferOnlyToRealEntityTypes() throws IOException {
		Path root = projectDir();
		String entities = read(root.resolve("src/main/java/dev/lilkuzco/cosmos/CosmosEntities.java"));
		String client = read(root.resolve("src/client/java/dev/lilkuzco/cosmos/client/CosmosClient.java"));

		Set<String> declared = matches(ENTITY_TYPE_FIELD, entities);
		Set<String> rendered = matches(RENDERER_REGISTRATION, client);

		List<String> unknown = new ArrayList<>();
		for (String type : rendered) {
			if (!declared.contains(type)) {
				unknown.add(type);
			}
		}

		// Catches the reverse drift: an entity gets deleted or renamed and a dangling registration
		// is left behind. That would not crash, but it means the two files have stopped agreeing.
		assertTrue(unknown.isEmpty(), () -> "CosmosClient registers renderers for entity types that "
				+ "CosmosEntities does not declare: " + unknown);
	}
}
