package org.totipo.internal;

import java.io.*;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BoundedInputTest {
    @Test void matchesJdkAtEofAndBoundsWithoutConsumingLookaheadOrClosing() throws Exception {
        byte[] bytes = new byte[17000]; new Random(30).nextBytes(bytes);
        for (int length : new int[]{0, 1, 87, 1024, 8191, 8192, 8193, 17000}) {
            for (int limit : new int[]{0, 1, 86, 87, 1024, 8192, 8193, 17001, Integer.MAX_VALUE}) {
                var expected = new ByteArrayInputStream(bytes, 0, length);
                var actual = new ByteArrayInputStream(bytes, 0, length) {
                    @Override public void close() { fail("Borrowed stream closed"); }
                    @Override public synchronized int read(byte[] b, int off, int len) {
                        return super.read(b, off, Math.min(7, len));
                    }
                };
                assertArrayEquals(expected.readNBytes(limit), BoundedInput.read(actual, limit));
                assertEquals(expected.read(), actual.read());
            }
        }
    }

    @Test void handlesZeroProgressAndPropagatesIoFailure() throws Exception {
        var input = new ByteArrayInputStream(new byte[]{1, 2, 3}) {
            @Override public synchronized int read(byte[] b, int off, int len) { return 0; }
        };
        assertArrayEquals(new byte[]{1, 2, 3}, BoundedInput.read(input, 4));
        var failure = new IOException("test failure");
        var broken = new InputStream() { @Override public int read() throws IOException { throw failure; } };
        assertSame(failure, assertThrows(IOException.class, () -> BoundedInput.read(broken, 1)));
        assertArrayEquals(new byte[0], BoundedInput.read(broken, 0));
        assertThrows(IllegalArgumentException.class, () -> BoundedInput.read(input, -1));
        assertThrows(NullPointerException.class, () -> BoundedInput.read(null, 1));
    }
}
