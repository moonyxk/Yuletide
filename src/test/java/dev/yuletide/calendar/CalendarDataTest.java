package dev.yuletide.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CalendarDataTest {
	private static final int YEAR = 2026;
	private static final UUID PLAYER = UUID.fromString("6bde63a6-f047-4bdd-8518-163a14c555d5");
	private static final UUID OTHER_PLAYER = UUID.fromString("26743112-a196-4f5b-922a-f3d17f80be38");

	@TempDir
	Path tempDir;

	@Test
	void savesClaimsAcrossReloadsWithoutMixingPlayersOrYears() {
		Path file = tempDir.resolve("world/data/yuletide_calendar.json");
		CalendarData data = new CalendarData();
		data.load(file);

		assertFalse(data.isClaimed(YEAR, PLAYER, 1));
		data.claim(YEAR, PLAYER, 1);
		data.claim(YEAR, PLAYER, 2);
		data.claim(YEAR, PLAYER, 1);
		data.claim(YEAR - 1, PLAYER, 1);
		data.claim(YEAR, OTHER_PLAYER, 1);

		Set<Integer> snapshot = data.claimedDays(YEAR, PLAYER);
		assertEquals(Set.of(1, 2), snapshot);
		assertThrows(UnsupportedOperationException.class, () -> snapshot.add(3));

		CalendarData loaded = new CalendarData();
		loaded.load(file);
		assertEquals(Set.of(1, 2), loaded.claimedDays(YEAR, PLAYER));
		assertTrue(loaded.isClaimed(YEAR - 1, PLAYER, 1));
		assertTrue(loaded.isClaimed(YEAR, OTHER_PLAYER, 1));
		assertFalse(loaded.isClaimed(YEAR, PLAYER, 3));
	}

	@Test
	void persistsSingleDayAndPlayerResets() {
		Path file = tempDir.resolve("world/data/yuletide_calendar.json");
		CalendarData data = new CalendarData();
		data.load(file);
		data.claim(YEAR, PLAYER, 1);
		data.claim(YEAR, PLAYER, 2);
		data.claim(YEAR, OTHER_PLAYER, 1);

		data.reset(YEAR, PLAYER, 1);
		CalendarData afterDayReset = new CalendarData();
		afterDayReset.load(file);
		assertFalse(afterDayReset.isClaimed(YEAR, PLAYER, 1));
		assertTrue(afterDayReset.isClaimed(YEAR, PLAYER, 2));
		assertTrue(afterDayReset.isClaimed(YEAR, OTHER_PLAYER, 1));

		afterDayReset.resetAll(YEAR, PLAYER);
		CalendarData afterPlayerReset = new CalendarData();
		afterPlayerReset.load(file);
		assertEquals(Set.of(), afterPlayerReset.claimedDays(YEAR, PLAYER));
		assertTrue(afterPlayerReset.isClaimed(YEAR, OTHER_PLAYER, 1));
	}
}
