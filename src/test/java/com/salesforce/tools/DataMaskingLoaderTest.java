package com.salesforce.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DataMaskingLoaderTest {

    @Test
    void tooFewArgumentsReturnsError() {
        assertEquals(DataMaskingLoader.EXIT_ERROR, DataMaskingLoader.run(new String[]{"mask-only"}));
    }

    @Test
    void unknownModeReturnsError() {
        assertEquals(DataMaskingLoader.EXIT_ERROR,
                DataMaskingLoader.run(new String[]{"mask-delete", "Account", "sample-accounts.csv"}));
    }

    @Test
    void unknownObjectReturnsError() {
        assertEquals(DataMaskingLoader.EXIT_ERROR,
                DataMaskingLoader.run(new String[]{"mask-only", "Opportunity", "sample-accounts.csv"}));
    }
}
