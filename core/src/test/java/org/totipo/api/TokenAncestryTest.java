package org.totipo.api;

import org.junit.jupiter.api.*;
import org.totipo.*;
import org.totipo.testing.MemoryVault;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.totipo.api.PublicApiTest.*;

class TokenAncestryTest {
    private static byte[] bootstrap;
    private MemoryVault store;
    private VaultSession session;
    @BeforeAll static void template() {
        var store = new MemoryVault();
        try (var created = store.create()) { bootstrap = store.bootstrap.clone(); assertNotNull(created.vaultId()); }
    }
    @BeforeEach void open() {
        store = new MemoryVault(); store.bootstrap = bootstrap.clone(); session = store.open(); finished(session);
    }
    @AfterEach void close() { session.close(); }
    private TokenHead root() {
        try (var secret = NewSecret.copyOf(new byte[]{1, 2, 3}); var create = session.state().createToken()) {
            var result = saved(create.secret(secret).metadata(metadata("source-marker")).save());
            return refresh(session).token(result.tokenId()).orElseThrow().heads().get(0);
        }
    }
    private static ClientMetadata metadata(String name) {
        return new ClientMetadata(Optional.of(name), Optional.of(-1L));
    }
    private TokenHead update(TokenHead parent, String name, TokenStatus status) {
        SaveResult.Saved result;
        try (var update = session.state().update(parent)) {
            result = saved(update.status(status).metadata(metadata(name)).save());
        }
        return refresh(session).token(parent.tokenId()).orElseThrow().heads().stream()
                .filter(h -> result.revisions().contains(h.revision())).findFirst().orElseThrow();
    }
    private static Set<TokenHead> reachable(TokenHead start) {
        var seen = new HashSet<TokenHead>();
        var pending = new ArrayDeque<TokenHead>(); pending.add(start);
        while (!pending.isEmpty()) {
            var head = pending.removeFirst();
            if (seen.add(head)) pending.addAll(head.parents());
        }
        return seen;
    }
    @Test void linearMetadataTombstoneSnapshotsAndHistoricalUpdate() {
        var a = root();
        var b = update(a, "replacement", TokenStatus.ACTIVE);
        var c = update(b, "deleted", TokenStatus.TOMBSTONED);
        var captured = session.state();
        assertEquals(b, c.parents().get(0));
        var historicalA = c.parents().get(0).parents().get(0);
        assertEquals(a, historicalA); assertEquals(a.hashCode(), historicalA.hashCode());
        assertEquals(a.tokenId(), historicalA.tokenId()); assertEquals(a.revision(), historicalA.revision());
        assertEquals(metadata("source-marker"), historicalA.metadata());
        assertEquals(metadata("replacement"), c.parents().get(0).metadata());
        assertTrue(historicalA.parents().isEmpty());
        assertSame(c.parents(), c.parents());
        assertThrows(UnsupportedOperationException.class, () -> c.parents().clear());
        // Historical references use the ordinary same-session update identity model.
        try (var edit = captured.update(historicalA)) { saved(edit.issuer("historical branch").save()); }
        refresh(session);
        try (var other = store.open()) {
            var otherHead = refresh(other).token(a.tokenId()).orElseThrow().heads().get(0);
            assertNotEquals(a, reachable(otherHead).stream().filter(h -> h.revision().equals(a.revision())).findFirst().orElseThrow());
            assertThrows(IllegalArgumentException.class, () -> other.state().update(historicalA));
        }
        session.close(); int scans = store.scans, writes = store.publications;
        assertEquals(Set.of(a, b, c), reachable(c));
        assertEquals(metadata("source-marker"), historicalA.metadata());
        assertEquals(TokenStatus.TOMBSTONED, captured.token(a.tokenId()).orElseThrow().alternatives().get(0).descriptor().status());
        assertEquals(scans, store.scans); assertEquals(writes, store.publications);
    }
    @Test void equivalentBranchesMergeAndSharedAncestors() {
        var a = root(); var b = update(a, "B", TokenStatus.ACTIVE); var c = update(a, "C", TokenStatus.ACTIVE);
        var token = session.state().token(a.tokenId()).orElseThrow();
        assertEquals(2, token.heads().size()); assertFalse(token.hasConflict());
        try (var merge = session.state().merge(a.tokenId())) { saved(merge.save()); }
        var d = refresh(session).token(a.tokenId()).orElseThrow().heads().get(0);
        assertEquals(Set.of(b, c), new HashSet<>(d.parents()));
        assertEquals(d.parents().stream().map(h -> h.revision().hex()).sorted().toList(),
                d.parents().stream().map(h -> h.revision().hex()).toList());
        assertSame(d.parents().get(0).parents().get(0), d.parents().get(1).parents().get(0));
        assertEquals(Set.of(a, b, c, d), reachable(d));
        assertEquals(ClientMetadata.empty(), d.metadata());
        assertEquals(d, refresh(session).token(a.tokenId()).orElseThrow().heads().get(0));
    }
    @Test void historicalGapsInvalidEvidenceAndLateAvailabilityRemainSnapshotLocal() {
        var a = root(); var b = update(a, "B", TokenStatus.ACTIVE); var c = update(b, "C", TokenStatus.ACTIVE);
        var bytes = store.objects.remove(a.revision().hex());
        var partial = refresh(session).token(a.tokenId()).orElseThrow();
        var partialC = partial.heads().get(0);
        assertEquals(List.of(new UnresolvedReference(b.revision(), a.revision())), partial.unresolvedReferences());
        assertEquals(b, partialC.parents().get(0)); assertTrue(partialC.parents().get(0).parents().isEmpty());
        assertEquals(Set.of(a, b, c), reachable(c));
        var corrupt = bytes.clone(); corrupt[0] ^= 1; store.objects.put(a.revision().hex(), corrupt);
        var invalid = refresh(session);
        assertFalse(invalid.diagnostics().isEmpty());
        assertEquals(Set.of(b, c), reachable(invalid.token(a.tokenId()).orElseThrow().heads().get(0)));
        store.observationUnavailable = true;
        assertFalse(refresh(session).diagnostics().isEmpty());
        store.observationUnavailable = false; store.objects.put(a.revision().hex(), bytes);
        var complete = refresh(session).token(a.tokenId()).orElseThrow();
        assertTrue(complete.unresolvedReferences().isEmpty());
        assertEquals(Set.of(a, b, c), reachable(complete.heads().get(0)));
        assertEquals(partialC, complete.heads().get(0));
        assertTrue(partialC.parents().get(0).parents().isEmpty());
        store.objects.remove(b.revision().hex());
        var missingImmediate = refresh(session).token(a.tokenId()).orElseThrow();
        assertTrue(missingImmediate.heads().stream().filter(c::equals).findFirst().orElseThrow().parents().isEmpty());
        assertEquals(List.of(new UnresolvedReference(c.revision(), b.revision())), missingImmediate.unresolvedReferences());
    }
    @Test void wrongTokenParentRemainsUnresolved() {
        var a = root(); var b = root();
        var child = org.totipo.format.ApplicationCausalFixture.add(store, b.tokenId(), a);
        var observed = refresh(session).token(b.tokenId()).orElseThrow();
        var head = observed.heads().stream().filter(h -> h.revision().equals(child)).findFirst().orElseThrow();
        assertTrue(head.parents().isEmpty());
        assertTrue(observed.unresolvedReferences().contains(new UnresolvedReference(child, a.revision())));
    }
    @Test void foldRetainsEveryExactBranchAndMetadata() {
        var a = root(); var branches = new HashSet<TokenHead>();
        for (int i = 0; i < 10; i++) branches.add(update(a, "branch-" + i, TokenStatus.ACTIVE));
        SaveResult.Saved result;
        try (var merge = session.state().merge(a.tokenId())) { result = saved(merge.save()); }
        assertTrue(result.revisions().size() > 1);
        var head = refresh(session).token(a.tokenId()).orElseThrow().heads().get(0);
        var all = reachable(head);
        assertTrue(all.containsAll(branches)); assertTrue(all.contains(a));
        assertEquals(11 + result.revisions().size(), all.size());
        branches.forEach(branch -> assertEquals(branch.metadata(), all.stream().filter(branch::equals).findFirst().orElseThrow().metadata()));
        var roots = all.stream().filter(h -> h.parents().isEmpty()).toList(); assertEquals(List.of(a), roots);
    }
    @Test void publicHistoryHasOnlyDescriptiveAccessors() {
        assertEquals(Set.of("tokenId", "revision", "metadata", "parents"),
                Arrays.stream(TokenHead.class.getDeclaredMethods()).map(java.lang.reflect.Method::getName)
                        .collect(java.util.stream.Collectors.toSet()));
        var head = root(); session.close();
        assertEquals(metadata("source-marker"), head.metadata()); assertTrue(head.parents().isEmpty());
    }
}
