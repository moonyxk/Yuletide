package dev.yuletide.santa;

import dev.yuletide.MinecraftCompat;
import dev.yuletide.Yuletide;
import dev.yuletide.config.Cfg;
import dev.yuletide.gift.ItemFactory;
import dev.yuletide.text.Text;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Das Weihnachtsmann-Event: höchstens eine laufende Instanz, getaktet über den Server-Tick. */
public final class SantaManager {
	public static final String TAG = "christmas_santa";
	public static final Pattern TIME_PATTERN = Pattern.compile("^\\d+[smh]$");

	record WeightedValue<T>(T value, long weight) {
	}

	private static final String GIFT_ITEMS_PATH = "santa-claus.gift-drop.items";

	/** Merkt sich eine gesetzte Schneeschicht mit Dimension und Ablaufzeit in Event-Ticks. */
	private record Trail(ResourceKey<Level> level, BlockPos pos, long expiresAt) {
	}

	private List<WeightedValue<Item>> giftDrops = defaultGiftDrops();
	private long giftDropWeightTotal = totalWeight(giftDrops);
	private @Nullable MinecraftServer server;
	// UUID als Identitaet, Referenz als schneller Zugriff und Dimensionsschluessel fuer die erneute Suche.
	private @Nullable UUID santa;
	private @Nullable Entity santaRef;
	private int goneTicks;
	private @Nullable ResourceKey<Level> santaLevel;
	private @Nullable BlockPos forcedChunk;
	// Verbleibende Laufzeit und bisherige Event-Ticks sind getrennte Zaehler.
	private int ticksLeft;
	private long tick;
	private final List<Trail> trail = new ArrayList<>();
	private final List<Entity> orphans = new ArrayList<>();

	public boolean isRunning() {
		return santa != null;
	}

	/** Laedt den Santa-Geschenkpool; fehlende Listen in aelteren Configs behalten den bisherigen Pool. */
	public void reload() {
		Cfg config = Yuletide.CONFIG.config();
		if (!config.has(GIFT_ITEMS_PATH)) {
			giftDrops = defaultGiftDrops();
		} else {
			List<WeightedValue<Item>> configured = new ArrayList<>();
			List<Cfg> entries = config.list(GIFT_ITEMS_PATH);
			for (int i = 0; i < entries.size(); i++) {
				Cfg entry = entries.get(i);
				String itemId = entry.str("item", "");
				Item item = ItemFactory.item(itemId);
				int weight = entry.integer("weight", 1);
				if (item == null) {
					Yuletide.LOGGER.warn("[{}[{}]] Unknown item '{}' - skipped", GIFT_ITEMS_PATH, i, itemId);
					continue;
				}
				if (weight <= 0) {
					Yuletide.LOGGER.warn("[{}[{}]] Weight must be positive - skipped", GIFT_ITEMS_PATH, i);
					continue;
				}
				configured.add(new WeightedValue<>(item, weight));
			}
			giftDrops = List.copyOf(configured);
			if (giftDrops.isEmpty()) {
				Yuletide.LOGGER.warn("No valid Santa gift-drop items configured; Santa gift drops are disabled");
			}
		}
		giftDropWeightTotal = totalWeight(giftDrops);
	}

	private static List<WeightedValue<Item>> defaultGiftDrops() {
		return List.of(
			new WeightedValue<>(Items.DIAMOND, 1),
			new WeightedValue<>(Items.EMERALD, 1),
			new WeightedValue<>(Items.GOLD_INGOT, 1),
			new WeightedValue<>(Items.GOLDEN_APPLE, 1),
			new WeightedValue<>(Items.ENCHANTED_GOLDEN_APPLE, 1),
			new WeightedValue<>(Items.COOKIE, 1),
			new WeightedValue<>(Items.CAKE, 1),
			new WeightedValue<>(Items.PUMPKIN_PIE, 1));
	}

	private static long totalWeight(List<WeightedValue<Item>> values) {
		long total = 0;
		for (WeightedValue<Item> value : values) {
			total += value.weight();
		}
		return total;
	}

	static <T> T chooseWeighted(List<WeightedValue<T>> values, long selection) {
		if (selection < 0) {
			throw new IllegalArgumentException("Selection must be non-negative");
		}
		for (WeightedValue<T> value : values) {
			if (value.weight() <= 0) {
				throw new IllegalArgumentException("Weights must be positive");
			}
			if (selection < value.weight()) {
				return value.value();
			}
			selection -= value.weight();
		}
		throw new IllegalArgumentException("Selection is outside the configured weight range");
	}

