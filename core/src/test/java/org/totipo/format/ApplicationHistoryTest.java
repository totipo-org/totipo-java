package org.totipo.format;

import org.junit.jupiter.api.Test;
import org.totipo.ClientMetadata;
import java.lang.management.ManagementFactory;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ApplicationHistoryTest {
    @Test void deepHistorySharesNodesAndBuildsWithoutRecursion() {
        int size = 50_000;
        var token = new org.totipo.TokenId("00".repeat(32));
        var facts = new LinkedHashMap<ObjectId, ApplicationSession.CausalFact>();
        for (int i = 1; i <= size; i++) {
            facts.put(TokenGraphTest.id(i), new ApplicationSession.CausalFact(token,
                    i == 1 ? List.of() : List.of(TokenGraphTest.id(i - 1)), ClientMetadata.empty(), 1));
        }
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long thread = Thread.currentThread().getId();
        long before = bean.getThreadAllocatedBytes(thread);
        long start = System.nanoTime();
        var history = new ApplicationSession.History(new Object(), facts);
        long elapsed = System.nanoTime() - start;
        long allocated = bean.getThreadAllocatedBytes(thread) - before;
        System.out.printf(Locale.ROOT, "History: %,d nodes, %,d edges, %,d allocated bytes, %.1f ms%n",
                size, size - 1, allocated, elapsed / 1_000_000.0);
        assertEquals(size, history.heads.size());
        var node = history.heads.get(TokenGraphTest.id(size));
        for (int i = size; i > 1; i--) {
            assertSame(node.parents(), node.parents());
            node = (ApplicationSession.Head) node.parents().get(0);
            assertSame(history.heads.get(TokenGraphTest.id(i - 1)), node);
        }
        assertTrue(node.parents().isEmpty());
    }
    @Test void defensiveCyclesAreFiniteAndDoNotAlterHeadIdentity() {
        var token = new org.totipo.TokenId("00".repeat(32));
        var identity = new Object();
        var a = TokenGraphTest.id(1); var b = TokenGraphTest.id(2);
        var facts = Map.of(a, new ApplicationSession.CausalFact(token, List.of(b), ClientMetadata.empty(), 1),
                b, new ApplicationSession.CausalFact(token, List.of(a), ClientMetadata.empty(), 1));
        var history = new ApplicationSession.History(identity, facts);
        var head = history.heads.get(a);
        assertSame(head, head.parents().get(0).parents().get(0));
        var seen = new HashSet<org.totipo.TokenHead>();
        var pending = new ArrayDeque<org.totipo.TokenHead>(); pending.add(head);
        while (!pending.isEmpty()) { var next = pending.removeFirst(); if (seen.add(next)) pending.addAll(next.parents()); }
        assertEquals(2, seen.size());
        var changed = new ApplicationSession.History(identity, Map.of(a,
                new ApplicationSession.CausalFact(token, List.of(),
                        new ClientMetadata(Optional.of("different"), Optional.empty()), 99)));
        assertEquals(head, changed.heads.get(a)); assertEquals(head.hashCode(), changed.heads.get(a).hashCode());
        // A captured head retains only a marker, immutable descriptions and graph nodes.
        for (var field : ApplicationSession.Head.class.getDeclaredFields()) {
            assertFalse(secretOrSessionType(field.getType()));
        }
    }
    private static boolean secretOrSessionType(Class<?> type) {
        return org.totipo.VaultSession.class.isAssignableFrom(type)
                || org.totipo.spi.TotipoStore.class.isAssignableFrom(type)
                || type == TokenValue.class || type == SecurityBytes.class || type == byte[].class;
    }
}
