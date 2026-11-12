package io.github.takicoyuki.miniweb.web;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonWriterTest {

    enum Color { RED }

    record Item(String name, int price, boolean soldOut, List<String> tags, Color color) {
    }

    @Test
    void recordとコレクションを入れ子でJSONにする() {
        Item item = new Item("ペン", 120, false, List.of("文具", "黒"), Color.RED);

        assertEquals("{\"name\":\"ペン\",\"price\":120,\"soldOut\":false,\"tags\":[\"文具\",\"黒\"],\"color\":\"RED\"}",
                JsonWriter.write(item));
    }

    @Test
    void Mapとnullと配列を扱える() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("a", null);
        map.put("b", new Integer[]{1, 2});

        assertEquals("{\"a\":null,\"b\":[1,2]}", JsonWriter.write(map));
    }

    @Test
    void 文字列の特殊文字をエスケープする() {
        assertEquals("\"\\\"q\\\" \\\\ \\n \\t \\u0001\"", JsonWriter.write("\"q\" \\ \n \t \u0001"));
    }

    @Test
    void 対応していない型はエラーにする() {
        assertThrows(IllegalArgumentException.class, () -> JsonWriter.write(new Object()));
    }
}
