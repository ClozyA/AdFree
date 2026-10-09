package xyz.fearr.adfree;

import org.junit.Test;
import static org.junit.Assert.*;

public class LogProviderTest {
    @Test
    public void acceptsOnlyKnownCallerOwnedByBinderUid() {
        assertTrue(LogProvider.isAllowed("run.xbud.android", new String[]{"run.xbud.android"}));
        assertTrue(LogProvider.isAllowed("com.fiveplay", new String[]{"com.fiveplay"}));
        assertTrue(LogProvider.isAllowed(BuildConfig.APPLICATION_ID, new String[]{BuildConfig.APPLICATION_ID}));
        assertFalse(LogProvider.isAllowed("com.other", new String[]{"com.other"}));
        assertFalse(LogProvider.isAllowed("com.fiveplay", new String[]{"com.other"}));
        assertFalse(LogProvider.isAllowed("com.other", new String[]{"com.fiveplay"}));
        assertFalse(LogProvider.isAllowed("com.fiveplay", null));
        assertFalse(LogProvider.isAllowed(null, new String[]{"com.fiveplay"}));
    }
}
