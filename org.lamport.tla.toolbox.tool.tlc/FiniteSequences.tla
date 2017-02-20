-------------------------- MODULE FiniteSequences --------------------------
EXTENDS Sequences

LOCAL INSTANCE Naturals

FiniteSeq(T,N) == UNION {[1..m -> T] : m \in 0..N}
=============================================================================
