package com.tencent.mobileqq.aio.input.edit;

import java.util.List;

/** Test APK only: the confirmed GetSelectMediaInfo result shape, with synthetic values. */
public class b {
    public static class i {
        private final List<?> d;
        private final boolean e;
        private final RuntimeException fixtureFailure;
        public int fixtureReads;
        public i(List<?> selectedMediaInfo,boolean quality) {this(selectedMediaInfo,quality,null);}
        public i(List<?> selectedMediaInfo,boolean quality,RuntimeException failure) {
            d=selectedMediaInfo;e=quality;fixtureFailure=failure;
        }
        public final List<?> b() {
            fixtureReads++;
            if(fixtureFailure!=null)throw fixtureFailure;
            return d;
        }
        public final boolean a() {throw new AssertionError("Media classification must not read quality");}
        @Override public String toString() {throw new AssertionError("Media classification must not stringify native data");}
    }
}
