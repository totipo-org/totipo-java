package org.totipo.api;

import org.totipo.*;
import org.totipo.testing.MemoryVault;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class ObjectCandidateValidationTest {
    @Test void coreAuthoredCandidateIsIndependentAndDefensivelyOwned() throws Exception {
        var store = new MemoryVault();
        try (var session = store.create()) {
            PublicApiTest.finished(session);
            SaveResult.Saved saved;
            try (var secret = NewSecret.copyOf(new byte[]{1, 2, 3}); var editor = session.state().createToken()) {
                saved = PublicApiTest.saved(editor.secret(secret).save());
            }
            var state = PublicApiTest.awaitState(session, s -> s.token(saved.tokenId()).isPresent());
            var id = state.token(saved.tokenId()).orElseThrow().heads().get(0).revision();
            byte[] bytes = store.objects.get(id.hex()).clone();
            byte[] original = bytes.clone();
            int scans = store.scans, writes = store.writes, publications = store.publications;
            byte[] vault = store.bootstrap.clone();
            var valid = assertInstanceOf(ObjectCandidateValidation.Valid.class, session.validateObject(id, bytes));
            assertEquals(id, valid.objectId());
            assertArrayEquals(original, bytes);
            assertArrayEquals(original, valid.representation());
            Arrays.fill(bytes, (byte) 0);
            Arrays.fill(valid.representation(), (byte) 0);
            assertArrayEquals(original, valid.representation());
            for (int i = 0; i < 100; i++) {
                var duplicate = session.validateObject(id, original);
                assertEquals(valid, duplicate); assertEquals(valid.hashCode(), duplicate.hashCode());
                assertInstanceOf(ObjectCandidateValidation.Invalid.class, session.validateObject(id, bytes));
            }
            // Validate a candidate absent from the canonical replica: no automatic import or head change.
            store.objects.remove(id.hex());
            assertEquals(valid, session.validateObject(id, original));
            assertTrue(store.objects.isEmpty());
            assertSame(state, session.state());
            assertEquals(scans, store.scans); assertEquals(writes, store.writes);
            assertEquals(publications, store.publications); assertArrayEquals(vault, store.bootstrap);
            assertThrows(NullPointerException.class, () -> session.validateObject(null, original));
            assertThrows(NullPointerException.class, () -> session.validateObject(id, null));
            assertInstanceOf(ObjectCandidateValidation.Invalid.class,
                    session.validateObject(new RevisionId("0".repeat(64)), original));
            var otherStore = new MemoryVault();
            try (var other = otherStore.create()) {
                assertInstanceOf(ObjectCandidateValidation.Invalid.class, other.validateObject(id, original));
            }
            // Exercise live concurrent callers and close; every admitted call validates fully
            // or rejects before touching a wiped root, and close cannot deadlock them.
            var executor = Executors.newFixedThreadPool(4);
            try {
                var futures = new java.util.ArrayList<Future<?>>();
                for (int i = 0; i < 100; i++) {
                    if (i == 20) futures.add(executor.submit(session::close));
                    futures.add(executor.submit(() -> {
                        try { assertEquals(valid, session.validateObject(id, original)); }
                        catch (SessionClosedException closed) { /* Valid lifecycle rejection. */ }
                    }));
                }
                for (var future : futures) future.get(10, TimeUnit.SECONDS);
            } finally { executor.shutdownNow(); }
            assertThrows(SessionClosedException.class, () -> session.validateObject(id, original));
            assertThrows(SessionClosedException.class, () -> session.validateObject(id, new byte[0]));
            assertArrayEquals(original, valid.representation());
            assertEquals(valid, new ObjectCandidateValidation.Valid(id, original));
            assertFalse(valid.toString().contains(Arrays.toString(original)));
        }
    }
}
