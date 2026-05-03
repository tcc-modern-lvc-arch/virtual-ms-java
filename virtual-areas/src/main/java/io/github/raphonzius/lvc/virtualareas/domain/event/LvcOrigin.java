package io.github.raphonzius.lvc.virtualareas.domain.event;

// Mirrors the proto LvcOrigin enum without importing it directly —
// both classes share the same simple name, so FQN is used in method signatures.
public enum LvcOrigin {
    LIVE, VIRTUAL, CONSTRUCTIVE;

    public static LvcOrigin fromProto(io.github.raphonzius.lvc.proto.event.LvcOrigin proto) {
        return switch (proto) {
            case LIVE -> LIVE;
            case VIRTUAL -> VIRTUAL;
            case CONSTRUCTIVE -> CONSTRUCTIVE;
            default -> LIVE;
        };
    }

    public io.github.raphonzius.lvc.proto.event.LvcOrigin toProto() {
        return switch (this) {
            case LIVE -> io.github.raphonzius.lvc.proto.event.LvcOrigin.LIVE;
            case VIRTUAL -> io.github.raphonzius.lvc.proto.event.LvcOrigin.VIRTUAL;
            case CONSTRUCTIVE -> io.github.raphonzius.lvc.proto.event.LvcOrigin.CONSTRUCTIVE;
        };
    }
}
