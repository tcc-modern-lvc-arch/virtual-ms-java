package io.github.raphonzius.lvc.virtualareas.domain.event;

// Domain-side LVC origin. Translation to/from the proto enum lives in the
// infrastructure anti-corruption layer (infrastructure.output.grpc.ProtoMapper).
public enum LvcOrigin {
    LIVE, VIRTUAL, CONSTRUCTIVE
}