	/**
	 * Wird am Ende jedes Server-Ticks aufgerufen, nicht in einem eigenen Hintergrund-Thread.
	 * Dadurch bleiben Entity-Zugriffe und Weltveraenderungen im normalen Serverablauf.
	 */
	public void tick(MinecraftServer server) {
		this.server = server;
		cleanOrphans();
		if (santa == null) {
			return;
		}
		tick++;
		Entity entity = santaEntity();
		if (entity == null || !entity.isAlive()) {
			// Bei verschwundener Entity kurz warten, statt schon beim ersten Fehlschlag abzubrechen.
			// 100 Ticks entsprechen bei normalem Servertakt etwa fuenf Sekunden.
			if (++goneTicks > 100) {
				Yuletide.LOGGER.info("Santa entity is gone - ending the event");
				stop(true);
			}
			return;
		}
		goneTicks = 0;
		ServerLevel level = (ServerLevel) entity.level();
		Cfg cfg = Yuletide.CONFIG.config();
		removeExpiredTrail(false);

		if (cfg.bool("santa-claus.gift-drop.enabled", true) && !giftDrops.isEmpty()) {
			// Konfiguration in Sekunden -> Ticks; Modulo prueft, ob ein Intervall erreicht ist.
			// Die Prozentchance wird einmal pro Intervall ausgewertet, nicht pro Tick.
			int interval = Math.max(1, cfg.integer("santa-claus.gift-drop.interval", 5)) * 20;
			if (tick % interval == 0 && ThreadLocalRandom.current().nextDouble() * 100 < cfg.dbl("santa-claus.gift-drop.chance", 15.0)) {
				dropGift(level, entity);
			}
		}
		if (cfg.bool("santa-claus.snow-trail.enabled", true) && tick % 10 == 0) {
			snowTrail(level, entity, cfg);
		}
		if (--ticksLeft <= 0) {
			stop(true);
		}
	}

