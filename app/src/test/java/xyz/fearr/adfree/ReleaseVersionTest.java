package xyz.fearr.adfree;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ReleaseVersionTest {
    @Test
    public void comparesNumericComponentsAndOptionalTagPrefix() {
        assertTrue(ReleaseVersion.parse("v0.10.0").compareTo(ReleaseVersion.parse("0.9.1")) > 0);
        assertTrue(ReleaseVersion.parse("1.0.0").compareTo(ReleaseVersion.parse("0.99.99")) > 0);
        assertTrue(ReleaseVersion.parse("0.10.1").compareTo(ReleaseVersion.parse("0.10.0")) > 0);
        assertTrue(ReleaseVersion.parse("0.9.9").compareTo(ReleaseVersion.parse("0.10.0")) < 0);
        assertEquals(0, ReleaseVersion.parse("v0.10.0").compareTo(ReleaseVersion.parse("0.10.0")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsPrereleaseTags() {
        ReleaseVersion.parse("v0.11.0-beta.1");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnexpectedTagPaths() {
        ReleaseVersion.parse("v0.11.0/../../other");
    }
}
