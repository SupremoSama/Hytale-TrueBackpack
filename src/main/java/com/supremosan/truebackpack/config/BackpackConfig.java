package com.supremosan.truebackpack.config;

import java.util.ArrayList;
import java.util.List;

public final class BackpackConfig {

    public List<Entry> backpacks = new ArrayList<>();

    public static final class Entry {
        public String itemId;
        public String blockId = "";
        public short capacity = 9;
        public String model;
        public String texture;
        public HelipackEntry helipack;

        public boolean isHelipack() {
            return helipack != null;
        }

        public boolean isValid() {
            return itemId != null && !itemId.isBlank()
                    && model != null && !model.isBlank()
                    && texture != null && !texture.isBlank();
        }
    }

    public static final class HelipackEntry {
        public String fuelItemId = "";
        public String itemAnimationsId = "";
        public float verticalFlySpeed = 6.0f;
        public float horizontalFlySpeed = 7.0f;
        public float fuelConsumeInterval = 10.0f;
        public int fuelConsumeAmount = 5;
    }
}