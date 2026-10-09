package org.totipo.format;

import org.totipo.spi.*;
import java.util.List;

/** Cohesive layout SPI fixture; tests override only the operations they exercise. */
abstract class TestStore implements TotipoStore {
    static BoundedRead read(byte[] bytes, int expected) {
        if (bytes == null) return new BoundedRead.Absent();
        if (bytes.length < expected) return new BoundedRead.Undersized(bytes.length);
        if (bytes.length > expected) return new BoundedRead.Oversized();
        return new BoundedRead.Present(bytes);
    }
    @Override public BoundedRead readVault(int expected) { return new BoundedRead.Absent(); }
    @Override public ObjectScan scanObjects() { return new ObjectScan.Complete(List.of()); }
    @Override public BoundedRead readObject(ObjectName name, int expected) { return new BoundedRead.Absent(); }
    @Override public ObjectWrite publishObject(ObjectName name, byte[] bytes) { throw new AssertionError("Unexpected publication"); }
    @Override public VaultCreate createVault(byte[] bytes) { throw new AssertionError("Unexpected creation"); }
    @Override public void close() { }
}
