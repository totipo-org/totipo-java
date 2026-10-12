package org.totipo.tooling;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import java.io.IOException;
import java.math.BigInteger;

/** Small streaming contract; future schemas must validate their own field types and ranges. */
public final class StrictJson {
    static final int MAX_BYTES = 16_384;
    static final int MAX_DEPTH = 16;
    static final int MAX_STRING = 256;
    static final int MAX_NAME = 128;
    static final int MAX_NUMBER = 128;

    public record Profile(int bytes, int depth, int string, int name, int number, int tokens) {}
    public static final Profile MODULE = new Profile(65_536, 16, 2048, 128, 128, 8192);
    public static final Profile MANIFEST = new Profile(32_768, 8, 1024, 128, 128, 4096);
    private static final Profile BOOTSTRAP = new Profile(MAX_BYTES, MAX_DEPTH, MAX_STRING,
            MAX_NAME, MAX_NUMBER, 4096);
    private static final JsonFactory FACTORY = factory(BOOTSTRAP);

    private StrictJson() {}

    private static JsonFactory factory(Profile profile) {
        var builder = JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxDocumentLength(profile.bytes())
                        .maxNestingDepth(profile.depth())
                        .maxStringLength(profile.string())
                        .maxNameLength(profile.name())
                        .maxNumberLength(profile.number())
                        .maxTokenCount(profile.tokens())
                        .build());
        // Explicitly disable every nonstandard JSON syntax extension.
        for (JsonReadFeature feature : JsonReadFeature.values()) {
            builder.disable(feature);
        }
        return builder.build();
    }

    static JsonParser open(byte[] input) throws IOException {
        // Independent raw-byte bound, before Jackson sees any input.
        if (input.length > MAX_BYTES) {
            throw new IOException("JSON input exceeds byte limit");
        }
        return FACTORY.createParser(input);
    }

    public static JsonParser open(byte[] input, Profile profile) throws IOException {
        if (input.length > profile.bytes()) throw new IOException("JSON input exceeds byte limit");
        return factory(profile).createParser(input);
    }

    static void validate(byte[] input) throws IOException {
        try (JsonParser parser = open(input)) {
            JsonToken token = parser.nextToken();
            if (token == null) {
                throw new IOException("Empty JSON input");
            }
            int depth = 0;
            do {
                if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) {
                    depth++;
                } else if (token == JsonToken.END_OBJECT || token == JsonToken.END_ARRAY) {
                    depth--;
                } else if (token == JsonToken.VALUE_STRING) {
                    // Force lazy string decoding and its resource constraint checks.
                    parser.getText();
                }
                if (depth == 0) {
                    break;
                }
                token = parser.nextToken();
                if (token == null) {
                    throw new IOException("Incomplete JSON value");
                }
            } while (true);
            if (parser.nextToken() != null) {
                throw new IOException("Trailing JSON value");
            }
        }
    }

    public static BigInteger exactInteger(JsonParser parser) throws IOException {
        if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
            throw new IOException("Expected JSON integer token");
        }
        return parser.getBigIntegerValue();
    }
}
