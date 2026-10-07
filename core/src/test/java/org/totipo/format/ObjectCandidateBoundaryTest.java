package org.totipo.format;

import org.totipo.*;
import org.totipo.conformance.VectorCaseLoader;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class ObjectCandidateBoundaryTest {
    private static ApplicationSession session(byte[] root) throws InterruptedException {
        var observed = new CountDownLatch(1);
        var session = new ApplicationSession(root, new VaultTestStore(), () -> {
            observed.countDown();
            return new DiscoverySource.Snapshot(List.of(), DiscoverySource.SnapshotIssue.NONE);
        }, new PublicationTestStore());
        assertTrue(observed.await(10, TimeUnit.SECONDS));
        return session;
    }

    @Test void corpusCoversAuthenticatedPaddingLengthIdentityAndGrammarFailures() throws Exception {
        var cases = new java.util.ArrayList<>(VectorCaseLoader.cryptoCases());
        cases.addAll(VectorCaseLoader.encodingCases());
        for (var vector : cases) {
            var data = vector.data();
            var bytes = data.field(data.has("post_aead") ? "post_aead" : "crypto");
            var id = new RevisionId(bytes.field("object_id").string());
            try (var session = session(data.field("root_hex").hex())) {
                var result = session.validateObject(id, bytes.field("object_hex").hex());
                if (vector.expected().equals("SUPPORTED_VALID"))
                    assertInstanceOf(ObjectCandidateValidation.Valid.class, result, vector.id());
                else assertInstanceOf(ObjectCandidateValidation.Invalid.class, result, vector.id());
            }
        }
    }

    @Test void physicalBoundsAndAeadFailureAreInvalidAndDoNotMutateInput() throws Exception {
        var object = TokenStoreReaderTest.object();
        var id = new RevisionId(object.id().filename());
        try (var session = session(TokenPublicationTest.root())) {
            for (int length : new int[]{0, 1, 87, 1008, 1023, 1025, 100000}) {
                var bytes = Arrays.copyOf(object.bytes(), length);
                var copy = bytes.clone();
                assertInstanceOf(ObjectCandidateValidation.Invalid.class, session.validateObject(id, bytes));
                assertArrayEquals(copy, bytes);
            }
            byte[] corrupted = object.bytes(); corrupted[1023] ^= 1;
            assertInstanceOf(ObjectCandidateValidation.Invalid.class, session.validateObject(id, corrupted));
            var malformed = V1EnvelopeWriter.seal(TokenPublicationTest.root(), new byte[]{0});
            assertInstanceOf(ObjectCandidateValidation.Invalid.class,
                    session.validateObject(new RevisionId(malformed.id().filename()), malformed.bytes()));
        }
    }

    @Test void deterministicRepresentationsCompareCompleteContentIncludingMetadata() throws Exception {
        var root = TokenPublicationTest.root();
        var token = TokenPublicationTest.plan(1).stages().get(0).token();
        try (var session = session(root)) {
            var first = V1EnvelopeWriter.seal(root, TokenWriter.write(token));
            var second = V1EnvelopeWriter.seal(root, TokenWriter.write(token));
            var a = session.validateObject(new RevisionId(first.id().filename()), first.bytes());
            var b = session.validateObject(new RevisionId(second.id().filename()), second.bytes());
            assertEquals(a, b); assertEquals(a.hashCode(), b.hashCode());
            var changed = new TokenObject(token.tokenId(), token.parents(), token.value(),
                    new TokenMetadata(java.util.Optional.of(""), java.util.Optional.empty()));
            var other = V1EnvelopeWriter.seal(root, TokenWriter.write(changed));
            assertNotEquals(a, session.validateObject(new RevisionId(other.id().filename()), other.bytes()));
            // Defensive graph collision cases are symbolic validated facts, not encrypted
            // HMAC collisions. Do not manufacture a cryptographic contradiction fixture.
        } finally { token.value().credential().secret().clear(); }
    }
}
