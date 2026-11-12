package dev.yuletide;

import dev.yuletide.calendar.CalendarManager;
import dev.yuletide.command.AdventCalendarCommand;
import dev.yuletide.command.ChristmasCommand;
import dev.yuletide.command.CrackerCommand;
import dev.yuletide.command.YuletideCommand;
import dev.yuletide.cracker.CrackerManager;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import dev.yuletide.santa.SantaManager;
import dev.yuletide.snow.SnowEffectManager;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import dev.yuletide.config.ConfigManager;
import dev.yuletide.gift.GiftManager;
import dev.yuletide.text.Messages;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Einstiegspunkt der Mod: Fabric ruft {@link #onInitialize()} beim Laden auf.
 * Diese Klasse verbindet die einzelnen Funktionen mit Minecraft-Ereignissen.
 * Die Spiellogik laeuft auf dem Server, auch im Einzelspieler auf dessen integriertem Server.
 */
public class Yuletide implements ModInitializer {
	public static final String MOD_ID = "yuletide";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	// Gemeinsam genutzte Dienste. Konstruktoren erhalten ihre Abhaengigkeiten dort,
	// wo sie benoetigt werden, z. B. braucht der Kalender Konfiguration, Texte und Geschenke.
	public static final ConfigManager CONFIG = new ConfigManager();
	public static final Messages MESSAGES = new Messages(CONFIG);
	public static final GiftManager GIFTS = new GiftManager();
	public static final CalendarManager CALENDAR = new CalendarManager(CONFIG, MESSAGES, GIFTS);

	public static final SantaManager SANTA = new SantaManager();
	public static final SnowEffectManager SNOW = new SnowEffectManager();
	public static final CrackerManager CRACKERS = new CrackerManager();

	@Override
	public void onInitialize() {
		// Hier stehen noch keine Welt und keine Server-Registries zur Verfuegung.
		// Deshalb zunaechst nur Dateien und weltunabhaengige Einstellungen laden.
		CONFIG.load();
		SNOW.reload();
		CRACKERS.reload();

		// Eine Lambda-Funktion wird als Callback hinterlegt: Fabric fuehrt sie spaeter
		// beim Aufbau des Befehlsbaums aus. Die weiteren Namen sind Befehls-Aliasse.
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			new AdventCalendarCommand().register(dispatcher, "adventcalendar", "ac", "calendar", "advent");
			new ChristmasCommand().register(dispatcher, "christmas", "xmas");
			new CrackerCommand().register(dispatcher, "cracker", "crackers");
			new YuletideCommand().register(dispatcher, "yuletide");
		});

		// Rechtsklick in die Luft und auf einen Block sind getrennte Ereignisse,
		// verwenden fuer Knallbonbons aber dieselbe Logik.
		UseItemCallback.EVENT.register((player, level, hand) -> CRACKERS.use(player, level, hand));
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> CRACKERS.use(player, level, hand));
		// Methodenreferenzen wie SANTA::tick sind eine Kurzform fuer passende Lambdas.
		// Ein Server-Tick dauert bei normaler Geschwindigkeit 1/20 Sekunde.
		ServerTickEvents.END_SERVER_TICK.register(SANTA::tick);
		ServerTickEvents.END_SERVER_TICK.register(SNOW::tick);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> SANTA.onEntityLoad(entity));
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> SANTA.allowDamage(entity));

		// Erst jetzt existiert die Welt: Verzauberungen koennen ueber Registries
		// aufgeloest und die Kalenderdaten aus dem Weltordner gelesen werden.
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			GIFTS.load(server, CONFIG.gifts(), CONFIG.config());
			CALENDAR.start(server);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			// Laufende Weltveraenderungen aufraeumen und Fortschritt sichern.
			SANTA.stop(false);
			CALENDAR.stop();
		});
		LOGGER.info("Yuletide loaded");
	}

	/** Lädt Konfiguration, Geschenke und Kalenderdaten neu (Santa-Event bleibt unberührt). */
	public static void reload(MinecraftServer server) {
		CONFIG.load();
		SNOW.reload();
		CRACKERS.reload();
		GIFTS.load(server, CONFIG.gifts(), CONFIG.config());
		CALENDAR.reload();
	}
}
