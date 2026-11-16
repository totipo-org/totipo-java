package org.totipo.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/** Internal two-module bridge for reads bounded by content, without trusting file sizes. */
public final class BoundedInput {
    private BoundedInput() { }

    public static byte[] read(InputStream input, int limit) throws IOException {
        if (limit < 0) throw new IllegalArgumentException("Negative read bound");
        Objects.requireNonNull(input);
        byte[] buffer = new byte[Math.min(limit, 8192)];
        var output = new ByteArrayOutputStream(buffer.length);
        int remaining = limit;
        while (remaining > 0) {
            int count = input.read(buffer, 0, Math.min(remaining, buffer.length));
            if (count < 0) break;
            if (count == 0) {
                int one = input.read();
                if (one < 0) break;
                output.write(one); remaining--;
            } else {
                output.write(buffer, 0, count); remaining -= count;
            }
        }
        return output.toByteArray();
    }
}
