package org.totipo.format;

import org.totipo.spi.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TokenObservationTest {
    @Test void incompleteScanAndUnavailableCandidateRetainIndependentAuthenticatedToken() {
        var object = V1EnvelopeWriter.seal(TokenPublicationTest.root(), TokenWriter.write(TokenPublicationTest.plan(1).stages().get(0).token()));
        var valid = new ObjectName(object.id().filename());
        var bad = new ObjectName("00".repeat(32));
        var junk = new ObjectName("junk");
        var store = new TestStore() {
            @Override public ObjectScan scanObjects() {
                return new ObjectScan.Incomplete(List.of(new ObjectEntry(valid, EntryKind.UNKNOWN, OptionalLong.of(9)),
                        new ObjectEntry(bad, EntryKind.REGULAR, OptionalLong.empty()),
                        new ObjectEntry(junk, EntryKind.REGULAR, OptionalLong.empty())), StoreFailure.UNAVAILABLE);
            }
            @Override public BoundedRead readObject(ObjectName name, int expected) {
                assertEquals(1024, expected); assertNotEquals(junk, name);
                return name.equals(valid) ? new BoundedRead.Present(object.bytes()) : new BoundedRead.Unavailable(StoreFailure.UNAVAILABLE);
            }
        };
        var observed = TokenStoreReader.read(store, TokenPublicationTest.root());
        assertEquals(1, observed.validatedTokens().size());
        assertEquals(List.of(TokenStoreObservation.SnapshotIssue.ENUMERATION_UNAVAILABLE), observed.snapshotDiagnostics());
        assertEquals(List.of(new TokenStoreObservation.CandidateDiagnostic(ObjectId.fromFilename(bad.value()),
                TokenStoreObservation.Reason.UNAVAILABLE)), observed.candidateDiagnostics());
    }
    @Test void sizeAndAuthenticatedGrammarFailuresRemainDistinct() {
        var malformed = V1EnvelopeWriter.seal(TokenPublicationTest.root(), new byte[]{0});
        var name = new ObjectName(malformed.id().filename());
        for (var read : List.of(new BoundedRead.Present(malformed.bytes()), new BoundedRead.Undersized(3), new BoundedRead.Oversized())) {
            var store = new TestStore() {
                @Override public ObjectScan scanObjects() { return new ObjectScan.Complete(List.of(new ObjectEntry(name, EntryKind.REGULAR, OptionalLong.empty()))); }
                @Override public BoundedRead readObject(ObjectName n, int expected) { return read; }
            };
            var observed = TokenStoreReader.read(store, TokenPublicationTest.root());
            assertTrue(observed.validatedTokens().isEmpty());
            assertEquals(read instanceof BoundedRead.Present ? TokenStoreObservation.Reason.INVALID_TOKEN
                    : TokenStoreObservation.Reason.INVALID_STORAGE, observed.candidateDiagnostics().get(0).reason());
        }
    }
}
