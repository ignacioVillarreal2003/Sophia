package com.sophia.documentworkspace.domain.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FractionalIndexTest {

    @Test
    void betweenProducesAKeyStrictlyInsideTheInterval() {
        String key = FractionalIndex.between("a", "c");
        assertThat(key).isGreaterThan("a").isLessThan("c");
    }

    @Test
    void openEndedBoundsWork() {
        String first = FractionalIndex.first();
        assertThat(FractionalIndex.after(first)).isGreaterThan(first);
        assertThat(FractionalIndex.before(first)).isLessThan(first);
        assertThat(FractionalIndex.between(null, first)).isLessThan(first);
        assertThat(FractionalIndex.between(first, null)).isGreaterThan(first);
    }

    @Test
    void adjacentKeysStillAdmitAKeyBetweenThem() {
        // 'U' and 'V' are adjacent digits: the key must grow in length.
        String key = FractionalIndex.between("U", "V");
        assertThat(key).isGreaterThan("U").isLessThan("V");
    }

    @Test
    void repeatedInsertionAtTheFrontStaysOrdered() {
        List<String> keys = new ArrayList<>();
        String current = FractionalIndex.first();
        keys.add(current);
        for (int i = 0; i < 200; i++) {
            current = FractionalIndex.before(current);
            keys.add(0, current);
        }
        assertThat(keys).isSorted();
    }

    @Test
    void repeatedInsertionBetweenTwoKeysStaysOrdered() {
        String low = FractionalIndex.first();
        String high = FractionalIndex.after(low);
        String previous = low;
        for (int i = 0; i < 200; i++) {
            String middle = FractionalIndex.between(previous, high);
            assertThat(middle).isGreaterThan(previous).isLessThan(high);
            previous = middle;
        }
    }

    @Test
    void invertedBoundsAreRejected() {
        assertThatThrownBy(() -> FractionalIndex.between("z", "a"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FractionalIndex.between("a", "a"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
