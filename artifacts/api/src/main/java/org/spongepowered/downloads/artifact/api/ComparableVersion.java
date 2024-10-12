package org.spongepowered.downloads.artifact.api;


import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * <p>
 * Generic implementation of version comparison.
 * </p>
 *
 * Features:
 * <ul>
 * <li>mixing of '<code>-</code>' (hyphen) and '<code>.</code>' (dot) separators,</li>
 * <li>transition between characters and digits also constitutes a separator:
 *     <code>1.0alpha1 =&gt; [1, 0, alpha, 1]</code></li>
 * <li>unlimited number of version components,</li>
 * <li>version components in the text can be digits or strings,</li>
 * <li>strings are checked for well-known qualifiers and the qualifier ordering is used for version ordering.
 *     Well-known qualifiers (case insensitive) are:<ul>
 *     <li><code>alpha</code> or <code>a</code></li>
 *     <li><code>beta</code> or <code>b</code></li>
 *     <li><code>milestone</code> or <code>m</code></li>
 *     <li><code>rc</code> or <code>cr</code></li>
 *     <li><code>snapshot</code></li>
 *     <li><code>(the empty string)</code> or <code>ga</code> or <code>final</code></li>
 *     <li><code>sp</code></li>
 *     </ul>
 *     Unknown qualifiers are considered after known qualifiers, with lexical order (always case insensitive),
 *   </li>
 * <li>a hyphen usually precedes a qualifier, and is always less important than digits/number, for example
 *   {@code 1.0.RC2 < 1.0-RC3 < 1.0.1}; but prefer {@code 1.0.0-RC1} over {@code 1.0.0.RC1}, and more
 *   generally: {@code 1.0.X2 < 1.0-X3 < 1.0.1} for any string {@code X}; but prefer {@code 1.0.0-X1}
 *   over {@code 1.0.0.X1}.</li>
 * </ul>
 *
 * @see <a href="https://cwiki.apache.org/confluence/display/MAVENOLD/Versioning">"Versioning" on Maven Wiki</a>
 * @author <a href="mailto:kenney@apache.org">Kenney Westerhof</a>
 * @author <a href="mailto:hboutemy@apache.org">Hervé Boutemy</a>
 */
public class ComparableVersion implements Comparable<ComparableVersion> {
    private static final int MAX_INTITEM_LENGTH = 9;

    private static final int MAX_LONGITEM_LENGTH = 18;

    private String value;

    private String canonical;

    private ComparableVersion.ListItem items;

    private interface Item {
        int INT_ITEM = 3;
        int LONG_ITEM = 4;
        int BIGINTEGER_ITEM = 0;
        int STRING_ITEM = 1;
        int LIST_ITEM = 2;

        int compareTo(ComparableVersion.Item item);

        int getType();

        boolean isNull();
    }

    /**
     * Represents a numeric item in the version item list that can be represented with an int.
     */
    private record IntItem(int value) implements ComparableVersion.Item {

        public static final ComparableVersion.IntItem ZERO = new ComparableVersion.IntItem();

        private IntItem() {
            this(0);
        }

        IntItem(String str) {
            this(Integer.parseInt(str));
        }

        @Override
        public int getType() {
            return INT_ITEM;
        }

        @Override
        public boolean isNull() {
            return value == 0;
        }

        @Override
        public int compareTo(ComparableVersion.Item item) {
            if (item == null) {
                return (value == 0) ? 0 : 1; // 1.0 == 1, 1.1 > 1
            }

            return switch (item.getType()) {
                case INT_ITEM -> {
                    int itemValue = ((IntItem) item).value;
                    yield Integer.compare(value, itemValue);
                }
                case LONG_ITEM, BIGINTEGER_ITEM -> -1;
                case STRING_ITEM -> 1; // 1.1 > 1-sp

                case LIST_ITEM -> 1; // 1.1 > 1-1

                default -> throw new IllegalStateException("invalid item: " + item.getClass());
            };
        }

        @Override
        public String toString() {
            return Integer.toString(value);
        }
    }

    /**
     * Represents a numeric item in the version item list that can be represented with a long.
     */
    private static record LongItem(long value) implements ComparableVersion.Item {

        LongItem(String str) {
            this(Long.parseLong(str));
        }

        @Override
        public int getType() {
            return LONG_ITEM;
        }

        @Override
        public boolean isNull() {
            return value == 0;
        }

        @Override
        public int compareTo(ComparableVersion.Item item) {
            if (item == null) {
                return (value == 0) ? 0 : 1; // 1.0 == 1, 1.1 > 1
            }

            return switch (item.getType()) {
                case INT_ITEM -> 1;
                case LONG_ITEM -> {
                    long itemValue = ((LongItem) item).value;
                    yield Long.compare(value, itemValue);
                }
                case BIGINTEGER_ITEM -> -1;
                case STRING_ITEM -> 1; // 1.1 > 1-sp

                case LIST_ITEM -> 1; // 1.1 > 1-1

                default -> throw new IllegalStateException("invalid item: " + item.getClass());
            };
        }

