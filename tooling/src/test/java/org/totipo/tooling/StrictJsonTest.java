package org.totipo.tooling;

import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

/** JDK-only deterministic test runner. Failures always terminate unsuccessfully. */
public final class StrictJsonTest {
    private static int assertions;

    private StrictJsonTest() {}

    public static void main(String[] args) throws IOException {
        accept("{\"outer\":[{},[],{\"x\":[true,false,null,123,\"123\"]}]}");
        accept(" \n [1,2] \r\t");
        accept("{\"a\":{},\"b\":{\"a\":1}}");
        accept("\"" + "x".repeat(StrictJson.MAX_STRING) + "\"");
        accept("[".repeat(StrictJson.MAX_DEPTH) + "0" + "]".repeat(StrictJson.MAX_DEPTH));
        accept("0" + " ".repeat(StrictJson.MAX_BYTES - 1));
        for (String invalid : new String[] {
                "", " ", "{", "[1", "{\"a\":1,\"a\":2}",
                "{\"x\":{\"a\":1,\"\\u0061\":2}}", "{} []", "1 2", "{}garbage",
                "{} /* comment */", "// comment\n{}", "{\"a\":1,}", "[1,]",
                "{'a':1}", "{a:1}", "[NaN]", "[Infinity]", "[01]", "[+1]",
                "[.1]", "[1.]", "\"raw\nnewline\"",
                "0" + " ".repeat(StrictJson.MAX_BYTES),
                "[".repeat(StrictJson.MAX_DEPTH + 1) + "0" + "]".repeat(StrictJson.MAX_DEPTH + 1),
                "\"" + "x".repeat(StrictJson.MAX_STRING + 1) + "\"",
                "{\"" + "n".repeat(StrictJson.MAX_NAME + 1) + "\":0}",
                "1".repeat(StrictJson.MAX_NUMBER + 1),
                "[" + "0,".repeat(4096) + "0]",
                "\"" + "é".repeat(StrictJson.MAX_BYTES / 2) + "\""
        }) {
            reject(invalid);
        }
        // No floating-point conversion: preserve values beyond long and double precision.
        String integer = "18446744073709551616";
        try (var parser = StrictJson.open(bytes(integer))) {
            check(parser.nextToken() == JsonToken.VALUE_NUMBER_INT);
            check(StrictJson.exactInteger(parser).equals(new BigInteger(integer)));
            check(parser.nextToken() == null);
        }
        for (String nonInteger : new String[] {"\"123\"", "1.0", "1e0", "true", "null"}) {
            try (var parser = StrictJson.open(bytes(nonInteger))) {
                parser.nextToken();
                boolean rejected = false;
                try {
                    StrictJson.exactInteger(parser);
                } catch (IOException expected) {
                    rejected = true;
                }
                check(rejected);
            }
        }
        System.out.println("Strict JSON tests passed: " + assertions + " assertions");
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static void accept(String value) throws IOException {
        StrictJson.validate(bytes(value));
        assertions++;
    }

    private static void reject(String value) {
        boolean rejected = false;
        try {
            StrictJson.validate(bytes(value));
        } catch (IOException expected) {
            rejected = true;
        }
        check(rejected);
    }

    private static void check(boolean condition) {
        if (!condition) {
            throw new AssertionError("Assertion " + (assertions + 1) + " failed");
        }
        assertions++;
    }
}
