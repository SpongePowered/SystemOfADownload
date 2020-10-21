package org.spongepowered.downloads.utils;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.UUID;

/**
 * A utility class for implementing UUID Type 5 due to lack of implementation support from the
 * Java standard libraries. This allows us to create a UUID based on a
 * user's provided email, turning user data into application created data,
 * thereby allowing us to refer to users by a UUID based on their email, as the
 * primary key for all persistent entities.
 *
 * @see <a href="https://stackoverflow.com/a/40230410">StackOverflow question regarding UUID Type 5 in Java</a>
 * @see <a href="https://stackoverflow.com/questions/10867405/generating-v5-uuid-what-is-name-and-namespace/28776880#28776880">Answer providing public domain code for it's implementation</a>
 */
public class UUIDType5 {

    private static final Charset UTF8 = StandardCharsets.UTF_8;
    public static final UUID NAMESPACE_DNS = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");
    public static final UUID NAMESPACE_URL = UUID.fromString("6ba7b811-9dad-11d1-80b4-00c04fd430c8");
    public static final UUID NAMESPACE_OID = UUID.fromString("6ba7b812-9dad-11d1-80b4-00c04fd430c8");
    public static final UUID NAMESPACE_X500 = UUID.fromString("6ba7b814-9dad-11d1-80b4-00c04fd430c8");

    public static UUID nameUUIDFromNamespaceAndString(final UUID namespace, final String name) {
        return UUIDType5.nameUUIDFromNamespaceAndBytes(namespace, Objects.requireNonNull(name, "name == null").getBytes(UUIDType5.UTF8));
    }

    public static UUID nameUUIDFromNamespaceAndBytes(final UUID namespace, final byte[] name) {
        final MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-1");
        } catch (final NoSuchAlgorithmException nsae) {
            throw new InternalError("SHA-1 not supported");
        }
        md.update(UUIDType5.toBytes(Objects.requireNonNull(namespace, "namespace is null")));
        md.update(Objects.requireNonNull(name, "name is null"));
        final byte[] sha1Bytes = md.digest();
        sha1Bytes[6] &= 0x0f;  /* clear version        */
        sha1Bytes[6] |= 0x50;  /* set to version 5     */
        sha1Bytes[8] &= 0x3f;  /* clear variant        */
        sha1Bytes[8] |= 0x80;  /* set to IETF variant  */
        return UUIDType5.fromBytes(sha1Bytes);
    }

    private static UUID fromBytes(final byte[] data) {
        // Based on the private UUID(bytes[]) constructor
        long msb = 0;
        long lsb = 0;
        assert data.length >= 16;
        for (int i = 0; i < 8; i++)
            msb = (msb << 8) | (data[i] & 0xff);
        for (int i = 8; i < 16; i++)
            lsb = (lsb << 8) | (data[i] & 0xff);
        return new UUID(msb, lsb);
    }


    private static byte[] toBytes(final UUID uuid) {
        // inverted logic of fromBytes()
        final byte[] out = new byte[16];
        final long msb = uuid.getMostSignificantBits();
        final long lsb = uuid.getLeastSignificantBits();
        for (int i = 0; i < 8; i++)
            out[i] = (byte) ((msb >> ((7 - i) * 8)) & 0xff);
        for (int i = 8; i < 16; i++)
            out[i] = (byte) ((lsb >> ((15 - i) * 8)) & 0xff);
        return out;
    }
}