        @Override
        public String toString() {
            return Long.toString(value);
        }
    }

    /**
     * Represents a numeric item in the version item list.
     */
    private static record BigIntegerItem(BigInteger value) implements ComparableVersion.Item {

        BigIntegerItem(String str) {
           this(new BigInteger(str));
        }

        @Override
        public int getType() {
            return BIGINTEGER_ITEM;
        }

        @Override
        public boolean isNull() {
            return BigInteger.ZERO.equals(value);
        }

        @Override
        public int compareTo(ComparableVersion.Item item) {
            if (item == null) {
                return BigInteger.ZERO.equals(value) ? 0 : 1; // 1.0 == 1, 1.1 > 1
            }

            return switch (item.getType()) {
                case INT_ITEM, LONG_ITEM -> 1;
                case BIGINTEGER_ITEM -> value.compareTo(((BigIntegerItem) item).value);
                case STRING_ITEM -> 1; // 1.1 > 1-sp

                case LIST_ITEM -> 1; // 1.1 > 1-1

                default -> throw new IllegalStateException("invalid item: " + item.getClass());
            };
        }

        public String toString() {
            return value.toString();
        }
    }

    /**
     * Represents a string in the version item list, usually a qualifier.
     */
    private record StringItem(String value) implements ComparableVersion.Item {
        private static final List<String> QUALIFIERS =
            Arrays.asList("alpha", "beta", "milestone", "rc", "snapshot", "", "sp");

        private static final Properties ALIASES = new Properties();

        static {
            ALIASES.put("ga", "");
            ALIASES.put("final", "");
            ALIASES.put("release", "");
            ALIASES.put("cr", "rc");
        }

        /**
         * A comparable value for the empty-string qualifier. This one is used to determine if a given qualifier makes
         * the version older than one without a qualifier, or more recent.
         */
        private static final String RELEASE_VERSION_INDEX = String.valueOf(QUALIFIERS.indexOf(""));

        StringItem(String value, boolean followedByDigit) {
            this(deriveValueFollowedByDigit(value, followedByDigit));
        }

        private static String deriveValueFollowedByDigit(String value, boolean followedByDigit) {
            if (followedByDigit && value.length() == 1) {
                // a1 = alpha-1, b1 = beta-1, m1 = milestone-1
                switch (value.charAt(0)) {
                    case 'a':
                        value = "alpha";
                        break;
                    case 'b':
                        value = "beta";
                        break;
                    case 'm':
                        value = "milestone";
                        break;
                    default:
                }
            }
            return ALIASES.getProperty(value, value);
        }

        @Override
        public int getType() {
            return STRING_ITEM;
        }

        @Override
        public boolean isNull() {
            return (comparableQualifier(value).compareTo(RELEASE_VERSION_INDEX) == 0);
        }

        public static String comparableQualifier(String qualifier) {
            int i = QUALIFIERS.indexOf(qualifier);

            return i == -1 ? (QUALIFIERS.size() + "-" + qualifier) : String.valueOf(i);
        }

        @Override
        public int compareTo(ComparableVersion.Item item) {
            if (item == null) {
                // 1-rc < 1, 1-ga > 1
                return comparableQualifier(value).compareTo(RELEASE_VERSION_INDEX);
            }
            return switch (item.getType()) {
                case INT_ITEM, LONG_ITEM, BIGINTEGER_ITEM -> -1; // 1.any < 1.1 ?

                case STRING_ITEM ->
                    comparableQualifier(value).compareTo(comparableQualifier(((StringItem) item).value));
                case LIST_ITEM -> -1; // 1.any < 1-1

                default -> throw new IllegalStateException("invalid item: " + item.getClass());
            };
        }

        public String toString() {
            return value;
        }
    }

