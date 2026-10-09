package xyz.fearr.adfree;

import java.math.BigInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Numeric release tags used by the release workflow; never compare them lexically. */
public final class ReleaseVersion implements Comparable<ReleaseVersion> {
    private static final Pattern FORMAT = Pattern.compile("v?([0-9]+)\\.([0-9]+)\\.([0-9]+)");
    private final BigInteger[] parts;

    private ReleaseVersion(BigInteger[] parts) {
        this.parts = parts;
    }

    public static ReleaseVersion parse(String value) {
        Matcher match = FORMAT.matcher(value);
        if (!match.matches()) throw new IllegalArgumentException("Unsupported release version");
        return new ReleaseVersion(new BigInteger[] {
                new BigInteger(match.group(1)), new BigInteger(match.group(2)), new BigInteger(match.group(3))
        });
    }

    @Override
    public int compareTo(ReleaseVersion other) {
        for (int i = 0; i < parts.length; i++) {
            int result = parts[i].compareTo(other.parts[i]);
            if (result != 0) return result;
        }
        return 0;
    }
}
