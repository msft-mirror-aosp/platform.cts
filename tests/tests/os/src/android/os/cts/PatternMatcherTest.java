/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.os.cts;

import android.os.Parcel;
import android.os.PatternMatcher;

import junit.framework.TestCase;

public class PatternMatcherTest extends TestCase {

    private PatternMatcher mPatternMatcher;

    @Override
    protected void setUp() throws Exception {

        super.setUp();
        mPatternMatcher = null;
    }

    public void testConstructor() {

        // new the PatternMatcher instance
        mPatternMatcher = new PatternMatcher("test", PatternMatcher.PATTERN_LITERAL);
        assertNotNull(mPatternMatcher);

        // new the PatternMatcher instance
        Parcel p = Parcel.obtain();
        p.writeString("test");
        p.writeInt(PatternMatcher.PATTERN_LITERAL);
        p.setDataPosition(0);
        mPatternMatcher = new PatternMatcher(p);
        assertNotNull(mPatternMatcher);

    }

    public void testGetType() {

        mPatternMatcher = new PatternMatcher("test", PatternMatcher.PATTERN_LITERAL);
        assertEquals(PatternMatcher.PATTERN_LITERAL, mPatternMatcher.getType());

        mPatternMatcher = new PatternMatcher("test", PatternMatcher.PATTERN_PREFIX);
        assertEquals(PatternMatcher.PATTERN_PREFIX, mPatternMatcher.getType());

        mPatternMatcher = new PatternMatcher("test", PatternMatcher.PATTERN_SIMPLE_GLOB);
        assertEquals(PatternMatcher.PATTERN_SIMPLE_GLOB, mPatternMatcher.getType());
    }

    public void testGetPath() {

        // set the expected value
        String expected1 = "test1";

        mPatternMatcher = new PatternMatcher(expected1, PatternMatcher.PATTERN_LITERAL);
        assertEquals(expected1, mPatternMatcher.getPath());

        String expected2 = "test2";

        mPatternMatcher = new PatternMatcher(expected2, PatternMatcher.PATTERN_LITERAL);
        assertEquals(expected2, mPatternMatcher.getPath());
    }

    public void testToString() {

        // set the expected value
        String str = "test";
        String expected1 = "PatternMatcher{LITERAL: test}";
        String expected2 = "PatternMatcher{PREFIX: test}";
        String expected3 = "PatternMatcher{GLOB: test}";

        mPatternMatcher = new PatternMatcher(str, PatternMatcher.PATTERN_LITERAL);
        assertEquals(expected1, mPatternMatcher.toString());

        mPatternMatcher = new PatternMatcher(str, PatternMatcher.PATTERN_PREFIX);
        assertEquals(expected2, mPatternMatcher.toString());

        mPatternMatcher = new PatternMatcher(str, PatternMatcher.PATTERN_SIMPLE_GLOB);
        assertEquals(expected3, mPatternMatcher.toString());

    }

    public void testWriteToParcel() {

        String expected = "test1";

        mPatternMatcher = new PatternMatcher(expected, PatternMatcher.PATTERN_LITERAL);
        Parcel p = Parcel.obtain();
        mPatternMatcher.writeToParcel(p, 0);

        p.setDataPosition(0);
        assertEquals(expected, p.readString());
        assertEquals(PatternMatcher.PATTERN_LITERAL, p.readInt());
    }

    public void testDescribeContents() {

        // set the expected value
        mPatternMatcher = new PatternMatcher("test", PatternMatcher.PATTERN_LITERAL);
        assertEquals(0, mPatternMatcher.describeContents());
    }

    public void testMatch() {

        // set the expected value
        mPatternMatcher = new PatternMatcher("test", PatternMatcher.PATTERN_LITERAL);
        assertTrue(mPatternMatcher.match("test"));
        assertFalse(mPatternMatcher.match("test1"));

        mPatternMatcher = new PatternMatcher("test", PatternMatcher.PATTERN_PREFIX);
        assertTrue(mPatternMatcher.match("testHello"));
        assertFalse(mPatternMatcher.match("atestHello"));

        mPatternMatcher = new PatternMatcher("test", -1);
        assertFalse(mPatternMatcher.match("testHello"));
        assertFalse(mPatternMatcher.match("test"));
        assertFalse(mPatternMatcher.match("atestHello"));

        mPatternMatcher = new PatternMatcher("", PatternMatcher.PATTERN_SIMPLE_GLOB);
        assertTrue(mPatternMatcher.match(""));

        mPatternMatcher = new PatternMatcher("d*", PatternMatcher.PATTERN_SIMPLE_GLOB);
        assertFalse(mPatternMatcher.match("test"));
    }

    public void testMatch_simpleGlobPattern_dot() {
        // A period (unless escaped) acts as a wildcard for one instance of any character.
        mPatternMatcher = new PatternMatcher(".", PatternMatcher.PATTERN_SIMPLE_GLOB);
        assertFalse(mPatternMatcher.match(""));
        assertTrue(mPatternMatcher.match("a"));
        assertTrue(mPatternMatcher.match("."));

        // A period only matches any one character and no more.
        mPatternMatcher = new PatternMatcher("....", PatternMatcher.PATTERN_SIMPLE_GLOB);
        assertTrue(mPatternMatcher.match("test"));
        assertFalse(mPatternMatcher.match("tests"));

        // A period (unless escaped) acts as a wildcard for a single character.
        mPatternMatcher = new PatternMatcher("te.t", PatternMatcher.PATTERN_SIMPLE_GLOB);
        assertTrue(mPatternMatcher.match("te.t"));
        assertTrue(mPatternMatcher.match("test"));

        // An escaped period only matches with a literal period and no other character.
        mPatternMatcher = new PatternMatcher("te\\.t", PatternMatcher.PATTERN_SIMPLE_GLOB);
        assertTrue(mPatternMatcher.match("te.t"));
        assertFalse(mPatternMatcher.match("test"));
        assertFalse(mPatternMatcher.match("te\\t"));

        // A ".*"  matches 0 or more instances of any character.
        mPatternMatcher = new PatternMatcher("te.*st", PatternMatcher.PATTERN_SIMPLE_GLOB);
        assertTrue(mPatternMatcher.match("terandomst"));
        assertTrue(mPatternMatcher.match("te.*st"));
        assertTrue(mPatternMatcher.match("test"));
        assertTrue(mPatternMatcher.match("te...st"));
        // ".*" matches characters until the next instance of the pattern is found. In this case, it
        // matches the 'r' and then stops since it's sees an 's' (the next character after ".*") in
        // the pattern. Therefore, the below string won't match because the "st" does not match
        // anything.
        assertFalse(mPatternMatcher.match("terstst"));
        assertFalse(mPatternMatcher.match("testst"));

        mPatternMatcher = new PatternMatcher("te*.t", PatternMatcher.PATTERN_SIMPLE_GLOB);
        assertTrue(mPatternMatcher.match("teest"));
        assertTrue(mPatternMatcher.match("te.t"));

        // An escaped period followed by an asterisk should only match literal period characters.
        mPatternMatcher = new PatternMatcher("te\\.*st", PatternMatcher.PATTERN_SIMPLE_GLOB);
        assertFalse(mPatternMatcher.match("terandomst"));
        assertTrue(mPatternMatcher.match("te...st"));
    }
}
