package org.totipo.format;

import org.totipo.spi.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Exact-byte SPI simulation; provides no filesystem or physical persistence evidence. */
final class PublicationTestStore extends TestStore {
    final Map<ObjectId, byte[]> objects = new LinkedHashMap<>();
    final List<ObjectWrite> acknowledgements = new ArrayList<>();
    int calls;
    int failCall = -1;
    boolean installOnFailure;
    boolean closed;
    @Override public ObjectWrite publishObject(ObjectName name, byte[] bytes) {
        ObjectId id = ObjectId.fromFilename(name.value());
        if (closed) throw new IllegalStateException("Closed store");
        if (bytes.length != 1024) throw new IllegalArgumentException("Exact object required");
        calls++;
        byte[] owned = bytes.clone();
        byte[] old = objects.get(id);
        if (old != null && !Arrays.equals(old, owned)) return new ObjectWrite.ExistingDifferent();
        if (calls == failCall) {
            if (installOnFailure) objects.putIfAbsent(id, owned);
            return new ObjectWrite.Uncertain(StoreFailure.UNAVAILABLE);
        }
        var result = old == null ? new ObjectWrite.Written() : new ObjectWrite.AlreadyPresentExact();
        objects.putIfAbsent(id, owned);
        acknowledgements.add(result);
        return result;
    }
    @Override public ObjectScan scanObjects() {
        return new ObjectScan.Complete(objects.keySet().stream().map(id -> new ObjectEntry(
                new ObjectName(id.filename()), EntryKind.REGULAR, java.util.OptionalLong.empty())).toList());
    }
    @Override public BoundedRead readObject(ObjectName name, int expected) {
        return read(objects.get(ObjectId.fromFilename(name.value())), expected);
    }
    @Override public void close() { closed = true; }
}
