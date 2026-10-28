package net.cscott.jdoctest;

import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/** Runs JDoctest's own doctests, as an example of {@link JDoctestRunner}. */
class JDoctestSelfTest {
    @TestFactory
    Stream<DynamicTest> doctests() {
        return JDoctestRunner.doctests(JDoctest.class);
    }
}