	/** Waehlt gewichtet aus der konfigurierten Liste; Santa-Drops sind unabhaengig von gifts.json. */
	private void dropGift(ServerLevel level, Entity entity) {
		ItemStack stack = new ItemStack(chooseWeighted(giftDrops, ThreadLocalRandom.current().nextLong(giftDropWeightTotal)), 1);
		level.addFreshEntity(new ItemEntity(level, entity.getX(), entity.getY(), entity.getZ(), stack));
		level.sendParticles(ParticleTypes.HEART, entity.getX(), entity.getY() + 1, entity.getZ(), 10, 0.5, 0.5, 0.5, 0.1);
		level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5f, 1.5f);
	}

	private void snowTrail(ServerLevel level, Entity entity, Cfg cfg) {
		level.sendParticles(ParticleTypes.SNOWFLAKE, entity.getX(), entity.getY() + 1, entity.getZ(),
			cfg.integer("santa-claus.snow-trail.particle-amount", 10), 0.5, 0.5, 0.5, 0.01);
		BlockPos at = entity.blockPosition();
		BlockPos below = at.below();
		// Nur auf tragfaehigem Boden und in Luft Schnee setzen, keine vorhandenen Bloecke ersetzen.
		if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) && level.getBlockState(at).isAir()) {
			level.setBlockAndUpdate(at, Blocks.SNOW.defaultBlockState());
			trail.add(new Trail(level.dimension(), at.immutable(), tick + Math.max(0, cfg.integer("santa-claus.snow-trail.fade-delay", 100))));
		}
	}

	/** Entfernt abgelaufenen Schnee; all=true raeumt beim Eventende die gesamte gemerkte Spur auf. */
	private void removeExpiredTrail(boolean all) {
		if (server == null) {
			trail.clear();
			return;
		}
		// Iterator.remove erlaubt Entfernen waehrend des Durchlaufens ohne ConcurrentModificationException.
		for (Iterator<Trail> it = trail.iterator(); it.hasNext(); ) {
			Trail t = it.next();
			if (!all && t.expiresAt() > tick) {
				continue;
			}
			it.remove();
			ServerLevel level = server.getLevel(t.level());
			// Wurde Schnee inzwischen durch etwas anderes ersetzt, diesen neuen Block nicht loeschen.
			if (level != null && level.getBlockState(t.pos()).is(Blocks.SNOW)) {
				level.setBlockAndUpdate(t.pos(), Blocks.AIR.defaultBlockState());
			}
		}
	}

	/** Nutzt die Entity-Referenz, solange sie gueltig ist, sonst eine Suche per UUID in der gemerkten Dimension. */
	private @Nullable Entity santaEntity() {
		if (santaRef != null && !santaRef.isRemoved()) {
			return santaRef;
		}
		if (server == null || santa == null || santaLevel == null) {
			return null;
		}
		ServerLevel level = server.getLevel(santaLevel);
		Entity found = level == null ? null : level.getEntity(santa);
		if (found != null) {
			santaRef = found;
		}
		return found;
	}

	public @Nullable Vec3 position() {
		Entity entity = santaEntity();
		return entity == null ? null : entity.position();
	}

	public @Nullable ServerLevel level() {
		Entity entity = santaEntity();
		return entity == null ? null : (ServerLevel) entity.level();
	}

	/** Entfernt Santa-Wesen, die nach einem Absturz oder Neustart übrig geblieben sind (Fehler 13). */
	public void onEntityLoad(Entity entity) {
		if (entity.entityTags().contains(TAG) && (santa == null || !entity.getUUID().equals(santa))) {
			// Nur vormerken: Das eigentliche Entfernen erfolgt im Tick, nicht mitten im Entity-Ladevorgang.
			orphans.add(entity);
		}
	}

	private void cleanOrphans() {
		if (!orphans.isEmpty()) {
			orphans.forEach(Entity::discard);
			orphans.clear();
		}
	}

	/** Rueckgabewert fuer Fabric ALLOW_DAMAGE: false schuetzt nur den aktuell registrierten Santa. */
	public boolean allowDamage(Entity entity) {
		return santa == null || !entity.getUUID().equals(santa);
	}

	/** Startet das Event; liefert {@code false}, wenn bereits eines läuft oder Santa nicht gespawnt werden konnte. */
	public boolean start(ServerLevel level, Vec3 pos, int seconds) {
		if (santa != null) {
			return false;
		}
		Cfg cfg = Yuletide.CONFIG.config();
		Entity entity = createSanta(level, cfg);
		if (entity == null) {
			return false;
		}
		entity.snapTo(pos.x, pos.y, pos.z);
		MinecraftCompat.setInvulnerable(entity);
		entity.addTag(TAG);
		entity.setCustomName(Text.legacy(cfg.str("santa-claus.name", "&c&lSanta Claus")));
		entity.setCustomNameVisible(cfg.bool("santa-claus.show-name-tag", true));
		if (entity instanceof Mob mob) {
			mob.setPersistenceRequired();
		}
		BlockPos block = BlockPos.containing(pos);
		// Ein Chunk ist 16 Bloecke breit; >> 4 rechnet Block- in Chunk-Koordinaten um.
		// Den Spawn-Chunk geladen halten, auch wenn gerade kein Spieler in der Naehe ist.
		level.setChunkForced(block.getX() >> 4, block.getZ() >> 4, true);
		level.getChunk(block.getX() >> 4, block.getZ() >> 4);
		server = level.getServer();
		// Identitaet VOR dem Einfuegen merken: onEntityLoad darf diesen Santa nicht als Altlast einstufen.
		santa = entity.getUUID();
		santaLevel = level.dimension();
		if (!level.addFreshEntity(entity)) {
			// Bei fehlgeschlagenem Spawn bereits gesetzten Zustand und Chunk-Zwang wieder zuruecknehmen.
			santa = null;
			santaLevel = null;
			level.setChunkForced(block.getX() >> 4, block.getZ() >> 4, false);
			return false;
		}
		ticksLeft = Math.max(1, seconds) * 20;
		santaRef = entity;
		goneTicks = 0;
		tick = 0;
		forcedChunk = block;
		String id = level.dimension().identifier().toString();
		broadcast(Yuletide.MESSAGES.get("santa.event-started", "world", id,
			"x", String.valueOf(block.getX()), "y", String.valueOf(block.getY()),
			"z", String.valueOf(block.getZ()), "time", formatTime(seconds)));
		return true;
	}

	/** Erstellt die konfigurierte Entity; ein Villager dient als Ersatz und erhaelt die Schnee-Variante. */
	private @Nullable Entity createSanta(ServerLevel level, Cfg cfg) {
		Identifier id = Identifier.tryParse(cfg.str("santa-claus.entity-type", "minecraft:villager").toLowerCase());
		Optional<EntityType<?>> type = id == null ? Optional.empty() : BuiltInRegistries.ENTITY_TYPE.getOptional(id).map(t -> (EntityType<?>) t);
		if (type.isEmpty()) {
			Yuletide.LOGGER.warn("Unknown santa-claus.entity-type - using villager");
		}
		EntityType<?> villagerType = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse("minecraft:villager"))
			.orElseThrow(() -> new IllegalStateException("Missing Minecraft villager entity type"));
		Entity entity = type.orElse(villagerType).create(level, EntitySpawnReason.COMMAND);
		if (entity == null) {
			entity = villagerType.create(level, EntitySpawnReason.COMMAND);
		}
		if (entity instanceof Villager villager) {
			villager.setVillagerData(villager.getVillagerData()
				.withType(level.registryAccess(), VillagerType.SNOW)
				.withProfession(level.registryAccess(), VillagerProfession.NONE));
		}
		return entity;
	}

	/** Startet an einer zufälligen konfigurierten Position. */
	public boolean startRandom(MinecraftServer server, int seconds) {
		List<Cfg> locations = Yuletide.CONFIG.config().list("santa-claus.spawn-locations");
		List<Object[]> valid = new ArrayList<>();
		// Erst ungueltige Welten aussortieren, dann gleichverteilt eine verbleibende Position waehlen.
		for (Cfg location : locations) {
			ServerLevel level = findLevel(server, location.str("world", "minecraft:overworld"));
			if (level != null) {
				valid.add(new Object[] {level, new Vec3(location.dbl("x", 0), location.dbl("y", 64), location.dbl("z", 0))});
			}
		}
		if (valid.isEmpty()) {
			Yuletide.LOGGER.warn("No valid santa-claus.spawn-locations configured for random spawn");
			return false;
		}
		Object[] pick = valid.get(ThreadLocalRandom.current().nextInt(valid.size()));
		if (!start((ServerLevel) pick[0], (Vec3) pick[1], seconds)) {
			return false;
		}
		broadcast(Yuletide.MESSAGES.get("santa.event-started-random"));
		return true;
	}

	/**
	 * Raeumt Schnee, erzwungenen Spawn-Chunk und Santa-Entity auf, danach den Laufzeitzustand.
	 * announce=false unterdrueckt die Chatmeldung, etwa beim Herunterfahren des Servers.
	 */
	public void stop(boolean announce) {
		if (santa == null) {
			return;
		}
		Entity entity = santaEntity();
		removeExpiredTrail(true);
		if (server != null && santaLevel != null && forcedChunk != null) {
			ServerLevel level = server.getLevel(santaLevel);
			if (level != null) {
				level.setChunkForced(forcedChunk.getX() >> 4, forcedChunk.getZ() >> 4, false);
			}
		}
		if (entity != null) {
			entity.discard();
		}
		santa = null;
		santaRef = null;
		santaLevel = null;
		forcedChunk = null;
		if (announce) {
			broadcast(Yuletide.MESSAGES.get("santa.event-stopped"));
		}
	}

	public boolean teleportToSanta(ServerPlayer player) {
		Entity entity = santaEntity();
		if (entity == null) {
			return false;
		}
		// Leere Relative-Menge bedeutet absolute Zielkoordinaten; die Blickrichtung des Spielers bleibt erhalten.
		player.teleportTo((ServerLevel) entity.level(), entity.getX(), entity.getY(), entity.getZ(),
			java.util.Set.<Relative>of(), player.getYRot(), player.getXRot(), true);
		return true;
	}

	private void broadcast(Component message) {
		if (server != null) {
			server.getPlayerList().broadcastSystemMessage(message, false);
		}
	}

	/** {@code 30s}, {@code 5m}, {@code 1h}; ungültig → 600 (Originalverhalten). */
	public static int parseTime(String input) {
		try {
			int value = Integer.parseInt(input.substring(0, input.length() - 1));
			return switch (input.charAt(input.length() - 1)) {
				case 's' -> value;
				// multiplyExact wirft bei int-Ueberlauf eine Exception; auch dann greift der Standardwert.
				case 'm' -> Math.multiplyExact(value, 60);
				case 'h' -> Math.multiplyExact(value, 3600);
				default -> 600;
			};
		} catch (RuntimeException e) {
			return 600;
		}
	}

	/** Kompakte Anzeige in ganzen Sekunden, Minuten oder Stunden; Restanteile werden abgeschnitten. */
	public static String formatTime(int seconds) {
		if (seconds < 60) {
			return seconds + "s";
		}
		return seconds < 3600 ? seconds / 60 + "m" : seconds / 3600 + "h";
	}

	/** Akzeptiert {@code overworld}, {@code minecraft:overworld}, {@code the_nether} usw. */
	public static @Nullable ServerLevel findLevel(MinecraftServer server, String name) {
		Identifier id = Identifier.tryParse(name.contains(":") ? name : "minecraft:" + name);
		return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
	}

	public static List<String> worldNames(MinecraftServer server) {
		List<String> names = new ArrayList<>();
		for (ResourceKey<Level> key : server.levelKeys()) {
			names.add(key.identifier().toString());
		}
		return names;
	}
}