    /**
     * Represents a version list item. This class is used both for the global item list and for sub-lists (which start
     * with '-(number)' in the version specification).
     */
    private static class ListItem extends ArrayList<ComparableVersion.Item>
        implements ComparableVersion.Item {
        @Override
        public int getType() {
            return LIST_ITEM;
        }

        @Override
        public boolean isNull() {
            return (size() == 0);
        }

        void normalize() {
            for (int i = size() - 1; i >= 0; i--) {
                ComparableVersion.Item lastItem = get(i);

                if (lastItem.isNull()) {
                    // remove null trailing items: 0, "", empty list
                    remove(i);
                } else if (!(lastItem instanceof ComparableVersion.ListItem)) {
                    break;
                }
            }
        }

        @Override
        public int compareTo(ComparableVersion.Item item) {
            if (item == null) {
                if (size() == 0) {
                    return 0; // 1-0 = 1- (normalize) = 1
                }
                // Compare the entire list of items with null - not just the first one, MNG-6964
                for (ComparableVersion.Item i : this) {
                    int result = i.compareTo(null);
                    if (result != 0) {
                        return result;
                    }
                }
                return 0;
            }
            switch (item.getType()) {
                case INT_ITEM:
                case LONG_ITEM:
                case BIGINTEGER_ITEM:
                    return -1; // 1-1 < 1.0.x

                case STRING_ITEM:
                    return 1; // 1-1 > 1-sp

                case LIST_ITEM:
                    Iterator<ComparableVersion.Item> left = iterator();
                    Iterator<ComparableVersion.Item> right = ((ComparableVersion.ListItem) item).iterator();

                    while (left.hasNext() || right.hasNext()) {
                        ComparableVersion.Item l = left.hasNext() ? left.next() : null;
                        ComparableVersion.Item r = right.hasNext() ? right.next() : null;

                        // if this is shorter, then invert the compare and mul with -1
                        int result = l == null ? (r == null ? 0 : -1 * r.compareTo(l)) : l.compareTo(r);

                        if (result != 0) {
                            return result;
                        }
                    }

                    return 0;

                default:
                    throw new IllegalStateException("invalid item: " + item.getClass());
            }
        }

        @Override
        public String toString() {
            StringBuilder buffer = new StringBuilder();
            for (ComparableVersion.Item item : this) {
                if (!buffer.isEmpty()) {
                    buffer.append((item instanceof ComparableVersion.ListItem) ? '-' : '.');
                }
                buffer.append(item);
            }
            return buffer.toString();
        }
    }

    public ComparableVersion(String version) {
        parseVersion(version);
    }

    @SuppressWarnings("checkstyle:innerassignment")
    public final void parseVersion(String version) {
        this.value = version;

        items = new ComparableVersion.ListItem();

        version = version.toLowerCase(Locale.ENGLISH);

        ComparableVersion.ListItem list = items;

        Deque<ComparableVersion.Item> stack = new ArrayDeque<>();
        stack.push(list);

        boolean isDigit = false;

        int startIndex = 0;

        for (int i = 0; i < version.length(); i++) {
            char c = version.charAt(i);

            if (c == '.') {
                if (i == startIndex) {
                    list.add(ComparableVersion.IntItem.ZERO);
                } else {
                    list.add(parseItem(isDigit, version.substring(startIndex, i)));
                }
                startIndex = i + 1;
            } else if (c == '-') {
                if (i == startIndex) {
                    list.add(ComparableVersion.IntItem.ZERO);
                } else {
                    list.add(parseItem(isDigit, version.substring(startIndex, i)));
                }
                startIndex = i + 1;

                list.add(list = new ComparableVersion.ListItem());
                stack.push(list);
            } else if (Character.isDigit(c)) {
                if (!isDigit && i > startIndex) {
                    // 1.0.0.X1 < 1.0.0-X2
                    // treat .X as -X for any string qualifier X
                    if (!list.isEmpty()) {
                        list.add(list = new ComparableVersion.ListItem());
                        stack.push(list);
                    }

                    list.add(new ComparableVersion.StringItem(version.substring(startIndex, i), true));
                    startIndex = i;

                    list.add(list = new ComparableVersion.ListItem());
                    stack.push(list);
                }

                isDigit = true;
            } else {
                if (isDigit && i > startIndex) {
                    list.add(parseItem(true, version.substring(startIndex, i)));
                    startIndex = i;

                    list.add(list = new ComparableVersion.ListItem());
                    stack.push(list);
                }

                isDigit = false;
            }
        }

        if (version.length() > startIndex) {
            // 1.0.0.X1 < 1.0.0-X2
            // treat .X as -X for any string qualifier X
            if (!isDigit && !list.isEmpty()) {
                list.add(list = new ComparableVersion.ListItem());
                stack.push(list);
            }

            list.add(parseItem(isDigit, version.substring(startIndex)));
        }

        while (!stack.isEmpty()) {
            list = (ComparableVersion.ListItem) stack.pop();
            list.normalize();
        }
    }

    private static ComparableVersion.Item parseItem(boolean isDigit, String buf) {
        if (isDigit) {
            buf = stripLeadingZeroes(buf);
            if (buf.length() <= MAX_INTITEM_LENGTH) {
                // lower than 2^31
                return new ComparableVersion.IntItem(buf);
            } else if (buf.length() <= MAX_LONGITEM_LENGTH) {
                // lower than 2^63
                return new ComparableVersion.LongItem(buf);
            }
            return new ComparableVersion.BigIntegerItem(buf);
        }
        return new ComparableVersion.StringItem(buf, false);
    }

    private static String stripLeadingZeroes(String buf) {
        if (buf == null || buf.isEmpty()) {
            return "0";
        }
        for (int i = 0; i < buf.length(); ++i) {
            char c = buf.charAt(i);
            if (c != '0') {
                return buf.substring(i);
            }
        }
        return buf;
    }

    @Override
    public int compareTo(ComparableVersion o) {
        return items.compareTo(o.items);
    }

    @Override
    public String toString() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceof ComparableVersion) && items.equals(((ComparableVersion) o).items);
    }

    @Override
    public int hashCode() {
        return items.hashCode();
    }
}
