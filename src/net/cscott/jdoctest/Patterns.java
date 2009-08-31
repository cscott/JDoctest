package net.cscott.jdoctest;

import java.util.regex.Pattern;

/** Split out some regexp processing so it can be reused in the JUnit test
 * runner.
 * @author C. Scott Ananian
 */
class Patterns {
    /** Export the EXPECT FAIL processing so it can be used by
     *  {@link RerunJDoctests}.
     */
    public static boolean expectFail(String test_text) {
        return P_expect_fail.matcher(test_text).find();
    }
    private static final Pattern P_expect_fail =
        Pattern.compile("\\bEXPECT\\s+FAIL\\b");
}
