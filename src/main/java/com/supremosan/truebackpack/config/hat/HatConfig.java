package com.supremosan.truebackpack.config.hat;

import java.util.ArrayList;
import java.util.List;

public final class HatConfig {

    public List<Entry> hats = new ArrayList<>();

    public static final class Entry {
        public String itemId;
        public short maxDurability = 100;
        public int drainIntervalTicks = 20;
        public String model;
        public String texture;
        public DynamicLightEntry dynamicLight;

        public boolean isValid() {
            return itemId != null && !itemId.isBlank()
                    && model != null && !model.isBlank()
                    && texture != null && !texture.isBlank()
                    && maxDurability > 0
                    && drainIntervalTicks > 0;
        }
    }

    public static final class DynamicLightEntry {
        public byte radius;
        public byte red;
        public byte green;
        public byte blue;

        public boolean isValid() {
            return radius > 0;
        }
    }
}