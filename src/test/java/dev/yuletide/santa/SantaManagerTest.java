package dev.yuletide.santa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class SantaManagerTest {
	@Test
	void choosesGiftByWeightAcrossBoundaries() {
		List<SantaManager.WeightedValue<String>> gifts = List.of(
			new SantaManager.WeightedValue<>("diamond", 1),
			new SantaManager.WeightedValue<>("cookie", 3));

		assertEquals("diamond", SantaManager.chooseWeighted(gifts, 0));
		assertEquals("cookie", SantaManager.chooseWeighted(gifts, 1));
		assertEquals("cookie", SantaManager.chooseWeighted(gifts, 3));
	}

	@Test
	void rejectsSelectionsOutsideTheConfiguredWeightRange() {
		List<SantaManager.WeightedValue<String>> gifts = List.of(
			new SantaManager.WeightedValue<>("diamond", 1));

		assertThrows(IllegalArgumentException.class, () -> SantaManager.chooseWeighted(gifts, -1));
		assertThrows(IllegalArgumentException.class, () -> SantaManager.chooseWeighted(gifts, 1));
	}
}
